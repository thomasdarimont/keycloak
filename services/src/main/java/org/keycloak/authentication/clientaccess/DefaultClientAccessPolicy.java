package org.keycloak.authentication.clientaccess;

import java.util.List;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;

/**
 * A policy that grants access when every condition sub-component matches. A policy without conditions grants.
 */
public class DefaultClientAccessPolicy implements ClientAccessPolicy {

    private static final Logger logger = Logger.getLogger(DefaultClientAccessPolicy.class);

    private final KeycloakSession session;
    private final ComponentModel model;

    public DefaultClientAccessPolicy(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    @Override
    public ClientAccessPolicyResult evaluate(ClientAccessContext context) {
        List<ComponentModel> conditions = context.getRealm()
                .getComponentsStream(model.getId(), ClientAccessCondition.class.getName())
                .collect(Collectors.toList());

        for (ComponentModel conditionModel : conditions) {
            ClientAccessCondition condition = session.getProvider(ClientAccessCondition.class, conditionModel);
            if (condition == null) {
                logger.warnf("Condition '%s' of type '%s' in policy '%s' is not available, treating as not matched",
                        conditionModel.getName(), conditionModel.getProviderId(), model.getName());
                return ClientAccessPolicyResult.denied(conditionModel.getName());
            }
            boolean matched = condition.matches(context);
            if (conditionModel.get(ClientAccessConditionFactory.CONFIG_NEGATE, false)) {
                matched = !matched;
            }
            if (!matched) {
                return ClientAccessPolicyResult.denied(conditionModel.getName());
            }
        }
        return ClientAccessPolicyResult.granted();
    }
}
