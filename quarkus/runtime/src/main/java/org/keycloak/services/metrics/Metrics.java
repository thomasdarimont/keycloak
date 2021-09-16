package org.keycloak.services.metrics;

import io.smallrye.metrics.MetricRegistries;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;

public final class Metrics {

    public static final Metadata SERVER_VERSION = Metadata.builder()
            .withName("keycloak_server_version")
            .withDescription("Keycloak Server Version")
            .withType(MetricType.GAUGE)
            .build();

    public static final Metadata METRICS_REFRESH = Metadata.builder()
            .withName("keycloak_metrics_refresh_total_milliseconds")
            .withDescription("Duration of Keycloak Metrics refresh in milliseconds.")
            .withType(MetricType.GAUGE)
            .build();

    public static final Metadata USERS_TOTAL = Metadata.builder()
            .withName("keycloak_users_total")
            .withDescription("Total users")
            .withType(MetricType.GAUGE)
            .build();

    public static final Metadata CLIENTS_TOTAL = Metadata.builder()
            .withName("keycloak_clients_total")
            .withDescription("Total clients")
            .withType(MetricType.GAUGE)
            .build();

    public static final Metadata GROUPS_TOTAL = Metadata.builder()
            .withName("keycloak_groups_total")
            .withDescription("Total groups")
            .withType(MetricType.GAUGE)
            .build();

    public static final Metadata CLIENT_LOGIN_SUCCESS_TOTAL = Metadata.builder()
            .withName("keycloak_client_login_success_total")
            .withDescription("Total successful client logins")
            .withType(MetricType.COUNTER)
            .build();

    public static final Metadata CLIENT_LOGIN_ERROR_TOTAL = Metadata.builder()
            .withName("keycloak_client_login_error_total")
            .withDescription("Total errors during client logins")
            .withType(MetricType.COUNTER)
            .build();

    public static final Metadata USER_LOGIN_SUCCESS_TOTAL = Metadata.builder()
            .withName("keycloak_user_login_success_total")
            .withDescription("Total successful user logins")
            .withType(MetricType.COUNTER)
            .build();

    public static final Metadata USER_LOGIN_ERROR_TOTAL = Metadata.builder()
            .withName("keycloak_user_login_error_total")
            .withDescription("Total errors during user logins")
            .withType(MetricType.COUNTER)
            .build();

    public static final Metadata USER_LOGOUT_SUCCESS_TOTAL = Metadata.builder()
            .withName("keycloak_user_logout_success_total")
            .withDescription("Total successful user logouts")
            .withType(MetricType.COUNTER)
            .build();

    public static final Metadata USER_LOGOUT_ERROR_TOTAL = Metadata.builder()
            .withName("keycloak_user_logout_error_total")
            .withDescription("Total errors during user logouts")
            .withType(MetricType.COUNTER)
            .build();

    public static final Metadata USER_REGISTER_SUCCESS_TOTAL = Metadata.builder()
            .withName("keycloak_user_register_success_total")
            .withDescription("Total user registrations")
            .withType(MetricType.COUNTER)
            .build();

    public static final Metadata USER_REGISTER_ERROR_TOTAL = Metadata.builder()
            .withName("keycloak_user_register_error_total")
            .withDescription("Total errors during user registrations")
            .withType(MetricType.COUNTER)
            .build();

    public static final Metadata OAUTH_TOKEN_REFRESH_SUCCESS_TOTAL = Metadata.builder()
            .withName("keycloak_oauth_token_refresh_success_total")
            .withDescription("Total token refreshes")
            .withType(MetricType.COUNTER)
            .build();

    public static final Metadata OAUTH_TOKEN_REFRESH_ERROR_TOTAL = Metadata.builder()
            .withName("keycloak_oauth_token_refresh_error_total")
            .withDescription("Total errors during token refreshes")
            .withType(MetricType.COUNTER)
            .build();

    public static final Metadata OAUTH_CODE_TO_TOKEN_SUCCESS_TOTAL = Metadata.builder()
            .withName("keycloak_oauth_code_to_token_success_total")
            .withDescription("Total code to token exchanges")
            .withType(MetricType.COUNTER)
            .build();

    public static final Metadata OAUTH_CODE_TO_TOKEN_ERROR_TOTAL = Metadata.builder()
            .withName("keycloak_oauth_code_to_token_error_total")
            .withDescription("Total errors during code to token exchanges")
            .withType(MetricType.COUNTER)
            .build();

    public static Tag tag(String name, String value) {
        return new Tag(name, value);
    }

    public static MetricRegistry getMetricRegistry() {
        return RegistryHolder.METRIC_REGISTRY;
    }

    static class RegistryHolder {
        private static final MetricRegistry METRIC_REGISTRY = MetricRegistries.get(MetricRegistry.Type.APPLICATION);
    }
}
