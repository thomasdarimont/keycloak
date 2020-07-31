package org.keycloak.services.validation.validators;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.validation.ValidationProvider;
import org.keycloak.validation.ValidationProviderFactory;

public class DefaultValidationProviderFactory implements ValidationProviderFactory {

    // Custom Validation provider factories should be ordered AFTER this, to be able to override default validators.
    public static final int ORDER = 1000;

    private static final DefaultValidationProvider INSTANCE = new DefaultValidationProvider();

    @Override
    public ValidationProvider create(KeycloakSession session) {
        return INSTANCE;
    }

    @Override
    public void init(Config.Scope config) {
        // NOOP
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // NOOP
    }

    @Override
    public void close() {
        // NOOP
    }

    @Override
    public String getId() {
        return "default";
    }

    @Override
    public int order() {
        return ORDER;
    }
}
