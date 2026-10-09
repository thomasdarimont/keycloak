package org.keycloak.events.outbox;

import java.time.Duration;

/**
 * Counters for one {@link OutboxDrainerTask} tick, handed to
 * {@link OutboxDrainerListener#onTickEnd}. The row counters cover the
 * drain pass; {@code stalePromoted}, {@code purgedDelivered} and
 * {@code purgedDeadLetter} are the housekeeping steps that follow it.
 */
public record OutboxDrainerTickSummary(String entryKind,
                                       int processed,
                                       int delivered,
                                       int retried,
                                       int deferred,
                                       int deadLettered,
                                       int stalePromoted,
                                       int purgedDelivered,
                                       int purgedDeadLetter,
                                       Duration duration) {
}
