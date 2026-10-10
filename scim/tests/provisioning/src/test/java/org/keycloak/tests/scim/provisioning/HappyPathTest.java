package org.keycloak.tests.scim.provisioning;

import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A user added to a scoped group appears in the destination, rendered attribute by attribute. */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class HappyPathTest extends AbstractScimProvisioningTest {

    @Test
    public void userAddedToScopedGroupIsProvisionedWithTheDefaultMapping() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String id = admin.createUser(realm, "alice", "alice@example.com", "Alice", "Liddell");
        pause(1500);
        assertThat("not in scope before joining", d.mock.users(), empty());

        admin.joinGroup(realm, id, g);
        JsonNode u = d.awaitUser("alice");

        assertThat(texts(u.get("schemas")), contains("urn:ietf:params:scim:schemas:core:2.0:User"));
        assertEquals("alice", u.get("userName").asText());
        assertEquals(id, u.get("externalId").asText());
        assertTrue(u.get("active").booleanValue());
        assertEquals("Alice Liddell", u.get("displayName").asText());
        assertEquals("Alice", u.get("name").get("givenName").asText());
        assertEquals("Liddell", u.get("name").get("familyName").asText());
        assertEquals(1, u.get("emails").size());
        assertEquals("work", u.get("emails").get(0).get("type").asText());
        assertEquals("alice@example.com", u.get("emails").get(0).get("value").asText());
        assertTrue(u.get("emails").get(0).get("primary").booleanValue());

        d.awaitDone("user:" + id);
        JsonNode link = d.link("user:" + id);
        assertEquals(u.get("id").asText(), link.get("remoteId").asText());
        assertTrue(link.get("active").booleanValue());
        assertThat(d.mock.ops(o -> method(o, "POST")), hasSize(1));
    }

    /**
     * Core renders the user from the realm's SCIM schemas and applies the destination's mapping lines on top, so
     * attributes no line addresses ({@code name.familyName}, {@code emails}) are still sent (the extension's mapping
     * replaced the whole resource and left them out).
     */
    @Test
    public void customMappingWithExtensionsAndAUserAttribute() {
        // declare nothing, allow unmanaged attributes: otherwise Keycloak drops attr:department
        admin.allowUnmanagedAttributes(realm);

        String g = admin.createGroup(realm, "Sales");
        Dest d = destinationForGroup("slack", "Sales", Map.of("mapping", List.of(
                "userName=email",
                "externalId=id",
                "name.givenName=firstName",
                "title=const:Seller",
                "urn:scim:schemas:extension:enterprise:1.0.department=attr:department",
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber=attr:emp")));
        String id = admin.createUser(realm, "bob", "bob@example.com", "Bob", "Builder");
        admin.updateUser(realm, id, Map.of("attributes", Map.of("department", List.of("Tour"), "emp", List.of("42"))));
        admin.joinGroup(realm, id, g);

        JsonNode u = d.awaitUser("bob@example.com");
        assertEquals(id, u.get("externalId").asText());
        assertEquals("Bob", u.get("name").get("givenName").asText());
        assertEquals("Builder", u.get("name").get("familyName").asText(), "not addressed by a line: as rendered");
        assertEquals("Seller", u.get("title").asText());
        assertEquals("bob@example.com", u.get("emails").get(0).get("value").asText(), "not addressed by a line: as rendered");
        assertEquals("Tour", u.get("urn:scim:schemas:extension:enterprise:1.0").get("department").asText());
        assertEquals("42",
                u.get("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User").get("employeeNumber").asText());
        // in any order: core's SCIM User keeps schemas in a set (the extension sent them in rule order)
        assertThat(texts(u.get("schemas")), containsInAnyOrder(
                "urn:ietf:params:scim:schemas:core:2.0:User",
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User",
                "urn:scim:schemas:extension:enterprise:1.0"));
    }

    @Test
    public void anUpdateIsOnePutWithTheNewState() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String id = user("carl");
        admin.joinGroup(realm, id, g);
        d.awaitDone("user:" + id);
        String remote = d.awaitUser("carl").get("id").asText();

        admin.updateUser(realm, id, Map.of("firstName", "Carlos"));
        until(WAIT, () -> d.mock.userByUserName("carl").get("name").get("givenName").asText().equals("Carlos"));
        assertThat(d.mock.ops(o -> method(o, "PUT") && o.path("id").asText().equals(remote)), hasSize(1));
    }

    @Test
    public void usersOutsideScopeAreNotProvisionedAndSubGroupMembersAre() {
        String parent = admin.createGroup(realm, "Engineering");
        String child = admin.createSubGroup(realm, parent, "Platform");
        String other = admin.createGroup(realm, "Marketing");
        Dest d = destinationForGroup("atlassian", "/Engineering", Map.of());
        String inSub = user("dora");
        String outside = user("edgar");
        admin.joinGroup(realm, inSub, child);
        admin.joinGroup(realm, outside, other);
        d.awaitUser("dora");
        d.awaitDone("user:" + outside);
        assertNull(d.mock.userByUserName("edgar"));
        assertNull(d.link("user:" + outside));
    }

    @Test
    public void scopeEveryoneSendsDisabledUsersInactive() {
        Dest d = destination("atlassian", cfg("scopeEveryone", "true"));
        String id = user("fay");
        d.awaitUser("fay");
        admin.updateUser(realm, id, Map.of("enabled", false));
        until(WAIT, () -> !d.mock.userByUserName("fay").get("active").booleanValue());
        assertFalse(d.link("user:" + id).get("deprovisioned").booleanValue());
    }

    @Test
    public void aDisabledDestinationKeepsMarkingAndDrainsWhenEnabled() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", cfg("enabled", "false"));
        int afterSaveCheck = d.mock.ops().size();
        String id = user("gil");
        admin.joinGroup(realm, id, g);
        until(WAIT, () -> d.entry("user:" + id) != null);
        pause(2000);
        assertEquals("pending", d.entry("user:" + id).get("state").asText());
        assertThat("nothing drained while disabled", d.mock.ops(), hasSize(afterSaveCheck));

        admin.updateComponentConfig(realm, d.id, cfg("enabled", "true")).ok();
        d.awaitUser("gil");
        d.awaitDone("user:" + id);
    }
}
