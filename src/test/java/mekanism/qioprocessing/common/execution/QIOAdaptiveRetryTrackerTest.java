package mekanism.qioprocessing.common.execution;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOAdaptiveRetryTrackerTest {

    @Test
    void blockedRetriesBackOffFromFiveToOneHundredTwentyTicks() {
        QIOAdaptiveRetryTracker tracker = new QIOAdaptiveRetryTracker();
        UUID frequency = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        long tick = 10;

        tracker.recordBlocked(job, frequency, Collections.singleton(device), tick);
        assertEquals(5, tracker.getDelayTicks(job));
        assertEquals(15, tracker.getNextAttemptTick(job));
        assertFalse(tracker.shouldAttempt(job, 14));
        assertTrue(tracker.shouldAttempt(job, 15));

        tick = 15;
        tracker.recordBlocked(job, frequency, Collections.singleton(device), tick);
        assertEquals(6, tracker.getDelayTicks(job));
        assertEquals(21, tracker.getNextAttemptTick(job));
        for (int delay = 7; delay <= QIOAdaptiveRetryTracker.MAX_RETRY_TICKS; delay++) {
            tick = tracker.getNextAttemptTick(job);
            tracker.recordBlocked(job, frequency, Collections.singleton(device), tick);
        }
        assertEquals(QIOAdaptiveRetryTracker.MAX_RETRY_TICKS,
              tracker.getDelayTicks(job));
        tick = tracker.getNextAttemptTick(job);
        tracker.recordBlocked(job, frequency, Collections.singleton(device), tick);
        assertEquals(QIOAdaptiveRetryTracker.MAX_RETRY_TICKS,
              tracker.getDelayTicks(job));
    }

    @Test
    void deviceAndStorageEventsWakeOnlyRelevantJobs() {
        QIOAdaptiveRetryTracker tracker = new QIOAdaptiveRetryTracker();
        UUID firstFrequency = UUID.randomUUID();
        UUID secondFrequency = UUID.randomUUID();
        UUID firstJob = UUID.randomUUID();
        UUID secondJob = UUID.randomUUID();
        UUID firstDevice = UUID.randomUUID();
        UUID secondDevice = UUID.randomUUID();

        tracker.recordBlocked(firstJob, firstFrequency, Collections.singleton(firstDevice), 0);
        tracker.recordBlocked(secondJob, secondFrequency, Collections.singleton(secondDevice), 0);
        tracker.wakeDevice(firstDevice);
        assertTrue(tracker.shouldAttempt(firstJob, 0));
        assertFalse(tracker.shouldAttempt(secondJob, 0));

        tracker.wakeFrequency(secondFrequency);
        assertTrue(tracker.shouldAttempt(secondJob, 0));
    }

    @Test
    void orphanCleanupAndProgressRemoveRetryState() {
        QIOAdaptiveRetryTracker tracker = new QIOAdaptiveRetryTracker();
        UUID frequency = UUID.randomUUID();
        UUID firstJob = UUID.randomUUID();
        UUID secondJob = UUID.randomUUID();
        tracker.recordBlocked(firstJob, frequency, Collections.emptySet(), 0);
        tracker.recordBlocked(secondJob, frequency, Collections.emptySet(), 0);

        tracker.recordProgress(firstJob);
        tracker.removeIf((entryFrequency, jobId) -> jobId.equals(secondJob));

        assertTrue(tracker.shouldAttempt(firstJob, 0));
        assertTrue(tracker.shouldAttempt(secondJob, 0));
    }

    @Test
    void directJobWakeBypassesTheMaximumCancellationDelay() {
        QIOAdaptiveRetryTracker tracker = new QIOAdaptiveRetryTracker();
        UUID frequency = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        for (int delay = QIOAdaptiveRetryTracker.MIN_RETRY_TICKS;
              delay <= QIOAdaptiveRetryTracker.MAX_RETRY_TICKS; delay++) {
            tracker.recordBlocked(job, frequency, Collections.emptySet(),
                  tracker.getNextAttemptTick(job));
        }
        assertEquals(QIOAdaptiveRetryTracker.MAX_RETRY_TICKS,
              tracker.getDelayTicks(job));
        assertFalse(tracker.shouldAttempt(job, 0));

        tracker.wakeJob(job);

        assertTrue(tracker.shouldAttempt(job, 0));
        assertEquals(0, tracker.getDelayTicks(job));
    }
}
