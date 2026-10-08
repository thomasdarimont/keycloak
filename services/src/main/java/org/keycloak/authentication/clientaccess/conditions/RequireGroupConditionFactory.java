package org.keycloak.authentication.clientaccess.conditions;

import java.util.List;

import org.keycloak.authentication.clientaccess.ClientAccessCondition;
import org.keycloak.authentication.clientaccess.ClientAccessConditionFactory;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

public class RequireGroupConditionFactory implements ClientAccessConditionFactory {

    public static final String PROVIDER_ID = "require-group";
    public static final String CONFIG_GROUPS = "groups";
    public static final String CONFIG_INCLUDE_SUBGROUPS = "includeSubgroups";

    private static final List<ProviderConfigProperty> CONFIG = ProviderConfigurationBuilder.create()
            .property().name(CONFIG_GROUPS).label("Groups")
            .helpText("Group paths, for example /staff/hr. Membership in any listed group is enough.")
            .type(ProviderConfigProperty.MULTIVALUED_STRING_TYPE).add()
            .property().name(CONFIG_INCLUDE_SUBGROUPS).label("Include subgroups")
            .helpText("If on, membership in a subgroup of a listed group also matches.")
            .type(ProviderConfigProperty.BOOLEAN_TYPE).defaultValue("true").add()
            .property(NEGATE_PROPERTY)
            .build();

    @Override
    public ClientAccessCondition create(KeycloakSession session, ComponentModel model) {
        return new RequireGroupCondition(session, model);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Requires the user to be a member of one of the configured groups.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG;
    }
}
