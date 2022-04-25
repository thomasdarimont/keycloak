package org.keycloak.health;

import org.keycloak.models.RealmModel;
import org.keycloak.provider.Provider;

public interface HealthIndicator extends Provider {

    /**
     * The name of the health indicator
     *
     * @return
     */
    String getName();

    /**
     * Performs the health check.
     *
     * @param realm
     * @return
     */
    Health check(RealmModel realm);

    /**
     * Determines if the health check is applicable.
     *
     * @param realm
     * @return
     */
    default boolean isApplicable(RealmModel realm) {
        return true;
    }

    default void close() {
    }
}