package org.keycloak.authentication.clientaccess.conditions;

import java.util.List;

import org.jboss.logging.Logger;
import org.keycloak.authentication.clientaccess.ClientAccessCondition;
import org.keycloak.authentication.clientaccess.ClientAccessContext;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.RoleUtils;

/**
 * Matches when the user is a member of one of the configured groups, given by path such as {@code /staff/hr}.
 */
public class RequireGroupCondition implements ClientAccessCondition {

    private static final Logger logger = Logger.getLogger(RequireGroupCondition.class);

    private final KeycloakSession session;
    private final ComponentModel model;

    public RequireGroupCondition(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    @Override
    public boolean matches(ClientAccessContext context) {
        List<String> paths = model.getConfig().getList(RequireGroupConditionFactory.CONFIG_GROUPS);
        if (paths == null || paths.isEmpty()) {
            return true;
        }
        boolean includeSubgroups = model.get(RequireGroupConditionFactory.CONFIG_INCLUDE_SUBGROUPS, true);

        for (String path : paths) {
            GroupModel group = KeycloakModelUtils.findGroupByPath(session, context.getRealm(), path);
            if (group == null) {
                logger.debugf("Group '%s' required by condition '%s' does not exist", path, model.getName());
                continue;
            }
            boolean member = includeSubgroups
                    ? RoleUtils.isMember(context.getUser().getGroupsStream(), group)
                    : RoleUtils.isDirectMember(context.getUser().getGroupsStream(), group);
            if (member) {
                return true;
            }
        }
        return false;
    }
}
