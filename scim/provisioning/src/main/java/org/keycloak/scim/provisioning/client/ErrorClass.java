package org.keycloak.scim.provisioning.client;

import java.util.Locale;

/**
 * Why a SCIM call failed. The class decides the retry regime.
 *
 * <p>Transient classes are about the destination: they back the whole destination off and never
 * count against an entry. Terminal classes are about one entry: they count against its attempts
 * and never back the destination off.
 */
public enum ErrorClass {
    /** Connection refused, DNS failure, timeout, I/O error. */
    UNREACHABLE(true),
    /** HTTP 429; honours {@code Retry-After}. */
    RATE_LIMITED(true),
    /** HTTP 408 or 5xx. */
    SERVER_ERROR(true),
    /** HTTP 401, or 403 when the connection check also fails. */
    AUTH_REJECTED(true),
    /**
     * The destination is set up wrong: 404 on a collection ({@code POST /Users}, {@code GET
     * /Users?filter}) or a redirect, which is refused.
     */
    MISCONFIGURED(true),
    /** HTTP 409 that could not be resolved by linking. */
    CONFLICT(false),
    /** Any other 4xx, including 404 on a resource where no recovery applies. */
    REJECTED(false),
    /** An unparseable or nonsensical response. */
    PROTOCOL(false);

    private final boolean transientClass;

    ErrorClass(boolean transientClass) {
        this.transientClass = transientClass;
    }

    /** {@code true} for the destination-level classes that back the destination off. */
    public boolean isTransient() {
        return transientClass;
    }

    /** The wire name used in storage, status output and metrics: {@code rate_limited}, …. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ErrorClass fromCode(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }

    /**
     * Classifies an HTTP status of a failed call.
     *
     * @param collection whether the request addressed a collection ({@code /Users}) rather than a
     *     resource ({@code /Users/{id}})
     */
    public static ErrorClass forStatus(int status, boolean collection) {
        if (status >= 300 && status < 400) return MISCONFIGURED;
        if (status == 401 || status == 403) return AUTH_REJECTED;
        if (status == 404) return collection ? MISCONFIGURED : REJECTED;
        if (status == 408) return SERVER_ERROR;
        if (status == 409) return CONFLICT;
        if (status == 429) return RATE_LIMITED;
        if (status >= 500 && status < 600) return SERVER_ERROR;
        if (status >= 400 && status < 500) return REJECTED;
        return PROTOCOL;
    }
}
