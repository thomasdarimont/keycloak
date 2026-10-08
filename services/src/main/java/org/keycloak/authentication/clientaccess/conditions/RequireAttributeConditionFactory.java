package org.keycloak.authentication.clientaccess.conditions;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.keycloak.authentication.clientaccess.ClientAccessCondition;
import org.keycloak.authentication.clientaccess.ClientAccessConditionFactory;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

public class RequireAttributeConditionFactory implements ClientAccessConditionFactory {

    public static final String PROVIDER_ID = "require-attribute";
    public static final String CONFIG_NAME = "attribute";
    public static final String CONFIG_VALUE = "value";
    public static final String CONFIG_REGEX = "regex";
    public static final String CONFIG_INCLUDE_GROUP_ATTRIBUTES = "includeGroupAttributes";

    private static final List<ProviderConfigProperty> CONFIG = ProviderConfigurationBuilder.create()
            .property().name(CONFIG_NAME).label("Attribute name")
            .type(ProviderConfigProperty.STRING_TYPE).add()
            .property().name(CONFIG_VALUE).label("Expected value")
            .helpText("Leave empty to only require the attribute to be present.")
            .type(ProviderConfigProperty.STRING_TYPE).add()
            .property().name(CONFIG_REGEX).label("Expected value is a regular expression")
            .type(ProviderConfigProperty.BOOLEAN_TYPE).defaultValue("false").add()
            .property().name(CONFIG_INCLUDE_GROUP_ATTRIBUTES).label("Include group attributes")
            .helpText("If on, attributes of the user's groups and their parents are considered as well.")
            .type(ProviderConfigProperty.BOOLEAN_TYPE).defaultValue("false").add()
            .property(NEGATE_PROPERTY)
            .build();

    @Override
    public ClientAccessCondition create(KeycloakSession session, ComponentModel model) {
        return new RequireAttributeCondition(session, model);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Requires a user attribute, directly on the user or resolved through group membership, to have an expected value.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG;
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) throws ComponentValidationException {
        String name = model.get(CONFIG_NAME);
        if (name == null || name.isBlank()) {
            throw new ComponentValidationException("Attribute name is required");
        }
        if (model.get(CONFIG_REGEX, false)) {
            String value = model.get(CONFIG_VALUE);
            if (value != null && !value.isEmpty()) {
                try {
                    Pattern.compile(value);
                } catch (PatternSyntaxException e) {
                    throw new ComponentValidationException("Expected value is not a valid regular expression: " + e.getDescription());
                }
            }
        }
    }
}
