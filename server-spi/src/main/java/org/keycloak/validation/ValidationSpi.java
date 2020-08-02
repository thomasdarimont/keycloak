package org.keycloak.validation;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

/**
 * An {@link Spi} to provide custom {@link ValidationProvider} implementations.
 */
public class ValidationSpi implements Spi {

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "validation";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return ValidationProvider.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return ValidationProviderFactory.class;
    }
}
