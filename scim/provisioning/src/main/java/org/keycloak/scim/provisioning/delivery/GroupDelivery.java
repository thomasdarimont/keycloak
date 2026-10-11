package org.keycloak.scim.provisioning.delivery;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.keycloak.scim.provisioning.client.ErrorClass;
import org.keycloak.scim.provisioning.client.ScimEndpoint;
import org.keycloak.scim.provisioning.client.ScimException;
import org.keycloak.scim.provisioning.hash.Hashing;
import org.keycloak.scim.provisioning.mapping.GroupResourceBuilder;
import org.keycloak.scim.provisioning.mapping.PatchOps;
import org.keycloak.scim.resource.group.Group;
import org.keycloak.util.JsonSerialization;

/**
 * Delivery of one group.
 *
 * <ul>
 *   <li>members are sent as the destination's resource ids, from the link rows;
 *   <li>while a member's account has not landed (pending, no link) the group is <b>deferred</b>;
 *       after {@code maxDeferrals} deferrals it is sent without that member, who is added when the
 *       host re-marks the group after the member lands;
 *   <li>without a link the group is found by {@code displayName} before it is created;
 *   <li>members are replaced with PATCH; a 404 re-creates the group, a destination without PATCH
 *       gets a PUT;
 *   <li>groups are never deleted remotely.
 * </ul>
 */
public final class GroupDelivery {
    private static final Set<Integer> PATCH_UNSUPPORTED = Set.of(405, 501);

    private final ScimEndpoint client;
    private final Clock clock;
    private final Duration deferral;

    /**
     * @param deferral how long a deferred group waits before the next try
     */
    public GroupDelivery(ScimEndpoint client, Clock clock, Duration deferral) {
        this.client = Objects.requireNonNull(client);
        this.clock = Objects.requireNonNull(clock);
        this.deferral = Objects.requireNonNull(deferral);
    }

    /**
     * A member as the destination sees it.
     *
     * @param remoteId the member's resource id in the destination, or {@code null} if none yet
     * @param pending whether the member has a delivery waiting
     */
    public record Member(String subject, String remoteId, boolean pending) {
        /** Not landed yet: there is a delivery waiting and no account to point at. */
        boolean notLanded() {
            return pending && remoteId == null;
        }
    }

    /**
     * @param deferrals how many times this group has been deferred since it was last marked
     */
    public DeliveryResult deliver(
            String subject,
            String displayName,
            String externalId,
            List<Member> members,
            Optional<LinkRecord> link,
            int deferrals,
            int maxDeferrals)
            throws ScimException {
        boolean waiting = members.stream().anyMatch(Member::notLanded);
        if (waiting && deferrals < maxDeferrals) {
            return new DeliveryResult.Deferred(deferral, "a member's account has not landed yet");
        }
        Set<String> memberIds = new TreeSet<>();
        for (Member m : members) if (m.remoteId() != null) memberIds.add(m.remoteId());
        Group resource = GroupResourceBuilder.build(displayName, externalId, memberIds);
        String hash = Hashing.hash(JsonSerialization.mapper.valueToTree(resource));
        if (link.isPresent() && link.get().isLive() && hash.equals(link.get().payloadHash())) {
            return new DeliveryResult.Skipped("unchanged");
        }

        String remoteId = link.filter(LinkRecord::isLive).map(LinkRecord::remoteId).orElse(null);
        boolean created = false;
        if (remoteId == null) remoteId = findId(displayName);
        if (remoteId == null) {
            try {
                remoteId = client.createGroup(resource).getId();
                created = true;
            } catch (ScimException e) {
                if (e.errorClass() != ErrorClass.CONFLICT) throw e;
                remoteId = findId(displayName);
                if (remoteId == null) throw e;
            }
        }
        if (!created) {
            try {
                client.patchGroup(remoteId, PatchOps.replaceGroup(displayName, memberIds));
            } catch (ScimException e) {
                if (e.status() == 404) {
                    remoteId = client.createGroup(resource).getId();
                    created = true;
                } else if (PATCH_UNSUPPORTED.contains(e.status())) {
                    client.replaceGroup(remoteId, resource);
                } else {
                    throw e;
                }
            }
        }
        return new DeliveryResult.Delivered(
                LinkRecord.live(subject, remoteId, hash, true, displayName, clock.millis()), created);
    }

    private String findId(String displayName) throws ScimException {
        Optional<Group> found = client.findGroupByDisplayName(displayName);
        return found.map(Group::getId).orElse(null);
    }
}
