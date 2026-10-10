package org.keycloak.scim.provisioning.client;

import java.time.Duration;
import java.util.Optional;

/**
 * A classified SCIM failure.
 *
 * <p>The message carries the class, the HTTP status and the destination's {@code detail}, truncated
 * to {@value #MAX_DETAIL} characters. The detail is text from the destination and can contain
 * personal data: hosts must not log it above DEBUG. Tokens never appear in it.
 */
public final class ScimException extends Exception {
    private static final long serialVersionUID = 1L;

    /** Longest detail kept. */
    public static final int MAX_DETAIL = 300;

    private final ErrorClass errorClass;
    private final int status;
    private final String detail;
    private final String scimType;
    private final transient Duration retryAfter;

    public ScimException(
            ErrorClass errorClass, int status, String detail, String scimType, Duration retryAfter) {
        super(message(errorClass, status, truncate(detail)));
        this.errorClass = errorClass;
        this.status = status;
        this.detail = truncate(detail);
        this.scimType = scimType;
        this.retryAfter = retryAfter;
    }

    public ScimException(ErrorClass errorClass, String detail) {
        this(errorClass, 0, detail, null, null);
    }

    public ErrorClass errorClass() {
        return errorClass;
    }

    /** The HTTP status, or 0 when no response was received. */
    public int status() {
        return status;
    }

    /** The destination's explanation, at most {@value #MAX_DETAIL} characters; may be null. */
    public String detail() {
        return detail;
    }

    /** RFC 7644 §3.12 {@code scimType}, when the destination sent one. */
    public String scimType() {
        return scimType;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    public boolean isTransient() {
        return errorClass.isTransient();
    }

    /** {@code "rate_limited: HTTP 429"}: class and status only, safe for INFO/WARN logs. */
    public String summary() {
        return status > 0 ? errorClass.code() + ": HTTP " + status : errorClass.code();
    }

    /** Text for the {@code last_error} column: summary plus detail, at most 300 characters. */
    public String storedText() {
        String s = detail == null || detail.isBlank() ? summary() : summary() + ": " + detail;
        return truncate(s);
    }

    static String truncate(String s) {
        if (s == null) return null;
        String flat = s.replace('\r', ' ').replace('\n', ' ').strip();
        if (flat.length() <= MAX_DETAIL) return flat;
        int end = MAX_DETAIL;
        if (Character.isHighSurrogate(flat.charAt(end - 1))) end--;
        return flat.substring(0, end);
    }

    private static String message(ErrorClass c, int status, String detail) {
        String s = status > 0 ? c.code() + ": HTTP " + status : c.code();
        return detail == null || detail.isBlank() ? s : s + ": " + detail;
    }
}
