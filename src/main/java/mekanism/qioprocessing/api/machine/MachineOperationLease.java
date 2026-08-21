package mekanism.qioprocessing.api.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Persistent exclusive ownership of one machine lane and its shared port groups. */
/**
 * QIO 处理模块中的 MachineOperationLease 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class MachineOperationLease {

    public static final int MAX_BASELINES = 256;
    private static final String LEASE_ID = "leaseId";
    private static final String OWNER_OPERATION_ID = "ownerOperationId";
    private static final String MODE = "mode";
    private static final String LANE_ID = "laneId";
    private static final String CREATED_AT = "createdAt";
    private static final String STATE = "state";
    private static final String BASELINES = "baselines";
    private static final String CONTAMINATION_REASON = "contaminationReason";

    public enum Mode {
        PROCESSING_EXCLUSIVE,
        OUTPUT_DRAIN
    }

    public enum State {
        ACQUIRED,
        LOADING,
        ACTIVE,
        COLLECTING,
        COMPLETED,
        RELEASED,
        CONTAMINATED;

        public boolean isTerminal() {
            return this == RELEASED || this == CONTAMINATED;
        }
    }

    private final UUID leaseId;
    private final UUID ownerOperationId;
    private final Mode mode;
    private final long laneId;
    private final long createdAt;
    private final State state;
    private final List<MachinePortBaseline> baselines;
    @Nullable
    private final String contaminationReason;

    private MachineOperationLease(UUID leaseId, UUID ownerOperationId, Mode mode, long laneId, long createdAt,
          State state, Collection<MachinePortBaseline> baselines, @Nullable String contaminationReason) {
        this.leaseId = Objects.requireNonNull(leaseId, "Lease id cannot be null");
        this.ownerOperationId = Objects.requireNonNull(ownerOperationId, "Owner operation id cannot be null");
        this.mode = Objects.requireNonNull(mode, "Lease mode cannot be null");
        if (laneId < 0) {
            throw new IllegalArgumentException("Lease lane id cannot be negative");
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("Lease creation tick cannot be negative");
        }
        this.laneId = laneId;
        this.createdAt = createdAt;
        this.state = Objects.requireNonNull(state, "Lease state cannot be null");
        this.baselines = copyBaselines(baselines);
        if (state == State.CONTAMINATED && (contaminationReason == null || contaminationReason.isEmpty())) {
            throw new IllegalArgumentException("A contaminated lease requires a diagnostic reason");
        }
        this.contaminationReason = contaminationReason;
    }

    /**
     * 创建处于 ACQUIRED 状态的新租约。
     *
     * @param leaseId 租约唯一标识
     * @param ownerOperationId 所属操作标识
     * @param mode 租约用途
     * @param laneId 机器通道编号
     * @param createdAt 创建时的游戏 tick
     * @param baselines 端口内容基线
     * @return 新租约
     */
    @Nonnull
    public static MachineOperationLease acquire(@Nonnull UUID leaseId, @Nonnull UUID ownerOperationId,
          @Nonnull Mode mode, long laneId, long createdAt, @Nonnull Collection<MachinePortBaseline> baselines) {
        return new MachineOperationLease(leaseId, ownerOperationId, mode, laneId, createdAt, State.ACQUIRED,
              baselines, null);
    }

    /** 返回租约唯一标识。 */
    @Nonnull
    public UUID leaseId() {
        return leaseId;
    }

    /** 返回拥有该租约的操作标识。 */
    @Nonnull
    public UUID ownerOperationId() {
        return ownerOperationId;
    }

    /** 返回租约用途。 */
    @Nonnull
    public Mode mode() {
        return mode;
    }

    /** 返回租约占用的机器通道编号。 */
    public long laneId() {
        return laneId;
    }

    /** 返回租约创建时的游戏 tick。 */
    public long createdAt() {
        return createdAt;
    }

    /** 返回当前租约状态。 */
    @Nonnull
    public State state() {
        return state;
    }

    /** 返回排序且不可修改的端口基线列表。 */
    @Nonnull
    public List<MachinePortBaseline> baselines() {
        return baselines;
    }

    /** 返回租约涉及的端口组标识集合。 */
    @Nonnull
    public Set<String> portGroupIds() {
        Set<String> groups = new HashSet<>();
        for (MachinePortBaseline baseline : baselines) {
            groups.add(baseline.portGroupId());
        }
        return Collections.unmodifiableSet(groups);
    }

    /** 返回污染诊断；未污染时返回 null。 */
    @Nullable
    public String contaminationReason() {
        return contaminationReason;
    }

    /**
     * 创建目标状态的新租约实例。
     *
     * @param next 目标状态
     * @return 状态转换后的租约
     * @throws IllegalStateException 当前状态不允许转换时抛出
     */
    @Nonnull
    public MachineOperationLease transition(@Nonnull State next) {
        Objects.requireNonNull(next, "Next lease state cannot be null");
        if (next == state) {
            return this;
        }
        if (!canTransition(state, next)) {
            throw new IllegalStateException("Invalid machine lease transition " + state + " -> " + next);
        }
        return new MachineOperationLease(leaseId, ownerOperationId, mode, laneId, createdAt, next, baselines,
              next == State.CONTAMINATED ? "unspecified contamination" : contaminationReason);
    }

    /** 将租约标记为污染并保留原因文本。 */
    @Nonnull
    public MachineOperationLease contaminate(@Nonnull String reason) {
        Objects.requireNonNull(reason, "Contamination reason cannot be null");
        if (reason.isEmpty() || reason.length() > 512) {
            throw new IllegalArgumentException("Contamination reason must contain 1..512 characters");
        }
        if (state == State.RELEASED) {
            throw new IllegalStateException("A released lease cannot become contaminated");
        }
        return new MachineOperationLease(leaseId, ownerOperationId, mode, laneId, createdAt, State.CONTAMINATED,
              baselines, reason);
    }

    /** 仅恢复主机已验证过的旧版自动输出 PREPARED 状态。 */
    @Nonnull
    public MachineOperationLease recoverCollectingAfterOutputRollback() {
        if (mode != Mode.OUTPUT_DRAIN || state != State.CONTAMINATED) {
            throw new IllegalStateException("Only a contaminated output lease can be recovered");
        }
        return new MachineOperationLease(leaseId, ownerOperationId, mode, laneId, createdAt,
              State.COLLECTING, baselines, null);
    }

    /** 将租约写入稳定字段顺序的 NBT。 */
    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString(LEASE_ID, leaseId.toString());
        data.setString(OWNER_OPERATION_ID, ownerOperationId.toString());
        data.setString(MODE, mode.name());
        data.setLong(LANE_ID, laneId);
        data.setLong(CREATED_AT, createdAt);
        data.setString(STATE, state.name());
        NBTTagList baselineList = new NBTTagList();
        for (MachinePortBaseline baseline : baselines) {
            baselineList.appendTag(baseline.write());
        }
        data.setTag(BASELINES, baselineList);
        if (contaminationReason != null) {
            data.setString(CONTAMINATION_REASON, contaminationReason);
        }
        return data;
    }

    /** 从 NBT 读取并校验租约及其全部端口基线。 */
    @Nonnull
    public static MachineOperationLease read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "Lease data cannot be null");
        try {
            NBTTagList list = data.getTagList(BASELINES, NBT.TAG_COMPOUND);
            if (list.tagCount() == 0 || list.tagCount() > MAX_BASELINES) {
                throw new IllegalArgumentException("Lease baseline count is outside 1.." + MAX_BASELINES);
            }
            List<MachinePortBaseline> baselines = new ArrayList<>(list.tagCount());
            for (int index = 0; index < list.tagCount(); index++) {
                baselines.add(MachinePortBaseline.read(list.getCompoundTagAt(index)));
            }
            return new MachineOperationLease(readUUID(data, LEASE_ID), readUUID(data, OWNER_OPERATION_ID),
                  Mode.valueOf(data.getString(MODE)), data.getLong(LANE_ID), data.getLong(CREATED_AT),
                  State.valueOf(data.getString(STATE)), baselines,
                  data.hasKey(CONTAMINATION_REASON, NBT.TAG_STRING) ? data.getString(CONTAMINATION_REASON) : null);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid machine operation lease", e);
        }
    }

    private static boolean canTransition(State current, State next) {
        return switch (current) {
            case ACQUIRED -> next == State.LOADING || next == State.COLLECTING || next == State.RELEASED ||
                             next == State.CONTAMINATED;
            case LOADING -> next == State.ACTIVE || next == State.RELEASED || next == State.CONTAMINATED;
            case ACTIVE -> next == State.COLLECTING || next == State.RELEASED ||
                           next == State.CONTAMINATED;
            case COLLECTING -> next == State.COMPLETED || next == State.CONTAMINATED;
            case COMPLETED, CONTAMINATED -> next == State.RELEASED;
            case RELEASED -> false;
        };
    }

    private static List<MachinePortBaseline> copyBaselines(Collection<MachinePortBaseline> values) {
        Objects.requireNonNull(values, "Lease baselines cannot be null");
        if (values.isEmpty() || values.size() > MAX_BASELINES) {
            throw new IllegalArgumentException("Lease baseline count must be within 1.." + MAX_BASELINES);
        }
        List<MachinePortBaseline> copy = new ArrayList<>(values.size());
        Set<String> ports = new HashSet<>();
        for (MachinePortBaseline baseline : values) {
            MachinePortBaseline value = Objects.requireNonNull(baseline, "Lease baseline cannot be null");
            if (!ports.add(value.portId())) {
                throw new IllegalArgumentException("Duplicate lease baseline port " + value.portId());
            }
            copy.add(value);
        }
        Collections.sort(copy);
        return Collections.unmodifiableList(copy);
    }

    private static UUID readUUID(NBTTagCompound data, String key) {
        String value = data.getString(key);
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("Non-canonical UUID in " + key);
        }
        return parsed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MachineOperationLease other)) {
            return false;
        }
        return laneId == other.laneId && createdAt == other.createdAt && leaseId.equals(other.leaseId) &&
              ownerOperationId.equals(other.ownerOperationId) && mode == other.mode && state == other.state &&
              baselines.equals(other.baselines) && Objects.equals(contaminationReason, other.contaminationReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leaseId, ownerOperationId, mode, laneId, createdAt, state, baselines, contaminationReason);
    }
}
