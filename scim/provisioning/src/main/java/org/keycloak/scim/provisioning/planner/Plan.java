package org.keycloak.scim.provisioning.planner;

import java.util.List;
import java.util.Optional;

import org.keycloak.scim.provisioning.delivery.DeprovisionMode;
import org.keycloak.scim.provisioning.delivery.GroupDelivery;
import org.keycloak.scim.provisioning.delivery.LinkRecord;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** What one outbox entry asks for, computed from committed state at delivery time. */
public sealed interface Plan {

    /** The user is in scope: the destination must hold this resource. */
    record UpsertUser(
            String subject,
            ObjectNode resource,
            String hash,
            Optional<LinkRecord> link,
            List<String> groupIds)
            implements Plan {}

    /** The user is out of scope, gone, or explicitly deprovisioned. */
    record DeprovisionUser(
            String subject,
            String userName,
            Optional<LinkRecord> link,
            DeprovisionMode mode,
            boolean forced)
            implements Plan {}

    /** A scoped group with its current direct members. */
    record DeliverGroup(
            String subject,
            String displayName,
            String externalId,
            List<GroupDelivery.Member> members,
            Optional<LinkRecord> link,
            int deferrals)
            implements Plan {}

    /** Nothing to send (out of scope and never linked, deleted group, groups unsupported, …). */
    record Nothing(String reason) implements Plan {}
}
