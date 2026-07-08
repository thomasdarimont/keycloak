package org.keycloak.protocol.oidc.rar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import org.keycloak.representations.AuthorizationDetailsJSONRepresentation;
import org.keycloak.util.JsonSerialization;

/**
 * Read-only, theme-facing view of a single {@code authorization_details} (RFC 9396) entry, produced by an
 * {@link AuthorizationDetailsProcessor} so that arbitrary RAR types can be rendered on the OAuth grant (consent)
 * screen without every type needing a bespoke {@code LoginFormsProvider}.
 * <p>
 * Because RAR entries are arbitrary JSON — frequently nested objects and arrays of objects — the content is modelled as
 * a tree of {@link Entry}s (an entry may itself have child {@link Entry#getFields() fields}) rather than a flat
 * key/value list. A template macro can render the tree recursively.
 * <p>
 * {@link #getTitle() title} and {@link Entry#getLabel() labels} are treated as message keys (resolved with
 * {@code advancedMsg} in the template, which falls back to the literal when there is no message), so they can be
 * localized and overridden per realm/theme while still working for generic field names.
 */
public class AuthorizationDetailDisplay {

    private final String type;
    private final String title;
    private final List<Entry> entries;

    public AuthorizationDetailDisplay(String type, String title, List<Entry> entries) {
        this.type = type;
        this.title = title;
        this.entries = entries == null ? List.of() : entries;
    }

    /**
     * @return the {@code authorization_details} "type" this display was created from.
     */
    public String getType() {
        return type;
    }

    /**
     * @return message key for the heading rendered above the entries, may be {@code null} to render no heading.
     */
    public String getTitle() {
        return title;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * Builds a generic, data-driven display: it renders exactly the fields that are present in the
     * {@code authorization_details} entry (using their own JSON keys as labels), recursing into nested objects and
     * arrays. It intentionally does not editorialize — no fixed set of "common" fields and no injected title — so what
     * is shown is entirely up to the entry itself. Used as the default rendering for types that do not provide a
     * curated {@link AuthorizationDetailsProcessor#toConsentDisplay}; labels are still passed through
     * {@code advancedMsg}, so a theme may localize a given key if it wishes.
     */
    public static AuthorizationDetailDisplay generic(AuthorizationDetailsJSONRepresentation detail) {
        Map<String, Object> raw = JsonSerialization.mapper.convertValue(detail, new TypeReference<>() {});
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Object> field : raw.entrySet()) {
            if ("type".equals(field.getKey())) {
                // rendered via the display type / data attribute, not as a field
                continue;
            }
            Entry entry = toEntry(field.getKey(), field.getValue());
            if (entry != null) {
                entries.add(entry);
            }
        }
        return new AuthorizationDetailDisplay(detail.getType(), null, entries);
    }

    private static Entry toEntry(String label, Object value) {
        if (value instanceof Map<?, ?> map) {
            List<Entry> fields = new ArrayList<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Entry child = toEntry(String.valueOf(e.getKey()), e.getValue());
                if (child != null) {
                    fields.add(child);
                }
            }
            return fields.isEmpty() ? null : new Entry(label, null, fields);
        }
        if (value instanceof List<?> list) {
            List<Entry> fields = new ArrayList<>();
            for (Object item : list) {
                // list items carry no label of their own; scalars become value-only leaves, objects become groups
                Entry child = toEntry(null, item);
                if (child != null) {
                    fields.add(child);
                }
            }
            return fields.isEmpty() ? null : new Entry(label, null, fields);
        }
        return value == null ? null : new Entry(label, String.valueOf(value), null);
    }

    /**
     * A node in the display tree: a labelled value, a labelled container of child {@link #getFields() fields}, or (for
     * list items) a bare value with no label. The template decides how to render each shape.
     */
    public static class Entry {

        private final String label;
        private final String value;
        private final List<Entry> fields;

        public Entry(String label, String value) {
            this(label, value, null);
        }

        public Entry(String label, String value, List<Entry> fields) {
            this.label = label;
            this.value = value;
            this.fields = fields == null ? List.of() : fields;
        }

        /**
         * @return message key for the entry label, or {@code null} for an unlabelled entry (e.g. a list item).
         */
        public String getLabel() {
            return label;
        }

        /**
         * @return the scalar value of this entry, or {@code null} when the entry is a container of {@link #getFields()}.
         */
        public String getValue() {
            return value;
        }

        /**
         * @return the nested child entries of this entry, never {@code null} (empty for a leaf).
         */
        public List<Entry> getFields() {
            return fields;
        }
    }
}
