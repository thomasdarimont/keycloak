package org.keycloak.ssf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class SecurityEvent {

    @JsonIgnore
    protected final String eventType;

    /**
     * Current risk level of the device
     */
    @JsonProperty("current_level")
    protected String currentLevel; // "low" "medium" "high" "secure" "none"

    /**
     * The time of the event (UNIX timestamp)
     */
    @JsonProperty("event_timestamp")
    protected long eventTimestamp;

    /**
     * The entity that initiated the event
     */
    @JsonProperty("initiating_entity")
    protected String initiatingEntity; // "admin" "user" "policy" "system"

    /**
     * Previous risk level of the device
     */
    @JsonProperty("previous_level")
    protected String previousLevel; // "low" "medium" "high" "secure" "none"

    /**
     * A localized administrative message intended for logging and auditing.
     */
    @JsonProperty("reason_admin")
    protected Map<String, String> reasonAdmin;

    /**
     * A localized message intended for the end user.
     */
    @JsonProperty("reason_user")
    protected Map<String, String> reasonUser;

    public SecurityEvent(String eventType) {
        this.eventType = eventType;
    }

    public String getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(String currentLevel) {
        this.currentLevel = currentLevel;
    }

    public long getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(long eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public String getInitiatingEntity() {
        return initiatingEntity;
    }

    public void setInitiatingEntity(String initiatingEntity) {
        this.initiatingEntity = initiatingEntity;
    }

    public String getPreviousLevel() {
        return previousLevel;
    }

    public void setPreviousLevel(String previousLevel) {
        this.previousLevel = previousLevel;
    }

    public Map<String, String> getReasonAdmin() {
        return reasonAdmin;
    }

    public void setReasonAdmin(Map<String, String> reasonAdmin) {
        this.reasonAdmin = reasonAdmin;
    }

    public Map<String, String> getReasonUser() {
        return reasonUser;
    }

    public void setReasonUser(Map<String, String> reasonUser) {
        this.reasonUser = reasonUser;
    }

    public String getEventType() {
        return eventType;
    }
}
