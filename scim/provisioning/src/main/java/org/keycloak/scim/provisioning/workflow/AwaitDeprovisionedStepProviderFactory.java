package org.keycloak.scim.provisioning.workflow;

import java.util.List;
import java.util.Set;

import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.workflow.ResourceType;
import org.keycloak.models.workflow.WorkflowStepProviderFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.scim.provisioning.ScimProvisioning;

/**
 * EXPERIMENTAL. {@code uses: scim-provisioning-await-deprovision}: a gate. It fails — so the engine runs
 * it again later — until the user's accounts are gone from the targeted destinations; the steps
 * after it run only then. It reads the outbox and writes nothing.
 *
 * <p>It must be a <em>scheduled</em> step (with {@code after:}): Keycloak 26.8 re-runs a failed
 * scheduled step on each pass of its step runner, but does not re-run a failed immediate step, so
 * an immediate gate that fails once would hold the workflow forever. Creation is refused without
 * {@code after}.
 */
public class AwaitDeprovisionedStepProviderFactory
        implements WorkflowStepProviderFactory<AwaitDeprovisionedStepProvider> {
    public static final String ID = "scim-provisioning-await-deprovision";
    static final String ON_DEAD_LETTER = "onDeadLetter";

    private static final List<ProviderConfigProperty> PROPERTIES =
            StepTargets.destinationProperty(ProviderConfigurationBuilder.create())
                    .property()
                    .name(ON_DEAD_LETTER)
                    .label("On a dead-lettered deprovision")
                    .helpText(
                            "wait (default): keep the workflow waiting until someone fixes or replays it. continue: let the workflow go on.")
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .options("wait", "continue")
                    .defaultValue("wait")
                    .add()
                    .build();

    @Override
    public AwaitDeprovisionedStepProvider create(KeycloakSession session, ComponentModel model) {
        return new AwaitDeprovisionedStepProvider(session, model);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getHelpText() {
        return "Experimental: wait until the user's accounts are deprovisioned in scim-provisioning destinations.";
    }

    @Override
    public Set<ResourceType> getSupportedResourceTypes() {
        return Set.of(ResourceType.USERS);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return PROPERTIES;
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model)
            throws ComponentValidationException {
        StepTargets.validate(realm, model);
        String after = model.get("after");
        if (after == null || after.isBlank()) {
            throw new ComponentValidationException(
                    ID
                            + " must be a scheduled step: give it an 'after' delay (e.g. after: 10m); Keycloak does"
                            + " not re-run a failed immediate step");
        }
        String v = model.get(ON_DEAD_LETTER);
        if (v != null && !v.equals("wait") && !v.equals("continue")) {
            throw new ComponentValidationException(ON_DEAD_LETTER + " must be wait or continue");
        }
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return ScimProvisioning.isWorkflowsEnabled();
    }
}
