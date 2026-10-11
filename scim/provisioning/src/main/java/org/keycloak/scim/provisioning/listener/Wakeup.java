package org.keycloak.scim.provisioning.listener;

import java.util.LinkedHashSet;
import java.util.Set;

import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;
import org.keycloak.scim.provisioning.drainer.DrainScheduler;

/**
 * Wakes the drainer once the transaction that wrote a marker has committed: an
 * {@code enlistAfterCompletion} hook that only submits to the executor. Never runs before commit,
 * never does I/O on the request thread.
 */
public final class Wakeup extends AbstractKeycloakTransaction {
    private static final String ATTR = "scim-provisioning.wakeup";
    private final Set<String> realms = new LinkedHashSet<>();

    private Wakeup() {}

    public static void afterCommit(KeycloakSession session, String realmId) {
        if (!session.getTransactionManager().isActive()) {
            now(Set.of(realmId));
            return;
        }
        Wakeup w = (Wakeup) session.getAttribute(ATTR);
        if (w == null) {
            w = new Wakeup();
            session.setAttribute(ATTR, w);
            session.getTransactionManager().enlistAfterCompletion(w);
        }
        w.realms.add(realmId);
    }

    static void now(Set<String> realms) {
        DrainScheduler.wake(realms);
    }

    @Override
    protected void commitImpl() {
        now(realms);
    }

    @Override
    protected void rollbackImpl() {
        realms.clear();
    }
}
