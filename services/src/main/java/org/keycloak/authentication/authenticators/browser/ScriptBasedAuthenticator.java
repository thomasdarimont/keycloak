package org.keycloak.authentication.authenticators.browser;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.scripting.InvocableScript;
import org.keycloak.scripting.Script;

import java.util.Map;

/**
 * An {@link Authenticator} that can execute a configured script during authentication flow.
 *
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public class ScriptBasedAuthenticator implements Authenticator {

    private static final Logger LOGGER = Logger.getLogger(ScriptBasedAuthenticator.class);

    static final String SCRIPT_SOURCE = "scriptSource";
    static final String SCRIPT_NAME = "scriptName";

    static final String CONTEXT = "context";
    static final String REALM = "realm";
    static final String USER = "user";
    static final String LOG = "LOG";

    static final String ACTION = "action";
    static final String AUTHENTICATE = "authenticate";

    @Override
    public void authenticate(AuthenticationFlowContext context) {

        try {
            getInvocableScript(context).invokeFunction(AUTHENTICATE, context);
        } catch (Exception e) {
            LOGGER.error(e);
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {

        try {
            getInvocableScript(context).invokeFunction(ACTION, context);
        } catch (Exception e) {
            LOGGER.error(e);
        }
    }

    private InvocableScript getInvocableScript(AuthenticationFlowContext context) throws Exception {

        Map<String, String> config = context.getAuthenticatorConfig().getConfig();

        String scriptName = config.get(SCRIPT_NAME);
        String scriptCode = config.get(SCRIPT_SOURCE);

        Script script = new Script(null, scriptName, "text/javascript", scriptCode, "");

        return context.getSession().scripting().loadScript(script, (bindings -> {
            bindings.put(CONTEXT, context);
            bindings.put(REALM, context.getRealm());
            bindings.put(USER, context.getUser());
            bindings.put(SCRIPT_NAME, scriptName);
            bindings.put(LOG, LOGGER);
        }));
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return false;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        //NOOP
    }

    @Override
    public void close() {
        //NOOP
    }
}
