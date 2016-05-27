package org.keycloak.models.analytics;

/**
 * Created by tom on 27.05.16.
 */
public class UserSummary {

    private String realmId;
    private long loginCount;
    private long registerCount;
    private long activeUsersCount;
    private long totalUserCount;

    public UserSummary(String realmId, long loginCount, long registerCount, long activeUsersCount, long totalUserCount) {
        this.realmId = realmId;
        this.loginCount = loginCount;
        this.registerCount = registerCount;
        this.activeUsersCount = activeUsersCount;
        this.totalUserCount = totalUserCount;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public long getLoginCount() {
        return loginCount;
    }

    public void setLoginCount(long loginCount) {
        this.loginCount = loginCount;
    }

    public long getRegisterCount() {
        return registerCount;
    }

    public void setRegisterCount(long registerCount) {
        this.registerCount = registerCount;
    }

    public long getActiveUsersCount() {
        return activeUsersCount;
    }

    public void setActiveUsersCount(long activeUsersCount) {
        this.activeUsersCount = activeUsersCount;
    }

    public long getTotalUserCount() {
        return totalUserCount;
    }

    public void setTotalUserCount(long totalUserCount) {
        this.totalUserCount = totalUserCount;
    }
}
