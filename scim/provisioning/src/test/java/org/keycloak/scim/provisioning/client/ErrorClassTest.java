package org.keycloak.scim.provisioning.client;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ErrorClassTest {

    @Test
    public void statusClassification() {
        assertEquals(ErrorClass.RATE_LIMITED, ErrorClass.forStatus(429, false));
        assertEquals(ErrorClass.AUTH_REJECTED, ErrorClass.forStatus(401, false));
        assertEquals(ErrorClass.AUTH_REJECTED, ErrorClass.forStatus(403, true));
        assertEquals(ErrorClass.MISCONFIGURED, ErrorClass.forStatus(404, true));
        assertEquals(ErrorClass.REJECTED, ErrorClass.forStatus(404, false));
        assertEquals(ErrorClass.CONFLICT, ErrorClass.forStatus(409, true));
        assertEquals(ErrorClass.REJECTED, ErrorClass.forStatus(400, true));
        assertEquals(ErrorClass.SERVER_ERROR, ErrorClass.forStatus(408, true));
        assertEquals(ErrorClass.SERVER_ERROR, ErrorClass.forStatus(500, true));
        assertEquals(ErrorClass.SERVER_ERROR, ErrorClass.forStatus(503, false));
        assertEquals(ErrorClass.MISCONFIGURED, ErrorClass.forStatus(302, true));
        assertEquals(ErrorClass.PROTOCOL, ErrorClass.forStatus(100, true));
    }

    @Test
    public void transientSplit() {
        List<ErrorClass> transientClasses = Arrays.stream(ErrorClass.values()).filter(ErrorClass::isTransient).toList();
        assertEquals(List.of(
                ErrorClass.UNREACHABLE,
                ErrorClass.RATE_LIMITED,
                ErrorClass.SERVER_ERROR,
                ErrorClass.AUTH_REJECTED,
                ErrorClass.MISCONFIGURED), transientClasses);
        assertEquals("rate_limited", ErrorClass.RATE_LIMITED.code());
        assertEquals(ErrorClass.SERVER_ERROR, ErrorClass.fromCode("server_error"));
    }

    @Test
    public void retryAfterSecondsOnly() {
        assertEquals(Optional.of(Duration.ofSeconds(120)), RetryAfter.parse("120"));
        assertEquals(Optional.of(Duration.ofSeconds(7)), RetryAfter.parse(" 7 "));
        assertEquals(Optional.empty(), RetryAfter.parse("Wed, 21 Oct 2015 07:28:00 GMT"));
        assertEquals(Optional.empty(), RetryAfter.parse("-1"));
        assertEquals(Optional.empty(), RetryAfter.parse(null));
    }

    @Test
    public void detailIsTruncatedAndFlattened() {
        ScimException e = new ScimException(ErrorClass.REJECTED, 400, "x\n".repeat(400), null, null);
        assertEquals(ScimException.MAX_DETAIL, e.detail().length());
        assertThat(e.detail(), not(containsString("\n")));
        assertThat(e.storedText().length(), lessThanOrEqualTo(ScimException.MAX_DETAIL));
        assertEquals("rejected: HTTP 400", e.summary());
    }
}
