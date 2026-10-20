package org.keycloak.scim.provisioning.model;

import org.keycloak.scim.provisioning.delivery.LinkRecord;

/**
 * What a destination holds for a subject, as far as we know: its resource id and the hash of what
 * was last delivered.
 *
 * <p>Read-only; links are written through {@link ScimProvisioningProvider#saveLink}. {@link #toRecord()}
 * is the hand-off to {@code core}, whose delivery algorithms work on the detached {@link
 * LinkRecord}.
 */
public interface ScimLinkModel {

    String getRealmId();

    String getDestinationId();

    /** {@code user:<id>} or {@code group:<id>}. */
    String getSubject();

    /** The destination's resource id. */
    String getRemoteId();

    /** Hash of the resource last delivered; {@code null} forces the next delivery. */
    String getPayloadHash();

    /** The {@code active} value last delivered (groups: always {@code true}). */
    boolean isActive();

    /** Tombstone: the account was deactivated or deleted by us. */
    boolean isDeprovisioned();

    /** The {@code userName} (groups: {@code displayName}) last delivered. */
    String getUserName();

    /** Epoch milliseconds of the last write. */
    long getSyncedAt();

    /** Whether this link points at an account we may still update. */
    default boolean isLive() {
        return !isDeprovisioned();
    }

    /** The detached value the delivery algorithms in {@code core} work on. */
    default LinkRecord toRecord() {
        return new LinkRecord(
                getSubject(),
                getRemoteId(),
                getPayloadHash(),
                isActive(),
                isDeprovisioned(),
                getUserName(),
                getSyncedAt());
    }
}
