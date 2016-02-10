package org.keycloak.scripting;

/**
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public class ScriptNotFoundException extends RuntimeException{

    public ScriptNotFoundException(String scriptName) {
        super("Could not find script with name '" + scriptName +"'");
    }
}
