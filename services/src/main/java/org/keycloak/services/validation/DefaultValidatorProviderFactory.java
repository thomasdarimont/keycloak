package org.keycloak.services.validation;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.validation.ValidationProvider;
import org.keycloak.validation.ValidationRegistry;
import org.keycloak.validation.ValidatorProvider;
import org.keycloak.validation.ValidatorProviderFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DefaultValidatorProviderFactory implements ValidatorProviderFactory {

    private ValidationRegistry validatorRegistry;

    @Override
    public ValidatorProvider create(KeycloakSession session) {
        return new DefaultValidatorProvider(session, validatorRegistry);
    }

    @Override
    public void init(Config.Scope config) {
        // NOOP
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
        // TODO discuss lazily constructing the ValidatorRegistry in #create(KeycloakSession) method instead of here
        this.validatorRegistry = createValidatorRegistry(keycloakSessionFactory);
    }

    protected ValidationRegistry createValidatorRegistry(KeycloakSessionFactory keycloakSessionFactory) {

        // TODO fix generics
        DefaultValidationRegistry validatorRegistry = new DefaultValidationRegistry();

        KeycloakSession keycloakSession = keycloakSessionFactory.create();

        List<ProviderFactory> providerFactories = keycloakSessionFactory.getProviderFactories(ValidationProvider.class);

        Collections.sort(providerFactories, Comparator.comparing(ProviderFactory::order));

        for (ProviderFactory providerFactory : providerFactories) {
            providerFactory.postInit(keycloakSessionFactory);
            ValidationProvider validatorProvider = (ValidationProvider) providerFactory.create(keycloakSession);
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
