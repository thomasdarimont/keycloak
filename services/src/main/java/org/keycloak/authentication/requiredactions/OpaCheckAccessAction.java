package org.keycloak.authentication.requiredactions;

import jakarta.ws.rs.core.Response;
import org.keycloak.Config;
import org.keycloak.accesscontrol.AccessDecision;
import org.keycloak.accesscontrol.AccessDecisionContext;
import org.keycloak.accesscontrol.AccessPolicyProvider;
import org.keycloak.accesscontrol.opa.OpaAccessPolicyProvider;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.messages.Messages;

/**
 * Required Action that uses an OpenPolicyAgent access policy to determine if the user can access the client.
 */
public class OpaCheckAccessAction implements RequiredActionProvider {

    public static final String ID = "opa-check-access";

    public static final String ACTION_ALREADY_EXECUTED_MARKER = ID;

    @Override
    public void evaluateTriggers(RequiredActionContext context) {

        var authSession = context.getAuthenticationSession();
        if (authSession.getAuthNote(ACTION_ALREADY_EXECUTED_MARKER) != null) {
            return;
        }
        authSession.setAuthNote(ACTION_ALREADY_EXECUTED_MARKER, "true");

        authSession.addRequiredAction(ID);
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {

        var realm = context.getRealm();
        var user = context.getUser();
        var session = context.getSession();
        var authSession = context.getAuthenticationSession();
        var client = authSession.getClient();

        AccessPolicyProvider accessPolicyProvider = session.getProvider(AccessPolicyProvider.class, OpaAccessPolicyProvider.ID);
        AccessDecisionContext decisionContext = new AccessDecisionContext(session, realm, user, client);
        AccessDecision accessDecision = accessPolicyProvider.evaluate(decisionContext);

        if (accessDecision.isAllowed()) {
            context.success();
            return;
        }

        // deny access
        var loginForm = session.getProvider(LoginFormsProvider.class);
        loginForm.setError(Messages.ACCESS_DENIED, user.getUsername());

        context.challenge(loginForm.createErrorPage(Response.Status.FORBIDDEN));
    }

    @Override
    public void processAction(RequiredActionContext context) {
        // NOOP
    }

    @Override
    public void close() {
        // NOOP
    }

    public static class Factory implements RequiredActionFactory {

        private static final OpaCheckAccessAction INSTANCE = new OpaCheckAccessAction();

        @Override
        public String getId() {
            return OpaCheckAccessAction.ID;
        }

        @Override
        public String getDisplayText() {
            return "OpenPolicyAgent: Check Access";
        }

        @Override
        public boolean isOneTimeAction() {
            return false;
        }

        @Override
        public RequiredActionProvider create(KeycloakSession session) {
            return INSTANCE;
        }

        @Override
        public void init(Config.Scope scope) {
            // NOOP
        }

        @Override
        public void postInit(KeycloakSessionFactory factory) {
            // NOOP
        }

        @Override
        public void close() {
            // NOOP
        }
    }
}
