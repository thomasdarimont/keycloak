package org.keycloak.tests.scim.provisioning;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

/** Transactional enqueue in the default mode: the marker is written inside the user-change transaction. */
@KeycloakIntegrationTest(config = ScimProvisioningServerConfig.class)
public class TransactionalEnqueueTest extends EnqueueContract {
}
