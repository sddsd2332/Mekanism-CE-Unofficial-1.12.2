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
/**
 * QIO 处理模块中的 QIOAdaptiveRetryTracker 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
final class QIOAdaptiveRetryTracker {

    static final int MIN_RETRY_TICKS = 5;
    static final int MAX_RETRY_TICKS = 120;

    private final Map<UUID, RetryEntry> entries = new LinkedHashMap<>();

    /** 判断任务在当前 tick 是否到达下一次尝试时间。 */
    synchronized boolean shouldAttempt(@Nonnull UUID jobId, long gameTick) {
        RetryEntry entry = entries.get(Objects.requireNonNull(jobId, "jobId"));
        return entry == null || gameTick >= entry.nextAttemptTick;
    }

    /** 记录一次无进展并按退避策略增加重试延迟。 */
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

    /** 记录任务取得进展并清除退避状态。 */
    synchronized void recordProgress(@Nonnull UUID jobId) {
        entries.remove(Objects.requireNonNull(jobId, "jobId"));
    }

    /** 立即唤醒指定任务。 */
    synchronized void wakeJob(@Nonnull UUID jobId) {
        entries.remove(Objects.requireNonNull(jobId, "jobId"));
    }

    /** 设备状态变化时唤醒使用该设备的任务。 */
    synchronized void wakeDevice(@Nonnull UUID deviceUUID) {
        UUID checked = Objects.requireNonNull(deviceUUID, "deviceUUID");
        entries.values().removeIf(entry -> entry.deviceUUIDs.contains(checked));
    }

    /** 频率内容变化时唤醒该频率下的任务。 */
    synchronized void wakeFrequency(@Nonnull UUID frequencyUUID) {
        UUID checked = Objects.requireNonNull(frequencyUUID, "frequencyUUID");
        entries.values().removeIf(entry -> entry.frequencyUUID.equals(checked));
    }

    /** 按频率 UUID 和任务 UUID 条件删除失效重试记录。 */
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

    /** 清空所有临时重试状态。 */
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
