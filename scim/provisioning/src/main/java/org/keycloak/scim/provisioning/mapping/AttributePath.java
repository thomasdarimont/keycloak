package org.keycloak.scim.provisioning.mapping;

import java.util.Locale;

import org.keycloak.scim.resource.Scim;

/**
 * The left-hand side of a mapping rule: where in a SCIM resource a value goes.
 *
 * <p>Five written forms are accepted:
 *
 * <ol>
 *   <li>simple: {@code userName} (RFC 7643 §4.1.1);
 *   <li>sub-attribute of a complex attribute: {@code name.givenName} (RFC 7643 §2.4);
 *   <li>filtered multi-valued: {@code emails[type eq "work"].value} — the value filter is the
 *       {@code attrExp} subset of RFC 7644 §3.4.2.2 restricted to {@code eq} with a JSON string
 *       ({@code compValue}); the element is the one whose {@code type} (or other) sub-attribute has
 *       that value (RFC 7643 §2.4, §4.1.2);
 *   <li>fully qualified extension attribute, SCIM 2.0 form: {@code
 *       urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department} — schema URN, a colon,
 *       then the attribute (RFC 7644 §3.10);
 *   <li>SCIM 1.x dotted extension form: {@code urn:scim:schemas:extension:enterprise:1.0.department},
 *       as documented by Slack's SCIM API; the numeric version segments after the last colon belong
 *       to the schema URN.
 * </ol>
 *
 * <p>An attribute qualified with the core User schema URN is normalised to the unqualified form, so
 * {@code urn:ietf:params:scim:schemas:core:2.0:User:userName} and {@code userName} are the same path.
 *
 * @param schemaUrn the extension schema the attribute belongs to, or {@code null} for the core
 *     schema
 * @param attribute the top-level attribute name within that schema
 * @param subAttribute the sub-attribute, or {@code null}
 * @param filterAttribute for filtered multi-valued paths, the sub-attribute compared, else {@code
 *     null}
 * @param filterValue for filtered multi-valued paths, the value it must equal, else {@code null}
 */
public record AttributePath(
        String schemaUrn,
        String attribute,
        String subAttribute,
        String filterAttribute,
        String filterValue) {

    /** The shape of a path, for detecting rules that would build conflicting JSON. */
    public enum Shape {
        SIMPLE,
        COMPLEX,
        MULTI_VALUED
    }

    public AttributePath {
        if (attribute == null) throw new IllegalArgumentException("attribute is required");
        if ((filterAttribute == null) != (filterValue == null)) {
            throw new IllegalArgumentException("filter attribute and value go together");
        }
        if (filterAttribute != null && subAttribute == null) {
            throw new IllegalArgumentException("a filtered path needs a sub-attribute");
        }
    }

    public boolean isExtension() {
        return schemaUrn != null;
    }

    public boolean isFiltered() {
        return filterAttribute != null;
    }

    public Shape shape() {
        if (isFiltered()) return Shape.MULTI_VALUED;
        return subAttribute == null ? Shape.SIMPLE : Shape.COMPLEX;
    }

    /**
     * One spelling per path, used for ordering rules and for error messages. Extension attributes
     * are written in the RFC 7644 §3.10 colon form whatever form they were parsed from.
     */
    public String canonical() {
        StringBuilder b = new StringBuilder();
        if (schemaUrn != null) b.append(schemaUrn).append(':');
        b.append(attribute);
        if (filterAttribute != null) {
            b.append('[').append(filterAttribute).append(" eq ");
            appendQuoted(b, filterValue);
            b.append(']');
        }
        if (subAttribute != null) b.append('.').append(subAttribute);
        return b.toString();
    }

    /** The case-folded identity of the path: SCIM attribute names and URNs are case-insensitive. */
    String identity() {
        return canonical().toLowerCase(Locale.ROOT);
    }

    /** The identity of the top-level attribute this path writes into. */
    String containerIdentity() {
        String s = (schemaUrn == null ? "" : schemaUrn + ":") + attribute;
        return s.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return canonical();
    }

    /**
     * Parses one path.
     *
     * @throws MappingException with a message naming the position of the problem
     */
    public static AttributePath parse(String text) {
        if (text == null || text.isBlank()) throw new MappingException("empty attribute path");
        return new Parser(text.strip()).path();
    }

    private static void appendQuoted(StringBuilder b, String value) {
        b.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') b.append('\\');
            b.append(c);
        }
        b.append('"');
    }

    /** A small hand-written scanner over the path text; no regular expressions. */
    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        AttributePath path() {
            String schema = null;
            if (text.regionMatches(true, 0, "urn:", 0, 4)) {
                int colon = lastTopLevelColon();
                if (colon <= 4 || colon == text.length() - 1) {
                    throw fail("an extension path needs a schema URN and an attribute");
                }
                schema = text.substring(0, colon);
                pos = colon + 1;
                // SCIM 1.x dotted form: leading all-digit segments after the last colon are the schema
                // version ("…:enterprise:1.0.department" → schema "…:enterprise:1.0").
                int versionEnd = versionSegmentsEnd(pos);
                if (versionEnd > pos) {
                    if (versionEnd >= text.length() || text.charAt(versionEnd) != '.') {
                        throw fail("the 1.x extension form needs an attribute after the version");
                    }
                    schema = schema + ':' + text.substring(pos, versionEnd);
                    pos = versionEnd + 1;
                }
                if (schema.equalsIgnoreCase(Scim.USER_CORE_SCHEMA)) schema = null;
            }
            String attribute = name();
            String filterAttribute = null;
            String filterValue = null;
            String subAttribute = null;
            if (peek() == '[') {
                pos++;
                skipSpaces();
                filterAttribute = name();
                if (!skipSpaces()) throw fail("expected ' eq ' after the filter attribute");
                expectKeyword("eq");
                if (!skipSpaces()) throw fail("expected a space after 'eq'");
                filterValue = quoted();
                skipSpaces();
                expect(']');
            }
            if (peek() == '.') {
                pos++;
                subAttribute = name();
            }
            if (pos != text.length()) throw fail("unexpected '" + text.charAt(pos) + "'");
            if (filterAttribute != null && subAttribute == null) {
                throw fail(
                        "a filtered multi-valued path needs a sub-attribute, e.g. emails[type eq \"work\"].value");
            }
            return new AttributePath(schema, attribute, subAttribute, filterAttribute, filterValue);
        }

        /** Index of the last ':' that is not inside brackets or quotes; -1 if none. */
        private int lastTopLevelColon() {
            int last = -1;
            int depth = 0;
            boolean quoted = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (quoted) {
                    if (c == '\\') i++;
                    else if (c == '"') quoted = false;
                } else if (c == '"') {
                    quoted = true;
                } else if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                } else if (c == ':' && depth == 0) {
                    last = i;
                }
            }
            return last;
        }

        /** End of a run of dot-separated all-digit segments starting at {@code from}, else from. */
        private int versionSegmentsEnd(int from) {
            int i = from;
            int end = from;
            while (i < text.length() && isDigit(text.charAt(i))) {
                while (i < text.length() && isDigit(text.charAt(i))) i++;
                end = i;
                if (i < text.length()
                        && text.charAt(i) == '.'
                        && i + 1 < text.length()
                        && isDigit(text.charAt(i + 1))) {
                    i++;
                } else {
                    break;
                }
            }
            return end;
        }

        /** RFC 7643 §2.1: ATTRNAME = ALPHA *(nameChar), nameChar = "-" / "_" / DIGIT / ALPHA; or $ref. */
        private String name() {
            int start = pos;
            if (text.startsWith("$ref", pos)) {
                pos += 4;
                return "$ref";
            }
            if (pos >= text.length() || !isAlpha(text.charAt(pos))) {
                throw fail("expected an attribute name");
            }
            pos++;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (isAlpha(c) || isDigit(c) || c == '-' || c == '_') pos++;
                else break;
            }
            return text.substring(start, pos);
        }

        /** A JSON string (RFC 8259 §7) as used for compValue in RFC 7644 §3.4.2.2. */
        private String quoted() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                if (pos >= text.length()) throw fail("unterminated string");
                char c = text.charAt(pos++);
                if (c == '"') return b.toString();
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                if (pos >= text.length()) throw fail("unterminated escape");
                char e = text.charAt(pos++);
                switch (e) {
                    case '"', '\\', '/' -> b.append(e);
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) throw fail("short \\u escape");
                        try {
                            b.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw fail("bad \\u escape");
                        }
                        pos += 4;
                    }
                    default -> throw fail("bad escape \\" + e);
                }
            }
        }

        private void expectKeyword(String keyword) {
            // RFC 7644 §3.4.2.2: operators are case-insensitive.
            if (!text.regionMatches(true, pos, keyword, 0, keyword.length())) {
                throw fail("only the 'eq' operator is supported in a mapping filter");
            }
            pos += keyword.length();
        }

        private void expect(char c) {
            if (peek() != c) throw fail("expected '" + c + "'");
            pos++;
        }

        private boolean skipSpaces() {
            int start = pos;
            while (pos < text.length() && text.charAt(pos) == ' ') pos++;
            return pos > start;
        }

        private char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        private MappingException fail(String why) {
            return new MappingException("'" + text + "' at " + (pos + 1) + ": " + why);
        }

        private static boolean isAlpha(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}
