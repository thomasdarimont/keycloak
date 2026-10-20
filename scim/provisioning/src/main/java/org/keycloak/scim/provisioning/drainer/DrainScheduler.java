package org.keycloak.scim.provisioning.drainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.keycloak.events.outbox.OutboxBackoff;
import org.keycloak.events.outbox.OutboxConfig;
import org.keycloak.events.outbox.OutboxDrainerListener;
import org.keycloak.events.outbox.OutboxDrainerRunner;
import org.keycloak.events.outbox.OutboxDrainerTask;
import org.keycloak.events.outbox.OutboxMetricsListener;
import org.keycloak.events.outbox.OutboxStore;
import org.keycloak.executors.ExecutorsProvider;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.scim.provisioning.Settings;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.component.Destinations;
import org.keycloak.scim.provisioning.metrics.Meters;
import org.keycloak.scim.provisioning.model.DestinationHealthModel;
import org.keycloak.scim.provisioning.model.OutboxEntryModel;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;
import org.keycloak.timer.TimerProvider;

import org.jboss.logging.Logger;

/**
 * Schedules drain ticks, sweeps and reconciles.
 *
 * <ul>
 *   <li>The timer callback only hands a scan to the executor and returns: Keycloak's basic timer
 *       runs every task on one shared thread. No network I/O happens on it.
 *   <li>Draining is Keycloak's {@code OutboxDrainerTask} for our entry kind with {@link
 *       ScimDeliveryHandler}: one tick claims up to {@code drainBatch} due rows, at most {@code
 *       drainPerDestination} per destination and round, under a lease, and delivers them within
 *       the {@code runTimeBox} budget, to up to {@code deliveryConcurrency} destinations in
 *       parallel (Keycloak's {@code outbox-delivery-scim-provisioning} executor). The tick runs on our
 *       executor through Keycloak's {@code OutboxDrainerRunner}, under the cluster lock {@code
 *       outbox-drainer:scim-provisioning}, one at a time per node. Keycloak's {@code
 *       OutboxMetricsListener} publishes the generic {@code keycloak.outbox.*} meters for the kind.
 *   <li>A tick is started by the timer and by a commit that wrote markers ({@link #wake}); a tick
 *       that filled its batch or ran out of budget is followed by another at once.
 * </ul>
 */
public final class DrainScheduler {
    private static final Logger log = Logger.getLogger(DrainScheduler.class);

    public static final String EXECUTOR = "scim-provisioning";
    public static final String TIMER_TASK = "scim-provisioning-drain";

    private static volatile DrainScheduler instance;

    private final KeycloakSessionFactory factory;
    private final Settings settings;
    private final ExecutorService executor;
    private final ScimDeliveryHandler handler;
    private final Runnable tick;
    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicBoolean drainAgain = new AtomicBoolean();
    private final Set<String> reconciling = ConcurrentHashMap.newKeySet();
    private final Semaphore reconcileSlot = new Semaphore(1);
    private final AtomicBoolean scanQueued = new AtomicBoolean();
    private final Sweeper sweeper;
    private volatile boolean stopped;

    private DrainScheduler(
            KeycloakSessionFactory factory, Settings settings, ExecutorService executor) {
        this.factory = factory;
        this.settings = settings;
        this.executor = executor;
        this.sweeper = new Sweeper(factory, settings);
        this.handler = new ScimDeliveryHandler(settings);
        if (factory == null) {
            this.tick = () -> {};
        } else {
            OutboxDrainerTask task =
                    new OutboxDrainerTask(
                            config(settings),
                            handler,
                            OutboxStore::new,
                            OutboxDrainerListener.composite(
                                    handler,
                                    new OutboxMetricsListener(
                                            ScimProvisioningProvider.ENTRY_KIND, OutboxStore::new)));
            // no transaction around the tick; the lock lives at least as long as the claim lease
            this.tick = new OutboxDrainerRunner(factory, task, settings.lockTtlSeconds() * 1000L)::run;
        }
    }

    /**
     * Our kind's drainer configuration. A refusal is retried on the terminal backoff curve; the
     * destination's {@code maxAttempts} is applied by the handler, so the kind's own attempt limit is
     * never reached. Pending rows never age out (a transient outage is waited out however long it
     * lasts), and delivered and dead rows are pruned per subject, not by retention.
     */
    static OutboxConfig config(Settings settings) {
        List<Duration> curve = new ArrayList<>();
        Duration d = settings.terminalBase();
        while (d.compareTo(settings.terminalMax()) < 0 && curve.size() < 32) {
            curve.add(d);
            d = d.multipliedBy(2);
        }
        curve.add(settings.terminalMax());
        Duration budget = settings.runTimeBox();
        Duration lease = OutboxConfig.DEFAULT_CLAIM_LEASE;
        if (lease.compareTo(budget.multipliedBy(2)) < 0) lease = budget.multipliedBy(2);
        return new OutboxConfig(
                ScimProvisioningProvider.ENTRY_KIND,
                settings.drainBatch(),
                new OutboxBackoff(Integer.MAX_VALUE, curve),
                null,
                null,
                null,
                lease,
                budget,
                settings.drainPerDestination(),
                settings.deliveryConcurrency());
    }

    public static synchronized void start(KeycloakSessionFactory factory, Settings settings) {
        if (instance != null) return;
        KeycloakModelUtils.runJobInTransaction(
                factory,
                session -> {
                    ExecutorService ex = session.getProvider(ExecutorsProvider.class).getExecutor(EXECUTOR);
                    DrainScheduler s = new DrainScheduler(factory, settings, ex);
                    instance = s;
                    session
                            .getProvider(TimerProvider.class)
                            .schedule(s::tick, settings.drainInterval().toMillis(), TIMER_TASK);
                });
    }

    /** For unit tests of the timer hand-off: no session factory, the given executor. */
    static DrainScheduler forTest(Settings settings, ExecutorService executor) {
        return new DrainScheduler(null, settings, executor);
    }

    public static synchronized void stop() {
        if (instance != null) instance.stopped = true;
        instance = null;
    }

    public static DrainScheduler get() {
        return instance;
    }

    /** Wakes the drainer (after a commit that wrote markers). Coalesced. */
    public static void wake(Set<String> realmIds) {
        DrainScheduler s = instance;
        if (s == null || realmIds.isEmpty()) return;
        s.drain();
    }

    /**
     * Runs drain ticks on the executor, one at a time on this node; a request while one runs makes
     * it go round again.
     */
    void drain() {
        if (stopped) return;
        drainAgain.set(true);
        if (!draining.compareAndSet(false, true)) return;
        submit(
                () -> {
                    try {
                        while (!stopped && drainAgain.getAndSet(false)) {
                            tick.run();
                            if (handler.takeMore()) drainAgain.set(true);
                        }
                    } finally {
                        draining.set(false);
                    }
                    if (drainAgain.get()) drain();
                });
    }

    /** The timer callback: hand over and return. */
    void tick() {
        if (stopped) return;
        if (scanQueued.compareAndSet(false, true)) {
            submit(
                    () -> {
                        scanQueued.set(false);
                        scan(null);
                    });
        }
    }

    private void submit(Runnable r) {
        try {
            executor.execute(
                    () -> {
                        try {
                            r.run();
                        } catch (RuntimeException e) {
                            log.warnf("scim-provisioning: drain task failed: %s", e.toString());
                            log.debug("drain task failure", e);
                        }
                    });
        } catch (RejectedExecutionException e) {
            log.debug("scim-provisioning: executor rejected a drain task (shutting down?)");
        }
    }

    /** Housekeeping, reconciles that are due, backlog gauges; then a drain tick. */
    void scan(Set<String> realms) {
        if (stopped) return;
        sweeper.maybeSweep();
        long now = System.currentTimeMillis();
        List<String[]> reconcile = new ArrayList<>();
        KeycloakModelUtils.runJobInTransaction(
                factory,
                session -> {
                    ScimProvisioningProvider outbox = session.getProvider(ScimProvisioningProvider.class);
                    for (DestinationHealthModel h : outbox.getAllHealth()) {
                        if (realms != null && !realms.contains(h.getRealmId())) continue;
                        RealmModel realm = session.realms().getRealm(h.getRealmId());
                        if (realm == null) continue;
                        Destination d = Destinations.find(realm, h.getDestinationId());
                        if (d == null || !d.enabled()) continue;
                        if (realms == null && reconcileDue(h, d, now))
                            reconcile.add(new String[] {h.getRealmId(), h.getDestinationId()});
                        Meters.backlog(
                                h.getRealmId(),
                                d,
                                outbox.countEntries(
                                        h.getRealmId(), h.getDestinationId(), OutboxEntryModel.PENDING));
                    }
                });
        for (String[] r : reconcile) reconcile(r[0], r[1]);
        drain();
    }

    /**
     * Every {@code reconcileHours}, counted from the last run; the first run of a destination is
     * staggered by up to an hour (by destination id) so destinations created together do not
     * reconcile together.
     */
    static boolean reconcileDue(DestinationHealthModel h, Destination d, long now) {
        if (d.reconcileHours() <= 0) return false;
        long interval = d.reconcileHours() * 3_600_000L;
        if (h.getLastReconcileAt() != null) return now >= h.getLastReconcileAt() + interval;
        long stagger = Math.floorMod(d.id().hashCode(), (int) Math.min(interval, 3_600_000L));
        return now >= h.getCreatedAt() + interval + stagger;
    }

    /** Runs a reconcile on this node, one at a time, unless one is already running here. */
    public void reconcile(String realmId, String destinationId) {
        if (stopped || !reconciling.add(destinationId)) return;
        submit(
                () -> {
                    try {
                        if (!reconcileSlot.tryAcquire()) return;
                        try {
                            org.keycloak.scim.provisioning.reconcile.Reconciler.runLocked(
                                    factory, realmId, destinationId, settings);
                        } finally {
                            reconcileSlot.release();
                        }
                    } finally {
                        reconciling.remove(destinationId);
                    }
                });
    }

    /** Runs a task on the drain executor (reconcile, resync). */
    public void execute(Runnable r) {
        submit(r);
    }

    public KeycloakSessionFactory factory() {
        return factory;
    }

    public Settings settings() {
        return settings;
    }
}
