package org.keycloak.protocol.ssf.caep;

import org.keycloak.authentication.AuthenticatorUtil;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.ssf.set.SecurityEvent;
import org.keycloak.protocol.ssf.SsfEventProcessor;
import org.keycloak.protocol.ssf.caep.events.CredentialChange;
import org.keycloak.protocol.ssf.caep.events.SessionEstablished;
import org.keycloak.protocol.ssf.caep.events.SessionPresented;
import org.keycloak.protocol.ssf.caep.events.SessionRevoked;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.Map;
import java.util.Set;

public class CaepEventListener implements EventListenerProvider {

    private final KeycloakSession session;

    private final SsfEventProcessor ssfEventProcessor;

    public CaepEventListener(KeycloakSession session, SsfEventProcessor ssfEventProcessor) {
        this.session = session;
        this.ssfEventProcessor = ssfEventProcessor;
    }

    @Override
    public void onEvent(Event userEvent) {

        KeycloakContext context = session.getContext();
        AuthenticationSessionModel authSession = context.getAuthenticationSession();

        boolean newLogin = authSession != null && !AuthenticatorUtil.isSSOAuthentication(authSession);

        SecurityEvent securityEvent = switch (userEvent.getType()) {
            case LOGIN-> {

                if (newLogin) {
                    SessionEstablished event = new SessionEstablished();
                    event.setIps(Set.of(context.getConnection().getRemoteAddr()));
                    // TODO add AMR information to auth session
                    // sessionEstablished.setAmr(...);
                    // TODO add ACR information to auth session
                    // sessionEstablished.setAcr(...);
                    // TODO how to compute browser fingerprint
                    // sessionEstablished.setFingerPrintUserAgent(...);
                    event.setReasonUser(Map.of("en", "New user session", "de", "Neue Benutzersitzung"));
                    yield event;
                } else {
                    SessionPresented event = new SessionPresented();
                    event.setIps(Set.of(context.getConnection().getRemoteAddr()));
                    event.setReasonUser(Map.of("en", "User session updated", "de", "Benutzersitzung aktualisiert"));
                    yield event;
                }
            }
            case LOGOUT -> {
                SessionRevoked event = new SessionRevoked();
                event.setReasonUser(Map.of("en", "User session ended", "de", "Benutzersitzung beendet"));
                yield event;
            }
            case UPDATE_PASSWORD -> {
                CredentialChange event = new CredentialChange();
                event.setChangeType(CredentialChange.ChangeType.UPDATE);
                event.setCredentialType(CredentialChange.CredentialType.PASSWORD);
                event.setReasonUser(Map.of("en", "Password updated", "de", "Passwort aktualisiert"));
                yield event;
            }
            case UPDATE_TOTP -> {
                CredentialChange event = new CredentialChange();
                event.setChangeType(CredentialChange.ChangeType.UPDATE);
                event.setCredentialType(CredentialChange.CredentialType.FIDO2_U2F);
                event.setReasonUser(Map.of("en", "FIDO2 device updated", "de", "FIDO2 Gerät aktualisiert"));
                yield event;
            }
            case REMOVE_TOTP -> {
                CredentialChange event = new CredentialChange();
                event.setChangeType(CredentialChange.ChangeType.REVOKE);
                event.setCredentialType(CredentialChange.CredentialType.FIDO2_U2F);
                event.setReasonUser(Map.of("en", "FIDO2 device removed", "de", "FIDO2 Gerät entfernt"));
                yield event;
            }
            default -> null;
        };

        if (securityEvent == null) {
            return;
        }

        securityEvent.setEventTimestamp(userEvent.getTime());
        securityEvent.setInitiatingEntity(SecurityEvent.InitiatingEntity.USER);

        UserModel user = resolveUser(userEvent, authSession);

        ssfEventProcessor.process(securityEvent, user);
    }

    protected UserModel resolveUser(Event userEvent, AuthenticationSessionModel authSession) {
        if (authSession != null) {
            return authSession.getAuthenticatedUser();
        }
        if (userEvent.getUserId() != null) {
            return session.users().getUserById(session.getContext().getRealm(), userEvent.getUserId());
        }
        return null;
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {

    }

    @Override
    public void close() {

    }
}
