package org.keycloak.health;

import org.keycloak.models.RealmModel;

public abstract class AbstractHealthIndicator implements HealthIndicator {

    private final String name;

    public AbstractHealthIndicator(String name) {
        this.name = name;
    }

    public abstract Health check(RealmModel realm);

    @Override
    public void close() {
        //NOOP
    }

    public String getName() {
        return name;
    }
}