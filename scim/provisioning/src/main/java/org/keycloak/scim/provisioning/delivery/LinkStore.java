package org.keycloak.scim.provisioning.delivery;

import java.util.Optional;

/**
 * Where link rows live. Delivery itself never writes: {@link UserDelivery} and {@link
 * GroupDelivery} return the link to store, and the host writes it after the HTTP call, in its own
 * transaction. This interface exists so the algorithms can be exercised against an in-memory or
 * failing store.
 */
public interface LinkStore {
    Optional<LinkRecord> find(String subject);

    void save(LinkRecord link);

    /** Marks the link deprovisioned; a no-op when there is none. */
    default void tombstone(String subject, long at) {
        find(subject).ifPresent(l -> save(l.tombstone(at)));
    }
}
