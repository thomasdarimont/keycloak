package org.keycloak.scim.provisioning.mapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A destination's attribute mapping: a list of {@code scimPath=userField} lines.
 *
 * <p>Rules are kept in code-point order of their canonical path ({@link String#compareTo}, never a
 * {@code Collator}), so the resource built from a mapping does not depend on the order the lines
 * were written in.
 *
 * <p>In a destination, the lines are overrides ({@link #overrides}) applied on top of the user as
 * the realm's SCIM user schemas render it; {@link #defaults()} is the rendering used when the
 * realm's schemas cannot be applied. {@code active} is always sent from the user's enabled flag,
 * mapped or not.
 */
public final class Mapping {

    /** Default mapping when none is configured. */
    public static final List<String> DEFAULT_LINES =
            List.of(
                    "userName=username",
                    "externalId=id",
                    "active=enabled",
                    "displayName=displayName",
                    "name.givenName=firstName",
                    "name.familyName=lastName",
                    "emails[type eq \"work\"].value=email");

    /**
     * Multi-valued attributes of the core User schema (RFC 7643 §4.1.2). Writing one of these
     * without a value filter would produce an object where the schema requires an array.
     */
    private static final Set<String> CORE_MULTI_VALUED =
            Set.of(
                    "emails",
                    "phonenumbers",
                    "ims",
                    "photos",
                    "addresses",
                    "entitlements",
                    "roles",
                    "x509certificates");

    /** Attributes the service provider owns or that are read-only (RFC 7643 §3.1, §4.1.2). */
    private static final Set<String> RESERVED = Set.of("id", "meta", "schemas", "groups");

    private static final Mapping DEFAULT = parse(DEFAULT_LINES);

    /** No rules: the realm's rendering is sent as is. */
    public static final Mapping NONE = new Mapping(List.of());

    private final List<MappingRule> rules;

    private Mapping(List<MappingRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static Mapping defaults() {
        return DEFAULT;
    }

    /** The rules, in code-point order of their canonical path. */
    public List<MappingRule> rules() {
        return rules;
    }

    /**
     * Parses and validates. A {@code null} or empty list (blank lines ignored) gives the default
     * mapping.
     *
     * @throws MappingException listing every problem found
     */
    public static Mapping parse(List<String> lines) {
        List<String> problems = new ArrayList<>();
        Mapping m = parse(lines, problems, true);
        if (!problems.isEmpty()) throw new MappingException(problems);
        return m;
    }

    /** Every problem with these lines as a destination's overrides; empty when they are usable. */
    public static List<String> validate(List<String> lines) {
        List<String> problems = new ArrayList<>();
        if (!effective(lines).isEmpty()) parse(lines, problems, false);
        return problems;
    }

    /**
     * A destination's mapping lines as overrides of the realm's SCIM rendering: a {@code null} or
     * empty list (blank lines ignored) gives no overrides.
     *
     * @throws MappingException listing every problem found
     */
    public static Mapping overrides(List<String> lines) {
        if (effective(lines).isEmpty()) return NONE;
        List<String> problems = new ArrayList<>();
        Mapping m = parse(lines, problems, false);
        if (!problems.isEmpty()) throw new MappingException(problems);
        return m;
    }

    private static List<String> effective(List<String> lines) {
        List<String> effective = new ArrayList<>();
        if (lines != null) {
            for (String l : lines) {
                if (l != null && !l.isBlank()) effective.add(l.strip());
            }
        }
        return effective;
    }

    /** Whether there are no rules. */
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** @param full a complete mapping, which must send a {@code userName}; overrides need not */
    private static Mapping parse(List<String> lines, List<String> problems, boolean full) {
        List<String> effective = effective(lines);
        if (effective.isEmpty()) {
            if (DEFAULT != null) return DEFAULT;
            effective = DEFAULT_LINES;
        }

        List<MappingRule> rules = new ArrayList<>();
        Map<String, String> seenPaths = new HashMap<>();
        Map<String, AttributePath.Shape> shapes = new HashMap<>();
        for (String line : effective) {
            int eq = topLevelEquals(line);
            if (eq < 0) {
                problems.add("'" + line + "': expected scimPath=userField");
                continue;
            }
            AttributePath path;
            FieldRef field;
            try {
                path = AttributePath.parse(line.substring(0, eq));
            } catch (MappingException e) {
                problems.addAll(e.problems());
                continue;
            }
            try {
                field = FieldRef.parse(line.substring(eq + 1));
            } catch (MappingException e) {
                problems.add("'" + line + "': " + e.getMessage());
                continue;
            }
            String top = path.attribute().toLowerCase(Locale.ROOT);
            if (!path.isExtension() && RESERVED.contains(top)) {
                problems.add("'" + line + "': " + path.attribute() + " cannot be mapped");
                continue;
            }
            if (!path.isExtension() && top.equals("active") && !(field instanceof FieldRef.Enabled)) {
                problems.add("'" + line + "': active is always sent from the user's enabled flag");
                continue;
            }
            if (!path.isExtension() && CORE_MULTI_VALUED.contains(top) && !path.isFiltered()) {
                problems.add(
                        "'"
                                + line
                                + "': "
                                + path.attribute()
                                + " is multi-valued; use a filter, e.g. "
                                + path.attribute()
                                + "[type eq \"work\"].value");
                continue;
            }
            String previous = seenPaths.putIfAbsent(path.identity(), line);
            if (previous != null) {
                problems.add("'" + line + "': duplicate of '" + previous + "'");
                continue;
            }
            AttributePath.Shape shape = shapes.putIfAbsent(path.containerIdentity(), path.shape());
            if (shape != null && shape != path.shape()) {
                problems.add(
                        "'"
                                + line
                                + "': "
                                + path.attribute()
                                + " is used both as "
                                + describe(shape)
                                + " and as "
                                + describe(path.shape()));
                continue;
            }
            rules.add(new MappingRule(path, field));
        }
        boolean hasUserName =
                rules.stream()
                        .anyMatch(
                                r ->
                                        !r.path().isExtension()
                                                && r.path().shape() == AttributePath.Shape.SIMPLE
                                                && r.path().attribute().equalsIgnoreCase("userName"));
        if (full && problems.isEmpty() && !hasUserName) {
            problems.add("a mapping must include a userName rule (RFC 7643 §4.1.1)");
        }
        rules.sort(Comparator.comparing(r -> r.path().canonical()));
        return new Mapping(rules);
    }

    private static String describe(AttributePath.Shape s) {
        switch (s) {
            case SIMPLE:
                return "a simple attribute";
            case COMPLEX:
                return "a complex attribute";
            default:
                return "a filtered multi-valued attribute";
        }
    }

    /** Index of the first '=' outside brackets and quotes; -1 if none. */
    private static int topLevelEquals(String line) {
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '\\') i++;
                else if (c == '"') quoted = false;
            } else if (c == '"') {
                quoted = true;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == '=' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The configuration lines this mapping is equivalent to, in rule order. */
    public List<String> lines() {
        List<String> out = new ArrayList<>(rules.size());
        for (MappingRule r : rules) out.add(r.toString());
        return out;
    }

    /** Names of the user attributes read by {@code attr:} rules. */
    public List<String> attributeNames() {
        List<String> out = new ArrayList<>();
        for (MappingRule r : rules) {
            if (r.source() instanceof FieldRef.Attr) out.add(((FieldRef.Attr) r.source()).name());
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Mapping && ((Mapping) o).rules.equals(rules);
    }

    @Override
    public int hashCode() {
        return rules.hashCode();
    }

    @Override
    public String toString() {
        return lines().toString();
    }
}
