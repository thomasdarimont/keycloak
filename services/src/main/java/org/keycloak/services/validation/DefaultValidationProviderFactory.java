package org.keycloak.services.validation;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.services.validation.validators.DefaultValidatorRegistry;
import org.keycloak.validation.ValidationProvider;
import org.keycloak.validation.ValidationProviderFactory;
import org.keycloak.validation.validator.ValidatorProvider;
import org.keycloak.validation.validator.ValidatorRegistry;

import java.util.List;

public class DefaultValidationProviderFactory implements ValidationProviderFactory {

    private ValidationProvider instance;

    @Override
    public ValidationProvider create(KeycloakSession session) {
        return instance;
    }

    @Override
    public void init(Config.Scope config) {
        // NOOP
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
        ValidatorRegistry validatorRegistry = createValidatorRegistry(keycloakSessionFactory);
        instance = new DefaultValidationProvider(validatorRegistry);
    }

    protected ValidatorRegistry createValidatorRegistry(KeycloakSessionFactory keycloakSessionFactory) {
        ValidatorRegistry validatorRegistry = new DefaultValidatorRegistry();

        KeycloakSession keycloakSession = keycloakSessionFactory.create();
        List<ProviderFactory> providerFactories = keycloakSessionFactory.getProviderFactories(ValidatorProvider.class);
        for (ProviderFactory providerFactory : providerFactories) {
            providerFactory.postInit(keycloakSessionFactory);
            ValidatorProvider validatorProvider = (ValidatorProvider) providerFactory.create(keycloakSession);
            validatorProvider.register(validatorRegistry);
        }
        return validatorRegistry;
    }

    @Override
    public void close() {
        // NOOP
    }

    @Override
    public String getId() {
        return "default";
    }
}
