package org.keycloak.scim.provisioning.hash;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The RFC 8785 (JSON Canonicalization Scheme) form of a JSON tree, as UTF-8 bytes.
 *
 * <ul>
 *   <li>object members sorted by property name, comparing UTF-16 code units (§3.2.3) — which is
 *       exactly {@link String#compareTo}; never a locale-sensitive collator;
 *   <li>array elements in place;
 *   <li>no whitespace;
 *   <li>strings escaped as in §3.2.2.2: {@code \"}, {@code \\}, the five short control escapes,
 *       {@code \}{@code u00xx} (lower-case hex) for the other C0 controls, everything else literal;
 *   <li>numbers serialised as ECMAScript's {@code Number.prototype.toString()} does (§3.2.2.3).
 * </ul>
 *
 * <p>The result depends only on the tree's content, never on the order in which it was built.
 * Shortest round-trip double formatting relies on {@link Double#toString(double)} as specified
 * from Java 19 onward; the Keycloak 26.8 image runs Java 21. SCIM resources built by this project
 * contain no floating-point numbers.
 */
public final class CanonicalJson {
    private static final BigInteger MAX_SAFE = BigInteger.valueOf(9007199254740992L);

    private CanonicalJson() {}

    public static byte[] canonicalize(JsonNode node) {
        StringBuilder b = new StringBuilder();
        write(node, b);
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** The canonical form as a string (for tests and diagnostics). */
    public static String toString(JsonNode node) {
        StringBuilder b = new StringBuilder();
        write(node, b);
        return b.toString();
    }

    private static void write(JsonNode n, StringBuilder b) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            b.append("null");
        } else if (n.isObject()) {
            List<Map.Entry<String, JsonNode>> members = new ArrayList<>(n.properties());
            members.sort(Map.Entry.comparingByKey());
            b.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonNode> m : members) {
                if (!first) b.append(',');
                first = false;
                string(m.getKey(), b);
                b.append(':');
                write(m.getValue(), b);
            }
            b.append('}');
        } else if (n.isArray()) {
            b.append('[');
            for (int i = 0; i < n.size(); i++) {
                if (i > 0) b.append(',');
                write(n.get(i), b);
            }
            b.append(']');
        } else if (n.isTextual()) {
            string(n.textValue(), b);
        } else if (n.isBoolean()) {
            b.append(n.booleanValue() ? "true" : "false");
        } else if (n.isIntegralNumber()) {
            BigInteger v = n.bigIntegerValue();
            if (v.abs().compareTo(MAX_SAFE) <= 0) b.append(v);
            else b.append(number(v.doubleValue()));
        } else if (n.isNumber()) {
            b.append(number(n.doubleValue()));
        } else {
            throw new IllegalArgumentException("not a JSON value: " + n.getNodeType());
        }
    }

    private static void string(String s, StringBuilder b) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    throw new IllegalArgumentException("lone surrogate in JSON string");
                }
                b.append(c).append(s.charAt(++i));
                continue;
            }
            if (Character.isLowSurrogate(c))
                throw new IllegalArgumentException("lone surrogate in JSON string");
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\b':
                    b.append("\\b");
                    break;
                case '\f':
                    b.append("\\f");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        b.append("\\u00");
                        b.append(Character.forDigit(c >> 4, 16));
                        b.append(Character.forDigit(c & 0xF, 16));
                    } else {
                        b.append(c);
                    }
            }
        }
        b.append('"');
    }

    /** ECMAScript Number::toString(x) for a finite double (ECMA-262, Number.prototype.toString). */
    static String number(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            throw new IllegalArgumentException("NaN and Infinity are not JSON numbers");
        }
        if (x == 0) return "0"; // also -0
        if (x < 0) return "-" + number(-x);

        // Shortest digit string s with k digits and exponent n such that s × 10^(n−k) = x.
        BigDecimal d = new BigDecimal(Double.toString(x)).stripTrailingZeros();
        String digits = d.unscaledValue().toString();
        // Double.toString prints at least two significant digits; a single digit may round-trip.
        if (digits.length() == 2) {
            BigDecimal one = d.round(new MathContext(1, RoundingMode.HALF_EVEN));
            if (Double.parseDouble(one.toString()) == x) {
                d = one.stripTrailingZeros();
                digits = d.unscaledValue().toString();
            }
        }
        int k = digits.length();
        int n = k - d.scale();

        StringBuilder b = new StringBuilder();
        if (k <= n && n <= 21) {
            b.append(digits);
            for (int i = 0; i < n - k; i++) b.append('0');
        } else if (0 < n && n <= 21) {
            b.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            b.append("0.");
            for (int i = 0; i < -n; i++) b.append('0');
            b.append(digits);
        } else {
            b.append(digits.charAt(0));
            if (k > 1) b.append('.').append(digits, 1, k);
            int e = n - 1;
            b.append('e').append(e < 0 ? '-' : '+').append(Math.abs(e));
        }
        return b.toString();
    }
}
