package org.keycloak.accesscontrol;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class AccessPolicySpi implements Spi {

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public String getName() {
        return "accessPolicy";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return AccessPolicyProvider.class;
    }

    @Override
    public Class<? extends ProviderFactory<?>> getProviderFactoryClass() {
        return AccessPolicyProviderFactory.class;
    }
}
