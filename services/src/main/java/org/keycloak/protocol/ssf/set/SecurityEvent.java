package org.keycloak.protocol.ssf.set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

public abstract class SecurityEvent {

    @JsonIgnore
    protected final String eventType;

    /**
     * The time of the event (UNIX timestamp)
     */
    @JsonProperty("event_timestamp")
    protected long eventTimestamp;

    /**
     * The entity that initiated the event
     */
    @JsonProperty("initiating_entity")
    protected InitiatingEntity initiatingEntity;

    /**
     * A localized administrative message intended for logging and auditing.
     * key is language code, value is message.
     */
    @JsonProperty("reason_admin")
    protected Map<String, String> reasonAdmin;

    /**
     * A localized message intended for the end user.
     * key is language code, value is message.
     */
    @JsonProperty("reason_user")
    protected Map<String, String> reasonUser;

    public SecurityEvent(String eventType) {
        this.eventType = eventType;
    }

    public long getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(long eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public InitiatingEntity getInitiatingEntity() {
        return initiatingEntity;
    }

    public void setInitiatingEntity(InitiatingEntity initiatingEntity) {
        this.initiatingEntity = initiatingEntity;
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

    public enum InitiatingEntity {
        ADMIN("admin"),
        USER("user"),
        POLICY("policy"),
        SYSTEM("system"),
        ;

        private final String code;

        InitiatingEntity(String code) {
            this.code = code;
        }

        @JsonValue
        public String getCode() {
            return code;
        }
    }
}
