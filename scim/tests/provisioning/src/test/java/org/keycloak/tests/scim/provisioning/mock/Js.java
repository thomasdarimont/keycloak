package org.keycloak.tests.scim.provisioning.mock;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * The few JavaScript coercions the Node mock relies on (truthiness, {@code String(v)}, {@code Number(v)}), so the port
 * answers the same for odd inputs as for normal ones.
 */
final class Js {

    private Js() {
    }

    /** JavaScript truthiness of a JSON value; a missing value is {@code undefined}. */
    static boolean truthy(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return false;
        }
        if (n.isBoolean()) {
            return n.booleanValue();
        }
        if (n.isNumber()) {
            double d = n.doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        if (n.isTextual()) {
            return !n.textValue().isEmpty();
        }
        return true;
    }

    /** {@code value == null} in JavaScript: missing or JSON null. */
    static boolean isNullish(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull();
    }

    /** {@code String(v)}. */
    static String string(JsonNode n) {
        if (n == null || n.isMissingNode()) {
            return "undefined";
        }
        if (n.isNull()) {
            return "null";
        }
        if (n.isTextual()) {
            return n.textValue();
        }
        if (n.isBoolean()) {
            return String.valueOf(n.booleanValue());
        }
        if (n.isNumber()) {
            return numberToString(n.doubleValue());
        }
        if (n.isArray()) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < n.size(); i++) {
                if (i > 0) {
                    b.append(',');
                }
                JsonNode e = n.get(i);
                if (!isNullish(e)) {
                    b.append(string(e));
                }
            }
            return b.toString();
        }
        return "[object Object]";
    }

    /** {@code Number(v)}. */
    static double number(JsonNode n) {
        if (n == null || n.isMissingNode()) {
            return Double.NaN;
        }
        if (n.isNull()) {
            return 0;
        }
        if (n.isNumber()) {
            return n.doubleValue();
        }
        if (n.isBoolean()) {
            return n.booleanValue() ? 1 : 0;
        }
        if (n.isTextual()) {
            return number(n.textValue());
        }
        return Double.NaN;
    }

    /** {@code Number(s)} for a string. */
    static double number(String s) {
        String t = s.trim();
        if (t.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** A JavaScript number as {@code JSON.stringify} writes it (NaN → null). */
    static JsonNode numberNode(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (d == Math.rint(d) && Math.abs(d) < 9.007199254740992E15) {
            return JsonNodeFactory.instance.numberNode((long) d);
        }
        return JsonNodeFactory.instance.numberNode(d);
    }

    static String numberToString(double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (d == Math.rint(d) && Math.abs(d) < 9.007199254740992E15) {
            return Long.toString((long) d);
        }
        return new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
    }
}
