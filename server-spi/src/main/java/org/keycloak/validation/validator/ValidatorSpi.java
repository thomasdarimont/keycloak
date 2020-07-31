package org.keycloak.validation.validator;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class ValidatorSpi implements Spi {

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "validator";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return ValidatorProvider.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return ValidatorProviderFactory.class;
    }
}