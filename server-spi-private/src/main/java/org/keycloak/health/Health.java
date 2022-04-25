package org.keycloak.health;

import java.util.Map;

public interface Health {

    /**
     * Returns the name of the health-check, e.g. ldap, email etc.
     *
     * @return
     */
    String getName();

    /**
     * Returns the health state.
     *
     * @return
     */
    HealthState getState();

    /**
     * Returns additional data.
     *
     * @return
     */
    Map<String, Object> getData();

}
