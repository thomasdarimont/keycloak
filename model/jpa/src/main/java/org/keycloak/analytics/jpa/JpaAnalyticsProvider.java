package org.keycloak.analytics.jpa;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.analytics.AnalyticsProvider;
import org.keycloak.models.analytics.TimeSeries;
import org.keycloak.models.analytics.UserSummary;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.Date;
import java.util.List;

/**
 * Created by tom on 27.05.16.
 */
public class JpaAnalyticsProvider implements AnalyticsProvider {

    private static final String QUERY_USER_SUMMARY_BY_REALM = "getUserSummaryByRealm";
    private static final String QUERY_LATEST_EVENTS = "getLatestEventsByRealmAndTypeAndLimit";
    private static final String QUERY_GET_DAILY_AGGREGATED_EVENTS = "getDailyAggregatedEventsByRealm";

    private final EntityManager em;

    public JpaAnalyticsProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public UserSummary getUserSummary(RealmModel realm) {

        Query userSummaryQuery = em.createNamedQuery(QUERY_USER_SUMMARY_BY_REALM);
        userSummaryQuery.setParameter("realm_id", realm.getId());

        List<UserSummary> optionalUserSummaries = userSummaryQuery.getResultList();
        if (optionalUserSummaries.isEmpty()) {
            return new UserSummary(realm.getId(), 0, 0, 0, 0);
        }

        return UserSummary.class.cast(optionalUserSummaries.get(0));
    }

    @Override
    public TimeSeries getLatestUserLogins(RealmModel realm, int limit) {

        Query latestEvents = em.createNamedQuery(QUERY_LATEST_EVENTS);
        latestEvents.setParameter("realm_id", realm.getId());
        latestEvents.setParameter("event_type", "LOGIN");
        latestEvents.setParameter("event_limit", limit);

        return new TimeSeries<>("latestUserLogins", latestEvents.getResultList());
    }

    @Override
    public TimeSeries getLatestUserRegistrations(RealmModel realm, int limit) {

        Query latestEvents = em.createNamedQuery(QUERY_LATEST_EVENTS);
        latestEvents.setParameter("realm_id", realm.getId());
        latestEvents.setParameter("event_type", "REGISTER");
        latestEvents.setParameter("event_limit", limit);

        return new TimeSeries<>("latestUserRegistrations", latestEvents.getResultList());
    }

    @Override
    public TimeSeries getDailyAggregatedUserLogins(RealmModel realm, Date start, Date end) {

        Query aggregatedEvents = em.createNamedQuery(QUERY_GET_DAILY_AGGREGATED_EVENTS);
        aggregatedEvents.setParameter("realm_id", realm.getId());
        aggregatedEvents.setParameter("event_type", "LOGIN");
        aggregatedEvents.setParameter("start_date", start);
        aggregatedEvents.setParameter("end_date", end);

        return new TimeSeries<>("dailyAggregatedUserLogins", aggregatedEvents.getResultList());
    }

    @Override
    public void close() {

    }
}
