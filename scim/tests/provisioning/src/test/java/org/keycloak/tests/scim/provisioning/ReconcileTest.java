package org.keycloak.tests.scim.provisioning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An account deactivated inside the app is reported {@code mismatched} and the following delivery corrects it (the
 * hash is cleared first); an account with a matching userName that we did not know about is reported {@code linked} and
 * not duplicated. Also {@code missing}, {@code unmanaged}, and an out-of-scope account someone reactivated inside the
 * app.
 */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class ReconcileTest extends AbstractScimProvisioningTest {

    private JsonNode lastReconcile(Dest d) {
        return admin.get(provisioning("reconcile?destination=" + d.id)).ok().body();
    }

    private static List<String> subjects(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.get("subject").asText()));
        return out;
    }

    private void forgetLink(Dest d, String subject) {
        admin.anonymous("POST", testResource("forget-link?destination=" + d.id + "&subject=" + subject), null).ok();
    }

    @Test
    public void driftIsReportedAndCorrected() {
        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String a = user("anna"); // will be deactivated inside the app
        String b = user("boris"); // link lost (a restore): found again by externalId
        String c = user("chen"); // link lost, account re-made by hand: found by userName
        String dd = user("dmitri"); // account deleted inside the app
        String e = user("elena"); // leaves scope, then reactivated inside the app
        for (String u : List.of(a, b, c, dd, e)) {
            admin.joinGroup(realm, u, g);
        }
        for (String u : List.of(a, b, c, dd, e)) {
            d.awaitDone("user:" + u);
        }
        admin.leaveGroup(realm, e, g);
        until(WAIT, () -> !d.mock.userByUserName("elena").get("active").booleanValue());
        d.awaitDone("user:" + e);

        d.mock.patchUser(d.mock.userByUserName("anna").get("id").asText(), Map.of("active", false));
        forgetLink(d, "user:" + b);
        forgetLink(d, "user:" + c);
        d.mock.deleteUserInApp(d.mock.userByUserName("chen").get("id").asText());
        String chenByHand = d.mock.seedUser(Map.of("userName", "chen", "active", true)).get("id").asText();
        d.mock.deleteUserInApp(d.mock.userByUserName("dmitri").get("id").asText());
        d.mock.patchUser(d.mock.userByUserName("elena").get("id").asText(), Map.of("active", true));
        d.mock.seedUser(Map.of("userName", "stranger", "active", true));
        int postsBefore = d.mock.ops(o -> method(o, "POST")).size();

        assertEquals(202, admin.post(provisioning("reconcile?destination=" + d.id), null).status());
        JsonNode last = until(WAIT, () -> lastReconcile(d), r -> r.get("status").asText().equals("ok"));
        JsonNode report = last.get("report");
        assertEquals(4, report.get("inScope").asInt());
        assertEquals(2, report.get("mismatched").asInt());
        assertEquals(2, report.get("linked").asInt());
        assertEquals(1, report.get("missing").asInt());
        assertEquals(1, report.get("unmanaged").asInt());
        assertThat(subjects(report.get("details").get("mismatched")), containsInAnyOrder("user:" + a, "user:" + e));
        assertThat(subjects(report.get("details").get("linked")), containsInAnyOrder("user:" + b, "user:" + c));
        assertThat(subjects(report.get("details").get("missing")), contains("user:" + dd));
        assertEquals(2, last.get("mismatches").asInt());

        // the following deliveries correct it
        until(WAIT, () -> d.mock.userByUserName("anna").get("active").booleanValue());
        until(WAIT, () -> !d.mock.userByUserName("elena").get("active").booleanValue());
        until(WAIT, () -> d.mock.userByUserName("dmitri") != null);
        for (String u : List.of(a, b, c, dd, e)) {
            d.awaitDone("user:" + u);
        }
        assertEquals(chenByHand, d.link("user:" + c).get("remoteId").asText());
        // linked accounts are updated, never duplicated: the only new account is dmitri's
        assertThat(d.mock.ops(o -> method(o, "POST")), hasSize(postsBefore + 1));
        assertThat(d.mock.users(), hasSize(6));

        JsonNode status = admin.get(provisioning("status")).ok().body().get(0);
        assertEquals(2, status.get("mismatches").asInt());
        assertEquals("ok", status.get("lastReconcileStatus").asText());
    }

    @Test
    public void aFailedReconcileIsRecordedNotThrown() {
        Dest d = destination("atlassian", cfg("scopeEveryone", "true"));
        d.mock.config(Map.of("status", 500));
        try {
            admin.post(provisioning("reconcile?destination=" + d.id), null).ok();
            JsonNode last = until(WAIT, () -> lastReconcile(d), r -> r.get("status").asText().equals("failed"));
            assertEquals("server_error", last.get("report").get("errorClass").asText());
        } finally {
            d.mock.heal();
        }
    }
}
