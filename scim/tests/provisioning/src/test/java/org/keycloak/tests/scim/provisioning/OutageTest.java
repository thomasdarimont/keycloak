package org.keycloak.tests.scim.provisioning;

import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The app is down; three changes to two users; it comes back. Each user receives exactly one delivery carrying their
 * final state, and nothing was counted against the entries meanwhile.
 */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class OutageTest extends AbstractScimProvisioningTest {

    @Test
    public void outageLosesNothingAndSendsEachFinalStateOnce() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String a = admin.createUser(realm, "hana", "hana@example.com", "Hana", "One");
        String b = admin.createUser(realm, "ivo", "ivo@example.com", "Ivo", "Two");
        admin.joinGroup(realm, a, g);
        admin.joinGroup(realm, b, g);
        d.awaitDone("user:" + a);
        d.awaitDone("user:" + b);
        String ra = d.mock.userByUserName("hana").get("id").asText();
        String rb = d.mock.userByUserName("ivo").get("id").asText();
        int before = d.mock.ops().size();

        d.mock.config(Map.of("down", true));
        try {
            admin.updateUser(realm, a, Map.of("firstName", "Hanna"));
            admin.updateUser(realm, a, Map.of("lastName", "Uno"));
            admin.updateUser(realm, b, Map.of("email", "ivo.two@example.com"));
            until(WAIT, () -> d.health().path("failingSince").isNumber());
            pause(2000);
            JsonNode h = d.health();
            assertEquals("unreachable", h.get("lastErrorClass").asText());
            for (String s : List.of("user:" + a, "user:" + b)) {
                JsonNode e = d.entry(s);
                assertEquals("pending", e.get("state").asText());
                assertEquals(0, e.get("attempts").asInt(), "transient failures do not count");
            }
        } finally {
            d.mock.heal();
        }

        d.awaitDone("user:" + a);
        d.awaitDone("user:" + b);
        // superseded rows settled and were pruned: each subject has one row, its latest outcome
        assertEquals(1, d.entryRows("user:" + a));
        assertEquals(1, d.entryRows("user:" + b));
        JsonNode hana = d.mock.userByUserName("hana");
        assertEquals("Hanna", hana.get("name").get("givenName").asText());
        assertEquals("Uno", hana.get("name").get("familyName").asText());
        assertEquals("ivo.two@example.com", d.mock.userByUserName("ivo").get("emails").get(0).get("value").asText());

        List<JsonNode> ops = d.mock.ops();
        List<JsonNode> after = ops.subList(before, ops.size());
        assertEquals(1, after.stream().filter(o -> method(o, "PUT") && o.path("id").asText().equals(ra)).count());
        assertEquals(1, after.stream().filter(o -> method(o, "PUT") && o.path("id").asText().equals(rb)).count());
        assertEquals(0, after.stream().filter(o -> !method(o, "PUT")).count());
        until(WAIT, () -> absent(d.health(), "failingSince"));
    }
}
