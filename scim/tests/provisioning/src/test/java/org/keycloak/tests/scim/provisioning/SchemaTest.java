package org.keycloak.tests.scim.provisioning;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keycloak creates the provisioning tables on first start and registers their entities. */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class SchemaTest extends AbstractScimProvisioningTest {

    @Test
    public void tablesHistoryTableAndEntitiesExistOnFirstStart() {
        JsonNode s = admin.anonymous("GET", testResource("schema"), null).ok().body();
        // JPQL over each entity works: the entity is in the persistence unit and its table exists
        assertTrue(s.get("OutboxEntry").isNumber());
        assertTrue(s.get("ScimProvisioningLink").isNumber());
        assertTrue(s.get("ScimProvisioningHealth").isNumber());
        // the tables come from one changeSet of Keycloak's own changelog (the extension had its own history table
        // with three changesets)
        assertEquals(1, s.get("changeSet").asLong());
    }
}
