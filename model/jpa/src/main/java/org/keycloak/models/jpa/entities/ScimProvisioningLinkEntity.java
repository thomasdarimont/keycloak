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

/**
 * Subject &harr; the destination's resource id, plus the hash of what was last delivered. Written
 * only through the conditional bulk statements named below; the setters exist only for the
 * persistence provider.
 */
@NamedQueries({
    @NamedQuery(
            name = "ScimProvisioningLink.findByKey",
            query =
                    "SELECT l FROM ScimProvisioningLink l WHERE l.realmId = :realmId"
                            + " AND l.destinationId = :destinationId AND l.subject = :subject"),
    @NamedQuery(
            name = "ScimProvisioningLink.findSubjectByKey",
            query =
                    "SELECT l.subject FROM ScimProvisioningLink l WHERE l.realmId = :realmId"
                            + " AND l.destinationId = :destinationId AND l.subject = :subject"),
    @NamedQuery(
            name = "ScimProvisioningLink.findBySubjects",
            query =
                    "SELECT l FROM ScimProvisioningLink l WHERE l.realmId = :realmId"
                            + " AND l.destinationId = :destinationId AND l.subject IN :subjects"),
    @NamedQuery(
            name = "ScimProvisioningLink.findByDestination",
            query =
                    "SELECT l FROM ScimProvisioningLink l WHERE l.realmId = :realmId"
                            + " AND l.destinationId = :destinationId"),
    @NamedQuery(
            name = "ScimProvisioningLink.findDestinations",
            query = "SELECT DISTINCT l.realmId, l.destinationId FROM ScimProvisioningLink l"),
    @NamedQuery(
            name = "ScimProvisioningLink.insert",
            query =
                    "INSERT INTO ScimProvisioningLink (realmId, destinationId, subject, remoteId, payloadHash,"
                            + " active, deprovisioned, userName, syncedAt)"
                            + " VALUES (:realmId, :destinationId, :subject, :remoteId, :payloadHash, :active,"
                            + " :deprovisioned, :userName, :syncedAt)"),
    @NamedQuery(
            name = "ScimProvisioningLink.update",
            query =
                    "UPDATE ScimProvisioningLink l SET l.remoteId = :remoteId, l.payloadHash = :payloadHash,"
                            + " l.active = :active, l.deprovisioned = :deprovisioned, l.userName = :userName,"
                            + " l.syncedAt = :syncedAt"
                            + " WHERE l.realmId = :realmId AND l.destinationId = :destinationId"
                            + " AND l.subject = :subject"),
    // clears the hash so the next delivery is not skipped
    @NamedQuery(
            name = "ScimProvisioningLink.invalidate",
            query =
                    "UPDATE ScimProvisioningLink l SET l.payloadHash = NULL WHERE l.realmId = :realmId"
                            + " AND l.destinationId = :destinationId AND l.subject = :subject"),
    // lifts a tombstone and clears the hash, so a deprovision is sent again
    @NamedQuery(
            name = "ScimProvisioningLink.revive",
            query =
                    "UPDATE ScimProvisioningLink l SET l.deprovisioned = false, l.payloadHash = NULL"
                            + " WHERE l.realmId = :realmId AND l.destinationId = :destinationId"
                            + " AND l.subject = :subject"),
    @NamedQuery(
            name = "ScimProvisioningLink.deleteByKey",
            query =
                    "DELETE FROM ScimProvisioningLink l WHERE l.realmId = :realmId"
                            + " AND l.destinationId = :destinationId AND l.subject = :subject"),
    @NamedQuery(
            name = "ScimProvisioningLink.deleteByDestination",
            query =
                    "DELETE FROM ScimProvisioningLink l WHERE l.realmId = :realmId"
                            + " AND l.destinationId = :destinationId"),
    @NamedQuery(
            name = "ScimProvisioningLink.deleteByRealm",
            query = "DELETE FROM ScimProvisioningLink l WHERE l.realmId = :realmId")
})
@Entity(name = "ScimProvisioningLink")
@Table(name = "SCIM_PROV_LINK")
@IdClass(ScimProvisioningLinkEntity.Key.class)
public class ScimProvisioningLinkEntity {
    @Id
    @Column(name = "REALM_ID", length = 36, nullable = false)
    private String realmId;

    @Id
    @Column(name = "DESTINATION_ID", length = 36, nullable = false)
    private String destinationId;

    @Id
    @Column(name = "SUBJECT", length = 255, nullable = false)
    private String subject;

    @Column(name = "REMOTE_ID", length = 255, nullable = false)
    private String remoteId;

    @Column(name = "PAYLOAD_HASH", length = 64)
    private String payloadHash;

    @Column(name = "ACTIVE", nullable = false)
    private boolean active;

    @Column(name = "DEPROVISIONED", nullable = false)
    private boolean deprovisioned;

    @Column(name = "USER_NAME", length = 255)
    private String userName;

    @Column(name = "SYNCED_AT", nullable = false)
    private long syncedAt;

    public String getRealmId() {
        return realmId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public String getSubject() {
        return subject;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDeprovisioned() {
        return deprovisioned;
    }

    public String getUserName() {
        return userName;
    }

    public long getSyncedAt() {
        return syncedAt;
    }

    protected void setRealmId(String v) {
        realmId = v;
    }

    protected void setDestinationId(String v) {
        destinationId = v;
    }

    protected void setSubject(String v) {
        subject = v;
    }

    protected void setRemoteId(String v) {
        remoteId = v;
    }

    protected void setPayloadHash(String v) {
        payloadHash = v;
    }

    protected void setActive(boolean v) {
        active = v;
    }

    protected void setDeprovisioned(boolean v) {
        deprovisioned = v;
    }

    protected void setUserName(String v) {
        userName = v;
    }

    protected void setSyncedAt(long v) {
        syncedAt = v;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        private String realmId;
        private String destinationId;
        private String subject;

        public Key() {}

        public Key(String realmId, String destinationId, String subject) {
            this.realmId = realmId;
            this.destinationId = destinationId;
            this.subject = subject;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return Objects.equals(realmId, k.realmId)
                    && Objects.equals(destinationId, k.destinationId)
                    && Objects.equals(subject, k.subject);
        }

        @Override
        public int hashCode() {
            return Objects.hash(realmId, destinationId, subject);
        }
    }
}
