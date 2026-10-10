package org.keycloak.scim.provisioning;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.keycloak.Config;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

/**
 * Node-wide tuning, read once from the event listener's SPI scope:
 * {@code --spi-events-listener--scim-provisioning--<key>=<value>} (keys in {@link #metadata()}). Defaults are
 * listed below. Per-destination settings live in the destination component instead.
 *
 * @param captureEvents {@code false} turns event capture off: no markers from
 *     user, admin or model events; drainer, resync and reconcile keep running
 * @param enqueueMode {@code transactional} (default) or {@code after-commit}
 * @param runTimeBox the budget of one drainer tick; claimed rows it does not reach are handed back
 * @param drainBatch rows one drainer tick claims, across all destinations
 * @param drainPerDestination at most this many of them per destination and round, so one slow or
 *     busy destination cannot take the whole batch
 * @param deliveryConcurrency how many destinations a tick delivers to in parallel; one
 *     destination's rows are always delivered in order, by one worker
 */
public record Settings(
        boolean captureEvents,
        EnqueueMode enqueueMode,
        Duration drainInterval,
        int lockTtlSeconds,
        Duration runTimeBox,
        int drainBatch,
        int drainPerDestination,
        int deliveryConcurrency,
        Duration transientBase,
        Duration transientMax,
        Duration terminalBase,
        Duration terminalMax,
        Duration groupDeferral,
        int maxGroupDeferrals,
        Duration sweepInterval,
        int reconcileLockTtlSeconds) {

    /** Where the listener writes its marker. */
    public enum EnqueueMode {
        /** Inside the user-change transaction: atomic with the change. */
        TRANSACTIONAL,
        /** After the user-change transaction committed, in a transaction of our own. */
        AFTER_COMMIT;

        static EnqueueMode parse(String s) {
            if (s == null) return TRANSACTIONAL;
            String t = s.strip().toLowerCase(Locale.ROOT).replace('_', '-');
            return t.equals("after-commit") ? AFTER_COMMIT : TRANSACTIONAL;
        }

        public String code() {
            return this == AFTER_COMMIT ? "after-commit" : "transactional";
        }
    }

    private static volatile Settings current = defaults();

    public static Settings defaults() {
        return new Settings(
                true,
                EnqueueMode.TRANSACTIONAL,
                Duration.ofSeconds(5),
                60,
                Duration.ofSeconds(30),
                100,
                20,
                4,
                Duration.ofSeconds(2),
                Duration.ofMinutes(10),
                Duration.ofSeconds(30),
                Duration.ofHours(1),
                Duration.ofSeconds(5),
                10,
                Duration.ofMinutes(5),
                3600);
    }

    public static Settings from(Config.Scope c) {
        Settings d = defaults();
        return new Settings(
                c.getBoolean("capture-events", d.captureEvents),
                EnqueueMode.parse(c.get("enqueue-mode", d.enqueueMode.code())),
                millis(c, "drain-interval-millis", d.drainInterval),
                c.getInt("lock-ttl-seconds", d.lockTtlSeconds),
                millis(c, "run-time-box-millis", d.runTimeBox),
                Math.max(1, c.getInt("drain-batch", d.drainBatch)),
                Math.max(1, c.getInt("drain-per-destination", d.drainPerDestination)),
                Math.max(1, c.getInt("delivery-concurrency", d.deliveryConcurrency)),
                millis(c, "transient-backoff-base-millis", d.transientBase),
                millis(c, "transient-backoff-max-millis", d.transientMax),
                millis(c, "terminal-backoff-base-millis", d.terminalBase),
                millis(c, "terminal-backoff-max-millis", d.terminalMax),
                millis(c, "group-deferral-millis", d.groupDeferral),
                c.getInt("max-group-deferrals", d.maxGroupDeferrals),
                millis(c, "sweep-interval-millis", d.sweepInterval),
                c.getInt("reconcile-lock-ttl-seconds", d.reconcileLockTtlSeconds));
    }

    /** The options, as shown by {@code kc.sh show-config} and in the server info. */
    public static List<ProviderConfigProperty> metadata() {
        Settings d = defaults();
        return ProviderConfigurationBuilder.create()
                .property().name("capture-events").type(ProviderConfigProperty.BOOLEAN_TYPE)
                .helpText("Capture user, group and admin events. When off, provisioning follows resync and reconcile only.")
                .defaultValue(d.captureEvents).add()
                .property().name("enqueue-mode").type(ProviderConfigProperty.LIST_TYPE)
                .helpText("Write the outbox entry inside the change's transaction, or after it commits.")
                .options("transactional", "after-commit").defaultValue(d.enqueueMode.code()).add()
                .property().name("drain-interval-millis").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("How often the drainer runs when no change wakes it.")
                .defaultValue(d.drainInterval.toMillis()).add()
                .property().name("lock-ttl-seconds").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("Minimum lease of the drainer's cluster lock; it is held at least as long as the claim lease.")
                .defaultValue(d.lockTtlSeconds).add()
                .property().name("run-time-box-millis").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("Budget of one drainer tick; claimed entries it does not reach are handed back.")
                .defaultValue(d.runTimeBox.toMillis()).add()
                .property().name("drain-batch").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("Entries one drainer tick claims, across all destinations.")
                .defaultValue(d.drainBatch).add()
                .property().name("drain-per-destination").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("At most this many entries of one destination per claim round.")
                .defaultValue(d.drainPerDestination).add()
                .property().name("delivery-concurrency").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("Destinations one tick delivers to in parallel. One destination's entries are always delivered in order.")
                .defaultValue(d.deliveryConcurrency).add()
                .property().name("transient-backoff-base-millis").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("First backoff of a destination that is down, rate limiting or rejecting the token.")
                .defaultValue(d.transientBase.toMillis()).add()
                .property().name("transient-backoff-max-millis").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("Longest backoff of a failing destination.")
                .defaultValue(d.transientMax.toMillis()).add()
                .property().name("terminal-backoff-base-millis").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("First retry delay of a change the destination refused.")
                .defaultValue(d.terminalBase.toMillis()).add()
                .property().name("terminal-backoff-max-millis").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("Longest retry delay of a change the destination refused.")
                .defaultValue(d.terminalMax.toMillis()).add()
                .property().name("group-deferral-millis").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("How long a group waits for its members' accounts.")
                .defaultValue(d.groupDeferral.toMillis()).add()
                .property().name("max-group-deferrals").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("Deferrals before a group is sent without the members that have no account yet.")
                .defaultValue(d.maxGroupDeferrals).add()
                .property().name("sweep-interval-millis").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("How often to check for destinations to reconcile and entries to clean up.")
                .defaultValue(d.sweepInterval.toMillis()).add()
                .property().name("reconcile-lock-ttl-seconds").type(ProviderConfigProperty.INTEGER_TYPE)
                .helpText("Lease of a destination's reconcile lock.")
                .defaultValue(d.reconcileLockTtlSeconds).add()
                .build();
    }

    private static Duration millis(Config.Scope c, String key, Duration d) {
        Long v = c.getLong(key, d.toMillis());
        return Duration.ofMillis(v == null ? d.toMillis() : v);
    }

    public static Settings get() {
        return current;
    }

    public static void set(Settings s) {
        current = s;
    }
}
