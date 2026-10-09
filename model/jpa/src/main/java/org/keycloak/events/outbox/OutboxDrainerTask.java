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
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.jpa.entities.OutboxEntryEntity;
import org.keycloak.timer.ScheduledTask;
import org.keycloak.utils.KeycloakSessionUtil;

import org.jboss.logging.Logger;

/**
 * Drains the generic outbox for one registered {@code entryKind}: locks
 * due PENDING rows, hands them off to the kind's
 * {@link OutboxDeliveryHandler}, and transitions each row based on the
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
 * <p>Concurrency within a single tick is cheap because rows are locked
 * {@code PESSIMISTIC_WRITE} via {@code FOR UPDATE SKIP LOCKED} by the
 * store, and each row is transitioned (DELIVERED / back to PENDING with
 * a future {@code next_attempt_at}, with or without an attempt counted /
 * DEAD_LETTER) before the transaction commits.
 *
 * <p>Per-tick housekeeping after the drain pass:
 * <ul>
 *   <li>Promote rows whose {@code createdAt} is older than
 *       {@link OutboxConfig#pendingMaxAge()} to DEAD_LETTER (backstop
 *       so stuck rows can't sit forever).</li>
 *   <li>Purge DELIVERED rows past {@link OutboxConfig#deliveredRetention()}.</li>
 *   <li>Purge DEAD_LETTER rows past {@link OutboxConfig#deadLetterRetention()}.</li>
 * </ul>
 *
 * <p>An optional {@link OutboxDrainerListener} observes the tick and
 * every row transition (metrics, dead-letter alerting). Listener
 * failures are logged and never affect the transition.
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
        // Publish the drainer's KeycloakSession into the thread-local
        // so handler collaborators that haven't been refactored to
        // take an explicit session parameter still resolve correctly.
        KeycloakSession previous = KeycloakSessionUtil.getKeycloakSession();
        KeycloakSessionUtil.setKeycloakSession(session);
        Instant tickStart = Instant.now();
        TickCounters counters = new TickCounters();
        notify(() -> listener.onTickStart(session, config.entryKind()));
        try {
            OutboxStore store = storeFactory.apply(session);
            drain(session, store, counters);
            counters.stalePromoted = promoteStaleQueuedToDeadLetter(store);
            counters.purgedDelivered = purgeDeliveredOlderThanRetention(store);
            counters.purgedDeadLetter = purgeDeadLetterOlderThanRetention(store);
        } finally {
            KeycloakSessionUtil.setKeycloakSession(previous);
            OutboxDrainerTickSummary summary = counters.summary(config.entryKind(),
                    Duration.between(tickStart, Instant.now()));
            notify(() -> listener.onTickEnd(session, summary));
        }
    }

    protected void drain(KeycloakSession session, OutboxStore store, TickCounters counters) {
        List<OutboxEntryEntity> due = store.lockDueForDrain(config.entryKind(), config.batchSize());
        if (due.isEmpty()) {
            return;
        }
        log.debugf("Outbox drainer processing %d due row(s) for entryKind=%s", due.size(), config.entryKind());
        for (OutboxEntryEntity row : due) {
            processOne(session, store, row, counters);
        }
    }

    protected void processOne(KeycloakSession session, OutboxStore store, OutboxEntryEntity row, TickCounters counters) {
        counters.processed++;
        OutboxDeliveryResult result = deliverSafely(session, row);

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
            String message = e.getMessage() == null
                    ? e.getClass().getSimpleName()
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            return OutboxDeliveryResult.retry(message);
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
        int processed;
        int delivered;
        int retried;
        int deferred;
        int deadLettered;
        int stalePromoted;
        int purgedDelivered;
        int purgedDeadLetter;

        OutboxDrainerTickSummary summary(String entryKind, Duration duration) {
            return new OutboxDrainerTickSummary(entryKind, processed, delivered, retried, deferred,
                    deadLettered, stalePromoted, purgedDelivered, purgedDeadLetter, duration);
        }
    }
}
