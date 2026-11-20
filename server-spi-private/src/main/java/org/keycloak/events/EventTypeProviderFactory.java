package org.keycloak.events;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderFactory;

/**
 * Factory for {@link EventTypeProvider}. Extensions implement this interface to contribute custom
 * {@link EventType event types} and list the implementation in
 * {@code META-INF/services/org.keycloak.events.EventTypeProviderFactory}.
 * <p>
 * The set of event types is static, so the factory is its own provider and only {@link #getId()} and
 * {@link #getEventTypes()} need to be implemented.
 * <p>
 * The event types of all enabled factories are registered when the Keycloak session factory is initialized, see
 * {@link EventTypeRegistry}.
 */
public interface EventTypeProviderFactory extends ProviderFactory<EventTypeProvider>, EventTypeProvider {

    @Override
    default EventTypeProvider create(KeycloakSession session) {
        return this;
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
