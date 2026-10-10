package org.keycloak.models.jpa.entities;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import org.hibernate.annotations.Nationalized;

/**
 * Per-destination health, backoff and reconcile state. Kept out of component config on purpose:
 * component config is cluster-cached and admin-visible. Written only through the conditional bulk
 * statements named below; the setters exist only for the persistence provider.
 */
@NamedQueries({
    @NamedQuery(
            name = "ScimProvisioningHealth.findByDestination",
            query =
                    "SELECT h FROM ScimProvisioningHealth h WHERE h.realmId = :realmId"
                            + " AND h.destinationId = :destinationId"),
    @NamedQuery(name = "ScimProvisioningHealth.findAll", query = "SELECT h FROM ScimProvisioningHealth h"),
    @NamedQuery(
            name = "ScimProvisioningHealth.findDestinations",
            query = "SELECT DISTINCT h.realmId, h.destinationId FROM ScimProvisioningHealth h"),
    @NamedQuery(
            name = "ScimProvisioningHealth.insert",
            query =
                    "INSERT INTO ScimProvisioningHealth (realmId, destinationId, createdAt, consecutiveFailures,"
                            + " mismatches) VALUES (:realmId, :destinationId, :now, 0, 0)"),
    // a successful delivery: clears the backoff
    @NamedQuery(
            name = "ScimProvisioningHealth.recordSuccess",
            query =
                    "UPDATE ScimProvisioningHealth h SET h.lastSuccessAt = :now, h.consecutiveFailures = 0,"
                            + " h.retryAt = NULL"
                            + " WHERE h.realmId = :realmId AND h.destinationId = :destinationId"),
    // matches a row only when the destination was failing, so recovery is logged once
    @NamedQuery(
            name = "ScimProvisioningHealth.clearFailing",
            query =
                    "UPDATE ScimProvisioningHealth h SET h.failingSince = NULL WHERE h.realmId = :realmId"
                            + " AND h.destinationId = :destinationId AND h.failingSince IS NOT NULL"),
    // lifts a backoff (the destination was just reconfigured, e.g. a rotated token)
    @NamedQuery(
            name = "ScimProvisioningHealth.clearBackoff",
            query =
                    "UPDATE ScimProvisioningHealth h SET h.retryAt = NULL, h.consecutiveFailures = 0"
                            + " WHERE h.realmId = :realmId AND h.destinationId = :destinationId"),
    @NamedQuery(
            name = "ScimProvisioningHealth.recordTransient",
            query =
                    "UPDATE ScimProvisioningHealth h SET h.consecutiveFailures = h.consecutiveFailures + 1,"
                            + " h.retryAt = :retryAt, h.lastErrorClass = :errorClass, h.lastError = :error"
                            + " WHERE h.realmId = :realmId AND h.destinationId = :destinationId"),
    // matches a row only when the destination has just started failing
    @NamedQuery(
            name = "ScimProvisioningHealth.startFailing",
            query =
                    "UPDATE ScimProvisioningHealth h SET h.failingSince = :now WHERE h.realmId = :realmId"
                            + " AND h.destinationId = :destinationId AND h.failingSince IS NULL"),
    @NamedQuery(
            name = "ScimProvisioningHealth.recordProblem",
            query =
                    "UPDATE ScimProvisioningHealth h SET h.lastErrorClass = :errorClass, h.lastError = :error"
                            + " WHERE h.realmId = :realmId AND h.destinationId = :destinationId"),
    @NamedQuery(
            name = "ScimProvisioningHealth.recordReconcile",
            query =
                    "UPDATE ScimProvisioningHealth h SET h.lastReconcileAt = :at, h.lastReconcileStatus = :status,"
                            + " h.lastReconcileReport = :report, h.mismatches = :mismatches"
                            + " WHERE h.realmId = :realmId AND h.destinationId = :destinationId"),
    @NamedQuery(
            name = "ScimProvisioningHealth.deleteByDestination",
            query =
                    "DELETE FROM ScimProvisioningHealth h WHERE h.realmId = :realmId"
                            + " AND h.destinationId = :destinationId"),
    @NamedQuery(
            name = "ScimProvisioningHealth.deleteByRealm",
            query = "DELETE FROM ScimProvisioningHealth h WHERE h.realmId = :realmId")
})
@Entity(name = "ScimProvisioningHealth")
@Table(name = "SCIM_PROV_HEALTH")
@IdClass(ScimProvisioningHealthEntity.Key.class)
public class ScimProvisioningHealthEntity {
    @Id
    @Column(name = "REALM_ID", length = 36, nullable = false)
    private String realmId;

    @Id
    @Column(name = "DESTINATION_ID", length = 36, nullable = false)
    private String destinationId;

    @Column(name = "CREATED_AT", nullable = false)
    private long createdAt;

    @Column(name = "LAST_SUCCESS_AT")
    private Long lastSuccessAt;

    @Column(name = "LAST_ERROR_CLASS", length = 32)
    private String lastErrorClass;

    @Column(name = "LAST_ERROR", length = 300)
    private String lastError;

    @Column(name = "FAILING_SINCE")
    private Long failingSince;

    @Column(name = "RETRY_AT")
    private Long retryAt;

    @Column(name = "CONSECUTIVE_FAILURES", nullable = false)
    private int consecutiveFailures;

    @Column(name = "LAST_RECONCILE_AT")
    private Long lastReconcileAt;

    @Column(name = "LAST_RECONCILE_STATUS", length = 16)
    private String lastReconcileStatus;

    @Nationalized
    @Column(name = "LAST_RECONCILE_REPORT")
    private String lastReconcileReport;

    @Column(name = "MISMATCHES", nullable = false)
    private int mismatches;

    public String getRealmId() {
        return realmId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Long getLastSuccessAt() {
        return lastSuccessAt;
    }

    public String getLastErrorClass() {
        return lastErrorClass;
    }

    public String getLastError() {
        return lastError;
    }

    public Long getFailingSince() {
        return failingSince;
    }

    public Long getRetryAt() {
        return retryAt;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public Long getLastReconcileAt() {
        return lastReconcileAt;
    }

    public String getLastReconcileStatus() {
        return lastReconcileStatus;
    }

    public String getLastReconcileReport() {
        return lastReconcileReport;
    }

    public int getMismatches() {
        return mismatches;
    }

    protected void setRealmId(String v) {
        realmId = v;
    }

    protected void setDestinationId(String v) {
        destinationId = v;
    }

    protected void setCreatedAt(long v) {
        createdAt = v;
    }

    protected void setLastSuccessAt(Long v) {
        lastSuccessAt = v;
    }

    protected void setLastErrorClass(String v) {
        lastErrorClass = v;
    }

    protected void setLastError(String v) {
        lastError = v;
    }

    protected void setFailingSince(Long v) {
        failingSince = v;
    }

    protected void setRetryAt(Long v) {
        retryAt = v;
    }

    protected void setConsecutiveFailures(int v) {
        consecutiveFailures = v;
    }

    protected void setLastReconcileAt(Long v) {
        lastReconcileAt = v;
    }

    protected void setLastReconcileStatus(String v) {
        lastReconcileStatus = v;
    }

    protected void setLastReconcileReport(String v) {
        lastReconcileReport = v;
    }

    protected void setMismatches(int v) {
        mismatches = v;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        private String realmId;
        private String destinationId;

        public Key() {}

        public Key(String realmId, String destinationId) {
            this.realmId = realmId;
            this.destinationId = destinationId;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return Objects.equals(realmId, k.realmId) && Objects.equals(destinationId, k.destinationId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(realmId, destinationId);
        }
    }
}
