package org.keycloak.scim.provisioning.component;

/** Component config keys of a destination. */
public final class DestinationConfig {
    public static final String BASE_URL = "baseUrl";
    public static final String TOKEN = "token";
    public static final String PROFILE = "profile";
    public static final String SCOPE_GROUPS = "scopeGroups";
    public static final String SCOPE_EVERYONE = "scopeEveryone";
    public static final String MAPPING = "mapping";
    public static final String DEPROVISION = "deprovision";
    public static final String SUPPORTS_GROUPS = "supportsGroups";
    public static final String REQUEST_TIMEOUT_SECONDS = "requestTimeoutSeconds";
    public static final String MAX_ATTEMPTS = "maxAttempts";
    public static final String RECONCILE_HOURS = "reconcileHours";
    public static final String SKIP_CONNECTION_CHECK = "skipConnectionCheck";

    /** Managed by Keycloak's user-storage model; the console's enabled toggle. */
    public static final String ENABLED = "enabled";

    public static final String PROFILE_GENERIC = "generic";

    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 15;
    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final int DEFAULT_RECONCILE_HOURS = 24;

    private DestinationConfig() {}
}
