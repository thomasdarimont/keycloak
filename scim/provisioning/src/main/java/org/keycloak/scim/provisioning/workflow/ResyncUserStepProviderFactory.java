package org.keycloak.scim.provisioning.workflow;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;

/**
 * EXPERIMENTAL. {@code uses: scim-provisioning-resync-user}: sends the user again — an ordinary, unforced
 * mark, so whatever is right for them now (upsert if in scope, deprovision if not) is delivered,
 * and nothing at all if the destination already holds exactly that.
 */
public class ResyncUserStepProviderFactory extends MarkingStepProviderFactory {
    public static final String ID = "scim-provisioning-resync-user";

    @Override
    public MarkingStepProvider create(KeycloakSession session, ComponentModel model) {
        return new MarkingStepProvider(session, model, ID, false, null);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getHelpText() {
        return "Experimental: re-deliver the user to scim-provisioning destinations (unchanged state sends nothing).";
    }
}
