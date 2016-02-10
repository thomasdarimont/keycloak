package org.keycloak.scripting;

import javax.script.ScriptEngineManager;

/**
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public abstract class AbstractJsr223ScriptingProviderFactory implements ScriptingProviderFactory {

    private ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

    protected ScriptEngineManager getScriptEngineManager() {
        return scriptEngineManager;
    }
}
