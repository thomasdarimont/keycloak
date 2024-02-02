package org.keycloak.accesscontrol.opa;

import org.keycloak.Config;
import org.keycloak.accesscontrol.AccessPolicyProvider;
import org.keycloak.accesscontrol.AccessPolicyProviderFactory;
import org.keycloak.accesscontrol.opa.OpaAccessPolicyProvider.Option;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.HashMap;
import java.util.Map;

public class OpaAccessPolicyProviderFactory implements AccessPolicyProviderFactory {

    private Map<String, Object> config;

    @Override
    public String getId() {
        return OpaAccessPolicyProvider.ID;
    }

    @Override
    public AccessPolicyProvider create(KeycloakSession session) {
        return new OpaAccessPolicyProvider(config);
    }

    @Override
    public void init(Config.Scope scope) {
        this.config = readConfig(scope);
    }

    protected Map<String, Object> readConfig(Config.Scope scope) {
        Map<String, Object> config = new HashMap<>();
        for (Option option : Option.values()) {
            config.put(option.getKey(), scope.get(option.getKey()));
        }
        return config;
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }
}
