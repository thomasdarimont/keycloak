/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
 *  and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.events.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.jpa.entities.OutboxEntryEntity;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.timer.ScheduledTask;
import org.keycloak.utils.KeycloakSessionUtil;

import org.jboss.logging.Logger;

/**
 * Drains the generic outbox for one registered {@code entryKind}: claims
 * due PENDING rows, hands each off to the kind's
 * {@link OutboxDeliveryHandler}, and transitions the row based on the
 * returned {@link OutboxDeliveryOutcome}.
 *
 * <p>One drainer instance per registered kind. Each is wrapped in a
 * {@code ClusterAwareScheduledTaskRunner} at scheduling time so in an
 * HA deployment only one node drains a given kind per interval, even
 * though every node schedules the timer. The runner must be given
 * {@link #getTaskName()} as its lock key: the key is unique per
 * {@code entryKind}, whereas the runner's default (the class name) is
 * shared by every drainer instance and would make them exclude each
 * other.
 *
 * <p>Transaction layout of one tick. The session the scheduler hands
 * to {@link #run} is used only for the tick-level listener callbacks;
 * all database work runs in transactions the drainer opens itself:
 * <ol>
 *   <li><b>Claim</b> — one short transaction locks up to
 *       {@link OutboxConfig#batchSize()} due rows ({@code FOR UPDATE
 *       SKIP LOCKED}), stamps a per-tick token and pushes their
 *       {@code next_attempt_at} out by {@link OutboxConfig#claimLease()}.
 *       After commit no row lock is held. With
 *       {@link OutboxConfig#perOwnerBatchSize()} set the rows are taken
 *       owner by owner, oldest-waiting owner first and at most that
 *       many per owner per round, so one destination cannot fill the
 *       batch (see {@link #claimOwnerFair}).</li>
 *   <li><b>Deliver</b> — per row, in a transaction of its own, the
 *       handler is invoked. Slow destinations therefore block neither
 *       the other rows of the batch nor other ticks, and a transaction
 *       timeout affects only this row.</li>
 *   <li><b>Record</b> — per row, in another short transaction, the
 *       row is re-read with a write lock and the transition applied
 *       only if it still carries the tick's token. A lease that
 *       expired mid-delivery lets another tick re-claim the row; the
 *       late tick's record is then skipped rather than overwriting the
 *       newer attempt.</li>
 *   <li><b>Budget</b> — between rows the tick checks
 *       {@link OutboxConfig#tickBudget()}; claimed rows it does not get
 *       to are handed back (token cleared, due now) in one
 *       transaction.</li>
 *   <li><b>Housekeeping</b> — one final transaction promotes rows
 *       older than {@link OutboxConfig#pendingMaxAge()} to DEAD_LETTER
 *       and purges DELIVERED / DEAD_LETTER rows past their retention.</li>
 * </ol>
 *
 * <p>Crash safety: a node that dies mid-tick leaves its claimed rows
 * with a token and a future {@code next_attempt_at}; once the lease
 * expires they are due again and the next tick re-claims them. No
 * reclaim sweep is needed. Delivery is at-least-once: a delivery that
 * completed but whose record transaction was lost is repeated after
 * the lease.
 *
 * <p>An optional {@link OutboxDrainerListener} observes the tick and
 * every row transition (metrics, dead-letter alerting). Row callbacks
 * run inside the row's record transaction; listener failures are
 * logged and never affect the transition.
 */
public class OutboxDrainerTask implements ScheduledTask {

    private static final Logger log = Logger.getLogger(OutboxDrainerTask.class);

    public static final String TASK_NAME_PREFIX = "outbox-drainer:";

    protected final OutboxConfig config;
    protected final OutboxDeliveryHandler handler;
    protected final Function<KeycloakSession, OutboxStore> storeFactory;
    protected final OutboxDrainerListener listener;

    /**
     * Uses the handler as {@link OutboxDrainerListener} if it
     * implements that interface, otherwise no listener.
     */
    public OutboxDrainerTask(OutboxConfig config,
                             OutboxDeliveryHandler handler,
                             Function<KeycloakSession, OutboxStore> storeFactory) {
        this(config, handler, storeFactory,
                handler instanceof OutboxDrainerListener l ? l : OutboxDrainerListener.NOOP);
    }

    public OutboxDrainerTask(OutboxConfig config,
                             OutboxDeliveryHandler handler,
                             Function<KeycloakSession, OutboxStore> storeFactory,
                             OutboxDrainerListener listener) {
        this.config = Objects.requireNonNull(config, "config");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.storeFactory = Objects.requireNonNull(storeFactory, "storeFactory");
        this.listener = listener == null ? OutboxDrainerListener.NOOP : listener;
        if (!Objects.equals(config.entryKind(), handler.entryKind())) {
            throw new IllegalArgumentException(
                    "config.entryKind=" + config.entryKind()
                            + " does not match handler.entryKind=" + handler.entryKind());
        }
    }

    /**
     * Unique per {@code entryKind}; used as the cluster lock key and
     * as the timer / tracing name.
     */
    @Override
    public String getTaskName() {
        return TASK_NAME_PREFIX + config.entryKind();
    }

    @Override
    public void run(KeycloakSession session) {
        KeycloakSessionFactory factory = session.getKeycloakSessionFactory();
        KeycloakSession previous = KeycloakSessionUtil.getKeycloakSession();
        KeycloakSessionUtil.setKeycloakSession(session);
        Instant tickStart = Instant.now();
        TickCounters counters = new TickCounters();
        notify(() -> listener.onTickStart(session, config.entryKind()));
        try {
            drain(factory, tickStart, counters);
            housekeeping(factory, counters);
        } finally {
            KeycloakSessionUtil.setKeycloakSession(previous);
            OutboxDrainerTickSummary summary = counters.summary(config.entryKind(),
                    Duration.between(tickStart, Instant.now()));
            notify(() -> listener.onTickEnd(session, summary));
        }
    }

    protected void drain(KeycloakSessionFactory factory, Instant tickStart, TickCounters counters) {
        String token = UUID.randomUUID().toString();
        List<OutboxEntryEntity> claimed = KeycloakModelUtils.runJobInTransactionWithResult(factory,
                s -> claimBatch(storeFactory.apply(s), token));
        counters.claimed = claimed.size();
        if (claimed.isEmpty()) {
            return;
        }
        log.debugf("Outbox drainer claimed %d due row(s) for entryKind=%s token=%s", claimed.size(), config.entryKind(), token);

        Instant deadline = config.tickBudget() == null ? null : tickStart.plus(config.tickBudget());
        int next = 0;
        for (; next < claimed.size(); next++) {
            if (deadline != null && !Instant.now().isBefore(deadline)) {
                break;
            }
            processClaimed(factory, claimed.get(next), token, counters);
        }
        if (next < claimed.size()) {
            releaseUnprocessed(factory, claimed.subList(next, claimed.size()), token, counters);
        }
    }

    protected List<OutboxEntryEntity> claimBatch(OutboxStore store, String token) {
        if (config.perOwnerBatchSize() == null) {
            return store.claimDueForDrain(config.entryKind(), config.batchSize(), token, config.claimLease());
        }
        return claimOwnerFair(store, token);
    }

    /**
     * Owner-fair claim: visits the owners that have due rows, oldest
     * waiting first, and takes at most {@code perOwnerBatchSize} rows
     * from each per round. Rounds repeat over the owners that still
     * had a full share until the batch is full or no owner has due
     * rows left, so a batch with few active owners is still filled.
     * The resulting list interleaves owners in round order, which also
     * spreads a budget-limited tick's deliveries across owners.
     */
    protected List<OutboxEntryEntity> claimOwnerFair(OutboxStore store, String token) {
        int batchSize = config.batchSize();
        int perOwner = Math.min(config.perOwnerBatchSize(), batchSize);
        List<String> owners = store.findOwnersWithDueRows(config.entryKind(), batchSize);
        List<OutboxEntryEntity> claimed = new ArrayList<>(batchSize);
        List<String> candidates = owners;
        while (!candidates.isEmpty() && claimed.size() < batchSize) {
            List<String> stillDue = new ArrayList<>(candidates.size());
            for (String owner : candidates) {
                int remaining = batchSize - claimed.size();
                if (remaining <= 0) {
                    break;
                }
                int limit = Math.min(perOwner, remaining);
                List<OutboxEntryEntity> rows = store.claimDueForOwner(config.entryKind(), owner, limit, token, config.claimLease());
                claimed.addAll(rows);
                if (rows.size() == limit) {
                    // Took a full share: the owner may have more.
                    stillDue.add(owner);
                }
            }
            candidates = stillDue;
        }
        if (!claimed.isEmpty()) {
            log.debugf("Outbox owner-fair claim took %d row(s) across %d owner(s) for entryKind=%s", claimed.size(), owners.size(), config.entryKind());
        }
        return claimed;
    }

    /**
     * Delivers one claimed row and records the outcome, each in its
     * own transaction. Never throws: a failure to record leaves the
     * row leased, and the lease expiry turns it into a retry.
     */
    protected void processClaimed(KeycloakSessionFactory factory, OutboxEntryEntity claimedRow, String token,
                                  TickCounters counters) {
        counters.processed++;
        String id = claimedRow.getId();

        OutboxDeliveryResult result = deliverInOwnTransaction(factory, id, token);
        if (result == null) {
            counters.claimLost++;
            log.infof("Outbox claim lost before delivery — skipping. id=%s entryKind=%s token=%s",
                    id, config.entryKind(), token);
            return;
        }

        try {
            KeycloakModelUtils.runJobInTransaction(factory, s -> {
                OutboxStore store = storeFactory.apply(s);
                OutboxEntryEntity row = store.findClaimed(id, token);
                if (row == null) {
                    counters.claimLost++;
                    log.warnf("Outbox claim lost after delivery — outcome %s not recorded, row will be retried by its new holder. id=%s entryKind=%s token=%s",
                            result.outcome(), id, config.entryKind(), token);
                    return;
                }
                applyResult(s, store, row, result, counters);
            });
        } catch (RuntimeException e) {
            log.warnf(e, "Outbox failed to record outcome %s — row stays leased and is retried after the lease. id=%s entryKind=%s",
                    result.outcome(), id, config.entryKind());
        }
    }

    /**
     * Runs the handler for the row in a fresh transaction. Returns
     * {@code null} if the row no longer carries the tick's token.
     * The handler's result is kept even if that transaction fails to
     * commit afterwards (the delivery did happen; only handler-side
     * database writes, if any, were lost).
     */
    protected OutboxDeliveryResult deliverInOwnTransaction(KeycloakSessionFactory factory, String id, String token) {
        OutboxDeliveryResult[] holder = new OutboxDeliveryResult[1];
        boolean[] claimLost = new boolean[1];
        try {
            KeycloakModelUtils.runJobInTransaction(factory, s -> {
                OutboxEntryEntity row = storeFactory.apply(s).findById(id);
                if (row == null || !token.equals(row.getClaimToken())) {
                    claimLost[0] = true;
                    return;
                }
                holder[0] = deliverSafely(s, row);
            });
        } catch (RuntimeException e) {
            if (holder[0] != null) {
                log.warnf(e, "Outbox delivery transaction failed after the handler returned %s — keeping the result. id=%s entryKind=%s",
                        holder[0].outcome(), id, config.entryKind());
            } else {
                log.warnf(e, "Outbox delivery transaction failed — treating as RETRY. id=%s entryKind=%s", id, config.entryKind());
                holder[0] = OutboxDeliveryResult.retry(describe(e));
            }
        }
        if (claimLost[0]) {
            return null;
        }
        return holder[0];
    }

    /**
     * Invokes the handler, mapping a {@code null} result or an
     * uncaught exception to {@link OutboxDeliveryResult#retry}.
     */
    protected OutboxDeliveryResult deliverSafely(KeycloakSession session, OutboxEntryEntity row) {
        try {
            OutboxDeliveryResult result = handler.deliver(session, row);
            return result != null ? result : OutboxDeliveryResult.retry("delivery handler returned null result");
        } catch (RuntimeException e) {
            log.warnf(e, "Outbox delivery handler threw — treating as RETRY. id=%s entryKind=%s correlationId=%s",
                    row.getId(), row.getEntryKind(), row.getCorrelationId());
            return OutboxDeliveryResult.retry(describe(e));
        }
    }

    protected static String describe(RuntimeException e) {
        return e.getMessage() == null
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    protected void applyResult(KeycloakSession session, OutboxStore store, OutboxEntryEntity row,
                               OutboxDeliveryResult result, TickCounters counters) {
        switch (result.outcome()) {
            case DELIVERED -> {
                store.markDelivered(row);
                counters.delivered++;
                log.debugf("Outbox delivered. id=%s entryKind=%s correlationId=%s attempts=%d",
                        row.getId(), row.getEntryKind(), row.getCorrelationId(), row.getAttempts());
                notify(() -> listener.onDelivered(session, row));
            }
            case RETRY -> handleRetry(session, store, row, result.errorMessage(), counters);
            case DEFER -> {
                store.deferUntil(row, result.notBefore(), result.errorMessage());
                counters.deferred++;
                log.debugf("Outbox deferred without counting an attempt. id=%s entryKind=%s correlationId=%s notBefore=%s reason=%s",
                        row.getId(), row.getEntryKind(), row.getCorrelationId(), result.notBefore(), result.errorMessage());
                notify(() -> listener.onDeferred(session, row, row.getNextAttemptAt(), result.errorMessage()));
            }
            case DEAD_LETTER -> {
                String reason = result.errorMessage() != null ? result.errorMessage()
                        : "handler returned DEAD_LETTER (attempt " + (row.getAttempts() + 1) + ")";
                deadLetter(session, store, row, OutboxDrainerListener.DeadLetterCause.HANDLER, reason, counters);
                log.warnf("Outbox dead-lettered by handler. id=%s entryKind=%s correlationId=%s reason=%s",
                        row.getId(), row.getEntryKind(), row.getCorrelationId(), reason);
            }
            case ORPHANED -> {
                String reason = result.errorMessage() != null ? result.errorMessage()
                        : "handler returned ORPHANED (destination no longer exists)";
                deadLetter(session, store, row, OutboxDrainerListener.DeadLetterCause.ORPHANED, reason, counters);
                log.warnf("Outbox dead-lettered as orphan. id=%s entryKind=%s correlationId=%s",
                        row.getId(), row.getEntryKind(), row.getCorrelationId());
            }
        }
    }

    protected void handleRetry(KeycloakSession session, OutboxStore store, OutboxEntryEntity row,
                               String errorMessage, TickCounters counters) {
        int nextAttempts = row.getAttempts() + 1;
        String reason = errorMessage != null ? errorMessage : "delivery failed";
        if (config.backoff().isExhausted(nextAttempts)) {
            log.warnf("Outbox dead-lettered after %d attempts. id=%s entryKind=%s correlationId=%s",
                    nextAttempts, row.getId(), row.getEntryKind(), row.getCorrelationId());
            deadLetter(session, store, row, OutboxDrainerListener.DeadLetterCause.ATTEMPTS_EXHAUSTED, reason, counters);
            return;
        }
        Instant nextAttemptAt = config.backoff().computeNextAttemptAt(Instant.now(), nextAttempts);
        log.debugf("Outbox scheduling retry. id=%s attempts=%d nextAttemptAt=%s",
                row.getId(), nextAttempts, nextAttemptAt);
        store.recordFailure(row, nextAttemptAt, reason);
        counters.retried++;
        notify(() -> listener.onRetryScheduled(session, row, nextAttemptAt, reason));
    }

    protected void deadLetter(KeycloakSession session, OutboxStore store, OutboxEntryEntity row,
                              OutboxDrainerListener.DeadLetterCause cause, String reason, TickCounters counters) {
        store.markDeadLetter(row, reason);
        counters.deadLettered++;
        notify(() -> listener.onDeadLetter(session, row, cause, reason));
    }

    protected void releaseUnprocessed(KeycloakSessionFactory factory, List<OutboxEntryEntity> unprocessed, String token,
                                      TickCounters counters) {
        List<String> ids = new ArrayList<>(unprocessed.size());
        for (OutboxEntryEntity row : unprocessed) {
            ids.add(row.getId());
        }
        try {
            KeycloakModelUtils.runJobInTransaction(factory,
                    s -> counters.released = storeFactory.apply(s).releaseClaims(ids, token));
            log.infof("Outbox tick budget %s exhausted for entryKind=%s — released %d of %d claimed row(s) for the next tick",
                    config.tickBudget(), config.entryKind(), counters.released, ids.size());
        } catch (RuntimeException e) {
            log.warnf(e, "Outbox failed to release %d unprocessed claimed row(s) — they become due again after the lease. entryKind=%s",
                    ids.size(), config.entryKind());
        }
    }

    protected void housekeeping(KeycloakSessionFactory factory, TickCounters counters) {
        try {
            KeycloakModelUtils.runJobInTransaction(factory, s -> {
                OutboxStore store = storeFactory.apply(s);
                counters.stalePromoted = promoteStaleQueuedToDeadLetter(store);
                counters.purgedDelivered = purgeDeliveredOlderThanRetention(store);
                counters.purgedDeadLetter = purgeDeadLetterOlderThanRetention(store);
            });
        } catch (RuntimeException e) {
            log.warnf(e, "Outbox housekeeping failed for entryKind=%s — retried on the next tick", config.entryKind());
        }
    }

    /**
     * Runs a listener callback, logging and swallowing any failure so
     * an observer bug cannot change a row's transition or abort the
     * tick.
     */
    protected void notify(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException e) {
            log.warnf(e, "Outbox drainer listener threw for entryKind=%s — ignoring", config.entryKind());
        }
    }

    protected int promoteStaleQueuedToDeadLetter(OutboxStore store) {
        Duration pendingMaxAge = config.pendingMaxAge();
        if (pendingMaxAge == null || pendingMaxAge.isZero() || pendingMaxAge.isNegative()) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(pendingMaxAge);
        int promoted = store.promoteStaleQueuedToDeadLetter(config.entryKind(), cutoff,
                "queued exceeded pendingMaxAge");
        if (promoted > 0) {
            log.infof("Outbox promoted %d stale queued row(s) to DEAD_LETTER (entryKind=%s, pendingMaxAge=%s)",
                    promoted, config.entryKind(), pendingMaxAge);
        }
        return promoted;
    }

    protected int purgeDeliveredOlderThanRetention(OutboxStore store) {
        Duration retention = config.deliveredRetention();
        if (retention == null || retention.isZero() || retention.isNegative()) {
            return 0;
        }
        return store.purgeDeliveredOlderThan(config.entryKind(), Instant.now().minus(retention));
    }

    protected int purgeDeadLetterOlderThanRetention(OutboxStore store) {
        Duration retention = config.deadLetterRetention();
        if (retention == null || retention.isZero() || retention.isNegative()) {
            return 0;
        }
        return store.purgeDeadLetterOlderThan(config.entryKind(), Instant.now().minus(retention));
    }

    /** Mutable per-tick counters, folded into an {@link OutboxDrainerTickSummary} at tick end. */
    protected static class TickCounters {
        int claimed;
        int processed;
        int delivered;
        int retried;
        int deferred;
        int deadLettered;
        int released;
        int claimLost;
        int stalePromoted;
        int purgedDelivered;
        int purgedDeadLetter;

        OutboxDrainerTickSummary summary(String entryKind, Duration duration) {
            return new OutboxDrainerTickSummary(entryKind, claimed, processed, delivered, retried, deferred,
                    deadLettered, released, claimLost, stalePromoted, purgedDelivered, purgedDeadLetter, duration);
        }
    }
}
