package org.keycloak.scim.provisioning.model;

/**
 * A destination's health, backoff and last reconcile. Deliberately not component config: config is
 * cluster-cached and admin-visible, this changes on every delivery.
 *
 * <p>Read-only; written through the {@code record*} methods of {@link ScimProvisioningProvider}.
 */
public interface DestinationHealthModel {

    String getRealmId();

    String getDestinationId();

    long getCreatedAt();

    Long getLastSuccessAt();

    String getLastErrorClass();

    String getLastError();

    /** Since when every delivery has failed; {@code null} when the destination is healthy. */
    Long getFailingSince();

    /** Nothing is sent before this; set by a transient failure, cleared by a success or a save. */
    Long getRetryAt();

    int getConsecutiveFailures();

    Long getLastReconcileAt();

    /** {@code ok} or {@code failed}; {@code null} when it has never run. */
    String getLastReconcileStatus();

    /** The last reconcile's report as JSON; there is no run history. */
    String getLastReconcileReport();

    int getMismatches();

    /** Whether the destination is backed off right now. */
    default boolean isBackedOff(long now) {
        Long retryAt = getRetryAt();
        return retryAt != null && retryAt > now;
    }
}
