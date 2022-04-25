package org.keycloak.quarkus.runtime.services.health.realm;

import org.keycloak.health.AbstractHealthIndicator;
import org.keycloak.health.CompositeHealth;
import org.keycloak.health.Health;
import org.keycloak.health.IntermediateHealth;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import java.util.List;

public class LdapHealthIndicator extends AbstractHealthIndicator {

    private final KeycloakSession session;
    private final int defaultConnectTimeoutMillis;

    public LdapHealthIndicator(KeycloakSession session, String name, int defaultConnectTimeoutMillis) {
        super(name);
        this.session = session;
        this.defaultConnectTimeoutMillis = defaultConnectTimeoutMillis;
    }

    @Override
    public boolean isApplicable(RealmModel realm) {
        // TODO check if ldap health checks are enabled for this realm
        // TODO check if the realm has at least one LDAP federation configured
        return super.isApplicable(realm);
    }

    @Override
    public Health check(RealmModel realm) {

        // TODO list all configured ldap federations
        // ping ldap servers

        // CompositeHealth can be used to report multiple "sub-checks" from a health-check, e.g.
        // check each LDAP server for a list of LDAP user federations.

        CompositeHealth status = new CompositeHealth(getName());
        for (String server : List.of("server1", "server2")) {
            status.add(new IntermediateHealth(getName()).withData("server", server));
        }

        return status;
    }
}
