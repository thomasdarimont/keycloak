package org.keycloak.authentication.authenticators.browser;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Map;

/**
 * An {@link Authenticator} that can execute a configured script during authentication flow.
 *
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public class ScriptBasedAuthenticator implements Authenticator {

    private static final Logger LOGGER = Logger.getLogger(ScriptBasedAuthenticator.class);

    static final String SCRIPT = "script";
    static final String SCRIPT_ENGINE_NAME = "scriptEngineName";
    static final String SCRIPT_NAME = "scriptName";

    static final String CONTEXT = "context";
    static final String REALM = "realm";
    static final String USER = "user";
    static final String LOG = "LOG";

    static final String ACTION = "action";
    static final String AUTHENTICATE = "authenticate";

    static final String DEFAULT_SCRIPT_TEMPLATE = "" +
            "function authenticate(context){\n" +
            "  \n" +
            "  LOG.info(scriptName + \" --> trace auth for: \" + user.username);\n" +
            "\n" +
            "  context.success();\n" +
            "}\n" +
            "\n" +
            "function action(context){\n" +
            "  context.attempted();\n" +
            "}";

    private final ScriptEngineManager scriptEngineManager;


    public ScriptBasedAuthenticator(ScriptEngineManager scriptEngineManager) {
        this.scriptEngineManager = scriptEngineManager;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {

        try {
            Invocable invocable = getInvocableScript(context);
            invocable.invokeFunction(AUTHENTICATE, context);
        } catch (Exception e) {
            LOGGER.error(e);
        }
    }

    private Invocable getInvocableScript(AuthenticationFlowContext context) throws Exception {

        Map<String, String> config = context.getAuthenticatorConfig().getConfig();

        String scriptName = config.get(SCRIPT_NAME);
        String scriptEngineName = config.get(SCRIPT_ENGINE_NAME);

        ScriptEngine engine = scriptEngineManager.getEngineByName(scriptEngineName);

        String script = config.get(SCRIPT);

        Bindings bindings = createContextBindings(context, engine, scriptName);
        engine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);

        engine.eval(script);

        return (Invocable) engine;
    }

    private Bindings createContextBindings(AuthenticationFlowContext context, ScriptEngine engine, String scriptName) {

        Bindings bindings = engine.createBindings();
        bindings.put(CONTEXT, context);
        bindings.put(REALM, context.getRealm());
        bindings.put(USER, context.getUser());
        bindings.put(SCRIPT_NAME, scriptName);
        bindings.put(LOG, LOGGER);

        return bindings;
    }

    @Override
    public void action(AuthenticationFlowContext context) {

        try {
            Invocable invocable = getInvocableScript(context);
            invocable.invokeFunction(ACTION, context);
        } catch (Exception e) {
            LOGGER.error(e);
        }
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
