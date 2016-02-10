package org.keycloak.scripting;

import org.keycloak.models.ScriptModel;
import org.keycloak.provider.Provider;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import java.util.List;

/**
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public interface ScriptingProvider extends Provider {

    List<ScriptModel> findAllScripts();

    ScriptModel findScript(String scriptName);

    ScriptModel saveScript(ScriptModel script);

    InvocableScript loadScript(ScriptModel script, ScriptBindingsConfigurer bindingsConfigurer);

    interface ScriptBindingsConfigurer {

        void configureBindings(Bindings bindings);
    }
}
