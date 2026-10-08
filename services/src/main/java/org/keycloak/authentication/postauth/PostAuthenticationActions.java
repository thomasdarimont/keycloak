package org.keycloak.authentication.postauth;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

/**
 * Runs the realm's configured {@link PostAuthenticationAction} components in priority order and stops at the first
 * denial.
 */
public final class PostAuthenticationActions {

    private static final Logger logger = Logger.getLogger(PostAuthenticationActions.class);

    private PostAuthenticationActions() {
    }

    public static PostAuthenticationResult run(PostAuthenticationContext context) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        List<ComponentModel> actions = realm.getComponentsStream(realm.getId(), PostAuthenticationAction.class.getName())
                .filter(model -> model.get(PostAuthenticationActionFactory.CONFIG_ENABLED, true))
                .filter(model -> appliesTo(session, model, context.getTrigger()))
                .sorted(Comparator.comparingInt(model -> model.get(PostAuthenticationActionFactory.CONFIG_PRIORITY, 0)))
                .collect(Collectors.toList());

        for (ComponentModel model : actions) {
            PostAuthenticationAction action = session.getProvider(PostAuthenticationAction.class, model);
            if (action == null) {
                logger.warnf("Post-authentication action '%s' of type '%s' is not available, skipping", model.getName(), model.getProviderId());
                continue;
            }
            PostAuthenticationResult result = action.execute(context);
            if (result.isDenied()) {
                logger.debugf("Post-authentication action '%s' denied %s for user '%s' and client '%s': %s",
                        model.getName(), context.getTrigger(), context.getUser().getUsername(), context.getClient().getClientId(), result.getReason());
                return result.withActionName(model.getName());
            }
        }
        return PostAuthenticationResult.proceed();
    }

    private static boolean appliesTo(KeycloakSession session, ComponentModel model, PostAuthenticationTrigger trigger) {
        PostAuthenticationActionFactory factory = (PostAuthenticationActionFactory) session.getKeycloakSessionFactory()
                .getProviderFactory(PostAuthenticationAction.class, model.getProviderId());
        if (factory == null || !factory.getSupportedTriggers().contains(trigger)) {
            return false;
        }
        List<String> configured = model.getConfig().getList(PostAuthenticationActionFactory.CONFIG_TRIGGERS);
        if (configured == null || configured.isEmpty()) {
            return true;
        }
        Set<PostAuthenticationTrigger> triggers = configured.stream()
                .flatMap(value -> Arrays.stream(value.split("[,\\s]+")))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .map(PostAuthenticationTrigger::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PostAuthenticationTrigger.class)));
        return triggers.contains(trigger);
    }
}
