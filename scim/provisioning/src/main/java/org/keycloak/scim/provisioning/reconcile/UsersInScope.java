package org.keycloak.scim.provisioning.reconcile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.planner.ScopeEvaluator;

/** Enumerates the users a destination is for: the reverse direction of {@link ScopeEvaluator}. */
final class UsersInScope {
    private static final int PAGE = 500;

    private UsersInScope() {}

    static List<UserModel> of(KeycloakSession s, RealmModel realm, Destination d) {
        Map<String, UserModel> out = new LinkedHashMap<>();
        if (d.scopeEveryone()) {
            Map<String, String> params = new HashMap<>();
            params.put(UserModel.INCLUDE_SERVICE_ACCOUNT, "false");
            for (int first = 0; ; first += PAGE) {
                List<UserModel> page = new ArrayList<>();
                s.users().searchForUserStream(realm, params, first, PAGE).forEach(page::add);
                for (UserModel u : page) {
                    if (u.getServiceAccountClientLink() == null) out.putIfAbsent(u.getId(), u);
                }
                if (page.size() < PAGE) break;
            }
        } else {
            for (GroupModel g : Resync.scopedGroups(s, realm, d)) {
                s.users()
                        .getGroupMembersStream(realm, g)
                        .filter(u -> u.getServiceAccountClientLink() == null)
                        .forEach(u -> out.putIfAbsent(u.getId(), u));
            }
        }
        return new ArrayList<>(out.values());
    }
}
