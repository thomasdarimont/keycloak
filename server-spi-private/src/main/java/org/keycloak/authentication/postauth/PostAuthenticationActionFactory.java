package org.keycloak.authentication.postauth;

import java.util.EnumSet;
import java.util.Set;

import org.keycloak.Config;
import org.keycloak.component.ComponentFactory;
import org.keycloak.models.KeycloakSessionFactory;

public interface PostAuthenticationActionFactory extends ComponentFactory<PostAuthenticationAction, PostAuthenticationAction> {

    /** Component config key: integer, lower runs first. */
    String CONFIG_PRIORITY = "priority";

    /** Component config key: boolean, defaults to true. */
    String CONFIG_ENABLED = "enabled";

    /** Component config key: multivalued {@link PostAuthenticationTrigger} names, defaults to all supported triggers. */
    String CONFIG_TRIGGERS = "triggers";

    /**
     * @return triggers this action can run on. A configured instance may narrow this via {@link #CONFIG_TRIGGERS}.
     */
    default Set<PostAuthenticationTrigger> getSupportedTriggers() {
        return EnumSet.allOf(PostAuthenticationTrigger.class);
    }

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
