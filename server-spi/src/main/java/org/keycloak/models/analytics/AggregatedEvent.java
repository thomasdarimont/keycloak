package org.keycloak.models.analytics;

import java.util.Date;

/**
 * Created by tom on 27.05.16.
 */
public class AggregatedEvent {

    private String realmId;
    private String eventType;
    private Date eventDate;
    private long eventCount;

    public AggregatedEvent(String realmId, String eventType, Date eventDate, long eventCount) {
        this.realmId = realmId.intern();
        this.eventType = eventType.intern();
        this.eventDate = eventDate;
        this.eventCount = eventCount;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public Date getEventDate() {
        return eventDate;
    }

    public void setEventDate(Date eventDate) {
        this.eventDate = eventDate;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public long getEventCount() {
        return eventCount;
    }

    public void setEventCount(long eventCount) {
        this.eventCount = eventCount;
    }
}
