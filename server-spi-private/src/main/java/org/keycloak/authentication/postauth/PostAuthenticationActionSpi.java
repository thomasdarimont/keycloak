package org.keycloak.authentication.postauth;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class PostAuthenticationActionSpi implements Spi {

    public static final String NAME = "post-authentication-action";

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return PostAuthenticationAction.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return PostAuthenticationActionFactory.class;
    }
}
