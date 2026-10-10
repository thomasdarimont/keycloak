package org.keycloak.scim.provisioning.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.scim.model.user.UserCoreModelSchema;
import org.keycloak.scim.model.user.UserEnterpriseModelSchema;
import org.keycloak.scim.model.user.UserExtensionModelSchema;
import org.keycloak.scim.provisioning.mapping.FieldRef;
import org.keycloak.scim.provisioning.mapping.Mapping;
import org.keycloak.scim.provisioning.mapping.UserResourceBuilder;
import org.keycloak.scim.resource.Scim;
import org.keycloak.scim.resource.schema.ModelSchema;
import org.keycloak.scim.resource.user.User;
import org.keycloak.util.JsonSerialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.logging.Logger;

/**
 * The SCIM user a destination should hold, as a JSON tree (hashed as is, sent as a {@link User}).
 *
 * <p>The user is rendered by the realm's SCIM user schemas, exactly as the SCIM API returns it:
 * the user profile's {@code kc.scim.schema.attribute} annotations decide the attributes. Then:
 *
 * <ul>
 *   <li>the server-managed {@code id} and {@code meta} and the read-only {@code groups} are removed
 *       (membership is delivered with the groups);
 *   <li>{@code externalId} is the Keycloak user id and {@code displayName} the first and last name,
 *       unless the realm maps them;
 *   <li>{@code active} is the user's enabled flag;
 *   <li>the destination's mapping lines are applied on top ({@link UserResourceBuilder#apply});
 *   <li>{@code schemas} lists the core User URN first, then the others in code-point order, so the
 *       hash does not depend on set order.
 * </ul>
 *
 * When the schemas cannot render the user, {@link Mapping#defaults()} is used instead.
 */
public final class UserRenderer {
    private static final Logger log = Logger.getLogger(UserRenderer.class);

    /** Membership is delivered with the groups; rendering it would need an admin's permissions. */
    private static final List<String> EXCLUDED = List.of("groups");

    private final List<ModelSchema<UserModel, User>> schemas;

    public UserRenderer(KeycloakSession session) {
        this.schemas = List.of(
                new UserCoreModelSchema(session),
                new UserEnterpriseModelSchema(session),
                new UserExtensionModelSchema(session));
    }

    public ObjectNode render(UserModel user, Mapping overrides) {
        KeycloakSubjectView view = new KeycloakSubjectView(user);
        ObjectNode root;
        try {
            root = fromSchemas(user);
        } catch (RuntimeException e) {
            log.debugf(e, "the realm's SCIM schemas could not render user %s; using the default mapping", user.getId());
            root = UserResourceBuilder.build(view, Mapping.defaults(), user.isEnabled());
        }
        UserResourceBuilder.apply(root, view, overrides);
        root.put("active", user.isEnabled());
        sortSchemas(root);
        return root;
    }

    /** The {@code userName} the destination would get (to find an unlinked account). */
    public String userName(UserModel user, Mapping overrides) {
        JsonNode n = render(user, overrides).get("userName");
        return n != null && n.isTextual() ? n.asText() : null;
    }

    private ObjectNode fromSchemas(UserModel user) {
        User resource = new User();
        for (ModelSchema<UserModel, User> schema : schemas) {
            schema.populate(resource, user, null, EXCLUDED);
        }
        ObjectNode root = JsonSerialization.mapper.valueToTree(resource);
        root.remove("id");
        root.remove("meta");
        root.remove("groups");
        if (!root.hasNonNull("externalId")) {
            root.put("externalId", user.getId());
        }
        if (!root.hasNonNull("displayName")) {
            Object displayName = new FieldRef.DisplayName().resolve(new KeycloakSubjectView(user));
            if (displayName != null) {
                root.put("displayName", displayName.toString());
            }
        }
        return root;
    }

    private static void sortSchemas(ObjectNode root) {
        JsonNode existing = root.get("schemas");
        TreeSet<String> others = new TreeSet<>();
        if (existing instanceof ArrayNode a) {
            for (JsonNode s : a) {
                others.add(s.asText());
            }
        }
        others.remove(Scim.USER_CORE_SCHEMA);
        List<String> ordered = new ArrayList<>();
        ordered.add(Scim.USER_CORE_SCHEMA);
        ordered.addAll(others);
        ArrayNode schemas = root.putArray("schemas");
        ordered.forEach(schemas::add);
    }
}
