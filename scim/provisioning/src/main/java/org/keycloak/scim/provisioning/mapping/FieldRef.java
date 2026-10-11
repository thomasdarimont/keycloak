package org.keycloak.scim.provisioning.mapping;

import java.util.Locale;
import java.util.Objects;

/**
 * The right-hand side of a mapping rule: which value of the user is sent.
 *
 * <p>String values that are null or blank resolve to {@code null}, and a rule whose value is
 * {@code null} is left out of the resource.
 */
public sealed interface FieldRef {

    /** The value for this subject: a {@link String}, a {@link Boolean}, or {@code null}. */
    Object resolve(SubjectView subject);

    /** The spelling used in configuration. */
    String spelling();

    /** The Keycloak user id; the natural {@code externalId}. */
    record Id() implements FieldRef {
        @Override
        public Object resolve(SubjectView s) {
            return text(s.id());
        }

        @Override
        public String spelling() {
            return "id";
        }
    }

    record Username() implements FieldRef {
        @Override
        public Object resolve(SubjectView s) {
            return text(s.username());
        }

        @Override
        public String spelling() {
            return "username";
        }
    }

    record Email() implements FieldRef {
        @Override
        public Object resolve(SubjectView s) {
            return text(s.email());
        }

        @Override
        public String spelling() {
            return "email";
        }
    }

    record FirstName() implements FieldRef {
        @Override
        public Object resolve(SubjectView s) {
            return text(s.firstName());
        }

        @Override
        public String spelling() {
            return "firstName";
        }
    }

    record LastName() implements FieldRef {
        @Override
        public Object resolve(SubjectView s) {
            return text(s.lastName());
        }

        @Override
        public String spelling() {
            return "lastName";
        }
    }

    /** {@code "First Last"}, trimmed; {@code null} when both are empty. */
    record FullName() implements FieldRef {
        @Override
        public Object resolve(SubjectView s) {
            return fullName(s);
        }

        @Override
        public String spelling() {
            return "fullName";
        }
    }

    /** The full name, falling back to the username. */
    record DisplayName() implements FieldRef {
        @Override
        public Object resolve(SubjectView s) {
            String full = fullName(s);
            return full != null ? full : text(s.username());
        }

        @Override
        public String spelling() {
            return "displayName";
        }
    }

    /** The user's enabled flag, as a JSON boolean. */
    record Enabled() implements FieldRef {
        @Override
        public Object resolve(SubjectView s) {
            return s.enabled();
        }

        @Override
        public String spelling() {
            return "enabled";
        }
    }

    /**
     * A user attribute. When it has several values the smallest in code-point order is sent, so the
     * result does not depend on the order the host returns them in.
     */
    record Attr(String name) implements FieldRef {
        public Attr {
            Objects.requireNonNull(name);
        }

        @Override
        public Object resolve(SubjectView s) {
            String best = null;
            for (String v : s.attribute(name)) {
                String t = text(v);
                if (t != null && (best == null || t.compareTo(best) < 0)) best = t;
            }
            return best;
        }

        @Override
        public String spelling() {
            return "attr:" + name;
        }
    }

    /** The same literal string for everyone. */
    record Const(String value) implements FieldRef {
        public Const {
            Objects.requireNonNull(value);
        }

        @Override
        public Object resolve(SubjectView s) {
            return value;
        }

        @Override
        public String spelling() {
            return "const:" + value;
        }
    }

    /**
     * Parses a right-hand side. Field names are matched case-insensitively.
     *
     * @throws MappingException for an unknown field or an empty {@code attr:}/{@code const:}
     */
    static FieldRef parse(String text) {
        if (text == null || text.isBlank()) throw new MappingException("empty user field");
        String t = text.strip();
        int colon = t.indexOf(':');
        if (colon > 0) {
            String prefix = t.substring(0, colon).toLowerCase(Locale.ROOT);
            String rest = t.substring(colon + 1);
            if (prefix.equals("attr")) {
                if (rest.isBlank()) throw new MappingException("'attr:' needs an attribute name");
                return new Attr(rest.strip());
            }
            if (prefix.equals("const")) {
                if (rest.isEmpty()) throw new MappingException("'const:' needs a value");
                return new Const(rest);
            }
        }
        switch (t.toLowerCase(Locale.ROOT)) {
            case "id":
                return new Id();
            case "username":
                return new Username();
            case "email":
                return new Email();
            case "firstname":
                return new FirstName();
            case "lastname":
                return new LastName();
            case "fullname":
                return new FullName();
            case "displayname":
                return new DisplayName();
            case "enabled":
                return new Enabled();
            default:
                throw new MappingException(
                        "unknown user field '"
                                + t
                                + "' (expected id, username, email, firstName, lastName, fullName, displayName,"
                                + " enabled, attr:<name> or const:<value>)");
        }
    }

    private static String text(String v) {
        if (v == null) return null;
        String t = v.strip();
        return t.isEmpty() ? null : t;
    }

    private static String fullName(SubjectView s) {
        String first = text(s.firstName());
        String last = text(s.lastName());
        if (first == null && last == null) return null;
        if (first == null) return last;
        if (last == null) return first;
        return first + " " + last;
    }
}
