package org.keycloak.tests.scim.provisioning;

import java.time.Duration;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.common.Profile;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testframework.annotations.InjectHttpServer;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.keycloak.tests.scim.provisioning.mock.Keys;
import org.keycloak.tests.scim.provisioning.mock.MockScimServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@KeycloakIntegrationTest(config = SmokeTest.ServerConfig.class)
public class SmokeTest {

    @InjectRealm
    ManagedRealm realm;

    @InjectHttpServer
    HttpServer httpServer;

    MockScimServer mock;

    @BeforeEach
    void attach() {
        mock = MockScimServer.attach(httpServer, "/scim-mock");
    }

    @AfterEach
    void detach() {
        mock.close();
    }

    @Test
    void aUserInScopeIsCreatedInTheApp() {
        RealmResource r = realm.admin();
        GroupRepresentation g = new GroupRepresentation();
        g.setName("Engineering");
        String groupId;
        try (Response resp = r.groups().add(g)) {
            groupId = resp.getLocation().getPath().replaceAll(".*/", "");
        }

        String key = Keys.unique("atlassian");
        ComponentRepresentation c = new ComponentRepresentation();
        c.setName("app");
        c.setProviderId("scim-provisioning");
        c.setProviderType("org.keycloak.storage.UserStorageProvider");
        c.setParentId(realm.getId());
        c.setConfig(new MultivaluedHashMap<>());
        c.getConfig().put("baseUrl", List.of(mock.scimBaseUrl(key)));
        c.getConfig().put("token", List.of("t"));
        c.getConfig().put("scopeGroups", List.of("Engineering"));
        try (Response resp = r.components().add(c)) {
            assertEquals(201, resp.getStatus(), () -> resp.readEntity(String.class));
        }

        UserRepresentation u = new UserRepresentation();
        u.setUsername("ann");
        u.setEmail("ann@example.com");
        u.setFirstName("Ann");
        u.setLastName("Lee");
        u.setEnabled(true);
        String userId;
        try (Response resp = r.users().create(u)) {
            userId = resp.getLocation().getPath().replaceAll(".*/", "");
        }
        r.users().get(userId).joinGroup(groupId);

        JsonNode remote = await().atMost(Duration.ofSeconds(30))
                .until(() -> mock.admin(key).userByUserName("ann"), x -> x != null);
        assertEquals(userId, remote.path("externalId").asText());
        assertEquals("Ann Lee", remote.path("displayName").asText());
        assertEquals("Ann", remote.path("name").path("givenName").asText());
        assertEquals("ann@example.com", remote.path("emails").path(0).path("value").asText());
        assertEquals(true, remote.path("active").asBoolean());
    }

    public static class ServerConfig implements KeycloakServerConfig {
        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            config.features(Profile.Feature.SCIM_PROVISIONING)
                    .spiOption("events-listener", "scim-provisioning", "drain-interval-millis", "500");
            config.log().categoryLevel("org.keycloak.scim.provisioning", "DEBUG");
            return config;
        }
    }
}
