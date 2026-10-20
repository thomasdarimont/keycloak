package org.keycloak.scim.provisioning.representation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * What would be sent for a user now: {@code action} is {@code upsert}, {@code deprovision} or
 * {@code none}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"destination", "subject", "action", "reason", "resource", "hash"})
public class PreviewRepresentation {

    private String destination;
    private String subject;
    private String action;
    private String reason;

    /** The SCIM resource the mapping builds, for a user in scope. */
    private JsonNode resource;

    private String hash;

    public PreviewRepresentation() {}

    public PreviewRepresentation(String destination, String subject) {
        this.destination = destination;
        this.subject = subject;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public JsonNode getResource() {
        return resource;
    }

    public void setResource(JsonNode resource) {
        this.resource = resource;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
