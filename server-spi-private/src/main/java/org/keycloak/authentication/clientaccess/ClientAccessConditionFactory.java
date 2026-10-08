package org.keycloak.authentication.clientaccess;

import org.keycloak.Config;
import org.keycloak.component.ComponentFactory;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public interface ClientAccessConditionFactory extends ComponentFactory<ClientAccessCondition, ClientAccessCondition> {

    /** Component config key: boolean, inverts the result of the condition. Evaluated by the policy, not the condition. */
    String CONFIG_NEGATE = "negate";

    ProviderConfigProperty NEGATE_PROPERTY = new ProviderConfigProperty(CONFIG_NEGATE, "Negate",
            "If on, the condition matches when its requirement is NOT fulfilled.", ProviderConfigProperty.BOOLEAN_TYPE, "false");

    @Override
    default void init(Config.Scope config) {
    }

    @Override
    default void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    default void close() {
    }
}
