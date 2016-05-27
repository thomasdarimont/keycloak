package org.keycloak.models.analytics;

import java.util.Date;

/**
 * Created by tom on 27.05.16.
 */
public class AnalyticsEvent {

    private String realmId;
    private String userId;
    private String username;
    private String eventType;
    private long eventOrder;
    private Date eventTimestamp;

    public AnalyticsEvent(String realmId, String userId, String username, String eventType, long eventOrder, Date eventTimestamp) {
        this.realmId = realmId.intern();
        this.userId = userId;
        this.username = username;
        this.eventType = eventType.intern();
        this.eventOrder = eventOrder;
        this.eventTimestamp = eventTimestamp;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public long getEventOrder() {
        return eventOrder;
    }

    public void setEventOrder(long eventOrder) {
        this.eventOrder = eventOrder;
    }

    public Date getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Date eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }
}
