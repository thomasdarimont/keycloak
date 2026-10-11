package org.keycloak.scim.provisioning.listener;

import org.keycloak.models.GroupModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;
import org.keycloak.scim.provisioning.planner.Subjects;

/**
 * Model-level events: published by the JPA adapters on every code path — admin REST,
 * account console, federation, other extensions — synchronously and in the changing transaction.
 * They are better than admin events for structural changes.
 */
public final class ModelEventBridge implements ProviderEventListener {

    @Override
    public void onEvent(ProviderEvent event) {
        if (event instanceof GroupModel.GroupMemberJoinEvent) {
            GroupModel.GroupMemberJoinEvent e = (GroupModel.GroupMemberJoinEvent) event;
            membership(e, e.getUser());
        } else if (event instanceof GroupModel.GroupMemberLeaveEvent) {
            GroupModel.GroupMemberLeaveEvent e = (GroupModel.GroupMemberLeaveEvent) event;
            membership(e, e.getUser());
        } else if (event instanceof GroupModel.GroupRemovedEvent) {
            // Published by JpaRealmProvider.removeGroup before the memberships are deleted (and once per
            // sub-group): the members are still enumerable. Each is marked; those no longer in scope by
            // any rule are deprovisioned at delivery time. The remote group is never deleted.
            GroupModel.GroupRemovedEvent e = (GroupModel.GroupRemovedEvent) event;
            String realmId = e.getRealm().getId();
            e.getKeycloakSession()
                    .users()
                    .getGroupMembersStream(e.getRealm(), e.getGroup())
                    .forEach(
                            u ->
                                    Enqueuer.fromEvent(
                                            e.getKeycloakSession(), realmId, Subjects.user(u.getId()), false));
        } else if (event instanceof GroupModel.GroupUpdatedEvent) {
            GroupModel.GroupUpdatedEvent e = (GroupModel.GroupUpdatedEvent) event;
            Enqueuer.fromEvent(
                    e.getKeycloakSession(),
                    e.getRealm().getId(),
                    Subjects.group(e.getGroup().getId()),
                    false);
        } else if (event instanceof UserModel.UserPreRemovedEvent) {
            UserModel.UserPreRemovedEvent e = (UserModel.UserPreRemovedEvent) event;
            String realmId = e.getRealm().getId();
            Enqueuer.fromEvent(
                    e.getKeycloakSession(), realmId, Subjects.user(e.getUser().getId()), false);
            // memberships are still there: the user's groups lose a member
            e.getUser()
                    .getGroupsStream()
                    .forEach(
                            g ->
                                    Enqueuer.fromEvent(
                                            e.getKeycloakSession(), realmId, Subjects.group(g.getId()), false));
        } else if (event instanceof RealmModel.RealmRemovedEvent) {
            RealmModel.RealmRemovedEvent e = (RealmModel.RealmRemovedEvent) event;
            e.getKeycloakSession()
                    .getProvider(ScimProvisioningProvider.class)
                    .removeRealm(e.getRealm().getId());
        }
    }

    private static void membership(GroupModel.GroupEvent e, UserModel user) {
        String realmId = e.getRealm().getId();
        Enqueuer.fromEvent(e.getKeycloakSession(), realmId, Subjects.user(user.getId()), false);
        Enqueuer.fromEvent(
                e.getKeycloakSession(), realmId, Subjects.group(e.getGroup().getId()), false);
    }
}
