package org.keycloak.tests.events.outbox;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.events.outbox.OutboxBackoff;
import org.keycloak.events.outbox.OutboxConfig;
import org.keycloak.events.outbox.OutboxDeliveryHandler;
import org.keycloak.events.outbox.OutboxDeliveryResult;
import org.keycloak.events.outbox.OutboxDrainerListener;
import org.keycloak.events.outbox.OutboxDrainerTask;
import org.keycloak.events.outbox.OutboxDrainerTickSummary;
import org.keycloak.events.outbox.OutboxStore;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.jpa.entities.OutboxEntryEntity;
import org.keycloak.models.jpa.entities.OutboxEntryStatus;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.remote.runonserver.InjectRunOnServer;
import org.keycloak.testframework.remote.runonserver.RunOnServerClient;
import org.keycloak.testframework.server.DefaultKeycloakServerConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link OutboxDrainerTask}: one tick is run
 * directly against persisted rows with a scripted
 * {@link OutboxDeliveryHandler} that doubles as
 * {@link OutboxDrainerListener}, and the resulting row states and
 * listener callbacks are checked. Uses the synthetic
 * {@code "test-kind"} like {@link OutboxStoreTests}.
 */
@KeycloakIntegrationTest(config = OutboxDrainerTaskTests.OutboxDrainerServerConfig.class)
public class OutboxDrainerTaskTests {

    private static final String TEST_KIND = "test-kind";

    @InjectRunOnServer
    RunOnServerClient runOnServer;

    private final String testRealmId = UUID.randomUUID().toString();

    @AfterEach
    public void cleanupRealm() {
        final String realmId = testRealmId;
        runOnServer.run(session -> new OutboxStore(session).deleteByRealm(TEST_KIND, realmId));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void tick_transitionsRowsPerOutcomeAndNotifiesListener() {
        final String realmId = testRealmId;
        Map<String, Object> result = runOnServer.fetch(session -> {
            Instant due = Instant.now().minusSeconds(1);
            // correlationId doubles as the scripted outcome, see ScriptedHandler.
            persistRow(session, realmId, "deliver", 0, due);
            persistRow(session, realmId, "retry", 0, due);
            persistRow(session, realmId, "retry-last", 1, due);   // maxAttempts=2 → exhausted
            persistRow(session, realmId, "defer", 1, due);
            persistRow(session, realmId, "dead", 0, due);
            persistRow(session, realmId, "orphan", 0, due);
            persistRow(session, realmId, "throw", 0, due);
            persistRow(session, realmId, "not-due", 0, Instant.now().plus(Duration.ofHours(1)));
            em(session).flush();
            em(session).clear();

            ScriptedHandler handler = new ScriptedHandler();
            OutboxConfig config = new OutboxConfig(TEST_KIND, 50,
                    new OutboxBackoff(2, List.of(Duration.ofMinutes(5))), null, null, null);
            OutboxDrainerTask task = new OutboxDrainerTask(config, handler, OutboxStore::new);
            task.run(session);
            em(session).flush();
            em(session).clear();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("taskName", task.getTaskName());
            for (OutboxEntryEntity row : new OutboxStore(session).listByOwner(TEST_KIND, "owner-drain", null, 0, 50)) {
                out.put("status." + row.getCorrelationId(), row.getStatus().name());
                out.put("attempts." + row.getCorrelationId(), row.getAttempts());
                out.put("lastError." + row.getCorrelationId(), row.getLastError());
                out.put("dueLater." + row.getCorrelationId(), row.getNextAttemptAt().isAfter(Instant.now()));
            }
            out.put("events", handler.events);
            out.put("summary", handler.summary);
            return out;
        }, Map.class);

        Assertions.assertEquals("outbox-drainer:test-kind", result.get("taskName"));

        Assertions.assertEquals("DELIVERED", result.get("status.deliver"));
        Assertions.assertEquals(1, result.get("attempts.deliver"));

        Assertions.assertEquals("PENDING", result.get("status.retry"));
        Assertions.assertEquals(1, result.get("attempts.retry"));
        Assertions.assertEquals(true, result.get("dueLater.retry"), "retry is pushed out by the backoff curve");
        Assertions.assertEquals("scripted retry", result.get("lastError.retry"));

        Assertions.assertEquals("DEAD_LETTER", result.get("status.retry-last"), "second failure exhausts maxAttempts=2");
        Assertions.assertEquals(2, result.get("attempts.retry-last"));

        Assertions.assertEquals("PENDING", result.get("status.defer"));
        Assertions.assertEquals(1, result.get("attempts.defer"), "DEFER must not count an attempt");
        Assertions.assertEquals(true, result.get("dueLater.defer"));
        Assertions.assertEquals("scripted defer", result.get("lastError.defer"));

        Assertions.assertEquals("DEAD_LETTER", result.get("status.dead"));
        Assertions.assertEquals("DEAD_LETTER", result.get("status.orphan"));

        Assertions.assertEquals("PENDING", result.get("status.throw"), "a throwing handler is treated as RETRY");
        Assertions.assertEquals(1, result.get("attempts.throw"));
        Assertions.assertTrue(((String) result.get("lastError.throw")).startsWith("IllegalStateException"));

        Assertions.assertEquals("PENDING", result.get("status.not-due"));
        Assertions.assertEquals(0, result.get("attempts.not-due"), "rows that are not due are not touched");

        List<String> events = (List<String>) result.get("events");
        Assertions.assertEquals("tickStart:test-kind", events.get(0));
        Assertions.assertEquals("tickEnd", events.get(events.size() - 1));
        Assertions.assertTrue(events.contains("delivered:deliver"), events.toString());
        Assertions.assertTrue(events.contains("retry:retry"), events.toString());
        Assertions.assertTrue(events.contains("retry:throw"), events.toString());
        Assertions.assertTrue(events.contains("deferred:defer"), events.toString());
        Assertions.assertTrue(events.contains("deadLetter:retry-last:ATTEMPTS_EXHAUSTED"), events.toString());
        Assertions.assertTrue(events.contains("deadLetter:dead:HANDLER"), events.toString());
        Assertions.assertTrue(events.contains("deadLetter:orphan:ORPHANED"), events.toString());
        Assertions.assertFalse(events.stream().anyMatch(e -> e.endsWith(":not-due")), events.toString());

        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        Assertions.assertEquals(TEST_KIND, summary.get("entryKind"));
        Assertions.assertEquals(7, summary.get("processed"));
        Assertions.assertEquals(1, summary.get("delivered"));
        Assertions.assertEquals(2, summary.get("retried"));
        Assertions.assertEquals(1, summary.get("deferred"));
        Assertions.assertEquals(3, summary.get("deadLettered"));
        Assertions.assertEquals(0, summary.get("stalePromoted"));
    }

    @Test
    public void tick_survivesAThrowingListener() {
        final String realmId = testRealmId;
        String status = runOnServer.fetch(session -> {
            OutboxEntryEntity row = persistRow(session, realmId, "deliver", 0, Instant.now().minusSeconds(1));
            em(session).flush();
            em(session).clear();

            OutboxDrainerListener broken = new BrokenListener();
            OutboxConfig config = new OutboxConfig(TEST_KIND, 50, new OutboxBackoff(), null, null, null);
            new OutboxDrainerTask(config, new ScriptedHandler(), OutboxStore::new, broken).run(session);
            em(session).flush();
            em(session).clear();
            return em(session).find(OutboxEntryEntity.class, row.getId()).getStatus().name();
        }, String.class);

        Assertions.assertEquals("DELIVERED", status, "a listener failure must not affect the row transition");
    }

    // -- scripted collaborators (Serializable: they travel inside the lambda) --

    /**
     * Maps the row's correlationId to an outcome and records every
     * listener callback into {@link #events}.
     */
    public static class ScriptedHandler implements OutboxDeliveryHandler, OutboxDrainerListener, Serializable {

        final List<String> events = new ArrayList<>();
        final Map<String, Object> summary = new LinkedHashMap<>();

        @Override
        public String entryKind() {
            return TEST_KIND;
        }

        @Override
        public OutboxDeliveryResult deliver(KeycloakSession session, OutboxEntryEntity row) {
            return switch (row.getCorrelationId()) {
                case "deliver" -> OutboxDeliveryResult.delivered();
                case "retry", "retry-last" -> OutboxDeliveryResult.retry("scripted retry");
                case "defer" -> OutboxDeliveryResult.defer(Duration.ofMinutes(10), "scripted defer");
                case "dead" -> OutboxDeliveryResult.deadLetter("scripted dead letter");
                case "orphan" -> OutboxDeliveryResult.orphaned("scripted orphan");
                case "throw" -> throw new IllegalStateException("scripted throw");
                default -> throw new AssertionError("unexpected row " + row.getCorrelationId());
            };
        }

        @Override
        public void onTickStart(KeycloakSession session, String entryKind) {
            events.add("tickStart:" + entryKind);
        }

        @Override
        public void onTickEnd(KeycloakSession session, OutboxDrainerTickSummary s) {
            events.add("tickEnd");
            summary.put("entryKind", s.entryKind());
            summary.put("processed", s.processed());
            summary.put("delivered", s.delivered());
            summary.put("retried", s.retried());
            summary.put("deferred", s.deferred());
            summary.put("deadLettered", s.deadLettered());
            summary.put("stalePromoted", s.stalePromoted());
        }

        @Override
        public void onDelivered(KeycloakSession session, OutboxEntryEntity row) {
            events.add("delivered:" + row.getCorrelationId());
        }

        @Override
        public void onRetryScheduled(KeycloakSession session, OutboxEntryEntity row, Instant nextAttemptAt, String reason) {
            events.add("retry:" + row.getCorrelationId());
        }

        @Override
        public void onDeferred(KeycloakSession session, OutboxEntryEntity row, Instant notBefore, String reason) {
            events.add("deferred:" + row.getCorrelationId());
        }

        @Override
        public void onDeadLetter(KeycloakSession session, OutboxEntryEntity row, DeadLetterCause cause, String reason) {
            events.add("deadLetter:" + row.getCorrelationId() + ":" + cause);
        }
    }

    public static class BrokenListener implements OutboxDrainerListener, Serializable {

        @Override
        public void onTickStart(KeycloakSession session, String entryKind) {
            throw new IllegalStateException("listener failure on tick start");
        }

        @Override
        public void onDelivered(KeycloakSession session, OutboxEntryEntity row) {
            throw new IllegalStateException("listener failure on delivered");
        }
    }

    private static EntityManager em(KeycloakSession session) {
        return session.getProvider(JpaConnectionProvider.class).getEntityManager();
    }

    private static OutboxEntryEntity persistRow(KeycloakSession session, String realmId, String correlationId,
                                                int attempts, Instant nextAttemptAt) {
        OutboxEntryEntity e = new OutboxEntryEntity();
        e.setId(UUID.randomUUID().toString());
        e.setEntryKind(TEST_KIND);
        e.setRealmId(realmId);
        e.setOwnerId("owner-drain");
        e.setCorrelationId(correlationId);
        e.setEntryType("test.event");
        e.setPayload("encoded-" + correlationId);
        e.setStatus(OutboxEntryStatus.PENDING);
        e.setAttempts(attempts);
        e.setNextAttemptAt(nextAttemptAt);
        e.setCreatedAt(Instant.now().minusSeconds(5));
        em(session).persist(e);
        return e;
    }

    public static class OutboxDrainerServerConfig extends DefaultKeycloakServerConfig {
    }
}
