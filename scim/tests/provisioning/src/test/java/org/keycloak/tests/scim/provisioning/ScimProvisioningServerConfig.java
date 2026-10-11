package org.keycloak.tests.scim.provisioning;

import org.keycloak.common.Profile;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;

/**
 * The server most provisioning tests share: the {@code scim-provisioning} feature, the test-only realm resource
 * {@code scim-provisioning-test} (from {@code keycloak-tests-custom-providers}), metrics, and test-speed drainer
 * options. Tests that need other node options extend it in their own config class (the framework restarts the server
 * when the config class changes).
 */
public class ScimProvisioningServerConfig implements KeycloakServerConfig {

    @Override
    public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
        config.features(Profile.Feature.SCIM_PROVISIONING)
                .dependency("org.keycloak.tests", "keycloak-tests-custom-providers")
                .option("metrics-enabled", "true");
        provisioning(config, "drain-interval-millis", "500");
        provisioning(config, "transient-backoff-base-millis", "500");
        provisioning(config, "transient-backoff-max-millis", "3000");
        provisioning(config, "terminal-backoff-base-millis", "200");
        provisioning(config, "terminal-backoff-max-millis", "1000");
        provisioning(config, "group-deferral-millis", "500");
        provisioning(config, "sweep-interval-millis", "2000");
        // the workflow step runner (every 12 h by default; seconds) re-runs scheduled and held steps
        config.spiOption("events-listener", "workflow-event-listener", "step-runner-task-interval", "2");
        config.log().categoryLevel("org.keycloak.scim.provisioning", "DEBUG");
        return config;
    }

    /** {@code --spi-events-listener--scim-provisioning--<option>=<value>}. */
    protected static KeycloakServerConfigBuilder provisioning(KeycloakServerConfigBuilder config, String option,
            String value) {
        return config.spiOption("events-listener", "scim-provisioning", option, value);
    }
}
