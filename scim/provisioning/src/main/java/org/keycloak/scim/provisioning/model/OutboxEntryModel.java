package org.keycloak.scim.provisioning.model;

/**
 * One queued change: the subject needs delivering to the destination. An entry carries no resource
 * data — only that something about the subject changed. Each change adds a row; a row whose
 * subject has a newer pending row settles without a delivery of its own, and settling a row removes
 * the subject's other settled rows.
 *
 * <p>Read-only: every transition goes through {@link ScimProvisioningProvider} and names the row by
 * {@link #getId()}, so a change that lands while a delivery is in flight (a newer row) is never
 * lost.
 */
public interface OutboxEntryModel {
    String PENDING = "pending";
    String DONE = "done";
    String DEAD = "dead";

    /** The row's id; transitions name it. */
    String getId();

    String getRealmId();

    String getDestinationId();

    /** {@code user:<id>} or {@code group:<id>}. */
    String getSubject();

    /** {@link #PENDING}, {@link #DONE} or {@link #DEAD}. */
    String getState();

    /** A forced deprovision: take the account away even if the person is still in scope. */
    boolean isForced();

    /** For a forced deprovision: {@code delete} overrides the destination's mode; null keeps it. */
    String getForcedMode();

    int getAttempts();

    /** Epoch milliseconds: not due before this. */
    long getNextAt();

    /** How often a group was put off waiting for its members. */
    int getDeferrals();

    String getLastErrorClass();

    String getLastError();

    long getCreatedAt();

    long getUpdatedAt();

    default boolean isPending() {
        return PENDING.equals(getState());
    }

    default boolean isDead() {
        return DEAD.equals(getState());
    }

    default boolean isDone() {
        return DONE.equals(getState());
    }
}
