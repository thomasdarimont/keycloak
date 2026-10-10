package org.keycloak.scim.provisioning.model;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

/** Makes {@code session.getProvider(ScimProvisioningProvider.class)} resolve. */
public class ScimProvisioningSpi implements Spi {

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public String getName() {
        return "scim-provisioning";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return ScimProvisioningProvider.class;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return ScimProvisioningProviderFactory.class;
    }
}
