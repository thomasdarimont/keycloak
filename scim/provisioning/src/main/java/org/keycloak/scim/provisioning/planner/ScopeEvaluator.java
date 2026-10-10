package org.keycloak.scim.provisioning.planner;

import java.util.HashSet;
import java.util.Set;

import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.scim.provisioning.component.Destination;

/**
 * Who a destination is for (roles are not a scope rule).
 *
 * <ul>
 *   <li>{@code scopeGroups}: members of these groups or of any of their sub-groups (ancestor walk
 *       from each of the user's direct groups);
 *   <li>{@code scopeEveryone}: every user of the realm, enabled or not (a disabled user is delivered
 *       with {@code active=false});
 *   <li>service-account users are never in scope.
 * </ul>
 *
 * <p>A group is delivered (when the destination supports groups) if it, or an ancestor, is listed in
 * {@code scopeGroups}.
 */
public final class ScopeEvaluator {
    private final Set<String> scopeGroupIds;
    private final boolean everyone;

    private ScopeEvaluator(Set<String> scopeGroupIds, boolean everyone) {
        this.scopeGroupIds = scopeGroupIds;
        this.everyone = everyone;
    }

    /** Resolves {@code scopeGroups} entries: a group id, a path ({@code /a/b}), or a top-level name. */
    public static ScopeEvaluator of(KeycloakSession session, RealmModel realm, Destination d) {
        Set<String> ids = new HashSet<>();
        for (String ref : d.scopeGroups()) {
            GroupModel g = resolve(session, realm, ref);
            if (g != null) ids.add(g.getId());
        }
        return new ScopeEvaluator(ids, d.scopeEveryone());
    }

    public static GroupModel resolve(KeycloakSession session, RealmModel realm, String ref) {
        if (ref == null || ref.isBlank()) return null;
        String r = ref.strip();
        GroupModel g = session.groups().getGroupById(realm, r);
        if (g != null) return g;
        try {
            g = KeycloakModelUtils.findGroupByPath(session, realm, r.startsWith("/") ? r : "/" + r);
        } catch (RuntimeException e) {
            g = null;
        }
        return g;
    }

    public Set<String> scopeGroupIds() {
        return scopeGroupIds;
    }

    public boolean everyone() {
        return everyone;
    }

    public boolean inScope(UserModel user) {
        if (user == null || user.getServiceAccountClientLink() != null) return false;
        if (everyone) return true;
        if (scopeGroupIds.isEmpty()) return false;
        return user.getGroupsStream().anyMatch(this::withinScopedGroup);
    }

    /** The group or one of its ancestors is listed in {@code scopeGroups}. */
    public boolean withinScopedGroup(GroupModel group) {
        Set<String> seen = new HashSet<>();
        for (GroupModel g = group; g != null && seen.add(g.getId()); g = g.getParent()) {
            if (scopeGroupIds.contains(g.getId())) return true;
        }
        return false;
    }
}
