package org.keycloak.scim.provisioning.delivery;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BackoffTest {

    private static Backoff fixed(double r) {
        return new Backoff(Duration.ofSeconds(2), Duration.ofMinutes(10), () -> r);
    }

    @Test
    public void growsExponentially() {
        Backoff b = fixed(1.0);
        assertEquals(Duration.ofSeconds(2), b.delay(1));
        assertEquals(Duration.ofSeconds(4), b.delay(2));
        assertEquals(Duration.ofSeconds(8), b.delay(3));
        assertEquals(Duration.ofSeconds(512), b.delay(9));
    }

    @Test
    public void isCapped() {
        Backoff b = fixed(1.0);
        assertEquals(Duration.ofMinutes(10), b.delay(10));
        assertEquals(Duration.ofMinutes(10), b.delay(1000));
        assertEquals(Duration.ofMinutes(10), b.delay(Integer.MAX_VALUE));
    }

    @Test
    public void jitterStaysWithinHalfToOne() {
        assertEquals(Duration.ofSeconds(4), fixed(0.0).delay(3));
        assertEquals(Duration.ofSeconds(6), fixed(0.5).delay(3));
        Backoff random = new Backoff(Duration.ofSeconds(2), Duration.ofMinutes(10));
        for (int i = 0; i < 1000; i++) {
            Duration d = random.delay(3);
            assertThat(d, allOf(greaterThanOrEqualTo(Duration.ofSeconds(4)), lessThanOrEqualTo(Duration.ofSeconds(8))));
        }
    }

    @Test
    public void longerRetryAfterWins() {
        Backoff b = fixed(1.0);
        assertEquals(Duration.ofSeconds(120), b.delay(1, Optional.of(Duration.ofSeconds(120))));
        // shorter Retry-After: the computed backoff stands
        assertEquals(Duration.ofSeconds(512), b.delay(9, Optional.of(Duration.ofSeconds(1))));
        assertEquals(Duration.ofSeconds(2), b.delay(1, Optional.empty()));
    }

    @Test
    public void retryAfterIsCappedAtOneHour() {
        assertEquals(Duration.ofHours(1), fixed(1.0).delay(1, Optional.of(Duration.ofDays(2))));
    }

    @Test
    public void defaults() {
        assertEquals(Duration.ofSeconds(30), new Backoff(Duration.ofSeconds(30), Duration.ofHours(1), () -> 1.0).delay(1));
        assertThat(Backoff.terminalDefaults().delay(20), lessThanOrEqualTo(Duration.ofHours(1)));
        assertThat(Backoff.transientDefaults().delay(1),
                allOf(greaterThanOrEqualTo(Duration.ofSeconds(1)), lessThanOrEqualTo(Duration.ofSeconds(2))));
    }
}
