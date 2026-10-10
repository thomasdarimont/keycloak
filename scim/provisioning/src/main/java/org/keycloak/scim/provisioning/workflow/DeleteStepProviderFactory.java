package org.keycloak.scim.provisioning.workflow;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;

/**
 * EXPERIMENTAL. {@code uses: scim-provisioning-delete}: deletes the user's accounts, whatever the
 * destinations' deprovision mode — including accounts an earlier step only deactivated, so a
 * workflow can deactivate at departure and delete after a retention period. One-shot like {@code
 * scim-provisioning-deprovision}. A {@code DELETE} answered 404 counts as done.
 */
public class DeleteStepProviderFactory extends MarkingStepProviderFactory {
    public static final String ID = "scim-provisioning-delete";

    @Override
    public MarkingStepProvider create(KeycloakSession session, ComponentModel model) {
        return new MarkingStepProvider(session, model, ID, true, "delete");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getHelpText() {
        return "Experimental: delete the user's accounts in scim-provisioning destinations, even ones already deactivated.";
    }
}
