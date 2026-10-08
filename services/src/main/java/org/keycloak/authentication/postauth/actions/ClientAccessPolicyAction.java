package org.keycloak.authentication.postauth.actions;

import org.keycloak.authentication.clientaccess.ClientAccessContext;
import org.keycloak.authentication.clientaccess.ClientAccessDecision;
import org.keycloak.authentication.clientaccess.ClientAccessPolicyEvaluator;
import org.keycloak.authentication.postauth.PostAuthenticationAction;
import org.keycloak.authentication.postauth.PostAuthenticationContext;
import org.keycloak.authentication.postauth.PostAuthenticationResult;
import org.keycloak.models.KeycloakSession;

/**
 * Evaluates the client access policies referenced by the requested client against the authenticated user.
 */
public class ClientAccessPolicyAction implements PostAuthenticationAction {

    private final KeycloakSession session;

    public ClientAccessPolicyAction(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public PostAuthenticationResult execute(PostAuthenticationContext context) {
        ClientAccessContext accessContext = new ClientAccessContext(session, context.getRealm(), context.getClient(), context.getUser(), context.getTrigger());
        ClientAccessDecision decision = ClientAccessPolicyEvaluator.evaluate(accessContext);
        if (decision.isGranted()) {
            return PostAuthenticationResult.proceed();
        }
        return PostAuthenticationResult.deny(decision.getReason());
    }
}
