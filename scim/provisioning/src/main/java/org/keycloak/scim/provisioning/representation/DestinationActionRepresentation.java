package org.keycloak.scim.provisioning.representation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * What an action did to one destination: {@code replay} ({@code state=pending}), {@code
 * deprovision} ({@code state=pending|off}), {@code resync} and {@code reconcile} ({@code
 * status=queued}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"destination", "id", "subject", "state", "status"})
public class DestinationActionRepresentation {
    private String destination;
    private String id;
    private String subject;
    private String state;
    private String status;

    public DestinationActionRepresentation() {}

    public DestinationActionRepresentation(String destination, String id, String subject, String state, String status) {
        this.destination = destination;
        this.id = id;
        this.subject = subject;
        this.state = state;
        this.status = status;
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

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
