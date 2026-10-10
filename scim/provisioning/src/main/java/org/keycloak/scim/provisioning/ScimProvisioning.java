package org.keycloak.scim.provisioning;

import org.keycloak.common.Profile;

/** Feature switch of SCIM provisioning. */
public final class ScimProvisioning {

    /** The outbox entry kind, the destination provider id and the admin resource path. */
    public static final String ID = "scim-provisioning";

    private ScimProvisioning() {}

    public static boolean isEnabled() {
        return Profile.isFeatureEnabled(Profile.Feature.SCIM_PROVISIONING);
    }

    /** Workflow steps need both features. */
    public static boolean isWorkflowsEnabled() {
        return isEnabled() && Profile.isFeatureEnabled(Profile.Feature.WORKFLOWS);
    }
}
