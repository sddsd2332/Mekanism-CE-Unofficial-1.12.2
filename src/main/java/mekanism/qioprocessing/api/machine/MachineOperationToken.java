package mekanism.qioprocessing.api.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Persistent identity and transfer receipt set for one real machine operation. */
/**
 * QIO 处理模块中的 MachineOperationToken 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class MachineOperationToken {

    public static final int MAX_TRANSFER_RECEIPTS = 512;
    private static final String OPERATION_ID = "operationId";
    private static final String LEASE_ID = "leaseId";
    private static final String KIND = "kind";
    private static final String JOB_ID = "jobId";
    private static final String PLAN_REVISION = "planRevision";
    private static final String ROUTE_ID = "routeId";
    private static final String RECIPE_KEY = "recipeKey";
    private static final String LANE_ID = "laneId";
    private static final String STATE = "state";
    private static final String TRANSFER_RECEIPTS = "transferReceipts";

    public enum Kind {
        JOB,
        PASSIVE,
        OUTPUT_DRAIN
    }

    public enum State {
        ALLOCATED,
        LOADING,
        ACTIVE,
        COLLECTING,
        COMPLETED,
        CANCELLED,
        CONTAMINATED;

        public boolean isTerminal() {
            return this == COMPLETED || this == CANCELLED || this == CONTAMINATED;
        }
    }

    private final UUID operationId;
    private final UUID leaseId;
    private final Kind kind;
    @Nullable
    private final UUID jobId;
    private final long planRevision;
    private final String routeId;
    private final String recipeKey;
    private final long laneId;
    private final State state;
    private final Set<UUID> transferReceipts;

    private MachineOperationToken(UUID operationId, UUID leaseId, Kind kind, @Nullable UUID jobId,
          long planRevision, String routeId, String recipeKey, long laneId, State state, Set<UUID> transferReceipts) {
        this.operationId = Objects.requireNonNull(operationId, "Operation id cannot be null");
        this.leaseId = Objects.requireNonNull(leaseId, "Lease id cannot be null");
        this.kind = Objects.requireNonNull(kind, "Operation kind cannot be null");
        if (kind == Kind.JOB && jobId == null || kind != Kind.JOB && jobId != null) {
            throw new IllegalArgumentException("Only job operations may carry a job id");
        }
        if (kind == Kind.JOB && planRevision < 0 || kind != Kind.JOB && planRevision != -1) {
            throw new IllegalArgumentException("Plan revision must be non-negative for jobs and -1 otherwise");
        }
        this.jobId = jobId;
        this.planRevision = planRevision;
        this.routeId = requireText(routeId, "Route id", kind == Kind.OUTPUT_DRAIN);
        this.recipeKey = requireText(recipeKey, "Recipe key", kind == Kind.OUTPUT_DRAIN);
        if (laneId < 0) {
            throw new IllegalArgumentException("Operation lane id cannot be negative");
        }
        this.laneId = laneId;
        this.state = Objects.requireNonNull(state, "Operation state cannot be null");
        this.transferReceipts = copyReceipts(transferReceipts);
    }

    /** 创建一个计划合成操作 token。参数必须与对应租约和计划版本一致。 */
    @Nonnull
    public static MachineOperationToken job(@Nonnull UUID operationId, @Nonnull UUID leaseId, @Nonnull UUID jobId,
          long planRevision, @Nonnull String routeId, @Nonnull String recipeKey, long laneId) {
        return new MachineOperationToken(operationId, leaseId, Kind.JOB, jobId, planRevision, routeId, recipeKey,
              laneId, State.ALLOCATED, Collections.emptySet());
    }

    /** 创建一个被动处理操作 token。 */
    @Nonnull
    public static MachineOperationToken passive(@Nonnull UUID operationId, @Nonnull UUID leaseId,
          @Nonnull String routeId, @Nonnull String recipeKey, long laneId) {
        return new MachineOperationToken(operationId, leaseId, Kind.PASSIVE, null, -1, routeId, recipeKey,
              laneId, State.ALLOCATED, Collections.emptySet());
    }

    /** 创建一个自动输出排空 token。 */
    @Nonnull
    public static MachineOperationToken outputDrain(@Nonnull UUID operationId, @Nonnull UUID leaseId, long laneId) {
        return new MachineOperationToken(operationId, leaseId, Kind.OUTPUT_DRAIN, null, -1, "", "", laneId,
              State.ALLOCATED, Collections.emptySet());
    }

    /** 返回操作唯一标识。 */
    @Nonnull
    public UUID operationId() {
        return operationId;
    }

    /** 返回关联租约标识。 */
    @Nonnull
    public UUID leaseId() {
        return leaseId;
    }

    /** 返回操作来源类型。 */
    @Nonnull
    public Kind kind() {
        return kind;
    }

    /** 返回关联任务标识；被动和输出操作返回 null。 */
    @Nullable
    public UUID jobId() {
        return jobId;
    }

    /** 返回创建该 token 时使用的计划版本。 */
    public long planRevision() {
        return planRevision;
    }

    /** 返回 Provider 路由标识。 */
    @Nonnull
    public String routeId() {
        return routeId;
    }

    /** 返回配方键。 */
    @Nonnull
    public String recipeKey() {
        return recipeKey;
    }

    /** 返回机器通道编号。 */
    public long laneId() {
        return laneId;
    }

    /** 返回当前操作状态。 */
    @Nonnull
    public State state() {
        return state;
    }

    /** 返回不可修改的 durable transfer 回执集合。 */
    @Nonnull
    public Set<UUID> transferReceipts() {
        return transferReceipts;
    }

    /** 判断 token 是否已经记录指定传输回执。 */
    public boolean hasTransferReceipt(@Nullable UUID transferId) {
        return transferId != null && transferReceipts.contains(transferId);
    }

    /**
     * 创建目标状态的新 token。
     *
     * @param next 目标状态
     * @return 状态转换后的 token
     * @throws IllegalStateException 当前状态不允许转换时抛出
     */
    @Nonnull
    public MachineOperationToken transition(@Nonnull State next) {
        Objects.requireNonNull(next, "Next operation state cannot be null");
        if (next == state) {
            return this;
        }
        if (!canTransition(state, next)) {
            throw new IllegalStateException("Invalid machine operation transition " + state + " -> " + next);
        }
        return copy(next, transferReceipts);
    }

    /** 添加一个幂等的 durable transfer 回执。 */
    @Nonnull
    public MachineOperationToken withTransferReceipt(@Nonnull UUID transferId) {
        Objects.requireNonNull(transferId, "Transfer id cannot be null");
        if (transferReceipts.contains(transferId)) {
            return this;
        }
        if (transferReceipts.size() >= MAX_TRANSFER_RECEIPTS) {
            throw new IllegalStateException("Machine operation transfer receipt limit reached");
        }
        Set<UUID> receipts = new LinkedHashSet<>(transferReceipts);
        receipts.add(transferId);
        return copy(state, receipts);
    }

    /** 仅恢复主机已验证匹配的旧版自动输出污染 token。 */
    @Nonnull
    public MachineOperationToken recoverCollectingAfterOutputRollback() {
        if (kind != Kind.OUTPUT_DRAIN || state != State.CONTAMINATED) {
            throw new IllegalStateException("Only a contaminated output token can be recovered");
        }
        return copy(State.COLLECTING, transferReceipts);
    }

    /** 将 token 和回执按稳定顺序写入 NBT。 */
    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString(OPERATION_ID, operationId.toString());
        data.setString(LEASE_ID, leaseId.toString());
        data.setString(KIND, kind.name());
        if (jobId != null) {
            data.setString(JOB_ID, jobId.toString());
        }
        data.setLong(PLAN_REVISION, planRevision);
        data.setString(ROUTE_ID, routeId);
        data.setString(RECIPE_KEY, recipeKey);
        data.setLong(LANE_ID, laneId);
        data.setString(STATE, state.name());
        NBTTagList receipts = new NBTTagList();
        transferReceipts.stream().map(UUID::toString).sorted().forEach(id -> receipts.appendTag(new NBTTagString(id)));
        data.setTag(TRANSFER_RECEIPTS, receipts);
        return data;
    }

    /** 从 NBT 读取并校验 token 的所有权字段和回执数量。 */
    @Nonnull
    public static MachineOperationToken read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "Operation token data cannot be null");
        try {
            Kind kind = Kind.valueOf(data.getString(KIND));
            NBTTagList list = data.getTagList(TRANSFER_RECEIPTS, NBT.TAG_STRING);
            if (list.tagCount() > MAX_TRANSFER_RECEIPTS) {
                throw new IllegalArgumentException("Too many operation transfer receipts");
            }
            Set<UUID> receipts = new LinkedHashSet<>();
            for (int index = 0; index < list.tagCount(); index++) {
                if (!receipts.add(parseUUID(list.getStringTagAt(index), TRANSFER_RECEIPTS))) {
                    throw new IllegalArgumentException("Duplicate operation transfer receipt");
                }
            }
            UUID jobId = data.hasKey(JOB_ID, NBT.TAG_STRING) ? parseUUID(data.getString(JOB_ID), JOB_ID) : null;
            return new MachineOperationToken(parseUUID(data.getString(OPERATION_ID), OPERATION_ID),
                  parseUUID(data.getString(LEASE_ID), LEASE_ID), kind, jobId, data.getLong(PLAN_REVISION),
                  data.getString(ROUTE_ID), data.getString(RECIPE_KEY), data.getLong(LANE_ID),
                  State.valueOf(data.getString(STATE)), receipts);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid machine operation token", e);
        }
    }

    private MachineOperationToken copy(State state, Set<UUID> receipts) {
        return new MachineOperationToken(operationId, leaseId, kind, jobId, planRevision, routeId, recipeKey,
              laneId, state, receipts);
    }

    private static boolean canTransition(State current, State next) {
        return switch (current) {
            case ALLOCATED -> next == State.LOADING || next == State.COLLECTING || next == State.CANCELLED ||
                              next == State.CONTAMINATED;
            case LOADING -> next == State.ACTIVE || next == State.CANCELLED || next == State.CONTAMINATED;
            case ACTIVE -> next == State.COLLECTING || next == State.CANCELLED ||
                           next == State.CONTAMINATED;
            case COLLECTING -> next == State.COMPLETED || next == State.CONTAMINATED;
            case COMPLETED, CANCELLED, CONTAMINATED -> false;
        };
    }

    private static Set<UUID> copyReceipts(Set<UUID> values) {
        Objects.requireNonNull(values, "Transfer receipts cannot be null");
        if (values.size() > MAX_TRANSFER_RECEIPTS) {
            throw new IllegalArgumentException("Too many operation transfer receipts");
        }
        List<UUID> sorted = new ArrayList<>(values.size());
        for (UUID value : values) {
            sorted.add(Objects.requireNonNull(value, "Transfer receipt cannot be null"));
        }
        Collections.sort(sorted);
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static String requireText(String value, String name, boolean allowEmpty) {
        Objects.requireNonNull(value, name + " cannot be null");
        if ((!allowEmpty && value.isEmpty()) || value.length() > 512) {
            throw new IllegalArgumentException(name + " has an invalid length");
        }
        return value;
    }

    private static UUID parseUUID(String value, String field) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("Non-canonical UUID in " + field);
        }
        return parsed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MachineOperationToken other)) {
            return false;
        }
        return planRevision == other.planRevision && laneId == other.laneId && operationId.equals(other.operationId) &&
              leaseId.equals(other.leaseId) && kind == other.kind && Objects.equals(jobId, other.jobId) &&
              routeId.equals(other.routeId) && recipeKey.equals(other.recipeKey) && state == other.state &&
              transferReceipts.equals(other.transferReceipts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationId, leaseId, kind, jobId, planRevision, routeId, recipeKey, laneId, state,
              transferReceipts);
    }
}
