package org.keycloak.authentication.postauth;

import org.keycloak.provider.Provider;

/**
 * An action executed after a user has been authenticated for a client and before the client session is created or
 * tokens are issued. Actions are non-interactive by default: they either let the request proceed or deny it.
 * Interactive triggers may add required actions to the authentication session for anything that needs user input.
 * <p>
 * Instances are configured as realm components of this type, ordered by priority.
 */
public interface PostAuthenticationAction extends Provider {

    PostAuthenticationResult execute(PostAuthenticationContext context);

    @Override
    default void close() {
    }
}
