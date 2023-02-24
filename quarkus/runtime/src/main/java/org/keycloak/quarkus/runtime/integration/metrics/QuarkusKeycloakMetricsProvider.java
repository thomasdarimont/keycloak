package org.keycloak.quarkus.runtime.integration.metrics;


import org.hibernate.jpa.QueryHints;
import org.jboss.logging.Logger;
import org.keycloak.common.Version;
import org.keycloak.events.Event;
import org.keycloak.models.metrics.KeycloakMetric;
import org.keycloak.metrics.KeycloakMetricsProvider;
import org.keycloak.models.RealmModel;
import org.keycloak.models.metrics.Type;
import org.keycloak.models.metrics.RealmReference;
import org.keycloak.models.utils.KeycloakModelUtils;

import javax.enterprise.inject.spi.CDI;
import javax.persistence.EntityManager;
import java.util.List;

public class QuarkusKeycloakMetricsProvider implements KeycloakMetricsProvider {

    private final static Logger LOG = Logger.getLogger(QuarkusKeycloakMetricsProvider.class);

    @Override
    public List<KeycloakMetric> createMetrics() {

        // Example for a custom metric on instance level that provides metadata via labels
        var instanceMetadata = new KeycloakMetric<>("keycloak_instance_metadata", "Keycloak instance metadata", //
                Type.INSTANCE,//
                context -> 0, //
                source -> new String[]{"version", Version.VERSION});

        // Example for a custom metric on instance level that provides a global metric
        var realmCount = new KeycloakMetric<>("keycloak_realms_total", "Total number of realms", //
                Type.INSTANCE, //
                context -> {
                    // TODO use more general and efficient way to count number of realms
                    LOG.debugf("Updating realm count");
                    EntityManager em = CDI.current().select(EntityManager.class).get();
                    Number count = (Number) em.createQuery("select count(r) from RealmEntity r") //
                            .setHint(QueryHints.HINT_READONLY, true) //
                            .getSingleResult();
                    LOG.debugf("Updated realm count");
                    return count;
                }, //
                source -> new String[0]);

        // Example for a custom metric on instance level that provides a global metric
        var userCount = new KeycloakMetric<>("keycloak_users_total", "Total number of users", //
                Type.INSTANCE, //
                context -> {
                    // TODO use more general and efficient way to count number of users
                    LOG.debugf("Updating user count");
                    EntityManager em = CDI.current().select(EntityManager.class).get();
                    Number count = (Number) em.createQuery("select count(u) from UserEntity u") //
                            .setHint(QueryHints.HINT_READONLY, true) //
                            .getSingleResult();
                    LOG.debugf("Updated user count");
                    return count;
                }, //
                source -> new String[0]);

        // Example for a custom metric on realm level that provides a realm scoped metric
        var realmClientCount = new KeycloakMetric<RealmModel>("keycloak_clients", "Total number of clients per realm", //
                Type.REALM, //
                context -> {
                    RealmReference realmReference = context.getRealmReference();
                    return KeycloakModelUtils.runJobInTransactionWithResult(context.getSessionFactory(), session -> {
                        RealmModel realm = session.realms().getRealm(realmReference.getId());
                        return realm.getClientsCount();
                    });
                }, //
                realm -> new String[]{"realm", realm.getName(), "realmId", realm.getId()});

        // Example for a custom metric on realm level that provides a realm scoped metric
        var realmUsersCount = new KeycloakMetric<RealmModel>("keycloak_users", "Total number of users per realm", //
                Type.REALM, //
                context -> {
                    RealmReference realmReference = context.getRealmReference();
                    return KeycloakModelUtils.runJobInTransactionWithResult(context.getSessionFactory(), session -> {
                        RealmModel realm = session.realms().getRealm(realmReference.getId());
                        return session.users().getUsersCount(realm);
                    });
                }, //
                realm -> new String[]{"realm", realm.getName(), "realmId", realm.getId()});

        // Example for a customizing a user event metric on realm level that provides a realm scoped metric
        var userLoginTotal = new KeycloakMetric<Event>("keycloak_userevent_login_total", "Total number of user logins per realm",//
                Type.USER_EVENT, //
                context -> 1, //
                event -> {
                    return new String[]{"realmId", event.getRealmId(), "client_id", event.getClientId()};
                });

        return List.of(instanceMetadata, realmCount, userCount, realmClientCount, realmUsersCount, userLoginTotal);
    }
}
