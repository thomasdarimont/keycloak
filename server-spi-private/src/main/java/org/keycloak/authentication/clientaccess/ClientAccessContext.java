package org.keycloak.authentication.clientaccess;

import org.keycloak.authentication.postauth.PostAuthenticationTrigger;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * The subject and the client a {@link ClientAccessPolicy} is evaluated for.
 */
public class ClientAccessContext {

    private final KeycloakSession session;
    private final RealmModel realm;
    private final ClientModel client;
    private final UserModel user;
    private final PostAuthenticationTrigger trigger;

    public ClientAccessContext(KeycloakSession session, RealmModel realm, ClientModel client, UserModel user, PostAuthenticationTrigger trigger) {
        this.session = session;
        this.realm = realm;
        this.client = client;
        this.user = user;
        this.trigger = trigger;
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
}
