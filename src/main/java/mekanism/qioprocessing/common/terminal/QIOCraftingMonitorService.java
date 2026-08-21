package mekanism.qioprocessing.common.terminal;

import mekanism.common.config.MekanismConfig;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOOperationAssignment;
import mekanism.qioprocessing.common.content.job.QIOCycleRuntime;
import mekanism.qioprocessing.common.content.job.QIOStepRuntime;
import mekanism.qioprocessing.common.content.material.QIOMaterialCommitment;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * QIO 处理模块中的 QIOCraftingMonitorService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOCraftingMonitorService {

    public enum CancelStatus {
        ACCEPTED,
        UNCHANGED,
        REVISION_CONFLICT,
        PERSISTENCE_ERROR,
        NOT_FOUND
    }

    public enum MutationStatus {
        ACCEPTED,
        UNCHANGED,
        REVISION_CONFLICT,
        INVALID_STATE,
        NOT_FOUND
    }

    private QIOCraftingMonitorService() {
    }

    @Nonnull
    /** 查询合成监控任务分页。 */
    public static QIOPage<QIOCraftingMonitorEntry> getPage(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nullable QIOPageCursor cursor, int requestedPageSize) {
        validateSession(session, network, currentAccessRevision);
        List<QIOCraftingJob> jobs = orderedJobs(network);
        List<QIOCraftingMonitorEntry> entries = snapshot(network, jobs);
        return QIOPagination.page(entries, listRevision(jobs),
              session.getSessionNonce(), cursor, requestedPageSize,
              MekanismConfig.current().qioProcessing.terminalPageSize.val());
    }

    @Nonnull
    /** 取消指定合成任务。 */
    public static CancelStatus cancel(@Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nonnull UUID jobId, long expectedRuntimeRevision) {
        validateSession(session, network, currentAccessRevision);
        QIOCraftingJob job = network.getJob(Objects.requireNonNull(jobId, "jobId"));
        if (job == null) return CancelStatus.NOT_FOUND;
        if (job.getRuntimeRevision() != expectedRuntimeRevision) {
            return CancelStatus.REVISION_CONFLICT;
        }
        if (!network.requestJobCancellation(jobId)) {
            return CancelStatus.UNCHANGED;
        }
        QIOProcessingExecutionService.INSTANCE.wakeJob(jobId);
        return CancelStatus.ACCEPTED;
    }

    @Nonnull
    /** 查询任务计划步骤分页。 */
    public static QIOPage<QIOCraftingMonitorPlanEntry> getPlanPage(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nonnull UUID jobId, int expectedPlanRevision,
          @Nullable QIOPageCursor cursor, int requestedPageSize) {
        validateSession(session, network, currentAccessRevision);
        QIOCraftingJob job = network.getJob(Objects.requireNonNull(jobId, "jobId"));
        if (job == null) throw new IllegalArgumentException("Unknown QIO job " + jobId);
        if (job.getActivePlan().getRevision() != expectedPlanRevision) {
            throw new IllegalStateException("QIO job plan revision changed");
        }
        List<QIOCraftingMonitorPlanEntry> entries = new ArrayList<>();
        job.getActivePlan().getSteps().forEach(step ->
              entries.add(QIOCraftingMonitorPlanEntry.step(step)));
        job.getActivePlan().getCycleNodes().forEach(cycle ->
              entries.add(QIOCraftingMonitorPlanEntry.cycle(cycle)));
        entries.sort(Comparator.comparingLong(QIOCraftingMonitorPlanEntry::getNodeId));
        return QIOPagination.page(entries, planSourceRevision(
                    job.getActivePlan().getStructuralSignature()), session.getSessionNonce(), cursor,
              requestedPageSize, MekanismConfig.current().qioProcessing.terminalPageSize.val());
    }

    @Nonnull
    /** 查询任务运行时、执行槽和恢复状态快照。 */
    public static QIOCraftingMonitorRuntimeSnapshot getRuntimeSnapshot(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nonnull UUID jobId, int expectedPlanRevision, long knownRuntimeRevision,
          boolean forceBaseline, int nodeOffset,
          @Nonnull Map<Long, Long> knownNodeRevisions,
          long currentTick) {
        validateSession(session, network, currentAccessRevision);
        if (knownRuntimeRevision < -1 || nodeOffset < 0 ||
              knownNodeRevisions.size() > 1_024 || currentTick < 0) {
            throw new IllegalArgumentException("Invalid QIO monitor runtime request");
        }
        QIOCraftingJob job = network.getJob(Objects.requireNonNull(jobId, "jobId"));
        if (job == null) throw new IllegalArgumentException("Unknown QIO job " + jobId);
        if (job.getActivePlan().getRevision() != expectedPlanRevision) {
            throw new IllegalStateException("QIO job plan revision changed");
        }
        List<Long> orderedNodeIds = new ArrayList<>();
        job.getActivePlan().getSteps().forEach(step -> orderedNodeIds.add(step.getNodeId()));
        job.getActivePlan().getCycleNodes().forEach(cycle -> orderedNodeIds.add(cycle.getNodeId()));
        Collections.sort(orderedNodeIds);
        if (nodeOffset > orderedNodeIds.size() ||
              knownNodeRevisions.size() > orderedNodeIds.size() - nodeOffset) {
            throw new IllegalArgumentException("Invalid QIO monitor runtime node window");
        }
        int requestedIndex = 0;
        for (Long nodeId : knownNodeRevisions.keySet()) {
            if (!nodeId.equals(orderedNodeIds.get(nodeOffset + requestedIndex++))) {
                throw new IllegalArgumentException("QIO monitor runtime nodes are not contiguous");
            }
        }
        boolean baseline = forceBaseline || knownRuntimeRevision < 0;
        List<QIOCraftingMonitorRuntimeNode> updates = new ArrayList<>();
        for (Map.Entry<Long, Long> requested : knownNodeRevisions.entrySet()) {
            long nodeId = requested.getKey();
            long knownNodeRevision = requested.getValue();
            if (nodeId < 0 || knownNodeRevision < -1) {
                throw new IllegalArgumentException("Invalid QIO monitor node revision");
            }
            QIOStepRuntime step = job.getStepRuntime(nodeId);
            if (step != null) {
                if (baseline || step.getRuntimeRevision() != knownNodeRevision) {
                    updates.add(QIOCraftingMonitorRuntimeNode.step(step));
                }
                continue;
            }
            QIOCycleRuntime cycle = job.getCycleRuntimes().get(nodeId);
            if (cycle == null) {
                throw new IllegalArgumentException("Unknown QIO plan node " + nodeId);
            }
            if (baseline || job.getRuntimeRevision() != knownNodeRevision) {
                updates.add(QIOCraftingMonitorRuntimeNode.cycle(cycle,
                      job.getRuntimeRevision()));
            }
        }
        updates.sort(Comparator.comparingLong(QIOCraftingMonitorRuntimeNode::getNodeId));

        long total = 0;
        long completed = 0;
        int active = 0;
        int blocked = 0;
        for (QIOStepRuntime step : job.getStepRuntimes().values()) {
            total = Math.addExact(total, step.getRequiredOperations());
            completed = Math.addExact(completed, step.getCompletedOperations());
            active = Math.addExact(active, step.getActiveAssignmentCount());
            if (step.getFailedAttempts() > 0 || step.getActiveOperations().values().stream()
                  .anyMatch(operation -> operation.getDiagnostic() != null)) blocked++;
        }
        QIOMaterialCommitment commitment = network.getCommitment(jobId);
        Map<PortableResourceDescriptor, Long> missing = commitment == null ?
              Collections.emptyMap() : commitment.getMissingAmounts();
        Map<PortableResourceDescriptor, Long> visibleMissing = new LinkedHashMap<>();
        for (Map.Entry<PortableResourceDescriptor, Long> entry :
              new TreeMap<>(missing).entrySet()) {
            if (visibleMissing.size() >=
                QIOCraftingMonitorRuntimeSnapshot.MAX_MISSING_RESOURCES) break;
            visibleMissing.put(entry.getKey(), entry.getValue());
        }
        int configuredSlots = MekanismConfig.current().qioProcessing
              .executionSlotsPerFrequency.val();
        return new QIOCraftingMonitorRuntimeSnapshot(jobId, expectedPlanRevision,
              job.getActivePlan().getStructuralSignature(),
              baseline ? -1 : knownRuntimeRevision, job.getRuntimeRevision(), baseline,
              nodeOffset, knownNodeRevisions.size(),
              job.getState().name(), job.getBasePriority(), job.getRequestedRootAmount(),
              job.getDeliveredRootAmount(), Math.max(0, currentTick - job.getCreatedAtTick()),
              total, completed, active,
              missing.size(), visibleMissing, visibleMissing.size() < missing.size(), blocked,
              network.getActiveExecutionSlotCount(), configuredSlots, slotState(job),
              job.getExecutionSlotToken(), job.getSlotAcquiredAtSchedulerClock(), updates);
    }

    @Nonnull
    /** 在版本匹配时更新任务优先级。 */
    public static MutationStatus updatePriority(@Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nonnull UUID jobId, long expectedRuntimeRevision, long priority) {
        validateSession(session, network, currentAccessRevision);
        QIOCraftingJob job = network.getJob(Objects.requireNonNull(jobId, "jobId"));
        if (job == null) return MutationStatus.NOT_FOUND;
        if (job.getRuntimeRevision() != expectedRuntimeRevision) {
            return MutationStatus.REVISION_CONFLICT;
        }
        if (job.getState().isTerminal()) return MutationStatus.INVALID_STATE;
        if (job.getBasePriority() == priority) return MutationStatus.UNCHANGED;
        return network.updateJobPriority(jobId, expectedRuntimeRevision, priority) ?
              MutationStatus.ACCEPTED : MutationStatus.REVISION_CONFLICT;
    }

    @Nonnull
    /** 接受强制恢复后的任务重新派发。 */
    public static MutationStatus redispatchAfterForcedRecovery(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nonnull UUID jobId, long expectedRuntimeRevision) {
        validateSession(session, network, currentAccessRevision);
        QIOCraftingJob job = network.getJob(Objects.requireNonNull(jobId, "jobId"));
        if (job == null) return MutationStatus.NOT_FOUND;
        if (job.getRuntimeRevision() != expectedRuntimeRevision) {
            return MutationStatus.REVISION_CONFLICT;
        }
        if (job.getState() !=
              mekanism.qioprocessing.common.content.job.QIOCraftingJobState.OPERATION_CONTAMINATED ||
              job.getRecoveryDiagnostic() == null || job.isCancellationRequested() ||
              job.hasActiveOperations()) {
            return MutationStatus.INVALID_STATE;
        }
        if (!network.acceptForcedRecoveryRedispatch(jobId, expectedRuntimeRevision)) {
            return MutationStatus.REVISION_CONFLICT;
        }
        QIOProcessingExecutionService.INSTANCE.wakeJob(jobId);
        return MutationStatus.ACCEPTED;
    }

    private static List<QIOCraftingJob> orderedJobs(QIOProcessingNetworkData network) {
        List<QIOCraftingJob> jobs = new ArrayList<>();
        for (QIOCraftingJob job : network.getJobs()) {
            if (!job.getState().isTerminal()) {
                jobs.add(job);
            }
        }
        jobs.sort((left, right) -> {
            int sequence = Long.compare(right.getEnqueueSequence(), left.getEnqueueSequence());
            return sequence != 0 ? sequence :
                  left.getJobId().toString().compareTo(right.getJobId().toString());
        });
        return jobs;
    }

    private static List<QIOCraftingMonitorEntry> snapshot(QIOProcessingNetworkData network,
          List<QIOCraftingJob> jobs) {
        List<QIOCraftingMonitorEntry> entries = new ArrayList<>();
        for (QIOCraftingJob job : jobs) {
            long total = 0;
            long completed = 0;
            int active = 0;
            String diagnostic = job.getRecoveryDiagnostic() == null ? "" :
                  job.getRecoveryDiagnostic();
            for (QIOStepRuntime runtime : job.getStepRuntimes().values()) {
                total = Math.addExact(total, runtime.getRequiredOperations());
                completed = Math.addExact(completed, runtime.getCompletedOperations());
                active = Math.addExact(active, runtime.getActiveAssignmentCount());
                if (diagnostic.isEmpty()) {
                    for (QIOOperationAssignment assignment :
                          runtime.getActiveOperations().values()) {
                        if (assignment.getDiagnostic() != null) {
                            diagnostic = assignment.getDiagnostic();
                            break;
                        }
                    }
                }
            }
            QIOMaterialCommitment commitment = network.getCommitment(job.getJobId());
            entries.add(new QIOCraftingMonitorEntry(QIOCraftingMonitorEntry.Kind.JOB,
                  job.getJobId(), job.getSource().name(), job.getState().name(),
                  job.getActivePlan().getRootResource(), job.getRequestedRootAmount(),
                  job.getDeliveredRootAmount(), job.getActivePlan().getRevision(),
                  job.getRuntimeRevision(), job.getBasePriority(), total, completed, active,
                  commitment == null ? 0 : commitment.getMissingAmounts().size(),
                  job.getExecutionSlotToken() != null, null, -1, null, diagnostic));
        }
        return entries;
    }

    /** Runtime progress does not change list membership or order and must not invalidate cursors. */
    private static long listRevision(List<QIOCraftingJob> jobs) {
        long revision = 17;
        for (QIOCraftingJob job : jobs) {
            revision = 31 * revision + job.getJobId().getMostSignificantBits();
            revision = 31 * revision + job.getJobId().getLeastSignificantBits();
            revision = 31 * revision + job.getEnqueueSequence();
            revision = 31 * revision + job.getSource().ordinal();
            revision = 31 * revision + job.getActivePlan().getRevision();
        }
        return revision & Long.MAX_VALUE;
    }

    private static long planSourceRevision(String signature) {
        try {
            return Long.parseUnsignedLong(signature.substring(0, 16), 16) & Long.MAX_VALUE;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid QIO plan structural signature", e);
        }
    }

    private static QIOCraftingMonitorRuntimeSnapshot.SlotState slotState(QIOCraftingJob job) {
        if (job.getExecutionSlotToken() != null) {
            return job.isCancellationRequested() || job.getState() ==
                  mekanism.qioprocessing.common.content.job.QIOCraftingJobState.RETURNING ||
                  job.getState() == mekanism.qioprocessing.common.content.job.QIOCraftingJobState.DELIVERING ?
                  QIOCraftingMonitorRuntimeSnapshot.SlotState.RELEASING :
                  QIOCraftingMonitorRuntimeSnapshot.SlotState.HELD;
        }
        return job.getState() ==
              mekanism.qioprocessing.common.content.job.QIOCraftingJobState.WAITING_EXECUTION_SLOT ?
              QIOCraftingMonitorRuntimeSnapshot.SlotState.WAITING :
              QIOCraftingMonitorRuntimeSnapshot.SlotState.UNREQUESTED;
    }

    private static void validateSession(QIOProcessingTerminalSession session,
          QIOProcessingNetworkData network, long currentAccessRevision) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(network, "network");
        if (!session.isOpen()) throw new IllegalStateException("Terminal session is closed");
        if (session.getTerminalType() != QIOProcessingTerminalType.CRAFTING_MONITOR) {
            throw new SecurityException("This terminal cannot monitor crafting");
        }
        if (session.getFrequencyUUID() == null ||
            !session.getFrequencyUUID().equals(network.getFrequencyUUID())) {
            throw new SecurityException("Terminal targets another frequency");
        }
        if (session.getAccessRevision() != currentAccessRevision ||
            currentAccessRevision < 0) {
            throw new IllegalStateException("Frequency access changed");
        }
    }
}
