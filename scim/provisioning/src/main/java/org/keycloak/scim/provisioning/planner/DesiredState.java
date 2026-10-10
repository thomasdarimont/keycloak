package org.keycloak.scim.provisioning.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.delivery.DeprovisionMode;
import org.keycloak.scim.provisioning.delivery.GroupDelivery;
import org.keycloak.scim.provisioning.delivery.LinkRecord;
import org.keycloak.scim.provisioning.hash.Hashing;
import org.keycloak.scim.provisioning.mapping.Mapping;
import org.keycloak.scim.provisioning.model.OutboxEntryModel;
import org.keycloak.scim.provisioning.model.ScimLinkModel;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Computes the desired state of one subject in one destination from what Keycloak holds now
 * (never from the snapshot of the transaction that marked it), read past the cache ({@link
 * Fresh}). Runs inside a short transaction, before any HTTP call.
 */
public final class DesiredState {
    private final KeycloakSession session;
    private final RealmModel realm;
    private final Destination destination;
    private final Mapping mapping;
    private final ScopeEvaluator scope;
    private final ScimProvisioningProvider outbox;
    private final UserRenderer renderer;

    public DesiredState(
            KeycloakSession session, RealmModel realm, Destination destination, Mapping mapping) {
        this.session = session;
        this.realm = realm;
        this.destination = destination;
        this.mapping = mapping;
        this.scope = ScopeEvaluator.of(session, realm, destination);
        this.outbox = session.getProvider(ScimProvisioningProvider.class);
        this.renderer = new UserRenderer(session);
    }

    public ScopeEvaluator scope() {
        return scope;
    }

    public Plan plan(OutboxEntryModel entry) {
        String subject = entry.getSubject();
        if (Subjects.isUser(subject)) return planUser(subject, entry.isForced(), entry.getForcedMode());
        if (Subjects.isGroup(subject)) return planGroup(subject, entry.getDeferrals());
        return new Plan.Nothing("unknown subject type");
    }

    public Plan planUser(String subject, boolean forced) {
        return planUser(subject, forced, null);
    }

    /** @param forcedMode {@code "delete"} overrides the destination's deprovision mode when forced */
    public Plan planUser(String subject, boolean forced, String forcedMode) {
        UserModel user = Fresh.users(session).getUserById(realm, Subjects.id(subject));
        Optional<LinkRecord> link = link(subject);
        boolean inScope = !forced && scope.inScope(user);
        if (inScope) {
            ObjectNode resource = renderer.render(user, mapping);
            List<String> groupIds =
                    user.getGroupsStream().map(GroupModel::getId).collect(Collectors.toList());
            return new Plan.UpsertUser(subject, resource, Hashing.hash(resource), link, groupIds);
        }
        if (link.isEmpty() && !forced) return new Plan.Nothing("out of scope, never provisioned");
        String userName =
                link.map(LinkRecord::userName).orElseGet(() -> user == null ? null : userNameFor(user));
        DeprovisionMode mode =
                forced && "delete".equals(forcedMode) ? DeprovisionMode.DELETE : destination.deprovision();
        return new Plan.DeprovisionUser(subject, userName, link, mode, forced);
    }

    /** What the destination holds for the subject, as the delivery algorithms want it. */
    private Optional<LinkRecord> link(String subject) {
        return outbox.getLink(realm.getId(), destination.id(), subject).map(ScimLinkModel::toRecord);
    }

    /** The {@code userName} the mapping would send for this user (to find an unlinked account). */
    private String userNameFor(UserModel user) {
        return renderer.userName(user, mapping);
    }

    public Plan planGroup(String subject, int deferrals) {
        if (!destination.supportsGroups()) return new Plan.Nothing("destination does not take groups");
        GroupModel group = Fresh.groups(session).getGroupById(realm, Subjects.id(subject));
        if (group == null) return new Plan.Nothing("group deleted; groups are never deleted remotely");
        if (!scope.withinScopedGroup(group)) return new Plan.Nothing("group out of scope");
        List<UserModel> direct =
                Fresh.users(session)
                        .getGroupMembersStream(realm, group)
                        .filter(u -> u.isEnabled() && scope.inScope(u))
                        .collect(Collectors.toList());
        List<String> memberSubjects = new ArrayList<>();
        for (UserModel u : direct) memberSubjects.add(Subjects.user(u.getId()));
        Map<String, ScimLinkModel> links =
                outbox.getLinks(realm.getId(), destination.id(), memberSubjects);
        Set<String> pending =
                outbox.getPendingSubjects(realm.getId(), destination.id(), memberSubjects);
        List<GroupDelivery.Member> members = new ArrayList<>();
        for (String s : memberSubjects) {
            ScimLinkModel l = links.get(s);
            String remoteId = l != null && l.isLive() ? l.getRemoteId() : null;
            members.add(new GroupDelivery.Member(s, remoteId, pending.contains(s)));
        }
        Optional<LinkRecord> link = link(subject);
        return new Plan.DeliverGroup(subject, group.getName(), group.getId(), members, link, deferrals);
    }
}
