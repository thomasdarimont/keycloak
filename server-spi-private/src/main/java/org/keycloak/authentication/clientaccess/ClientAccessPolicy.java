package org.keycloak.authentication.clientaccess;

import org.keycloak.provider.Provider;

/**
 * A named, realm-level set of requirements a user must fulfil to access a client. Policies are realm components;
 * a client opts into one or more of them through the {@link #CLIENT_ATTRIBUTE} client attribute, which holds a
 * comma separated list of policy component names.
 */
public interface ClientAccessPolicy extends Provider {

    String CLIENT_ATTRIBUTE = "access.policies";

    /**
     * @return a result stating whether the policy grants access to the user for the client in the context
     */
    ClientAccessPolicyResult evaluate(ClientAccessContext context);

    @Override
    default void close() {
    }
}
