package org.keycloak.scim.provisioning.listener;

import java.util.List;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.scim.provisioning.ScimProvisioning;
import org.keycloak.scim.provisioning.Settings;
import org.keycloak.scim.provisioning.drainer.DrainScheduler;

import org.jboss.logging.Logger;

/**
 * The {@code scim-provisioning} event listener. Global: it needs no per-realm registration.
 * Opt out of event capture with {@code --spi-events-listener--scim-provisioning--capture-events=false}:
 * the drainer, resync and the scheduled reconcile keep running, so provisioning continues on the
 * reconcile schedule. (Disabling the factory itself with {@code ...--enabled=false} would stop the
 * drainer too.)
 *
 * <p>Its SPI scope also carries the node-wide tuning ({@link Settings}). Its {@code postInit}
 * registers the model-event bridge, and starts the drainer once the database is migrated: {@code
 * postInit} runs before Liquibase, and on a new database our tables do not exist yet.
 */
public class ScimProvisioningEventListenerFactory implements EventListenerProviderFactory, EnvironmentDependentProviderFactory {
    private static final Logger log = Logger.getLogger(ScimProvisioningEventListenerFactory.class);

    public static final String ID = ScimProvisioning.ID;

    private static final EventListenerProvider OFF =
            new EventListenerProvider() {
                @Override
                public void onEvent(org.keycloak.events.Event event) {}

                @Override
                public void onEvent(
                        org.keycloak.events.admin.AdminEvent event, boolean includeRepresentation) {}

                @Override
                public void close() {}
            };

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return Settings.get().captureEvents() ? new ScimProvisioningEventListener(session) : OFF;
    }

    @Override
    public void init(Config.Scope config) {
        Settings.set(Settings.from(config));
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        if (Settings.get().captureEvents()) factory.register(new ModelEventBridge());
        factory.register(
                event -> {
                    if (event instanceof PostMigrationEvent pme) {
                        DrainScheduler.start(pme.getFactory(), Settings.get());
                    }
                });
        if (Settings.get().captureEvents()) {
            log.infof(
                    "scim-provisioning: enqueue mode %s, drain every %d ms",
                    Settings.get().enqueueMode().code(), Settings.get().drainInterval().toMillis());
        } else {
            log.infof(
                    "scim-provisioning: event capture is off; provisioning follows resync and reconcile only");
        }
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    public void close() {
        DrainScheduler.stop();
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return Settings.metadata();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return ScimProvisioning.isEnabled();
    }
}
