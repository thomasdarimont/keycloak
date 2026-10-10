package org.keycloak.scim.provisioning.representation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** One destination in {@code GET /scim-provisioning/status}. Times are epoch milliseconds. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "destination",
    "id",
    "state",
    "backlog",
    "dead",
    "lastSuccessAt",
    "lastReconcileAt",
    "lastReconcileStatus",
    "mismatches",
    "lastProblem",
    "lastProblemClass",
    "failingSince",
    "retryAt",
    "consecutiveFailures"
})
public class DestinationStatusRepresentation {

    private String destination;
    private String id;

    /** {@code healthy}, {@code catching_up}, {@code failing} or {@code off}. */
    private String state;

    private long backlog;
    private long dead;
    private Long lastSuccessAt;
    private Long lastReconcileAt;
    private String lastReconcileStatus;
    private Integer mismatches;
    private String lastProblem;
    private String lastProblemClass;
    private Long failingSince;
    private Long retryAt;
    private Integer consecutiveFailures;

    public DestinationStatusRepresentation() {}

    public DestinationStatusRepresentation(String destination, String id, String state, long backlog, long dead) {
        this.destination = destination;
        this.id = id;
        this.state = state;
        this.backlog = backlog;
        this.dead = dead;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public long getBacklog() {
        return backlog;
    }

    public void setBacklog(long backlog) {
        this.backlog = backlog;
    }

    public long getDead() {
        return dead;
    }

    public void setDead(long dead) {
        this.dead = dead;
    }

    public Long getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(Long lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public Long getLastReconcileAt() {
        return lastReconcileAt;
    }

    public void setLastReconcileAt(Long lastReconcileAt) {
        this.lastReconcileAt = lastReconcileAt;
    }

    public String getLastReconcileStatus() {
        return lastReconcileStatus;
    }

    public void setLastReconcileStatus(String lastReconcileStatus) {
        this.lastReconcileStatus = lastReconcileStatus;
    }

    public Integer getMismatches() {
        return mismatches;
    }

    public void setMismatches(Integer mismatches) {
        this.mismatches = mismatches;
    }

    public String getLastProblem() {
        return lastProblem;
    }

    public void setLastProblem(String lastProblem) {
        this.lastProblem = lastProblem;
    }

    public String getLastProblemClass() {
        return lastProblemClass;
    }

    public void setLastProblemClass(String lastProblemClass) {
        this.lastProblemClass = lastProblemClass;
    }

    public Long getFailingSince() {
        return failingSince;
    }

    public void setFailingSince(Long failingSince) {
        this.failingSince = failingSince;
    }

    public Long getRetryAt() {
        return retryAt;
    }

    public void setRetryAt(Long retryAt) {
        this.retryAt = retryAt;
    }

    public Integer getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(Integer consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }
}
