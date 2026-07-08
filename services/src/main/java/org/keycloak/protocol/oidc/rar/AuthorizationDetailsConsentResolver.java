package org.keycloak.protocol.oidc.rar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.keycloak.OAuth2Constants;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.rar.AuthorizationDetails;
import org.keycloak.rar.AuthorizationRequestSource;
import org.keycloak.representations.AuthorizationDetailsJSONRepresentation;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.util.JsonSerialization;

import com.fasterxml.jackson.core.type.TypeReference;
import org.jboss.logging.Logger;

/**
 * Associates the {@code authorization_details} (RAR) entries of the current request with the OAuth client scopes shown
 * on the consent screen, and turns each associated entry into an {@link AuthorizationDetailDisplay} via its
 * {@link AuthorizationDetailsProcessor}.
 * <p>
 * The association is configured per client scope through the {@link #CLIENT_SCOPE_ATTRIBUTE_TYPES} attribute, which
 * holds one or more type matchers (newline or comma separated). Each matcher is either an exact type or, when prefixed
 * with {@code regex:}, an anchored (full-match) regular expression. An entry is rendered under a scope only when the
 * scope is requested (and displayed on the consent screen) and one of its matchers matches the entry's type. When
 * several scopes match the same entry, the scope with the lowest gui order (then name) owns it, so the entry is shown
 * exactly once.
 */
public class AuthorizationDetailsConsentResolver {

    private static final Logger logger = Logger.getLogger(AuthorizationDetailsConsentResolver.class);

    /**
     * Client scope attribute holding the {@code authorization_details} type matchers associated with the scope.
     */
    public static final String CLIENT_SCOPE_ATTRIBUTE_TYPES = "authorization_details.types";

    private static final String REGEX_PREFIX = "regex:";

    private final KeycloakSession session;

    public AuthorizationDetailsConsentResolver(KeycloakSession session) {
        this.session = session;
    }

    /**
     * @param authSession       the current authentication session, used to read the {@code authorization_details} client note
     * @param requestedScopes   the client scopes about to be shown on the consent screen
     * @return a map from client scope id to the display representations to render underneath that scope's consent line,
     * never {@code null} (empty when there is nothing to show)
     */
    public Map<String, List<AuthorizationDetailDisplay>> resolve(AuthenticationSessionModel authSession, List<AuthorizationDetails> requestedScopes) {
        Map<String, List<AuthorizationDetailDisplay>> result = new LinkedHashMap<>();
        if (authSession == null || requestedScopes == null || requestedScopes.isEmpty()) {
            return result;
        }

        List<AuthorizationDetailsJSONRepresentation> entries = parseAuthorizationDetails(authSession.getClientNote(OAuth2Constants.AUTHORIZATION_DETAILS));
        if (entries.isEmpty()) {
            return result;
        }

        // Deterministic owner order: lowest gui order first, then scope name.
        List<ClientScopeModel> scopes = requestedScopes.stream()
                .filter(ad -> ad.getSource() == AuthorizationRequestSource.SCOPE && ad.getClientScope() != null)
                .map(AuthorizationDetails::getClientScope)
                .sorted(Comparator.comparingInt(AuthorizationDetailsConsentResolver::guiOrder).thenComparing(ClientScopeModel::getName))
                .toList();
        if (scopes.isEmpty()) {
            return result;
        }

        AuthorizationDetailsProcessorManager processorManager = new AuthorizationDetailsProcessorManager(session);

        for (AuthorizationDetailsJSONRepresentation entry : entries) {
            String type = entry.getType();
            if (type == null) {
                continue;
            }
            ClientScopeModel owner = findOwnerScope(scopes, type);
            if (owner == null) {
                continue;
            }
            AuthorizationDetailsProcessor<?> processor = processorManager.getProcessor(type);
            AuthorizationDetailDisplay display;
            try {
                // A registered processor may curate the rendering; otherwise fall back to a generic tree so that
                // arbitrary types associated with a scope still render without bespoke code.
                display = processor != null ? processor.toConsentDisplay(entry) : AuthorizationDetailDisplay.generic(entry);
            } catch (RuntimeException e) {
                logger.warnf(e, "Failed to build consent display for authorization_details type '%s'", type);
                continue;
            }
            if (display != null) {
                result.computeIfAbsent(owner.getId(), k -> new ArrayList<>()).add(display);
            }
        }

        return result;
    }

    private ClientScopeModel findOwnerScope(List<ClientScopeModel> scopes, String type) {
        for (ClientScopeModel scope : scopes) {
            if (matches(scope, type)) {
                return scope;
            }
        }
        return null;
    }

    private static boolean matches(ClientScopeModel scope, String type) {
        String raw = scope.getAttribute(CLIENT_SCOPE_ATTRIBUTE_TYPES);
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (String spec : raw.split("[,\\n]")) {
            spec = spec.trim();
            if (spec.isEmpty()) {
                continue;
            }
            if (spec.startsWith(REGEX_PREFIX)) {
                try {
                    if (Pattern.compile(spec.substring(REGEX_PREFIX.length())).matcher(type).matches()) {
                        return true;
                    }
                } catch (RuntimeException e) {
                    logger.warnf("Ignoring invalid %s regex '%s' on client scope '%s'", CLIENT_SCOPE_ATTRIBUTE_TYPES, spec, scope.getName());
                }
            } else if (spec.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static int guiOrder(ClientScopeModel scope) {
        try {
            String order = scope.getGuiOrder();
            return order == null ? Integer.MAX_VALUE : Integer.parseInt(order);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private List<AuthorizationDetailsJSONRepresentation> parseAuthorizationDetails(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return JsonSerialization.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            logger.warnf(e, "Cannot parse authorization_details for consent rendering: %s", raw);
            return List.of();
        }
    }
}
