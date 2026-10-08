package org.keycloak.authentication.postauth.actions;

import java.util.Arrays;
import java.util.List;

import org.keycloak.authentication.postauth.PostAuthenticationAction;
import org.keycloak.authentication.postauth.PostAuthenticationActionFactory;
import org.keycloak.authentication.postauth.PostAuthenticationTrigger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

public class ClientAccessPolicyActionFactory implements PostAuthenticationActionFactory {

    public static final String PROVIDER_ID = "client-access-policy";

    private static final List<ProviderConfigProperty> CONFIG = ProviderConfigurationBuilder.create()
            .property().name(CONFIG_PRIORITY).label("Priority").helpText("Lower values run first.")
            .type(ProviderConfigProperty.INTEGER_TYPE).defaultValue("0").add()
            .property().name(CONFIG_ENABLED).label("Enabled")
            .type(ProviderConfigProperty.BOOLEAN_TYPE).defaultValue("true").add()
            .property().name(CONFIG_TRIGGERS).label("Triggers")
            .helpText("Restrict the triggers this action runs on. Empty means all supported triggers.")
            .type(ProviderConfigProperty.MULTIVALUED_LIST_TYPE)
            .options(Arrays.stream(PostAuthenticationTrigger.values()).map(Enum::name).toList()).add()
            .build();

    @Override
    public PostAuthenticationAction create(KeycloakSession session, ComponentModel model) {
        return new ClientAccessPolicyAction(session);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Denies access when the user does not satisfy the client access policies referenced by the client's '"
                + org.keycloak.authentication.clientaccess.ClientAccessPolicy.CLIENT_ATTRIBUTE + "' attribute.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG;
    }
}
