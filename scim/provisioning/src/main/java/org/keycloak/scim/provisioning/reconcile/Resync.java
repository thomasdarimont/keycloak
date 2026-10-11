package org.keycloak.scim.provisioning.reconcile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.scim.provisioning.Realms;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.component.Destinations;
import org.keycloak.scim.provisioning.drainer.DrainScheduler;
import org.keycloak.scim.provisioning.model.ScimLinkModel;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;
import org.keycloak.scim.provisioning.planner.ScopeEvaluator;
import org.keycloak.scim.provisioning.planner.Subjects;

import org.jboss.logging.Logger;

/**
 * Resync: mark every subject the destination should hold — every user in scope, every
 * linked subject, every scoped group when groups are on — and every existing row. The drainer's
 * hash dedupe against the link rows makes this free for unchanged subjects, which is why
 * resync is the universal repair: after a scope or mapping change, on enable, for an imported
 * destination, or on request.
 *
 * <p>Runs on the drain executor, never in a request, in chunks of short transactions.
 */
public final class Resync {
    private static final Logger log = Logger.getLogger(Resync.class);

    private static final int CHUNK = 500;

    private Resync() {}

    /** Runs a resync once the current transaction has committed. */
    public static void afterCommit(KeycloakSession session, String realmId, String destinationId) {
        session
                .getTransactionManager()
                .enlistAfterCompletion(
                        new AbstractKeycloakTransaction() {
                            @Override
                            protected void commitImpl() {
                                request(session.getKeycloakSessionFactory(), realmId, destinationId);
                            }

                            @Override
                            protected void rollbackImpl() {}
                        });
    }

    /** Queues a resync on the drain executor. */
    public static void request(KeycloakSessionFactory factory, String realmId, String destinationId) {
        DrainScheduler s = DrainScheduler.get();
        if (s == null) return;
        s.execute(
                () -> {
                    try {
                        int n = run(factory, realmId, destinationId);
                        log.infof(
                                "scim-provisioning: resync of destination %s marked %d subject(s)", destinationId, n);
                    } catch (RuntimeException e) {
                        log.warnf(
                                "scim-provisioning: resync of destination %s failed: %s", destinationId, e.toString());
                        log.debug("resync failure", e);
                    }
                });
    }

    /** The resync itself; returns how many subjects were marked. */
    public static int run(KeycloakSessionFactory factory, String realmId, String destinationId) {
        long now = System.currentTimeMillis();
        // 1. the subjects that already have a row (delivered, pending or dead)
        Set<String> existing =
                KeycloakModelUtils.runJobInTransactionWithResult(
                        factory,
                        s -> {
                            RealmModel realm = Realms.bind(s, realmId);
                            if (realm == null || Destinations.find(realm, destinationId) == null) return null;
                            ScimProvisioningProvider outbox = s.getProvider(ScimProvisioningProvider.class);
                            outbox.ensureHealth(realmId, destinationId, now);
                            return outbox.getSubjects(realmId, destinationId);
                        });
        if (existing == null) return 0;
        // 2. who should be there
        Set<String> wanted =
                KeycloakModelUtils.runJobInTransactionWithResult(
                        factory,
                        s -> {
                            RealmModel realm = Realms.bind(s, realmId);
                            Destination d = Destinations.find(realm, destinationId);
                            return d == null ? Set.<String>of() : subjectsFor(s, realm, d);
                        });
        // 3. a new row for each of them: the drainer delivers whoever differs from what was sent, and a
        //    dead letter among them gets fresh attempts
        Set<String> all = new LinkedHashSet<>(wanted);
        all.addAll(existing);
        List<String> subjects = new ArrayList<>(all);
        for (int i = 0; i < subjects.size(); i += CHUNK) {
            List<String> chunk = subjects.subList(i, Math.min(subjects.size(), i + CHUNK));
            org.keycloak.scim.provisioning.drainer.Retry.onLockConflict(
                    () -> {
                        KeycloakModelUtils.runJobInTransaction(
                                factory,
                                s -> {
                                    ScimProvisioningProvider outbox = s.getProvider(ScimProvisioningProvider.class);
                                    long at = System.currentTimeMillis();
                                    for (String subject : chunk)
                                        outbox.mark(realmId, destinationId, subject, false, at);
                                });
                        return null;
                    });
        }
        DrainScheduler.wake(Set.of(realmId));
        return subjects.size();
    }

    /** Users in scope, linked subjects and (with groups on) scoped groups. */
    static Set<String> subjectsFor(KeycloakSession s, RealmModel realm, Destination d) {
        Set<String> out = new LinkedHashSet<>();
        for (UserModel u : UsersInScope.of(s, realm, d)) out.add(Subjects.user(u.getId()));
        for (ScimLinkModel l :
                s.getProvider(ScimProvisioningProvider.class).getLinks(realm.getId(), d.id())) {
            if (Subjects.isUser(l.getSubject()) || d.supportsGroups()) out.add(l.getSubject());
        }
        if (d.supportsGroups()) {
            for (GroupModel g : scopedGroups(s, realm, d)) out.add(Subjects.group(g.getId()));
        }
        return out;
    }

    /** The scoped groups and all their descendants. */
    static List<GroupModel> scopedGroups(KeycloakSession s, RealmModel realm, Destination d) {
        ScopeEvaluator scope = ScopeEvaluator.of(s, realm, d);
        List<GroupModel> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<GroupModel> todo = new ArrayList<>();
        for (String id : scope.scopeGroupIds()) {
            GroupModel g = s.groups().getGroupById(realm, id);
            if (g != null) todo.add(g);
        }
        while (!todo.isEmpty()) {
            GroupModel g = todo.remove(todo.size() - 1);
            if (!seen.add(g.getId())) continue;
            out.add(g);
            g.getSubGroupsStream().forEach(todo::add);
        }
        return out;
    }
}
