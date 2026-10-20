package org.keycloak.tests.scim.provisioning;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.tests.scim.provisioning.mock.Keys;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The destination is a user-storage provider type, so the console's <i>User federation → Add custom provider</i> page
 * lists it and renders its form from the properties served here. Also the save-time validation and connection check.
 */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class ConsoleSmokeTest extends AbstractScimProvisioningTest {

    @Test
    public void serverInfoOffersTheDestinationFormUnderUserFederation() {
        JsonNode types = admin.get("/admin/serverinfo").ok().body().get("componentTypes")
                .get("org.keycloak.storage.UserStorageProvider");
        JsonNode ours = null;
        for (JsonNode t : types) {
            if (t.get("id").asText().equals("scim-provisioning")) {
                ours = t;
            }
        }
        assertNotNull(ours, "scim-provisioning listed as a user-storage provider type");
        assertThat(ours.get("helpText").asText(), containsString("SCIM"));
        Map<String, JsonNode> props = new HashMap<>();
        for (JsonNode p : ours.get("properties")) {
            props.put(p.get("name").asText(), p);
        }
        for (String name : List.of("baseUrl", "token", "scopeGroups", "scopeEveryone", "mapping", "deprovision",
                "supportsGroups", "profile", "requestTimeoutSeconds", "maxAttempts", "reconcileHours",
                "skipConnectionCheck")) {
            assertThat(props, hasKey(name));
        }
        assertEquals("Password", props.get("token").get("type").asText());
        assertTrue(props.get("token").get("secret").asBoolean());
        assertEquals("MultivaluedString", props.get("mapping").get("type").asText());
        // no synchronization settings: the destination imports nothing
        assertFalse(ours.path("metadata").path("synchronizable").asBoolean(false));
    }

    @Test
    public void theTokenIsMaskedInAdminResponses() {
        Dest d = destination("atlassian", cfg("scopeEveryone", "true"));
        JsonNode c = admin.component(realm, d.id);
        assertEquals("**********", c.get("config").get("token").get(0).asText());
    }

    @Test
    public void invalidConfigurationIsRefused() {
        ProvisioningAdmin.Response r = admin.createDestination(realm, "bad",
                Map.of("baseUrl", List.of("not a url"), "token", List.of("t")));
        assertEquals(400, r.status());
        assertThat(r.text(), containsString("baseUrl"));

        String key = Keys.unique("atlassian");
        r = admin.createDestination(realm, "bad2", Map.of("baseUrl", List.of(scimBaseUrl(key)), "token", List.of("t"),
                "mapping", List.of("title=jobTitle")));
        assertEquals(400, r.status());
        assertThat(r.text(), containsString("jobTitle"));

        r = admin.createDestination(realm, "bad3", Map.of("baseUrl", List.of(scimBaseUrl(key)), "token", List.of("t"),
                "scopeGroups", List.of("/NoSuchGroup")));
        assertEquals(400, r.status());
        assertThat(r.text(), containsString("NoSuchGroup"));
    }

    @Test
    public void connectionCheckOnSaveReportsARejectedToken() {
        String key = Keys.unique("atlassian");
        mockServer.admin(key).config(Map.of("token", "right"));
        ProvisioningAdmin.Response r = admin.createDestination(realm, "wrong-token",
                Map.of("baseUrl", List.of(scimBaseUrl(key)), "token", List.of("wrong")));
        assertEquals(400, r.status());
        assertThat(r.text(), containsString("token was rejected"));

        // the right token saves
        assertEquals(201, admin.createDestination(realm, "right-token",
                Map.of("baseUrl", List.of(scimBaseUrl(key)), "token", List.of("right"))).status());
    }

    @Test
    public void connectionCheckIsCappedAndCanBeSkipped() {
        Map<String, List<String>> c = Map.of("baseUrl", List.of("http://10.255.255.1:81/scim/v2"), "token",
                List.of("t"));
        long t0 = System.currentTimeMillis();
        ProvisioningAdmin.Response r = admin.createDestination(realm, "unreachable", c);
        long took = System.currentTimeMillis() - t0;
        assertEquals(400, r.status());
        assertThat(r.text(), containsString("could not be reached"));
        assertThat("5 s cap", took, lessThan(9_000L));

        Map<String, List<String>> skip = Map.of("baseUrl", List.of("http://10.255.255.1:81/scim/v2"), "token",
                List.of("t"), "skipConnectionCheck", List.of("true"));
        assertEquals(201, admin.createDestination(realm, "skipped", skip).status());
    }

    @Test
    public void wrongBasePathIsCaughtOnSave() {
        String key = Keys.unique("atlassian");
        String wrong = scimBaseUrl(key).replace("/scim/v2", "/nope");
        ProvisioningAdmin.Response r = admin.createDestination(realm, "wrong-path",
                Map.of("baseUrl", List.of(wrong), "token", List.of("t")));
        assertEquals(400, r.status());
        assertThat(r.text(), containsString("does not look like a SCIM endpoint"));
    }
}
