package org.keycloak.tests.scim.provisioning;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/** The meters appear on Keycloak's own metrics endpoint (the management interface). */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class MetricsTest extends AbstractScimProvisioningTest {

    private String metrics() {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(keycloakUrls.getMetric())).GET().build();
            return HttpClient.newHttpClient().send(r, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void deliveriesBacklogAndFailuresAreExported() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String u = user("sven");
        admin.joinGroup(realm, u, g);
        d.awaitDone("user:" + u);
        d.mock.config(Map.of("status", 503));
        try {
            admin.updateUser(realm, u, Map.of("firstName", "Svenn"));
            until(WAIT, () -> metrics().contains("keycloak_scim_provisioning_failed_total{class=\"server_error\""));
        } finally {
            d.mock.heal();
        }
        String m = metrics();
        assertThat(m, containsString("keycloak_scim_provisioning_delivered_total{"));
        assertThat(m, containsString("keycloak_scim_provisioning_backlog{"));
        assertThat(m, containsString("keycloak_scim_provisioning_delivery_seconds"));
        assertThat(m, containsString("destination=\"atlassian-dest\""));
    }
}
