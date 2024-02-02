package org.keycloak.accesscontrol;

import org.keycloak.provider.Provider;

public interface AccessPolicyProvider extends Provider {

    AccessDecision evaluate(AccessDecisionContext context);

    default void close() {
    }
}
