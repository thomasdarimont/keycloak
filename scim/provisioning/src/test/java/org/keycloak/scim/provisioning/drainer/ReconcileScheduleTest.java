package org.keycloak.scim.provisioning.drainer;

import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.model.DestinationHealthModel;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scheduled reconcile: every reconcileHours from the last run, first run staggered. */
public class ReconcileScheduleTest {

    private static final long HOUR = 3_600_000L;

    private static Destination destination(String id, String hours) {
        ComponentModel m = new ComponentModel();
        m.setId(id);
        m.setName("d");
        m.setConfig(new MultivaluedHashMap<>());
        if (hours != null) {
            m.getConfig().putSingle("reconcileHours", hours);
        }
        return Destination.of(m, "realm");
    }

    /** Only the two fields the schedule looks at matter here. */
    private record Health(long createdAt, Long lastReconcileAt) implements DestinationHealthModel {
        @Override
        public String getRealmId() {
            return "realm";
        }

        @Override
        public String getDestinationId() {
            return "d";
        }

        @Override
        public long getCreatedAt() {
            return createdAt;
        }

        @Override
        public Long getLastSuccessAt() {
            return null;
        }

        @Override
        public String getLastErrorClass() {
            return null;
        }

        @Override
        public String getLastError() {
            return null;
        }

        @Override
        public Long getFailingSince() {
            return null;
        }

        @Override
        public Long getRetryAt() {
            return null;
        }

        @Override
        public int getConsecutiveFailures() {
            return 0;
        }

        @Override
        public Long getLastReconcileAt() {
            return lastReconcileAt;
        }

        @Override
        public String getLastReconcileStatus() {
            return null;
        }

        @Override
        public String getLastReconcileReport() {
            return null;
        }

        @Override
        public int getMismatches() {
            return 0;
        }
    }

    private static DestinationHealthModel health(long createdAt, Long lastReconcileAt) {
        return new Health(createdAt, lastReconcileAt);
    }

    @Test
    public void dueOneIntervalAfterTheLastRun() {
        Destination d = destination("dest-1", "24");
        long last = 1_000_000_000L;
        assertFalse(DrainScheduler.reconcileDue(health(0, last), d, last + 24 * HOUR - 1));
        assertTrue(DrainScheduler.reconcileDue(health(0, last), d, last + 24 * HOUR));
    }

    @Test
    public void firstRunIsStaggeredByUpToAnHour() {
        long created = 5_000_000_000L;
        Destination a = destination("dest-a", "24");
        long staggerA = Math.floorMod("dest-a".hashCode(), (int) HOUR);
        assertFalse(DrainScheduler.reconcileDue(health(created, null), a, created + 24 * HOUR + staggerA - 1));
        assertTrue(DrainScheduler.reconcileDue(health(created, null), a, created + 24 * HOUR + staggerA));
        assertThat(staggerA, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(HOUR - 1)));
        long staggerB = Math.floorMod("dest-b".hashCode(), (int) HOUR);
        assertNotEquals(staggerA, staggerB);
    }

    @Test
    public void zeroHoursTurnsTheScheduleOff() {
        assertFalse(DrainScheduler.reconcileDue(health(0, null), destination("x", "0"), Long.MAX_VALUE / 2));
    }

    @Test
    public void defaultIsADay() {
        assertEquals(24, destination("x", null).reconcileHours());
    }
}
