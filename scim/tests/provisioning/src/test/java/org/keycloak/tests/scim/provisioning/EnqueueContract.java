package org.keycloak.tests.scim.provisioning;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Transactional enqueue, run in both enqueue modes: a rolled-back user change leaves no outbox row; a committed one
 * does and is delivered. The rollback comes from a test-only endpoint that makes a real model change (create a user, add
 * them to a scoped group — the group-join model event makes the listener write its marker) and then fails the request.
 */
public abstract class EnqueueContract extends AbstractScimProvisioningTest {

    private ProvisioningAdmin.Response call(String op, String username, String groupId) {
        return admin.anonymous("POST", testResource(op + "?username=" + username + "&group=" + groupId), null);
    }

    private JsonNode entriesFor(String userId) {
        return admin.anonymous("GET", testResource("entries?subject=user:" + userId), null).ok().body();
    }

    @Test
    public void rolledBackUserCreationLeavesNoOutboxRow() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());

        ProvisioningAdmin.Response r = call("create-and-fail", "rolledback", g);
        assertEquals(409, r.status());
        String userId = r.body().get("userId").asText();

        // the user change was rolled back …
        assertEquals(404, admin.get("/admin/realms/" + realm + "/users/" + userId).status());
        // … and so was (transactional) or never happened (after-commit) the marker
        assertThat(entriesFor(userId), emptyIterable());
        pause(3000); // give an after-commit write or a drain every chance to happen
        assertThat(entriesFor(userId), emptyIterable());
        assertNull(d.mock.userByUserName("rolledback"));
    }

    @Test
    public void committedUserCreationLeavesARowAndIsDelivered() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());

        ProvisioningAdmin.Response r = call("create-and-commit", "committed", g);
        assertEquals(200, r.status());
        String userId = r.body().get("userId").asText();

        until(WAIT, () -> entriesFor(userId).size() == 1);
        d.awaitUser("committed");
        d.awaitDone("user:" + userId);
    }

    @Test
    public void adminRestChangeIsEnqueuedAndDelivered() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String id = user("viaadmin");
        admin.joinGroup(realm, id, g);
        d.awaitUser("viaadmin");
        d.awaitDone("user:" + id);
    }
}
