package org.keycloak.scim.provisioning.drainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.scim.provisioning.Settings;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.component.Destinations;
import org.keycloak.scim.provisioning.model.DestinationRef;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;

import org.jboss.logging.Logger;

/**
 * Startup and periodic housekeeping:
 *
 * <ul>
 *   <li>a destination without a health row gets one — and, being new to us (for instance imported
 *       with a realm, which bypasses {@code onCreate}), a resync;
 *   <li>rows whose destination component no longer exists are deleted.
 * </ul>
 */
final class Sweeper {
    private static final Logger log = Logger.getLogger(Sweeper.class);


    private final KeycloakSessionFactory factory;
    private final Settings settings;
    private volatile long lastSweep;

    Sweeper(KeycloakSessionFactory factory, Settings settings) {
        this.factory = factory;
        this.settings = settings;
    }

    void maybeSweep() {
        long now = System.currentTimeMillis();
        if (lastSweep != 0 && now - lastSweep < settings.sweepInterval().toMillis()) return;
        lastSweep = now;
        try {
            sweep(now);
        } catch (RuntimeException e) {
            log.warnf("scim-provisioning: housekeeping sweep failed: %s", e.toString());
            log.debug("sweep failure", e);
        }
    }

    void sweep(long now) {
        List<String[]> created = new ArrayList<>();
        KeycloakModelUtils.runJobInTransaction(
                factory,
                session -> {
                    ScimProvisioningProvider outbox = session.getProvider(ScimProvisioningProvider.class);
                    Set<DestinationRef> live = new HashSet<>();
                    session
                            .realms()
                            .getRealmsStream()
                            .forEach(
                                    (RealmModel realm) -> {
                                        for (Destination d : Destinations.of(realm)) {
                                            live.add(new DestinationRef(realm.getId(), d.id()));
                                            if (outbox.ensureHealth(realm.getId(), d.id(), now)) {
                                                created.add(new String[] {realm.getId(), d.id()});
                                            }
                                        }
                                    });
                    for (DestinationRef known : outbox.getKnownDestinations()) {
                        if (!live.contains(known)) {
                            log.infof(
                                    "scim-provisioning: removing rows of deleted destination %s", known.destinationId());
                            outbox.removeDestination(known.realmId(), known.destinationId());
                        }
                    }
                });
        for (String[] c : created) {
            log.infof("scim-provisioning: destination %s is new to this database; resynchronizing", c[1]);
            ResyncHook.request(factory, c[0], c[1]);
        }
    }
}
