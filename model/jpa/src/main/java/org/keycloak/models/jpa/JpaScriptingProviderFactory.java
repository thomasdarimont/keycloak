package org.keycloak.models.jpa;

import org.keycloak.Config;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.scripting.AbstractJsr223ScriptingProviderFactory;

import javax.persistence.EntityManager;

/**
 * Created by tom on 10.02.16.
 */
public class JpaScriptingProviderFactory extends AbstractJsr223ScriptingProviderFactory {

    public static final String ID = "jpa";

    @Override
    public JpaScriptingProvider create(KeycloakSession session) {

        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        return new JpaScriptingProvider(getScriptEngineManager(), em, session);
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return ID;
    }
}
