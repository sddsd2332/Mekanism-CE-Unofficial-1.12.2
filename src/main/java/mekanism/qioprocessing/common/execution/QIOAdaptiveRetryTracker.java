package mekanism.qioprocessing.common.execution;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Transient adaptive retry state for QIO jobs that made no progress on their last pass. */
final class QIOAdaptiveRetryTracker {

    static final int MIN_RETRY_TICKS = 5;
    static final int MAX_RETRY_TICKS = 120;

    private final Map<UUID, RetryEntry> entries = new LinkedHashMap<>();

    synchronized boolean shouldAttempt(@Nonnull UUID jobId, long gameTick) {
        RetryEntry entry = entries.get(Objects.requireNonNull(jobId, "jobId"));
        return entry == null || gameTick >= entry.nextAttemptTick;
    }

    synchronized void recordBlocked(@Nonnull UUID jobId, @Nonnull UUID frequencyUUID,
          @Nonnull Collection<UUID> deviceUUIDs, long gameTick) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(frequencyUUID, "frequencyUUID");
        Objects.requireNonNull(deviceUUIDs, "deviceUUIDs");
        RetryEntry previous = entries.get(jobId);
        int delay = previous == null ? MIN_RETRY_TICKS :
              Math.min(MAX_RETRY_TICKS, previous.delayTicks + 1);
        entries.put(jobId, new RetryEntry(frequencyUUID, deviceUUIDs, delay,
              saturatedAdd(gameTick, delay)));
    }

    synchronized void recordProgress(@Nonnull UUID jobId) {
        entries.remove(Objects.requireNonNull(jobId, "jobId"));
    }

    synchronized void wakeJob(@Nonnull UUID jobId) {
        entries.remove(Objects.requireNonNull(jobId, "jobId"));
    }

    synchronized void wakeDevice(@Nonnull UUID deviceUUID) {
        UUID checked = Objects.requireNonNull(deviceUUID, "deviceUUID");
        entries.values().removeIf(entry -> entry.deviceUUIDs.contains(checked));
    }

    synchronized void wakeFrequency(@Nonnull UUID frequencyUUID) {
        UUID checked = Objects.requireNonNull(frequencyUUID, "frequencyUUID");
        entries.values().removeIf(entry -> entry.frequencyUUID.equals(checked));
    }

    synchronized void removeIf(@Nonnull BiPredicate<UUID, UUID> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        Iterator<Map.Entry<UUID, RetryEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RetryEntry> entry = iterator.next();
            if (predicate.test(entry.getValue().frequencyUUID, entry.getKey())) {
                iterator.remove();
            }
        }
    }

    synchronized void clear() {
        entries.clear();
    }

    synchronized int getDelayTicks(UUID jobId) {
        RetryEntry entry = entries.get(jobId);
        return entry == null ? 0 : entry.delayTicks;
    }

    synchronized long getNextAttemptTick(UUID jobId) {
        RetryEntry entry = entries.get(jobId);
        return entry == null ? 0 : entry.nextAttemptTick;
    }

    private static long saturatedAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private static final class RetryEntry {

        private final UUID frequencyUUID;
        private final Set<UUID> deviceUUIDs;
        private final int delayTicks;
        private final long nextAttemptTick;

        private RetryEntry(UUID frequencyUUID, Collection<UUID> deviceUUIDs, int delayTicks,
              long nextAttemptTick) {
            this.frequencyUUID = frequencyUUID;
            this.deviceUUIDs = deviceUUIDs.isEmpty() ? Collections.emptySet() :
                  Collections.unmodifiableSet(new LinkedHashSet<>(deviceUUIDs));
            this.delayTicks = delayTicks;
            this.nextAttemptTick = nextAttemptTick;
        }
    }
}
