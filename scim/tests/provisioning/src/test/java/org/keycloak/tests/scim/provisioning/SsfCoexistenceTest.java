package org.keycloak.tests.scim.provisioning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.keycloak.common.Profile;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Provisioning entries share Keycloak's {@code OUTBOX_ENTRY} with the Shared Signals Framework transmitter. With SSF
 * enabled and its drainer running every two seconds, neither consumer touches the other's rows: SSF's drainer leaves our
 * pending entries alone, and our drainer, counts, sweeper and destination removal leave SSF's rows alone, even one whose
 * owner id is our destination's.
 */
@KeycloakIntegrationTest(config = SsfCoexistenceTest.SsfConfig.class)
public class SsfCoexistenceTest extends AbstractScimProvisioningTest {

    private static final String SSF_PUSH = "ssf-push";

    public static class SsfConfig extends ScimProvisioningServerConfig {
        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            return super.configure(config)
                    .features(Profile.Feature.SSF)
                    .spiOption("ssf-transmitter", "default", "outbox-drainer-interval", "2s");
        }
    }

    private List<JsonNode> ssfRows() {
        return list(admin.anonymous("GET", testResource("foreign-rows?kind=" + SSF_PUSH), null).ok().body());
    }

    private void ssfRow(String owner) {
        admin.anonymous("POST", testResource("foreign-row?kind=" + SSF_PUSH + "&owner=" + owner), null).ok();
    }

    private JsonNode status(Dest d) {
        for (JsonNode s : list(admin.get(provisioning("status")).ok().body())) {
            if (s.get("id").asText().equals(d.id)) {
                return s;
            }
        }
        throw new AssertionError("no status for " + d.id);
    }

    @Test
    public void ssfAndScimProvisioningNeverTouchEachOthersRows() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String u = user("sana");
        admin.joinGroup(realm, u, g);
        d.awaitDone("user:" + u);

        // SSF rows: one owned by an id that is our destination's, one by a client that does not exist
        ssfRow(d.id);
        ssfRow("no-such-receiver");

        // an outage: our entry stays pending while SSF's drainer ticks several times
        d.mock.config(Map.of("down", true));
        try {
            admin.updateUser(realm, u, Map.of("lastName", "Changed"));
            until(WAIT, () -> d.health().path("failingSince").isNumber());
            // SSF's drainer ran: it found no receiver for its rows and dead-lettered them as orphans
            until(WAIT, () -> ssfRows().stream().allMatch(r -> r.get("status").asText().equals("DEAD_LETTER")));
            JsonNode e = d.entry("user:" + u);
            assertEquals("pending", e.get("state").asText(), "SSF's drainer left our entry alone");
            assertEquals(0, e.get("attempts").asInt());
            // our counts see only our rows, though SSF has a dead row owned by our destination's id
            JsonNode st = status(d);
            assertEquals(0, st.get("dead").asInt());
            assertEquals(1, st.get("backlog").asInt());
        } finally {
            d.mock.heal();
        }
        d.awaitDone("user:" + u);
        assertEquals("Changed", d.mock.userByUserName("sana").get("name").get("familyName").asText());

        // our sweeper (every 2 s here) removes rows of unknown destinations, but only of our kind
        pause(5000);
        assertThat(ssfRows(), hasSize(2));

        // removing our destination removes our rows, not SSF's row with the same owner id
        admin.deleteComponent(realm, d.id);
        JsonNode n = admin.anonymous("GET", testResource("count?destination=" + d.id), null).ok().body();
        assertEquals(0, n.get("OutboxEntry").asLong());
        List<String> owners = new ArrayList<>();
        for (JsonNode r : ssfRows()) {
            owners.add(r.get("owner").asText());
        }
        assertThat(owners, containsInAnyOrder(d.id, "no-such-receiver"));
        // no outbox errors once Keycloak is up. (On a fresh database SSF's drainer can tick before Keycloak has
        // migrated its schema and log "OUTBOX_ENTRY does not exist" until it has: an upstream startup race.)
        List<String> log = ServerLog.sinceClassStart();
        int started = -1;
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i).contains(" started in ")) {
                started = i;
            }
        }
        List<String> errors = log.subList(Math.max(0, started), log.size()).stream()
                .filter(l -> l.contains("ERROR"))
                .filter(l -> l.contains("scim") || l.contains("Outbox"))
                .toList();
        assertThat(errors, empty());
    }
}
