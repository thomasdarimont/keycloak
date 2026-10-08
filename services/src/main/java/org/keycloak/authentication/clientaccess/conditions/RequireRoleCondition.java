package org.keycloak.authentication.clientaccess.conditions;

import java.util.List;

import org.jboss.logging.Logger;
import org.keycloak.authentication.clientaccess.ClientAccessCondition;
import org.keycloak.authentication.clientaccess.ClientAccessContext;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RoleModel;
import org.keycloak.models.utils.KeycloakModelUtils;

/**
 * Matches when the user has one (or all) of the configured roles. Roles are given as realm role names or as
 * {@code clientId.roleName}. The placeholder {@code ${client}} is replaced with the client id of the requested
 * client, so a single shared policy can require a per-application role.
 */
public class RequireRoleCondition implements ClientAccessCondition {

    private static final Logger logger = Logger.getLogger(RequireRoleCondition.class);

    public static final String CLIENT_PLACEHOLDER = "${client}";

    private final KeycloakSession session;
    private final ComponentModel model;

    public RequireRoleCondition(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    @Override
    public boolean matches(ClientAccessContext context) {
        List<String> roleNames = model.getConfig().getList(RequireRoleConditionFactory.CONFIG_ROLES);
        if (roleNames == null || roleNames.isEmpty()) {
            return true;
        }
        boolean requireAll = model.get(RequireRoleConditionFactory.CONFIG_REQUIRE_ALL, false);

        for (String configured : roleNames) {
            String roleName = configured.replace(CLIENT_PLACEHOLDER, context.getClient().getClientId());
            RoleModel role = KeycloakModelUtils.getRoleFromString(session, context.getRealm(), roleName);
            boolean has = role != null && context.getUser().hasRole(role);
            if (role == null) {
                logger.debugf("Role '%s' required by condition '%s' does not exist", roleName, model.getName());
            }
            if (has && !requireAll) {
                return true;
            }
            if (!has && requireAll) {
                return false;
            }
        }
        return requireAll;
    }
}
