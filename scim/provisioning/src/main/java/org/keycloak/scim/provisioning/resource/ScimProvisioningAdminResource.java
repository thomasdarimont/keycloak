package org.keycloak.scim.provisioning.resource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.common.constants.KeycloakOpenAPI;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.Constants;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.component.Destinations;
import org.keycloak.scim.provisioning.delivery.LinkRecord;
import org.keycloak.scim.provisioning.listener.Enqueuer;
import org.keycloak.scim.provisioning.listener.Wakeup;
import org.keycloak.scim.provisioning.mapping.MappingException;
import org.keycloak.scim.provisioning.model.DestinationHealthModel;
import org.keycloak.scim.provisioning.model.OutboxEntryModel;
import org.keycloak.scim.provisioning.model.ScimLinkModel;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;
import org.keycloak.scim.provisioning.planner.DesiredState;
import org.keycloak.scim.provisioning.planner.Plan;
import org.keycloak.scim.provisioning.planner.Subjects;
import org.keycloak.scim.provisioning.reconcile.Reconciler;
import org.keycloak.scim.provisioning.reconcile.Resync;
import org.keycloak.scim.provisioning.representation.DeadLetterRepresentation;
import org.keycloak.scim.provisioning.representation.DeliveryRepresentation;
import org.keycloak.scim.provisioning.representation.DestinationActionRepresentation;
import org.keycloak.scim.provisioning.representation.DestinationStatusRepresentation;
import org.keycloak.scim.provisioning.representation.PreviewRepresentation;
import org.keycloak.scim.provisioning.representation.ReconcileReportRepresentation;
import org.keycloak.services.ErrorResponse;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * {@code /admin/realms/{realm}/scim-provisioning}: the operator's verbs. Reading needs {@code
 * view-users}; anything that changes delivery needs {@code manage-users} and emits an admin event
 * ({@code COMPONENT}/{@code ACTION} on {@code components/{destination}/scim-provisioning/<action>/…}; a
 * custom resource type would break Keycloak's admin-event queries). No endpoint calls a
 * destination.
 *
 * <p>{@code destination} parameters take a component id or a destination name.
 */
@Extension(name = KeycloakOpenAPI.Profiles.ADMIN, value = "")
@Tag(name = KeycloakOpenAPI.Admin.Tags.SCIM_PROVISIONING)
public class ScimProvisioningAdminResource {
    private static final Logger log = Logger.getLogger(ScimProvisioningAdminResource.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    protected final KeycloakSession session;
    protected final RealmModel realm;
    protected final AdminPermissionEvaluator auth;
    protected final AdminEventBuilder adminEvent;

    public ScimProvisioningAdminResource(
            KeycloakSession session,
            RealmModel realm,
            AdminPermissionEvaluator auth,
            AdminEventBuilder adminEvent) {
        this.session = session;
        this.realm = realm;
        this.auth = auth;
        this.adminEvent = adminEvent.resource(ResourceType.COMPONENT);
    }

    protected ScimProvisioningProvider outbox() {
        return session.getProvider(ScimProvisioningProvider.class);
    }

    // ------------------------------------------------------------------ status

    /** Per destination: state, backlog, dead count, last success, last reconcile, last problem. */
    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Delivery status of every destination of the realm")
    public Stream<DestinationStatusRepresentation> status() {
        auth.users().requireView();
        long now = System.currentTimeMillis();
        return Destinations.of(realm).stream().map(d -> status(d, now));
    }

    protected DestinationStatusRepresentation status(Destination d, long now) {
        ScimProvisioningProvider outbox = outbox();
        DestinationHealthModel h = outbox.getHealth(realm.getId(), d.id()).orElse(null);
        long backlog = outbox.countEntries(realm.getId(), d.id(), OutboxEntryModel.PENDING);
        long dead = outbox.countEntries(realm.getId(), d.id(), OutboxEntryModel.DEAD);
        String state;
        if (!d.enabled()) state = "off";
        else if (h != null && (h.getFailingSince() != null || h.isBackedOff(now))) state = "failing";
        else if (backlog > 0) state = "catching_up";
        else state = "healthy";
        DestinationStatusRepresentation rep =
                new DestinationStatusRepresentation(d.name(), d.id(), state, backlog, dead);
        if (h != null) {
            rep.setLastSuccessAt(h.getLastSuccessAt());
            rep.setLastReconcileAt(h.getLastReconcileAt());
            rep.setLastReconcileStatus(h.getLastReconcileStatus());
            rep.setMismatches(h.getMismatches());
            rep.setLastProblem(h.getLastError());
            rep.setLastProblemClass(h.getLastErrorClass());
            rep.setFailingSince(h.getFailingSince());
            rep.setRetryAt(h.getRetryAt());
            rep.setConsecutiveFailures(h.getConsecutiveFailures());
        }
        return rep;
    }

    // ------------------------------------------------------------------ dead letters

    /** Changes the destination refused on every attempt, oldest first. Kept until replayed or superseded. */
    @GET
    @Path("dead-letters")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Changes a destination refused on every attempt")
    public Stream<DeadLetterRepresentation> deadLetters(
            @QueryParam("destination") String destination,
            @QueryParam("first") Integer first,
            @QueryParam("max") Integer max) {
        auth.users().requireView();
        Destination d = destination(destination);
        int from = first == null || first < 0 ? 0 : first;
        int size =
                max != null && max > 0 && max <= Constants.DEFAULT_MAX_RESULTS
                        ? max
                        : Constants.DEFAULT_MAX_RESULTS;
        List<OutboxEntryModel> dead = outbox().getDeadEntries(realm.getId(), d.id(), from, size);
        return dead.stream()
                .map(
                        e ->
                                new DeadLetterRepresentation(
                                        e.getSubject(),
                                        subjectName(e.getSubject()),
                                        e.isForced(),
                                        e.getAttempts(),
                                        e.getLastErrorClass(),
                                        e.getLastError(),
                                        e.getUpdatedAt()));
    }

    /**
     * Returns a dead entry to pending with fresh attempts. It needs the destination: a subject can be
     * dead in several.
     */
    @POST
    @Path("replay")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Return a dead entry to pending with fresh attempts")
    public DestinationActionRepresentation replay(
            @QueryParam("destination") String destination, @QueryParam("subject") String subject) {
        auth.users().requireManage();
        Destination d = destination(destination);
        if (subject == null || subject.isBlank()) throw new BadRequestException("subject is required");
        int n = outbox().replay(realm.getId(), d.id(), subject, System.currentTimeMillis());
        if (n == 0) throw new NotFoundException("no dead letter for " + subject + " in " + d.name());
        Wakeup.afterCommit(session, realm.getId());
        adminEvent
                .operation(OperationType.ACTION)
                .resourcePath("components", d.id(), "scim-provisioning", "replay", subject)
                .representation(Map.of("destination", d.id(), "subject", subject))
                .success();
        return new DestinationActionRepresentation(d.name(), null, subject, OutboxEntryModel.PENDING, null);
    }

    // ------------------------------------------------------------------ offboarding

    /**
     * Forced deprovision across every destination of the realm: the account is taken away even if
     * the person is still in scope, and looked up by {@code userName} even without a link. One-shot:
     * the next change to a person still in scope provisions them again; disable the user in Keycloak
     * to make it stick. Poll {@code deliveries} for the outcome.
     */
    @POST
    @Path("deprovision")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Take a user's accounts away in every destination, now")
    public List<DestinationActionRepresentation> deprovision(@QueryParam("user") String userId) {
        auth.users().requireManage();
        if (userId == null || userId.isBlank()) throw new BadRequestException("user is required");
        UserModel user = session.users().getUserById(realm, userId);
        if (user != null) auth.users().requireManage(user);
        String subject = Subjects.user(userId);
        return Destinations.of(realm).stream()
                .map(
                        d -> {
                            Enqueuer.now(session, new Enqueuer.Mark(realm.getId(), d.id(), subject, true));
                            adminEvent
                                    .operation(OperationType.ACTION)
                                    .resourcePath("components", d.id(), "scim-provisioning", "deprovision", subject)
                                    .representation(Map.of("destination", d.id(), "subject", subject))
                                    .success();
                            return new DestinationActionRepresentation(d.name(), d.id(), null, d.enabled() ? OutboxEntryModel.PENDING : "off", null);
                        })
                .collect(Collectors.toList());
    }

    /** Per-destination delivery state of one user: the offboarding poll. */
    @GET
    @Path("deliveries")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Per-destination delivery state of one user")
    public Stream<DeliveryRepresentation> deliveries(@QueryParam("user") String userId) {
        auth.users().requireView();
        if (userId == null || userId.isBlank()) throw new BadRequestException("user is required");
        UserModel user = session.users().getUserById(realm, userId);
        if (user != null) auth.users().requireView(user);
        String subject = Subjects.user(userId);
        ScimProvisioningProvider outbox = outbox();
        return Destinations.of(realm).stream()
                .map(
                        d -> {
                            OutboxEntryModel e = outbox.getEntry(realm.getId(), d.id(), subject).orElse(null);
                            ScimLinkModel link = outbox.getLink(realm.getId(), d.id(), subject).orElse(null);
                            DeliveryRepresentation rep =
                                    new DeliveryRepresentation(d.name(), d.id(), e == null ? "none" : e.getState(), e == null || e.isDone());
                            if (e != null) {
                                rep.setForced(e.isForced());
                                rep.setForcedMode(e.getForcedMode());
                                rep.setAttempts(e.getAttempts());
                                rep.setLastErrorClass(e.getLastErrorClass());
                                rep.setLastError(e.getLastError());
                                rep.setUpdatedAt(e.getUpdatedAt());
                            }
                            if (link != null) {
                                rep.setAccount(
                                        new DeliveryRepresentation.AccountRepresentation(link.getRemoteId(), link.isActive(), link.isDeprovisioned(), link.getUserName(), link.getSyncedAt()));
                            }
                            return rep;
                        });
    }

    // ------------------------------------------------------------------ resync and reconcile

    /** Marks every subject of the destination; unchanged subjects cost no request. */
    @POST
    @Path("resync")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Resynchronize a destination")
    public Response resync(@QueryParam("destination") String destination) {
        auth.users().requireManage();
        Destination d = destination(destination);
        Resync.afterCommit(session, realm.getId(), d.id());
        adminEvent
                .operation(OperationType.ACTION)
                .resourcePath("components", d.id(), "scim-provisioning", "resync")
                .success();
        return Response.accepted(queued(d)).type(MediaType.APPLICATION_JSON).build();
    }

    /** Starts a reconcile now (it also runs every {@code reconcileHours}). */
    @POST
    @Path("reconcile")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reconcile a destination now")
    public Response reconcile(@QueryParam("destination") String destination) {
        auth.users().requireManage();
        Destination d = destination(destination);
        String realmId = realm.getId();
        KeycloakSessionFactory factory = session.getKeycloakSessionFactory();
        session
                .getTransactionManager()
                .enlistAfterCompletion(
                        new AbstractKeycloakTransaction() {
                            @Override
                            protected void commitImpl() {
                                Reconciler.request(factory, realmId, d.id());
                            }

                            @Override
                            protected void rollbackImpl() {}
                        });
        adminEvent
                .operation(OperationType.ACTION)
                .resourcePath("components", d.id(), "scim-provisioning", "reconcile")
                .success();
        return Response.accepted(queued(d)).type(MediaType.APPLICATION_JSON).build();
    }

    /** The last reconcile's report (kept on the health row; there is no run history). */
    @GET
    @Path("reconcile")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "The last reconcile report of a destination")
    public ReconcileReportRepresentation lastReconcile(
            @QueryParam("destination") String destination) {
        auth.users().requireView();
        Destination d = destination(destination);
        DestinationHealthModel h = outbox().getHealth(realm.getId(), d.id()).orElse(null);
        ReconcileReportRepresentation rep =
                new ReconcileReportRepresentation(d.name(), d.id());
        if (h == null || h.getLastReconcileAt() == null) {
            rep.setStatus("never");
            return rep;
        }
        rep.setStatus(h.getLastReconcileStatus());
        rep.setAt(h.getLastReconcileAt());
        rep.setMismatches(h.getMismatches());
        try {
            if (h.getLastReconcileReport() != null)
                rep.setReport(JSON.readTree(h.getLastReconcileReport()));
        } catch (Exception e) {
            log.debugf("unreadable reconcile report for %s: %s", d.id(), e.toString());
        }
        return rep;
    }

    // ------------------------------------------------------------------ preview

    /**
     * What would be sent for a user right now: the plan and, for an upsert, the SCIM resource.
     * Nothing is sent.
     */
    @GET
    @Path("preview")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "What would be sent for a user now, without sending it")
    public PreviewRepresentation preview(
            @QueryParam("destination") String destination, @QueryParam("user") String userId) {
        auth.users().requireView();
        Destination d = destination(destination);
        UserModel user = userId == null ? null : session.users().getUserById(realm, userId);
        if (user == null) throw new NotFoundException("no such user");
        auth.users().requireView(user);
        String subject = Subjects.user(user.getId());
        PreviewRepresentation rep =
                new PreviewRepresentation(d.name(), subject);
        Plan plan;
        try {
            plan = new DesiredState(session, realm, d, d.mapping()).planUser(subject, false);
        } catch (MappingException e) {
            throw ErrorResponse.error(
                    "the destination's mapping is invalid: " + e.getMessage(), Response.Status.CONFLICT);
        }
        if (plan instanceof Plan.UpsertUser u) {
            boolean unchanged =
                    u.link().map(l -> l.isLive() && u.hash().equals(l.payloadHash())).orElse(false);
            rep.setAction(unchanged ? "none" : "upsert");
            rep.setResource(u.resource());
            rep.setHash(u.hash());
            return rep;
        }
        if (plan instanceof Plan.DeprovisionUser p) {
            rep.setAction(p.link().map(LinkRecord::isLive).orElse(false) ? "deprovision" : "none");
            return rep;
        }
        rep.setAction("none");
        rep.setReason(((Plan.Nothing) plan).reason());
        return rep;
    }

    // ------------------------------------------------------------------ plumbing

    private static DestinationActionRepresentation queued(Destination d) {
        return new DestinationActionRepresentation(d.name(), d.id(), null, null, "queued");
    }

    protected Destination destination(String ref) {
        if (ref == null || ref.isBlank()) throw new BadRequestException("destination is required");
        Destination d = Destinations.find(realm, ref);
        if (d != null) return d;
        for (Destination x : Destinations.of(realm)) if (ref.equals(x.name())) return x;
        throw new NotFoundException("no such destination: " + ref);
    }

    /** A username or group name for a subject, when it still exists. */
    protected String subjectName(String subject) {
        if (Subjects.isUser(subject)) {
            UserModel u = session.users().getUserById(realm, Subjects.id(subject));
            return u == null ? null : u.getUsername();
        }
        if (Subjects.isGroup(subject)) {
            GroupModel g = session.groups().getGroupById(realm, Subjects.id(subject));
            return g == null ? null : g.getName();
        }
        return null;
    }
}
