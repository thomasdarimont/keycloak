package org.keycloak.scim.provisioning.delivery;

import java.util.Objects;

/**
 * What a destination holds for a subject, as far as we know.
 *
 * @param subject {@code user:<id>} or {@code group:<id>}
 * @param remoteId the destination's resource id
 * @param payloadHash hash of the resource last delivered; {@code null} forces the next delivery
 * @param active the {@code active} value last delivered (groups: always {@code true})
 * @param deprovisioned tombstone: the account was deactivated or deleted by us
 * @param userName the {@code userName} (groups: {@code displayName}) last delivered
 * @param syncedAt epoch milliseconds of the last write
 */
public record LinkRecord(
        String subject,
        String remoteId,
        String payloadHash,
        boolean active,
        boolean deprovisioned,
        String userName,
        long syncedAt) {

    public LinkRecord {
        Objects.requireNonNull(subject);
        Objects.requireNonNull(remoteId);
    }

    /** A live link to a resource that holds the given payload. */
    public static LinkRecord live(
            String subject, String remoteId, String hash, boolean active, String userName, long at) {
        return new LinkRecord(subject, remoteId, hash, active, false, userName, at);
    }

    /** The tombstone left behind by a deprovision. */
    public LinkRecord tombstone(long at) {
        return new LinkRecord(subject, remoteId, null, false, true, userName, at);
    }

    /** The same link with its hash cleared, so the next delivery is not skipped. */
    public LinkRecord invalidated() {
        return new LinkRecord(subject, remoteId, null, active, deprovisioned, userName, syncedAt);
    }

    /** Whether this link points at an account we may still update. */
    public boolean isLive() {
        return !deprovisioned;
    }
}
