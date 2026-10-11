package org.keycloak.tests.scim.provisioning;

import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A 429 with Retry-After: 120 holds the destination off at least that long; nothing dies. */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class RateLimitTest extends AbstractScimProvisioningTest {

    @Test
    public void retryAfterIsHonouredAndNothingIsDeadLettered() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        int afterSaveCheck = d.mock.ops().size();
        d.mock.config(Map.of("status", 429, "retryAfter", 120));
        long t0 = System.currentTimeMillis();
        String id = user("jun");
        admin.joinGroup(realm, id, g);

        JsonNode h = until(WAIT, d::health, x -> x != null && x.path("retryAt").isNumber());
        assertThat(h.get("retryAt").asLong(), greaterThanOrEqualTo(t0 + 119_000));
        assertEquals("rate_limited", h.get("lastErrorClass").asText());
        int calls = d.mock.ops().size();
        assertEquals(1, calls - afterSaveCheck, "one call, answered 429");

        pause(5000); // ten drain ticks
        assertThat("no call inside the Retry-After window", d.mock.ops(), hasSize(calls));
        JsonNode e = d.entry("user:" + id);
        assertEquals("pending", e.get("state").asText());
        assertEquals(0, e.get("attempts").asInt());
    }
}
