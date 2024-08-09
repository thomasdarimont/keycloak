package org.keycloak.ssf.caep.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.keycloak.ssf.SecurityEvent;

/**
 * Device Compliance Change signals that a device's compliance status has changed.
 */
public class DeviceComplianceChange extends SecurityEvent {

    /**
     * The compliance status prior to the change that triggered the event
     * This MUST be one of the following strings: compliant, not-compliant
     */
    @JsonProperty("previous_status")
    protected ComplianceChange previousStatus;

    /**
     * The current status that triggered the event.
     */
    @JsonProperty("current_status")
    protected ComplianceChange currentStatus;

    public DeviceComplianceChange() {
        super(CaepEventType.DEVICE_COMPLIANCE_CHANGE);
    }

    public ComplianceChange getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(ComplianceChange previousStatus) {
        this.previousStatus = previousStatus;
    }

    public ComplianceChange getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(ComplianceChange currentStatus) {
        this.currentStatus = currentStatus;
    }

    public enum ComplianceChange {

        COMPLIANT("compliant"),
        NOT_COMPLIANT("not-compliant");

        private final String type;

        ComplianceChange(String type) {
            this.type = type;
        }

        @JsonValue
        public String getType() {
            return type;
        }
    }
}
