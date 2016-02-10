package org.keycloak.scripting;

import org.keycloak.models.ScriptModel;

import javax.script.Invocable;
import javax.script.ScriptException;

/**
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public class InvocableScript implements Invocable {

    private final ScriptModel script;
    private final Invocable invocable;

    public InvocableScript(ScriptModel script, Invocable invocable) {
        this.script = script;
        this.invocable = invocable;
    }

    @Override
    public Object invokeMethod(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException {
        return invocable.invokeMethod(thiz, name, args);
    }

    @Override
    public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException {
        return invocable.invokeFunction(name, args);
    }

    @Override
    public <T> T getInterface(Class<T> clasz) {
        return invocable.getInterface(clasz);
    }

    @Override
    public <T> T getInterface(Object thiz, Class<T> clasz) {
        return invocable.getInterface(thiz, clasz);
    }

    public ScriptModel getScript() {
        return script;
    }
}
