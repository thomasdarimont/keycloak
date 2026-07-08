package org.keycloak.protocol.oidc.rar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.keycloak.representations.AuthorizationDetailsJSONRepresentation;
import org.keycloak.util.JsonSerialization;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Read-only, theme-facing view of a single {@code authorization_details} (RFC 9396) entry, produced by an
 * {@link AuthorizationDetailsProcessor} so that arbitrary RAR types can be rendered on the OAuth grant (consent)
 * screen without every type needing a bespoke {@code LoginFormsProvider}.
 * <p>
 * Because RAR entries are arbitrary JSON — frequently nested objects and arrays of objects — the content is modelled as
 * a tree of {@link Entry}s (an entry may itself have child {@link Entry#getFields() fields}) rather than a flat
 * key/value list. A template macro can render the tree recursively.
 * <p>
 * The model carries only machine names ({@link #getType() type}, {@link Entry#getName() entry name}) and values — no
 * message keys. Labels are resolved in the template from the type, by convention, so translations can be provided per
 * type without touching Java:
 * <ul>
 *     <li>the heading from {@code <type>_title} (rendered only if such a message exists), and</li>
 *     <li>each field label from {@code <type>_entry_<name>}, falling back to the raw field name when absent.</li>
 * </ul>
 * For example an entry of type {@code acme_booking} with fields {@code name} and {@code cost} is translated via
 * {@code acme_booking_title}, {@code acme_booking_entry_name} and {@code acme_booking_entry_cost}.
 */
public class AuthorizationDetailDisplay {

    private final String type;
    private final List<Entry> entries;

    public AuthorizationDetailDisplay(String type, List<Entry> entries) {
        this.type = type;
        this.entries = entries == null ? List.of() : entries;
    }

    /**
     * @return the {@code authorization_details} "type" this display was created from; also the prefix used to resolve
     * the heading and field labels in the template.
     */
    public String getType() {
        return type;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * Builds a generic, data-driven display: it renders exactly the fields that are present in the
     * {@code authorization_details} entry (using their own JSON keys as machine names), recursing into nested objects
     * and arrays. It intentionally does not editorialize — no fixed set of "common" fields — so what is shown is
     * entirely up to the entry itself. Used as the default rendering for types that do not provide a curated
     * {@link AuthorizationDetailsProcessor#toConsentDisplay}. Labels are resolved by the template from the type (see the
     * class javadoc), so a theme may localize any field by adding a {@code <type>_entry_<name>} message.
     */
    public static AuthorizationDetailDisplay generic(AuthorizationDetailsJSONRepresentation detail) {
        Map<String, Object> raw = JsonSerialization.mapper.convertValue(detail, new TypeReference<>() {});
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Object> field : raw.entrySet()) {
            if ("type".equals(field.getKey())) {
                // conveyed via the display type, not rendered as a field
                continue;
            }
            Entry entry = toEntry(field.getKey(), field.getValue());
            if (entry != null) {
                entries.add(entry);
            }
        }
        return new AuthorizationDetailDisplay(detail.getType(), entries);
    }

    private static Entry toEntry(String name, Object value) {
        if (value instanceof Map<?, ?> map) {
            List<Entry> fields = new ArrayList<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Entry child = toEntry(String.valueOf(e.getKey()), e.getValue());
                if (child != null) {
                    fields.add(child);
                }
            }
            return fields.isEmpty() ? null : new Entry(name, null, fields);
        }
        if (value instanceof List<?> list) {
            List<Entry> fields = new ArrayList<>();
            for (Object item : list) {
                // list items carry no name of their own; scalars become value-only leaves, objects become groups
                Entry child = toEntry(null, item);
                if (child != null) {
                    fields.add(child);
                }
            }
            return fields.isEmpty() ? null : new Entry(name, null, fields);
        }
        return value == null ? null : new Entry(name, String.valueOf(value), null);
    }

    /**
     * A node in the display tree: a named value, a named container of child {@link #getFields() fields}, or (for list
     * items) a bare value with no name. The template resolves the label for {@link #getName() name} from the enclosing
     * display type and decides how to render each shape.
     */
    public static class Entry {

        private final String name;
        private final String value;
        private final String description;
        private final List<Entry> fields;

        public Entry(String name, String value) {
            this(name, value, null, null);
        }

        public Entry(String name, String value, List<Entry> fields) {
            this(name, value, null, fields);
        }

        public Entry(String name, String value, String description, List<Entry> fields) {
            this.name = name;
            this.value = value;
            this.description = description;
            this.fields = fields == null ? List.of() : fields;
        }

        /**
         * @return machine name of the field (e.g. {@code cost}), used to resolve its label as {@code <type>_entry_<name>}
         * with fallback to this name; {@code null} for an unlabelled entry such as a list item.
         */
        public String getName() {
            return name;
        }

        /**
         * @return the scalar value of this entry, or {@code null} when the entry is a container of {@link #getFields()}.
         */
        public String getValue() {
            return value;
        }

        /**
         * @return optional explanatory text rendered under the field (a literal, or a {@code ${messageKey}} resolved by
         * the template), or {@code null} for none.
         */
        public String getDescription() {
            return description;
        }

        /**
         * @return the nested child entries of this entry, never {@code null} (empty for a leaf).
         */
        public List<Entry> getFields() {
            return fields;
        }
    }
}
