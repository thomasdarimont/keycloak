package org.keycloak.scim.provisioning.listener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.scim.provisioning.Settings;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.component.Destinations;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;
import org.keycloak.scim.provisioning.planner.Subjects;

/**
 * Writes "this subject needs delivering" markers. The marker is all the listener
 * writes: no planning, no scope evaluation, no HTTP.
 *
 * <p>Two entry points:
 *
 * <ul>
 *   <li>{@link #fromEvent}: for Keycloak events on someone else's transaction (listener, model
 *       events, user-storage hooks); honours the configured enqueue mode;
 *   <li>{@link #now}: for our own transactions (admin REST, resync, the drainer); writes in the
 *       caller's transaction.
 * </ul>
 *
 * Either way the drainer is woken after the transaction commits.
 */
public final class Enqueuer {
    private static final String MARKED = "scim-provisioning.marked";

    private Enqueuer() {}

    /** One mark for one destination. */
    /**
     * @param forcedMode for a forced mark, {@code "delete"} to delete whatever the destination's
     *     deprovision mode; null otherwise
     */
    public record Mark(
            String realmId, String destinationId, String subject, boolean forced, String forcedMode) {
        public Mark(String realmId, String destinationId, String subject, boolean forced) {
            this(realmId, destinationId, subject, forced, null);
        }
    }

    /** Marks the subject for every destination of the realm, in the configured enqueue mode. */
    public static void fromEvent(
            KeycloakSession session, String realmId, String subject, boolean forced) {
        if (realmId == null || subject == null) return;
        RealmModel realm = session.realms().getRealm(realmId);
        if (realm == null) return;
        List<Destination> destinations = Destinations.of(realm);
        if (destinations.isEmpty()) return;
        boolean group = Subjects.isGroup(subject);
        for (Destination d : destinations) {
            // a destination that does not take groups never delivers one; a resync catches up if that
            // changes
            if (group && !d.supportsGroups()) continue;
            fromEvent(session, new Mark(realmId, d.id(), subject, forced));
        }
    }

    /** One destination, in the configured enqueue mode. */
    public static void fromEvent(KeycloakSession session, Mark mark) {
        if (!firstTime(session, mark)) return;
        if (Settings.get().enqueueMode() == Settings.EnqueueMode.AFTER_COMMIT
                || !session.getTransactionManager().isActive()) {
            AfterCommitMarks.add(session, mark);
        } else {
            write(session, mark);
            Wakeup.afterCommit(session, mark.realmId());
        }
    }

    /** Our own transaction: write now, wake after commit. */
    public static void now(KeycloakSession session, Mark mark) {
        write(session, mark);
        Wakeup.afterCommit(session, mark.realmId());
    }

    static void write(KeycloakSession session, Mark m) {
        session
                .getProvider(ScimProvisioningProvider.class)
                .mark(
                        m.realmId(),
                        m.destinationId(),
                        m.subject(),
                        m.forced(),
                        m.forcedMode(),
                        System.currentTimeMillis());
    }

    /** Several events in one request mark the same subject once. */
    @SuppressWarnings("unchecked")
    private static boolean firstTime(KeycloakSession session, Mark mark) {
        Set<Mark> marked = (Set<Mark>) session.getAttribute(MARKED);
        if (marked == null) {
            marked = new HashSet<>();
            session.setAttribute(MARKED, marked);
        }
        return marked.add(mark);
    }
}
