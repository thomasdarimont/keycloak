package org.keycloak.ssf.caep;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.ssf.caep.events.CredentialChange;
import org.keycloak.ssf.caep.events.SessionEstablished;
import org.keycloak.ssf.caep.events.SessionPresented;
import org.keycloak.ssf.caep.events.SessionRevoked;

public class CaepEventListener implements EventListenerProvider {

    @Override
    public void onEvent(Event event) {

        switch (event.getType()) {
            case LOGIN -> {
                new SessionEstablished();
            }
            case CLIENT_LOGIN -> {
                new SessionPresented();
            }
            case LOGOUT -> {
                new SessionRevoked();
            }
            case UPDATE_PASSWORD -> {
                CredentialChange credentialChange = new CredentialChange();
                credentialChange.setChangeType(CredentialChange.ChangeType.UPDATE);
                credentialChange.setCredentialType(CredentialChange.CredentialType.PASSWORD);
            }
            case UPDATE_TOTP -> {
                CredentialChange credentialChange = new CredentialChange();
                credentialChange.setChangeType(CredentialChange.ChangeType.UPDATE);
                credentialChange.setCredentialType(CredentialChange.CredentialType.FIDO2_U2F);
            }
            case REMOVE_TOTP -> {
                CredentialChange credentialChange = new CredentialChange();
                credentialChange.setChangeType(CredentialChange.ChangeType.REVOKE);
                credentialChange.setCredentialType(CredentialChange.CredentialType.FIDO2_U2F);
            }
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {

    }

    @Override
    public void close() {

    }
}
