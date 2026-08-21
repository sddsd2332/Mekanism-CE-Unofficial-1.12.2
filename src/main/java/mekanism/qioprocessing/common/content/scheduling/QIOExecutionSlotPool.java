package mekanism.qioprocessing.common.content.scheduling;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Sparse execution-slot ownership; its memory use follows active jobs, not the configured limit. */
/**
 * QIO 处理模块中的 QIOExecutionSlotPool 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOExecutionSlotPool {

    private static final int MAX_PERSISTED_ACTIVE_SLOTS = 1_000_000;

    private final Map<UUID, ActiveExecutionSlot> activeByJob = new LinkedHashMap<>();

    /** 在配置上限内为任务取得或复用执行槽。 */
    @Nullable
    public ActiveExecutionSlot acquire(@Nonnull QIOCraftingJob job, int configuredLimit,
          long schedulerClock) {
        Objects.requireNonNull(job, "job");
        if (configuredLimit <= 0) {
            throw new IllegalArgumentException("configuredLimit must be positive");
        }
        ActiveExecutionSlot existing = activeByJob.get(job.getJobId());
        if (existing != null) {
            return existing;
        }
        if (activeByJob.size() >= configuredLimit) {
            return null;
        }
        ActiveExecutionSlot slot = new ActiveExecutionSlot(UUID.randomUUID(), job.getJobId(),
              schedulerClock, job.getSource());
        job.assignExecutionSlot(slot.getSlotToken(), schedulerClock);
        activeByJob.put(job.getJobId(), slot);
        return slot;
    }

    /** 释放任务持有的执行槽。 */
    public boolean release(@Nonnull QIOCraftingJob job) {
        Objects.requireNonNull(job, "job");
        ActiveExecutionSlot slot = activeByJob.get(job.getJobId());
        if (slot == null) {
            return false;
        }
        job.clearExecutionSlot(slot.getSlotToken());
        activeByJob.remove(job.getJobId());
        return true;
    }

    /** 按任务标识查询活动执行槽。 */
    @Nullable
    public ActiveExecutionSlot get(UUID jobId) {
        return jobId == null ? null : activeByJob.get(jobId);
    }

    /** 返回当前活动槽数量。 */
    public int size() {
        return activeByJob.size();
    }

    /** 返回活动执行槽的只读快照。 */
    @Nonnull
    public Collection<ActiveExecutionSlot> getActiveSlots() {
        return Collections.unmodifiableList(new ArrayList<>(activeByJob.values()));
    }

    /** 将活动执行槽按任务 UUID 排序写入 NBT 列表。 */
    @Nonnull
    public NBTTagList write() {
        NBTTagList list = new NBTTagList();
        activeByJob.values().stream()
              .sorted((left, right) -> left.getOwnerJobId().toString()
                    .compareTo(right.getOwnerJobId().toString()))
              .forEach(slot -> list.appendTag(slot.write()));
        return list;
    }

    /** 从 NBT 读取槽池并拒绝重复任务/重复 token。 */
    @Nonnull
    public static QIOExecutionSlotPool read(@Nonnull NBTTagList list)
          throws QIOProcessingDataException {
        if (list.tagCount() > MAX_PERSISTED_ACTIVE_SLOTS) {
            throw new QIOProcessingDataException("QIO execution slot list exceeds its safety limit");
        }
        QIOExecutionSlotPool pool = new QIOExecutionSlotPool();
        Set<UUID> tokens = new HashSet<>();
        for (int i = 0; i < list.tagCount(); i++) {
            ActiveExecutionSlot slot = ActiveExecutionSlot.read(list.getCompoundTagAt(i));
            if (pool.activeByJob.put(slot.getOwnerJobId(), slot) != null) {
                throw new QIOProcessingDataException("QIO job owns more than one execution slot: " +
                      slot.getOwnerJobId());
            }
            if (!tokens.add(slot.getSlotToken())) {
                throw new QIOProcessingDataException("Duplicate QIO execution slot token: " +
                      slot.getSlotToken());
            }
        }
        return pool;
    }

    /** 校验槽池与任务中的执行槽引用双向一致。 */
    public void validateAgainst(@Nonnull Map<UUID, QIOCraftingJob> jobs)
          throws QIOProcessingDataException {
        Objects.requireNonNull(jobs, "jobs");
        for (ActiveExecutionSlot slot : activeByJob.values()) {
            QIOCraftingJob job = jobs.get(slot.getOwnerJobId());
            if (job == null) {
                throw new QIOProcessingDataException("Execution slot references unknown job " +
                      slot.getOwnerJobId());
            }
            if (job.getState().isTerminal() || !slot.getSlotToken().equals(job.getExecutionSlotToken()) ||
                  slot.getAcquiredAtSchedulerClock() != job.getSlotAcquiredAtSchedulerClock() ||
                  slot.getSource() != job.getSource()) {
                throw new QIOProcessingDataException("Execution slot ownership disagrees with job " +
                      slot.getOwnerJobId());
            }
        }
        for (QIOCraftingJob job : jobs.values()) {
            if (job.getExecutionSlotToken() != null && !activeByJob.containsKey(job.getJobId())) {
                throw new QIOProcessingDataException("Job references a missing execution slot " + job.getJobId());
            }
        }
    }
}
