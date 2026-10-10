package org.keycloak.tests.scim.provisioning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scoped group lands after its members, with their resource ids; a group whose member is still pending is deferred,
 * boundedly; group deletion deprovisions the members no longer in scope and leaves the remote group alone.
 */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class GroupsTest extends AbstractScimProvisioningTest {

    private static List<String> members(JsonNode group) {
        List<String> out = new ArrayList<>();
        group.path("members").forEach(m -> out.add(m.get("value").asText()));
        return out;
    }

    private Dest groupDestination(String family, Map<String, List<String>> more, Map<String, ?> mockConfig,
            String... scopeGroups) {
        Map<String, List<String>> c = new LinkedHashMap<>(more);
        c.put("scopeGroups", List.of(scopeGroups));
        c.put("supportsGroups", List.of("true"));
        return destination(family, c, mockConfig);
    }

    private Dest groupDestination(String family, String... scopeGroups) {
        return groupDestination(family, Map.of(), Map.of(), scopeGroups);
    }

    @Test
    public void aGroupLandsAfterItsMembersWithTheirResourceIds() {
        // the group and its members exist first; creating the destination resyncs all three at once,
        // and the group (sorted before the users in the batch) must wait for them
        String g = admin.createGroup(realm, "Engineering");
        String a = user("gwen");
        String b = user("hugo");
        admin.joinGroup(realm, a, g);
        admin.joinGroup(realm, b, g);
        // scoped by id, so the rename below keeps the group in scope (a name or path would not)
        Dest d = groupDestination("atlassian", g);

        JsonNode group = until(WAIT, () -> d.mock.groupByDisplayName("Engineering"),
                x -> x != null && members(x).size() == 2);
        String ra = d.mock.userByUserName("gwen").get("id").asText();
        String rb = d.mock.userByUserName("hugo").get("id").asText();
        assertThat(members(group), containsInAnyOrder(ra, rb));
        assertEquals(g, group.get("externalId").asText());
        d.awaitDone("group:" + g);
        assertEquals(group.get("id").asText(), d.link("group:" + g).get("remoteId").asText());

        // never sent wrong: the group's first request came after both members existed
        List<JsonNode> ops = d.mock.ops();
        int firstGroupOp = -1;
        int lastUserPost = -1;
        for (int i = 0; i < ops.size(); i++) {
            JsonNode o = ops.get(i);
            if (o.path("type").asText().equals("Group") && firstGroupOp < 0 && !method(o, "GET")) {
                firstGroupOp = i;
            }
            if (o.path("type").asText().equals("User") && method(o, "POST")) {
                lastUserPost = i;
            }
        }
        assertThat(firstGroupOp, greaterThan(lastUserPost));
        for (JsonNode o : ops) {
            if (o.path("type").asText().equals("Group") && method(o, "POST")) {
                assertEquals(201, o.path("status").asInt());
            }
        }

        // a member leaves: one PATCH with the new member list
        admin.leaveGroup(realm, b, g);
        until(WAIT, () -> members(d.mock.groupByDisplayName("Engineering")).equals(List.of(ra)));
        // a rename: the same remote group gets the new name
        admin.renameGroup(realm, g, "Platform Engineering");
        until(WAIT, () -> d.mock.groupByDisplayName("Platform Engineering") != null);
        assertThat(d.mock.groups(), hasSize(1));
        assertTrue(d.mock.userByUserName("gwen").get("active").booleanValue());
    }

    /**
     * The app refuses a user without a {@code title} (the mock's {@code requireAttribute}); the destination maps
     * {@code title} from the {@code jobTitle} attribute. (The extension used the github mock's "externalId is
     * required" with an {@code externalId=attr:extId} line; core always sends {@code externalId}, the Keycloak id when
     * no value overrides it.)
     */
    @Test
    public void aGroupWaitingForAMemberIsDeferredThenSentWithoutIt() {
        admin.allowUnmanagedAttributes(realm);
        String g = admin.createGroup(realm, "Sales");
        String ok = user("ines");
        admin.updateUser(realm, ok, Map.of("attributes", Map.of("jobTitle", List.of("Seller"))));
        String stuck = user("jack");
        admin.joinGroup(realm, ok, g);
        admin.joinGroup(realm, stuck, g);
        // users without a title are refused, so jack never lands
        Dest d = groupDestination("github",
                Map.of("mapping", List.of("userName=username", "title=attr:jobTitle"), "maxAttempts", List.of("100")),
                Map.of("requireAttribute", "title"), "Sales");

        // deferred while jack is pending without an account …
        JsonNode e = until(WAIT, () -> d.entry("group:" + g), x -> x != null && x.get("deferrals").asInt() > 0);
        assertEquals("pending", e.get("state").asText());
        // … and after the bounded number of deferrals, sent without him
        JsonNode group = until(WAIT, () -> d.mock.groupByDisplayName("Sales"), x -> x != null);
        String rOk = d.mock.userByUserName("ines").get("id").asText();
        assertThat(members(group), contains(rOk));

        // jack's data is fixed: he lands, the group is marked again and picks him up
        admin.updateUser(realm, stuck, Map.of("attributes", Map.of("jobTitle", List.of("Buyer"))));
        String rStuck = d.awaitUser("jack").get("id").asText();
        until(WAIT, () -> members(d.mock.groupByDisplayName("Sales")).size() == 2);
        assertThat(members(d.mock.groupByDisplayName("Sales")), containsInAnyOrder(rOk, rStuck));
    }

    @Test
    public void anExistingRemoteGroupIsFoundByDisplayName() {
        String g = admin.createGroup(realm, "Design");
        Dest d = groupDestination("atlassian", "Design");
        String existing = d.mock.createGroupInApp("Design").get("id").asText();
        String u = user("kira");
        admin.joinGroup(realm, u, g);
        until(WAIT, () -> members(d.mock.groupByDisplayName("Design")).size() == 1);
        assertThat(d.mock.groups(), hasSize(1));
        assertEquals(existing, d.link("group:" + g).get("remoteId").asText());
    }

    @Test
    public void deletingAGroupDeprovisionsMembersNoLongerInScopeAndLeavesTheRemoteGroup() {
        String a = admin.createGroup(realm, "Alpha");
        String b = admin.createGroup(realm, "Beta");
        Dest d = groupDestination("atlassian", "Alpha", "Beta");
        String onlyAlpha = user("lars");
        String both = user("mona");
        admin.joinGroup(realm, onlyAlpha, a);
        admin.joinGroup(realm, both, a);
        admin.joinGroup(realm, both, b);
        until(WAIT, () -> d.mock.groupByDisplayName("Alpha") != null
                && members(d.mock.groupByDisplayName("Alpha")).size() == 2);
        d.awaitDone("group:" + a);

        admin.deleteGroup(realm, a);
        until(WAIT, () -> !d.mock.userByUserName("lars").get("active").booleanValue());
        d.awaitDone("user:" + both);
        assertTrue(d.mock.userByUserName("mona").get("active").booleanValue(), "still in scope through Beta");
        assertNotNull(d.mock.groupByDisplayName("Alpha"), "never deleted remotely");
        assertThat(d.mock.ops(o -> o.path("type").asText().equals("Group") && method(o, "DELETE")), empty());
    }

    @Test
    public void aDestinationWithoutGroupsNeverCallsGroupEndpoints() {
        String g = admin.createGroup(realm, "Support");
        // zoom family: no /Groups at all
        Dest d = destinationForGroup("zoom", "Support", Map.of());
        String u = user("nils");
        admin.joinGroup(realm, u, g);
        d.awaitUser("nils");
        d.awaitDone("user:" + u);
        pause(1500);
        assertThat(d.mock.ops(o -> o.path("path").asText().contains("/Groups")), empty());
        assertNull(d.entry("group:" + g), "no group rows at all");
    }

    @Test
    public void aDisabledMemberLeavesTheRemoteGroup() {
        String g = admin.createGroup(realm, "Ops");
        Dest d = groupDestination("atlassian", "Ops");
        String a = user("olga");
        String b = user("piet");
        admin.joinGroup(realm, a, g);
        admin.joinGroup(realm, b, g);
        until(WAIT, () -> d.mock.groupByDisplayName("Ops") != null
                && members(d.mock.groupByDisplayName("Ops")).size() == 2);
        admin.updateUser(realm, b, Map.of("enabled", false));
        until(WAIT, () -> members(d.mock.groupByDisplayName("Ops")).size() == 1);
        assertFalse(d.mock.userByUserName("piet").get("active").booleanValue());
    }
}
