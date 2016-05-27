package org.keycloak.representations.idm;

public class DashboardRepresentation {

    private String realmId;

    private Object userSummary;

    private Object latestUserLogins;

    private Object latestUserRegistrations;

    private Object dailyAggregatedUserLogins;

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public Object getUserSummary() {
        return userSummary;
    }

    public void setUserSummary(Object userSummary) {
        this.userSummary = userSummary;
    }

    public Object getLatestUserLogins() {
        return latestUserLogins;
    }

    public void setLatestUserLogins(Object latestUserLogins) {
        this.latestUserLogins = latestUserLogins;
    }

    public Object getLatestUserRegistrations() {
        return latestUserRegistrations;
    }

    public void setLatestUserRegistrations(Object latestUserRegistrations) {
        this.latestUserRegistrations = latestUserRegistrations;
    }

    public Object getDailyAggregatedUserLogins() {
        return dailyAggregatedUserLogins;
    }

    public void setDailyAggregatedUserLogins(Object dailyAggregatedUserLogins) {
        this.dailyAggregatedUserLogins = dailyAggregatedUserLogins;
    }
}
