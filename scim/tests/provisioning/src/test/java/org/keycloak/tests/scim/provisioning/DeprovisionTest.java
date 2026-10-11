package org.keycloak.tests.scim.provisioning;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Leaving scope deactivates (not deletes); repeating is a no-op; rejoining brings the account back. Also delete mode,
 * the PATCH→PUT fallback, user deletion, forced deprovision across three destinations with the deliveries poll, and
 * cleanup when a destination or realm goes away.
 */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class DeprovisionTest extends AbstractScimProvisioningTest {

    private long writes(Dest d) {
        return d.mock.writes().size();
    }

    @Test
    public void leavingTheGroupDeactivatesRepeatingIsANoOpAndRejoiningRestores() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String id = user("yara");
        admin.joinGroup(realm, id, g);
        d.awaitDone("user:" + id);
        String remote = d.awaitUser("yara").get("id").asText();

        admin.leaveGroup(realm, id, g);
        until(WAIT, () -> !d.mock.userByUserName("yara").get("active").booleanValue());
        d.awaitDone("user:" + id);
        assertThat("deactivated, not deleted", d.mock.users(), hasSize(1));
        assertThat(d.mock.ops(o -> method(o, "PATCH")), hasSize(1));
        JsonNode tomb = d.link("user:" + id);
        assertTrue(tomb.get("deprovisioned").booleanValue());
        assertFalse(tomb.get("active").booleanValue());

        // repeat: another change to the out-of-scope user sends nothing
        long before = writes(d);
        admin.updateUser(realm, id, Map.of("firstName", "Yarah"));
        pause(2000);
        d.awaitDone("user:" + id);
        assertEquals(before, writes(d));

        // rejoin: the account comes back (found by userName, not written blindly by the stale id)
        admin.joinGroup(realm, id, g);
        until(WAIT, () -> d.mock.userByUserName("yara").get("active").booleanValue());
        d.awaitDone("user:" + id);
        assertThat(d.mock.users(), hasSize(1));
        assertFalse(d.link("user:" + id).get("deprovisioned").booleanValue());
        assertEquals(remote, d.link("user:" + id).get("remoteId").asText());
        assertEquals("Yarah", d.mock.userByUserName("yara").get("name").get("givenName").asText());
    }

    @Test
    public void deleteModeDeletesAndARejoinRecreates() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", cfg("deprovision", "delete"));
        String id = user("zane");
        admin.joinGroup(realm, id, g);
        String first = d.awaitUser("zane").get("id").asText();
        d.awaitDone("user:" + id);

        admin.leaveGroup(realm, id, g);
        until(WAIT, () -> d.mock.users().isEmpty());
        d.awaitDone("user:" + id);

        admin.joinGroup(realm, id, g);
        String second = d.awaitUser("zane").get("id").asText();
        assertNotEquals(first, second);
        d.awaitDone("user:" + id);
        assertEquals(second, d.link("user:" + id).get("remoteId").asText());
    }

    @Test
    public void anAppWithoutPatchIsDeactivatedWithAPut() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String id = user("abel");
        admin.joinGroup(realm, id, g);
        d.awaitDone("user:" + id);
        d.mock.config(Map.of("failNext", 1, "failStatus", 501));
        admin.leaveGroup(realm, id, g);
        until(WAIT, () -> !d.mock.userByUserName("abel").get("active").booleanValue());
        List<JsonNode> ops = d.mock.ops();
        List<String> tail = ops.subList(ops.size() - 3, ops.size()).stream()
                .map(o -> o.get("method").asText() + " " + o.get("status").asInt())
                .toList();
        assertThat(tail, contains("PATCH 501", "GET 200", "PUT 200"));
    }

    @Test
    public void deletingTheUserInKeycloakDeactivatesTheAccount() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String id = user("bea");
        admin.joinGroup(realm, id, g);
        d.awaitDone("user:" + id);
        admin.deleteUser(realm, id);
        until(WAIT, () -> !d.mock.userByUserName("bea").get("active").booleanValue());
        assertTrue(d.link("user:" + id).get("deprovisioned").booleanValue());
    }

    @Test
    public void forcedDeprovisionTakesAccessAwayEverywhereOnce() {
        String g = admin.createGroup(realm, "Everyone");
        Dest a = destinationForGroup("atlassian", "Everyone", Map.of());
        Dest b = destinationForGroup("slack", "Everyone", cfg("deprovision", "delete"));
        Dest c = destinationForGroup("notion", "Everyone", Map.of());
        String id = user("cleo");
        admin.joinGroup(realm, id, g);
        for (Dest d : List.of(a, b, c)) {
            d.awaitDone("user:" + id);
        }

        JsonNode started = admin.post(provisioning("deprovision?user=" + id), null).ok().body();
        assertEquals(3, started.size());

        JsonNode deliveries = until(WAIT, () -> admin.get(provisioning("deliveries?user=" + id)).ok().body(), l -> {
            for (JsonNode x : l) {
                if (!x.get("done").booleanValue() || !x.path("account").path("deprovisioned").asBoolean()) {
                    return false;
                }
            }
            return true;
        });
        assertEquals(3, deliveries.size());
        for (Dest d : List.of(a, b, c)) {
            assertFalse(d.mock.userByUserName("cleo").get("active").booleanValue());
        }
        // slack family: DELETE deactivates
        assertThat(b.mock.ops(o -> method(o, "DELETE")), hasSize(1));

        // one-shot: she is still in the group, so her next change provisions her again
        admin.updateUser(realm, id, Map.of("lastName", "Again"));
        for (Dest d : List.of(a, b, c)) {
            until(WAIT, () -> d.mock.userByUserName("cleo").get("active").booleanValue());
        }
    }

    /**
     * A forced deprovision is sticky until it is delivered: a later, ordinary change to the same person, made while the
     * app is down, carries it over rather than undoing it.
     */
    @Test
    public void forcedDeprovisionSurvivesALaterChangeDuringAnOutage() {
        String g = admin.createGroup(realm, "Everyone");
        Dest d = destinationForGroup("atlassian", "Everyone", Map.of());
        String id = user("ines");
        admin.joinGroup(realm, id, g);
        d.awaitDone("user:" + id);

        d.mock.config(Map.of("down", true));
        try {
            admin.post(provisioning("deprovision?user=" + id), null).ok();
            admin.updateUser(realm, id, Map.of("lastName", "Later"));
            until(WAIT, () -> d.health().path("failingSince").isNumber());
        } finally {
            d.mock.heal();
        }

        until(WAIT, () -> !d.mock.userByUserName("ines").get("active").booleanValue());
        d.awaitDone("user:" + id);
        assertTrue(d.link("user:" + id).get("deprovisioned").booleanValue());
        assertEquals(1, d.entryRows("user:" + id));
        pause(2000);
        assertFalse(d.mock.userByUserName("ines").get("active").booleanValue(),
                "the later change did not provision her again");
    }

    @Test
    public void forcedDeprovisionFindsAnAccountThatPredatesUs() {
        // no scope at all: she was never provisioned by us, the account predates the destination
        Dest d = destination("atlassian", Map.of());
        String id = user("dina");
        String seeded = d.mock.seedUser(Map.of("userName", "dina", "active", true)).get("id").asText();
        admin.post(provisioning("deprovision?user=" + id), null).ok();
        until(WAIT, () -> !d.mock.userByUserName("dina").get("active").booleanValue());
        assertEquals(seeded, d.link("user:" + id).get("remoteId").asText());
    }

    @Test
    public void removingADestinationRemovesItsRows() {
        Dest d = destination("atlassian", cfg("scopeEveryone", "true"));
        String id = user("eli");
        d.awaitDone("user:" + id);
        admin.deleteComponent(realm, d.id);
        JsonNode n = admin.anonymous("GET", testResource("count?destination=" + d.id), null).ok().body();
        assertEquals(0, n.get("OutboxEntry").asLong());
        assertEquals(0, n.get("ScimProvisioningLink").asLong());
        assertEquals(0, n.get("ScimProvisioningHealth").asLong());
    }

    /**
     * The realm that goes away is a second one, made for this test (the framework owns and removes the test's realm);
     * the counts are read through the test's realm.
     */
    @Test
    public void removingARealmRemovesItsRowsAndOrphansAreSwept() {
        String doomed = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        admin.createRealm(doomed);
        String realmId;
        try {
            Dest d = destinationIn(doomed, "atlassian", cfg("scopeEveryone", "true"), Map.of());
            String id = admin.createUser(doomed, "fin", "fin@example.com", "Fin", "Tester");
            d.awaitDone("user:" + id);
            realmId = admin.realmId(doomed);
        } finally {
            admin.deleteRealm(doomed);
        }

        JsonNode n = admin.anonymous("GET", testResource("count?realmId=" + realmId), null).ok().body();
        assertEquals(0, n.get("OutboxEntry").asLong());
        assertEquals(0, n.get("ScimProvisioningLink").asLong());
        assertEquals(0, n.get("ScimProvisioningHealth").asLong());

        // a row for a destination that no longer exists is removed by the sweeper
        admin.anonymous("POST", testResource("orphan?destination=no-such-destination"), null).ok();
        until(WAIT, () -> admin.anonymous("GET", testResource("count?destination=no-such-destination"), null).ok()
                .body().get("OutboxEntry").asLong() == 0);
    }
}
