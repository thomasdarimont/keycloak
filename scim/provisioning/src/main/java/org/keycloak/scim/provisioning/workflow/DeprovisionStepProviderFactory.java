package org.keycloak.scim.provisioning.workflow;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;

/**
 * EXPERIMENTAL. {@code uses: scim-provisioning-deprovision}: takes the user's accounts away,
 * in every destination or in those named by {@code destination}, with each destination's own
 * deprovision mode. The same one-shot forced deprovision as {@code POST /scim-provisioning/deprovision}.
 *
 * <p>Users only: the Workflows SPI has no group resource type. With the {@code workflows} feature
 * off, Keycloak does not load the step factories and nothing else in SCIM provisioning changes.
 */
public class DeprovisionStepProviderFactory extends MarkingStepProviderFactory {
    public static final String ID = "scim-provisioning-deprovision";

    @Override
    public MarkingStepProvider create(KeycloakSession session, ComponentModel model) {
        return new MarkingStepProvider(session, model, ID, true, null);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getHelpText() {
        return "Experimental: deprovision the user in scim-provisioning destinations (one-shot), with each destination's mode.";
    }
}
