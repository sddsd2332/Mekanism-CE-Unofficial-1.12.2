package mekanism.qioprocessing.common.content.job;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOCyclePlanNode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Persistent runtime envelope for one frequency-level order. */
public final class QIOCraftingJob {

    private final UUID jobId;
    private final QIOCraftingJobSource source;
    @Nullable
    private final UUID requester;
    private long basePriority;
    private final long enqueueSequence;
    private final long createdAtTick;
    private final long requestedRootAmount;
    private QIOCraftPlan activePlan;
    @Nullable
    private QIOPlanRevisionTransition revisionTransition;
    private final Map<Long, QIOStepRuntime> stepRuntimes = new LinkedHashMap<>();
    private final Map<Long, QIOCycleRuntime> cycleRuntimes = new LinkedHashMap<>();
    private QIOCraftingJobState state;
    private long runtimeRevision;
    private long readySinceSchedulerClock;
    private long lastDispatchSequence;
    @Nullable
    private UUID executionSlotToken;
    private long slotAcquiredAtSchedulerClock;
    private boolean cancellationRequested;
    private long deliveredRootAmount;

    public QIOCraftingJob(@Nonnull UUID jobId, @Nonnull QIOCraftingJobSource source,
          @Nullable UUID requester, long basePriority, long enqueueSequence, long createdAtTick,
          @Nonnull QIOCraftPlan activePlan) {
        this(jobId, source, requester, basePriority, enqueueSequence, createdAtTick, activePlan,
              QIOCraftingJobState.QUEUED, 0, -1, -1, null, -1, false, 0,
              activePlan.getRootAmount(), null, null, null);
    }

    private QIOCraftingJob(UUID jobId, QIOCraftingJobSource source, @Nullable UUID requester,
          long basePriority, long enqueueSequence, long createdAtTick, QIOCraftPlan activePlan,
          QIOCraftingJobState state, long runtimeRevision, long readySinceSchedulerClock,
          long lastDispatchSequence, @Nullable UUID executionSlotToken,
          long slotAcquiredAtSchedulerClock, boolean cancellationRequested,
          long deliveredRootAmount, long requestedRootAmount,
          @Nullable Map<Long, QIOStepRuntime> restoredRuntimes,
          @Nullable QIOPlanRevisionTransition revisionTransition,
          @Nullable Map<Long, QIOCycleRuntime> restoredCycleRuntimes) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.source = Objects.requireNonNull(source, "source");
        this.requester = requester;
        this.basePriority = basePriority;
        this.enqueueSequence = QIOProcessingNbt.requireNonNegative(enqueueSequence, "enqueueSequence");
        this.createdAtTick = QIOProcessingNbt.requireNonNegative(createdAtTick, "createdAtTick");
        if (requestedRootAmount <= 0) {
            throw new IllegalArgumentException("requestedRootAmount must be positive");
        }
        this.requestedRootAmount = requestedRootAmount;
        this.activePlan = Objects.requireNonNull(activePlan, "activePlan");
        this.revisionTransition = revisionTransition;
        initializeStepRuntimes(restoredRuntimes);
        initializeCycleRuntimes(restoredCycleRuntimes);
        this.state = Objects.requireNonNull(state, "state");
        this.runtimeRevision = QIOProcessingNbt.requireNonNegative(runtimeRevision, "runtimeRevision");
        this.readySinceSchedulerClock = readySinceSchedulerClock;
        this.lastDispatchSequence = lastDispatchSequence;
        this.executionSlotToken = executionSlotToken;
        this.slotAcquiredAtSchedulerClock = slotAcquiredAtSchedulerClock;
        this.cancellationRequested = cancellationRequested;
        this.deliveredRootAmount = QIOProcessingNbt.requireNonNegative(deliveredRootAmount,
              "deliveredRootAmount");
        if (this.deliveredRootAmount > requestedRootAmount) {
            throw new IllegalArgumentException("Delivered QIO root amount exceeds the request");
        }
        validateSchedulingFields();
        validateSlotFields();
        validateRevisionTransition();
    }

    @Nonnull
    public UUID getJobId() {
        return jobId;
    }

    @Nonnull
    public QIOCraftingJobSource getSource() {
        return source;
    }

    @Nullable
    public UUID getRequester() {
        return requester;
    }

    public long getBasePriority() {
        return basePriority;
    }

    public void updateBasePriority(long priority) {
        if (state.isTerminal()) {
            throw new IllegalStateException("A terminal QIO job cannot change priority");
        }
        if (basePriority != priority) {
            basePriority = priority;
            incrementRuntimeRevision();
        }
    }

    public long getEnqueueSequence() {
        return enqueueSequence;
    }

    public long getCreatedAtTick() {
        return createdAtTick;
    }

    public long getRequestedRootAmount() {
        return requestedRootAmount;
    }

    @Nonnull
    public QIOCraftPlan getActivePlan() {
        return activePlan;
    }

    @Nullable
    public QIOPlanRevisionTransition getRevisionTransition() {
        return revisionTransition;
    }

    public boolean hasActiveOperations() {
        return stepRuntimes.values().stream().anyMatch(runtime ->
              runtime.hasActiveAssignments());
    }

    public void requestReplan(@Nonnull QIOCraftPlan nextPlan) {
        QIOCraftPlan checked = Objects.requireNonNull(nextPlan, "nextPlan");
        if (state.isTerminal() || cancellationRequested || revisionTransition != null ||
              checked.getRevision() <= activePlan.getRevision() ||
              !checked.getRootResource().equals(activePlan.getRootResource()) ||
              checked.getRootAmount() != getRemainingGuaranteedRootAmount()) {
            throw new IllegalStateException("QIO job cannot begin the requested plan revision");
        }
        revisionTransition = new QIOPlanRevisionTransition(checked);
        incrementRuntimeRevision();
    }

    public void activatePendingPlan() {
        if (revisionTransition == null || hasActiveOperations()) {
            throw new IllegalStateException("QIO plan revision is not ready to activate");
        }
        activePlan = revisionTransition.getPendingPlan();
        revisionTransition = null;
        stepRuntimes.clear();
        initializeStepRuntimes(null);
        cycleRuntimes.clear();
        initializeCycleRuntimes(null);
        incrementRuntimeRevision();
    }

    public void cancelPendingReplan() {
        if (revisionTransition != null) {
            revisionTransition = null;
            incrementRuntimeRevision();
        }
    }

    @Nonnull
    public Map<Long, QIOStepRuntime> getStepRuntimes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(stepRuntimes));
    }

    @Nullable
    public QIOStepRuntime getStepRuntime(long nodeId) {
        return stepRuntimes.get(nodeId);
    }

    public boolean areAllStepsComplete() {
        return stepRuntimes.values().stream().allMatch(QIOStepRuntime::isComplete) &&
              cycleRuntimes.values().stream().allMatch(QIOCycleRuntime::isComplete);
    }

    @Nonnull
    public Map<Long, QIOCycleRuntime> getCycleRuntimes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(cycleRuntimes));
    }

    public long getCycleDispatchAllowance(long memberNodeId) {
        QIOCyclePlanNode cycle = activePlan.getCycleForMember(memberNodeId);
        if (cycle == null) return Long.MAX_VALUE;
        QIOCycleRuntime runtime = cycleRuntimes.get(cycle.getNodeId());
        QIOStepRuntime member = stepRuntimes.get(memberNodeId);
        return runtime == null || member == null ? 0 :
              runtime.allowance(cycle, memberNodeId, member);
    }

    public boolean areDependenciesComplete(long nodeId) {
        mekanism.qioprocessing.common.content.plan.QIOPlanStep step = activePlan.getSteps().stream()
              .filter(candidate -> candidate.getNodeId() == nodeId).findFirst().orElse(null);
        if (step == null) {
            return false;
        }
        for (long dependency : step.getDependencies()) {
            QIOStepRuntime runtime = stepRuntimes.get(dependency);
            if (runtime == null || !runtime.isComplete()) {
                return false;
            }
        }
        return true;
    }

    public void startStepOperation(long nodeId, @Nonnull QIOOperationAssignment assignment) {
        requireStepRuntime(nodeId).start(assignment);
        incrementRuntimeRevision();
    }

    public boolean updateStepOperation(long nodeId, @Nonnull UUID operationId,
          @Nonnull QIOOperationAssignment.State state, long currentTick, long totalTicks,
          @Nullable String diagnostic) {
        if (!requireStepRuntime(nodeId).update(operationId, state, currentTick, totalTicks,
              diagnostic)) {
            return false;
        }
        incrementRuntimeRevision();
        return true;
    }

    public void completeStepOperation(long nodeId, @Nonnull UUID operationId) {
        QIOStepRuntime runtime = requireStepRuntime(nodeId);
        QIOOperationAssignment assignment = runtime.getOperation(operationId);
        long operationCount = assignment == null ? 0 : assignment.getOperationCount();
        runtime.complete(operationId);
        QIOCyclePlanNode cycle = activePlan.getCycleForMember(nodeId);
        if (cycle != null) {
            cycleRuntimes.get(cycle.getNodeId()).complete(cycle, nodeId, operationCount);
        }
        incrementRuntimeRevision();
    }

    public void failStepOperation(long nodeId, @Nonnull UUID operationId,
          @Nonnull String diagnostic) {
        requireStepRuntime(nodeId).fail(operationId, diagnostic);
        incrementRuntimeRevision();
    }

    @Nonnull
    public QIOCraftingJobState getState() {
        return state;
    }

    public long getRuntimeRevision() {
        return runtimeRevision;
    }

    public long getReadySinceSchedulerClock() {
        return readySinceSchedulerClock;
    }

    public long getLastDispatchSequence() {
        return lastDispatchSequence;
    }

    @Nullable
    public UUID getExecutionSlotToken() {
        return executionSlotToken;
    }

    public long getSlotAcquiredAtSchedulerClock() {
        return slotAcquiredAtSchedulerClock;
    }

    public boolean isCancellationRequested() {
        return cancellationRequested;
    }

    public long getDeliveredRootAmount() {
        return deliveredRootAmount;
    }

    public long getRemainingGuaranteedRootAmount() {
        return requestedRootAmount - deliveredRootAmount;
    }

    public void recordRootDelivery(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Delivered QIO root amount must be positive");
        }
        long remaining = getRemainingGuaranteedRootAmount();
        if (remaining <= 0) {
            return;
        }
        deliveredRootAmount = Math.addExact(deliveredRootAmount, Math.min(remaining, amount));
        incrementRuntimeRevision();
    }

    public boolean requestCancellation() {
        if (state.isTerminal() || cancellationRequested) {
            return false;
        }
        cancellationRequested = true;
        incrementRuntimeRevision();
        return true;
    }

    @Nonnull
    public String getClaimOwnerId() {
        return jobId + "/plan/" + activePlan.getRevision();
    }

    @Nonnull
    public String getPendingClaimOwnerId() {
        if (revisionTransition == null) throw new IllegalStateException("QIO job has no pending plan");
        return jobId + "/plan/" + revisionTransition.getPendingPlan().getRevision();
    }

    public void transitionTo(@Nonnull QIOCraftingJobState nextState) {
        Objects.requireNonNull(nextState, "nextState");
        if (isRunnableState(nextState) && state != nextState) {
            throw new IllegalArgumentException("Runnable QIO states require a scheduler clock");
        }
        transition(nextState);
        if (!isRunnableState(nextState)) {
            readySinceSchedulerClock = -1;
        }
    }

    public void transitionToRunnable(@Nonnull QIOCraftingJobState nextState,
          long schedulerClock) {
        Objects.requireNonNull(nextState, "nextState");
        if (!isRunnableState(nextState)) {
            throw new IllegalArgumentException("QIO state is not runnable: " + nextState);
        }
        long checkedClock = QIOProcessingNbt.requireNonNegative(schedulerClock, "schedulerClock");
        boolean wasRunnable = isRunnableState(state) && readySinceSchedulerClock >= 0;
        transition(nextState);
        if (!wasRunnable) {
            readySinceSchedulerClock = checkedClock;
            incrementRuntimeRevision();
        }
    }

    public void recordDispatch(long dispatchSequence) {
        long checkedSequence = QIOProcessingNbt.requireNonNegative(dispatchSequence,
              "dispatchSequence");
        if (checkedSequence <= lastDispatchSequence) {
            throw new IllegalArgumentException("QIO dispatch sequence must increase");
        }
        lastDispatchSequence = checkedSequence;
        incrementRuntimeRevision();
    }

    private void transition(QIOCraftingJobState nextState) {
        if (state.isTerminal() && state != nextState) {
            throw new IllegalStateException("A terminal QIO job cannot leave " + state);
        }
        if (state != nextState) {
            state = nextState;
            incrementRuntimeRevision();
        }
    }

    public void assignExecutionSlot(@Nonnull UUID slotToken, long schedulerClock) {
        if (executionSlotToken != null) {
            throw new IllegalStateException("QIO job already owns an execution slot");
        }
        if (state.isTerminal()) {
            throw new IllegalStateException("A terminal QIO job cannot acquire an execution slot");
        }
        executionSlotToken = Objects.requireNonNull(slotToken, "slotToken");
        slotAcquiredAtSchedulerClock = QIOProcessingNbt.requireNonNegative(schedulerClock,
              "schedulerClock");
        incrementRuntimeRevision();
    }

    public void clearExecutionSlot(@Nonnull UUID expectedToken) {
        if (!Objects.equals(executionSlotToken, Objects.requireNonNull(expectedToken, "expectedToken"))) {
            throw new IllegalStateException("QIO execution slot token does not match its job");
        }
        executionSlotToken = null;
        slotAcquiredAtSchedulerClock = -1;
        incrementRuntimeRevision();
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        QIOProcessingNbt.writeUUID(data, "jobId", jobId);
        data.setString("source", source.name());
        if (requester != null) {
            QIOProcessingNbt.writeUUID(data, "requester", requester);
        }
        data.setLong("basePriority", basePriority);
        data.setLong("enqueueSequence", enqueueSequence);
        data.setLong("createdAtTick", createdAtTick);
        data.setString("state", state.name());
        data.setLong("runtimeRevision", runtimeRevision);
        data.setLong("readySinceSchedulerClock", readySinceSchedulerClock);
        data.setLong("lastDispatchSequence", lastDispatchSequence);
        data.setBoolean("cancellationRequested", cancellationRequested);
        data.setLong("deliveredRootAmount", deliveredRootAmount);
        data.setLong("requestedRootAmount", requestedRootAmount);
        data.setTag("activePlan", activePlan.write());
        if (revisionTransition != null) {
            data.setTag("revisionTransition", revisionTransition.write());
        }
        NBTTagList runtimes = new NBTTagList();
        stepRuntimes.values().stream().sorted((left, right) ->
              Long.compare(left.getNodeId(), right.getNodeId())).forEach(runtime ->
              runtimes.appendTag(runtime.write()));
        data.setTag("stepRuntimes", runtimes);
        NBTTagList cycleList = new NBTTagList();
        cycleRuntimes.values().stream().sorted((left, right) ->
              Long.compare(left.getCycleNodeId(), right.getCycleNodeId())).forEach(runtime ->
              cycleList.appendTag(runtime.write()));
        data.setTag("cycleRuntimes", cycleList);
        if (executionSlotToken != null) {
            QIOProcessingNbt.writeUUID(data, "executionSlotToken", executionSlotToken);
            data.setLong("slotAcquiredAtSchedulerClock", slotAcquiredAtSchedulerClock);
        }
        return data;
    }

    @Nonnull
    public static QIOCraftingJob read(@Nonnull NBTTagCompound data) throws QIOProcessingDataException {
        try {
            if (!data.hasKey("readySinceSchedulerClock", NBT.TAG_LONG) ||
                  !data.hasKey("lastDispatchSequence", NBT.TAG_LONG)) {
                throw new QIOProcessingDataException("QIO crafting job is missing scheduler state");
            }
            QIOCraftPlan plan = QIOCraftPlan.read(data.getCompoundTag("activePlan"));
            if (!data.hasKey("stepRuntimes", NBT.TAG_LIST) ||
                  !data.hasKey("cycleRuntimes", NBT.TAG_LIST) ||
                  !data.hasKey("requestedRootAmount", NBT.TAG_LONG)) {
                throw new QIOProcessingDataException(
                      "QIO crafting job is missing current-schema runtime state");
            }
            Map<Long, QIOStepRuntime> runtimes = new LinkedHashMap<>();
            NBTTagList storedRuntimes = data.getTagList("stepRuntimes", NBT.TAG_COMPOUND);
            if (storedRuntimes.tagCount() > 65_536) {
                throw new QIOProcessingDataException("QIO job contains too many step runtimes");
            }
            for (int index = 0; index < storedRuntimes.tagCount(); index++) {
                QIOStepRuntime runtime = QIOStepRuntime.read(storedRuntimes.getCompoundTagAt(index));
                if (runtimes.put(runtime.getNodeId(), runtime) != null) {
                    throw new QIOProcessingDataException("Duplicate QIO job step runtime");
                }
            }
            Map<Long, QIOCycleRuntime> cycleRuntimes = new LinkedHashMap<>();
            NBTTagList storedCycles = data.getTagList("cycleRuntimes", NBT.TAG_COMPOUND);
            if (storedCycles.tagCount() > 65_536) {
                throw new QIOProcessingDataException("QIO job contains too many cycle runtimes");
            }
            for (int index = 0; index < storedCycles.tagCount(); index++) {
                QIOCycleRuntime runtime = QIOCycleRuntime.read(storedCycles.getCompoundTagAt(index));
                if (cycleRuntimes.put(runtime.getCycleNodeId(), runtime) != null) {
                    throw new QIOProcessingDataException("Duplicate QIO cycle runtime");
                }
            }
            return new QIOCraftingJob(QIOProcessingNbt.readUUID(data, "jobId"),
                  QIOProcessingNbt.readEnum(data, "source", QIOCraftingJobSource.class),
                  QIOProcessingNbt.readOptionalUUID(data, "requester"), data.getLong("basePriority"),
                  data.getLong("enqueueSequence"), data.getLong("createdAtTick"),
                  plan,
                  QIOProcessingNbt.readEnum(data, "state", QIOCraftingJobState.class),
                  data.getLong("runtimeRevision"), data.getLong("readySinceSchedulerClock"),
                  data.getLong("lastDispatchSequence"),
                  data.hasKey("executionSlotToken", NBT.TAG_STRING) ?
                        QIOProcessingNbt.readUUID(data, "executionSlotToken") : null,
                  data.hasKey("executionSlotToken", NBT.TAG_STRING) ?
                        data.getLong("slotAcquiredAtSchedulerClock") : -1,
                  data.getBoolean("cancellationRequested"),
                  data.getLong("deliveredRootAmount"),
                  data.getLong("requestedRootAmount"), runtimes,
                  data.hasKey("revisionTransition", NBT.TAG_COMPOUND) ?
                        QIOPlanRevisionTransition.read(data.getCompoundTag("revisionTransition")) : null,
                  cycleRuntimes);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO crafting job", e);
        }
    }

    private void validateSchedulingFields() {
        if (readySinceSchedulerClock < -1 || lastDispatchSequence < -1) {
            throw new IllegalArgumentException("QIO job has invalid scheduler sequence fields");
        }
        if (isRunnableState(state) != (readySinceSchedulerClock >= 0)) {
            throw new IllegalArgumentException("QIO job runnable state disagrees with readySince");
        }
    }

    private void validateSlotFields() {
        if (executionSlotToken == null && slotAcquiredAtSchedulerClock != -1 ||
              executionSlotToken != null && slotAcquiredAtSchedulerClock < 0) {
            throw new IllegalArgumentException("QIO job has inconsistent execution slot fields");
        }
        if (executionSlotToken != null && state.isTerminal()) {
            throw new IllegalArgumentException("Terminal QIO job cannot retain an execution slot");
        }
    }

    private void validateRevisionTransition() {
        boolean transitionState = state == QIOCraftingJobState.REPLAN_REQUIRED ||
              state == QIOCraftingJobState.REPLAN_DRAINING;
        if (transitionState != (revisionTransition != null)) {
            throw new IllegalArgumentException("QIO job replan state and transition disagree");
        }
        if (revisionTransition != null) {
            QIOCraftPlan pending = revisionTransition.getPendingPlan();
            if (pending.getRevision() <= activePlan.getRevision() ||
                  !pending.getRootResource().equals(activePlan.getRootResource()) ||
                  pending.getRootAmount() != getRemainingGuaranteedRootAmount()) {
                throw new IllegalArgumentException("QIO pending plan is incompatible with its active plan");
            }
        }
    }

    private void initializeStepRuntimes(@Nullable Map<Long, QIOStepRuntime> restoredRuntimes) {
        Map<Long, mekanism.qioprocessing.common.content.plan.QIOPlanStep> steps = new LinkedHashMap<>();
        for (mekanism.qioprocessing.common.content.plan.QIOPlanStep step : activePlan.getSteps()) {
            steps.put(step.getNodeId(), step);
        }
        if (restoredRuntimes == null) {
            for (mekanism.qioprocessing.common.content.plan.QIOPlanStep step : steps.values()) {
                stepRuntimes.put(step.getNodeId(), QIOStepRuntime.create(step));
            }
            return;
        }
        if (!steps.keySet().equals(restoredRuntimes.keySet())) {
            throw new IllegalArgumentException("QIO job step runtimes do not match its active plan");
        }
        for (Map.Entry<Long, QIOStepRuntime> entry : restoredRuntimes.entrySet()) {
            QIOStepRuntime runtime = Objects.requireNonNull(entry.getValue(), "step runtime");
            if (runtime.getRequiredOperations() != steps.get(entry.getKey()).getOperations()) {
                throw new IllegalArgumentException("QIO step runtime operation count changed");
            }
            stepRuntimes.put(entry.getKey(), runtime);
        }
    }

    private void initializeCycleRuntimes(@Nullable Map<Long, QIOCycleRuntime> restored) {
        if (restored == null || restored.isEmpty() && activePlan.getCycleNodes().isEmpty()) {
            for (QIOCyclePlanNode node : activePlan.getCycleNodes()) {
                cycleRuntimes.put(node.getNodeId(), new QIOCycleRuntime(node));
            }
            return;
        }
        if (restored.size() != activePlan.getCycleNodes().size()) {
            throw new IllegalArgumentException("QIO cycle runtimes do not match its active plan");
        }
        for (QIOCyclePlanNode node : activePlan.getCycleNodes()) {
            QIOCycleRuntime runtime = restored.get(node.getNodeId());
            if (runtime == null) {
                throw new IllegalArgumentException("Missing QIO cycle runtime " + node.getNodeId());
            }
            runtime.validate(node, stepRuntimes);
            cycleRuntimes.put(node.getNodeId(), runtime);
        }
    }

    private QIOStepRuntime requireStepRuntime(long nodeId) {
        QIOStepRuntime runtime = stepRuntimes.get(nodeId);
        if (runtime == null) {
            throw new IllegalArgumentException("Unknown QIO plan step " + nodeId);
        }
        return runtime;
    }

    private void incrementRuntimeRevision() {
        if (runtimeRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO job runtime revision exhausted");
        }
        runtimeRevision++;
    }

    private static boolean isRunnableState(QIOCraftingJobState state) {
        return state == QIOCraftingJobState.WAITING_EXECUTION_SLOT ||
              state == QIOCraftingJobState.READY;
    }
}
