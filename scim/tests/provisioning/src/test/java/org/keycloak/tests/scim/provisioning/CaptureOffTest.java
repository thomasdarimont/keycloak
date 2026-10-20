package org.keycloak.tests.scim.provisioning;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event-capture opt-out: with event capture off, nothing is marked by events, and the reconcile (here run on
 * demand) still provisions — "the periodic reconcile catches everything".
 */
@KeycloakIntegrationTest(config = CaptureOffTest.CaptureOffConfig.class)
public class CaptureOffTest extends AbstractScimProvisioningTest {

    public static class CaptureOffConfig extends ScimProvisioningServerConfig {
        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            return provisioning(super.configure(config), "capture-events", "false");
        }
    }

    @Test
    public void withoutEventsTheReconcileStillProvisions() {
        Dest d = destination("atlassian", cfg("scopeEveryone", "true"));
        String u = user("tess");
        pause(3000);
        assertNull(d.mock.userByUserName("tess"), "no event capture");
        assertNull(d.entry("user:" + u));

        admin.post(provisioning("reconcile?destination=" + d.id), null).ok();
        d.awaitUser("tess");
        d.awaitDone("user:" + u);
        assertTrue(ServerLog.sinceClassStart().stream().anyMatch(l -> l.contains("event capture is off")));
    }
}
