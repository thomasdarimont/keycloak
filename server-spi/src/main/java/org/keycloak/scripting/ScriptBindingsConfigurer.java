package org.keycloak.scripting;

import javax.script.Bindings;

/**
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public interface ScriptBindingsConfigurer {

    void configureBindings(Bindings bindings);
}