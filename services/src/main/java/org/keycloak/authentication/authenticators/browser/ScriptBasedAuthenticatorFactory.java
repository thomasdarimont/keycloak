package org.keycloak.authentication.authenticators.browser;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import javax.script.ScriptEngineManager;
import java.util.List;

import static java.util.Arrays.asList;
import static org.keycloak.authentication.authenticators.browser.ScriptBasedAuthenticator.DEFAULT_SCRIPT_TEMPLATE;
import static org.keycloak.authentication.authenticators.browser.ScriptBasedAuthenticator.SCRIPT;
import static org.keycloak.authentication.authenticators.browser.ScriptBasedAuthenticator.SCRIPT_ENGINE_NAME;
import static org.keycloak.authentication.authenticators.browser.ScriptBasedAuthenticator.SCRIPT_NAME;
import static org.keycloak.provider.ProviderConfigProperty.LIST_TYPE;
import static org.keycloak.provider.ProviderConfigProperty.SCRIPT_TYPE;
import static org.keycloak.provider.ProviderConfigProperty.STRING_TYPE;

/**
 * An {@link AuthenticatorFactory} for {@link ScriptBasedAuthenticator}s.
 *
 * @author <a href="mailto:thomas.darimont@gmail.com">Thomas Darimont</a>
 */
public class ScriptBasedAuthenticatorFactory implements AuthenticatorFactory {

    static final String PROVIDER_ID = "auth-script-based";

    static final ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();

    static final ScriptBasedAuthenticator SINGLETON = new ScriptBasedAuthenticator(SCRIPT_ENGINE_MANAGER);

    static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.OPTIONAL,
            AuthenticationExecutionModel.Requirement.DISABLED};

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        //NOOP
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        //NOOP
    }

    @Override
    public void close() {
        //NOOP
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getReferenceCategory() {
        return "script";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public String getDisplayType() {
        return "Script based Authentication";
    }

    @Override
    public String getHelpText() {
        return "Script based authentication.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {

        ProviderConfigProperty scriptTypeList = new ProviderConfigProperty();
        scriptTypeList.setType(LIST_TYPE);
        scriptTypeList.setName(SCRIPT_ENGINE_NAME);
        scriptTypeList.setLabel("Script Engine");

        //TODO allow to configure supported script engines via keycloak-server.json
        scriptTypeList.setDefaultValue(asList("nashorn"));

        scriptTypeList.setHelpText("Script Engine Name");

        ProviderConfigProperty scriptName = new ProviderConfigProperty();
        scriptName.setType(STRING_TYPE);
        scriptName.setName(SCRIPT_NAME);
        scriptName.setLabel("Script Name");
        scriptName.setHelpText("The name of the script used to authenticate.");

        ProviderConfigProperty script = new ProviderConfigProperty();
        script.setType(SCRIPT_TYPE);
        script.setName(SCRIPT);
        script.setLabel("Script");
        script.setDefaultValue(DEFAULT_SCRIPT_TEMPLATE);
        script.setHelpText("The script used to authenticate.");

        return asList(scriptTypeList, scriptName, script);
    }
}
