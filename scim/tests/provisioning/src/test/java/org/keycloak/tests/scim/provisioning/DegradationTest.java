package org.keycloak.tests.scim.provisioning;

import java.util.List;
import java.util.Map;

import org.keycloak.common.Profile;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** With the {@code workflows} feature disabled the provisioning loads and everything except the workflow steps works. */
@KeycloakIntegrationTest(config = DegradationTest.WorkflowsOffConfig.class)
public class DegradationTest extends AbstractScimProvisioningTest {

    public static class WorkflowsOffConfig extends ScimProvisioningServerConfig {
        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            return super.configure(config).featuresDisabled(Profile.Feature.WORKFLOWS);
        }
    }

    @Test
    public void everythingButTheWorkflowStepWorksWithWorkflowsDisabled() {
        String serverInfo = admin.get("/admin/serverinfo").ok().text();
        assertThat(serverInfo, not(containsString("scim-provisioning-deprovision")));
        assertThat(serverInfo, containsString("\"scim-provisioning\""));

        String g = admin.createGroup(realm, "Engineering");
        Dest d = destinationForGroup("atlassian", "Engineering", Map.of());
        String u = user("rosa");
        admin.joinGroup(realm, u, g);
        d.awaitUser("rosa");
        d.awaitDone("user:" + u);
        admin.leaveGroup(realm, u, g);
        until(WAIT, () -> !d.mock.userByUserName("rosa").get("active").booleanValue());
        assertEquals(200, admin.get(provisioning("status")).status());
        List<String> errors = ServerLog.sinceClassStart().stream()
                .filter(l -> l.contains("ERROR"))
                .filter(l -> l.contains("scim"))
                .toList();
        assertThat(errors, empty());
    }
}
