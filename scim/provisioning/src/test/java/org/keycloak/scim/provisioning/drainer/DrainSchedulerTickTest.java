package org.keycloak.scim.provisioning.drainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import org.keycloak.scim.provisioning.Settings;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;

/** The timer callback only hands work over; it returns in well under 50 ms. */
public class DrainSchedulerTickTest {

    /** An executor that only records what it was given. */
    static final class Recording extends AbstractExecutorService {
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    @Test
    public void tickReturnsAtOnceAndCoalesces() {
        Recording ex = new Recording();
        DrainScheduler s = DrainScheduler.forTest(Settings.defaults(), ex);
        long t0 = System.nanoTime();
        s.tick();
        long tookMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(tookMs, lessThan(50L));
        assertThat(ex.tasks, hasSize(1));
        s.tick(); // a scan is already queued: nothing more is submitted
        assertThat(ex.tasks, hasSize(1));
    }
}
