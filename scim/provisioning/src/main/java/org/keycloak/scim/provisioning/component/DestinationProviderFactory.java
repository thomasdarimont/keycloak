package org.keycloak.scim.provisioning.component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.keycloak.scim.provisioning.ScimProvisioning;
import org.keycloak.scim.provisioning.client.Endpoints;
import org.keycloak.scim.provisioning.client.ScimEndpoint;
import org.keycloak.scim.provisioning.client.ScimException;
import org.keycloak.scim.provisioning.mapping.Mapping;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;
import org.keycloak.scim.provisioning.planner.ScopeEvaluator;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.userprofile.UserProfileProvider;

import org.jboss.logging.Logger;

import static org.keycloak.scim.provisioning.component.DestinationConfig.BASE_URL;
import static org.keycloak.scim.provisioning.component.DestinationConfig.DEFAULT_MAX_ATTEMPTS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.DEFAULT_RECONCILE_HOURS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.DEFAULT_REQUEST_TIMEOUT_SECONDS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.DEPROVISION;
import static org.keycloak.scim.provisioning.component.DestinationConfig.MAPPING;
import static org.keycloak.scim.provisioning.component.DestinationConfig.MAX_ATTEMPTS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.PROFILE;
import static org.keycloak.scim.provisioning.component.DestinationConfig.PROFILE_GENERIC;
import static org.keycloak.scim.provisioning.component.DestinationConfig.RECONCILE_HOURS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.REQUEST_TIMEOUT_SECONDS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.SCOPE_EVERYONE;
import static org.keycloak.scim.provisioning.component.DestinationConfig.SCOPE_GROUPS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.SKIP_CONNECTION_CHECK;
import static org.keycloak.scim.provisioning.component.DestinationConfig.SUPPORTS_GROUPS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.TOKEN;

/**
 * A destination: one app's SCIM endpoint, token, mapping, scope and deprovision mode, stored as a
 * user-storage component so the admin console renders its form under <i>User federation → Add
 * custom provider → scim-provisioning</i>.
 */
public class DestinationProviderFactory implements UserStorageProviderFactory<DestinationProvider>, EnvironmentDependentProviderFactory {
    private static final Logger log = Logger.getLogger(DestinationProviderFactory.class);

    public static final String ID = "scim-provisioning";

    /** The connection check on save is capped. */
    static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);

    private static final List<ProviderConfigProperty> PROPERTIES =
            ProviderConfigurationBuilder.create()
                    .property()
                    .name(BASE_URL)
                    .label("scimProvisioningBaseUrl")
                    .helpText("scimProvisioningBaseUrlHelp")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .required(true)
                    .add()
                    .property()
                    .name(TOKEN)
                    .label("scimProvisioningToken")
                    .helpText("scimProvisioningTokenHelp")
                    .type(ProviderConfigProperty.PASSWORD)
                    .secret(true)
                    .required(true)
                    .add()
                    .property()
                    .name(SCOPE_GROUPS)
                    .label("scimProvisioningScopeGroups")
                    .helpText("scimProvisioningScopeGroupsHelp")
                    .type(ProviderConfigProperty.MULTIVALUED_STRING_TYPE)
                    .add()
                    .property()
                    .name(SCOPE_EVERYONE)
                    .label("scimProvisioningScopeEveryone")
                    .helpText("scimProvisioningScopeEveryoneHelp")
                    .type(ProviderConfigProperty.BOOLEAN_TYPE)
                    .defaultValue("false")
                    .add()
                    .property()
                    .name(MAPPING)
                    .label("scimProvisioningMapping")
                    .helpText("scimProvisioningMappingHelp")
                    .type(ProviderConfigProperty.MULTIVALUED_STRING_TYPE)
                    .add()
                    .property()
                    .name(DEPROVISION)
                    .label("scimProvisioningDeprovision")
                    .helpText("scimProvisioningDeprovisionHelp")
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .options("deactivate", "delete")
                    .defaultValue("deactivate")
                    .add()
                    .property()
                    .name(SUPPORTS_GROUPS)
                    .label("scimProvisioningSupportsGroups")
                    .helpText("scimProvisioningSupportsGroupsHelp")
                    .type(ProviderConfigProperty.BOOLEAN_TYPE)
                    .defaultValue("false")
                    .add()
                    .property()
                    .name(PROFILE)
                    .label("scimProvisioningProfile")
                    .helpText("scimProvisioningProfileHelp")
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .options(PROFILE_GENERIC)
                    .defaultValue(PROFILE_GENERIC)
                    .add()
                    .property()
                    .name(REQUEST_TIMEOUT_SECONDS)
                    .label("scimProvisioningRequestTimeoutSeconds")
                    .helpText("scimProvisioningRequestTimeoutSecondsHelp")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue(Integer.toString(DEFAULT_REQUEST_TIMEOUT_SECONDS))
                    .add()
                    .property()
                    .name(MAX_ATTEMPTS)
                    .label("scimProvisioningMaxAttempts")
                    .helpText("scimProvisioningMaxAttemptsHelp")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue(Integer.toString(DEFAULT_MAX_ATTEMPTS))
                    .add()
                    .property()
                    .name(RECONCILE_HOURS)
                    .label("scimProvisioningReconcileHours")
                    .helpText("scimProvisioningReconcileHoursHelp")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue(Integer.toString(DEFAULT_RECONCILE_HOURS))
                    .add()
                    .property()
                    .name(SKIP_CONNECTION_CHECK)
                    .label("scimProvisioningSkipConnectionCheck")
                    .helpText("scimProvisioningSkipConnectionCheckHelp")
                    .type(ProviderConfigProperty.BOOLEAN_TYPE)
                    .defaultValue("false")
                    .add()
                    .build();

    @Override
    public DestinationProvider create(KeycloakSession session, ComponentModel model) {
        return new DestinationProvider(session, model);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getHelpText() {
        return "scim-provisioning: outbound SCIM 2.0 provisioning into an app. Users in scope are created,"
                + " updated and deprovisioned in the app; nothing is read from it at login.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return PROPERTIES;
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model)
            throws ComponentValidationException {
        Destination d = Destination.of(model, realm.getId());
        List<String> problems = new ArrayList<>();
        URI uri = null;
        if (d.baseUrl() == null || d.baseUrl().isBlank()) {
            problems.add("baseUrl is required");
        } else {
            try {
                uri = URI.create(d.baseUrl());
                if (uri.getScheme() == null
                        || !(uri.getScheme().equalsIgnoreCase("https")
                                || uri.getScheme().equalsIgnoreCase("http"))
                        || uri.getHost() == null) {
                    problems.add("baseUrl must be an absolute http(s) URL");
                }
            } catch (IllegalArgumentException e) {
                problems.add("baseUrl is not a valid URL");
            }
        }
        if (d.tokenRef() == null || d.tokenRef().isEmpty()) problems.add("token is required");
        problems.addAll(Mapping.validate(d.mappingLines()));
        for (String ref : d.scopeGroups()) {
            if (ScopeEvaluator.resolve(session, realm, ref) == null)
                problems.add("scope group not found: " + ref);
        }
        for (String key : List.of(REQUEST_TIMEOUT_SECONDS, MAX_ATTEMPTS, RECONCILE_HOURS)) {
            String v = model.get(key);
            if (v != null && !v.isBlank()) {
                try {
                    int n = Integer.parseInt(v.strip());
                    if (n < 0 || (n == 0 && !key.equals(RECONCILE_HOURS)))
                        problems.add(key + " must be positive");
                } catch (NumberFormatException e) {
                    problems.add(key + " must be a number");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new ComponentValidationException(
                    "Invalid scim-provisioning destination: " + String.join("; ", problems));
        }
        warnUndeclaredAttributes(session, realm, d);
        if (!d.skipConnectionCheck()) connectionCheck(session, d, uri);
    }

    /** An authenticated GET /Users?count=1, capped at 5 s. */
    private static void connectionCheck(KeycloakSession session, Destination d, URI uri) {
        Optional<String> token = Tokens.resolve(session, d.tokenRef());
        if (token.isEmpty()) {
            throw new ComponentValidationException(
                    "Connection check failed: the token's vault reference cannot be resolved");
        }
        String t = token.get();
        ScimEndpoint client = Endpoints.create(session, uri.toString(), () -> t, CHECK_TIMEOUT);
        try {
            client.check();
        } catch (ScimException e) {
            throw new ComponentValidationException("Connection check failed: " + hint(e));
        }
    }

    private static String hint(ScimException e) {
        String why;
        switch (e.errorClass()) {
            case AUTH_REJECTED:
                why = "the token was rejected";
                break;
            case UNREACHABLE:
                why = "the app could not be reached";
                break;
            case MISCONFIGURED:
                why =
                        e.status() >= 300 && e.status() < 400
                                ? "the base URL redirects (redirects are not followed)"
                                : "the base URL does not look like a SCIM endpoint (no /Users there)";
                break;
            case RATE_LIMITED:
                why = "the app is rate limiting";
                break;
            default:
                why = "the app answered with an error";
        }
        return why + " (" + e.summary() + ")";
    }

    /**
     * {@code attr:} mappings read empty when the realm's user profile does not declare the attribute
     * and unmanaged attributes are off. Warn; do not refuse.
     */
    private static void warnUndeclaredAttributes(
            KeycloakSession session, RealmModel realm, Destination d) {
        List<String> names;
        try {
            names = d.mapping().attributeNames();
        } catch (RuntimeException e) {
            return;
        }
        if (names.isEmpty()) return;
        try {
            UserProfileProvider up = session.getProvider(UserProfileProvider.class);
            if (up == null) return;
            UPConfig config = up.getConfiguration();
            if (config.getUnmanagedAttributePolicy() != null) return;
            Set<String> declared = new HashSet<>();
            if (config.getAttributes() != null)
                config.getAttributes().forEach(a -> declared.add(a.getName()));
            for (String n : names) {
                if (!declared.contains(n)) {
                    log.warnf(
                            "scim-provisioning: destination %s maps attr:%s, which realm %s's user profile does not"
                                    + " declare; Keycloak drops undeclared attributes, so it will be sent empty",
                            d.name(), n, realm.getName());
                }
            }
        } catch (RuntimeException e) {
            log.debug("could not read the user profile configuration", e);
        }
    }

    @Override
    public void onCreate(KeycloakSession session, RealmModel realm, ComponentModel model) {
        session
                .getProvider(ScimProvisioningProvider.class)
                .ensureHealth(realm.getId(), model.getId(), System.currentTimeMillis());
        Lifecycle.created(session, realm, model);
    }

    @Override
    public void onUpdate(
            KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel newModel) {
        Lifecycle.updated(session, realm, oldModel, newModel);
    }

    @Override
    public void preRemove(KeycloakSession session, RealmModel realm, ComponentModel model) {
        Lifecycle.removed(session, realm, model);
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return ScimProvisioning.isEnabled();
    }
}
