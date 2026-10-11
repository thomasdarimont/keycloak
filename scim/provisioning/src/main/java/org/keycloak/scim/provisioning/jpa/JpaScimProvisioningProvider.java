package org.keycloak.scim.provisioning.jpa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import org.keycloak.events.outbox.OutboxEntry;
import org.keycloak.events.outbox.OutboxEntryStatus;
import org.keycloak.events.outbox.OutboxStore;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.jpa.entities.OutboxEntryEntity;
import org.keycloak.models.jpa.entities.ScimProvisioningHealthEntity;
import org.keycloak.models.jpa.entities.ScimProvisioningLinkEntity;
import org.keycloak.scim.provisioning.delivery.LinkRecord;
import org.keycloak.scim.provisioning.model.DestinationHealthModel;
import org.keycloak.scim.provisioning.model.DestinationRef;
import org.keycloak.scim.provisioning.model.OutboxEntryModel;
import org.keycloak.scim.provisioning.model.ScimLinkModel;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;

/**
 * All reads and writes of SCIM provisioning's rows.
 *
 * <p><b>Outbox entries</b> live in Keycloak's generic outbox table, {@code OUTBOX_ENTRY}
 * ({@code org.keycloak.events.outbox}, Keycloak 26.7+), under the entry kind {@value #KIND}: the
 * destination is the owner, the subject ({@code user:<id>} or {@code group:<id>}) is the entry
 * type, the payload is empty (the drainer reads the subject at delivery time), and what that table
 * has no column for is in {@link EntryMetadata}. Keycloak's {@code OutboxDrainerTask} drains this
 * kind with our delivery handler: it claims, leases and transitions the rows.
 *
 * <p>Marking only inserts, through Keycloak's {@code OutboxStore.enqueuePending}: one row per
 * change, no update and no lock on the enqueue path. The handler settles a row whose subject has a
 * newer pending one without delivering it ({@link #supersede}) and prunes a subject's settled rows
 * when a row settles ({@link #pruneSettled}). Our statements on Keycloak's entity are the HQL in
 * {@code Kc}: it declares no named queries of ours.
 *
 * <p><b>Links and health</b> are our own tables, {@code SCIM_PROV_LINK} and {@code
 * SCIM_PROV_HEALTH}, through the named queries their entities declare. Every state change is a
 * conditional bulk statement, never a managed-entity write.
 */
public class JpaScimProvisioningProvider implements ScimProvisioningProvider {

    /** Our entry kind in {@code OUTBOX_ENTRY}. */
    public static final String KIND = ENTRY_KIND;

    /** {@code IN} lists are chunked: Oracle caps them at 1000 and large ones ruin plan caches. */
    private static final int IN_CHUNK = 500;

    private static final String EMPTY_PAYLOAD = "{}";

    /** The statements on Keycloak's {@code OutboxEntryEntity}, every one scoped to {@link #KIND}. */
    private static final class Kc {
        static final String ROWS = "SELECT e FROM OutboxEntryEntity e WHERE e.entryKind = :k";
        static final String SUBJECT_ROWS =
                ROWS + " AND e.ownerId = :d AND e.entryType = :s ORDER BY e.createdAt, e.id";

        /** A subject's current row: a pending one if there is any, else the newest. */
        static final String CURRENT_FIRST =
                " ORDER BY CASE WHEN e.status = :pending THEN 0 ELSE 1 END, e.createdAt DESC, e.id DESC";

        static final String NEWEST = ROWS + " AND e.ownerId = :d AND e.entryType = :s" + CURRENT_FIRST;
        static final String NEWEST_IN_REALM =
                ROWS + " AND e.realmId = :r AND e.entryType = :s" + CURRENT_FIRST;
        static final String DEAD =
                ROWS + " AND e.ownerId = :d AND e.status = :dead ORDER BY e.createdAt, e.id";
        static final String DEAD_OF_SUBJECT =
                ROWS + " AND e.ownerId = :d AND e.entryType = :s AND e.status = :dead";
        static final String COUNT_SUBJECTS =
                "SELECT COUNT(DISTINCT e.entryType) FROM OutboxEntryEntity e WHERE e.entryKind = :k"
                        + " AND e.ownerId = :d AND e.status = :st";
        static final String PENDING_SUBJECTS_IN =
                "SELECT e.entryType FROM OutboxEntryEntity e WHERE e.entryKind = :k AND e.ownerId = :d"
                        + " AND e.status = :pending AND e.entryType IN :subs";
        static final String SUBJECTS =
                "SELECT DISTINCT e.entryType FROM OutboxEntryEntity e WHERE e.entryKind = :k"
                        + " AND e.ownerId = :d";
        static final String DESTINATIONS =
                "SELECT DISTINCT e.realmId, e.ownerId FROM OutboxEntryEntity e WHERE e.entryKind = :k";
        static final String METADATA = "SELECT e.metadata FROM OutboxEntryEntity e WHERE e.id = :id";
        static final String SET_METADATA =
                "UPDATE OutboxEntryEntity e SET e.metadata = :m WHERE e.id = :id";
        static final String DELETE_IDS = "DELETE FROM OutboxEntryEntity e WHERE e.id IN :ids";
        static final String COMPLETE =
                "UPDATE OutboxEntryEntity e SET e.status = :done, e.deliveredAt = :now, e.attempts = 0,"
                        + " e.lastError = NULL, e.claimToken = NULL, e.claimedUntil = NULL, e.metadata = :m"
                        + " WHERE e.id = :id AND e.status = :pending";
    }

    protected final KeycloakSession session;
    protected final EntityManager em;

    public JpaScimProvisioningProvider(KeycloakSession session, EntityManager em) {
        this.session = session;
        this.em = em;
    }

    private OutboxStore outbox() {
        return new OutboxStore(session);
    }

    // ---------------------------------------------------------------- marking

    @Override
    public void mark(
            String realmId,
            String destinationId,
            String subject,
            boolean forced,
            String forcedMode,
            long now) {
        outbox()
                .enqueuePending(
                        KIND,
                        realmId,
                        destinationId,
                        null,
                        UUID.randomUUID().toString(),
                        subject,
                        EMPTY_PAYLOAD,
                        new EntryMetadata(forced, forcedMode, 0, null, null).json());
    }

    // ---------------------------------------------------------------- entries

    @Override
    public OutboxEntryModel toModel(OutboxEntry row) {
        return new OutboxEntryAdapter(row);
    }

    @Override
    public Optional<OutboxEntryModel> getEntry(String realmId, String destinationId, String subject) {
        return em
                .createQuery(Kc.NEWEST, OutboxEntryEntity.class)
                .setParameter("k", KIND)
                .setParameter("pending", OutboxEntryStatus.PENDING)
                .setParameter("d", destinationId)
                .setParameter("s", subject)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .<OutboxEntryModel>map(OutboxEntryAdapter::new)
                .findFirst();
    }

    @Override
    public List<OutboxEntryModel> getEntriesBySubject(String realmId, String subject) {
        Map<String, OutboxEntryModel> newest = new LinkedHashMap<>();
        for (OutboxEntryEntity e :
                em.createQuery(Kc.NEWEST_IN_REALM, OutboxEntryEntity.class)
                        .setParameter("k", KIND)
                        .setParameter("pending", OutboxEntryStatus.PENDING)
                        .setParameter("r", realmId)
                        .setParameter("s", subject)
                        .getResultList()) {
            newest.putIfAbsent(e.getOwnerId(), new OutboxEntryAdapter(e));
        }
        return new ArrayList<>(newest.values());
    }

    @Override
    public List<OutboxEntryModel> getDeadEntries(
            String realmId, String destinationId, int first, int max) {
        List<OutboxEntryModel> out = new ArrayList<>();
        for (OutboxEntryEntity e :
                em.createQuery(Kc.DEAD, OutboxEntryEntity.class)
                        .setParameter("k", KIND)
                        .setParameter("d", destinationId)
                        .setParameter("dead", OutboxEntryStatus.DEAD_LETTER)
                        .setFirstResult(first)
                        .setMaxResults(max)
                        .getResultList()) {
            out.add(new OutboxEntryAdapter(e));
        }
        return out;
    }

    @Override
    public long countEntries(String realmId, String destinationId, String state) {
        return em.createQuery(Kc.COUNT_SUBJECTS, Long.class)
                .setParameter("k", KIND)
                .setParameter("d", destinationId)
                .setParameter("st", OutboxEntryAdapter.status(state))
                .getSingleResult();
    }

    @Override
    public Set<String> getPendingSubjects(
            String realmId, String destinationId, Collection<String> subjects) {
        Set<String> out = new HashSet<>();
        for (List<String> chunk : chunks(subjects)) {
            out.addAll(
                    em.createQuery(Kc.PENDING_SUBJECTS_IN, String.class)
                            .setParameter("k", KIND)
                            .setParameter("d", destinationId)
                            .setParameter("pending", OutboxEntryStatus.PENDING)
                            .setParameter("subs", chunk)
                            .getResultList());
        }
        return out;
    }

    @Override
    public Set<String> getSubjects(String realmId, String destinationId) {
        return new HashSet<>(
                em.createQuery(Kc.SUBJECTS, String.class)
                        .setParameter("k", KIND)
                        .setParameter("d", destinationId)
                        .getResultList());
    }

    // ---------------------------------------------------------------- transitions

    @Override
    public boolean isSuperseded(OutboxEntryModel entry) {
        return newerPending(entry) != null;
    }

    @Override
    public boolean supersede(OutboxEntryModel entry, long now) {
        OutboxEntryEntity next = newerPending(entry);
        if (next == null) return false;
        if (entry.isForced()) {
            String mode = entry.getForcedMode();
            updateMeta(
                    next.getId(), m -> m.withForced(true, "delete".equals(m.forcedMode) ? "delete" : mode));
        }
        return true;
    }

    @Override
    public void pruneSettled(OutboxEntryModel entry) {
        List<String> drop = new ArrayList<>();
        for (OutboxEntryEntity row : subjectRows(entry)) {
            OutboxEntryStatus st = row.getStatus();
            if (!row.getId().equals(entry.getId())
                    && (st == OutboxEntryStatus.DELIVERED || st == OutboxEntryStatus.DEAD_LETTER))
                drop.add(row.getId());
        }
        for (List<String> chunk : chunks(drop)) {
            em.createQuery(Kc.DELETE_IDS).setParameter("ids", chunk).executeUpdate();
        }
    }

    @Override
    public void noteDeferral(OutboxEntryModel entry, long now) {
        updateMeta(entry.getId(), m -> m.withDeferrals(m.deferralCount() + 1).at(now));
    }

    @Override
    public void noteError(OutboxEntryModel entry, String errorClass, long now) {
        updateMeta(entry.getId(), m -> m.withError(errorClass).at(now));
    }

    @Override
    public void clearOnDelivery(String entryId, long now) {
        em.createQuery(Kc.SET_METADATA)
                .setParameter("m", new EntryMetadata(false, null, 0, null, now).json())
                .setParameter("id", entryId)
                .executeUpdate();
    }

    /** Keycloak's {@code OutboxStore.makeDueForOwner}: rows in flight are left to their tick. */
    @Override
    public int makeDue(String realmId, String destinationId) {
        return outbox().makeDueForOwner(KIND, destinationId);
    }

    @Override
    public int complete(OutboxEntryModel entry, long now) {
        return em.createQuery(Kc.COMPLETE)
                .setParameter("done", OutboxEntryStatus.DELIVERED)
                .setParameter("pending", OutboxEntryStatus.PENDING)
                .setParameter("now", now)
                .setParameter("m", new EntryMetadata(false, null, 0, null, now).json())
                .setParameter("id", entry.getId())
                .executeUpdate();
    }

    /** Keycloak's {@code OutboxStore.requeue}, which re-arms in place; our deferral count restarts. */
    @Override
    public int replay(String realmId, String destinationId, String subject, long now) {
        List<String> ids =
                em
                        .createQuery(Kc.DEAD_OF_SUBJECT, OutboxEntryEntity.class)
                        .setParameter("k", KIND)
                        .setParameter("d", destinationId)
                        .setParameter("s", subject)
                        .setParameter("dead", OutboxEntryStatus.DEAD_LETTER)
                        .getResultList()
                        .stream()
                        .map(OutboxEntryEntity::getId)
                        .toList();
        if (ids.isEmpty()) return 0;
        int n = outbox().requeue(KIND, ids);
        for (String id : ids) updateMeta(id, m -> m.withDeferrals(0).at(now));
        return n;
    }

    /** The subject's newest pending row created after {@code entry}, or null. */
    private OutboxEntryEntity newerPending(OutboxEntryModel entry) {
        List<OutboxEntryEntity> rows = subjectRows(entry);
        int at = indexOf(rows, entry.getId());
        if (at < 0) return null;
        for (int i = rows.size() - 1; i > at; i--) {
            if (rows.get(i).getStatus() == OutboxEntryStatus.PENDING) return rows.get(i);
        }
        return null;
    }

    /** A subject's rows, oldest first. */
    private List<OutboxEntryEntity> subjectRows(OutboxEntryModel entry) {
        return em.createQuery(Kc.SUBJECT_ROWS, OutboxEntryEntity.class)
                .setParameter("k", KIND)
                .setParameter("d", entry.getDestinationId())
                .setParameter("s", entry.getSubject())
                .getResultList();
    }

    private static int indexOf(List<OutboxEntryEntity> rows, String id) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).getId().equals(id)) return i;
        return -1;
    }

    /** Rewrites a row's metadata from what is stored now, not from a copy read earlier. */
    private void updateMeta(String id, UnaryOperator<EntryMetadata> change) {
        List<String> stored =
                em.createQuery(Kc.METADATA, String.class).setParameter("id", id).getResultList();
        if (stored.isEmpty()) return;
        em.createQuery(Kc.SET_METADATA)
                .setParameter("m", change.apply(EntryMetadata.parse(stored.get(0))).json())
                .setParameter("id", id)
                .executeUpdate();
    }

    // ---------------------------------------------------------------- links

    @Override
    public Optional<ScimLinkModel> getLink(String realmId, String destinationId, String subject) {
        return key(
                        em.createNamedQuery("ScimProvisioningLink.findByKey", ScimProvisioningLinkEntity.class),
                        realmId,
                        destinationId,
                        subject)
                .getResultList()
                .stream()
                .<ScimLinkModel>map(LinkAdapter::new)
                .findFirst();
    }

    @Override
    public Map<String, ScimLinkModel> getLinks(
            String realmId, String destinationId, Collection<String> subjects) {
        Map<String, ScimLinkModel> out = new HashMap<>();
        for (List<String> chunk : chunks(subjects)) {
            for (ScimProvisioningLinkEntity l :
                    destination(
                                    em.createNamedQuery("ScimProvisioningLink.findBySubjects", ScimProvisioningLinkEntity.class),
                                    realmId,
                                    destinationId)
                            .setParameter("subjects", chunk)
                            .getResultList()) {
                out.put(l.getSubject(), new LinkAdapter(l));
            }
        }
        return out;
    }

    @Override
    public List<ScimLinkModel> getLinks(String realmId, String destinationId) {
        List<ScimLinkModel> out = new ArrayList<>();
        for (ScimProvisioningLinkEntity l :
                destination(
                                em.createNamedQuery("ScimProvisioningLink.findByDestination", ScimProvisioningLinkEntity.class),
                                realmId,
                                destinationId)
                        .getResultList()) {
            out.add(new LinkAdapter(l));
        }
        return out;
    }

    /** An update of the existing row, else an insert (checked first, so no gap lock is taken). */
    @Override
    public void saveLink(String realmId, String destinationId, LinkRecord link) {
        int n =
                !linkExists(realmId, destinationId, link.subject())
                        ? 0
                        : linkValues(
                                        key(
                                                em.createNamedQuery("ScimProvisioningLink.update"),
                                                realmId,
                                                destinationId,
                                                link.subject()),
                                        link)
                                .executeUpdate();
        if (n > 0) return;
        linkValues(
                        key(
                                em.createNamedQuery("ScimProvisioningLink.insert"),
                                realmId,
                                destinationId,
                                link.subject()),
                        link)
                .executeUpdate();
    }

    @Override
    public int invalidateLink(String realmId, String destinationId, String subject) {
        return key(em.createNamedQuery("ScimProvisioningLink.invalidate"), realmId, destinationId, subject)
                .executeUpdate();
    }

    @Override
    public int reviveTombstone(String realmId, String destinationId, String subject) {
        return key(em.createNamedQuery("ScimProvisioningLink.revive"), realmId, destinationId, subject)
                .executeUpdate();
    }

    @Override
    public int removeLink(String realmId, String destinationId, String subject) {
        return key(em.createNamedQuery("ScimProvisioningLink.deleteByKey"), realmId, destinationId, subject)
                .executeUpdate();
    }

    private boolean linkExists(String realmId, String destinationId, String subject) {
        return !key(
                        em.createNamedQuery("ScimProvisioningLink.findSubjectByKey", String.class),
                        realmId,
                        destinationId,
                        subject)
                .getResultList()
                .isEmpty();
    }

    private static Query linkValues(Query q, LinkRecord l) {
        return q.setParameter("remoteId", l.remoteId())
                .setParameter("payloadHash", l.payloadHash())
                .setParameter("active", l.active())
                .setParameter("deprovisioned", l.deprovisioned())
                .setParameter("userName", l.userName())
                .setParameter("syncedAt", l.syncedAt());
    }

    // ---------------------------------------------------------------- health

    @Override
    public boolean ensureHealth(String realmId, String destinationId, long now) {
        if (getHealth(realmId, destinationId).isPresent()) return false;
        destination(em.createNamedQuery("ScimProvisioningHealth.insert"), realmId, destinationId)
                .setParameter("now", now)
                .executeUpdate();
        return true;
    }

    @Override
    public Optional<DestinationHealthModel> getHealth(String realmId, String destinationId) {
        return healthQuery(realmId, destinationId).getResultList().stream()
                .<DestinationHealthModel>map(DestinationHealthAdapter::new)
                .findFirst();
    }

    @Override
    public List<DestinationHealthModel> getAllHealth() {
        List<DestinationHealthModel> out = new ArrayList<>();
        for (ScimProvisioningHealthEntity h :
                em.createNamedQuery("ScimProvisioningHealth.findAll", ScimProvisioningHealthEntity.class).getResultList()) {
            out.add(new DestinationHealthAdapter(h));
        }
        return out;
    }

    @Override
    public void recordSuccess(String realmId, String destinationId, long now) {
        destination(em.createNamedQuery("ScimProvisioningHealth.recordSuccess"), realmId, destinationId)
                .setParameter("now", now)
                .executeUpdate();
    }

    @Override
    public int clearFailing(String realmId, String destinationId) {
        return destination(em.createNamedQuery("ScimProvisioningHealth.clearFailing"), realmId, destinationId)
                .executeUpdate();
    }

    @Override
    public void clearBackoff(String realmId, String destinationId) {
        destination(em.createNamedQuery("ScimProvisioningHealth.clearBackoff"), realmId, destinationId)
                .executeUpdate();
    }

    @Override
    public void recordTransient(
            String realmId,
            String destinationId,
            long retryAt,
            String errorClass,
            String error,
            long now) {
        destination(em.createNamedQuery("ScimProvisioningHealth.recordTransient"), realmId, destinationId)
                .setParameter("retryAt", retryAt)
                .setParameter("errorClass", errorClass)
                .setParameter("error", error)
                .executeUpdate();
    }

    @Override
    public int startFailing(String realmId, String destinationId, long now) {
        return destination(em.createNamedQuery("ScimProvisioningHealth.startFailing"), realmId, destinationId)
                .setParameter("now", now)
                .executeUpdate();
    }

    @Override
    public void recordProblem(String realmId, String destinationId, String errorClass, String error) {
        destination(em.createNamedQuery("ScimProvisioningHealth.recordProblem"), realmId, destinationId)
                .setParameter("errorClass", errorClass)
                .setParameter("error", error)
                .executeUpdate();
    }

    @Override
    public void recordReconcile(
            String realmId, String destinationId, long at, String status, String report, int mismatches) {
        destination(em.createNamedQuery("ScimProvisioningHealth.recordReconcile"), realmId, destinationId)
                .setParameter("at", at)
                .setParameter("status", status)
                .setParameter("report", report)
                .setParameter("mismatches", mismatches)
                .executeUpdate();
    }

    private TypedQuery<ScimProvisioningHealthEntity> healthQuery(String realmId, String destinationId) {
        return destination(
                em.createNamedQuery("ScimProvisioningHealth.findByDestination", ScimProvisioningHealthEntity.class),
                realmId,
                destinationId);
    }

    // ---------------------------------------------------------------- cleanup

    @Override
    public void removeDestination(String realmId, String destinationId) {
        outbox().deleteByOwner(KIND, destinationId);
        for (String q :
                List.of("ScimProvisioningLink.deleteByDestination", "ScimProvisioningHealth.deleteByDestination")) {
            destination(em.createNamedQuery(q), realmId, destinationId).executeUpdate();
        }
    }

    @Override
    public void removeRealm(String realmId) {
        outbox().deleteByRealm(KIND, realmId);
        for (String q : List.of("ScimProvisioningLink.deleteByRealm", "ScimProvisioningHealth.deleteByRealm")) {
            em.createNamedQuery(q).setParameter("realmId", realmId).executeUpdate();
        }
    }

    @Override
    public Set<DestinationRef> getKnownDestinations() {
        Set<DestinationRef> out = new HashSet<>();
        for (Object[] row :
                em.createQuery(Kc.DESTINATIONS, Object[].class).setParameter("k", KIND).getResultList()) {
            out.add(new DestinationRef((String) row[0], (String) row[1]));
        }
        for (String q :
                List.of("ScimProvisioningLink.findDestinations", "ScimProvisioningHealth.findDestinations")) {
            for (Object[] row : em.createNamedQuery(q, Object[].class).getResultList()) {
                out.add(new DestinationRef((String) row[0], (String) row[1]));
            }
        }
        return out;
    }

    @Override
    public void close() {}

    // ---------------------------------------------------------------- plumbing

    /** Binds the (realm, destination) parameters every named query here takes. */
    private static <Q extends Query> Q destination(Q q, String realmId, String destinationId) {
        q.setParameter("realmId", realmId).setParameter("destinationId", destinationId);
        return q;
    }

    /** Binds the full (realm, destination, subject) primary key. */
    private static <Q extends Query> Q key(
            Q q, String realmId, String destinationId, String subject) {
        destination(q, realmId, destinationId).setParameter("subject", subject);
        return q;
    }

    private static List<List<String>> chunks(Collection<String> all) {
        List<List<String>> out = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        for (String s : all) {
            cur.add(s);
            if (cur.size() == IN_CHUNK) {
                out.add(cur);
                cur = new ArrayList<>();
            }
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }
}
