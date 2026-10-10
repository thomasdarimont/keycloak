package org.keycloak.tests.scim.provisioning;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The workflow steps: {@code scim-provisioning-deprovision} (optionally per destination),
 * {@code scim-provisioning-delete}, {@code scim-provisioning-resync-user}, {@code scim-provisioning-await-deprovision}.
 */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class WorkflowStepTest extends AbstractScimProvisioningTest {

    private static Map<String, Object> step(String uses, String... with) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("uses", uses);
        if (with.length > 0) {
            Map<String, Object> w = new LinkedHashMap<>();
            for (int i = 0; i < with.length; i += 2) {
                w.put(with[i], List.of(with[i + 1]));
            }
            s.put("with", w);
        }
        return s;
    }

    private static Map<String, Object> after(Map<String, Object> step, String delay) {
        step.put("after", delay);
        return step;
    }

    private ProvisioningAdmin.Response createWorkflow(String name, List<Map<String, Object>> steps) {
        return admin.post("/admin/realms/" + realm + "/workflows", Map.of("name", name + "-" + realm, "steps", steps));
    }

    /** Creates a workflow and starts it for one user. */
    private void run(String name, String userId, List<Map<String, Object>> steps) {
        ProvisioningAdmin.Response created = createWorkflow(name, steps);
        assertEquals(201, created.status(), created.text());
        ProvisioningAdmin.Response activated = admin.post(
                "/admin/realms/" + realm + "/workflows/" + created.createdId() + "/activate/USERS/" + userId, null);
        assertThat(activated.text(), activated.status(), lessThan(300));
    }

    private boolean active(Dest d, String userName) {
        return d.mock.userByUserName(userName).get("active").booleanValue();
    }

    @Test
    public void theStepsAreRegisteredWhenWorkflowsAreOn() {
        String info = admin.get("/admin/serverinfo").ok().text();
        assertThat(info, containsString("scim-provisioning-deprovision"));
        assertThat(info, containsString("scim-provisioning-delete"));
        assertThat(info, containsString("scim-provisioning-resync-user"));
        assertThat(info, containsString("scim-provisioning-await-deprovision"));
    }

    @Test
    public void deprovisionEverywhere() {
        Dest d = destination("atlassian", cfg("scopeEveryone", "true"));
        String u = user("quinn");
        d.awaitDone("user:" + u);
        run("offboard", u, List.of(step("scim-provisioning-deprovision")));
        until(WAIT, () -> !active(d, "quinn"));
        assertTrue(d.link("user:" + u).get("deprovisioned").booleanValue());
    }

    @Test
    public void deprovisionOneDestinationOnly() {
        Dest keep = destination("atlassian", cfg("scopeEveryone", "true"));
        Dest drop = destination("notion", cfg("scopeEveryone", "true"));
        String u = user("ravi");
        keep.awaitDone("user:" + u);
        drop.awaitDone("user:" + u);
        run("drop-notion", u, List.of(step("scim-provisioning-deprovision", "destination", "notion-dest")));
        until(WAIT, () -> !active(drop, "ravi"));
        keep.awaitDone("user:" + u);
        pause(1500);
        assertTrue(active(keep, "ravi"), "the other destination is untouched");
        assertThat(keep.mock.writes(), hasSize(1)); // the create only
    }

    @Test
    public void aStepNamingAnUnknownDestinationIsRefused() {
        destination("atlassian", cfg("scopeEveryone", "true"));
        ProvisioningAdmin.Response r = createWorkflow("bad",
                List.of(step("scim-provisioning-deprovision", "destination", "no-such-destination")));
        assertEquals(400, r.status(), r.text());
        assertThat(r.text(), containsString("no-such-destination"));
    }

    @Test
    public void deactivateNowDeleteLater() {
        Dest d = destination("atlassian", cfg("scopeEveryone", "true"));
        String u = user("sara");
        d.awaitDone("user:" + u);
        run("deactivate", u, List.of(step("scim-provisioning-deprovision")));
        until(WAIT, () -> !active(d, "sara"));
        d.awaitDone("user:" + u);
        assertThat("deactivated, still there", d.mock.users(), hasSize(1));

        run("delete", u, List.of(step("scim-provisioning-delete")));
        until(WAIT, () -> d.mock.users().isEmpty());
        d.awaitDone("user:" + u);
        assertThat(d.mock.ops(o -> method(o, "DELETE")), hasSize(1));
        JsonNode deliveries = admin.get(provisioning("deliveries?user=" + u)).ok().body();
        assertTrue(deliveries.get(0).get("account").get("deprovisioned").booleanValue());
        assertTrue(absent(deliveries.get(0), "forcedMode"), "cleared once delivered");
    }

    @Test
    public void deleteWithoutAnEarlierDeactivation() {
        Dest d = destination("atlassian", Map.of("scopeEveryone", List.of("true"), "deprovision", List.of("deactivate")));
        String u = user("tomas");
        d.awaitDone("user:" + u);
        run("delete-now", u, List.of(step("scim-provisioning-delete")));
        until(WAIT, () -> d.mock.users().isEmpty());
        assertThat("delete, not the destination's deactivate", d.mock.ops(o -> method(o, "PATCH")), empty());
    }

    @Test
    public void resyncUserRestoresAForgottenLinkWithoutDuplicating() {
        Dest d = destination("atlassian", cfg("scopeEveryone", "true"));
        String u = user("ursula");
        d.awaitDone("user:" + u);
        String remote = d.link("user:" + u).get("remoteId").asText();
        admin.anonymous("POST", testResource("forget-link?destination=" + d.id + "&subject=user:" + u), null).ok();
        assertNull(d.link("user:" + u));

        run("resync", u, List.of(step("scim-provisioning-resync-user")));
        until(WAIT, () -> d.link("user:" + u) != null);
        assertEquals(remote, d.link("user:" + u).get("remoteId").asText());
        assertThat(d.mock.ops(o -> method(o, "POST")), hasSize(1));
    }

    @Test
    public void aDeadLetteredDeprovisionHoldsTheGateUnlessToldToContinue() {
        Dest d = destination("atlassian", Map.of("scopeEveryone", List.of("true"), "maxAttempts", List.of("1")));
        String held = user("wanda");
        String released = user("xavi");
        d.awaitDone("user:" + held);
        d.awaitDone("user:" + released);
        d.mock.config(Map.of("status", 400)); // the app refuses everything: deprovisions are dead-lettered
        try {
            run("gate-wait", held, List.of(
                    step("scim-provisioning-deprovision"),
                    after(step("scim-provisioning-await-deprovision"), "1s"),
                    step("disable-user")));
            run("gate-continue", released, List.of(
                    step("scim-provisioning-deprovision"),
                    after(step("scim-provisioning-await-deprovision", "onDeadLetter", "continue"), "1s"),
                    step("disable-user")));
            until(WAIT, () -> !admin.user(realm, released).get("enabled").booleanValue());
            assertEquals("dead", d.entry("user:" + held).get("state").asText());
            pause(4000);
            assertTrue(admin.user(realm, held).get("enabled").booleanValue(), "still held");
        } finally {
            d.mock.heal();
        }
    }

    @Test
    public void theGateMustBeAScheduledStep() {
        destination("atlassian", cfg("scopeEveryone", "true"));
        ProvisioningAdmin.Response r = createWorkflow("gate-now", List.of(step("scim-provisioning-await-deprovision")));
        assertEquals(400, r.status(), r.text());
        assertThat(r.text(), containsString("after"));
    }

    @Test
    public void theGateHoldsLaterStepsUntilEveryAccountIsGone() {
        Dest a = destination("atlassian", cfg("scopeEveryone", "true"));
        Dest b = destination("notion", cfg("scopeEveryone", "true"));
        String u = user("vlad");
        a.awaitDone("user:" + u);
        b.awaitDone("user:" + u);
        b.mock.config(Map.of("down", true));
        try {
            run("offboard-gated", u, List.of(
                    step("scim-provisioning-deprovision"),
                    after(step("scim-provisioning-await-deprovision"), "1s"),
                    step("disable-user")));
            until(WAIT, () -> !active(a, "vlad"));
            pause(4000); // several step-runner passes while notion is down
            assertTrue(admin.user(realm, u).get("enabled").booleanValue(), "held by the gate");
        } finally {
            b.mock.heal();
        }
        until(WAIT, () -> !active(b, "vlad"));
        until(WAIT, () -> !admin.user(realm, u).get("enabled").booleanValue());
        assertFalse(admin.user(realm, u).get("enabled").booleanValue());
    }
}
