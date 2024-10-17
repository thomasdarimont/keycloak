package org.keycloak.protocol.ssf.caep;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.protocol.ssf.SharedSignalsEventProcessor;
import org.keycloak.protocol.ssf.transmitter.PushTransmitter;

public class CaepEventListenerFactory implements EventListenerProviderFactory {

    @Override
    public String getId() {
        return "caep-event-listener";
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        PushTransmitter pushTransmitter = new PushTransmitter(session);
        SharedSignalsEventProcessor sharedSignalsEventProcessor = new SharedSignalsEventProcessor(session, pushTransmitter);
        return new CaepEventListener(session, sharedSignalsEventProcessor);
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }
}
