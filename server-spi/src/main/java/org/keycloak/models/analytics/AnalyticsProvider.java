package org.keycloak.models.analytics;

import org.keycloak.models.RealmModel;
import org.keycloak.provider.Provider;

import java.util.Date;
import java.util.List;

/**
 * Created by tom on 27.05.16.
 */
public interface AnalyticsProvider extends Provider {

    UserSummary getUserSummary(RealmModel realm);

    TimeSeries getLatestUserLogins(RealmModel realm, int limit);

    TimeSeries getLatestUserRegistrations(RealmModel realm, int limit);

    TimeSeries getDailyAggregatedUserLogins(RealmModel realm, Date start, Date end);
}
