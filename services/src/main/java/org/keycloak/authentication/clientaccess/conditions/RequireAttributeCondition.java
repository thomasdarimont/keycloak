package org.keycloak.authentication.clientaccess.conditions;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;

import org.keycloak.authentication.clientaccess.ClientAccessCondition;
import org.keycloak.authentication.clientaccess.ClientAccessContext;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.utils.KeycloakModelUtils;

/**
 * Matches when a user attribute has the expected value. Optionally resolves the attribute through the user's groups
 * and optionally treats the expected value as a regular expression. Without an expected value, the attribute only
 * has to be present.
 */
public class RequireAttributeCondition implements ClientAccessCondition {

    private final KeycloakSession session;
    private final ComponentModel model;

    public RequireAttributeCondition(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    @Override
    public boolean matches(ClientAccessContext context) {
        String name = model.get(RequireAttributeConditionFactory.CONFIG_NAME);
        if (name == null || name.isBlank()) {
            return true;
        }
        String expected = model.get(RequireAttributeConditionFactory.CONFIG_VALUE);
        boolean regex = model.get(RequireAttributeConditionFactory.CONFIG_REGEX, false);
        boolean includeGroups = model.get(RequireAttributeConditionFactory.CONFIG_INCLUDE_GROUP_ATTRIBUTES, false);

        Collection<String> values = includeGroups
                ? KeycloakModelUtils.resolveAttribute(context.getUser(), name, true)
                : context.getUser().getAttributeStream(name).toList();

        if (expected == null || expected.isEmpty()) {
            return !values.isEmpty();
        }
        if (regex) {
            Pattern pattern = Pattern.compile(expected);
            return values.stream().anyMatch(v -> pattern.matcher(v).matches());
        }
        return values.stream().anyMatch(v -> Objects.equals(v, expected));
    }
}
