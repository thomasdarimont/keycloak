package org.keycloak.authentication.clientaccess.conditions;

import java.util.List;

import org.keycloak.authentication.clientaccess.ClientAccessCondition;
import org.keycloak.authentication.clientaccess.ClientAccessConditionFactory;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

public class RequireRoleConditionFactory implements ClientAccessConditionFactory {

    public static final String PROVIDER_ID = "require-role";
    public static final String CONFIG_ROLES = "roles";
    public static final String CONFIG_REQUIRE_ALL = "requireAll";

    private static final List<ProviderConfigProperty> CONFIG = ProviderConfigurationBuilder.create()
            .property().name(CONFIG_ROLES).label("Roles")
            .helpText("Realm role names or clientId.roleName. Use ${client} for the client id of the requested client.")
            .type(ProviderConfigProperty.MULTIVALUED_STRING_TYPE).add()
            .property().name(CONFIG_REQUIRE_ALL).label("Require all roles")
            .helpText("If on, the user must have every listed role. Otherwise one is enough.")
            .type(ProviderConfigProperty.BOOLEAN_TYPE).defaultValue("false").add()
            .property(NEGATE_PROPERTY)
            .build();

    @Override
    public ClientAccessCondition create(KeycloakSession session, ComponentModel model) {
        return new RequireRoleCondition(session, model);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Requires the user to have one or all of the configured roles, including roles inherited via groups and composites.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG;
    }
}
