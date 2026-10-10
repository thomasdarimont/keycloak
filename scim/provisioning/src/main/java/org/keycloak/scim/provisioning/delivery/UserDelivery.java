package org.keycloak.scim.provisioning.delivery;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.keycloak.scim.provisioning.client.ErrorClass;
import org.keycloak.scim.provisioning.client.ScimEndpoint;
import org.keycloak.scim.provisioning.client.ScimException;
import org.keycloak.scim.provisioning.mapping.PatchOps;
import org.keycloak.scim.resource.user.User;

/**
 * Upsert and deprovision of one user against one destination.
 *
 * <p>Neither method writes anything locally: both take the link as already read and return the link
 * to store. That keeps the host's database transaction closed across the HTTP calls.
 *
 * <p><b>Find-before-create is the correctness property</b>: without a live link the user is looked
 * up by {@code userName} before anything is created, so a crash between the destination's
 * {@code 201} and the host's commit, or an account that predates us, ends in adoption rather than
 * a duplicate.
 */
public final class UserDelivery {
    /** PATCH statuses that mean "this destination cannot PATCH": fall back to PUT. */
    private static final Set<Integer> PATCH_UNSUPPORTED = Set.of(400, 405, 501);

    private final ScimEndpoint client;
    private final Clock clock;

    public UserDelivery(ScimEndpoint client, Clock clock) {
        this.client = Objects.requireNonNull(client);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Makes the destination hold {@code resource} for {@code subject}.
     *
     * @param resource the SCIM user, as built by the mapping
     * @param hash the hash of {@code resource}
     * @param link the current link, if any
     */
    public DeliveryResult upsert(
            String subject, User resource, String hash, Optional<LinkRecord> link)
            throws ScimException {
        if (link.isPresent() && link.get().isLive() && hash.equals(link.get().payloadHash())) {
            return new DeliveryResult.Skipped("unchanged");
        }
        String userName = text(resource.getUserName());
        boolean active = !Boolean.FALSE.equals(resource.getActive());

        // A tombstoned link is not reused: a returning user is found again or re-created, never
        // written to blindly by a stale id.
        String remoteId = link.filter(LinkRecord::isLive).map(LinkRecord::remoteId).orElse(null);
        boolean created = false;

        if (remoteId == null && userName != null) remoteId = findId(userName);

        if (remoteId == null) {
            try {
                remoteId = client.createUser(resource).getId();
                created = true;
            } catch (ScimException e) {
                if (e.errorClass() != ErrorClass.CONFLICT || userName == null) throw e;
                remoteId = findId(userName);
                if (remoteId == null) throw e;
            }
        }

        if (!created) {
            try {
                client.replaceUser(remoteId, resource);
            } catch (ScimException e) {
                if (e.status() != 404) throw e;
                // deleted inside the app meanwhile
                remoteId = client.createUser(resource).getId();
                created = true;
            }
        }
        return new DeliveryResult.Delivered(
                LinkRecord.live(subject, remoteId, hash, active, userName, clock.millis()), created);
    }

    /**
     * Takes the account away.
     *
     * @param userName the {@code userName} to look the account up by when there is no link; used
     *     only when {@code forced}
     * @param forced an explicit deprovision (offboarding): looks the account up even without a link,
     *     because it may predate us. A forced {@link DeprovisionMode#DELETE} is also sent for a link
     *     that is already tombstoned — "deactivate now, delete later" — and a 404 counts as done.
     */
    public DeliveryResult deprovision(
            String subject,
            String userName,
            Optional<LinkRecord> link,
            DeprovisionMode mode,
            boolean forced)
            throws ScimException {
        boolean deleteTombstone = forced && mode == DeprovisionMode.DELETE;
        if (link.isPresent() && !link.get().isLive() && !deleteTombstone) {
            return new DeliveryResult.Skipped("already deprovisioned");
        }
        LinkRecord current = link.orElse(null);
        String remoteId = current == null ? null : current.remoteId();
        if (remoteId == null) {
            if (!forced) return new DeliveryResult.Skipped("never had an account there");
            if (userName != null) remoteId = findId(userName);
            if (remoteId == null) return new DeliveryResult.Skipped("no account there");
            current = LinkRecord.live(subject, remoteId, null, true, userName, clock.millis());
        }

        long now = clock.millis();
        if (mode == DeprovisionMode.DELETE) {
            client.deleteUser(remoteId); // 404 counts as success
            return new DeliveryResult.Delivered(current.tombstone(now), false);
        }

        try {
            client.patchUser(remoteId, PatchOps.deactivate());
        } catch (ScimException e) {
            if (e.status() == 404) return new DeliveryResult.Delivered(current.tombstone(now), false);
            if (!PATCH_UNSUPPORTED.contains(e.status())) throw e;
            Optional<User> remote = client.getUser(remoteId);
            if (remote.isEmpty()) {
                return new DeliveryResult.Delivered(current.tombstone(now), false);
            }
            client.replaceUser(remoteId, deactivated(remote.get()));
        }
        return new DeliveryResult.Delivered(current.tombstone(now), false);
    }

    /**
     * The read-modify-write body for destinations without PATCH: the account as returned, with
     * {@code active=false} and the server-managed attributes removed — {@code meta}, {@code id} and
     * the read-only {@code groups} (RFC 7643 §3.1).
     */
    static User deactivated(User remote) {
        remote.setMeta(null);
        remote.setId(null);
        remote.setGroups(null);
        remote.setActive(false);
        return remote;
    }

    /** The exact {@code userName} first, then its lower-case form. */
    private String findId(String userName) throws ScimException {
        Optional<User> found = client.findUserByUserName(userName);
        if (found.isEmpty()) {
            String lower = userName.toLowerCase(Locale.ROOT);
            if (!lower.equals(userName)) found = client.findUserByUserName(lower);
        }
        return found.map(User::getId).orElse(null);
    }

    /** Stores the outcome, if any (for hosts and tests that hold links in a {@link LinkStore}). */
    public static DeliveryResult apply(LinkStore store, DeliveryResult result) {
        if (result instanceof DeliveryResult.Delivered) {
            store.save(((DeliveryResult.Delivered) result).link());
        }
        return result;
    }

    private static String text(String s) {
        return s != null && !s.isBlank() ? s : null;
    }
}
