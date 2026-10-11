package org.keycloak.scim.provisioning.drainer;

import java.util.function.Supplier;

/** Retries a transaction of our own when the database reports a lock conflict or deadlock. */
public final class Retry {
    private static final int ATTEMPTS = 3;

    private Retry() {}

    public static <T> T onLockConflict(Supplier<T> tx) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.get();
            } catch (RuntimeException e) {
                if (attempt >= ATTEMPTS || !isLockConflict(e)) throw e;
                try {
                    Thread.sleep(20L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    static boolean isLockConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof jakarta.persistence.PessimisticLockException
                    || t instanceof jakarta.persistence.LockTimeoutException) return true;
            String n = t.getClass().getSimpleName();
            if (n.equals("LockAcquisitionException")
                    || n.equals("MySQLTransactionRollbackException")
                    || n.equals("CannotAcquireLockException")) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }
}
