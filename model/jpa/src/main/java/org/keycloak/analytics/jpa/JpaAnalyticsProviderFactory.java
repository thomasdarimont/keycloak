package org.keycloak.analytics.jpa;

import org.keycloak.Config;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserProvider;
import org.keycloak.models.UserProviderFactory;
import org.keycloak.models.analytics.AnalyticsProvider;
import org.keycloak.models.analytics.AnalyticsProviderFactory;

import javax.persistence.EntityManager;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class JpaAnalyticsProviderFactory implements AnalyticsProviderFactory {

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public String getId() {
        return "analytics-jpa";
    }

    @Override
    public AnalyticsProvider create(KeycloakSession session) {
        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        return new JpaAnalyticsProvider(em);
    }

    @Override
    public void close() {
    }

}