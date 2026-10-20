package org.keycloak.scim.provisioning.delivery;

import java.time.Duration;
import java.util.Objects;

/**
 * The outcome of one delivery that did not fail. Failures are {@code ScimException}s.
 *
 * <p>Delivery never writes; the host stores {@link Delivered#link()} after the HTTP call.
 */
public sealed interface DeliveryResult {

    /**
     * The destination now holds the desired state.
     *
     * @param link the link row to store (a tombstone after a deprovision)
     * @param created whether a new resource was created (as opposed to adopted or updated)
     */
    record Delivered(LinkRecord link, boolean created) implements DeliveryResult {
        public Delivered {
            Objects.requireNonNull(link);
        }
    }

    /** Nothing had to be sent: identical state already delivered, or nothing to take away. */
    record Skipped(String reason) implements DeliveryResult {}

    /** Not sent yet; try again after {@code delay} (a group waiting for its members). */
    record Deferred(Duration delay, String reason) implements DeliveryResult {}
}
