package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.job.QIOCycleRuntime;
import mekanism.qioprocessing.common.content.job.QIOOperationAssignment;
import mekanism.qioprocessing.common.content.job.QIOStepRuntime;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable wire projection of one step or cycle runtime. */
/**
 * QIO 处理模块中的 QIOCraftingMonitorRuntimeNode 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOCraftingMonitorRuntimeNode {

    private static final int MAX_ACTIVITIES = 65_536;
    private static final int MAX_MEMBERS = 65_536;

    public enum Kind {
        STEP,
        CYCLE
    }

    private final Kind kind;
    private final long nodeId;
    private final long nodeRevision;
    private final long totalOperations;
    private final long completedOperations;
    private final long failedAttempts;
    private final long currentRound;
    private final long totalRounds;
    private final Map<Long, Long> completedThisRound;
    private final List<Activity> activities;

    private QIOCraftingMonitorRuntimeNode(Kind kind, long nodeId, long nodeRevision,
          long totalOperations, long completedOperations, long failedAttempts,
          long currentRound, long totalRounds, Map<Long, Long> completedThisRound,
          List<Activity> activities) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.nodeId = QIOProcessingNbt.requireNonNegative(nodeId, "nodeId");
        this.nodeRevision = QIOProcessingNbt.requireNonNegative(nodeRevision, "nodeRevision");
        this.totalOperations = QIOProcessingNbt.requireNonNegative(totalOperations,
              "totalOperations");
        this.completedOperations = QIOProcessingNbt.requireNonNegative(completedOperations,
              "completedOperations");
        this.failedAttempts = QIOProcessingNbt.requireNonNegative(failedAttempts,
              "failedAttempts");
        this.currentRound = QIOProcessingNbt.requireNonNegative(currentRound, "currentRound");
        this.totalRounds = QIOProcessingNbt.requireNonNegative(totalRounds, "totalRounds");
        if (completedOperations > totalOperations || currentRound > totalRounds ||
            completedThisRound.size() > MAX_MEMBERS || activities.size() > MAX_ACTIVITIES) {
            throw new IllegalArgumentException("Invalid QIO monitor runtime progress");
        }
        this.completedThisRound = Collections.unmodifiableMap(
              new LinkedHashMap<>(completedThisRound));
        this.activities = Collections.unmodifiableList(new ArrayList<>(activities));
        if (kind == Kind.STEP && (totalRounds != 0 || !completedThisRound.isEmpty()) ||
            kind == Kind.CYCLE && (totalOperations != 0 || completedOperations != 0 ||
                  failedAttempts != 0 || !activities.isEmpty() || totalRounds == 0)) {
            throw new IllegalArgumentException("QIO monitor runtime kind disagrees with payload");
        }
    }

    @Nonnull
    public static QIOCraftingMonitorRuntimeNode step(@Nonnull QIOStepRuntime runtime) {
        List<Activity> activities = new ArrayList<>();
        for (QIOOperationAssignment assignment : runtime.getActiveOperations().values()) {
            activities.add(Activity.from(assignment));
        }
        activities.sort((left, right) -> left.operationId.compareTo(right.operationId));
        return new QIOCraftingMonitorRuntimeNode(Kind.STEP, runtime.getNodeId(),
              runtime.getRuntimeRevision(), runtime.getRequiredOperations(),
              runtime.getCompletedOperations(), runtime.getFailedAttempts(), 0, 0,
              Collections.emptyMap(), activities);
    }

    @Nonnull
    public static QIOCraftingMonitorRuntimeNode cycle(@Nonnull QIOCycleRuntime runtime,
          long jobRuntimeRevision) {
        return new QIOCraftingMonitorRuntimeNode(Kind.CYCLE, runtime.getCycleNodeId(),
              jobRuntimeRevision, 0, 0, 0, runtime.getCurrentRound(),
              runtime.getTotalRounds(), runtime.getCompletedThisRound(),
              Collections.emptyList());
    }

    @Nonnull public Kind getKind() { return kind; }
    public long getNodeId() { return nodeId; }
    public long getNodeRevision() { return nodeRevision; }
    public long getTotalOperations() { return totalOperations; }
    public long getCompletedOperations() { return completedOperations; }
    public long getFailedAttempts() { return failedAttempts; }
    public long getCurrentRound() { return currentRound; }
    public long getTotalRounds() { return totalRounds; }
    @Nonnull public Map<Long, Long> getCompletedThisRound() { return completedThisRound; }
    @Nonnull public List<Activity> getActivities() { return activities; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("kind", kind.name());
        data.setLong("nodeId", nodeId);
        data.setLong("nodeRevision", nodeRevision);
        data.setLong("totalOperations", totalOperations);
        data.setLong("completedOperations", completedOperations);
        data.setLong("failedAttempts", failedAttempts);
        data.setLong("currentRound", currentRound);
        data.setLong("totalRounds", totalRounds);
        NBTTagList members = new NBTTagList();
        for (Map.Entry<Long, Long> entry : completedThisRound.entrySet()) {
            NBTTagCompound member = new NBTTagCompound();
            member.setLong("nodeId", entry.getKey());
            member.setLong("completed", entry.getValue());
            members.appendTag(member);
        }
        data.setTag("completedThisRound", members);
        NBTTagList activityList = new NBTTagList();
        for (Activity activity : activities) activityList.appendTag(activity.write());
        data.setTag("activities", activityList);
        return data;
    }

    @Nonnull
    public static QIOCraftingMonitorRuntimeNode read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            NBTTagList members = data.getTagList("completedThisRound", NBT.TAG_COMPOUND);
            NBTTagList activityList = data.getTagList("activities", NBT.TAG_COMPOUND);
            if (members.tagCount() > MAX_MEMBERS || activityList.tagCount() > MAX_ACTIVITIES) {
                throw new QIOProcessingDataException("QIO monitor runtime node is too large");
            }
            Map<Long, Long> completed = new LinkedHashMap<>();
            for (int i = 0; i < members.tagCount(); i++) {
                NBTTagCompound member = members.getCompoundTagAt(i);
                if (completed.put(member.getLong("nodeId"), member.getLong("completed")) != null) {
                    throw new QIOProcessingDataException("Duplicate QIO cycle runtime member");
                }
            }
            List<Activity> activities = new ArrayList<>();
            for (int i = 0; i < activityList.tagCount(); i++) {
                activities.add(Activity.read(activityList.getCompoundTagAt(i)));
            }
            return new QIOCraftingMonitorRuntimeNode(
                  QIOProcessingNbt.readEnum(data, "kind", Kind.class),
                  data.getLong("nodeId"), data.getLong("nodeRevision"),
                  data.getLong("totalOperations"), data.getLong("completedOperations"),
                  data.getLong("failedAttempts"), data.getLong("currentRound"),
                  data.getLong("totalRounds"), completed, activities);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO monitor runtime node", e);
        }
    }

    public static final class Activity {
        private final UUID operationId;
        private final String providerKind;
        private final UUID deviceUUID;
        private final long laneId;
        private final long operationCount;
        private final String state;
        private final long currentTick;
        private final long totalTicks;
        private final String diagnostic;

        private Activity(UUID operationId, String providerKind, UUID deviceUUID, long laneId,
              long operationCount, String state, long currentTick, long totalTicks,
              @Nullable String diagnostic) {
            this.operationId = Objects.requireNonNull(operationId, "operationId");
            this.providerKind = bounded(providerKind, 64);
            this.deviceUUID = Objects.requireNonNull(deviceUUID, "deviceUUID");
            this.laneId = QIOProcessingNbt.requireNonNegative(laneId, "laneId");
            if (operationCount <= 0) throw new IllegalArgumentException("operationCount must be positive");
            this.operationCount = operationCount;
            this.state = bounded(state, 64);
            this.currentTick = QIOProcessingNbt.requireNonNegative(currentTick, "currentTick");
            this.totalTicks = QIOProcessingNbt.requireNonNegative(totalTicks, "totalTicks");
            if (totalTicks > 0 && currentTick > totalTicks) {
                throw new IllegalArgumentException("Activity progress exceeds total ticks");
            }
            this.diagnostic = bounded(diagnostic, 512);
        }

        private static Activity from(QIOOperationAssignment assignment) {
            return new Activity(assignment.getOperationId(), assignment.getProviderKind().name(),
                  assignment.getDeviceUUID(), assignment.getLaneId(),
                  assignment.getOperationCount(), assignment.getState().name(),
                  assignment.getCurrentTick(), assignment.getTotalTicks(),
                  assignment.getDiagnostic());
        }

        @Nonnull public UUID getOperationId() { return operationId; }
        @Nonnull public String getProviderKind() { return providerKind; }
        @Nonnull public UUID getDeviceUUID() { return deviceUUID; }
        public long getLaneId() { return laneId; }
        public long getOperationCount() { return operationCount; }
        @Nonnull public String getState() { return state; }
        public long getCurrentTick() { return currentTick; }
        public long getTotalTicks() { return totalTicks; }
        @Nonnull public String getDiagnostic() { return diagnostic; }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            QIOProcessingNbt.writeUUID(data, "operationId", operationId);
            data.setString("providerKind", providerKind);
            QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
            data.setLong("laneId", laneId);
            data.setLong("operationCount", operationCount);
            data.setString("state", state);
            data.setLong("currentTick", currentTick);
            data.setLong("totalTicks", totalTicks);
            if (!diagnostic.isEmpty()) data.setString("diagnostic", diagnostic);
            return data;
        }

        private static Activity read(NBTTagCompound data) throws QIOProcessingDataException {
            return new Activity(QIOProcessingNbt.readUUID(data, "operationId"),
                  data.getString("providerKind"), QIOProcessingNbt.readUUID(data, "deviceUUID"),
                  data.getLong("laneId"), data.getLong("operationCount"),
                  data.getString("state"), data.getLong("currentTick"),
                  data.getLong("totalTicks"), data.getString("diagnostic"));
        }
    }

    private static String bounded(@Nullable String value, int maximum) {
        String checked = value == null ? "" : value.trim();
        return checked.substring(0, Math.min(maximum, checked.length()));
    }
}
