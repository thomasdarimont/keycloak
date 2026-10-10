package org.keycloak.scim.provisioning.workflow;

import java.util.ArrayList;
import java.util.List;

import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.component.Destinations;

import org.jboss.logging.Logger;

/**
 * Which destinations a step acts on: the {@code destination} parameter ({@code with: destination:
 * …}, a destination name or component id, several allowed), or every destination of the realm
 * when it is absent.
 */
final class StepTargets {
    private static final Logger log = Logger.getLogger(StepTargets.class);

    static final String DESTINATION = "destination";

    private StepTargets() {}

    static ProviderConfigurationBuilder destinationProperty(ProviderConfigurationBuilder b) {
        return b.property()
                .name(DESTINATION)
                .label("Destination")
                .helpText(
                        "scim-provisioning destination name or id; several allowed. Empty means every destination of the realm.")
                .type(ProviderConfigProperty.MULTIVALUED_STRING_TYPE)
                .add();
    }

    static List<String> refs(ComponentModel model) {
        List<String> out = new ArrayList<>();
        List<String> raw =
                model == null || model.getConfig() == null ? null : model.getConfig().get(DESTINATION);
        if (raw == null) return out;
        for (String v : raw) {
            if (v == null) continue;
            for (String part : v.split("##|,")) if (!part.isBlank()) out.add(part.strip());
        }
        return out;
    }

    /** The destinations named, or all of them; a name that no longer resolves is logged and skipped. */
    static List<Destination> resolve(RealmModel realm, ComponentModel model, String stepId) {
        List<Destination> all = Destinations.of(realm);
        List<String> refs = refs(model);
        if (refs.isEmpty()) return all;
        List<Destination> out = new ArrayList<>();
        for (String ref : refs) {
            Destination d = find(all, ref);
            if (d == null) {
                log.warnf(
                        "scim-provisioning: workflow step %s names destination '%s', which realm %s does not have",
                        stepId, ref, realm.getName());
            } else if (!out.contains(d)) {
                out.add(d);
            }
        }
        return out;
    }

    /** Refuses a step whose {@code destination} names nothing in the realm. */
    static void validate(RealmModel realm, ComponentModel model) {
        List<Destination> all = Destinations.of(realm);
        for (String ref : refs(model)) {
            if (find(all, ref) == null) {
                throw new ComponentValidationException(
                        "no scim-provisioning destination '" + ref + "' in this realm");
            }
        }
    }

    private static Destination find(List<Destination> all, String ref) {
        for (Destination d : all) if (ref.equals(d.id())) return d;
        for (Destination d : all) if (ref.equals(d.name())) return d;
        return null;
    }
}
