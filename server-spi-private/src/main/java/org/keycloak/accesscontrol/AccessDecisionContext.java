package org.keycloak.accesscontrol;

import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class AccessDecisionContext {

    private final KeycloakSession session;

    private final RealmModel realm;

    private final UserModel user;

    private final ClientModel client;

    public AccessDecisionContext(KeycloakSession session, RealmModel realm, UserModel user, ClientModel client) {
        this.session = session;
        this.realm = realm;
        this.user = user;
        this.client = client;
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

    public KeycloakSession getSession() {
        return session;
    }
}
