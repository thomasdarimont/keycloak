package org.keycloak.scim.provisioning.reconcile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.keycloak.cluster.ClusterProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.scim.provisioning.Realms;
import org.keycloak.scim.provisioning.Settings;
import org.keycloak.scim.provisioning.client.Endpoints;
import org.keycloak.scim.provisioning.client.ErrorClass;
import org.keycloak.scim.provisioning.client.ListPage;
import org.keycloak.scim.provisioning.client.ScimEndpoint;
import org.keycloak.scim.provisioning.client.ScimException;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.component.Destinations;
import org.keycloak.scim.provisioning.component.Tokens;
import org.keycloak.scim.provisioning.delivery.LinkRecord;
import org.keycloak.scim.provisioning.drainer.DrainScheduler;
import org.keycloak.scim.provisioning.mapping.Mapping;
import org.keycloak.scim.provisioning.metrics.Meters;
import org.keycloak.scim.provisioning.model.ScimLinkModel;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;
import org.keycloak.scim.provisioning.planner.Subjects;
import org.keycloak.scim.provisioning.planner.UserRenderer;
import org.keycloak.scim.resource.user.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.logging.Logger;

/**
 * Compares a destination's accounts with the users in scope and
 * marks what needs correcting. It detects existence and {@code active} drift, not attribute
 * drift.
 *
 * <table>
 *   <tr><th>Finding</th><th>Meaning</th><th>Action</th></tr>
 *   <tr><td>{@code missing}</td><td>in scope, no account there</td><td>forget a stale link, mark</td></tr>
 *   <tr><td>{@code linked}</td><td>an account we did not know about (by {@code externalId}, else
 *       {@code userName} ignoring case)</td><td>record the link without a hash, mark</td></tr>
 *   <tr><td>{@code mismatched}</td><td>{@code active} differs from what it should be</td><td>clear
 *       the hash (and lift a tombstone), mark</td></tr>
 *   <tr><td>{@code unmanaged}</td><td>an account that matches nobody in scope</td><td>count only</td></tr>
 * </table>
 *
 * <p>Clearing the hash matters: an unchanged user hashes to what was last delivered, so without it
 * the corrective delivery would be skipped. Runs under the cluster lock {@code
 * scim-provisioning:reconcile:<destination>}; a failed run is recorded on the health row, not thrown. No
 * transaction is open while the destination is paged.
 */
public final class Reconciler {
    private static final Logger log = Logger.getLogger(Reconciler.class);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int PAGE = 100;
    private static final int MAX_ACCOUNTS = 1_000_000;
    private static final int DETAIL_LIMIT = 200;
    private static final int WRITE_CHUNK = 200;

    private final KeycloakSessionFactory factory;
    private final String realmId;
    private final String destinationId;

    public Reconciler(KeycloakSessionFactory factory, String realmId, String destinationId) {
        this.factory = factory;
        this.realmId = realmId;
        this.destinationId = destinationId;
    }

    /** One account as listed by the destination. */
    record Remote(String id, String userName, String externalId, boolean active) {}

    /** One user in scope as Keycloak would send it. */
    record Wanted(String subject, String userName, String externalId, boolean active) {}

    /** Queues a run on the drain executor, under the cluster lock. */
    public static void request(KeycloakSessionFactory factory, String realmId, String destinationId) {
        DrainScheduler s = DrainScheduler.get();
        if (s != null) s.reconcile(realmId, destinationId);
    }

    public static void runLocked(
            KeycloakSessionFactory factory, String realmId, String destinationId, Settings settings) {
        KeycloakSession session = factory.create();
        try {
            session
                    .getProvider(ClusterProvider.class)
                    .executeIfNotExecuted(
                            "scim-provisioning:reconcile:" + destinationId,
                            settings.reconcileLockTtlSeconds(),
                            () -> {
                                new Reconciler(factory, realmId, destinationId).run();
                                return null;
                            });
        } catch (RuntimeException e) {
            log.warnf("scim-provisioning: reconcile of %s failed: %s", destinationId, e.toString());
            log.debug("reconcile failure", e);
        } finally {
            session.close();
        }
    }

    private record Setup(Destination destination, String token) {}

    public void run() {
        long started = System.currentTimeMillis();
        Setup setup =
                KeycloakModelUtils.runJobInTransactionWithResult(
                        factory,
                        s -> {
                            RealmModel realm = Realms.bind(s, realmId);
                            Destination d = realm == null ? null : Destinations.find(realm, destinationId);
                            if (d == null || !d.enabled()) return null;
                            s.getProvider(ScimProvisioningProvider.class).ensureHealth(realmId, destinationId, started);
                            return new Setup(d, Tokens.resolve(s, d.tokenRef()).orElse(null));
                        });
        if (setup == null) return;
        Destination d = setup.destination();
        if (setup.token() == null) {
            fail(
                    started,
                    new ScimException(
                            ErrorClass.MISCONFIGURED, "the token's vault reference cannot be resolved"));
            return;
        }

        // ---- page the destination, no transaction open
        List<Remote> remote = new ArrayList<>();
        ScimEndpoint client = Endpoints.create(factory, d.baseUrl(), setup::token, d.requestTimeout());
        try {
            for (int start = 1; remote.size() < MAX_ACCOUNTS; ) {
                ListPage page = client.listUsers(start, PAGE);
                for (User r : page.resources()) {
                    if (r.getId() == null) continue;
                    remote.add(
                            new Remote(
                                    r.getId(),
                                    text(r.getUserName()),
                                    text(r.getExternalId()),
                                    !Boolean.FALSE.equals(r.getActive())));
                }
                if (page.resources().isEmpty() || page.resources().size() < PAGE) break;
                start += page.resources().size();
            }
        } catch (ScimException e) {
            fail(started, e);
            return;
        }

        // ---- compare, in a transaction of its own
        Findings f =
                KeycloakModelUtils.runJobInTransactionWithResult(
                        factory,
                        s -> {
                            RealmModel realm = Realms.bind(s, realmId);
                            Destination current = realm == null ? null : Destinations.find(realm, destinationId);
                            if (current == null) return null;
                            return compare(s, realm, current, remote);
                        });
        if (f == null) return;

        // ---- correct, in small transactions
        apply(f);

        ObjectNode report = report(f, started, remote.size());
        int mismatches = f.mismatched.size();
        KeycloakModelUtils.runJobInTransaction(
                factory,
                s ->
                        s.getProvider(ScimProvisioningProvider.class)
                                .recordReconcile(
                                        realmId,
                                        destinationId,
                                        System.currentTimeMillis(),
                                        "ok",
                                        report.toString(),
                                        mismatches));
        Meters.reconcileMismatches(realmId, d, mismatches);
        log.infof(
                "scim-provisioning: reconcile of %s: %d in scope, %d accounts; missing %d, linked %d, mismatched %d, unmanaged %d",
                d.name(),
                f.inScope,
                remote.size(),
                f.missing.size(),
                f.linked.size(),
                f.mismatched.size(),
                f.unmanaged);
        DrainScheduler.wake(Set.of(realmId));
    }

    /** What the comparison found, and what to write. */
    static final class Findings {
        int inScope;
        int unmanaged;
        final List<ObjectNode> missing = new ArrayList<>();
        final List<ObjectNode> linked = new ArrayList<>();
        final List<ObjectNode> mismatched = new ArrayList<>();
        final List<String> deleteLinks = new ArrayList<>();
        final List<LinkRecord> newLinks = new ArrayList<>();
        final List<String> invalidate = new ArrayList<>();
        final List<String> revive = new ArrayList<>();
        final Set<String> mark = new HashSet<>();
    }

    private Findings compare(
            KeycloakSession s, RealmModel realm, Destination d, List<Remote> remote) {
        Findings f = new Findings();
        Mapping mapping = d.mapping();
        Map<String, Remote> byId = new HashMap<>();
        Map<String, Remote> byExternalId = new HashMap<>();
        Map<String, Remote> byUserName = new HashMap<>();
        for (Remote r : remote) {
            byId.put(r.id(), r);
            if (r.externalId() != null) byExternalId.putIfAbsent(r.externalId(), r);
            if (r.userName() != null) byUserName.putIfAbsent(r.userName().toLowerCase(Locale.ROOT), r);
        }
        Map<String, LinkRecord> links = new HashMap<>();
        for (ScimLinkModel l : s.getProvider(ScimProvisioningProvider.class).getLinks(realmId, destinationId))
            links.put(l.getSubject(), l.toRecord());
        Set<String> claimed = new HashSet<>();

        List<UserModel> users = UsersInScope.of(s, realm, d);
        f.inScope = users.size();
        Set<String> inScope = new HashSet<>();
        long now = System.currentTimeMillis();
        UserRenderer renderer = new UserRenderer(s);
        for (UserModel u : users) {
            ObjectNode resource = renderer.render(u, mapping);
            Wanted w =
                    new Wanted(
                            Subjects.user(u.getId()),
                            text(resource.path("userName").asText(null)),
                            text(resource.path("externalId").asText(null)),
                            u.isEnabled());
            inScope.add(w.subject());
            LinkRecord link = links.get(w.subject());
            Remote r = link != null && link.isLive() ? byId.get(link.remoteId()) : null;
            if (r == null) {
                Remote found = match(w, byExternalId, byUserName);
                if (found == null) {
                    f.missing.add(entry(w.subject(), w.userName()));
                    if (link != null) f.deleteLinks.add(w.subject());
                    f.mark.add(w.subject());
                    continue;
                }
                r = found;
                f.linked.add(entry(w.subject(), w.userName()).put("remoteId", r.id()));
                f.newLinks.add(
                        new LinkRecord(w.subject(), r.id(), null, r.active(), false, r.userName(), now));
                f.mark.add(w.subject());
                claimed.add(r.id());
                continue;
            }
            claimed.add(r.id());
            if (r.active() != w.active()) {
                f.mismatched.add(
                        entry(w.subject(), w.userName())
                                .put("expectedActive", w.active())
                                .put("actualActive", r.active()));
                f.invalidate.add(w.subject());
                f.mark.add(w.subject());
            }
        }

        // linked subjects no longer in scope whose account is still active
        for (LinkRecord l : links.values()) {
            if (!Subjects.isUser(l.subject()) || inScope.contains(l.subject())) continue;
            Remote r = byId.get(l.remoteId());
            if (r == null) continue;
            claimed.add(r.id());
            if (r.active()) {
                f.mismatched.add(
                        entry(l.subject(), l.userName())
                                .put("expectedActive", false)
                                .put("actualActive", true));
                if (l.deprovisioned()) f.revive.add(l.subject());
                else f.invalidate.add(l.subject());
                f.mark.add(l.subject());
            }
        }
        for (Remote r : remote) if (!claimed.contains(r.id())) f.unmanaged++;
        return f;
    }

    private static Remote match(
            Wanted w, Map<String, Remote> byExternalId, Map<String, Remote> byUserName) {
        if (w.externalId() != null && byExternalId.containsKey(w.externalId()))
            return byExternalId.get(w.externalId());
        if (w.userName() != null) return byUserName.get(w.userName().toLowerCase(Locale.ROOT));
        return null;
    }

    private void apply(Findings f) {
        // link writes first, then the marks that make the drainer act on them
        List<OutboxWrite> tasks = new ArrayList<>();
        for (String subject : f.deleteLinks)
            tasks.add(o -> o.removeLink(realmId, destinationId, subject));
        for (LinkRecord l : f.newLinks) tasks.add(o -> o.saveLink(realmId, destinationId, l));
        for (String subject : f.invalidate)
            tasks.add(o -> o.invalidateLink(realmId, destinationId, subject));
        for (String subject : f.revive)
            tasks.add(o -> o.reviveTombstone(realmId, destinationId, subject));
        for (String subject : f.mark)
            tasks.add(o -> o.mark(realmId, destinationId, subject, false, System.currentTimeMillis()));
        for (int i = 0; i < tasks.size(); i += WRITE_CHUNK) {
            List<OutboxWrite> chunk = tasks.subList(i, Math.min(tasks.size(), i + WRITE_CHUNK));
            try {
                KeycloakModelUtils.runJobInTransaction(
                        factory,
                        s -> {
                            ScimProvisioningProvider outbox = s.getProvider(ScimProvisioningProvider.class);
                            for (OutboxWrite t : chunk) t.run(outbox);
                        });
            } catch (RuntimeException e) {
                // a concurrent delivery wrote the same row: apply this chunk one write at a time
                for (OutboxWrite t : chunk) {
                    try {
                        KeycloakModelUtils.runJobInTransaction(
                                factory, s -> t.run(s.getProvider(ScimProvisioningProvider.class)));
                    } catch (RuntimeException single) {
                        log.debugf("scim-provisioning: reconcile write skipped: %s", single.toString());
                    }
                }
            }
        }
    }

    /** One corrective write against the outbox. */
    @FunctionalInterface
    interface OutboxWrite {
        void run(ScimProvisioningProvider outbox);
    }

    private void fail(long started, ScimException e) {
        ObjectNode report = JSON.createObjectNode();
        report.put("startedAt", started);
        report.put("finishedAt", System.currentTimeMillis());
        report.put("status", "failed");
        report.put("errorClass", e.errorClass().code());
        report.put("error", e.storedText());
        KeycloakModelUtils.runJobInTransaction(
                factory,
                s -> {
                    ScimProvisioningProvider outbox = s.getProvider(ScimProvisioningProvider.class);
                    outbox
                            .getHealth(realmId, destinationId)
                            .ifPresent(
                                    h ->
                                            outbox.recordReconcile(
                                                    realmId,
                                                    destinationId,
                                                    System.currentTimeMillis(),
                                                    "failed",
                                                    report.toString(),
                                                    h.getMismatches()));
                });
        log.warnf("scim-provisioning: reconcile of %s failed: %s", destinationId, e.summary());
    }

    private static ObjectNode report(Findings f, long started, int remoteCount) {
        ObjectNode r = JSON.createObjectNode();
        r.put("startedAt", started);
        r.put("finishedAt", System.currentTimeMillis());
        r.put("status", "ok");
        r.put("inScope", f.inScope);
        r.put("accounts", remoteCount);
        r.put("missing", f.missing.size());
        r.put("linked", f.linked.size());
        r.put("mismatched", f.mismatched.size());
        r.put("unmanaged", f.unmanaged);
        ObjectNode details = r.putObject("details");
        details.set("missing", limited(f.missing));
        details.set("linked", limited(f.linked));
        details.set("mismatched", limited(f.mismatched));
        return r;
    }

    private static ArrayNode limited(List<ObjectNode> items) {
        ArrayNode a = JSON.createArrayNode();
        for (int i = 0; i < items.size() && i < DETAIL_LIMIT; i++) a.add(items.get(i));
        return a;
    }

    private static ObjectNode entry(String subject, String userName) {
        ObjectNode n = JSON.createObjectNode();
        n.put("subject", subject);
        if (userName != null) n.put("userName", userName);
        return n;
    }

    private static String text(String s) {
        return s != null && !s.isBlank() ? s : null;
    }
}
