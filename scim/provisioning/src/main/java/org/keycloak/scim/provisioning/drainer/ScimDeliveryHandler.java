package org.keycloak.scim.provisioning.drainer;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.keycloak.events.outbox.OutboxDelivery;
import org.keycloak.events.outbox.OutboxDeliveryHandler;
import org.keycloak.events.outbox.OutboxDeliveryOutcome;
import org.keycloak.events.outbox.OutboxDeliveryResult;
import org.keycloak.events.outbox.OutboxDrainerListener;
import org.keycloak.events.outbox.OutboxDrainerTickSummary;
import org.keycloak.events.outbox.OutboxEntry;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.scim.provisioning.Realms;
import org.keycloak.scim.provisioning.Settings;
import org.keycloak.scim.provisioning.client.Endpoints;
import org.keycloak.scim.provisioning.client.ErrorClass;
import org.keycloak.scim.provisioning.client.ScimEndpoint;
import org.keycloak.scim.provisioning.client.ScimException;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.component.Destinations;
import org.keycloak.scim.provisioning.component.Tokens;
import org.keycloak.scim.provisioning.delivery.Backoff;
import org.keycloak.scim.provisioning.delivery.DeliveryResult;
import org.keycloak.scim.provisioning.delivery.GroupDelivery;
import org.keycloak.scim.provisioning.delivery.UserDelivery;
import org.keycloak.scim.provisioning.mapping.Mapping;
import org.keycloak.scim.provisioning.mapping.MappingException;
import org.keycloak.scim.provisioning.metrics.Meters;
import org.keycloak.scim.provisioning.model.DestinationHealthModel;
import org.keycloak.scim.provisioning.model.OutboxEntryModel;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;
import org.keycloak.scim.provisioning.planner.DesiredState;
import org.keycloak.scim.provisioning.planner.Plan;
import org.keycloak.scim.provisioning.planner.ScopeEvaluator;
import org.keycloak.scim.provisioning.planner.Subjects;
import org.keycloak.scim.resource.user.User;
import org.keycloak.util.JsonSerialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.logging.Logger;

/**
 * Delivers one of our outbox rows for Keycloak's {@code OutboxDrainerTask}, which claims the rows
 * under a lease and, per row, calls {@link #prepare} in a short transaction, runs the returned
 * delivery outside any transaction, and records the result (delivered, retry, defer, dead letter)
 * in a transaction of its own.
 *
 * <ol>
 *   <li><b>prepare</b> (the drainer's transaction): the destination and its health; a row whose
 *       subject has a newer pending row settles here without a delivery; resolve the token and
 *       compute the desired state. Nothing of ours is written here but the health row.
 *   <li><b>delivery</b> (no session, no transaction): only HTTP, on plain values captured in
 *       prepare.
 *   <li><b>record</b> (the drainer's record transaction): what the outcome writes — link, health,
 *       error class, deferral count, a forced deprovision carried over, and on a settled row the
 *       pruning of the subject's other settled rows — is the result's effect ({@code
 *       OutboxDeliveryResult.withEffect}), so it commits with the row's transition or not at all.
 *       Every effect is idempotent: a failing one rolls the record back and the row is delivered
 *       again after its lease.
 * </ol>
 *
 * <p>The two failure regimes stay separate. A transient failure backs the whole destination off on
 * its health row and defers the row to the end of the backoff without spending an attempt; rows of
 * a backed-off destination are deferred the same way without an HTTP call. A refusal of this one
 * change is a retry, on Keycloak's backoff curve, until the destination's {@code maxAttempts}, then
 * a dead letter.
 */
public final class ScimDeliveryHandler implements OutboxDeliveryHandler, OutboxDrainerListener {
    private static final Logger log = Logger.getLogger(ScimDeliveryHandler.class);


    private final Settings settings;
    private final Clock clock = Clock.systemUTC();
    private final Backoff transientBackoff;

    private final AtomicBoolean more = new AtomicBoolean();

    public ScimDeliveryHandler(Settings settings) {
        this.settings = settings;
        this.transientBackoff = new Backoff(settings.transientBase(), settings.transientMax());
    }

    @Override
    public String entryKind() {
        return ScimProvisioningProvider.ENTRY_KIND;
    }

    /**
     * Whether the tick that just ran filled its batch or ran out of budget: drain again at once.
     * Reading it resets it, so a tick that did not run (another node held the lock) reads false.
     */
    boolean takeMore() {
        return more.getAndSet(false);
    }

    @Override
    public void onTickEnd(KeycloakSession session, OutboxDrainerTickSummary summary) {
        more.set(summary.claimed() >= settings.drainBatch() || summary.released() > 0);
    }

    /**
     * {@code r} with what its outcome writes in the record transaction: {@code effect}, then, on a
     * delivered row, the forced deprovision, deferral count and error class it carried are cleared,
     * and on a settled row (delivered or dead) the subject's other settled rows are removed, so this
     * row is the subject's only settled row.
     */
    private OutboxDeliveryResult with(
            OutboxEntry row, OutboxDeliveryResult r, Consumer<KeycloakSession> effect) {
        OutboxDeliveryOutcome outcome = r.outcome();
        boolean delivered = outcome == OutboxDeliveryOutcome.DELIVERED;
        boolean settles =
                delivered
                        || outcome == OutboxDeliveryOutcome.DEAD_LETTER
                        || outcome == OutboxDeliveryOutcome.ORPHANED;
        if (!settles) return r.withEffect(effect);
        String id = row.getId();
        return r.withEffect(
                s -> {
                    if (effect != null) effect.accept(s);
                    ScimProvisioningProvider outbox = s.getProvider(ScimProvisioningProvider.class);
                    if (delivered) outbox.clearOnDelivery(id, clock.millis());
                    outbox.pruneSettled(outbox.toModel(row));
                });
    }

    @Override
    public OutboxDelivery prepare(KeycloakSession s, OutboxEntry row) {
        String realmId = row.getRealmId();
        String destinationId = row.getOwnerId();
        RealmModel realm = Realms.bind(s, realmId);
        Destination d = realm == null ? null : Destinations.find(realm, destinationId);
        if (d == null) return settled(with(row, OutboxDeliveryResult.orphaned("destination removed"), null));
        if (!d.enabled())
            return settled(OutboxDeliveryResult.defer(settings.sweepInterval(), row.getLastError()));
        ScimProvisioningProvider outbox = s.getProvider(ScimProvisioningProvider.class);
        long now = clock.millis();
        outbox.ensureHealth(realmId, destinationId, now);
        DestinationHealthModel h = outbox.getHealth(realmId, destinationId).orElseThrow();
        if (h.isBackedOff(now)) return settled(backedOff(h, row));
        OutboxEntryModel entry = outbox.toModel(row);
        if (outbox.isSuperseded(entry)) {
            // carried over in the record transaction, with the transition
            return settled(
                    with(
                            row,
                            OutboxDeliveryResult.delivered(),
                            rs -> rs.getProvider(ScimProvisioningProvider.class).supersede(entry, clock.millis())));
        }
        int failures = h.getConsecutiveFailures();
        String token = Tokens.resolve(s, d.tokenRef()).orElse(null);
        if (token == null) {
            return settled(
                    transientFailure(
                            d,
                            failures,
                            row,
                            new ScimException(
                                    ErrorClass.MISCONFIGURED, "the token's vault reference cannot be resolved")));
        }
        Mapping mapping;
        try {
            mapping = d.mapping();
        } catch (MappingException e) {
            return settled(
                    transientFailure(
                            d,
                            failures,
                            row,
                            new ScimException(ErrorClass.MISCONFIGURED, "invalid mapping: " + e.getMessage())));
        }
        Plan plan;
        try {
            plan = new DesiredState(s, realm, d, mapping).plan(entry);
        } catch (RuntimeException e) {
            log.warnf(
                    "scim-provisioning: cannot plan %s for %s: %s", entry.getSubject(), d.name(), e.toString());
            log.debug("planning failure", e);
            return settled(
                    refused(
                            d,
                            row,
                            entry,
                            null,
                            new ScimException(ErrorClass.PROTOCOL, "internal error while planning")));
        }
        ScimEndpoint client = Endpoints.create(s, d.baseUrl(), () -> token, d.requestTimeout());
        return () -> deliver(d, failures, row, entry, plan, client);
    }

    private static OutboxDelivery settled(OutboxDeliveryResult r) {
        return OutboxDelivery.settled(r);
    }

    /** Waits out the destination's backoff; no attempt is spent and the row's last error is kept. */
    private static OutboxDeliveryResult backedOff(DestinationHealthModel h, OutboxEntry row) {
        return OutboxDeliveryResult.defer(Instant.ofEpochMilli(h.getRetryAt()), row.getLastError());
    }

    /** The delivery: HTTP only, no session and no transaction. */
    private OutboxDeliveryResult deliver(
            Destination d,
            int failures,
            OutboxEntry row,
            OutboxEntryModel entry,
            Plan plan,
            ScimEndpoint client) {
        String realmId = entry.getRealmId();
        long started = System.nanoTime();
        DeliveryResult result;
        try {
            result =
                    deliver(
                            plan,
                            new UserDelivery(client, clock),
                            new GroupDelivery(client, clock, settings.groupDeferral()));
        } catch (ScimException e) {
            ScimException effective = probeForbidden(client, e);
            Meters.failed(realmId, d, effective.errorClass());
            if (effective.isTransient()) return transientFailure(d, failures, row, effective);
            return refused(d, row, entry, plan, effective);
        } catch (RuntimeException e) {
            log.warnf(
                    "scim-provisioning: delivery of %s to %s failed unexpectedly: %s",
                    entry.getSubject(), d.name(), e.toString());
            log.debug("delivery failure", e);
            return refused(
                    d,
                    row,
                    entry,
                    plan,
                    new ScimException(ErrorClass.PROTOCOL, "internal error during delivery"));
        }
        Meters.deliverySeconds(realmId, d, System.nanoTime() - started);
        if (result instanceof DeliveryResult.Deferred deferred) {
            log.debugf(
                    "scim-provisioning: %s deferred for %s: %s", entry.getSubject(), d.name(), deferred.reason());
            return with(
                    row,
                    OutboxDeliveryResult.defer(deferred.delay(), entry.getLastError()),
                    s -> s.getProvider(ScimProvisioningProvider.class).noteDeferral(entry, clock.millis()));
        }
        return with(row, OutboxDeliveryResult.delivered(), s -> landed(s, d, entry, plan, result));
    }

    private DeliveryResult deliver(Plan plan, UserDelivery users, GroupDelivery groups)
            throws ScimException {
        if (plan instanceof Plan.UpsertUser u) {
            return users.upsert(u.subject(), toUser(u.resource()), u.hash(), u.link());
        }
        if (plan instanceof Plan.DeprovisionUser u) {
            return users.deprovision(u.subject(), u.userName(), u.link(), u.mode(), u.forced());
        }
        if (plan instanceof Plan.DeliverGroup g) {
            return groups.deliver(
                    g.subject(),
                    g.displayName(),
                    g.externalId(),
                    g.members(),
                    g.link(),
                    g.deferrals(),
                    settings.maxGroupDeferrals());
        }
        return new DeliveryResult.Skipped(((Plan.Nothing) plan).reason());
    }

    private static User toUser(ObjectNode resource) {
        try {
            return JsonSerialization.mapper.treeToValue(resource, User.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("the rendered user is not a SCIM User", e);
        }
    }

    /**
     * A 403 is sometimes about one resource, not the token: if the connection check passes, treat it
     * as a rejection of this entry.
     */
    private static ScimException probeForbidden(ScimEndpoint client, ScimException e) {
        if (e.status() != 403) return e;
        try {
            client.check();
            return new ScimException(ErrorClass.REJECTED, 403, e.detail(), e.scimType(), null);
        } catch (ScimException probe) {
            return e;
        }
    }

    /** In the record transaction: what a successful (or skipped) delivery writes. */
    private void landed(
            KeycloakSession s, Destination d, OutboxEntryModel entry, Plan plan, DeliveryResult r) {
        String realmId = entry.getRealmId();
        String destinationId = entry.getDestinationId();
        RealmModel realm = Realms.bind(s, realmId);
        if (realm == null || Destinations.find(realm, destinationId) == null) return; // removed
        String subject = entry.getSubject();
        if (r instanceof DeliveryResult.Delivered dl) {
            ScimProvisioningProvider outbox = s.getProvider(ScimProvisioningProvider.class);
            long now = clock.millis();
            outbox.saveLink(realmId, destinationId, dl.link());
            outbox.recordSuccess(realmId, destinationId, now);
            if (outbox.clearFailing(realmId, destinationId) > 0) {
                log.warnf("scim-provisioning: destination %s (%s) has recovered", d.name(), destinationId);
            }
            Meters.delivered(realmId, d);
            if (plan instanceof Plan.UpsertUser u) remarkGroupsOnLanding(s, realm, d, u, now);
            log.debugf("scim-provisioning: delivered %s to %s", subject, d.name());
        } else {
            log.debugf(
                    "scim-provisioning: %s skipped for %s: %s",
                    subject, d.name(), ((DeliveryResult.Skipped) r).reason());
        }
    }

    /**
     * When a user's account first lands, or its {@code active} flag changes, the scoped groups it
     * belongs to are marked again: a group waiting for the member picks it up (bounded group
     * deferral), and groups carry only enabled members.
     */
    private void remarkGroupsOnLanding(
            KeycloakSession s, RealmModel realm, Destination d, Plan.UpsertUser u, long now) {
        if (!d.supportsGroups() || u.groupIds().isEmpty()) return;
        boolean active = u.resource().path("active").asBoolean(true);
        boolean landed = u.link().map(l -> !l.isLive() || l.active() != active).orElse(true);
        if (!landed) return;
        ScopeEvaluator scope = ScopeEvaluator.of(s, realm, d);
        ScimProvisioningProvider outbox = s.getProvider(ScimProvisioningProvider.class);
        for (String gid : u.groupIds()) {
            GroupModel g = s.groups().getGroupById(realm, gid);
            if (g != null && scope.withinScopedGroup(g)) {
                outbox.mark(realm.getId(), d.id(), Subjects.group(gid), false, now);
            }
        }
    }

    /**
     * Backs the destination off (in the record transaction); the row waits for the end of the
     * backoff, no attempt spent.
     */
    private OutboxDeliveryResult transientFailure(
            Destination d, int failures, OutboxEntry row, ScimException e) {
        String realmId = row.getRealmId();
        String destinationId = row.getOwnerId();
        long now = clock.millis();
        long retryAt = now + transientBackoff.delay(failures + 1, e.retryAfter()).toMillis();
        return with(
                row,
                OutboxDeliveryResult.defer(Instant.ofEpochMilli(retryAt), row.getLastError()),
                s -> {
                    ScimProvisioningProvider outbox = s.getProvider(ScimProvisioningProvider.class);
                    outbox.recordTransient(
                            realmId, destinationId, retryAt, e.errorClass().code(), e.storedText(), now);
                    if (outbox.startFailing(realmId, destinationId, now) > 0) {
                        log.warnf(
                                "scim-provisioning: destination %s (%s) is failing: %s; backing off, nothing is lost",
                                d.name(), destinationId, e.summary());
                    } else {
                        log.debugf("scim-provisioning: destination %s still failing: %s", d.name(), e.summary());
                    }
                });
    }

    /**
     * A refusal of this one change: a retry, or a dead letter once the destination's {@code
     * maxAttempts} are used. The reason (the stored text, truncated) goes to the row's last error;
     * the drainer logs it at DEBUG only, and our WARN carries the summary (class and status).
     */
    private OutboxDeliveryResult refused(
            Destination d, OutboxEntry row, OutboxEntryModel entry, Plan plan, ScimException e) {
        String realmId = entry.getRealmId();
        String destinationId = entry.getDestinationId();
        int attempts = entry.getAttempts() + 1;
        String subject = entry.getSubject();
        Consumer<KeycloakSession> note =
                s -> {
                    ScimProvisioningProvider outbox = s.getProvider(ScimProvisioningProvider.class);
                    outbox.recordProblem(realmId, destinationId, e.errorClass().code(), e.storedText());
                    outbox.noteError(entry, e.errorClass().code(), clock.millis());
                };
        log.debugf(
                "scim-provisioning: %s refused by %s (attempt %d): %s",
                subject, d.name(), attempts, e.getMessage());
        if (attempts < d.maxAttempts())
            return with(row, OutboxDeliveryResult.retry(e.storedText()), note);
        Meters.dead(realmId, d, plan instanceof Plan.DeprovisionUser);
        if (plan instanceof Plan.DeprovisionUser) {
            log.warnf(
                    "scim-provisioning: DEPROVISION of %s dead-lettered by %s after %d attempts (%s); the person"
                            + " may still have access there",
                    subject, d.name(), attempts, e.summary());
        } else {
            log.warnf(
                    "scim-provisioning: %s dead-lettered by %s after %d attempts (%s)",
                    subject, d.name(), attempts, e.summary());
        }
        return with(row, OutboxDeliveryResult.deadLetter(e.storedText()), note);
    }
}
