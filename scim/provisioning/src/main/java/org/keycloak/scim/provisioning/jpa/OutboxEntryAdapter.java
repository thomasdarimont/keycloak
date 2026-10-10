package org.keycloak.scim.provisioning.jpa;

import org.keycloak.events.outbox.OutboxEntry;
import org.keycloak.events.outbox.OutboxEntryStatus;
import org.keycloak.scim.provisioning.model.OutboxEntryModel;

/**
 * {@link OutboxEntryModel} over one of our rows in Keycloak's {@code OUTBOX_ENTRY}: the destination
 * is the owner, the subject the entry type, and what that table has no column for is in {@link
 * EntryMetadata}. Reads only; see {@link
 * org.keycloak.scim.provisioning.model.ScimProvisioningProvider} for the transitions.
 */
public class OutboxEntryAdapter implements OutboxEntryModel {

    protected final OutboxEntry entry;
    protected final EntryMetadata meta;

    /** Over Keycloak's read-only view of the row; its entity implements it. */
    public OutboxEntryAdapter(OutboxEntry entry) {
        this.entry = entry;
        this.meta = EntryMetadata.parse(entry.getMetadata());
    }

    EntryMetadata getMeta() {
        return meta;
    }

    @Override
    public String getId() {
        return entry.getId();
    }

    @Override
    public String getRealmId() {
        return entry.getRealmId();
    }

    @Override
    public String getDestinationId() {
        return entry.getOwnerId();
    }

    @Override
    public String getSubject() {
        return entry.getEntryType();
    }

    @Override
    public String getState() {
        return state(entry.getStatus());
    }

    static String state(OutboxEntryStatus s) {
        return switch (s) {
            case PENDING, HELD -> PENDING;
            case DELIVERED -> DONE;
            case DEAD_LETTER -> DEAD;
        };
    }

    static OutboxEntryStatus status(String state) {
        return switch (state) {
            case PENDING -> OutboxEntryStatus.PENDING;
            case DONE -> OutboxEntryStatus.DELIVERED;
            case DEAD -> OutboxEntryStatus.DEAD_LETTER;
            default -> throw new IllegalArgumentException("unknown entry state " + state);
        };
    }

    @Override
    public boolean isForced() {
        return meta.isForced();
    }

    @Override
    public String getForcedMode() {
        return meta.forcedMode;
    }

    @Override
    public int getAttempts() {
        return entry.getAttempts();
    }

    @Override
    public long getNextAt() {
        return entry.getNextAttemptAt().toEpochMilli();
    }

    @Override
    public int getDeferrals() {
        return meta.deferralCount();
    }

    @Override
    public String getLastErrorClass() {
        return meta.lastErrorClass;
    }

    @Override
    public String getLastError() {
        return entry.getLastError();
    }

    @Override
    public long getCreatedAt() {
        return entry.getCreatedAt().toEpochMilli();
    }

    @Override
    public long getUpdatedAt() {
        if (meta.updatedAt != null) return meta.updatedAt;
        if (entry.getDeliveredAt() != null) return entry.getDeliveredAt().toEpochMilli();
        return getCreatedAt();
    }
}
