package org.keycloak.tests.scim.provisioning;

import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** An account that already exists is linked, not duplicated. */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class AdoptionTest extends AbstractScimProvisioningTest {

    @Test
    public void existingAccountIsAdopted() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String seeded = d.mock.seedUser(Map.of("userName", "kim", "active", true)).get("id").asText();
        String other = user("lee");
        String id = user("kim");
        admin.joinGroup(realm, other, g);
        admin.joinGroup(realm, id, g);

        d.awaitDone("user:" + id);
        d.awaitDone("user:" + other);
        JsonNode link = d.link("user:" + id);
        assertEquals(seeded, link.get("remoteId").asText());
        // exactly one POST, and it is not for kim
        List<JsonNode> posts = d.mock.ops(o -> method(o, "POST"));
        assertThat(posts, hasSize(1));
        assertEquals("lee", posts.get(0).get("userName").asText());
        assertThat(d.mock.users(), hasSize(2));
        assertEquals(id, d.mock.userByUserName("kim").get("externalId").asText());
    }
}
