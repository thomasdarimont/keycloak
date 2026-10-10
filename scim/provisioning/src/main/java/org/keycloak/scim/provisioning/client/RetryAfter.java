package org.keycloak.scim.provisioning.client;

import java.time.Duration;
import java.util.Optional;

/**
 * Parses {@code Retry-After} (RFC 9110 §10.2.3). Only the delay-seconds form is understood;
 * the HTTP-date form gives empty, and the normal backoff applies.
 */
public final class RetryAfter {
    private RetryAfter() {}

    public static Optional<Duration> parse(String header) {
        if (header == null) return Optional.empty();
        String t = header.strip();
        if (t.isEmpty() || t.length() > 10) return Optional.empty();
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) < '0' || t.charAt(i) > '9') return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(Long.parseLong(t)));
    }
}
