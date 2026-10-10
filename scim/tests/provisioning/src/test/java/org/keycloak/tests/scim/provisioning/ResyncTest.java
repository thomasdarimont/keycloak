package org.keycloak.tests.scim.provisioning;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.tests.scim.provisioning.mock.Keys;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A resync of 1 000 unchanged users sends zero write requests. Also: creating a destination resyncs it, a scope change
 * on save resyncs it, and a destination imported with its realm (which bypasses {@code onCreate}) is resynced by the
 * startup sweep.
 */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class ResyncTest extends AbstractScimProvisioningTest {

    private void importUsers(int n, String prefix) {
        List<Map<String, Object>> users = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("username", prefix + i);
            u.put("email", prefix + i + "@example.com");
            u.put("firstName", "First" + i);
            u.put("lastName", "Last" + i);
            u.put("enabled", true);
            users.add(u);
        }
        admin.post("/admin/realms/" + realm + "/partialImport", Map.of("ifResourceExists", "FAIL", "users", users))
                .ok();
    }

    private JsonNode status(Dest d) {
        for (JsonNode s : admin.get(provisioning("status")).ok().body()) {
            if (s.get("id").asText().equals(d.id)) {
                return s;
            }
        }
        throw new AssertionError("no status");
    }

    @Test
    public void resyncOfAThousandUnchangedUsersSendsNoWrites() {
        importUsers(1000, "bulk");
        // creating the destination resyncs it: everyone is provisioned
        Dest d = destination("atlassian", cfg("scopeEveryone", "true"));
        until(Duration.ofMinutes(5), () -> d.mock.users().size() == 1000);
        until(Duration.ofMinutes(2), () -> status(d).get("backlog").asInt() == 0);
        int writes = d.mock.writes().size();
        assertEquals(1000, writes);

        String probe = "user:" + admin.get("/admin/realms/" + realm + "/users?username=bulk999&exact=true").ok().body()
                .get(0).get("id").asText();
        String before = d.entry(probe).get("id").asText();
        assertEquals(202, admin.post(provisioning("resync?destination=" + d.id), null).status());
        // every subject gets a new row, delivered without a request that writes
        until(WAIT, () -> !d.entry(probe).get("id").asText().equals(before));
        until(Duration.ofMinutes(2), () -> status(d).get("backlog").asInt() == 0);
        d.awaitDone(probe);
        assertThat(d.mock.writes(), hasSize(writes));
        assertEquals("healthy", status(d).get("state").asText());
    }

    @Test
    public void savingAScopeChangeResyncs() {
        String eng = admin.createGroup(realm, "Engineering");
        String ops = admin.createGroup(realm, "Ops");
        String e1 = user("eng1");
        String o1 = user("ops1");
        admin.joinGroup(realm, e1, eng);
        admin.joinGroup(realm, o1, ops);
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        d.awaitUser("eng1");
        assertNull(d.mock.userByUserName("ops1"));

        // widen: Ops members arrive without any event about them
        admin.updateComponentConfig(realm, d.id, Map.of("scopeGroups", List.of("Engineering", "Ops"))).ok();
        d.awaitUser("ops1");

        // narrow: Engineering members are deprovisioned
        admin.updateComponentConfig(realm, d.id, Map.of("scopeGroups", List.of("Ops"))).ok();
        until(WAIT, () -> !d.mock.userByUserName("eng1").get("active").booleanValue());
        assertTrue(d.mock.userByUserName("ops1").get("active").booleanValue());
    }

    @Test
    public void aDestinationImportedWithItsRealmIsResyncedByTheSweep() {
        String imported = "imp" + realm;
        String key = Keys.unique("atlassian");
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("name", "imported-dest");
        component.put("providerId", "scim-provisioning");
        component.put("subComponents", Map.of());
        component.put("config", Map.of(
                "enabled", List.of("true"),
                "baseUrl", List.of(scimBaseUrl(key)),
                "token", List.of("t"),
                "scopeEveryone", List.of("true")));
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("realm", imported);
        rep.put("enabled", true);
        rep.put("users", List.of(Map.of("username", "already", "email", "already@example.com", "enabled", true)));
        rep.put("components", Map.of("org.keycloak.storage.UserStorageProvider", List.of(component)));
        admin.importRealm(rep);
        try {
            until(WAIT, () -> mockServer.admin(key).userByUserName("already") != null);
        } finally {
            admin.deleteRealm(imported);
        }
    }
}
