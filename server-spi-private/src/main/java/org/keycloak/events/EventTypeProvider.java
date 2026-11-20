package org.keycloak.events;

import java.util.Set;

import org.keycloak.provider.Provider;

/**
 * Provides custom {@link EventType event types}.
 *
 * @see EventTypeProviderFactory
 */
public interface EventTypeProvider extends Provider {

    /**
     * The custom event types. The names must be unique across all event types, so it is recommended to prefix them,
     * for instance {@code ACME_RISK_SCORE} and {@code ACME_RISK_SCORE_ERROR}.
     */
    Set<? extends EventType> getEventTypes();

    @Override
    default void close() {
    }
}
