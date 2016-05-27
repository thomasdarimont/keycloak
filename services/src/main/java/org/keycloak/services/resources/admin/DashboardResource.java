package org.keycloak.services.resources.admin;

import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.analytics.AnalyticsProvider;
import org.keycloak.models.analytics.TimeSeries;
import org.keycloak.models.analytics.UserSummary;
import org.keycloak.representations.idm.DashboardRepresentation;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.sql.Date;

public class DashboardResource {

    private final RealmModel realm;

    @Context
    protected KeycloakSession session;

    public DashboardResource(RealmModel realm) {
        this.realm = realm;
    }

    /**
     * Get representation of the Dashboard Summary
     *
     * @return
     */
    @GET
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public DashboardRepresentation getSummary() {

//        auth.requireView();

        AnalyticsProvider analytics = session.analytics();

        UserSummary userSummary = analytics.getUserSummary(realm);
        TimeSeries latestUserLogins = analytics.getLatestUserLogins(realm, 5);
        TimeSeries latestUserRegistrations = analytics.getLatestUserRegistrations(realm, 5);
        TimeSeries dailyAggregatedUserLogins = analytics.getDailyAggregatedUserLogins(realm, new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 365), new Date(System.currentTimeMillis()));

        DashboardRepresentation dashboard = new DashboardRepresentation();
        dashboard.setRealmId(realm.getId());
        dashboard.setUserSummary(userSummary);
        dashboard.setLatestUserLogins(latestUserLogins);
        dashboard.setLatestUserRegistrations(latestUserRegistrations);
        dashboard.setDailyAggregatedUserLogins(dailyAggregatedUserLogins);

        return dashboard;
    }
}
