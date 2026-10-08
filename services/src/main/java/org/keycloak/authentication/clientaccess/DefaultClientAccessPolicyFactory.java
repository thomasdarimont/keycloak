package org.keycloak.authentication.clientaccess;

import java.util.List;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;

public class DefaultClientAccessPolicyFactory implements ClientAccessPolicyFactory {

    public static final String PROVIDER_ID = "default";

    @Override
    public ClientAccessPolicy create(KeycloakSession session, ComponentModel model) {
        return new DefaultClientAccessPolicy(session, model);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Grants access when all attached conditions match. Conditions are sub-components of this policy.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }
}
