package org.keycloak.scim.provisioning.component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.keycloak.component.ComponentModel;
import org.keycloak.scim.provisioning.delivery.DeprovisionMode;
import org.keycloak.scim.provisioning.mapping.Mapping;

import static org.keycloak.scim.provisioning.component.DestinationConfig.BASE_URL;
import static org.keycloak.scim.provisioning.component.DestinationConfig.DEFAULT_MAX_ATTEMPTS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.DEFAULT_RECONCILE_HOURS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.DEFAULT_REQUEST_TIMEOUT_SECONDS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.DEPROVISION;
import static org.keycloak.scim.provisioning.component.DestinationConfig.ENABLED;
import static org.keycloak.scim.provisioning.component.DestinationConfig.MAPPING;
import static org.keycloak.scim.provisioning.component.DestinationConfig.MAX_ATTEMPTS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.RECONCILE_HOURS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.REQUEST_TIMEOUT_SECONDS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.SCOPE_EVERYONE;
import static org.keycloak.scim.provisioning.component.DestinationConfig.SCOPE_GROUPS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.SKIP_CONNECTION_CHECK;
import static org.keycloak.scim.provisioning.component.DestinationConfig.SUPPORTS_GROUPS;
import static org.keycloak.scim.provisioning.component.DestinationConfig.TOKEN;

/**
 * A typed, immutable view over a destination's {@link ComponentModel}. Holds the raw token
 * reference (possibly {@code ${vault.x}}), never a resolved secret.
 */
public final class Destination {
    private final String id;
    private final String realmId;
    private final String name;
    private final boolean enabled;
    private final String baseUrl;
    private final String tokenRef;
    private final List<String> scopeGroups;
    private final boolean scopeEveryone;
    private final List<String> mappingLines;
    private final DeprovisionMode deprovision;
    private final boolean supportsGroups;
    private final int requestTimeoutSeconds;
    private final int maxAttempts;
    private final int reconcileHours;
    private final boolean skipConnectionCheck;

    private Destination(ComponentModel m, String realmId) {
        this.id = m.getId();
        this.realmId = realmId;
        this.name = m.getName();
        String en = m.get(ENABLED);
        this.enabled = en == null || Boolean.parseBoolean(en);
        this.baseUrl = trim(m.get(BASE_URL));
        this.tokenRef = m.get(TOKEN);
        this.scopeGroups = values(m, SCOPE_GROUPS);
        this.scopeEveryone = Boolean.parseBoolean(m.get(SCOPE_EVERYONE));
        this.mappingLines = values(m, MAPPING);
        this.deprovision = DeprovisionMode.parse(m.get(DEPROVISION));
        this.supportsGroups = Boolean.parseBoolean(m.get(SUPPORTS_GROUPS));
        this.requestTimeoutSeconds =
                positive(m.get(REQUEST_TIMEOUT_SECONDS), DEFAULT_REQUEST_TIMEOUT_SECONDS);
        this.maxAttempts = positive(m.get(MAX_ATTEMPTS), DEFAULT_MAX_ATTEMPTS);
        this.reconcileHours = nonNegative(m.get(RECONCILE_HOURS), DEFAULT_RECONCILE_HOURS);
        this.skipConnectionCheck = Boolean.parseBoolean(m.get(SKIP_CONNECTION_CHECK));
    }

    public static Destination of(ComponentModel m, String realmId) {
        return new Destination(m, realmId);
    }

    public String id() {
        return id;
    }

    public String realmId() {
        return realmId;
    }

    public String name() {
        return name;
    }

    /** {@code enabled=false}: keep marking subjects dirty, do not drain. */
    public boolean enabled() {
        return enabled;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public URI baseUri() {
        return URI.create(baseUrl);
    }

    /** The configured token: a literal or a {@code ${vault.x}} reference. Resolve per use. */
    public String tokenRef() {
        return tokenRef;
    }

    public List<String> scopeGroups() {
        return scopeGroups;
    }

    public boolean scopeEveryone() {
        return scopeEveryone;
    }

    public List<String> mappingLines() {
        return mappingLines;
    }

    /** The parsed mapping overrides; {@link Mapping#NONE} when none are configured. */
    public Mapping mapping() {
        return Mapping.overrides(mappingLines);
    }

    public DeprovisionMode deprovision() {
        return deprovision;
    }

    public boolean supportsGroups() {
        return supportsGroups;
    }

    public Duration requestTimeout() {
        return Duration.ofSeconds(requestTimeoutSeconds);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /** Hours between reconciles; 0 turns the scheduled reconcile off. */
    public int reconcileHours() {
        return reconcileHours;
    }

    public boolean skipConnectionCheck() {
        return skipConnectionCheck;
    }

    /**
     * All values of a multivalued property. Older console versions join values with {@code ##};
     * both forms are accepted.
     */
    static List<String> values(ComponentModel m, String key) {
        List<String> out = new ArrayList<>();
        List<String> raw = m.getConfig() == null ? null : m.getConfig().get(key);
        if (raw == null) return out;
        for (String v : raw) {
            if (v == null) continue;
            for (String part : v.split("##")) {
                if (!part.isBlank()) out.add(part.strip());
            }
        }
        return out;
    }

    private static String trim(String s) {
        return s == null ? null : s.strip();
    }

    private static int positive(String s, int d) {
        try {
            int v = Integer.parseInt(s.strip());
            return v > 0 ? v : d;
        } catch (RuntimeException e) {
            return d;
        }
    }

    private static int nonNegative(String s, int d) {
        try {
            int v = Integer.parseInt(s.strip());
            return v >= 0 ? v : d;
        } catch (RuntimeException e) {
            return d;
        }
    }
}
