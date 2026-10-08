package org.keycloak.authentication.clientaccess;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

/**
 * Resolves the policies attached to a client and evaluates them for a user. All attached policies must grant.
 * A client without attached policies grants access to everyone. A reference to a policy that does not exist denies,
 * so a typo never silently opens a client.
 */
public final class ClientAccessPolicyEvaluator {

    private static final Logger logger = Logger.getLogger(ClientAccessPolicyEvaluator.class);

    private ClientAccessPolicyEvaluator() {
    }

    public static List<String> getAttachedPolicyNames(ClientModel client) {
        String value = client.getAttribute(ClientAccessPolicy.CLIENT_ATTRIBUTE);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public static Optional<ComponentModel> findPolicy(RealmModel realm, String name) {
        return realm.getComponentsStream(realm.getId(), ClientAccessPolicy.class.getName())
                .filter(model -> Objects.equals(model.getName(), name))
                .findFirst();
    }

    public static ClientAccessDecision evaluate(ClientAccessContext context) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        List<String> names = getAttachedPolicyNames(context.getClient());

        for (String name : names) {
            Optional<ComponentModel> model = findPolicy(realm, name);
            if (model.isEmpty()) {
                logger.warnf("Client '%s' references unknown client access policy '%s'", context.getClient().getClientId(), name);
                return ClientAccessDecision.denied(name, "Unknown client access policy '" + name + "'");
            }
            ClientAccessPolicy policy = session.getProvider(ClientAccessPolicy.class, model.get());
            if (policy == null) {
                return ClientAccessDecision.denied(name, "Client access policy provider '" + model.get().getProviderId() + "' not available");
            }
            ClientAccessPolicyResult result = policy.evaluate(context);
            if (!result.isGranted()) {
                return ClientAccessDecision.denied(name, "Client access policy '" + name + "' not satisfied, failed condition: " + result.getFailedCondition());
            }
        }
        return ClientAccessDecision.granted();
    }
}
