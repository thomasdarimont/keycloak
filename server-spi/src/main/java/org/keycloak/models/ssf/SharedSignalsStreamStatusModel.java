package org.keycloak.models.ssf;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SharedSignalsStreamStatusModel {

    @JsonProperty("stream_id")
    private String id;

    @JsonProperty("status")
    private SharedSignalsStreamStatus status;

    @JsonProperty("reason")
    private String reason;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SharedSignalsStreamStatus getStatus() {
        return status;
    }

    public void setStatus(SharedSignalsStreamStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "{" +
               "id='" + id + '\'' +
               ", status=" + status +
               ", reason='" + reason + '\'' +
               '}';
    }
}
