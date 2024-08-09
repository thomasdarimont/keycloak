package org.keycloak.ssf;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.ssf.subjects.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;

public class SecurityEventToken extends JsonWebToken {

    @JsonProperty("txn")
    protected String txn;

    @JsonProperty("sub_id")
    protected SubjectId subjectId;

    @JsonProperty("events")
    protected Map<String, SecurityEvent> events;

    public String getTxn() {
        return txn;
    }

    public void setTxn(String txn) {
        this.txn = txn;
    }

    public SecurityEventToken txn(String txn) {
        setTxn(txn);
        return this;
    }

    public SubjectId getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(SubjectId subjectId) {
        this.subjectId = subjectId;
    }

    public SecurityEventToken subjectId(SubjectId subjectId) {
        setSubjectId(subjectId);
        return this;
    }

    public Map<String, SecurityEvent> getEvents() {
        if (events == null) {
            events = new LinkedHashMap<>();
        }
        return events;
    }

    public void setEvents(Map<String, SecurityEvent> events) {
        this.events = events;
    }

    public SecurityEventToken addEvent(SecurityEvent event) {
        getEvents().put(event.getEventType(), event);
        return this;
    }
}
