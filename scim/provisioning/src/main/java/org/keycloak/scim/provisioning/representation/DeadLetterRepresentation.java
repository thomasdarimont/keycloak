package org.keycloak.scim.provisioning.representation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** A change a destination refused on every attempt. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "subject",
    "name",
    "forced",
    "attempts",
    "lastErrorClass",
    "lastError",
    "deadSince"
})
public class DeadLetterRepresentation {

    /** {@code user:<id>} or {@code group:<id>}. */
    private String subject;

    /** The username or group name, while it still exists. */
    private String name;

    private boolean forced;
    private int attempts;
    private String lastErrorClass;
    private String lastError;
    private long deadSince;

    public DeadLetterRepresentation() {}

    public DeadLetterRepresentation(String subject, String name, boolean forced, int attempts, String lastErrorClass, String lastError, long deadSince) {
        this.subject = subject;
        this.name = name;
        this.forced = forced;
        this.attempts = attempts;
        this.lastErrorClass = lastErrorClass;
        this.lastError = lastError;
        this.deadSince = deadSince;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isForced() {
        return forced;
    }

    public void setForced(boolean forced) {
        this.forced = forced;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastErrorClass() {
        return lastErrorClass;
    }

    public void setLastErrorClass(String lastErrorClass) {
        this.lastErrorClass = lastErrorClass;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public long getDeadSince() {
        return deadSince;
    }

    public void setDeadSince(long deadSince) {
        this.deadSince = deadSince;
    }
}
