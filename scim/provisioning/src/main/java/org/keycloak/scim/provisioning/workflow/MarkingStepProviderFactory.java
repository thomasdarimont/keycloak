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

/** Common ground of the marker-writing steps: users only, the {@code destination} parameter. */
abstract class MarkingStepProviderFactory
        implements WorkflowStepProviderFactory<MarkingStepProvider> {
    private static final List<ProviderConfigProperty> PROPERTIES =
            StepTargets.destinationProperty(ProviderConfigurationBuilder.create()).build();

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
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return ScimProvisioning.isWorkflowsEnabled();
    }
}
