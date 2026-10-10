package org.keycloak.scim.provisioning.mapping;

import java.util.LinkedHashSet;
import java.util.Set;

import org.keycloak.scim.resource.Scim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds a SCIM {@code User} resource (RFC 7643 §4.1) as a JSON tree from a subject and a mapping.
 *
 * <ul>
 *   <li>a rule whose value is {@code null} is left out;
 *   <li>{@code active} is always present and is the {@code active} argument;
 *   <li>an extension attribute goes into a top-level object keyed by its schema URN (RFC 7643
 *       §3.3), for the 2.0 and the 1.x URN forms alike;
 *   <li>{@code schemas} lists the core User URN, then each extension URN that received a value, in
 *       rule order;
 *   <li>a filtered multi-valued rule writes into the array element whose filter sub-attribute
 *       equals the filter value, creating it on first use; the first element created in an array
 *       gets {@code "primary": true} (RFC 7643 §2.4).
 * </ul>
 *
 * <p>The tree's key order is not significant: hashing goes through {@code CanonicalJson}.
 */
public final class UserResourceBuilder {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private UserResourceBuilder() {}

    public static ObjectNode build(SubjectView subject, Mapping mapping, boolean active) {
        ObjectNode root = JSON.objectNode();
        ArrayNode schemas = root.putArray("schemas");
        Set<String> extensions = write(root, subject, mapping);
        root.put("active", active);
        schemas.add(Scim.USER_CORE_SCHEMA);
        for (String e : extensions) schemas.add(e);
        return root;
    }

    /**
     * Writes the mapping's values into an already rendered user, replacing what the rules address
     * and leaving everything else; an extension URN that received a value is added to {@code
     * schemas} when missing. A rule whose value is {@code null} changes nothing.
     */
    public static void apply(ObjectNode root, SubjectView subject, Mapping overrides) {
        if (overrides.isEmpty()) return;
        Set<String> extensions = write(root, subject, overrides);
        JsonNode existing = root.get("schemas");
        ArrayNode schemas = existing instanceof ArrayNode a ? a : root.putArray("schemas");
        for (String e : extensions) {
            boolean present = false;
            for (JsonNode s : schemas) present |= e.equals(s.asText());
            if (!present) schemas.add(e);
        }
    }

    /** The extension URNs that received a value, in rule order. */
    private static Set<String> write(ObjectNode root, SubjectView subject, Mapping mapping) {
        Set<String> extensions = new LinkedHashSet<>();
        for (MappingRule rule : mapping.rules()) {
            Object value = rule.source().resolve(subject);
            if (value == null) continue;
            AttributePath path = rule.path();
            // active is written once, below, from the argument.
            if (!path.isExtension() && path.attribute().equalsIgnoreCase("active")) continue;
            ObjectNode container = root;
            if (path.isExtension()) {
                container = objectChild(root, path.schemaUrn());
                extensions.add(path.schemaUrn());
            }
            switch (path.shape()) {
                case SIMPLE:
                    put(container, path.attribute(), value);
                    break;
                case COMPLEX:
                    put(objectChild(container, path.attribute()), path.subAttribute(), value);
                    break;
                case MULTI_VALUED:
                    put(element(container, path), path.subAttribute(), value);
                    break;
                default:
                    throw new IllegalStateException();
            }
        }
        return extensions;
    }

    private static ObjectNode element(ObjectNode container, AttributePath path) {
        JsonNode existing = container.get(path.attribute());
        ArrayNode array =
                existing instanceof ArrayNode ? (ArrayNode) existing : container.putArray(path.attribute());
        for (JsonNode e : array) {
            JsonNode f = e.get(path.filterAttribute());
            if (f != null && f.isTextual() && f.textValue().equals(path.filterValue())) {
                return (ObjectNode) e;
            }
        }
        ObjectNode e = JSON.objectNode();
        e.put(path.filterAttribute(), path.filterValue());
        if (array.isEmpty()) e.put("primary", true);
        array.add(e);
        return e;
    }

    private static ObjectNode objectChild(ObjectNode parent, String name) {
        JsonNode existing = parent.get(name);
        if (existing instanceof ObjectNode) return (ObjectNode) existing;
        return parent.putObject(name);
    }

    private static void put(ObjectNode node, String name, Object value) {
        if (value instanceof Boolean) node.put(name, (Boolean) value);
        else node.put(name, value.toString());
    }
}
