package org.keycloak.authentication.clientaccess;

import org.keycloak.provider.Provider;

/**
 * A single requirement inside a {@link ClientAccessPolicy}. Conditions are configured as sub-components of the policy
 * component. All conditions of a policy must match for the policy to grant access.
 */
public interface ClientAccessCondition extends Provider {

    boolean matches(ClientAccessContext context);

    @Override
    default void close() {
    }
}
