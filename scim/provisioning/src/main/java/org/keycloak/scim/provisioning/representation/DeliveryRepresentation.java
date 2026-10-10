package org.keycloak.scim.provisioning.representation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** One destination in {@code GET /scim-provisioning/deliveries?user=}: the offboarding poll. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "destination",
    "id",
    "state",
    "done",
    "forced",
    "forcedMode",
    "attempts",
    "lastErrorClass",
    "lastError",
    "updatedAt",
    "account"
})
public class DeliveryRepresentation {

    private String destination;
    private String id;

    /** {@code pending}, {@code done}, {@code dead}, or {@code none} when nothing was ever queued. */
    private String state;

    private boolean done;
    private Boolean forced;
    private String forcedMode;
    private Integer attempts;
    private String lastErrorClass;
    private String lastError;
    private Long updatedAt;
    private AccountRepresentation account;

    /** What the destination holds for the user, as far as Keycloak knows. */
    @JsonPropertyOrder({"remoteId", "active", "deprovisioned", "userName", "syncedAt"})
    public static class AccountRepresentation {
        private String remoteId;
        private boolean active;
        private boolean deprovisioned;
        private String userName;
        private long syncedAt;

        public AccountRepresentation() {}

        public AccountRepresentation(String remoteId, boolean active, boolean deprovisioned, String userName, long syncedAt) {
            this.remoteId = remoteId;
            this.active = active;
            this.deprovisioned = deprovisioned;
            this.userName = userName;
            this.syncedAt = syncedAt;
        }

        public String getRemoteId() {
            return remoteId;
        }

        public void setRemoteId(String remoteId) {
            this.remoteId = remoteId;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public boolean isDeprovisioned() {
            return deprovisioned;
        }

        public void setDeprovisioned(boolean deprovisioned) {
            this.deprovisioned = deprovisioned;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public long getSyncedAt() {
            return syncedAt;
        }

        public void setSyncedAt(long syncedAt) {
            this.syncedAt = syncedAt;
        }
    }

    public DeliveryRepresentation() {}

    public DeliveryRepresentation(String destination, String id, String state, boolean done) {
        this.destination = destination;
        this.id = id;
        this.state = state;
        this.done = done;
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

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public Boolean getForced() {
        return forced;
    }

    public void setForced(Boolean forced) {
        this.forced = forced;
    }

    public String getForcedMode() {
        return forcedMode;
    }

    public void setForcedMode(String forcedMode) {
        this.forcedMode = forcedMode;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
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

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public AccountRepresentation getAccount() {
        return account;
    }

    public void setAccount(AccountRepresentation account) {
        this.account = account;
    }
}
