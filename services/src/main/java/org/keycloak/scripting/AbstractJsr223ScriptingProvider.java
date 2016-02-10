package org.keycloak.scripting;

import org.keycloak.models.ScriptModel;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public abstract class AbstractJsr223ScriptingProvider implements ScriptingProvider {

    private final ScriptEngineManager scriptEngineManager;

    public AbstractJsr223ScriptingProvider(ScriptEngineManager scriptEngineManager) {
        this.scriptEngineManager = scriptEngineManager;
    }

    @Override
    public InvocableScript prepareScript(ScriptModel script, ScriptBindingsConfigurer bindingsConfigurer) {

        ScriptEngine engine = lookupScriptEngineFor(script);

        Bindings bindings = engine.createBindings();
        bindingsConfigurer.configureBindings(bindings);
        engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

        try {
            engine.eval(script.getCode());
        } catch (ScriptException se) {
            throw new ScriptExecutionException(script, se);
        }

        return new InvocableScript(script, (Invocable) engine);
    }

    protected ScriptEngine lookupScriptEngineFor(ScriptModel script) {
        return scriptEngineManager.getEngineByMimeType(script.getType());
    }
}
