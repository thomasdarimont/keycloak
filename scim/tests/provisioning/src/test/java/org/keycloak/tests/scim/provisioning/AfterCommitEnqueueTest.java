package org.keycloak.tests.scim.provisioning;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;

/** Transactional enqueue with {@code enqueue-mode=after-commit}: the marker is written only after the commit. */
@KeycloakIntegrationTest(config = AfterCommitEnqueueTest.AfterCommitConfig.class)
public class AfterCommitEnqueueTest extends EnqueueContract {

    public static class AfterCommitConfig extends ScimProvisioningServerConfig {
        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            return provisioning(super.configure(config), "enqueue-mode", "after-commit");
        }
    }
}
