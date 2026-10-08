package org.keycloak.authentication.postauth;

import org.keycloak.events.Details;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Everything a {@link PostAuthenticationAction} may look at. Which of the session objects are present depends on the
 * {@link PostAuthenticationTrigger}:
 * <ul>
 * <li>Browser login, broker login, direct grant and client credentials run inside the authentication session, after the
 * user was established on it and before the user session and client session for this login are created. A denial
 * therefore leaves no session behind. Notes set via {@code authSession.setUserSessionNote} are copied to the user
 * session once it is attached.</li>
 * <li>Browser SSO additionally carries the existing user session and, if the client already has one, its client
 * session.</li>
 * <li>Token refresh has no authentication session; it carries user session and client session, and a denial removes
 * that client session.</li>
 * </ul>
 */
public class PostAuthenticationContext {

    private final KeycloakSession session;
    private final RealmModel realm;
    private final ClientModel client;
    private final UserModel user;
    private final PostAuthenticationTrigger trigger;
    private final AuthenticationSessionModel authSession;
    private final UserSessionModel userSession;
    private final AuthenticatedClientSessionModel clientSession;
    private final String identityProviderAlias;

    private PostAuthenticationContext(Builder b) {
        this.session = b.session;
        this.realm = b.realm;
        this.client = b.client;
        this.user = b.user;
        this.trigger = b.trigger;
        this.authSession = b.authSession;
        this.userSession = b.userSession;
        this.clientSession = b.clientSession;
        this.identityProviderAlias = b.identityProviderAlias;
    }

    public static Builder builder(KeycloakSession session, PostAuthenticationTrigger trigger) {
        return new Builder(session, trigger);
    }

    public KeycloakSession getSession() {
        return session;
    }

    public RealmModel getRealm() {
        return realm;
    }

    public ClientModel getClient() {
        return client;
    }

    public UserModel getUser() {
        return user;
    }

    public PostAuthenticationTrigger getTrigger() {
        return trigger;
    }

    public boolean isInteractive() {
        return trigger.isInteractive();
    }

    /** @return the authentication session, {@code null} for {@link PostAuthenticationTrigger#TOKEN_REFRESH} */
    public AuthenticationSessionModel getAuthSession() {
        return authSession;
    }

    /** @return the user session if one exists already, may be {@code null} */
    public UserSessionModel getUserSession() {
        return userSession;
    }

    /** @return the client session if one exists already, may be {@code null} */
    public AuthenticatedClientSessionModel getClientSession() {
        return clientSession;
    }

    /**
     * @return alias of the identity provider the user authenticated with, {@code null} unless the trigger is
     * {@link PostAuthenticationTrigger#BROKER_LOGIN}
     */
    public String getIdentityProviderAlias() {
        return identityProviderAlias;
    }

    /**
     * @return {@code true} if the authentication session carries the identity provider note that the brokering
     * code sets once the user authenticated through an identity provider in this session
     */
    public static boolean isBrokeredLogin(AuthenticationSessionModel authSession) {
        return authSession != null && authSession.getUserSessionNotes().get(Details.IDENTITY_PROVIDER) != null;
    }

    public static final class Builder {
        private final KeycloakSession session;
        private final PostAuthenticationTrigger trigger;
        private RealmModel realm;
        private ClientModel client;
        private UserModel user;
        private AuthenticationSessionModel authSession;
        private UserSessionModel userSession;
        private AuthenticatedClientSessionModel clientSession;
        private String identityProviderAlias;

        private Builder(KeycloakSession session, PostAuthenticationTrigger trigger) {
            this.session = session;
            this.trigger = trigger;
            this.realm = session.getContext().getRealm();
        }

        public Builder realm(RealmModel realm) {
            this.realm = realm;
            return this;
        }

        public Builder client(ClientModel client) {
            this.client = client;
            return this;
        }

        public Builder user(UserModel user) {
            this.user = user;
            return this;
        }

        public Builder authSession(AuthenticationSessionModel authSession) {
            this.authSession = authSession;
            if (authSession != null) {
                if (client == null) client = authSession.getClient();
                if (user == null) user = authSession.getAuthenticatedUser();
                if (realm == null) realm = authSession.getRealm();
                if (identityProviderAlias == null) {
                    identityProviderAlias = authSession.getUserSessionNotes().get(Details.IDENTITY_PROVIDER);
                }
            }
            return this;
        }

        public Builder userSession(UserSessionModel userSession) {
            this.userSession = userSession;
            if (userSession != null) {
                if (user == null) user = userSession.getUser();
                if (realm == null) realm = userSession.getRealm();
            }
            return this;
        }

        public Builder clientSession(AuthenticatedClientSessionModel clientSession) {
            this.clientSession = clientSession;
            if (clientSession != null && client == null) client = clientSession.getClient();
            return this;
        }

        public PostAuthenticationContext build() {
            if (realm == null || client == null || user == null) {
                throw new IllegalStateException("realm, client and user are required for a post-authentication context");
            }
            if (clientSession == null && userSession != null) {
                // SSO re-authentication into a client that already has a client session on the user session
                clientSession = userSession.getAuthenticatedClientSessionByClient(client.getId());
            }
            return new PostAuthenticationContext(this);
        }
    }
}
