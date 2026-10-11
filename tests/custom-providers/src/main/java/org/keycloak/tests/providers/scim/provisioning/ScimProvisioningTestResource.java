package org.keycloak.tests.providers.scim.provisioning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.scim.provisioning.jpa.JpaScimProvisioningProvider;
import org.keycloak.scim.provisioning.model.DestinationHealthModel;
import org.keycloak.scim.provisioning.model.OutboxEntryModel;
import org.keycloak.scim.provisioning.model.ScimLinkModel;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;

/** TEST ONLY. See {@link ScimProvisioningTestResourceProviderFactory}. */
public class ScimProvisioningTestResource {
    private final KeycloakSession session;

    public ScimProvisioningTestResource(KeycloakSession session) {
        this.session = session;
    }

    private RealmModel realm() {
        return session.getContext().getRealm();
    }

    /**
     * Creates a user and adds them to a group — a real model change that fires the group-join model
     * event, so the listener writes its marker — and then fails the request: the transaction is
     * marked rollback-only and the call answers 409 with the id the user would have had.
     */
    @POST
    @Path("create-and-fail")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createAndFail(
            @QueryParam("username") String username, @QueryParam("group") String groupId) {
        String id = createAndJoin(username, groupId);
        session.getTransactionManager().setRollbackOnly();
        return Response.status(409).entity(Map.of("userId", id)).build();
    }

    /** The same, committed. */
    @POST
    @Path("create-and-commit")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createAndCommit(
            @QueryParam("username") String username, @QueryParam("group") String groupId) {
        String id = createAndJoin(username, groupId);
        return Response.ok(Map.of("userId", id)).build();
    }

    private String createAndJoin(String username, String groupId) {
        RealmModel realm = realm();
        UserModel u = session.users().addUser(realm, username);
        u.setEnabled(true);
        u.setEmail(username + "@example.com");
        GroupModel g = session.groups().getGroupById(realm, groupId);
        u.joinGroup(g);
        return u.getId();
    }

    /** The entry, link and health rows for one subject of one destination. */
    @GET
    @Path("rows")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> rows(
            @QueryParam("destination") String destinationId, @QueryParam("subject") String subject) {
        String realmId = realm().getId();
        ScimProvisioningProvider outbox = session.getProvider(ScimProvisioningProvider.class);
        Map<String, Object> out = new LinkedHashMap<>();
        Optional<OutboxEntryModel> e = outbox.getEntry(realmId, destinationId, subject);
        out.put("entry", e.map(ScimProvisioningTestResource::entry).orElse(null));
        out.put(
                "entryRows",
                session
                        .getProvider(JpaConnectionProvider.class)
                        .getEntityManager()
                        .createQuery(
                                "SELECT COUNT(e) FROM OutboxEntryEntity e WHERE e.entryKind = :k"
                                        + " AND e.ownerId = :d AND e.entryType = :s",
                                Long.class)
                        .setParameter("k", JpaScimProvisioningProvider.KIND)
                        .setParameter("d", destinationId)
                        .setParameter("s", subject)
                        .getSingleResult());
        Optional<ScimLinkModel> l = outbox.getLink(realmId, destinationId, subject);
        out.put("link", l.map(ScimProvisioningTestResource::link).orElse(null));
        out.put(
                "health", outbox.getHealth(realmId, destinationId).map(ScimProvisioningTestResource::health).orElse(null));
        return out;
    }

    /** Every entry of a subject across destinations (to prove "no row"). */
    @GET
    @Path("entries")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Map<String, Object>> entries(@QueryParam("subject") String subject) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (OutboxEntryModel e :
                session.getProvider(ScimProvisioningProvider.class).getEntriesBySubject(realm().getId(), subject))
            out.add(entry(e));
        return out;
    }

    /** Proves the entities are registered and the tables and history table exist. */
    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> schema() {
        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
                "OutboxEntry",
                em.createQuery(
                                "SELECT COUNT(e) FROM OutboxEntryEntity e WHERE e.entryKind = :k", Long.class)
                        .setParameter("k", JpaScimProvisioningProvider.KIND)
                        .getSingleResult());
        for (String entity : List.of("ScimProvisioningLink", "ScimProvisioningHealth")) {
            out.put(
                    entity,
                    em.createQuery("SELECT COUNT(e) FROM " + entity + " e", Long.class).getSingleResult());
        }
        Object changeSet = em.createNativeQuery(
                        "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = '27.0.0-scim-provisioning'")
                .getSingleResult();
        out.put("changeSet", ((Number) changeSet).longValue());
        return out;
    }

    /** Rows of any realm id across the three tables (to prove realm cleanup). */
    @GET
    @Path("count")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> count(
            @QueryParam("realmId") String realmId, @QueryParam("destination") String destinationId) {
        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        Map<String, Object> out = new LinkedHashMap<>();
        String ours =
                "SELECT COUNT(e) FROM OutboxEntryEntity e WHERE e.entryKind = :k AND "
                        + (realmId != null ? "e.realmId = :x" : "e.ownerId = :x");
        out.put(
                "OutboxEntry",
                em.createQuery(ours, Long.class)
                        .setParameter("k", JpaScimProvisioningProvider.KIND)
                        .setParameter("x", realmId != null ? realmId : destinationId)
                        .getSingleResult());
        for (String entity : List.of("ScimProvisioningLink", "ScimProvisioningHealth")) {
            String q =
                    "SELECT COUNT(e) FROM "
                            + entity
                            + " e WHERE "
                            + (realmId != null ? "e.realmId = :x" : "e.destinationId = :x");
            out.put(
                    entity,
                    em.createQuery(q, Long.class)
                            .setParameter("x", realmId != null ? realmId : destinationId)
                            .getSingleResult());
        }
        return out;
    }

    /**
     * Forgets a link and settles its entry, as if the database had been restored from before the
     * delivery: the account exists in the app, Keycloak no longer knows it.
     */
    @POST
    @Path("forget-link")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> forgetLink(
            @QueryParam("destination") String destinationId, @QueryParam("subject") String subject) {
        String realmId = realm().getId();
        ScimProvisioningProvider outbox = session.getProvider(ScimProvisioningProvider.class);
        outbox.removeLink(realmId, destinationId, subject);
        outbox
                .getEntry(realmId, destinationId, subject)
                .ifPresent(e -> outbox.complete(e, System.currentTimeMillis()));
        return Map.of("ok", true);
    }

    /**
     * Writes a row of another outbox consumer (for instance SSF's {@code ssf-push}) into Keycloak's
     * {@code OUTBOX_ENTRY}, to prove that we never touch rows of another kind.
     */
    @POST
    @Path("foreign-row")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> foreignRow(
            @QueryParam("kind") String kind, @QueryParam("owner") String owner) {
        String id =
                new org.keycloak.events.outbox.OutboxStore(session)
                        .enqueuePending(
                                kind,
                                realm().getId(),
                                owner,
                                null,
                                java.util.UUID.randomUUID().toString(),
                                "urn:example:test-event",
                                "{}",
                                null);
        return Map.of("id", id);
    }

    /** The rows of another outbox consumer: id, owner and status. */
    @GET
    @Path("foreign-rows")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Map<String, Object>> foreignRows(@QueryParam("kind") String kind) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (org.keycloak.models.jpa.entities.OutboxEntryEntity e :
                session
                        .getProvider(JpaConnectionProvider.class)
                        .getEntityManager()
                        .createQuery(
                                "SELECT e FROM OutboxEntryEntity e WHERE e.entryKind = :k",
                                org.keycloak.models.jpa.entities.OutboxEntryEntity.class)
                        .setParameter("k", kind)
                        .getResultList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("owner", e.getOwnerId());
            m.put("status", e.getStatus().name());
            m.put("lastError", e.getLastError());
            out.add(m);
        }
        return out;
    }

    /** Writes a row for a destination that does not exist (to prove the sweeper removes orphans). */
    @POST
    @Path("orphan")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> orphan(@QueryParam("destination") String destinationId) {
        session
                .getProvider(ScimProvisioningProvider.class)
                .mark(realm().getId(), destinationId, "user:orphan", false, System.currentTimeMillis());
        return Map.of("ok", true);
    }

    private static Map<String, Object> entry(OutboxEntryModel e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("destinationId", e.getDestinationId());
        m.put("subject", e.getSubject());
        m.put("state", e.getState());
        m.put("forced", e.isForced());
        m.put("attempts", e.getAttempts());
        m.put("nextAt", e.getNextAt());
        m.put("deferrals", e.getDeferrals());
        m.put("lastErrorClass", e.getLastErrorClass());
        m.put("lastError", e.getLastError());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    private static Map<String, Object> link(ScimLinkModel l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("remoteId", l.getRemoteId());
        m.put("payloadHash", l.getPayloadHash());
        m.put("active", l.isActive());
        m.put("deprovisioned", l.isDeprovisioned());
        m.put("userName", l.getUserName());
        return m;
    }

    private static Map<String, Object> health(DestinationHealthModel h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lastSuccessAt", h.getLastSuccessAt());
        m.put("lastErrorClass", h.getLastErrorClass());
        m.put("lastError", h.getLastError());
        m.put("failingSince", h.getFailingSince());
        m.put("retryAt", h.getRetryAt());
        m.put("consecutiveFailures", h.getConsecutiveFailures());
        m.put("lastReconcileAt", h.getLastReconcileAt());
        m.put("lastReconcileStatus", h.getLastReconcileStatus());
        m.put("mismatches", h.getMismatches());
        return m;
    }
}
