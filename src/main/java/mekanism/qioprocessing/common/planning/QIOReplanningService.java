package mekanism.qioprocessing.common.planning;

import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobState;
import mekanism.qioprocessing.common.order.QIOOrderService;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Bounded main-thread audit plus worker-pool replanning for stale active plans. */
/**
 * QIO 处理模块中的 QIOReplanningService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOReplanningService {
    public static final QIOReplanningService INSTANCE = new QIOReplanningService();
    private static final int AUDITS_PER_TICK = 8;
    private static final long AUDIT_INTERVAL = 20;
    private final Map<TaskKey, ActiveTask> active = new LinkedHashMap<>();
    private final Map<TaskKey, Long> nextAudit = new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<CompletedTask> completed = new ConcurrentLinkedQueue<>();
    private int networkCursor;

    private QIOReplanningService() {}

    public synchronized void tick(@Nonnull World world) {
        if (world.isRemote || world.provider.getDimension() != 0) return;
        drainCompletions();
        List<QIOProcessingNetworkData> networks = new ArrayList<>(
              QIOProcessingNetworkManager.INSTANCE.getNetworks());
        networks.sort(Comparator.comparing(network -> network.getFrequencyUUID().toString()));
        if (networks.isEmpty()) { networkCursor = 0; return; }
        long tick = world.getTotalWorldTime();
        int audited = 0;
        int start = Math.floorMod(networkCursor, networks.size());
        for (int offset = 0; offset < networks.size() && audited < AUDITS_PER_TICK; offset++) {
            QIOProcessingNetworkData network = networks.get((start + offset) % networks.size());
            List<QIOCraftingJob> jobs = new ArrayList<>(network.getJobs());
            jobs.sort(Comparator.comparingLong(QIOCraftingJob::getEnqueueSequence)
                  .thenComparing(job -> job.getJobId().toString()));
            for (QIOCraftingJob job : jobs) {
                if (audited >= AUDITS_PER_TICK) break;
                TaskKey key = new TaskKey(network.getFrequencyUUID(), job.getJobId());
                if (!eligible(job) || active.containsKey(key) ||
                      tick < nextAudit.getOrDefault(key, 0L)) continue;
                nextAudit.put(key, saturatedAdd(tick, AUDIT_INTERVAL));
                audited++;
                auditOrSubmit(world, network, job, key);
            }
        }
        networkCursor = start + 1;
    }

    public synchronized void shutdown() {
        QIOPlanningExecutor executor = QIOPlanningService.INSTANCE.getExecutor();
        if (executor != null) for (ActiveTask task : active.values()) executor.cancel(task.taskId);
        active.clear(); nextAudit.clear(); completed.clear(); networkCursor = 0;
    }

    private void auditOrSubmit(World world, QIOProcessingNetworkData network,
          QIOCraftingJob job, TaskKey key) {
        IQIOStorageView view = open(network, job);
        if (view == null) return;
        try {
            if (job.getState() != QIOCraftingJobState.PLANNING &&
                  QIOOrderService.INSTANCE.isAutomatedPlanCurrent(world, network,
                        view.getSnapshot(), job.getActivePlan().getSourceRevisions())) return;
            if (job.getState() != QIOCraftingJobState.PLANNING) {
                network.beginJobReplanning(job.getJobId());
                persist(network);
            }
            submit(world, network, job, key, view);
        } catch (IOException | RuntimeException ignored) {
        } finally { view.close(); }
    }

    private void submit(World world, QIOProcessingNetworkData network, QIOCraftingJob job,
          TaskKey key, IQIOStorageView view) throws IOException {
        QIOOrderService.PlanningCaptureResult captured =
              QIOOrderService.INSTANCE.capturePlanning(world, network, view.getSnapshot(),
                    job.getActivePlan().getRootResource());
        if (captured.getCapture() == null) {
            if (!network.hasPhysicalJobAssets(job.getJobId())) {
                network.failJobReplanning(job.getJobId());
                persist(network);
            }
            return;
        }
        int revision;
        try { revision = Math.addExact(job.getActivePlan().getRevision(), 1); }
        catch (ArithmeticException e) { network.failJobReplanning(job.getJobId()); persist(network); return; }
        long remaining = job.getRemainingGuaranteedRootAmount();
        if (remaining <= 0) { network.failJobReplanning(job.getJobId()); persist(network); return; }
        QIOOrderService.PlanningCapture capture = captured.getCapture();
        UUID planId = job.getActivePlan().getPlanId();
        QIOPlanningExecutor.Submission submission = QIOPlanningService.INSTANCE.submitTask(
              capture, (input, cancellation) -> {
                  QIOOrderService.PlanningPreparation preparation =
                        QIOOrderService.INSTANCE.prepareCaptured(input,
                              cancellation::isCancelled);
                  if (preparation.getSnapshot() == null) {
                      return QIOPlanningResult.failure(QIOPlanningResult.Status.NO_ROUTE,
                            preparation.getStatus().name(), 0, 0);
                  }
                  return QIOPlanner.INSTANCE.plan(new QIOPlanningRequest(planId, revision,
                        preparation.getSnapshot(), input.getTarget(), remaining), cancellation);
              }, result -> completed.add(new CompletedTask(key, revision, result)));
        if (submission.isAccepted()) active.put(key, new ActiveTask(submission.getTaskId(), revision));
    }

    private void drainCompletions() {
        CompletedTask completion;
        while ((completion = completed.poll()) != null) {
            ActiveTask expected = active.get(completion.key);
            if (expected == null || expected.revision != completion.revision ||
                  !expected.taskId.equals(completion.result.getTaskId())) continue;
            active.remove(completion.key);
            QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
                  completion.key.frequencyUUID);
            QIOCraftingJob job = network == null ? null : network.getJob(completion.key.jobId);
            if (job == null || job.getState() != QIOCraftingJobState.PLANNING ||
                  job.getRevisionTransition() != null || job.isCancellationRequested()) continue;
            try {
                if (completion.result.getStatus() == QIOPlanningExecutor.ResultStatus.SUCCESS &&
                      completion.result.getValue() != null &&
                      completion.result.getValue().getStatus() == QIOPlanningResult.Status.SUCCESS &&
                      completion.result.getValue().getPlan() != null) {
                    network.requestJobReplan(job.getJobId(), completion.result.getValue().getPlan());
                } else if (!network.hasPhysicalJobAssets(job.getJobId())) {
                    network.failJobReplanning(job.getJobId());
                }
                persist(network);
            } catch (IOException | RuntimeException ignored) {
            }
        }
    }

    private static boolean eligible(QIOCraftingJob job) {
        return !job.getState().isTerminal() && !job.isCancellationRequested() &&
              job.getState() != QIOCraftingJobState.OPERATION_CONTAMINATED &&
              job.getRevisionTransition() == null && job.getRemainingGuaranteedRootAmount() > 0;
    }

    @Nullable
    private static IQIOStorageView open(QIOProcessingNetworkData network, QIOCraftingJob job) {
        QIOFrequencyIdentitySnapshot identity = network.getLastKnownFrequencyIdentity();
        UUID requester = job.getRequester() == null ? identity.getOwnerUUID() : job.getRequester();
        return QIOFrequencyStorageAccess.INSTANCE.open(new QIOFrequencyReference(
              network.getFrequencyUUID(), identity.getName(), identity.getOwnerUUID(),
              identity.getSecurityMode(), requester), requester);
    }

    private static void persist(QIOProcessingNetworkData network) throws IOException {
        QIOProcessingNetworkManager.INSTANCE.persistenceBarrier().persist(network);
    }
    private static long saturatedAdd(long left,long right){return right>Long.MAX_VALUE-left?Long.MAX_VALUE:left+right;}
    private static final class ActiveTask {final UUID taskId;final int revision;ActiveTask(UUID id,int revision){taskId=id;this.revision=revision;}}
    private static final class CompletedTask {final TaskKey key;final int revision;final QIOPlanningExecutor.PlanningResult<QIOPlanningResult> result;CompletedTask(TaskKey key,int revision,QIOPlanningExecutor.PlanningResult<QIOPlanningResult> result){this.key=key;this.revision=revision;this.result=result;}}
    private static final class TaskKey {final UUID frequencyUUID,jobId;TaskKey(UUID frequencyUUID,UUID jobId){this.frequencyUUID=frequencyUUID;this.jobId=jobId;}@Override public boolean equals(Object o){return o instanceof TaskKey key&&frequencyUUID.equals(key.frequencyUUID)&&jobId.equals(key.jobId);}@Override public int hashCode(){return Objects.hash(frequencyUUID,jobId);}}
}
