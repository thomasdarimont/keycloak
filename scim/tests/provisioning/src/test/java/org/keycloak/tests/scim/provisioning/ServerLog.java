package org.keycloak.tests.scim.provisioning;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.server.KeycloakServerConfig;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * The managed Keycloak server's log, as the test framework forwards it (every line of the server's output goes to the
 * {@code managed.keycloak} logger of the test JVM; a cluster node's to {@code managed.keycloak.<n>}). Collected for the whole JVM, so the very
 * first startup on a fresh database is included, and split into segments per test class, so a test can look at the
 * lines of its own server only.
 *
 * <p>Registered on {@link AbstractScimProvisioningTest}; its {@code beforeEach} runs before the framework's, which is
 * where the server is (re)started for a class.
 */
public final class ServerLog implements BeforeEachCallback {

    private static final String CATEGORY = "managed.keycloak";
    private static final int MAX_LINES = 2_000_000;

    /** Held, so the logger (and the handler on it) is not garbage collected while nothing else refers to it. */
    private static final Logger LOGGER = Logger.getLogger(CATEGORY);
    private static final List<String> LINES = new ArrayList<>();
    private static final List<Segment> SEGMENTS = new ArrayList<>();
    private static final Handler HANDLER = new Handler() {
        @Override
        public void publish(LogRecord record) {
            String message = record.getMessage();
            if (message == null) {
                return;
            }
            synchronized (LINES) {
                if (LINES.size() >= MAX_LINES) {
                    // keep indexes stable: segments point into the list
                    return;
                }
                LINES.add(message);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    /** The lines from {@code from} on, written while a server of {@code config} served the tests of {@code testClass}. */
    private record Segment(int from, Class<?> testClass, Class<? extends KeycloakServerConfig> config) {
    }

    static {
        install();
    }

    private static void install() {
        Logger logger = LOGGER;
        boolean attached = false;
        for (Handler h : logger.getHandlers()) {
            attached |= h == HANDLER;
        }
        if (!attached) {
            logger.addHandler(HANDLER);
        }
        // the framework logs a distribution server's lines at INFO, a cluster node's standard output at DEBUG
        Level level = logger.getLevel();
        if (level == null || level.intValue() > Level.FINE.intValue()) {
            logger.setLevel(Level.FINE);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        // the framework (re)configures logging (levels too) when it starts: make sure we are still attached
        install();
        Class<?> testClass = context.getRequiredTestClass();
        synchronized (LINES) {
            Segment last = SEGMENTS.isEmpty() ? null : SEGMENTS.get(SEGMENTS.size() - 1);
            if (last == null || last.testClass() != testClass) {
                KeycloakIntegrationTest it = testClass.getAnnotation(KeycloakIntegrationTest.class);
                SEGMENTS.add(new Segment(last == null ? 0 : LINES.size(), testClass, it == null ? null : it.config()));
            }
        }
    }

    /** Every line since the JVM started. */
    public static List<String> all() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }

    /** The lines since the current test class started, including its server's (re)start when it needed one. */
    public static List<String> sinceClassStart() {
        synchronized (LINES) {
            int from = SEGMENTS.isEmpty() ? 0 : SEGMENTS.get(SEGMENTS.size() - 1).from();
            return new ArrayList<>(LINES.subList(from, LINES.size()));
        }
    }

    /** Every line except those written while a test class with server config {@code config} ran. */
    public static List<String> excluding(Class<? extends KeycloakServerConfig> config) {
        synchronized (LINES) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < SEGMENTS.size(); i++) {
                Segment s = SEGMENTS.get(i);
                int to = i + 1 < SEGMENTS.size() ? SEGMENTS.get(i + 1).from() : LINES.size();
                if (s.config() != config) {
                    out.addAll(LINES.subList(s.from(), to));
                }
            }
            return out;
        }
    }

    /** The full log lines (with a level) at INFO or above. */
    public static boolean isInfoOrAbove(String line) {
        return line.contains(" INFO ") || line.contains(" WARN ") || line.contains(" ERROR ") || line.contains(" FATAL ");
    }
}
