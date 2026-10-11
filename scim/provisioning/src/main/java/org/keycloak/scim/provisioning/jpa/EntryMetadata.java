package org.keycloak.scim.provisioning.jpa;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@code METADATA} column of our rows in Keycloak's {@code OUTBOX_ENTRY}: what an entry needs
 * beyond that table's columns. Absent fields mean false, zero or none; all defaults is no metadata
 * at all.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class EntryMetadata {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A forced deprovision: take the account away even if the person is still in scope. */
    public Boolean forced;

    /** For a forced deprovision: {@code delete} overrides the destination's mode. */
    public String forcedMode;

    /** How often a group was put off waiting for its members. */
    public Integer deferrals;

    public String lastErrorClass;

    /** Epoch milliseconds of the last transition. */
    public Long updatedAt;

    public EntryMetadata() {}

    public EntryMetadata(
            boolean forced, String forcedMode, int deferrals, String lastErrorClass, Long updatedAt) {
        this.forced = forced ? Boolean.TRUE : null;
        this.forcedMode = forced ? forcedMode : null;
        this.deferrals = deferrals > 0 ? deferrals : null;
        this.lastErrorClass = lastErrorClass;
        this.updatedAt = updatedAt;
    }

    public static EntryMetadata parse(String json) {
        if (json == null || json.isBlank()) return new EntryMetadata();
        try {
            return JSON.readValue(json, EntryMetadata.class);
        } catch (IOException e) {
            return new EntryMetadata();
        }
    }

    boolean isForced() {
        return Boolean.TRUE.equals(forced);
    }

    int deferralCount() {
        return deferrals == null ? 0 : deferrals;
    }

    EntryMetadata withForced(boolean f, String mode) {
        return new EntryMetadata(f, mode, deferralCount(), lastErrorClass, updatedAt);
    }

    EntryMetadata withDeferrals(int n) {
        return new EntryMetadata(isForced(), forcedMode, n, lastErrorClass, updatedAt);
    }

    EntryMetadata withError(String errorClass) {
        return new EntryMetadata(isForced(), forcedMode, deferralCount(), errorClass, updatedAt);
    }

    EntryMetadata at(long now) {
        return new EntryMetadata(isForced(), forcedMode, deferralCount(), lastErrorClass, now);
    }

    /** JSON, or null when every field is at its default. */
    public String json() {
        if (forced == null
                && forcedMode == null
                && deferrals == null
                && lastErrorClass == null
                && updatedAt == null) return null;
        try {
            return JSON.writeValueAsString(this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
