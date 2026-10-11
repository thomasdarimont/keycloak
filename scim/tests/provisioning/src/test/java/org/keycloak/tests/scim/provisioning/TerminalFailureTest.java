package org.keycloak.tests.scim.provisioning;

import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The terminal regime and the operator surface: a user the app rejects with 400 on every attempt is dead-lettered
 * after {@code maxAttempts} while every other user provisions; status, dead letters and replay; a rejected token backs
 * the destination off until it is rotated.
 *
 * <p>The app refuses users without a {@code title} (the mock's {@code requireAttribute}), which the destination maps
 * from the {@code jobTitle} attribute. (The extension used the github mock's "externalId is required" with an
 * {@code externalId=attr:extId} line; core always sends {@code externalId}, the Keycloak id when no value overrides
 * it.)
 */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class TerminalFailureTest extends AbstractScimProvisioningTest {

    private static final List<String> MAPPING = List.of("userName=username", "title=attr:jobTitle",
            "displayName=displayName");
    private static final Map<String, ?> REQUIRE_TITLE = Map.of("requireAttribute", "title");

    private String withTitle(String username, String title) {
        String id = user(username);
        if (title != null) {
            admin.updateUser(realm, id, Map.of("attributes", Map.of("jobTitle", List.of(title))));
        }
        return id;
    }

    private Dest refusingDestination(String maxAttempts) {
        return destination("github", Map.of("scopeGroups", List.of("Engineering"), "mapping", MAPPING, "maxAttempts",
                List.of(maxAttempts)), REQUIRE_TITLE);
    }

    private JsonNode status(Dest d) {
        for (JsonNode s : admin.get(provisioning("status")).ok().body()) {
            if (s.get("id").asText().equals(d.id)) {
                return s;
            }
        }
        throw new AssertionError("destination not in status");
    }

    @Test
    public void aRejectedUserIsDeadLetteredAndEveryoneElseProvisions() {
        admin.allowUnmanagedAttributes(realm);
        String g = admin.createGroup(realm, "Engineering");
        Dest d = refusingDestination("3");
        String u1 = withTitle("ulla", "e1");
        String u2 = withTitle("ugo", null);
        String u3 = withTitle("uma", "e3");
        for (String u : List.of(u1, u2, u3)) {
            admin.joinGroup(realm, u, g);
        }

        d.awaitUser("ulla");
        d.awaitUser("uma");
        JsonNode dead = until(WAIT, () -> d.entry("user:" + u2), e -> e != null && e.get("state").asText().equals("dead"));
        assertEquals(3, dead.get("attempts").asInt());
        assertEquals("rejected", dead.get("lastErrorClass").asText());
        assertThat(dead.get("lastError").asText(), containsString("title is required"));
        assertThat(d.mock.ops(o -> method(o, "POST") && o.path("status").asInt() == 400), hasSize(3));

        JsonNode st = status(d);
        assertEquals("healthy", st.get("state").asText(), "a refused change does not back the destination off");
        assertEquals(1, st.get("dead").asInt());
        assertEquals(0, st.get("backlog").asInt());
        assertEquals("rejected", st.get("lastProblemClass").asText());
        assertTrue(absent(st, "retryAt"));

        JsonNode letters = admin.get(provisioning("dead-letters?destination=github-dest")).ok().body();
        assertEquals(1, letters.size());
        assertEquals("user:" + u2, letters.get(0).get("subject").asText());
        assertEquals("ugo", letters.get(0).get("name").asText());
        assertEquals(3, letters.get(0).get("attempts").asInt());

        // WARN with subject and class; the app's text (personal data) never above DEBUG
        List<String> log = ServerLog.sinceClassStart();
        assertThat(log, hasItem(containsString(
                "user:" + u2 + " dead-lettered by github-dest after 3 attempts (rejected: HTTP 400)")));
        assertThat(log.stream().filter(ServerLog::isInfoOrAbove).filter(l -> l.contains("title is required")).toList(),
                empty());

        // fixing the data is a change like any other: it revives the entry with fresh attempts
        admin.updateUser(realm, u2, Map.of("attributes", Map.of("jobTitle", List.of("e2"))));
        d.awaitUser("ugo");
        d.awaitDone("user:" + u2);
        assertEquals(0, status(d).get("dead").asInt());
    }

    @Test
    public void replayReturnsADeadLetterToPendingAndIsAudited() {
        admin.allowUnmanagedAttributes(realm);
        admin.enableAdminEvents(realm);
        String g = admin.createGroup(realm, "Engineering");
        Dest d = refusingDestination("2");
        String u = withTitle("vera", null);
        admin.joinGroup(realm, u, g);
        until(WAIT, () -> d.entry("user:" + u), e -> e != null && e.get("state").asText().equals("dead"));

        d.mock.config(Map.of("requireAttribute", false)); // the app changed its mind
        ProvisioningAdmin.Response r = admin.post(provisioning("replay?destination=" + d.id + "&subject=user:" + u),
                null);
        assertEquals(200, r.status());
        d.awaitUser("vera");
        d.awaitDone("user:" + u);

        // nothing dead any more: a second replay is a 404
        assertEquals(404, admin.post(provisioning("replay?destination=" + d.id + "&subject=user:" + u), null).status());
        // ambiguous without a destination
        assertEquals(400, admin.post(provisioning("replay?subject=user:" + u), null).status());

        JsonNode events = admin.get("/admin/realms/" + realm + "/admin-events?resourceTypes=COMPONENT&operationTypes=ACTION")
                .ok().body();
        List<String> paths = list(events).stream().map(e -> e.get("resourcePath").asText()).toList();
        assertThat(paths, hasItem("components/" + d.id + "/scim-provisioning/replay/user:" + u));
    }

    @Test
    public void aRejectedTokenBacksOffUntilTheComponentIsUpdated() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        d.mock.config(Map.of("token", "rotated")); // the app rotated its token; Keycloak still has "t"
        String u = user("walt");
        admin.joinGroup(realm, u, g);

        JsonNode st = until(WAIT, () -> status(d), s -> s.get("state").asText().equals("failing"));
        assertEquals("auth_rejected: HTTP 401: invalid token", st.get("lastProblem").asText());
        assertEquals(1, st.get("backlog").asInt());
        assertEquals(0, d.entry("user:" + u).get("attempts").asInt(), "never counted against the entry");

        admin.updateComponentConfig(realm, d.id, Map.of("token", List.of("rotated"))).ok();
        d.awaitUser("walt");
        until(WAIT, () -> status(d).get("state").asText().equals("healthy"));
    }

    @Test
    public void readingNeedsViewUsersAndActingNeedsManageUsers() {
        Dest d = destination("atlassian", cfg("scopeEveryone", "true"));
        String viewer = user("viewer");
        admin.setPassword(realm, viewer, "pw");
        admin.grantRealmManagementRole(realm, viewer, "view-users");
        String nobody = user("nobody");
        admin.setPassword(realm, nobody, "pw");

        ProvisioningAdmin asViewer = admin.as(realm, "viewer", "pw");
        assertEquals(200, asViewer.get(provisioning("status")).status());
        assertEquals(403, asViewer.post(provisioning("replay?destination=" + d.id + "&subject=user:x"), null).status());
        assertEquals(403, admin.as(realm, "nobody", "pw").get(provisioning("status")).status());
    }

    @Test
    public void previewShowsWhatWouldBeSent() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String u = admin.createUser(realm, "xena", "xena@example.com", "Xena", "Warrior");
        JsonNode out = admin.get(provisioning("preview?destination=" + d.id + "&user=" + u)).ok().body();
        assertEquals("none", out.get("action").asText());
        admin.joinGroup(realm, u, g);
        d.awaitDone("user:" + u);
        out = admin.get(provisioning("preview?destination=" + d.id + "&user=" + u)).ok().body();
        assertEquals("none", out.get("action").asText(), "already delivered as is");
        assertEquals("Xena", out.get("resource").get("name").get("givenName").asText());
        assertEquals(64, out.get("hash").asText().length());
    }
}
