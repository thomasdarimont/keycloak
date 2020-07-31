package org.keycloak.services.validation.validators;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.validation.validator.ValidatorProvider;
import org.keycloak.validation.validator.ValidatorProviderFactory;

public class DefaultValidatorProviderFactory implements ValidatorProviderFactory {

    // Custom Validator provider factories should be ordered AFTER this, to be able to override default validators.
    public static final int ORDER = 1000;

    private static final DefaultValidatorProvider INSTANCE = new DefaultValidatorProvider();

    @Override
    public ValidatorProvider create(KeycloakSession session) {
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
