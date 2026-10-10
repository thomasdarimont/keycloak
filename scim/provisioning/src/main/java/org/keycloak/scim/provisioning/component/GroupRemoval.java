package org.keycloak.scim.provisioning.component;

import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.scim.provisioning.listener.Enqueuer;
import org.keycloak.scim.provisioning.planner.Subjects;

/** What a group deletion does to one destination. */
final class GroupRemoval {
    private GroupRemoval() {}

    static void membersLeaving(
            KeycloakSession session, RealmModel realm, String destinationId, GroupModel group) {
        session
                .users()
                .getGroupMembersStream(realm, group)
                .forEach(
                        u ->
                                Enqueuer.fromEvent(
                                        session,
                                        new Enqueuer.Mark(
                                                realm.getId(), destinationId, Subjects.user(u.getId()), false)));
    }
}
