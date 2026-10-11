package org.keycloak.scim.provisioning.representation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The last reconcile of a destination: {@code status} is {@code ok}, {@code failed} or {@code
 * never}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"destination", "id", "status", "at", "mismatches", "report"})
public class ReconcileReportRepresentation {

    private String destination;
    private String id;
    private String status;
    private Long at;
    private Integer mismatches;

    /**
     * Counts and up to 200 subjects per finding ({@code missing}, {@code linked}, {@code
     * mismatched}).
     */
    private JsonNode report;

    public ReconcileReportRepresentation() {}

    public ReconcileReportRepresentation(String destination, String id) {
        this.destination = destination;
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAt() {
        return at;
    }

    public void setAt(Long at) {
        this.at = at;
    }

    public Integer getMismatches() {
        return mismatches;
    }

    public void setMismatches(Integer mismatches) {
        this.mismatches = mismatches;
    }

    public JsonNode getReport() {
        return report;
    }

    public void setReport(JsonNode report) {
        this.report = report;
    }
}
