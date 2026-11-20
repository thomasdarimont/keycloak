package org.keycloak.events;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class EventTypeSpi implements Spi {

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public String getName() {
        return "eventsType";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return EventTypeProvider.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return EventTypeProviderFactory.class;
    }
}
