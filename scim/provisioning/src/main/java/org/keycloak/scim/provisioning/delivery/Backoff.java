package org.keycloak.scim.provisioning.delivery;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Exponential backoff with jitter: {@code min(base · 2^(n−1), max)}, scaled by a
 * random factor in {@code [0.5, 1.0]}. A {@code Retry-After} longer than the computed delay wins,
 * capped at one hour.
 */
public final class Backoff {
    /** Cap on how long a {@code Retry-After} can hold a destination off. */
    public static final Duration RETRY_AFTER_CAP = Duration.ofHours(1);

    private final Duration base;
    private final Duration max;
    private final DoubleSupplier random;

    /** Transient regime defaults: base 2 s, max 10 min. */
    public static Backoff transientDefaults() {
        return new Backoff(Duration.ofSeconds(2), Duration.ofMinutes(10));
    }

    /** Terminal regime defaults: base 30 s, max 1 h. */
    public static Backoff terminalDefaults() {
        return new Backoff(Duration.ofSeconds(30), Duration.ofHours(1));
    }

    public Backoff(Duration base, Duration max) {
        this(base, max, () -> ThreadLocalRandom.current().nextDouble());
    }

    /**
     * @param random values in {@code [0, 1]}; mapped onto the jitter factor {@code [0.5, 1.0]}
     */
    public Backoff(Duration base, Duration max, DoubleSupplier random) {
        this.base = Objects.requireNonNull(base);
        this.max = Objects.requireNonNull(max);
        this.random = Objects.requireNonNull(random);
        if (base.isNegative() || max.compareTo(base) < 0) {
            throw new IllegalArgumentException("need 0 <= base <= max");
        }
    }

    /** The delay before attempt {@code n + 1} after {@code n} consecutive failures ({@code n ≥ 1}). */
    public Duration delay(int n) {
        int exp = Math.max(0, Math.min(n - 1, 40));
        long baseMs = base.toMillis();
        long maxMs = max.toMillis();
        long raw = baseMs > (maxMs >> exp) ? maxMs : Math.min(maxMs, baseMs << exp);
        double r = Math.min(1.0, Math.max(0.0, random.getAsDouble()));
        double factor = 0.5 + 0.5 * r;
        return Duration.ofMillis(Math.round(raw * factor));
    }

    /** As {@link #delay(int)}, but a longer {@code Retry-After} wins, capped at one hour. */
    public Duration delay(int n, Optional<Duration> retryAfter) {
        Duration computed = delay(n);
        if (retryAfter.isPresent() && retryAfter.get().compareTo(computed) > 0) {
            Duration ra = retryAfter.get();
            return ra.compareTo(RETRY_AFTER_CAP) > 0 ? RETRY_AFTER_CAP : ra;
        }
        return computed;
    }
}
