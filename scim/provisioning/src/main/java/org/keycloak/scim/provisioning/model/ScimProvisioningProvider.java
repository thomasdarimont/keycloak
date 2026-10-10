package org.keycloak.scim.provisioning.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.keycloak.provider.Provider;
import org.keycloak.scim.provisioning.delivery.LinkRecord;

/**
 * The transactional outbox: queued changes, subject &harr; account links and per-destination
 * health. Obtained with {@code session.getProvider(ScimProvisioningProvider.class)}; every method joins
 * the session's transaction.
 *
 * <p><b>Events are hints, never data.</b> An entry says only that a (destination, subject) needs
 * delivering. The drainer reads the subject at delivery time. Marking adds a row per change;
 * Keycloak's outbox drainer claims and transitions the rows, and {@link #isSuperseded} lets a row
 * whose subject has a newer pending row settle without a delivery of its own, so a change during delivery (a
 * newer row) goes out on the next run.
 */
public interface ScimProvisioningProvider extends Provider {

    /** Our entry kind in Keycloak's {@code OUTBOX_ENTRY}. */
    String ENTRY_KIND = "scim-provisioning";

    // ---------------------------------------------------------------- marking

    /** Marks a subject for delivery to a destination: one new entry row. */
    default void mark(
            String realmId, String destinationId, String subject, boolean forced, long now) {
        mark(realmId, destinationId, subject, forced, null, now);
    }

    /**
     * As {@link #mark(String, String, String, boolean, long)}; {@code forcedMode} {@code "delete"}
     * asks a forced deprovision to delete whatever the destination's mode. It is sticky until the
     * subject is delivered: {@link #supersede} carries it over to the newer row.
     */
    void mark(
            String realmId,
            String destinationId,
            String subject,
            boolean forced,
            String forcedMode,
            long now);

    // ---------------------------------------------------------------- entries

    /** The entry of a row Keycloak's outbox drainer hands to our delivery handler. */
    OutboxEntryModel toModel(org.keycloak.events.outbox.OutboxEntry row);

    /** The subject's current entry for the destination: a pending row if any, else its newest. */
    Optional<OutboxEntryModel> getEntry(String realmId, String destinationId, String subject);

    /** The subject's current entry in every destination of the realm. */
    List<OutboxEntryModel> getEntriesBySubject(String realmId, String subject);

    /** Dead entries, oldest first, one page. */
    List<OutboxEntryModel> getDeadEntries(String realmId, String destinationId, int first, int max);

    /** How many subjects have an entry in {@code state}. */
    long countEntries(String realmId, String destinationId, String state);

    /** The subjects among {@code subjects} that have a pending entry. */
    Set<String> getPendingSubjects(String realmId, String destinationId, Collection<String> subjects);

    /** Every subject a destination has an entry for, in any state. */
    Set<String> getSubjects(String realmId, String destinationId);

    // ---------------------------------------------------------------- transitions

    // Keycloak's drainer records delivered, retry, defer and dead letter; these are the rest.

    /**
     * Whether the subject has a newer pending row, which makes delivering this one unnecessary. (A
     * newer <em>delivered</em> row does not count: creation times come from each node's clock, so
     * they cannot show that the delivery read this row's change.) Reads only.
     */
    boolean isSuperseded(OutboxEntryModel entry);

    /**
     * In the transaction that settles a superseded {@code entry}: the subject's newer pending row
     * inherits a forced deprovision from it. False when there is no newer pending row.
     */
    boolean supersede(OutboxEntryModel entry, long now);

    /**
     * In the transaction that settles {@code entry} (delivered or dead-lettered): removes the
     * subject's other settled rows, older or newer; pending rows stay. A subject therefore has at
     * most one settled row, its latest outcome, and reads never depend on creation-time order.
     */
    void pruneSettled(OutboxEntryModel entry);

    /** A group was put off waiting for its members: counts the deferral on the row. */
    void noteDeferral(OutboxEntryModel entry, long now);

    /** A refusal: notes its error class on the row. */
    void noteError(OutboxEntryModel entry, String errorClass, long now);

    /**
     * In the transaction that records the row as delivered: clears what applied only until then (a
     * forced deprovision, the deferral count, the error class).
     */
    void clearOnDelivery(String entryId, long now);

    /**
     * The destination's pending rows that are waiting (deferred through a backoff, or while disabled)
     * become due now; rows a drainer tick holds are left alone. For a reconfigured destination.
     */
    int makeDue(String realmId, String destinationId);

    /** Settles a pending entry as delivered without a delivery (tests). */
    int complete(OutboxEntryModel entry, long now);

    /** The subject's dead entries back to pending with fresh attempts; how many there were. */
    int replay(String realmId, String destinationId, String subject, long now);

    // ---------------------------------------------------------------- links

    Optional<ScimLinkModel> getLink(String realmId, String destinationId, String subject);

    /** The links of the given subjects, by subject; subjects without one are absent. */
    Map<String, ScimLinkModel> getLinks(
            String realmId, String destinationId, Collection<String> subjects);

    List<ScimLinkModel> getLinks(String realmId, String destinationId);

    /**
     * Writes a link. The caller must have checked, in this transaction, that the destination still
     * exists.
     */
    void saveLink(String realmId, String destinationId, LinkRecord link);

    /** Clears the hash so the next delivery is not skipped. */
    int invalidateLink(String realmId, String destinationId, String subject);

    /** Lifts a tombstone and clears the hash, so a deprovision is sent again. */
    int reviveTombstone(String realmId, String destinationId, String subject);

    int removeLink(String realmId, String destinationId, String subject);

    // ---------------------------------------------------------------- health

    /** Creates the destination's health row if it has none; true when it did create one. */
    boolean ensureHealth(String realmId, String destinationId, long now);

    Optional<DestinationHealthModel> getHealth(String realmId, String destinationId);

    /** Every health row of every realm: what the scheduler scans. */
    List<DestinationHealthModel> getAllHealth();

    /** A successful delivery: clears the backoff. */
    void recordSuccess(String realmId, String destinationId, long now);

    /** Clears {@code failingSince}; 1 when the destination was failing (it has recovered). */
    int clearFailing(String realmId, String destinationId);

    /** Lifts a backoff (the destination was just reconfigured, e.g. a rotated token). */
    void clearBackoff(String realmId, String destinationId);

    /** A transient failure: back the destination off until {@code retryAt}. */
    void recordTransient(
            String realmId,
            String destinationId,
            long retryAt,
            String errorClass,
            String error,
            long now);

    /** Sets {@code failingSince} if unset; 1 when the destination has just started failing. */
    int startFailing(String realmId, String destinationId, long now);

    /** A terminal failure: noted on the health row, without any backoff. */
    void recordProblem(String realmId, String destinationId, String errorClass, String error);

    void recordReconcile(
            String realmId, String destinationId, long at, String status, String report, int mismatches);

    // ---------------------------------------------------------------- cleanup

    /** Removes every row of a destination (its component was removed). */
    void removeDestination(String realmId, String destinationId);

    /** Removes every row of a realm (the realm was removed). */
    void removeRealm(String realmId);

    /** Every (realm, destination) pair that has rows of ours anywhere. */
    Set<DestinationRef> getKnownDestinations();
}
