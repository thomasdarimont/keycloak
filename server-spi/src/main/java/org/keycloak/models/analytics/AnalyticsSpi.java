package org.keycloak.models.analytics;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

/**
 * Created by tom on 27.05.16.
 */
public class AnalyticsSpi implements Spi {

    public static final String NAME = "analytics";

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
        return AnalyticsProvider.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return AnalyticsProviderFactory.class;
    }
}
