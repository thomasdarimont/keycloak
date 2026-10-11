package org.keycloak.scim.provisioning.listener;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.KeycloakModelUtils;

import org.jboss.logging.Logger;

/**
 * The {@code after-commit} enqueue mode: marks are queued during the
 * request and written, in a transaction of our own, only once the user-change transaction has
 * committed. A rolled-back change is never enqueued; a crash between the commit and this write
 * loses that mark until the next resync or reconcile.
 *
 * <p>Enlisted with {@code enlistAfterCompletion}, like Keycloak's own logging event listener does
 * with {@code EventListenerTransaction}.
 */
final class AfterCommitMarks extends AbstractKeycloakTransaction {
    private static final Logger log = Logger.getLogger(AfterCommitMarks.class);

    private static final String ATTR = "scim-provisioning.after-commit";

    private final KeycloakSessionFactory factory;
    private final Set<Enqueuer.Mark> marks = new LinkedHashSet<>();

    private AfterCommitMarks(KeycloakSessionFactory factory) {
        this.factory = factory;
    }

    static void add(KeycloakSession session, Enqueuer.Mark mark) {
        if (!session.getTransactionManager().isActive()) {
            // no transaction to wait for: write straight away in our own
            writeAll(session.getKeycloakSessionFactory(), List.of(mark));
            return;
        }
        AfterCommitMarks tx = (AfterCommitMarks) session.getAttribute(ATTR);
        if (tx == null) {
            tx = new AfterCommitMarks(session.getKeycloakSessionFactory());
            session.setAttribute(ATTR, tx);
            session.getTransactionManager().enlistAfterCompletion(tx);
        }
        tx.marks.add(mark);
    }

    @Override
    protected void commitImpl() {
        writeAll(factory, new ArrayList<>(marks));
        marks.clear();
    }

    @Override
    protected void rollbackImpl() {
        marks.clear();
    }

    private static void writeAll(KeycloakSessionFactory factory, List<Enqueuer.Mark> marks) {
        if (marks.isEmpty()) return;
        for (int attempt = 1; ; attempt++) {
            try {
                KeycloakModelUtils.runJobInTransaction(
                        factory,
                        s -> {
                            for (Enqueuer.Mark m : marks) Enqueuer.write(s, m);
                        });
                break;
            } catch (RuntimeException e) {
                // a first-contact insert can collide with a concurrent one; the retry takes the update path
                if (attempt >= 3) {
                    log.warnf(
                            "scim-provisioning: could not record %d change(s) after commit (%s); the next resync or"
                                    + " reconcile will pick them up",
                            marks.size(), e.getClass().getSimpleName());
                    log.debug("after-commit marker write failed", e);
                    return;
                }
            }
        }
        Set<String> realms = new LinkedHashSet<>();
        for (Enqueuer.Mark m : marks) realms.add(m.realmId());
        Wakeup.now(realms);
    }
}
