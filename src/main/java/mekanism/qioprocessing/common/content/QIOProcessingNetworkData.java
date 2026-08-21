package mekanism.qioprocessing.common.content;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.buffer.QIOJobBuffer;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobState;
import mekanism.qioprocessing.common.content.job.QIOOperationAssignment;
import mekanism.qioprocessing.common.content.job.QIOPlanRevisionTransition;
import mekanism.qioprocessing.common.content.job.QIOStepRuntime;
import mekanism.qioprocessing.common.content.material.QIOMaterialCommitment;
import mekanism.qioprocessing.common.content.material.QIOPendingClaimMutation;
import mekanism.qioprocessing.common.content.material.QIOWaitingMaterialIndex;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.content.scheduling.ActiveExecutionSlot;
import mekanism.qioprocessing.common.content.scheduling.QIOExecutionSlotPool;
import mekanism.qioprocessing.common.content.transfer.QIODurableTransferRecord;
import mekanism.qioprocessing.common.content.transfer.QIOConfigurationExchangeRecord;
import mekanism.qioprocessing.common.planning.QIOProviderCatalog;
import mekanism.qioprocessing.common.planning.QIOProviderRouteCatalog;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.qioprocessing.common.content.policy.QIOPolicyCatalog;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileCatalog;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.content.passive.QIOPassiveOperation;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRuleCatalog;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceCatalog;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Authoritative, serially-mutated QIO Processing state for one stable frequency UUID. */
/**
 * QIO 处理模块中的 QIOProcessingNetworkData 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingNetworkData {

    public static final int SCHEMA_VERSION = 7;
    public static final String PROGRESSIVE_ROOT_DELIVERY_NODE_ID = "progressive-root-delivery";
    private static final int MAX_PERSISTED_JOBS = 1_000_000;
    private static final int MAX_PERSISTED_TRANSFERS = 1_000_000;
    private static final int MAX_PERSISTED_PASSIVE_OPERATIONS = 1_000_000;
    private static final Comparator<QIOCraftingJob> CLAIM_ORDER = Comparator
          .comparingLong(QIOCraftingJob::getBasePriority).reversed()
          .thenComparingLong(QIOCraftingJob::getEnqueueSequence)
          .thenComparing(job -> job.getJobId().toString());

    private final UUID frequencyUUID;
    private QIOFrequencyIdentitySnapshot lastKnownFrequencyIdentity;
    private QIOProcessingNetworkLifecycle lifecycle;
    private long networkRevision;
    private long taskCommitmentRevision;
    private long schedulerClock;
    private long enqueueCounter;
    private long dispatchCounter;
    private final Map<UUID, QIOCraftingJob> jobs = new LinkedHashMap<>();
    private final Map<UUID, QIOMaterialCommitment> commitments = new LinkedHashMap<>();
    private final Map<UUID, QIOJobBuffer> jobBuffers = new LinkedHashMap<>();
    private final Map<UUID, QIODurableTransferRecord> durableTransfers = new LinkedHashMap<>();
    private final Map<UUID, QIOConfigurationExchangeRecord> configurationExchanges =
          new LinkedHashMap<>();
    private final Map<UUID, QIOPassiveOperation> passiveOperations = new LinkedHashMap<>();
    private final Set<UUID> waitingExecutionSlotJobs = new LinkedHashSet<>();
    // Transient scheduler indexes keep the per-tick hot path independent of retained history.
    private final Set<UUID> dispatchCandidateJobs = new LinkedHashSet<>();
    private final Set<UUID> waitingProviderJobs = new LinkedHashSet<>();
    private final Set<UUID> reservingJobs = new LinkedHashSet<>();
    private final Set<UUID> replanJobs = new LinkedHashSet<>();
    private final Set<UUID> cancellationJobs = new LinkedHashSet<>();
    private final Set<UUID> activePassiveOperations = new LinkedHashSet<>();
    private final Map<UUID, Integer> activePassiveDeviceCounts = new LinkedHashMap<>();
    private final Set<UUID> terminalJobHistory = new LinkedHashSet<>();
    private final Set<UUID> terminalPassiveHistory = new LinkedHashSet<>();
    private QIOWaitingMaterialIndex waitingMaterialIndex = new QIOWaitingMaterialIndex();
    private QIOExecutionSlotPool executionSlots = new QIOExecutionSlotPool();
    private QIOPolicyCatalog policies = new QIOPolicyCatalog();
    private QIOAutomationRecipeProfileCatalog automationRecipeProfiles =
          new QIOAutomationRecipeProfileCatalog();
    private QIOWorkbenchConfiguration workbenchConfiguration =
          new QIOWorkbenchConfiguration();
    private QIOMaintenanceRuleCatalog maintenanceRules = new QIOMaintenanceRuleCatalog();
    private QIOAutomationDeviceCatalog automationDevices = new QIOAutomationDeviceCatalog();
    private QIOProviderCatalog providerCatalog = new QIOProviderCatalog();
    private long claimWakeGeneration;
    @Nullable
    private Runnable dirtyListener;
    private boolean repairedOnLoad;

    /** 创建指定频率的空网络状态。 */
    public QIOProcessingNetworkData(@Nonnull UUID frequencyUUID,
          @Nonnull QIOFrequencyIdentitySnapshot lastKnownFrequencyIdentity) {
        this.frequencyUUID = Objects.requireNonNull(frequencyUUID, "frequencyUUID");
        this.lastKnownFrequencyIdentity = Objects.requireNonNull(lastKnownFrequencyIdentity,
              "lastKnownFrequencyIdentity");
        lifecycle = QIOProcessingNetworkLifecycle.ACTIVE;
    }

    @Nonnull
    /** 返回网络所属频率 UUID。 */
    public UUID getFrequencyUUID() {
        return frequencyUUID;
    }

    @Nonnull
    /** 返回最近一次观测到的频率身份。 */
    public QIOFrequencyIdentitySnapshot getLastKnownFrequencyIdentity() {
        return lastKnownFrequencyIdentity;
    }

    @Nonnull
    /** 返回网络生命周期。 */
    public QIOProcessingNetworkLifecycle getLifecycle() {
        return lifecycle;
    }

    /** 返回网络结构版本。 */
    public long getNetworkRevision() {
        return networkRevision;
    }

    /** 返回任务材料承诺版本。 */
    public long getTaskCommitmentRevision() {
        return taskCommitmentRevision;
    }

    /** 返回调度时钟。 */
    public long getSchedulerClock() {
        return schedulerClock;
    }

    /** 返回任务入队计数器。 */
    public long getEnqueueCounter() {
        return enqueueCounter;
    }

    /** 返回任务派发计数器。 */
    public long getDispatchCounter() {
        return dispatchCounter;
    }

    /** 返回加载时是否执行过结构修复。 */
    public boolean wasRepairedOnLoad() {
        return repairedOnLoad;
    }

    @Nullable
    /** 按任务 UUID 查询任务。 */
    public QIOCraftingJob getJob(UUID jobId) {
        return jobId == null ? null : jobs.get(jobId);
    }

    @Nullable
    /** 按任务 UUID 查询材料承诺。 */
    public QIOMaterialCommitment getCommitment(UUID jobId) {
        return jobId == null ? null : commitments.get(jobId);
    }

    @Nullable
    /** 按任务 UUID 查询任务资源缓冲。 */
    public QIOJobBuffer getJobBuffer(UUID jobId) {
        return jobId == null ? null : jobBuffers.get(jobId);
    }

    @Nullable
    /** 按传输 UUID 查询 durable transfer。 */
    public QIODurableTransferRecord getDurableTransfer(UUID transferId) {
        return transferId == null ? null : durableTransfers.get(transferId);
    }

    @Nonnull
    /** 返回任务的只读集合视图。 */
    public Collection<QIOCraftingJob> getJobs() {
        return Collections.unmodifiableList(new ArrayList<>(jobs.values()));
    }

    @Nonnull
    /** 返回材料承诺的只读集合视图。 */
    public Collection<QIOMaterialCommitment> getCommitments() {
        return Collections.unmodifiableList(new ArrayList<>(commitments.values()));
    }

    @Nonnull
    /** 返回 durable transfer 的只读集合视图。 */
    public Collection<QIODurableTransferRecord> getDurableTransfers() {
        return Collections.unmodifiableList(new ArrayList<>(durableTransfers.values()));
    }

    @Nullable
    /** 按交换 UUID 查询配置交换记录。 */
    public QIOConfigurationExchangeRecord getConfigurationExchange(UUID exchangeId) {
        return exchangeId == null ? null : configurationExchanges.get(exchangeId);
    }

    @Nonnull
    /** 返回配置交换记录的只读集合视图。 */
    public Collection<QIOConfigurationExchangeRecord> getConfigurationExchanges() {
        return Collections.unmodifiableList(new ArrayList<>(configurationExchanges.values()));
    }

    @Nonnull
    public List<QIOConfigurationExchangeRecord> getOperationConfigurationExchanges(
          @Nonnull UUID operationId) {
        List<QIOConfigurationExchangeRecord> result = new ArrayList<>();
        for (QIOConfigurationExchangeRecord exchange : configurationExchanges.values()) {
            if (exchange.getOperationId().equals(operationId)) {
                result.add(exchange);
            }
        }
        result.sort(Comparator.comparing(QIOConfigurationExchangeRecord::getPortId));
        return Collections.unmodifiableList(result);
    }

    /** 添加配置交换并更新网络索引。 */
    public void addConfigurationExchange(@Nonnull QIOConfigurationExchangeRecord exchange) {
        Objects.requireNonNull(exchange, "exchange");
        if (configurationExchanges.size() >= MAX_PERSISTED_TRANSFERS ||
              configurationExchanges.putIfAbsent(exchange.getExchangeId(), exchange) != null) {
            throw new IllegalStateException("Cannot add duplicate/excess QIO configuration exchange");
        }
        markDirty();
    }

    public void markConfigurationExchangeChanged(@Nonnull UUID exchangeId) {
        if (!configurationExchanges.containsKey(Objects.requireNonNull(exchangeId,
              "exchangeId"))) {
            throw new IllegalArgumentException("Unknown QIO configuration exchange " + exchangeId);
        }
        markDirty();
    }

    public void removeCommittedConfigurationExchanges(@Nonnull UUID operationId) {
        UUID checked = Objects.requireNonNull(operationId, "operationId");
        List<UUID> removedExchanges = new ArrayList<>();
        configurationExchanges.entrySet().removeIf(entry -> {
            QIOConfigurationExchangeRecord exchange = entry.getValue();
            boolean remove = exchange.getOperationId().equals(checked) &&
                  exchange.getPhase().isSettled();
            if (remove) {
                removedExchanges.add(exchange.getTargetReturnTransferId());
            }
            return remove;
        });
        for (UUID transferId : removedExchanges) {
            QIODurableTransferRecord transfer = durableTransfers.get(transferId);
            if (transfer != null && transfer.getResolution() ==
                  QIODurableTransferRecord.Resolution.FORWARD_COMMITTED) {
                durableTransfers.remove(transferId);
            }
        }
        markDirty();
    }

    public boolean hasUnsettledConfigurationExchange(@Nonnull UUID operationId) {
        UUID checked = Objects.requireNonNull(operationId, "operationId");
        return configurationExchanges.values().stream().anyMatch(exchange ->
              exchange.getOperationId().equals(checked) &&
                    !exchange.getPhase().isSettled());
    }

    @Nullable
    public QIOPassiveOperation getPassiveOperation(UUID operationId) {
        return operationId == null ? null : passiveOperations.get(operationId);
    }

    @Nonnull
    public Collection<QIOPassiveOperation> getPassiveOperations() {
        return Collections.unmodifiableList(new ArrayList<>(passiveOperations.values()));
    }

    /** 添加被动处理操作并更新活动索引。 */
    public void addPassiveOperation(@Nonnull QIOPassiveOperation operation) {
        Objects.requireNonNull(operation, "operation");
        if (passiveOperations.size() >= MAX_PERSISTED_PASSIVE_OPERATIONS ||
              passiveOperations.putIfAbsent(operation.getOperationId(), operation) != null) {
            throw new IllegalStateException("Cannot add duplicate/excess QIO passive operation");
        }
        refreshPassiveIndexes(operation);
        markDirty();
    }

    public void markPassiveOperationChanged(@Nonnull UUID operationId) {
        QIOPassiveOperation operation = passiveOperations.get(Objects.requireNonNull(operationId,
              "operationId"));
        if (operation == null) {
            throw new IllegalArgumentException("Unknown QIO passive operation " + operationId);
        }
        refreshPassiveIndexes(operation);
        markDirty();
    }

    /** Returns and rotates a bounded batch of non-terminal passive operations. */
    @Nonnull
    /** 按稳定顺序取出不超过上限的活动被动操作。 */
    public List<QIOPassiveOperation> pollActivePassiveOperations(int maximum) {
        return pollIndexed(activePassiveOperations, passiveOperations, maximum);
    }

    @Nonnull
    public List<QIOPassiveOperation> pollTerminalPassiveHistory(int maximum) {
        return pollIndexed(terminalPassiveHistory, passiveOperations, maximum);
    }

    /** 判断设备是否已有活动被动操作。 */
    public boolean hasActivePassiveOperation(@Nonnull UUID deviceUUID) {
        return activePassiveDeviceCounts.containsKey(
              Objects.requireNonNull(deviceUUID, "deviceUUID"));
    }

    public void acknowledgeTerminalPassiveHistory(@Nonnull UUID operationId) {
        terminalPassiveHistory.remove(Objects.requireNonNull(operationId, "operationId"));
    }

    @Nonnull
    /** 返回活动执行槽的只读快照。 */
    public Collection<ActiveExecutionSlot> getActiveExecutionSlots() {
        return executionSlots.getActiveSlots();
    }

    @Nonnull
    public QIOPolicyCatalog getPolicies() {
        return policies;
    }

    @Nonnull
    public QIOAutomationRecipeProfileCatalog getAutomationRecipeProfiles() {
        return automationRecipeProfiles;
    }

    @Nonnull
    public QIOWorkbenchConfiguration getWorkbenchConfiguration() {
        return workbenchConfiguration;
    }

    /** Marks a catalog mutation dirty without exposing the network's persistence callback. */
    public boolean markAutomationRecipeProfilesChanged(long previousRevision) {
        if (automationRecipeProfiles.getRevision() == previousRevision) {
            return false;
        }
        requestMaintenanceRouteAudit();
        markDirty();
        return true;
    }

    /** Marks a workbench configuration mutation dirty and invalidates route revisions. */
    public boolean markWorkbenchConfigurationChanged(long previousRevision) {
        if (workbenchConfiguration.getRevision() == previousRevision) {
            return false;
        }
        requestMaintenanceRouteAudit();
        markDirty();
        return true;
    }

    @Nonnull
    public QIOMaintenanceRuleCatalog getMaintenanceRules() {
        return maintenanceRules;
    }

    @Nonnull
    public QIOAutomationDeviceCatalog getAutomationDevices() {
        return automationDevices;
    }

    @Nonnull
    public QIOProviderCatalog getProviderCatalog() {
        return providerCatalog;
    }

    public boolean observeProviderCatalog(@Nonnull QIOProviderRouteCatalog.Snapshot snapshot,
          int maximumRoutes) {
        if (providerCatalog.observe(snapshot, automationDevices, maximumRoutes)) {
            requestMaintenanceRouteAudit();
            markDirty();
            return true;
        }
        return false;
    }

    public boolean observeAutomationDeviceRoutes(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationMode mode, @Nonnull String profileScopeId,
          @Nonnull ResourceLocation providerId, @Nonnull List<MachineRecipeRoute> routes,
          int maximumRoutes) {
        if (providerCatalog.observeDeviceRoutes(deviceUUID, mode, profileScopeId, providerId,
              routes, maximumRoutes)) {
            requestMaintenanceRouteAudit();
            markDirty();
            return true;
        }
        return false;
    }

    public boolean forgetAutomationDeviceRoutes(@Nonnull UUID deviceUUID) {
        if (providerCatalog.forgetDeviceRoutes(deviceUUID)) {
            requestMaintenanceRouteAudit();
            markDirty();
            return true;
        }
        return false;
    }

    public boolean observeAutomationDevice(@Nonnull QIOAutomationDeviceSnapshot snapshot,
          int maximumDevices) {
        if (automationDevices.observe(snapshot, maximumDevices)) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean markAutomationDeviceOffline(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationDeviceLocation expectedLocation, long currentTick) {
        if (automationDevices.markOffline(deviceUUID, expectedLocation, currentTick)) {
            requestMaintenanceRouteAudit();
            markDirty();
            return true;
        }
        return false;
    }

    public boolean forgetOfflineAutomationDevice(@Nonnull UUID deviceUUID,
          long expectedDirectoryRevision) {
        if (automationDevices.remove(deviceUUID, expectedDirectoryRevision)) {
            providerCatalog.forgetDevice(deviceUUID);
            requestMaintenanceRouteAudit();
            markDirty();
            return true;
        }
        return false;
    }

    /** Removes a device which authoritatively left this frequency at the expected location. */
    public boolean forgetAutomationDeviceMembership(@Nonnull UUID deviceUUID,
          @Nonnull QIOAutomationDeviceLocation expectedLocation) {
        if (automationDevices.removeAtLocation(deviceUUID, expectedLocation)) {
            providerCatalog.forgetDevice(deviceUUID);
            requestMaintenanceRouteAudit();
            markDirty();
            return true;
        }
        return false;
    }

    @Nonnull
    public QIOMaintenanceRule createMaintenanceRule(
          @Nonnull PortableResourceDescriptor resource, @Nonnull UUID creator, boolean enabled,
          long triggerAmount, long targetAmount, long maximumSingleRequest, long jobPriority,
          int retryIntervalTicks, long currentTick, int maximumRules) {
        QIOMaintenanceRule rule = maintenanceRules.create(resource, creator, enabled,
              triggerAmount, targetAmount, maximumSingleRequest, jobPriority,
              retryIntervalTicks, currentTick, maximumRules);
        markDirty();
        return rule;
    }

    public void updateMaintenanceRule(@Nonnull UUID ruleId, long expectedRevision,
          @Nonnull UUID editor, boolean enabled, long triggerAmount, long targetAmount,
          long maximumSingleRequest, long jobPriority, int retryIntervalTicks,
          long currentTick) {
        maintenanceRules.update(ruleId, expectedRevision, editor, enabled, triggerAmount,
              targetAmount, maximumSingleRequest, jobPriority, retryIntervalTicks, currentTick);
        markDirty();
    }

    public void removeMaintenanceRule(@Nonnull UUID ruleId, long expectedRevision) {
        maintenanceRules.remove(ruleId, expectedRevision);
        markDirty();
    }

    public void markMaintenanceRuntimeChanged() {
        markDirty();
    }

    private void requestMaintenanceRouteAudit() {
        if (maintenanceRules.hasRules()) {
            maintenanceRules.requestEvaluationPass();
        }
    }

    public void setGlobalDefaultPolicy(@Nonnull QIOPolicyCatalog.Toggle toggle,
          long priority) {
        setGlobalDefaultPolicy(toggle, priority, 0);
    }

    public void setGlobalDefaultPolicy(@Nonnull QIOPolicyCatalog.Toggle toggle,
          long priority, long passivePriority) {
        long before = policies.getRevision();
        policies.setGlobalDefaultPolicy(toggle, priority, passivePriority);
        if (policies.getRevision() != before) {
            markDirty();
        }
    }

    public void setGlobalRoutePolicy(@Nonnull String providerId,
          @Nonnull String routeId,
          @Nonnull String recipeKey, @Nonnull QIOPolicyCatalog.Toggle toggle,
          @Nullable Long priority) {
        setGlobalRoutePolicy(providerId, routeId, recipeKey, toggle, priority, null);
    }

    public void setGlobalRoutePolicy(@Nonnull String providerId,
          @Nonnull String routeId, @Nonnull String recipeKey,
          @Nonnull QIOPolicyCatalog.Toggle toggle, @Nullable Long priority,
          @Nullable Long passivePriority) {
        long before = policies.getRevision();
        policies.setGlobalRoutePolicy(providerId, routeId, recipeKey, toggle, priority,
              passivePriority);
        if (policies.getRevision() != before) {
            markDirty();
        }
    }

    public void setDeviceDefaultPolicy(@Nonnull UUID deviceUUID,
          @Nonnull QIOPolicyCatalog.Toggle toggle, long machinePriority,
          long passivePriority) {
        long before = policies.getRevision();
        policies.setDeviceDefaultPolicy(deviceUUID, toggle, machinePriority,
              passivePriority);
        if (policies.getRevision() != before) {
            markDirty();
        }
    }

    public void setDeviceRoutePolicy(@Nonnull UUID deviceUUID,
          @Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey, @Nonnull QIOPolicyCatalog.Toggle toggle,
          @Nullable Long priority) {
        setDeviceRoutePolicy(deviceUUID, providerId, routeId, recipeKey, toggle,
              priority, null);
    }

    public void setDeviceRoutePolicy(@Nonnull UUID deviceUUID,
          @Nonnull String providerId, @Nonnull String routeId,
          @Nonnull String recipeKey, @Nonnull QIOPolicyCatalog.Toggle toggle,
          @Nullable Long priority, @Nullable Long passivePriority) {
        long before = policies.getRevision();
        policies.setDeviceRoutePolicy(deviceUUID, providerId, routeId, recipeKey,
              toggle, priority, passivePriority);
        if (policies.getRevision() != before) {
            markDirty();
        }
    }

    @Nonnull
    /** 返回当前可派发任务的稳定快照。 */
    public List<QIOCraftingJob> getDispatchCandidates() {
        return getDispatchCandidates(Integer.MAX_VALUE);
    }

    /** Returns a bounded round-robin slice without scanning terminal job history. */
    @Nonnull
    /** 取出不超过上限的派发候选任务。 */
    public List<QIOCraftingJob> getDispatchCandidates(int maximum) {
        return peekIndexed(dispatchCandidateJobs, jobs, maximum);
    }

    /** Requeues idle provider waiters only when provider-relevant state changed. */
    /** 将等待 Provider 的任务重新加入派发候选。 */
    public void wakeWaitingProviderJobs() {
        if (!waitingProviderJobs.isEmpty()) {
            dispatchCandidateJobs.addAll(waitingProviderJobs);
            waitingProviderJobs.clear();
        }
    }

    /** 将暂时没有 Provider 的任务延后等待。 */
    public void deferWaitingProviderJob(@Nonnull UUID jobId) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getState() == QIOCraftingJobState.WAITING_PROVIDER &&
              !job.hasActiveOperations()) {
            dispatchCandidateJobs.remove(jobId);
            waitingProviderJobs.add(jobId);
        }
    }

    public void rotateDispatchJob(@Nonnull UUID jobId) {
        UUID checked = Objects.requireNonNull(jobId, "jobId");
        if (dispatchCandidateJobs.remove(checked)) {
            dispatchCandidateJobs.add(checked);
        }
    }

    @Nonnull
    /** 取出等待材料预留的任务。 */
    public List<QIOCraftingJob> pollReservingJobs(int maximum) {
        return pollIndexed(reservingJobs, jobs, maximum);
    }

    @Nonnull
    /** 取出终止任务历史供清理或诊断。 */
    public List<QIOCraftingJob> pollTerminalJobHistory(int maximum) {
        return pollIndexed(terminalJobHistory, jobs, maximum);
    }

    public void acknowledgeTerminalJobHistory(@Nonnull UUID jobId) {
        terminalJobHistory.remove(Objects.requireNonNull(jobId, "jobId"));
    }

    /**
     * Drops a fully settled terminal job and all of its persisted planning/runtime state.
     * Returns false while any resource ownership or durable handoff can still reference it.
     */
    /** 删除已完成且无剩余所有权的终止任务。 */
    public boolean removeSettledTerminalJob(@Nonnull UUID jobId) {
        UUID checkedJobId = Objects.requireNonNull(jobId, "jobId");
        QIOCraftingJob job = jobs.get(checkedJobId);
        QIOMaterialCommitment commitment = commitments.get(checkedJobId);
        QIOJobBuffer buffer = jobBuffers.get(checkedJobId);
        if (job == null || commitment == null || buffer == null ||
              !job.getState().isTerminal() || job.getExecutionSlotToken() != null ||
              job.hasActiveOperations() || !buffer.isEmpty() ||
              commitment.getPendingMutation() != null ||
              commitment.getState() != QIOMaterialCommitment.State.CONSUMED &&
                    commitment.getState() != QIOMaterialCommitment.State.RELEASED) {
            return false;
        }
        for (QIODurableTransferRecord transfer : durableTransfers.values()) {
            if (checkedJobId.equals(transfer.getOwnerJobId())) {
                return false;
            }
        }
        for (QIOConfigurationExchangeRecord exchange : configurationExchanges.values()) {
            if (checkedJobId.equals(exchange.getOwnerJobId())) {
                return false;
            }
        }

        waitingMaterialIndex.update(checkedJobId,
              commitment.getMissingResourceKeys(), Collections.emptySet());
        jobs.remove(checkedJobId);
        commitments.remove(checkedJobId);
        jobBuffers.remove(checkedJobId);
        waitingExecutionSlotJobs.remove(checkedJobId);
        dispatchCandidateJobs.remove(checkedJobId);
        waitingProviderJobs.remove(checkedJobId);
        reservingJobs.remove(checkedJobId);
        replanJobs.remove(checkedJobId);
        cancellationJobs.remove(checkedJobId);
        terminalJobHistory.remove(checkedJobId);
        advanceTaskCommitmentRevision();
        markDirty();
        return true;
    }

    /** 在任务步骤中登记机器/处理器操作分配。 */
    public void startStepOperation(@Nonnull UUID jobId, long nodeId,
          @Nonnull QIOOperationAssignment assignment) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getExecutionSlotToken() == null || job.getState().isTerminal()) {
            throw new IllegalStateException("QIO job cannot dispatch without an execution slot");
        }
        job.startStepOperation(nodeId, assignment);
        job.recordDispatch(nextDispatchSequence());
        transitionJob(job, QIOCraftingJobState.DISPATCHING);
        markDirty();
    }

    /** 更新任务步骤操作的状态和进度。 */
    public boolean updateStepOperation(@Nonnull UUID jobId, long nodeId,
          @Nonnull UUID operationId, @Nonnull QIOOperationAssignment.State state,
          long currentTick, long totalTicks, @Nullable String diagnostic) {
        QIOCraftingJob job = requireJob(jobId);
        long previousRevision = job.getRuntimeRevision();
        job.updateStepOperation(nodeId, operationId, state, currentTick, totalTicks, diagnostic);
        transitionJob(job, state == QIOOperationAssignment.State.COLLECTING ||
              state == QIOOperationAssignment.State.OUTPUT_BLOCKED ?
              QIOCraftingJobState.COLLECTING : QIOCraftingJobState.PROCESSING);
        if (job.getRuntimeRevision() == previousRevision) {
            return false;
        }
        markDirty();
        return true;
    }

    /** 结算任务步骤操作。 */
    public void completeStepOperation(@Nonnull UUID jobId, long nodeId,
          @Nonnull UUID operationId) {
        QIOCraftingJob job = requireJob(jobId);
        job.completeStepOperation(nodeId, operationId);
        transitionJob(job, job.areAllStepsComplete() ? QIOCraftingJobState.DELIVERING :
              QIOCraftingJobState.PROCESSING);
        settleProgressiveRootOutput(jobId);
        markDirty();
    }

    /** 记录任务步骤操作失败。 */
    public void failStepOperation(@Nonnull UUID jobId, long nodeId,
          @Nonnull UUID operationId, @Nonnull String diagnostic) {
        QIOCraftingJob job = requireJob(jobId);
        job.failStepOperation(nodeId, operationId, diagnostic);
        transitionJob(job, QIOCraftingJobState.WAITING_PROVIDER);
        markDirty();
    }

    /** 将任务步骤操作标记为污染并保留恢复诊断。 */
    public void contaminateStepOperation(@Nonnull UUID jobId, long nodeId,
          @Nonnull UUID operationId, @Nonnull String diagnostic) {
        QIOCraftingJob job = requireJob(jobId);
        job.failStepOperation(nodeId, operationId, diagnostic);
        job.markForcedRecovery(diagnostic);
        waitingExecutionSlotJobs.remove(jobId);
        refreshJobIndexes(job);
        markDirty();
    }

    /** Records a forced machine recovery as a player-decision point for one job operation. */
    /** 将任务操作标记为强制恢复后的可重派发状态。 */
    public boolean markForcedRecoveryOperation(@Nonnull UUID jobId, long nodeId,
          @Nonnull UUID operationId, @Nonnull String diagnostic) {
        QIOCraftingJob job = requireJob(jobId);
        QIOStepRuntime runtime = job.getStepRuntime(nodeId);
        if (runtime == null || runtime.getOperation(operationId) == null) {
            return false;
        }
        job.failStepOperation(nodeId, operationId, diagnostic);
        job.markForcedRecovery(diagnostic);
        refreshJobIndexes(job);
        markDirty();
        return true;
    }

    /** Marks a job as awaiting a player's redispatch decision when its operation record is missing. */
    /** 记录任务级强制恢复诊断。 */
    public boolean markForcedRecoveryJob(@Nonnull UUID jobId, @Nonnull String diagnostic) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getState().isTerminal() || job.isCancellationRequested()) {
            return false;
        }
        job.markForcedRecovery(diagnostic);
        refreshJobIndexes(job);
        markDirty();
        return true;
    }

    /** Accepts the monitor's explicit redispatch decision after a forced recovery. */
    /** 接受任务强制恢复后的重新派发。 */
    public boolean acceptForcedRecoveryRedispatch(@Nonnull UUID jobId,
          long expectedRuntimeRevision) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getRuntimeRevision() != expectedRuntimeRevision ||
            job.getState() != QIOCraftingJobState.OPERATION_CONTAMINATED ||
            job.hasActiveOperations()) {
            return false;
        }
        job.acceptForcedRecoveryRedispatch();
        refreshJobIndexes(job);
        markDirty();
        return true;
    }

    /** 推进任务生命周期状态并刷新调度索引。 */
    public void transitionJobState(@Nonnull UUID jobId, @Nonnull QIOCraftingJobState state) {
        transitionJob(requireJob(jobId), Objects.requireNonNull(state, "state"));
        markDirty();
    }

    /** 在版本匹配时更新任务优先级。 */
    public boolean updateJobPriority(@Nonnull UUID jobId, long expectedRuntimeRevision,
          long priority) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getRuntimeRevision() != expectedRuntimeRevision || job.getState().isTerminal()) {
            return false;
        }
        if (job.getBasePriority() == priority) {
            return true;
        }
        QIOMaterialCommitment commitment = requireCommitment(jobId);
        job.updateBasePriority(priority);
        commitment.updatePriority(priority);
        advanceTaskCommitmentRevision();
        advanceClaimWakeGeneration();
        markDirty();
        return true;
    }

    /** Begins a durable plan transition while the old plan remains authoritative. */
    /** 请求任务在安全边界切换到新的合成计划。 */
    public void requestJobReplan(@Nonnull UUID jobId, @Nonnull QIOCraftPlan nextPlan) {
        QIOCraftingJob job = requireJob(jobId);
        job.requestReplan(nextPlan);
        waitingExecutionSlotJobs.remove(jobId);
        transitionJob(job, hasPhysicalJobAssets(jobId) ?
              QIOCraftingJobState.REPLAN_DRAINING : QIOCraftingJobState.REPLAN_REQUIRED);
        advanceTaskCommitmentRevision();
        markDirty();
    }

    /** Stops new dispatch while an asynchronous replacement plan is generated. */
    public void beginJobReplanning(@Nonnull UUID jobId) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getState().isTerminal() || job.isCancellationRequested() ||
              job.getRevisionTransition() != null) {
            throw new IllegalStateException("QIO job cannot enter asynchronous replanning");
        }
        waitingExecutionSlotJobs.remove(jobId);
        transitionJob(job, QIOCraftingJobState.PLANNING);
        markDirty();
    }

    public void failJobReplanning(@Nonnull UUID jobId) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getState() != QIOCraftingJobState.PLANNING ||
              job.getRevisionTransition() != null || hasPhysicalJobAssets(jobId)) {
            throw new IllegalStateException("QIO job is not an idle planning job");
        }
        job.transitionTo(QIOCraftingJobState.WAITING_PROVIDER);
        refreshJobIndexes(job);
        markDirty();
    }

    @Nonnull
    public List<QIOCraftingJob> getReplanCandidates() {
        return peekIndexed(replanJobs, jobs, Integer.MAX_VALUE);
    }

    @Nonnull
    public List<QIOCraftingJob> pollReplanCandidates(int maximum) {
        return pollIndexed(replanJobs, jobs, maximum);
    }

    public boolean hasPhysicalJobAssets(@Nonnull UUID jobId) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getExecutionSlotToken() != null || job.hasActiveOperations() ||
              !requireJobBuffer(jobId).isEmpty()) {
            return true;
        }
        for (QIODurableTransferRecord transfer : durableTransfers.values()) {
            if (Objects.equals(jobId, transfer.getOwnerJobId()) &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE) {
                return true;
            }
        }
        for (QIOConfigurationExchangeRecord exchange : configurationExchanges.values()) {
            if (Objects.equals(jobId, exchange.getOwnerJobId()) &&
                  !exchange.getPhase().isSettled()) {
                return true;
            }
        }
        return false;
    }

    public void finishReplanDrain(@Nonnull UUID jobId) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getState() != QIOCraftingJobState.REPLAN_DRAINING ||
              job.getRevisionTransition() == null || job.hasActiveOperations() ||
              !requireJobBuffer(jobId).isEmpty()) {
            throw new IllegalStateException("QIO plan revision still has physical assets to drain");
        }
        for (QIODurableTransferRecord transfer : durableTransfers.values()) {
            if (Objects.equals(jobId, transfer.getOwnerJobId()) &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE) {
                throw new IllegalStateException("QIO plan revision still has an active transfer");
            }
        }
        if (job.getExecutionSlotToken() != null) {
            if (!executionSlots.release(job)) {
                throw new IllegalStateException("QIO replan execution slot could not be released");
            }
        }
        transitionJob(job, QIOCraftingJobState.REPLAN_REQUIRED);
        markDirty();
    }

    public void finishPlanningDrain(@Nonnull UUID jobId) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getState() != QIOCraftingJobState.PLANNING || job.hasActiveOperations() ||
              !requireJobBuffer(jobId).isEmpty()) {
            throw new IllegalStateException("QIO planning job still has physical assets to drain");
        }
        for (QIODurableTransferRecord transfer : durableTransfers.values()) {
            if (Objects.equals(jobId, transfer.getOwnerJobId()) &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE) {
                throw new IllegalStateException("QIO planning job still has an active transfer");
            }
        }
        if (job.getExecutionSlotToken() != null && !executionSlots.release(job)) {
            throw new IllegalStateException("QIO planning execution slot could not be released");
        }
        transitionJob(job, QIOCraftingJobState.PLANNING);
        markDirty();
    }

    public void prepareReplanClaim(@Nonnull UUID jobId,
          @Nonnull QIOPlanRevisionTransition.ClaimMode mode, @Nonnull UUID requestId,
          @Nullable UUID sourceClaimId, long contentsRevision, long claimRevision,
          @Nonnull Map<PortableResourceDescriptor, Long> targetAmounts) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getState() != QIOCraftingJobState.REPLAN_REQUIRED ||
              job.getRevisionTransition() == null || hasPhysicalJobAssets(jobId)) {
            throw new IllegalStateException("QIO job is not ready to prepare its new claim");
        }
        job.getRevisionTransition().prepareClaim(mode, requestId, sourceClaimId,
              contentsRevision, claimRevision, targetAmounts);
        markDirty();
    }

    public void clearReplanClaimIntent(@Nonnull UUID jobId) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getRevisionTransition() == null ||
              !job.getRevisionTransition().hasClaimIntent()) {
            throw new IllegalStateException("QIO job has no replan claim intent");
        }
        job.getRevisionTransition().clearClaimIntent();
        markDirty();
    }

    public void completeReplan(@Nonnull UUID jobId,
          @Nonnull Map<PortableResourceDescriptor, Long> committed, long claimRevision) {
        QIOCraftingJob job = requireJob(jobId);
        QIOPlanRevisionTransition transition = job.getRevisionTransition();
        if (transition == null || job.getState() != QIOCraftingJobState.REPLAN_REQUIRED ||
              hasPhysicalJobAssets(jobId)) {
            throw new IllegalStateException("QIO job cannot activate its pending plan");
        }
        QIOMaterialCommitment old = requireCommitment(jobId);
        waitingMaterialIndex.update(jobId, old.getMissingResourceKeys(), Collections.emptySet());
        QIOCraftPlan pending = transition.getPendingPlan();
        QIOMaterialCommitment replacement = new QIOMaterialCommitment(jobId,
              pending.getRevision(), transition.getTargetClaimId(), job.getBasePriority(),
              job.getEnqueueSequence(), pending.getMaterialRequirements());
        replacement.applyActiveClaim(committed, claimRevision);
        commitments.put(jobId, replacement);
        job.activatePendingPlan();
        waitingMaterialIndex.update(jobId, Collections.emptySet(),
              replacement.getMissingResourceKeys());
        transitionJob(job, replacement.isFullyCommitted() ?
              QIOCraftingJobState.WAITING_EXECUTION_SLOT : QIOCraftingJobState.WAITING_MATERIALS);
        advanceTaskCommitmentRevision();
        updateClaimWakeGeneration(jobId, false);
        markDirty();
    }

    /** 请求取消任务并加入取消队列。 */
    public boolean requestJobCancellation(@Nonnull UUID jobId) {
        QIOCraftingJob job = requireJob(jobId);
        boolean wasWakeCandidate = isClaimWakeCandidate(jobId);
        if (!job.getState().isTerminal() && !job.isCancellationRequested() &&
              taskCommitmentRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO task commitment revision exhausted");
        }
        if (!job.requestCancellation()) {
            return false;
        }
        if (job.getState() == QIOCraftingJobState.PLANNING &&
              job.getRevisionTransition() == null) {
            job.transitionTo(hasPhysicalJobAssets(jobId) ?
                  QIOCraftingJobState.PROCESSING : QIOCraftingJobState.WAITING_MATERIALS);
        }
        if (job.getRevisionTransition() != null) {
            job.cancelPendingReplan();
            if (job.getState() == QIOCraftingJobState.REPLAN_REQUIRED) {
                transitionJob(job, QIOCraftingJobState.WAITING_MATERIALS);
            } else if (job.getState() == QIOCraftingJobState.REPLAN_DRAINING) {
                transitionJob(job, QIOCraftingJobState.PROCESSING);
            }
        }
        advanceTaskCommitmentRevision();
        if (job.getSource() == QIOCraftingJobSource.MAINTENANCE) {
            maintenanceRules.clearOutstandingForJob(jobId);
        }
        waitingExecutionSlotJobs.remove(jobId);
        if (wasWakeCandidate != isClaimWakeCandidate(jobId)) {
            advanceClaimWakeGeneration();
        }
        refreshJobIndexes(job);
        markDirty();
        return true;
    }

    @Nonnull
    public List<QIOCraftingJob> getCancellationCandidates() {
        return peekIndexed(cancellationJobs, jobs, Integer.MAX_VALUE);
    }

    @Nonnull
    public List<QIOCraftingJob> pollCancellationCandidates(int maximum) {
        return pollIndexed(cancellationJobs, jobs, maximum);
    }

    public void stageJobMachineInput(@Nonnull UUID jobId,
          @Nonnull Map<PortableResourceDescriptor, Long> resources) {
        requireJobBuffer(jobId).stageMachineInput(resources);
        markDirty();
    }

    public void commitJobMachineInput(@Nonnull UUID jobId,
          @Nonnull Map<PortableResourceDescriptor, Long> resources) {
        requireJobBuffer(jobId).commitMachineInput(resources);
        markDirty();
    }

    public void restoreJobMachineInput(@Nonnull UUID jobId,
          @Nonnull Map<PortableResourceDescriptor, Long> resources) {
        requireJobBuffer(jobId).restoreMachineInput(resources);
        markDirty();
    }

    public void creditJobProduced(@Nonnull UUID jobId,
          @Nonnull Map<PortableResourceDescriptor, Long> resources) {
        QIOJobBuffer buffer = requireJobBuffer(jobId);
        resources.forEach((resource, amount) -> buffer.add(QIOJobBuffer.Compartment.PRODUCED,
              resource, amount));
        markDirty();
    }

    /**
     * Moves root output that cannot be consumed by any unfinished operation into the delivery-only
     * compartment. Keeping the still-required amount in PRODUCED/RESERVED preserves seeded cycles
     * such as E -> 2E and replacement routes such as A + B -> A.
     */
    public long settleProgressiveRootOutput(@Nonnull UUID jobId) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getState().isTerminal() || job.isCancellationRequested() ||
              job.getRemainingGuaranteedRootAmount() <= 0) {
            return 0;
        }
        QIOJobBuffer buffer = requireJobBuffer(jobId);
        PortableResourceDescriptor root = job.getActivePlan().getRootResource();
        long produced = buffer.get(QIOJobBuffer.Compartment.PRODUCED, root);
        if (produced <= 0) {
            return 0;
        }

        long outstandingInputs = 0;
        for (QIOPlanStep step : job.getActivePlan().getSteps()) {
            long perOperation = step.getExactInputs().getOrDefault(root, 0L);
            QIOStepRuntime runtime = job.getStepRuntime(step.getNodeId());
            if (perOperation <= 0 || runtime == null) {
                continue;
            }
            long unfinished = runtime.getRequiredOperations() - runtime.getCompletedOperations();
            outstandingInputs = saturatedAdd(outstandingInputs,
                  saturatedMultiply(perOperation, unfinished));
        }
        long alreadyStaged = buffer.get(QIOJobBuffer.Compartment.IN_PROCESS_RETURN, root);
        long unstagedInputs = Math.max(0, outstandingInputs - alreadyStaged);
        long available = saturatedAdd(produced,
              buffer.get(QIOJobBuffer.Compartment.RESERVED, root));
        long safeProduced = Math.min(produced, Math.max(0, available - unstagedInputs));

        long pendingDelivery = saturatedAdd(
              buffer.get(QIOJobBuffer.Compartment.SETTLED_OUTPUT, root),
              buffer.get(QIOJobBuffer.Compartment.RETURNING, root));
        long deliveryAllowance = Math.max(0,
              job.getRemainingGuaranteedRootAmount() - pendingDelivery);
        long settled = Math.min(safeProduced, deliveryAllowance);
        if (settled > 0) {
            buffer.settleProducedOutput(root, settled);
            markDirty();
        }
        return settled;
    }

    public boolean hasStagedJobMachineInput(@Nonnull UUID jobId,
          @Nonnull Map<PortableResourceDescriptor, Long> resources) {
        return requireJobBuffer(jobId).contains(QIOJobBuffer.Compartment.IN_PROCESS_RETURN,
              resources);
    }

    /** 绑定网络状态变化回调。 */
    public void bindDirtyListener(@Nullable Runnable dirtyListener) {
        this.dirtyListener = dirtyListener;
        if (repairedOnLoad && dirtyListener != null) {
            dirtyListener.run();
        }
    }

    /** 更新网络最近观测的频率身份。 */
    public void updateFrequencyIdentity(@Nonnull QIOFrequencyIdentitySnapshot identity) {
        QIOFrequencyIdentitySnapshot next = Objects.requireNonNull(identity, "identity");
        if (!sameIdentity(lastKnownFrequencyIdentity, next)) {
            lastKnownFrequencyIdentity = next;
            markDirty();
        }
    }

    /** 设置网络生命周期并触发对应索引刷新。 */
    public void setLifecycle(@Nonnull QIOProcessingNetworkLifecycle lifecycle) {
        QIOProcessingNetworkLifecycle next = Objects.requireNonNull(lifecycle, "lifecycle");
        if (this.lifecycle != next) {
            this.lifecycle = next;
            for (QIOCraftingJob job : jobs.values()) {
                if (!job.getState().isTerminal()) {
                    transitionJob(job, next == QIOProcessingNetworkLifecycle.ORPHANED_FREQUENCY ?
                          QIOCraftingJobState.ORPHANED_FREQUENCY : QIOCraftingJobState.WAITING_ACCESS);
                }
            }
            markDirty();
        }
    }

    @Nonnull
    /** 创建并登记一个新的合成任务。 */
    public QIOCraftingJob createJob(@Nonnull QIOCraftingJobSource source, @Nullable UUID requester,
          long priority, long createdAtTick, @Nonnull QIOCraftPlan plan, int maximumNonTerminalJobs) {
        return createJob(UUID.randomUUID(), source, requester, priority, createdAtTick, plan,
              maximumNonTerminalJobs);
    }

    @Nonnull
    /** 从持久化标识创建并登记任务。 */
    public QIOCraftingJob createJob(@Nonnull UUID jobId, @Nonnull QIOCraftingJobSource source,
          @Nullable UUID requester, long priority, long createdAtTick, @Nonnull QIOCraftPlan plan,
          int maximumNonTerminalJobs) {
        if (maximumNonTerminalJobs <= 0) {
            throw new IllegalArgumentException("maximumNonTerminalJobs must be positive");
        }
        long activeJobs = jobs.values().stream().filter(job -> !job.getState().isTerminal()).count();
        if (activeJobs >= maximumNonTerminalJobs) {
            throw new IllegalStateException("This QIO frequency reached its non-terminal job limit");
        }
        Objects.requireNonNull(jobId, "jobId");
        if (jobs.containsKey(jobId)) {
            throw new IllegalArgumentException("Duplicate QIO job ID " + jobId);
        }
        if (enqueueCounter == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO enqueue sequence exhausted and requires rebaselining");
        }
        if (taskCommitmentRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO task commitment revision exhausted");
        }
        long enqueueSequence = enqueueCounter++;
        QIOCraftingJob job = new QIOCraftingJob(jobId, source, requester, priority,
              enqueueSequence, createdAtTick, Objects.requireNonNull(plan, "plan"));
        QIOMaterialCommitment commitment = new QIOMaterialCommitment(jobId, plan.getRevision(),
              UUID.randomUUID(), priority, enqueueSequence, plan.getMaterialRequirements());
        transitionJob(job, commitment.getMissingAmounts().isEmpty() ?
              QIOCraftingJobState.WAITING_EXECUTION_SLOT : QIOCraftingJobState.WAITING_MATERIALS);
        jobs.put(jobId, job);
        commitments.put(jobId, commitment);
        jobBuffers.put(jobId, new QIOJobBuffer(jobId));
        waitingMaterialIndex.update(jobId, Collections.emptySet(),
              commitment.getMissingResourceKeys());
        advanceTaskCommitmentRevision();
        if (isClaimWakeCandidate(jobId)) {
            advanceClaimWakeGeneration();
        }
        markDirty();
        return job;
    }

    @Nonnull
    public List<QIOCraftingJob> getWaitingJobs(@Nonnull PortableResourceDescriptor resource) {
        Set<UUID> waiting = waitingMaterialIndex.getWaitingJobs(resource);
        List<QIOCraftingJob> ordered = new ArrayList<>(waiting.size());
        for (UUID jobId : waiting) {
            QIOCraftingJob job = jobs.get(jobId);
            if (job != null && job.getState() == QIOCraftingJobState.WAITING_MATERIALS) {
                ordered.add(job);
            }
        }
        ordered.sort(CLAIM_ORDER);
        return Collections.unmodifiableList(ordered);
    }

    @Nonnull
    public List<QIOCraftingJob> getClaimRefreshCandidates() {
        List<QIOCraftingJob> ordered = new ArrayList<>();
        for (QIOCraftingJob job : jobs.values()) {
            if (isClaimRefreshCandidate(job.getJobId())) {
                ordered.add(job);
            }
        }
        ordered.sort(CLAIM_ORDER);
        return Collections.unmodifiableList(ordered);
    }

    public boolean isClaimRefreshCandidate(@Nullable UUID jobId) {
        QIOCraftingJob job = jobId == null ? null : jobs.get(jobId);
        QIOMaterialCommitment commitment = jobId == null ? null : commitments.get(jobId);
        if (job == null || commitment == null || job.getState().isTerminal() ||
              job.isCancellationRequested()) {
            return false;
        }
        if (job.getState() != QIOCraftingJobState.WAITING_MATERIALS &&
              job.getState() != QIOCraftingJobState.WAITING_ACCESS) {
            return false;
        }
        return (commitment.getState() == QIOMaterialCommitment.State.ACTIVE ||
              commitment.getState() == QIOMaterialCommitment.State.NEEDS_RECONCILIATION) &&
              (commitment.getPendingMutation() != null ||
                    !commitment.getMissingAmounts().isEmpty() ||
                    job.getState() == QIOCraftingJobState.WAITING_ACCESS);
    }

    public boolean hasClaimRefreshCandidates() {
        for (UUID jobId : jobs.keySet()) {
            if (isClaimRefreshCandidate(jobId)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    public List<QIOCraftingJob> getClaimWakeCandidates() {
        List<QIOCraftingJob> ordered = new ArrayList<>();
        for (QIOCraftingJob job : jobs.values()) {
            if (isClaimWakeCandidate(job.getJobId())) {
                ordered.add(job);
            }
        }
        ordered.sort(CLAIM_ORDER);
        return Collections.unmodifiableList(ordered);
    }

    public boolean isClaimWakeCandidate(@Nullable UUID jobId) {
        QIOCraftingJob job = jobId == null ? null : jobs.get(jobId);
        QIOMaterialCommitment commitment = jobId == null ? null : commitments.get(jobId);
        if (job == null || commitment == null || job.getExecutionSlotToken() != null ||
              job.getState().isTerminal() || job.isCancellationRequested()) {
            return false;
        }
        if (job.getState() != QIOCraftingJobState.WAITING_MATERIALS &&
              job.getState() != QIOCraftingJobState.WAITING_ACCESS &&
              job.getState() != QIOCraftingJobState.WAITING_EXECUTION_SLOT) {
            return false;
        }
        return commitment.getState() == QIOMaterialCommitment.State.ACTIVE ||
              commitment.getState() == QIOMaterialCommitment.State.NEEDS_RECONCILIATION;
    }

    public boolean hasClaimWakeCandidates() {
        for (UUID jobId : jobs.keySet()) {
            if (isClaimWakeCandidate(jobId)) {
                return true;
            }
        }
        return false;
    }

    public long getClaimWakeGeneration() {
        return claimWakeGeneration;
    }

    public void prepareClaimMutation(@Nonnull UUID jobId,
          @Nonnull QIOPendingClaimMutation mutation) {
        requireCommitment(jobId).prepare(mutation);
        markDirty();
    }

    public void prepareClaimRelease(@Nonnull UUID jobId,
          @Nonnull QIOPendingClaimMutation mutation) {
        boolean wasWakeCandidate = isClaimWakeCandidate(jobId);
        QIOCraftingJob job = requireJob(jobId);
        if (job.getExecutionSlotToken() != null || !requireJobBuffer(jobId).isEmpty()) {
            throw new IllegalStateException("A physically active QIO job requires the return protocol");
        }
        QIOMaterialCommitment commitment = requireCommitment(jobId);
        waitingMaterialIndex.update(jobId, commitment.getMissingResourceKeys(),
              Collections.emptySet());
        commitment.beginRelease(mutation);
        transitionJob(job, QIOCraftingJobState.CANCEL_REQUESTED);
        updateClaimWakeGeneration(jobId, wasWakeCandidate);
        markDirty();
    }

    public void completeClaimRelease(@Nonnull UUID jobId, long claimRevision) {
        QIOCraftingJob job = requireJob(jobId);
        if (job.getExecutionSlotToken() != null || !requireJobBuffer(jobId).isEmpty()) {
            throw new IllegalStateException("Cannot finish cancellation while physical assets remain");
        }
        QIOMaterialCommitment commitment = requireCommitment(jobId);
        waitingMaterialIndex.update(jobId, commitment.getMissingResourceKeys(),
              Collections.emptySet());
        commitment.markReleased(claimRevision);
        transitionJob(job, QIOCraftingJobState.CANCELLED);
        markDirty();
    }

    public void applyActiveClaim(@Nonnull UUID jobId,
          @Nonnull Map<PortableResourceDescriptor, Long> committed, long claimRevision) {
        boolean wasWakeCandidate = isClaimWakeCandidate(jobId);
        QIOMaterialCommitment commitment = requireCommitment(jobId);
        Collection<PortableResourceDescriptor> previousMissing =
              new ArrayList<>(commitment.getMissingResourceKeys());
        commitment.applyActiveClaim(committed, claimRevision);
        waitingMaterialIndex.update(jobId, previousMissing,
              commitment.getMissingResourceKeys());
        QIOCraftingJob job = requireJob(jobId);
        if (job.getExecutionSlotToken() == null && !job.getState().isTerminal()) {
            transitionJob(job, commitment.isFullyCommitted() ?
                  QIOCraftingJobState.WAITING_EXECUTION_SLOT : QIOCraftingJobState.WAITING_MATERIALS);
        }
        updateClaimWakeGeneration(jobId, wasWakeCandidate);
        markDirty();
    }

    public void markClaimNeedsReconciliation(@Nonnull UUID jobId) {
        boolean wasWakeCandidate = isClaimWakeCandidate(jobId);
        QIOMaterialCommitment commitment = requireCommitment(jobId);
        waitingMaterialIndex.update(jobId, commitment.getMissingResourceKeys(),
              Collections.emptySet());
        commitment.markNeedsReconciliation();
        transitionJob(requireJob(jobId), QIOCraftingJobState.WAITING_ACCESS);
        updateClaimWakeGeneration(jobId, wasWakeCandidate);
        markDirty();
    }

    public boolean markClaimStorageAccessUnavailable() {
        boolean changed = false;
        for (QIOCraftingJob job : jobs.values()) {
            if ((job.getState() == QIOCraftingJobState.WAITING_MATERIALS ||
                  job.getState() == QIOCraftingJobState.WAITING_EXECUTION_SLOT) &&
                  isClaimWakeCandidate(job.getJobId())) {
                // Keep the commitment and any durable request ID intact. Access loss alone does
                // not prove whether a submitted core mutation committed.
                transitionJob(job, QIOCraftingJobState.WAITING_ACCESS);
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
        return changed;
    }

    @Nullable
    public ActiveExecutionSlot acquireExecutionSlot(@Nonnull UUID jobId, int configuredLimit) {
        QIOCraftingJob job = requireJob(jobId);
        boolean wasWakeCandidate = isClaimWakeCandidate(jobId);
        QIOMaterialCommitment commitment = requireCommitment(jobId);
        if (job.getState() != QIOCraftingJobState.WAITING_EXECUTION_SLOT ||
              !commitment.isFullyCommitted() || job.isCancellationRequested()) {
            throw new IllegalStateException("QIO job is not eligible for an execution slot");
        }
        if (dispatchCounter == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO dispatch sequence exhausted and requires rebaselining");
        }
        ActiveExecutionSlot slot = executionSlots.acquire(job, configuredLimit, schedulerClock);
        if (slot != null) {
            job.recordDispatch(nextDispatchSequence());
            transitionJob(job, QIOCraftingJobState.RESERVING);
            updateClaimWakeGeneration(jobId, wasWakeCandidate);
            markDirty();
        }
        return slot;
    }

    public boolean releaseExecutionSlot(@Nonnull UUID jobId,
          @Nonnull QIOCraftingJobState nextState) {
        QIOCraftingJob job = requireJob(jobId);
        boolean wasWakeCandidate = isClaimWakeCandidate(jobId);
        if (!executionSlots.release(job)) {
            return false;
        }
        transitionJob(job, Objects.requireNonNull(nextState, "nextState"));
        updateClaimWakeGeneration(jobId, wasWakeCandidate);
        markDirty();
        return true;
    }

    /** 添加 durable transfer 并按其所有者更新网络索引。 */
    public void addDurableTransfer(@Nonnull QIODurableTransferRecord transfer) {
        Objects.requireNonNull(transfer, "transfer");
        if (durableTransfers.putIfAbsent(transfer.getTransferId(), transfer) != null) {
            throw new IllegalArgumentException("Duplicate durable QIO transfer " + transfer.getTransferId());
        }
        if (transfer.getOwnerJobId() != null && !jobs.containsKey(transfer.getOwnerJobId())) {
            durableTransfers.remove(transfer.getTransferId());
            throw new IllegalArgumentException("Durable transfer references an unknown job");
        }
        if (transfer.getType() == QIODurableTransferRecord.Type.QIO_TO_JOB) {
            QIOCraftingJob job = requireJob(transfer.getOwnerJobId());
            QIOMaterialCommitment commitment = requireCommitment(job.getJobId());
            if (job.getState() != QIOCraftingJobState.RESERVING ||
                  transfer.getPlanRevision() != job.getActivePlan().getRevision() ||
                  !transfer.getResources().equals(commitment.getCommittedAmounts()) ||
                  !commitment.isFullyCommitted()) {
                durableTransfers.remove(transfer.getTransferId());
                throw new IllegalArgumentException("QIO reservation transfer disagrees with its job");
            }
        }
        markDirty();
    }

    /**
     * Creates the durable QIO return leg for a configuration target which was already debited
     * but has not reached the machine.  The record is written before any insertion is attempted;
     * a crash can therefore only replay the same idempotent transfer, never create an unowned
     * template debit.
     */
    @Nonnull
    public QIODurableTransferRecord ensureConfigurationTargetReturnTransfer(
          @Nonnull QIOConfigurationExchangeRecord exchange, @Nonnull BigInteger qioBaseline) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(qioBaseline, "qioBaseline");
        if (!exchange.requiresTargetReturn()) {
            throw new IllegalStateException("Configuration exchange has no target return obligation");
        }
        if (exchange.getTargetQioBaseline() == null ||
              !qioBaseline.equals(exchange.getTargetQioBaseline().subtract(BigInteger.ONE))) {
            throw new IllegalStateException(
                  "Configuration target return baseline disagrees with the target debit");
        }
        QIODurableTransferRecord existing = durableTransfers.get(
              exchange.getTargetReturnTransferId());
        if (existing != null) {
            if (!isConfigurationTargetReturnTransfer(existing, exchange, qioBaseline, false)) {
                throw new IllegalStateException(
                      "Configuration target return transfer disagrees with its exchange");
            }
            return existing;
        }
        if (qioBaseline.signum() < 0) {
            throw new IllegalArgumentException("Configuration target return baseline cannot be negative");
        }
        Map<PortableResourceDescriptor, Long> resources = Collections.singletonMap(
              exchange.getTargetResource(), 1L);
        Map<PortableResourceDescriptor, BigInteger> baselines = Collections.singletonMap(
              exchange.getTargetResource(), qioBaseline);
        UUID ownerOperationId = exchange.getOwnerJobId() == null ? exchange.getOperationId() : null;
        UUID ownerJobId = exchange.getOwnerJobId();
        int planRevision = ownerJobId == null ? 0 :
              getJob(ownerJobId) == null ? 1 : getJob(ownerJobId).getActivePlan().getRevision();
        QIODurableTransferRecord transfer = new QIODurableTransferRecord(
              exchange.getTargetReturnTransferId(), UUID.randomUUID(),
              QIODurableTransferRecord.Type.JOB_TO_QIO, ownerJobId, ownerOperationId,
              planRevision, "configuration-return/" + exchange.getExchangeId() + "/" +
                    exchange.getOperationId(),
              exchange.getLeaseId(),
              "configuration-held/" + exchange.getDeviceUUID() + "/" + exchange.getExchangeId() +
                    "/" + exchange.getOperationId(),
              "qio/" + frequencyUUID, resources, baselines);
        addDurableTransfer(transfer);
        return transfer;
    }

    private boolean isConfigurationTargetReturnTransfer(
          QIODurableTransferRecord transfer, QIOConfigurationExchangeRecord exchange,
          BigInteger baseline, boolean allowCommitted) {
        return transfer.getType() == QIODurableTransferRecord.Type.JOB_TO_QIO &&
              transfer.getTransferId().equals(exchange.getTargetReturnTransferId()) &&
              Objects.equals(transfer.getOwnerJobId(), exchange.getOwnerJobId()) &&
              Objects.equals(transfer.getOwnerOperationId(),
                    exchange.getOwnerJobId() == null ? exchange.getOperationId() : null) &&
              transfer.getLeaseId() != null && transfer.getLeaseId().equals(exchange.getLeaseId()) &&
              transfer.getNodeId().equals("configuration-return/" + exchange.getExchangeId() + "/" +
                    exchange.getOperationId()) &&
              transfer.getSource().equals("configuration-held/" + exchange.getDeviceUUID() + "/" +
                    exchange.getExchangeId() + "/" + exchange.getOperationId()) &&
              transfer.getDestination().equals("qio/" + frequencyUUID) &&
              transfer.getResources().equals(Collections.singletonMap(
                    exchange.getTargetResource(), 1L)) &&
              transfer.getQIOBaselines().equals(Collections.singletonMap(
                    exchange.getTargetResource(), baseline)) &&
              (allowCommitted && transfer.getResolution() ==
                    QIODurableTransferRecord.Resolution.FORWARD_COMMITTED ||
                    transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE);
    }

    @Nullable
    /** 查询任务当前的 QIO 预留 transfer。 */
    public QIODurableTransferRecord getReservationTransfer(@Nonnull UUID jobId) {
        QIODurableTransferRecord found = null;
        for (QIODurableTransferRecord transfer : durableTransfers.values()) {
            if (transfer.getType() == QIODurableTransferRecord.Type.QIO_TO_JOB &&
                  Objects.equals(jobId, transfer.getOwnerJobId()) &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE) {
                if (found != null) {
                    throw new IllegalStateException("QIO job has more than one active reservation transfer");
                }
                found = transfer;
            }
        }
        return found;
    }

    @Nonnull
    /** 返回任务拥有的未结算 transfer。 */
    public List<QIODurableTransferRecord> getActiveJobTransfers(@Nonnull UUID jobId,
          @Nonnull QIODurableTransferRecord.Type type) {
        List<QIODurableTransferRecord> result = new ArrayList<>();
        for (QIODurableTransferRecord transfer : durableTransfers.values()) {
            if (Objects.equals(jobId, transfer.getOwnerJobId()) && transfer.getType() == type &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE) {
                result.add(transfer);
            }
        }
        result.sort(Comparator.comparing(transfer -> transfer.getTransferId().toString()));
        return Collections.unmodifiableList(result);
    }

    @Nonnull
    /** 返回独立机器操作拥有的未结算 transfer。 */
    public List<QIODurableTransferRecord> getActiveOperationTransfers(
          @Nonnull UUID operationId, @Nonnull QIODurableTransferRecord.Type type) {
        List<QIODurableTransferRecord> result = new ArrayList<>();
        for (QIODurableTransferRecord transfer : durableTransfers.values()) {
            if (Objects.equals(operationId, transfer.getOwnerOperationId()) &&
                  transfer.getType() == type &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE) {
                result.add(transfer);
            }
        }
        result.sort(Comparator.comparing(transfer -> transfer.getTransferId().toString()));
        return Collections.unmodifiableList(result);
    }

    /** 记录通用 transfer 来源已扣除。 */
    public void markGenericTransferSourceDebited(@Nonnull UUID transferId,
          @Nonnull String receipt, long sourceRevision) {
        QIODurableTransferRecord transfer = requireGenericTransfer(transferId);
        transfer.markSourceDebited(receipt, sourceRevision);
        markDirty();
    }

    /** 记录通用 transfer 目标已入账。 */
    public void markGenericTransferDestinationCredited(@Nonnull UUID transferId,
          @Nonnull String receipt) {
        QIODurableTransferRecord transfer = requireGenericTransfer(transferId);
        transfer.markDestinationCredited(receipt);
        markDirty();
    }

    /** 结算通用 transfer 的正向路径。 */
    public void commitGenericTransfer(@Nonnull UUID transferId) {
        QIODurableTransferRecord transfer = requireGenericTransfer(transferId);
        transfer.commitForward();
        markDirty();
    }

    /** 删除尚未扣除来源的 PREPARED 通用 transfer。 */
    public void discardPreparedGenericTransfer(@Nonnull UUID transferId) {
        QIODurableTransferRecord transfer = requireGenericTransfer(transferId);
        if (transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED) {
            throw new IllegalStateException("Only a prepared generic transfer can be discarded");
        }
        durableTransfers.remove(transferId);
        markDirty();
    }

    /**
     * 丢弃被动输入在“仅网络侧暂存、尚未确认机器收取”阶段的 ownership。
     * 该操作不会触碰被动操作自己的 inputBuffer；调用方必须先用机器基线证明
     * 物理端点仍未变化，才能把 SOURCE_DEBITED 记录安全地移除。
     */
    public void discardUnconfirmedPassiveMachineTransfer(@Nonnull UUID transferId) {
        QIODurableTransferRecord transfer = requireGenericTransfer(transferId);
        if (transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_MACHINE ||
              transfer.getOwnerJobId() != null || transfer.getOwnerOperationId() == null ||
              transfer.getPhase() != QIODurableTransferRecord.Phase.SOURCE_DEBITED ||
              transfer.getResolution() != QIODurableTransferRecord.Resolution.NONE) {
            throw new IllegalStateException(
                  "Only an unconfirmed passive machine input can be discarded");
        }
        durableTransfers.remove(transferId);
        markDirty();
    }

    /** Removes only fully settled transfers after their external operation owner was durably acknowledged. */
    /** 删除指定操作已经结算且无回退需求的 transfer。 */
    public int removeCommittedOperationTransfers(@Nonnull UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        for (QIODurableTransferRecord transfer : durableTransfers.values()) {
            if (operationId.equals(transfer.getOwnerOperationId()) &&
                transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE) {
                throw new IllegalStateException("Cannot remove an operation with an active durable transfer");
            }
        }
        for (QIOConfigurationExchangeRecord exchange : configurationExchanges.values()) {
            if (operationId.equals(exchange.getOperationId()) &&
                  !exchange.getPhase().isSettled()) {
                throw new IllegalStateException(
                      "Cannot remove an operation with an active configuration exchange");
            }
        }
        int before = durableTransfers.size();
        durableTransfers.entrySet().removeIf(entry -> operationId.equals(
              entry.getValue().getOwnerOperationId()));
        int removed = before - durableTransfers.size();
        int exchangesBefore = configurationExchanges.size();
        configurationExchanges.entrySet().removeIf(entry -> operationId.equals(
              entry.getValue().getOperationId()));
        removed = Math.addExact(removed, exchangesBefore - configurationExchanges.size());
        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    /**
     * Explicitly discards durable ownership records for a force-cleared machine operation.
     * Inputs already staged in the job's in-process compartment are removed as the user-visible
     * lost-material consequence of this action; configuration exchanges with an outstanding QIO
     * claim remain as marked cleanup records until that claim is released.
     */
    /** 强制恢复中清理指定操作的本地 transfer 所有权。 */
    public int forceDiscardOperationOwnership(@Nonnull UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        int removed = 0;
        for (QIODurableTransferRecord transfer : new ArrayList<>(durableTransfers.values())) {
            if (!ownsOperation(transfer, operationId)) {
                continue;
            }
            // A configuration target may already have been debited from QIO.  Its return leg
            // is an independent durable ownership record and must survive a host force-clear
            // until the idempotent QIO credit has completed.
            if (isPendingConfigurationTargetReturn(transfer)) {
                continue;
            }
            if (transfer.getType() == QIODurableTransferRecord.Type.JOB_TO_MACHINE &&
                transfer.getOwnerJobId() != null) {
                QIOJobBuffer buffer = jobBuffers.get(transfer.getOwnerJobId());
                if (buffer != null) {
                    for (Map.Entry<PortableResourceDescriptor, Long> resource :
                          transfer.getResources().entrySet()) {
                        long staged = Math.min(resource.getValue(), buffer.get(
                              QIOJobBuffer.Compartment.IN_PROCESS_RETURN, resource.getKey()));
                        if (staged > 0) {
                            buffer.remove(QIOJobBuffer.Compartment.IN_PROCESS_RETURN,
                                  resource.getKey(), staged);
                        }
                    }
                }
            }
            durableTransfers.remove(transfer.getTransferId());
            removed++;
        }
        int exchangesBefore = configurationExchanges.size();
        configurationExchanges.entrySet().removeIf(entry -> {
            QIOConfigurationExchangeRecord exchange = entry.getValue();
            if (!operationId.equals(exchange.getOperationId())) {
                return false;
            }
            if (exchange.requiresTargetReturn()) {
                if (!exchange.isForceRecoveryPending()) {
                    exchange.contaminate(QIOConfigurationExchangeRecord.FORCE_RECOVERY_PENDING_PREFIX +
                          "operation ownership was explicitly cleared");
                    markConfigurationExchangeChanged(exchange.getExchangeId());
                }
                return false;
            }
            return !exchange.hasOutstandingClaim();
        });
        removed = Math.addExact(removed, exchangesBefore - configurationExchanges.size());
        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    private boolean isPendingConfigurationTargetReturn(QIODurableTransferRecord transfer) {
        if (transfer.getResolution() != QIODurableTransferRecord.Resolution.NONE ||
              transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_QIO) {
            return false;
        }
        for (QIOConfigurationExchangeRecord exchange : configurationExchanges.values()) {
            if (exchange.getTargetReturnTransferId().equals(transfer.getTransferId()) &&
                  exchange.requiresTargetReturn()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A scheduled machine transfer stores its job as the durable owner and embeds the
     * operation UUID in its operation-key node. Treat that form as the same ownership
     * record when a host is explicitly force-cleared.
     */
    private static boolean ownsOperation(QIODurableTransferRecord transfer, UUID operationId) {
        if (operationId.equals(transfer.getOwnerOperationId())) {
            return true;
        }
        return transfer.getOwnerJobId() != null &&
              transfer.getNodeId().endsWith("/" + operationId);
    }

    /**
     * Drops machine-side passive transfer records while retaining an in-flight QIO return
     * transfer. The passive operation owns the retained return buffer and can finish it without
     * the machine host after a force-clear.
     */
    /**
     * 清理被动机器操作中尚未跨过物理边界的 transfer 所有权。
     * 已扣源或已入账的记录必须保留给阶段化恢复，不能因普通污染处理而删除。
     */
    public int forceDiscardPassiveMachineOwnership(@Nonnull UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        int removed = 0;
        for (QIODurableTransferRecord transfer : new ArrayList<>(durableTransfers.values())) {
            if (operationId.equals(transfer.getOwnerOperationId()) &&
                  transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_QIO &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                  transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
                durableTransfers.remove(transfer.getTransferId());
                removed++;
            }
        }
        int before = configurationExchanges.size();
        configurationExchanges.entrySet().removeIf(entry -> {
            QIOConfigurationExchangeRecord exchange = entry.getValue();
            if (!operationId.equals(exchange.getOperationId())) {
                return false;
            }
            if (exchange.requiresTargetReturn()) {
                if (!exchange.isForceRecoveryPending()) {
                    exchange.contaminate(QIOConfigurationExchangeRecord.FORCE_RECOVERY_PENDING_PREFIX +
                          "operation ownership was explicitly cleared");
                    markConfigurationExchangeChanged(exchange.getExchangeId());
                }
                return false;
            }
            return !exchange.hasOutstandingClaim();
        });
        removed = Math.addExact(removed, before - configurationExchanges.size());
        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    /**
     * 用户明确接受材料损失后，删除被动操作的全部机器侧 ownership 记录。
     * 普通污染和自动重试路径不得调用此方法。
     */
    public int forceDiscardPassiveMachineOwnershipAfterLossAccepted(@Nonnull UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        int removed = 0;
        for (QIODurableTransferRecord transfer : new ArrayList<>(durableTransfers.values())) {
            if (operationId.equals(transfer.getOwnerOperationId()) &&
                  transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_QIO) {
                durableTransfers.remove(transfer.getTransferId());
                removed++;
            }
        }
        int before = configurationExchanges.size();
        configurationExchanges.entrySet().removeIf(entry -> {
            QIOConfigurationExchangeRecord exchange = entry.getValue();
            if (!operationId.equals(exchange.getOperationId())) {
                return false;
            }
            if (exchange.requiresTargetReturn()) {
                if (!exchange.isForceRecoveryPending()) {
                    exchange.contaminate(QIOConfigurationExchangeRecord.FORCE_RECOVERY_PENDING_PREFIX +
                          "operation ownership was explicitly cleared");
                    markConfigurationExchangeChanged(exchange.getExchangeId());
                }
                return false;
            }
            return !exchange.hasOutstandingClaim();
        });
        removed = Math.addExact(removed, before - configurationExchanges.size());
        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    /** Removes a force-recovery exchange after its outstanding claim has been released. */
    /** 删除已由恢复服务结算的配置交换记录。 */
    public boolean removeForcedRecoveryConfigurationExchange(@Nonnull UUID exchangeId) {
        QIOConfigurationExchangeRecord exchange = configurationExchanges.get(
              Objects.requireNonNull(exchangeId, "exchangeId"));
        if (exchange == null || exchange.hasOutstandingClaim() ||
              exchange.getPhase() != QIOConfigurationExchangeRecord.Phase.CANCELLED &&
                    (exchange.getPhase() != QIOConfigurationExchangeRecord.Phase.CONTAMINATED ||
                          !exchange.isForceRecoveryPending())) {
            return false;
        }
        QIODurableTransferRecord returnTransfer = durableTransfers.get(
              exchange.getTargetReturnTransferId());
        if (returnTransfer != null && returnTransfer.getResolution() !=
              QIODurableTransferRecord.Resolution.FORWARD_COMMITTED) {
            return false;
        }
        configurationExchanges.remove(exchangeId);
        if (returnTransfer != null) {
            durableTransfers.remove(returnTransfer.getTransferId());
        }
        markDirty();
        return true;
    }

    /** Removes one settled job operation's handoff history after its endpoint was saved. */
    public int removeCommittedJobOperationTransfers(@Nonnull UUID jobId,
          @Nonnull String operationNodeId) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(operationNodeId, "operationNodeId");
        for (QIODurableTransferRecord transfer : durableTransfers.values()) {
            if (jobId.equals(transfer.getOwnerJobId()) &&
                  operationNodeId.equals(transfer.getNodeId()) &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE) {
                throw new IllegalStateException(
                      "Cannot remove a job operation with an active durable transfer");
            }
        }
        int before = durableTransfers.size();
        durableTransfers.entrySet().removeIf(entry -> jobId.equals(
              entry.getValue().getOwnerJobId()) && operationNodeId.equals(
                    entry.getValue().getNodeId()));
        int removed = before - durableTransfers.size();
        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    public int removeCommittedJobOperationTransfers(@Nonnull UUID jobId,
          @Nonnull UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        for (QIOConfigurationExchangeRecord exchange : configurationExchanges.values()) {
            if (jobId.equals(exchange.getOwnerJobId()) &&
                  operationId.equals(exchange.getOperationId()) &&
                  !exchange.getPhase().isSettled()) {
                throw new IllegalStateException(
                      "Cannot remove a job operation with an active configuration exchange");
            }
        }
        String suffix = "/" + operationId;
        List<String> nodeIds = durableTransfers.values().stream()
              .filter(transfer -> jobId.equals(transfer.getOwnerJobId()) &&
                    transfer.getNodeId().endsWith(suffix))
              .map(QIODurableTransferRecord::getNodeId).distinct()
              .collect(java.util.stream.Collectors.toList());
        int removed = 0;
        for (String nodeId : nodeIds) {
            removed = Math.addExact(removed,
                  removeCommittedJobOperationTransfers(jobId, nodeId));
        }
        int exchangesBefore = configurationExchanges.size();
        configurationExchanges.entrySet().removeIf(entry -> jobId.equals(
              entry.getValue().getOwnerJobId()) && operationId.equals(
                    entry.getValue().getOperationId()));
        removed = Math.addExact(removed, exchangesBefore - configurationExchanges.size());
        if (exchangesBefore != configurationExchanges.size()) markDirty();
        return removed;
    }

    /** Best-effort history compaction for a terminal job. Active handoffs remain untouched. */
    /** 删除任务所有已经结算的 transfer。 */
    public int removeCommittedJobTransfers(@Nonnull UUID jobId) {
        Objects.requireNonNull(jobId, "jobId");
        QIOCraftingJob job = jobs.get(jobId);
        if (job == null || !job.getState().isTerminal()) {
            return 0;
        }
        int before = durableTransfers.size();
        durableTransfers.entrySet().removeIf(entry -> jobId.equals(
              entry.getValue().getOwnerJobId()) && entry.getValue().getResolution() !=
                    QIODurableTransferRecord.Resolution.NONE);
        int removed = before - durableTransfers.size();
        int exchangesBefore = configurationExchanges.size();
        configurationExchanges.entrySet().removeIf(entry -> jobId.equals(
              entry.getValue().getOwnerJobId()) && entry.getValue().getPhase().isSettled());
        removed = Math.addExact(removed, exchangesBefore - configurationExchanges.size());
        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    /** 记录任务向机器发送输入已扣除。 */
    public void debitJobMachineTransfer(@Nonnull UUID transferId) {
        QIODurableTransferRecord transfer = requireGenericTransfer(transferId);
        if (transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_MACHINE ||
              transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED) {
            throw new IllegalStateException("QIO transfer is not a prepared job-to-machine handoff");
        }
        QIOJobBuffer buffer = requireJobBuffer(transfer.getOwnerJobId());
        if (!buffer.contains(QIOJobBuffer.Compartment.IN_PROCESS_RETURN,
              transfer.getResources())) {
            buffer.stageMachineInput(transfer.getResources());
        }
        transfer.markSourceDebited("job-buffer-staged", networkRevision);
        markDirty();
    }

    /** 记录机器输出 transfer 已写入任务缓冲。 */
    public void creditMachineOutputTransfer(@Nonnull UUID transferId) {
        QIODurableTransferRecord transfer = requireGenericTransfer(transferId);
        if (transfer.getType() != QIODurableTransferRecord.Type.MACHINE_TO_JOB ||
              transfer.getPhase() != QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            throw new IllegalStateException("QIO transfer is not a debited machine output handoff");
        }
        QIOJobBuffer buffer = requireJobBuffer(transfer.getOwnerJobId());
        transfer.getResources().forEach((resource, amount) ->
              buffer.add(QIOJobBuffer.Compartment.PRODUCED, resource, amount));
        transfer.markDestinationCredited("job-buffer-produced");
        markDirty();
    }

    /** 完成任务到机器的输入 transfer。 */
    public void completeJobMachineTransfer(@Nonnull UUID transferId) {
        QIODurableTransferRecord transfer = requireGenericTransfer(transferId);
        if (transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_MACHINE ||
              transfer.getPhase() != QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            throw new IllegalStateException("QIO transfer is not a credited job-to-machine handoff");
        }
        QIOJobBuffer buffer = requireJobBuffer(transfer.getOwnerJobId());
        if (buffer.contains(QIOJobBuffer.Compartment.IN_PROCESS_RETURN,
              transfer.getResources())) {
            buffer.commitMachineInput(transfer.getResources());
        }
        transfer.commitForward();
        markDirty();
    }

    /**
     * Restores a staged machine input after the caller proved that the leased machine ports
     * still match their pre-transfer baselines. Both the source buffer and transfer record are
     * local to this persisted network, so the rollback is committed as one network mutation.
     */
    /** 回滚尚未入账机器输入 transfer。 */
    public void rollbackUncreditedJobMachineTransfer(@Nonnull UUID transferId) {
        QIODurableTransferRecord transfer = requireGenericTransfer(transferId);
        if (transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_MACHINE ||
              transfer.getPhase() != QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            throw new IllegalStateException(
                  "QIO transfer is not an uncredited job-to-machine handoff");
        }
        QIOJobBuffer buffer = requireJobBuffer(transfer.getOwnerJobId());
        if (!buffer.contains(QIOJobBuffer.Compartment.IN_PROCESS_RETURN,
              transfer.getResources())) {
            throw new IllegalStateException("QIO job no longer owns the staged machine input");
        }
        buffer.restoreMachineInput(transfer.getResources());
        durableTransfers.remove(transferId);
        markDirty();
    }

    /** 暂存机器输入回退 transfer。 */
    public void stageJobReturnTransfer(@Nonnull UUID transferId) {
        QIODurableTransferRecord transfer = requireGenericTransfer(transferId);
        if (transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_QIO ||
              transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED ||
              transfer.getResources().size() != 1) {
            throw new IllegalStateException("QIO transfer is not a prepared job return");
        }
        Map.Entry<PortableResourceDescriptor, Long> resource =
              transfer.getResources().entrySet().iterator().next();
        QIOJobBuffer buffer = requireJobBuffer(transfer.getOwnerJobId());
        if (!buffer.contains(QIOJobBuffer.Compartment.RETURNING,
              transfer.getResources())) {
            if (PROGRESSIVE_ROOT_DELIVERY_NODE_ID.equals(transfer.getNodeId())) {
                buffer.stageSettledOutputReturn(resource.getKey(), resource.getValue());
            } else {
                buffer.stageReturn(resource.getKey(), resource.getValue());
            }
        }
        transfer.markSourceDebited("job-buffer-returning", networkRevision);
        markDirty();
    }

    /** 完成机器输入回退 transfer。 */
    public void completeJobReturnTransfer(@Nonnull UUID transferId) {
        QIODurableTransferRecord transfer = requireGenericTransfer(transferId);
        if (transfer.getType() != QIODurableTransferRecord.Type.JOB_TO_QIO ||
              transfer.getPhase() != QIODurableTransferRecord.Phase.DESTINATION_CREDITED ||
              transfer.getResources().size() != 1) {
            throw new IllegalStateException("QIO transfer is not a credited job return");
        }
        Map.Entry<PortableResourceDescriptor, Long> resource =
              transfer.getResources().entrySet().iterator().next();
        requireJobBuffer(transfer.getOwnerJobId()).commitReturn(resource.getKey(),
              resource.getValue());
        QIOCraftingJob job = requireJob(transfer.getOwnerJobId());
        if ((job.getState() == QIOCraftingJobState.DELIVERING ||
              PROGRESSIVE_ROOT_DELIVERY_NODE_ID.equals(transfer.getNodeId())) &&
              resource.getKey().equals(job.getActivePlan().getRootResource())) {
            job.recordRootDelivery(resource.getValue());
        }
        transfer.commitForward();
        markDirty();
    }

    /** 记录 QIO 预留来源扣除回执。 */
    public void markReservationSourceDebited(@Nonnull UUID transferId, @Nonnull String receipt,
          long claimRevision) {
        QIODurableTransferRecord transfer = requireReservationTransfer(transferId);
        transfer.markSourceDebited(receipt, claimRevision);
        markDirty();
    }

    /** 将预留 transfer 标记为目标已入账。 */
    public void creditReservationDestination(@Nonnull UUID transferId) {
        QIODurableTransferRecord transfer = requireReservationTransfer(transferId);
        if (transfer.getPhase() != QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            throw new IllegalStateException("Reservation source has not been durably debited");
        }
        QIOJobBuffer buffer = requireJobBuffer(transfer.getOwnerJobId());
        if (!buffer.get(QIOJobBuffer.Compartment.RESERVED).isEmpty()) {
            throw new IllegalStateException("Reservation destination already contains resources");
        }
        for (Map.Entry<PortableResourceDescriptor, Long> entry : transfer.getResources().entrySet()) {
            buffer.add(QIOJobBuffer.Compartment.RESERVED, entry.getKey(), entry.getValue());
        }
        transfer.markDestinationCredited("job-buffer/" + transfer.getTransferId());
        markDirty();
    }

    /** 完成预留 transfer 并推进任务到 READY。 */
    public void completeReservation(@Nonnull UUID transferId) {
        QIODurableTransferRecord transfer = requireReservationTransfer(transferId);
        if (transfer.getPhase() != QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            throw new IllegalStateException("Reservation destination is not durably credited");
        }
        QIOCraftingJob job = requireJob(transfer.getOwnerJobId());
        QIOJobBuffer buffer = requireJobBuffer(job.getJobId());
        for (Map.Entry<PortableResourceDescriptor, Long> entry : transfer.getResources().entrySet()) {
            if (buffer.get(QIOJobBuffer.Compartment.RESERVED, entry.getKey()) < entry.getValue()) {
                throw new IllegalStateException("Reservation buffer does not contain the transferred resources");
            }
        }
        transfer.commitForward();
        requireCommitment(job.getJobId()).markConsumed(transfer.getSourceStateRevision());
        transitionJob(job, QIOCraftingJobState.READY);
        // The destination buffer, consumed commitment, and job state are persisted in this
        // same network record. Once all three advance together, the reservation transfer no
        // longer bridges an independent persistence boundary and must not become a permanent
        // historical dependency of the job's later states.
        durableTransfers.remove(transferId);
        markDirty();
    }

    /** 完成无需扣除资源的空预留。 */
    public void completeEmptyReservation(@Nonnull UUID jobId, long claimRevision) {
        QIOCraftingJob job = requireJob(jobId);
        QIOMaterialCommitment commitment = requireCommitment(jobId);
        if (job.getState() != QIOCraftingJobState.RESERVING || job.getExecutionSlotToken() == null ||
              !commitment.getRequiredAmounts().isEmpty() || !requireJobBuffer(jobId).isEmpty()) {
            throw new IllegalStateException("QIO job is not an empty reservation");
        }
        commitment.markConsumed(claimRevision);
        transitionJob(job, QIOCraftingJobState.READY);
        markDirty();
    }

    /** 放弃尚未扣除来源的预留并把任务转入等待状态。 */
    public void abortPreparedReservation(@Nonnull UUID transferId,
          @Nonnull QIOCraftingJobState nextState) {
        QIODurableTransferRecord transfer = requireReservationTransfer(transferId);
        if (transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED) {
            throw new IllegalStateException("A debited reservation transfer cannot be discarded");
        }
        QIOCraftingJob job = requireJob(transfer.getOwnerJobId());
        boolean wasWakeCandidate = isClaimWakeCandidate(job.getJobId());
        durableTransfers.remove(transferId);
        if (!executionSlots.release(job)) {
            throw new IllegalStateException("Reservation job no longer owns its execution slot");
        }
        transitionJob(job, Objects.requireNonNull(nextState, "nextState"));
        updateClaimWakeGeneration(job.getJobId(), wasWakeCandidate);
        markDirty();
    }

    /** 标记 transfer 发生持久化变化。 */
    public void markTransferChanged(@Nonnull UUID transferId) {
        if (!durableTransfers.containsKey(Objects.requireNonNull(transferId, "transferId"))) {
            throw new IllegalArgumentException("Unknown durable QIO transfer " + transferId);
        }
        markDirty();
    }

    /** 分配下一个稳定派发序号。 */
    public long nextDispatchSequence() {
        if (dispatchCounter == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO dispatch sequence exhausted and requires rebaselining");
        }
        long sequence = dispatchCounter++;
        markDirty();
        return sequence;
    }

    /** 推进频率级调度时钟。 */
    public void advanceSchedulerClock() {
        if (schedulerClock == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO scheduler clock exhausted and requires rebaselining");
        }
        schedulerClock++;
        markDirty();
    }

    @Nonnull
    /** 返回等待执行槽的任务候选。 */
    public List<QIOCraftingJob> getExecutionSlotCandidates() {
        List<QIOCraftingJob> candidates = new ArrayList<>(waitingExecutionSlotJobs.size());
        for (UUID jobId : waitingExecutionSlotJobs) {
            QIOCraftingJob job = jobs.get(jobId);
            if (job != null && job.getState() == QIOCraftingJobState.WAITING_EXECUTION_SLOT &&
                  !job.isCancellationRequested()) {
                candidates.add(job);
            }
        }
        return Collections.unmodifiableList(candidates);
    }

    /** 返回当前活动执行槽数量。 */
    public int getActiveExecutionSlotCount() {
        return executionSlots.size();
    }

    @Nonnull
    /** 返回阻止频率删除的外部所有权说明列表。 */
    public List<String> getFrequencyDeletionBlockers() {
        List<String> blockers = new ArrayList<>();
        if (jobs.values().stream().anyMatch(job -> !job.getState().isTerminal())) {
            blockers.add("non_terminal_jobs");
        }
        if (jobBuffers.values().stream().anyMatch(buffer -> !buffer.isEmpty())) {
            blockers.add("job_buffers");
        }
        if (executionSlots.size() > 0) {
            blockers.add("active_execution_slots");
        }
        if (durableTransfers.values().stream().anyMatch(transfer ->
              transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE)) {
            blockers.add("pending_transfers");
        }
        if (configurationExchanges.values().stream().anyMatch(exchange ->
              !exchange.getPhase().isSettled())) {
            blockers.add("pending_configuration_exchanges");
        }
        if (commitments.values().stream().anyMatch(commitment ->
              commitment.getPendingMutation() != null ||
                    commitment.getState() != QIOMaterialCommitment.State.CONSUMED &&
                          commitment.getState() != QIOMaterialCommitment.State.RELEASED)) {
            blockers.add("material_claims");
        }
        if (passiveOperations.values().stream().anyMatch(operation ->
              !operation.getState().isTerminal() || !operation.getInputBuffer().isEmpty() ||
                    !operation.getOutputBuffer().isEmpty())) {
            blockers.add("passive_operations");
        }
        if (!maintenanceRules.getPendingEvaluations().isEmpty()) {
            blockers.add("maintenance_evaluations");
        }
        return Collections.unmodifiableList(blockers);
    }

    @Nonnull
    /** 将网络所有任务、传输、策略和索引写入 NBT。 */
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("networkDataSchemaVersion", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "frequencyUUID", frequencyUUID);
        data.setTag("lastKnownFrequencyIdentity", lastKnownFrequencyIdentity.write());
        data.setString("networkLifecycle", lifecycle.name());
        data.setLong("networkRevision", networkRevision);
        data.setLong("taskCommitmentRevision", taskCommitmentRevision);
        data.setLong("schedulerClock", schedulerClock);
        data.setLong("enqueueCounter", enqueueCounter);
        data.setLong("dispatchCounter", dispatchCounter);
        data.setTag("jobs", writeValues(jobs.values(), QIOCraftingJob::write));
        data.setTag("jobBuffers", writeValues(jobBuffers.values(), QIOJobBuffer::write));
        data.setTag("materialCommitments", writeValues(commitments.values(), QIOMaterialCommitment::write));
        data.setTag("waitingMaterialIndex", waitingMaterialIndex.write());
        data.setTag("activeExecutionSlotsByJobId", executionSlots.write());
        data.setTag("durableTransfers", writeValues(durableTransfers.values(), QIODurableTransferRecord::write));
        data.setTag("configurationExchanges", writeValues(configurationExchanges.values(),
              QIOConfigurationExchangeRecord::write));
        data.setTag("passiveOperations", writeValues(passiveOperations.values(),
              QIOPassiveOperation::write));
        data.setTag("policies", policies.write());
        data.setTag("automationRecipeProfiles", automationRecipeProfiles.write());
        data.setTag("workbenchConfiguration", workbenchConfiguration.write());
        data.setTag("maintenanceRules", maintenanceRules.write());
        data.setTag("automationDevices", automationDevices.write());
        data.setTag("providerCatalog", providerCatalog.write());
        return data;
    }

    @Nonnull
    /** 从 NBT 读取网络并验证所有交叉引用。 */
    public static QIOProcessingNetworkData read(@Nonnull NBTTagCompound data,
          @Nullable UUID expectedFrequencyUUID) throws QIOProcessingDataException {
        int schema = data.getInteger("networkDataSchemaVersion");
        if (schema != SCHEMA_VERSION) {
            throw new QIOProcessingDataException("Unsupported QIO Processing network schema " + schema +
                  ", expected " + SCHEMA_VERSION);
        }
        UUID frequencyUUID = QIOProcessingNbt.readUUID(data, "frequencyUUID");
        if (expectedFrequencyUUID != null && !expectedFrequencyUUID.equals(frequencyUUID)) {
            throw new QIOProcessingDataException("QIO Processing file name and frequency UUID disagree");
        }
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequencyUUID,
              QIOFrequencyIdentitySnapshot.read(data.getCompoundTag("lastKnownFrequencyIdentity")));
        network.lifecycle = QIOProcessingNbt.readEnum(data, "networkLifecycle",
              QIOProcessingNetworkLifecycle.class);
        network.networkRevision = requirePersistedCounter(data.getLong("networkRevision"), "networkRevision");
        if (!data.hasKey("taskCommitmentRevision", NBT.TAG_LONG) ||
              !data.hasKey("waitingMaterialIndex", NBT.TAG_LIST) ||
              !data.hasKey("activeExecutionSlotsByJobId", NBT.TAG_LIST) ||
              !data.hasKey("policies", NBT.TAG_COMPOUND) ||
              !data.hasKey("automationRecipeProfiles", NBT.TAG_COMPOUND) ||
              !data.hasKey("workbenchConfiguration", NBT.TAG_COMPOUND) ||
              !data.hasKey("maintenanceRules", NBT.TAG_COMPOUND) ||
              !data.hasKey("automationDevices", NBT.TAG_COMPOUND) ||
              !data.hasKey("providerCatalog", NBT.TAG_COMPOUND)) {
            throw new QIOProcessingDataException(
                  "QIO Processing network is missing required current-schema state");
        }
        network.taskCommitmentRevision = requirePersistedCounter(
              data.getLong("taskCommitmentRevision"), "taskCommitmentRevision");
        network.schedulerClock = requirePersistedCounter(data.getLong("schedulerClock"), "schedulerClock");
        network.enqueueCounter = requirePersistedCounter(data.getLong("enqueueCounter"), "enqueueCounter");
        network.dispatchCounter = requirePersistedCounter(data.getLong("dispatchCounter"), "dispatchCounter");

        NBTTagList jobs = boundedList(data, "jobs", MAX_PERSISTED_JOBS);
        for (int i = 0; i < jobs.tagCount(); i++) {
            QIOCraftingJob job = QIOCraftingJob.read(jobs.getCompoundTagAt(i));
            if (network.jobs.put(job.getJobId(), job) != null) {
                throw new QIOProcessingDataException("Duplicate QIO job " + job.getJobId());
            }
            if (job.getState() == QIOCraftingJobState.WAITING_EXECUTION_SLOT) {
                network.waitingExecutionSlotJobs.add(job.getJobId());
            }
        }
        NBTTagList buffers = boundedList(data, "jobBuffers", MAX_PERSISTED_JOBS);
        for (int i = 0; i < buffers.tagCount(); i++) {
            QIOJobBuffer buffer = QIOJobBuffer.read(buffers.getCompoundTagAt(i));
            if (network.jobBuffers.put(buffer.getJobId(), buffer) != null) {
                throw new QIOProcessingDataException("Duplicate QIO job buffer " + buffer.getJobId());
            }
        }
        NBTTagList commitments = boundedList(data, "materialCommitments", MAX_PERSISTED_JOBS);
        for (int i = 0; i < commitments.tagCount(); i++) {
            QIOMaterialCommitment commitment = QIOMaterialCommitment.read(
                  commitments.getCompoundTagAt(i));
            if (network.commitments.put(commitment.getJobId(), commitment) != null) {
                throw new QIOProcessingDataException("Duplicate QIO material commitment " +
                      commitment.getJobId());
            }
        }
        network.waitingMaterialIndex = QIOWaitingMaterialIndex.read(
              data.getTagList("waitingMaterialIndex", NBT.TAG_COMPOUND));
        network.executionSlots = QIOExecutionSlotPool.read(
              data.getTagList("activeExecutionSlotsByJobId", NBT.TAG_COMPOUND));
        network.policies = QIOPolicyCatalog.read(data.getCompoundTag("policies"));
        network.automationRecipeProfiles = QIOAutomationRecipeProfileCatalog.read(
              data.getCompoundTag("automationRecipeProfiles"));
        network.workbenchConfiguration = QIOWorkbenchConfiguration.read(
              data.getCompoundTag("workbenchConfiguration"));
        network.maintenanceRules = QIOMaintenanceRuleCatalog.read(frequencyUUID,
              data.getCompoundTag("maintenanceRules"));
        network.automationDevices = QIOAutomationDeviceCatalog.read(
              data.getCompoundTag("automationDevices"));
        network.providerCatalog = QIOProviderCatalog.read(data.getCompoundTag("providerCatalog"));
        network.providerCatalog.validateDevices(network.automationDevices);
        if (network.automationDevices.markAllOfflineAfterLoad()) {
            network.repairedOnLoad = true;
        }
        NBTTagList transfers = boundedList(data, "durableTransfers", MAX_PERSISTED_TRANSFERS);
        for (int i = 0; i < transfers.tagCount(); i++) {
            QIODurableTransferRecord transfer = QIODurableTransferRecord.read(
                  transfers.getCompoundTagAt(i));
            if (network.durableTransfers.put(transfer.getTransferId(), transfer) != null) {
                throw new QIOProcessingDataException("Duplicate durable QIO transfer " +
                      transfer.getTransferId());
            }
        }
        if (data.hasKey("configurationExchanges", NBT.TAG_LIST)) {
            NBTTagList exchanges = boundedList(data, "configurationExchanges",
                  MAX_PERSISTED_TRANSFERS);
            for (int i = 0; i < exchanges.tagCount(); i++) {
                QIOConfigurationExchangeRecord exchange =
                      QIOConfigurationExchangeRecord.read(exchanges.getCompoundTagAt(i));
                if (network.configurationExchanges.put(exchange.getExchangeId(), exchange) != null) {
                    throw new QIOProcessingDataException("Duplicate QIO configuration exchange " +
                          exchange.getExchangeId());
                }
            }
        }
        NBTTagList passive = boundedList(data, "passiveOperations",
              MAX_PERSISTED_PASSIVE_OPERATIONS);
        for (int i = 0; i < passive.tagCount(); i++) {
            QIOPassiveOperation operation = QIOPassiveOperation.read(passive.getCompoundTagAt(i));
            if (network.passiveOperations.put(operation.getOperationId(), operation) != null) {
                throw new QIOProcessingDataException("Duplicate QIO passive operation " +
                      operation.getOperationId());
            }
        }
        network.validateReferences();
        QIOWaitingMaterialIndex rebuilt = QIOWaitingMaterialIndex.rebuild(network.commitments.values());
        if (!rebuilt.equals(network.waitingMaterialIndex)) {
            network.waitingMaterialIndex = rebuilt;
            network.repairedOnLoad = true;
        }
        if (network.hasClaimWakeCandidates()) {
            network.claimWakeGeneration = 1;
        }
        network.rebuildRuntimeIndexes();
        return network;
    }

    private void validateReferences() throws QIOProcessingDataException {
        if (!jobs.keySet().equals(commitments.keySet()) || !jobs.keySet().equals(jobBuffers.keySet())) {
            throw new QIOProcessingDataException("QIO jobs, material commitments, and buffers are not one-to-one");
        }
        for (QIOCraftingJob job : jobs.values()) {
            QIOMaterialCommitment commitment = commitments.get(job.getJobId());
            if (job.getReadySinceSchedulerClock() > schedulerClock) {
                throw new QIOProcessingDataException("QIO job readySince is ahead of scheduler clock " +
                      job.getJobId());
            }
            if (job.getLastDispatchSequence() >= dispatchCounter &&
                  job.getLastDispatchSequence() >= 0) {
                throw new QIOProcessingDataException("QIO job dispatch sequence is ahead of its network " +
                      job.getJobId());
            }
            if (commitment.getPlanRevision() != job.getActivePlan().getRevision() ||
                  !commitment.getMaterialRequirements().write().equals(
                        job.getActivePlan().getMaterialRequirements().write()) ||
                  commitment.getPriority() != job.getBasePriority() ||
                  commitment.getEnqueueSequence() != job.getEnqueueSequence()) {
                throw new QIOProcessingDataException("Material commitment disagrees with job " + job.getJobId());
            }
            long activeOperations = job.getStepRuntimes().values().stream()
                  .mapToLong(QIOStepRuntime::getActiveAssignmentCount).sum();
            if (activeOperations > 0 && job.getExecutionSlotToken() == null) {
                throw new QIOProcessingDataException("QIO job has active operations without an execution slot " +
                      job.getJobId());
            }
            if (job.getState() == QIOCraftingJobState.WAITING_MATERIALS &&
                  (commitment.getMissingAmounts().isEmpty() || job.getExecutionSlotToken() != null)) {
                throw new QIOProcessingDataException("WAITING_MATERIALS job has inconsistent ownership " +
                      job.getJobId());
            }
            if (job.getState() == QIOCraftingJobState.WAITING_EXECUTION_SLOT &&
                  (!commitment.isFullyCommitted() || job.getExecutionSlotToken() != null)) {
                throw new QIOProcessingDataException("WAITING_EXECUTION_SLOT job is not ready " +
                      job.getJobId());
            }
        }
        executionSlots.validateAgainst(jobs);
        for (QIODurableTransferRecord transfer : durableTransfers.values()) {
            if (transfer.getOwnerJobId() != null && !jobs.containsKey(transfer.getOwnerJobId())) {
                throw new QIOProcessingDataException("Durable transfer references unknown job " +
                      transfer.getOwnerJobId());
            }
            if (transfer.getType() == QIODurableTransferRecord.Type.QIO_TO_JOB) {
                validateReservationTransfer(transfer);
            }
        }
        for (QIOConfigurationExchangeRecord exchange : configurationExchanges.values()) {
            if (exchange.getOwnerJobId() != null &&
                  !jobs.containsKey(exchange.getOwnerJobId())) {
                throw new QIOProcessingDataException(
                      "Configuration exchange references unknown job " +
                            exchange.getOwnerJobId());
            }
            if (exchange.getOwnerJobId() == null &&
                  !passiveOperations.containsKey(exchange.getOperationId())) {
                throw new QIOProcessingDataException(
                      "Configuration exchange references unknown passive operation " +
                            exchange.getOperationId());
            }
            QIODurableTransferRecord targetReturn = durableTransfers.get(
                  exchange.getTargetReturnTransferId());
            if (targetReturn != null) {
                BigInteger baseline = exchange.getTargetReturnQioBaseline();
                if (baseline == null && exchange.requiresTargetReturn()) {
                    BigInteger persistedBaseline = targetReturn.getQIOBaselines().get(
                          exchange.getTargetResource());
                    if (persistedBaseline != null && persistedBaseline.equals(
                          exchange.getTargetQioBaseline().subtract(BigInteger.ONE))) {
                        exchange.prepareTargetReturn(persistedBaseline);
                        baseline = persistedBaseline;
                        repairedOnLoad = true;
                    }
                }
                if (baseline == null || !isConfigurationTargetReturnTransfer(targetReturn,
                      exchange, baseline, true)) {
                    throw new QIOProcessingDataException(
                          "Configuration target return transfer disagrees with its exchange " +
                                exchange.getExchangeId());
                }
            }
            if (exchange.isTargetReturned() && targetReturn == null) {
                // A settled exchange may have had its historical return transfer compacted;
                // the explicit disposition remains the authoritative ownership marker.
                if (exchange.getPhase() != QIOConfigurationExchangeRecord.Phase.CANCELLED) {
                    throw new QIOProcessingDataException(
                          "Returned configuration target is missing its durable transfer");
                }
            }
        }
    }

    @Nonnull
    private QIOCraftingJob requireJob(UUID jobId) {
        QIOCraftingJob job = jobs.get(Objects.requireNonNull(jobId, "jobId"));
        if (job == null) {
            throw new IllegalArgumentException("Unknown QIO job " + jobId);
        }
        return job;
    }

    @Nonnull
    private QIOMaterialCommitment requireCommitment(UUID jobId) {
        QIOMaterialCommitment commitment = commitments.get(Objects.requireNonNull(jobId, "jobId"));
        if (commitment == null) {
            throw new IllegalArgumentException("Unknown QIO material commitment for job " + jobId);
        }
        return commitment;
    }

    @Nonnull
    private QIOJobBuffer requireJobBuffer(UUID jobId) {
        QIOJobBuffer buffer = jobBuffers.get(Objects.requireNonNull(jobId, "jobId"));
        if (buffer == null) {
            throw new IllegalArgumentException("Unknown QIO job buffer for job " + jobId);
        }
        return buffer;
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    @Nonnull
    private QIODurableTransferRecord requireReservationTransfer(UUID transferId) {
        QIODurableTransferRecord transfer = durableTransfers.get(
              Objects.requireNonNull(transferId, "transferId"));
        if (transfer == null || transfer.getType() != QIODurableTransferRecord.Type.QIO_TO_JOB ||
              transfer.getResolution() != QIODurableTransferRecord.Resolution.NONE) {
            throw new IllegalArgumentException("Unknown active QIO reservation transfer " + transferId);
        }
        return transfer;
    }

    @Nonnull
    private QIODurableTransferRecord requireGenericTransfer(UUID transferId) {
        QIODurableTransferRecord transfer = durableTransfers.get(
              Objects.requireNonNull(transferId, "transferId"));
        if (transfer == null || transfer.getType() == QIODurableTransferRecord.Type.QIO_TO_JOB ||
              transfer.getResolution() != QIODurableTransferRecord.Resolution.NONE) {
            throw new IllegalArgumentException("Unknown active generic QIO transfer " + transferId);
        }
        return transfer;
    }

    private void validateReservationTransfer(QIODurableTransferRecord transfer)
          throws QIOProcessingDataException {
        QIOCraftingJob job = jobs.get(transfer.getOwnerJobId());
        QIOMaterialCommitment commitment = commitments.get(transfer.getOwnerJobId());
        QIOJobBuffer buffer = jobBuffers.get(transfer.getOwnerJobId());
        if (job == null || commitment == null || buffer == null ||
              transfer.getPlanRevision() != job.getActivePlan().getRevision() ||
              job.getExecutionSlotToken() == null) {
            throw new QIOProcessingDataException("Reservation transfer has invalid job ownership " +
                  transfer.getTransferId());
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED ||
              transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            if (job.getState() != QIOCraftingJobState.RESERVING || !commitment.isFullyCommitted() ||
                  !buffer.get(QIOJobBuffer.Compartment.RESERVED).isEmpty()) {
                throw new QIOProcessingDataException("Pending reservation state is inconsistent for job " +
                      job.getJobId());
            }
        } else if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            if (job.getState() != QIOCraftingJobState.RESERVING || !commitment.isFullyCommitted()) {
                throw new QIOProcessingDataException("Credited reservation state is inconsistent for job " +
                      job.getJobId());
            }
            for (Map.Entry<PortableResourceDescriptor, Long> entry : transfer.getResources().entrySet()) {
                if (buffer.get(QIOJobBuffer.Compartment.RESERVED, entry.getKey()) < entry.getValue()) {
                    throw new QIOProcessingDataException("Credited reservation is missing from job buffer");
                }
            }
        } else if (transfer.getPhase() == QIODurableTransferRecord.Phase.COMMITTED &&
              transfer.getResolution() == QIODurableTransferRecord.Resolution.FORWARD_COMMITTED) {
            if (job.getState() != QIOCraftingJobState.READY ||
                  commitment.getState() != QIOMaterialCommitment.State.CONSUMED) {
                throw new QIOProcessingDataException("Committed reservation did not activate its job");
            }
        }
    }

    private void markDirty() {
        if (networkRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO network revision exhausted");
        }
        networkRevision++;
        if (dirtyListener != null) {
            dirtyListener.run();
        }
    }

    private void transitionJob(QIOCraftingJob job, QIOCraftingJobState nextState) {
        if (job.getRevisionTransition() != null &&
              job.getState() == QIOCraftingJobState.REPLAN_DRAINING &&
              nextState != QIOCraftingJobState.REPLAN_DRAINING &&
              nextState != QIOCraftingJobState.REPLAN_REQUIRED) {
            nextState = QIOCraftingJobState.REPLAN_DRAINING;
        }
        if (job.getRevisionTransition() == null &&
              job.getState() == QIOCraftingJobState.PLANNING &&
              nextState != QIOCraftingJobState.PLANNING) {
            nextState = QIOCraftingJobState.PLANNING;
        }
        if (job.getState() == QIOCraftingJobState.OPERATION_CONTAMINATED &&
              job.getRecoveryDiagnostic() != null &&
              nextState != QIOCraftingJobState.OPERATION_CONTAMINATED &&
              nextState != QIOCraftingJobState.CANCEL_REQUESTED &&
              nextState != QIOCraftingJobState.RETURNING && !nextState.isTerminal()) {
            nextState = QIOCraftingJobState.OPERATION_CONTAMINATED;
        }
        if (nextState == QIOCraftingJobState.WAITING_EXECUTION_SLOT ||
              nextState == QIOCraftingJobState.READY) {
            job.transitionToRunnable(nextState, schedulerClock);
        } else {
            job.transitionTo(nextState);
        }
        if (nextState == QIOCraftingJobState.WAITING_EXECUTION_SLOT) {
            waitingExecutionSlotJobs.add(job.getJobId());
        } else {
            waitingExecutionSlotJobs.remove(job.getJobId());
        }
        if ((nextState.isTerminal() || nextState == QIOCraftingJobState.RETURNING ||
              nextState == QIOCraftingJobState.CANCEL_REQUESTED) &&
              job.getSource() == QIOCraftingJobSource.MAINTENANCE) {
            maintenanceRules.clearOutstandingForJob(job.getJobId());
        }
        refreshJobIndexes(job);
    }

    private void rebuildRuntimeIndexes() {
        dispatchCandidateJobs.clear();
        waitingProviderJobs.clear();
        reservingJobs.clear();
        replanJobs.clear();
        cancellationJobs.clear();
        activePassiveOperations.clear();
        activePassiveDeviceCounts.clear();
        terminalJobHistory.clear();
        terminalPassiveHistory.clear();
        for (QIOCraftingJob job : jobs.values()) {
            refreshJobIndexes(job);
        }
        List<UUID> orderedDispatch = new ArrayList<>(dispatchCandidateJobs);
        orderedDispatch.sort((left, right) -> {
            QIOCraftingJob leftJob = jobs.get(left);
            QIOCraftingJob rightJob = jobs.get(right);
            int dispatch = Long.compare(leftJob.getLastDispatchSequence(),
                  rightJob.getLastDispatchSequence());
            if (dispatch != 0) {
                return dispatch;
            }
            int enqueue = Long.compare(leftJob.getEnqueueSequence(),
                  rightJob.getEnqueueSequence());
            return enqueue != 0 ? enqueue : left.toString().compareTo(right.toString());
        });
        dispatchCandidateJobs.clear();
        dispatchCandidateJobs.addAll(orderedDispatch);
        for (QIOPassiveOperation operation : passiveOperations.values()) {
            refreshPassiveIndexes(operation);
        }
    }

    private void refreshJobIndexes(QIOCraftingJob job) {
        UUID jobId = job.getJobId();
        boolean dispatchable = job.getExecutionSlotToken() != null &&
              (isDispatchState(job.getState()) ||
                    job.getState() == QIOCraftingJobState.OPERATION_CONTAMINATED &&
                          job.hasActiveOperations());
        if (!dispatchable) {
            dispatchCandidateJobs.remove(jobId);
            waitingProviderJobs.remove(jobId);
        } else if (job.getState() == QIOCraftingJobState.WAITING_PROVIDER &&
              !job.hasActiveOperations()) {
            dispatchCandidateJobs.remove(jobId);
            waitingProviderJobs.add(jobId);
        } else {
            waitingProviderJobs.remove(jobId);
            dispatchCandidateJobs.add(jobId);
        }
        updateIndex(reservingJobs, jobId, job.getState() == QIOCraftingJobState.RESERVING);
        updateIndex(replanJobs, jobId, job.getRevisionTransition() != null &&
              (job.getState() == QIOCraftingJobState.REPLAN_DRAINING ||
                    job.getState() == QIOCraftingJobState.REPLAN_REQUIRED));
        updateIndex(cancellationJobs, jobId, job.isCancellationRequested() &&
              !job.getState().isTerminal());
        if (job.getState().isTerminal()) {
            terminalJobHistory.add(jobId);
        }
    }

    private void refreshPassiveIndexes(QIOPassiveOperation operation) {
        UUID operationId = operation.getOperationId();
        boolean wasActive = activePassiveOperations.contains(operationId);
        boolean active = !operation.getState().isTerminal();
        updateIndex(activePassiveOperations, operationId, active);
        if (wasActive != active) {
            UUID deviceUUID = operation.getDeviceUUID();
            if (active) {
                activePassiveDeviceCounts.merge(deviceUUID, 1, Math::addExact);
            } else {
                int remaining = activePassiveDeviceCounts.getOrDefault(deviceUUID, 0) - 1;
                if (remaining <= 0) {
                    activePassiveDeviceCounts.remove(deviceUUID);
                } else {
                    activePassiveDeviceCounts.put(deviceUUID, remaining);
                }
            }
        }
        if (operation.getState().isTerminal()) {
            terminalPassiveHistory.add(operationId);
        }
    }

    private static boolean isDispatchState(QIOCraftingJobState state) {
        return state == QIOCraftingJobState.READY ||
              state == QIOCraftingJobState.DISPATCHING ||
              state == QIOCraftingJobState.PROCESSING ||
              state == QIOCraftingJobState.COLLECTING ||
              state == QIOCraftingJobState.WAITING_PROVIDER ||
              state == QIOCraftingJobState.DELIVERING ||
              state == QIOCraftingJobState.PLANNING ||
              state == QIOCraftingJobState.REPLAN_DRAINING ||
              state == QIOCraftingJobState.RETURN_BLOCKED ||
              state == QIOCraftingJobState.RETURNING;
    }

    private static void updateIndex(Set<UUID> index, UUID id, boolean present) {
        if (present) {
            index.add(id);
        } else {
            index.remove(id);
        }
    }

    @Nonnull
    private static <T> List<T> peekIndexed(Set<UUID> index, Map<UUID, T> values,
          int maximum) {
        if (maximum <= 0) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>(Math.min(index.size(), maximum));
        for (UUID id : index) {
            T value = values.get(id);
            if (value != null) {
                result.add(value);
                if (result.size() >= maximum) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Nonnull
    private static <T> List<T> pollIndexed(Set<UUID> index, Map<UUID, T> values,
          int maximum) {
        if (maximum <= 0) {
            return Collections.emptyList();
        }
        List<UUID> rotated = new ArrayList<>(Math.min(index.size(), maximum));
        List<T> result = new ArrayList<>(Math.min(index.size(), maximum));
        for (UUID id : index) {
            T value = values.get(id);
            if (value != null) {
                rotated.add(id);
                result.add(value);
                if (result.size() >= maximum) {
                    break;
                }
            }
        }
        index.removeAll(rotated);
        index.addAll(rotated);
        return Collections.unmodifiableList(result);
    }

    private void advanceTaskCommitmentRevision() {
        if (taskCommitmentRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO task commitment revision exhausted");
        }
        taskCommitmentRevision++;
    }

    private void updateClaimWakeGeneration(UUID jobId, boolean wasWakeCandidate) {
        if (wasWakeCandidate != isClaimWakeCandidate(jobId)) {
            advanceClaimWakeGeneration();
        }
    }

    private void advanceClaimWakeGeneration() {
        claimWakeGeneration = claimWakeGeneration == Long.MAX_VALUE ? 0 : claimWakeGeneration + 1;
    }

    private static boolean sameIdentity(QIOFrequencyIdentitySnapshot left,
          QIOFrequencyIdentitySnapshot right) {
        return left.getName().equals(right.getName()) &&
              Objects.equals(left.getOwnerUUID(), right.getOwnerUUID()) &&
              left.getSecurityMode() == right.getSecurityMode();
    }

    @Nonnull
    private static <T> NBTTagList writeValues(Collection<T> values,
          java.util.function.Function<T, NBTTagCompound> writer) {
        List<NBTTagCompound> serialized = new ArrayList<>(values.size());
        for (T value : values) {
            serialized.add(writer.apply(value));
        }
        serialized.sort(Comparator.comparing(QIOProcessingNetworkData::stableId));
        NBTTagList list = new NBTTagList();
        serialized.forEach(list::appendTag);
        return list;
    }

    private static String stableId(NBTTagCompound data) {
        for (String key : new String[]{"jobId", "transferId"}) {
            if (data.hasKey(key, NBT.TAG_STRING)) {
                return data.getString(key);
            }
        }
        return data.toString();
    }

    @Nonnull
    private static NBTTagList boundedList(NBTTagCompound data, String key, int maximum)
          throws QIOProcessingDataException {
        if (!data.hasKey(key, NBT.TAG_LIST)) {
            throw new QIOProcessingDataException(key + " is missing from current-schema data");
        }
        NBTTagList list = data.getTagList(key, NBT.TAG_COMPOUND);
        if (list.tagCount() > maximum) {
            throw new QIOProcessingDataException(key + " exceeds its persisted entry safety limit");
        }
        return list;
    }

    private static long requirePersistedCounter(long value, String name)
          throws QIOProcessingDataException {
        if (value < 0) {
            throw new QIOProcessingDataException(name + " cannot be negative");
        }
        return value;
    }
}
