package mekanism.qioprocessing.common.maintenance;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOMaintenanceWakeTrackerTest {

    @Test
    void repeatedContentSignalsCoalesceUntilTheSettleDeadline() {
        QIOMaintenanceWakeTracker tracker = tracker(4_096);
        PortableResourceDescriptor resource = resource(0);
        for (int index = 0; index < 10_000; index++) {
            tracker.recordResource(resource, 100 + index / 1_000);
        }
        assertEquals(1, tracker.getDirtyResourceCount());
        assertEquals(111, tracker.getWakeAtTick());
        assertFalse(tracker.consumeIfDue(110, resource::equals));
        assertTrue(tracker.consumeIfDue(111, resource::equals));
        assertFalse(tracker.consumeIfDue(112, resource::equals));
    }

    @Test
    void oversizedBurstFallsBackToOneFullScanUnderFiveSeconds() {
        QIOMaintenanceWakeTracker tracker = tracker(4_096);
        assertTimeout(Duration.ofSeconds(5), () -> {
            for (int index = 0; index < 20_000; index++) {
                tracker.recordResource(resource(index), 20);
            }
        });
        assertTrue(tracker.isFullRescanRequested());
        assertEquals(0, tracker.getDirtyResourceCount());
        assertFalse(tracker.consumeIfDue(21, ignored -> false));
        assertTrue(tracker.consumeIfDue(22, ignored -> false));
    }

    @Test
    void structuralChangesWaitForStabilityAndInvalidationCancelsWake() {
        QIOMaintenanceWakeTracker tracker = tracker(8);
        tracker.requestStructuralScan(50);
        assertEquals(150, tracker.getWakeAtTick());
        assertFalse(tracker.consumeIfDue(149, ignored -> true));
        assertTrue(tracker.consumeIfDue(150, ignored -> false));

        tracker.recordResource(resource(1), 200);
        tracker.invalidate();
        assertTrue(tracker.isInvalidated());
        assertFalse(tracker.consumeIfDue(1_000, ignored -> true));
    }

    @Test
    void continuousContentChangesCannotPostponeEvaluationForever() {
        QIOMaintenanceWakeTracker tracker = tracker(32);
        PortableResourceDescriptor resource = resource(1);
        for (long tick = 100; tick < 120; tick++) {
            tracker.recordResource(resource, tick);
        }
        assertEquals(120, tracker.getWakeAtTick());
        assertFalse(tracker.consumeIfDue(119, ignored -> true));
        QIOMaintenanceWakeTracker.WakeBatch batch = tracker.drainIfDue(120,
              ignored -> true);
        assertFalse(batch.isFullScan());
        assertEquals(java.util.Collections.singletonList(resource), batch.getResources());
    }

    @Test
    void drainReturnsOnlyRelevantDeduplicatedResources() {
        QIOMaintenanceWakeTracker tracker = tracker(8);
        PortableResourceDescriptor first = resource(1);
        PortableResourceDescriptor ignored = resource(2);
        tracker.recordResource(first, 10);
        tracker.recordResource(first, 10);
        tracker.recordResource(ignored, 10);

        QIOMaintenanceWakeTracker.WakeBatch batch = tracker.drainIfDue(12,
              first::equals);
        assertFalse(batch.isFullScan());
        assertEquals(java.util.Collections.singletonList(first), batch.getResources());
        assertTrue(tracker.drainIfDue(13, ignoredResource -> true).isEmpty());
    }

    @Test
    void duplicateAtDirtyLimitDoesNotForceFullScan() {
        QIOMaintenanceWakeTracker tracker = tracker(1);
        PortableResourceDescriptor resource = resource(1);
        tracker.recordResource(resource, 10);
        tracker.recordResource(resource, 11);
        assertFalse(tracker.isFullRescanRequested());
        assertEquals(1, tracker.getDirtyResourceCount());
    }

    private static QIOMaintenanceWakeTracker tracker(int maximumDirtyResources) {
        return new QIOMaintenanceWakeTracker(maximumDirtyResources, 2, 20, 100, 200);
    }

    private static PortableResourceDescriptor resource(int index) {
        return PortableResourceDescriptor.named(PortableResourceDescriptor.Kind.ITEM,
              "stress:wake_" + index, 0, null);
    }
}
