package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.qioprocessing.api.machine.MachineActivitySnapshot;
import mekanism.qioprocessing.api.machine.MachineOperationLease;
import mekanism.qioprocessing.api.machine.MachineOperationToken;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.api.machine.QIOOutputBufferEntry;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import mekanism.qioprocessing.common.execution.QIOEndpointPersistenceService;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.transfer.QIODurableTransferRecord;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
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

/**
 * 挂载在已接纳机器方块上的 QIO 自动化主机默认实现。
 *
 * <p>主机保存频率绑定、自动化模式以及机器操作的持久化所有权。普通的 Provider
 * 不可用会以可重试状态记录；只有 NBT 结构或资源所有权无法确认时才进入隔离状态。</p>
 */
public final class DefaultQIOAutomationHost implements QIOAutomationHost {

    static final int SCHEMA_VERSION = 2;
    static final int MAX_ACTIVE_LEASES = 256;
    static final int MAX_OPERATION_TOKENS = 256;
    static final int MAX_OUTPUT_BUFFERS = 256;
    private static final String SCHEMA = "schema";
    private static final String DEVICE_UUID = "deviceUUID";
    private static final String FREQUENCY = "frequency";
    private static final String ENABLED_MODE = "enabledMode";
    private static final String STATE = "state";
    private static final String CONFIGURATION_REVISION = "configurationRevision";
    private static final String MANAGEMENT_PAUSED = "managementPaused";
    private static final String LEASES = "leases";
    private static final String TOKENS = "tokens";
    private static final String OUTPUT_BUFFERS = "outputBuffers";
    private static final String DATA_ERROR = "dataError";
    private static final String RECOVERY_STATE = "recoveryState";
    private static final String RECOVERY_DIAGNOSTIC = "recoveryDiagnostic";
    private static final String QUARANTINED_DATA = "quarantinedData";
    private static final String CLEAR_MODE_WHEN_DRAINED = "clearModeWhenDrained";
    private static final String OUTPUT_QUARANTINE_PREFIX = "Automatic output operation quarantined: ";
    private static final String OPERATION_QUARANTINE_PREFIX = "Machine operation quarantined: ";
    private static final String PROCESSING_OUTPUT_PAUSE_PREFIX = "Machine output collection paused: ";
    private static final String LEGACY_OUTPUT_RECOVERY_PENDING =
          "Deferred legacy automatic output recovery pending";

    @Nullable
    private final TileEntity tile;
    private UUID persistentDeviceUUID = UUID.randomUUID();
    @Nullable
    private QIOFrequencyReference frequencyReference;
    @Nullable
    private QIOAutomationMode enabledMode;
    private boolean clearModeWhenDrained;
    private State state = State.UNBOUND;
    private long configurationRevision;
    private boolean managementPaused;
    private Map<UUID, MachineOperationLease> leases = new LinkedHashMap<>();
    private Map<UUID, MachineOperationToken> operationTokens = new LinkedHashMap<>();
    private Map<UUID, QIOOutputBufferEntry> outputBuffers = new LinkedHashMap<>();
    private final Set<UUID> persistedCompletedOperations = new java.util.LinkedHashSet<>();
    private final Set<UUID> persistedTransferReceipts = new java.util.LinkedHashSet<>();
    /** Runtime-only handoff markers set when an output quarantine explicitly yields to ejection. */
    private final Set<UUID> ordinaryOutputHandoffOperations = new HashSet<>();
    private final Map<Long, MachineActivitySnapshot> activitySnapshots = new LinkedHashMap<>();
    @Nullable
    private String dataError;
    private RecoveryState recoveryState = RecoveryState.NONE;
    @Nullable
    private NBTTagCompound quarantinedData;
    @Nullable
    private ParsedState pendingLegacyOutputRecovery;
    private boolean pendingRuntimeOutputRecovery;

    /** Forge 创建脱离方块的 capability 默认实例时使用的构造器。 */
    public DefaultQIOAutomationHost() {
        tile = null;
    }

    /**
     * 创建绑定到指定机器方块的主机。
     *
     * @param tile 所属机器方块，不能为 null
     */
    public DefaultQIOAutomationHost(@Nonnull TileEntity tile) {
        this.tile = Objects.requireNonNull(tile, "Host tile cannot be null");
    }

    @Nullable
    TileEntity tile() {
        return tile;
    }

    /** 返回跨 NBT 保存边界保持不变的机器唯一标识。 */
    @Nonnull
    @Override
    public UUID getPersistentDeviceUUID() {
        return persistentDeviceUUID;
    }

    /** 返回当前频率引用；尚未绑定频率时返回 null。 */
    @Nullable
    @Override
    public QIOFrequencyReference getFrequencyReference() {
        return frequencyReference;
    }

    /** 返回当前启用的自动化模式；尚未安装模式升级时返回 null。 */
    @Nullable
    @Override
    public QIOAutomationMode getEnabledMode() {
        return enabledMode;
    }

    /** 返回主机生命周期状态。旧版 DATA_ERROR 会在序列化/读取边界归一化。 */
    @Nonnull
    @Override
    public State getState() {
        return state;
    }

    /** 返回用于诊断界面的错误或恢复说明，不用于判断是否可以重试。 */
    @Nullable
    public String getDataError() {
        return dataError;
    }

    /** 返回供显式恢复审计使用的原始 capability 数据副本。 */
    @Nullable
    NBTTagCompound getQuarantinedDataCopy() {
        return quarantinedData == null ? null : quarantinedData.copy();
    }

    /**
     * 判断强制恢复是否仍可能涉及外部资源所有权。
     * 只有操作/缓冲、延迟传输或未解析的原始 NBT 才会阻止直接清理；单纯的绑定和暂时性
     * 诊断不能把空闲机器锁死。
     */
    boolean hasPotentialExternalOwnership() {
        if (hasResourceOwnershipState() || pendingLegacyOutputRecovery != null ||
              pendingRuntimeOutputRecovery || quarantinedData != null) {
            return true;
        }
        // A frequency binding and a diagnostic marker are not, by themselves, proof that
        // another service owns resources.  Treating every bound retry marker as external
        // ownership made an idle machine impossible to unbind when the network was not loaded.
        // Actual leases, buffers, deferred transfers, or raw quarantined NBT remain conservative.
        return false;
    }

    @Nonnull
    @Override
    public RecoveryState getRecoveryState() {
        return recoveryState;
    }

    /** 返回是否存在待重试、待恢复或已隔离的持久化操作。 */
    @Override
    public boolean hasRecoveryPending() {
        return recoveryState != RecoveryState.NONE || hasDeferredOutputRecovery();
    }

    /** 返回有限长度的恢复诊断文本；普通运行时重试也可以没有诊断文本。 */
    @Nullable
    @Override
    public String getRecoveryDiagnostic() {
        return dataError;
    }

    /** 判断是否仍有状态必须写入 capability NBT；空闲且未绑定的主机可以省略。 */
    boolean shouldPersistCapabilityNbt() {
        // Completed/released tokens remain durable state until the endpoint snapshot has
        // been confirmed and the execution service prunes them. Do not use only
        // hasUnsettledOperations() here or a crash in that window can lose the ownership
        // receipt needed to finish the operation exactly once.
        return enabledMode != null || frequencyReference != null || clearModeWhenDrained ||
              state != State.UNBOUND || !leases.isEmpty() || !operationTokens.isEmpty() ||
              !outputBuffers.isEmpty() || !persistedCompletedOperations.isEmpty() ||
              !persistedTransferReceipts.isEmpty() || recoveryState != RecoveryState.NONE ||
              dataError != null || quarantinedData != null;
    }

    @Override
    public long getConfigurationRevision() {
        return configurationRevision;
    }

    /** 返回管理端是否暂时暂停了新操作。 */
    @Override
    public boolean isManagementPaused() {
        return managementPaused;
    }

    /**
     * 设置管理暂停标志。
     *
     * @param paused 是否暂停新操作
     * @return 身份冲突时返回 false，否则返回 true
     */
    @Override
    public boolean setManagementPaused(boolean paused) {
        if (state == State.IDENTITY_CONFLICT) return false;
        if (managementPaused != paused) {
            managementPaused = paused;
            incrementConfigurationRevision();
        }
        return true;
    }

    /**
     * 选择并启用一种自动化模式。
     *
     * @param mode 要启用的模式
     * @return 当前没有排空、冲突或未结算操作且选择成功时返回 true
     */
    @Override
    public boolean selectMode(@Nonnull QIOAutomationMode mode) {
        Objects.requireNonNull(mode, "Automation mode cannot be null");
        if (clearModeWhenDrained || hasUnsettledOperations() ||
            state == State.DRAINING_CHANGE || state == State.IDENTITY_CONFLICT ||
            !prepareForConfigurationMutation()) {
            return false;
        }
        enabledMode = mode;
        state = frequencyReference == null ? State.UNBOUND : State.WAITING_ACCESS;
        incrementConfigurationRevision();
        return true;
    }

    /** 请求清除自动化模式；存在所有权时进入排空流程。 */
    @Override
    public boolean clearMode() {
        return clearMode(false);
    }

    /**
     * 请求清除自动化模式并可选择立即解除频率。
     *
     * @param detachFrequencyImmediately 是否立即移除频率引用
     * @return 请求被接受或已完成时返回 true；无法安全恢复时返回 false
     */
    @Override
    public boolean clearMode(boolean detachFrequencyImmediately) {
        if (hasDeferredOutputRecovery()) {
            DeferredOutputRecoveryResult recovery = attemptDeferredOutputRecovery();
            if (recovery != DeferredOutputRecoveryResult.RECOVERED) {
                return false;
            }
        }
        if (state == State.IDENTITY_CONFLICT && hasRemovalOwnedState()) {
            return false;
        }
        if (recoveryState == RecoveryState.QUARANTINED && hasRemovalOwnedState()) {
            // A direct capability call cannot discard an owned operation. The
            // explicit upgrade-removal path audits it through ForcedRecoveryService.
            // A quarantine containing only the binding envelope is safe to clear here,
            // which keeps older addon callbacks from trapping an otherwise idle machine.
            if (!hasResourceOwnershipState() && quarantinedData != null &&
                  isDiagnosticOnlyQuarantine()) {
                clearRecoveryPending();
            } else {
                return false;
            }
        }
        if (state == State.IDENTITY_CONFLICT) {
            clearAutomationStateAfterUpgradeRemoval();
            return true;
        }
        if (hasRemovalOwnedState()) {
            boolean changed = !clearModeWhenDrained || state != State.DRAINING_CHANGE;
            if (detachFrequencyImmediately && frequencyReference != null) {
                frequencyReference = null;
                changed = true;
            }
            if (changed) {
                clearModeWhenDrained = true;
                state = State.DRAINING_CHANGE;
                incrementConfigurationRevision();
            }
            return true;
        }
        clearAutomationStateAfterUpgradeRemoval();
        return true;
    }

    /** 返回当前租约的只读视图。 */
    @Nonnull
    @Override
    public Map<UUID, MachineOperationLease> getLeases() {
        return Collections.unmodifiableMap(leases);
    }

    /** 返回当前操作 token 的只读视图。 */
    @Nonnull
    @Override
    public Map<UUID, MachineOperationToken> getOperationTokens() {
        return Collections.unmodifiableMap(operationTokens);
    }

    /** 返回按机器通道索引的活动快照只读视图。 */
    @Nonnull
    @Override
    public Map<Long, MachineActivitySnapshot> getActivitySnapshots() {
        return Collections.unmodifiableMap(activitySnapshots);
    }

    /** 返回自动输出中间缓冲的只读视图。 */
    @Nonnull
    @Override
    public Map<UUID, QIOOutputBufferEntry> getOutputBufferEntries() {
        return Collections.unmodifiableMap(outputBuffers);
    }

    /**
     * 应用已经由上层授权的频率/模式绑定变更。
     *
     * @param frequency 新频率引用；null 表示解除绑定
     * @param mode 新自动化模式；频率非 null 时不能为 null
     * @param accessValidated 是否已验证请求者可以访问该频率
     * @return 状态允许且变更成功时返回 true
     */
    public boolean configureBinding(@Nullable QIOFrequencyReference frequency, @Nullable QIOAutomationMode mode,
          boolean accessValidated) {
        if (clearModeWhenDrained || hasUnsettledOperations() ||
            state == State.DRAINING_CHANGE || state == State.IDENTITY_CONFLICT ||
            !prepareForConfigurationMutation()) {
            return false;
        }
        if (frequency != null && mode == null) {
            throw new IllegalArgumentException("A frequency binding requires an automation mode");
        }
        frequencyReference = frequency;
        enabledMode = mode;
        state = frequency == null ? State.UNBOUND : accessValidated ? State.ACTIVE : State.WAITING_ACCESS;
        incrementConfigurationRevision();
        return true;
    }

    /**
     * 更新频率访问验证结果。
     *
     * @param valid 当前引用是否可访问
     * @return 主机仍有绑定且接受该状态时返回 true
     */
    public boolean setAccessValidated(boolean valid) {
        if (frequencyReference == null || enabledMode == null || state == State.IDENTITY_CONFLICT) {
            return false;
        }
        if (clearModeWhenDrained || state == State.DRAINING_CHANGE) {
            return valid;
        }
        State next = valid ? State.ACTIVE : State.WAITING_ACCESS;
        if (state != next) {
            state = next;
            markDirty();
        }
        return true;
    }

    void quarantineIdentityConflict() {
        if (state != State.IDENTITY_CONFLICT) {
            state = State.IDENTITY_CONFLICT;
            markDirty();
        }
    }

    /** 显式恢复入口；调用方必须在前后完成所有权审计和设备注册。 */
    boolean regenerateDeviceIdentity() {
        if (clearModeWhenDrained || hasUnsettledOperations()) {
            return false;
        }
        UUID replacement;
        do {
            replacement = UUID.randomUUID();
        } while (!QIOAutomationDeviceRegistry.INSTANCE.isRecoveryIdentityUnique(
              replacement, this));
        persistentDeviceUUID = replacement;
        if (pendingLegacyOutputRecovery != null) {
            // The parsed legacy candidate is keyed by its original device UUID and its durable
            // transfer endpoints use that UUID in their ownership paths. Replaying it after an
            // explicit identity regeneration would silently restore the conflict. Identity
            // recovery is the audited discard boundary, so clear both the executable candidate
            // and the raw payload from which it could otherwise be reconstructed after reload.
            pendingLegacyOutputRecovery = null;
            pendingRuntimeOutputRecovery = false;
            quarantinedData = null;
            dataError = null;
            recoveryState = RecoveryState.NONE;
        }
        state = frequencyReference == null ? State.UNBOUND : State.WAITING_ACCESS;
        incrementConfigurationRevision();
        return true;
    }

    /**
     * 为机器操作申请端口组租约。
     *
     * @param leaseId 租约唯一标识
     * @param ownerOperationId 所属操作标识
     * @param mode 租约用途
     * @param laneId 机器通道编号
     * @param createdAt 创建时的游戏 tick
     * @param baselines 创建时的端口内容基线
     * @return 成功取得的租约；冲突或状态不允许时返回 null
     */
    @Nullable
    @Override
    public MachineOperationLease tryAcquireLease(@Nonnull UUID leaseId, @Nonnull UUID ownerOperationId,
          @Nonnull MachineOperationLease.Mode mode, long laneId, long createdAt,
          @Nonnull Collection<MachinePortBaseline> baselines) {
        Objects.requireNonNull(leaseId, "Lease id cannot be null");
        Objects.requireNonNull(ownerOperationId, "Operation id cannot be null");
        Objects.requireNonNull(mode, "Lease mode cannot be null");
        Objects.requireNonNull(baselines, "Lease baselines cannot be null");
        if (managementPaused || !state.acceptsNewOperations() || hasBlockingRecovery() ||
            !modeMatchesEnabledAutomation(mode) || leases.containsKey(leaseId) ||
            operationTokens.containsKey(ownerOperationId) || leases.size() >= MAX_ACTIVE_LEASES) {
            return null;
        }
        for (MachineOperationLease existing : leases.values()) {
            if (existing.state() == MachineOperationLease.State.RELEASED) {
                continue;
            }
            if (existing.ownerOperationId().equals(ownerOperationId)) {
                return null;
            }
            if (mode == MachineOperationLease.Mode.PROCESSING_EXCLUSIVE &&
                existing.mode() == MachineOperationLease.Mode.PROCESSING_EXCLUSIVE && existing.laneId() == laneId) {
                return null;
            }
            if (sharesPortGroup(existing.portGroupIds(), baselines)) {
                return null;
            }
        }
        MachineOperationLease lease = MachineOperationLease.acquire(leaseId, ownerOperationId, mode, laneId,
              createdAt, baselines);
        leases.put(leaseId, lease);
        markDirty();
        return lease;
    }

    /** 将操作 token 绑定到已经取得的租约。 */
    @Override
    public boolean attachOperationToken(@Nonnull MachineOperationToken token) {
        Objects.requireNonNull(token, "Operation token cannot be null");
        MachineOperationLease lease = leases.get(token.leaseId());
        if (lease == null || lease.state() != MachineOperationLease.State.ACQUIRED ||
            !lease.ownerOperationId().equals(token.operationId()) || lease.laneId() != token.laneId() ||
            operationTokens.containsKey(token.operationId()) || operationTokens.size() >= MAX_OPERATION_TOKENS ||
            !tokenMatchesLeaseMode(token, lease)) {
            return false;
        }
        operationTokens.put(token.operationId(), token);
        persistedCompletedOperations.remove(token.operationId());
        markDirty();
        return true;
    }

    /**
     * 原子推进操作 token 与租约状态。
     *
     * @param operationId 操作标识
     * @param tokenState token 的目标状态
     * @param leaseState 租约的目标状态
     * @return 状态转换成功时返回 true
     */
    @Override
    public boolean transitionOperation(@Nonnull UUID operationId, @Nonnull MachineOperationToken.State tokenState,
          @Nonnull MachineOperationLease.State leaseState) {
        Objects.requireNonNull(operationId, "Operation id cannot be null");
        MachineOperationToken token = operationTokens.get(operationId);
        if (token == null) {
            return false;
        }
        MachineOperationLease lease = leases.get(token.leaseId());
        if (lease == null || !lease.ownerOperationId().equals(operationId)) {
            markRecoveryPending("Operation token references a missing or mismatched lease");
            return false;
        }
        try {
            MachineOperationToken nextToken = token.transition(tokenState);
            MachineOperationLease nextLease = lease.transition(leaseState);
            operationTokens.put(operationId, nextToken);
            leases.put(lease.leaseId(), nextLease);
            markDirty();
            // A completed token is not durable until its lease has also been released.  The
            // execution service performs those transitions separately; requesting a checkpoint
            // after the first transition could acknowledge a snapshot that still owns the lane.
            if (nextToken.state() == MachineOperationToken.State.COMPLETED &&
                  nextLease.state() == MachineOperationLease.State.RELEASED && tile != null) {
                QIOEndpointPersistenceService.INSTANCE.requestAutomationHost(tile, this,
                      operationId);
            }
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    /** 记录一次已写入持久传输记录的回执，保证重启后可以幂等恢复。 */
    @Override
    public boolean recordTransferReceipt(@Nonnull UUID operationId, @Nonnull UUID transferId) {
        Objects.requireNonNull(operationId, "Operation id cannot be null");
        Objects.requireNonNull(transferId, "Transfer id cannot be null");
        MachineOperationToken token = operationTokens.get(operationId);
        if (token == null || token.state().isTerminal()) {
            return false;
        }
        MachineOperationToken updated = token.withTransferReceipt(transferId);
        if (updated != token) {
            operationTokens.put(operationId, updated);
            persistedTransferReceipts.remove(transferId);
            markDirty();
        }
        return true;
    }

    /** 将仍拥有资源的租约标记为污染并保留所有权记录供恢复审计。 */
    @Override
    public boolean contaminateLease(@Nonnull UUID leaseId, @Nonnull String reason) {
        Objects.requireNonNull(leaseId, "Lease id cannot be null");
        MachineOperationLease lease = leases.get(leaseId);
        if (lease == null || lease.state() == MachineOperationLease.State.RELEASED) {
            return false;
        }
        // A PREPARED/HELD/DELIVERING output buffer is only valid while its token and
        // lease remain COLLECTING. Transitioning either object to CONTAMINATED here
        // creates an NBT state that parse() must reject and strands the durable
        // machine transfer. Quarantine the whole host instead and retain every
        // ownership record for explicit recovery.
        QIOOutputBufferEntry buffered = outputBuffers.values().stream()
              .filter(entry -> entry.leaseId().equals(leaseId)).findFirst().orElse(null);
        if (buffered != null) {
            markRecoveryPending(OUTPUT_QUARANTINE_PREFIX + reason);
            // PREPARED means the machine-side extraction has not been observed and
            // must go through the strict baseline recovery path. HELD/DELIVERING
            // already own a concrete resource/transfer and can be reconciled by the
            // normal output controller without replaying extraction. The strict path
            // is intentionally single-buffer; multiple live lanes are handled by the
            // normal per-buffer controller instead of being trapped in BLOCKED forever.
            pendingRuntimeOutputRecovery = buffered.phase() == QIOOutputBufferEntry.Phase.PREPARED &&
                  outputBuffers.size() == 1;
            QIOAutomationDeviceRegistry.INSTANCE.trackPending(this);
            return true;
        }
        leases.put(leaseId, lease.contaminate(reason));
        MachineOperationToken token = operationTokens.get(lease.ownerOperationId());
        if (token != null && !token.state().isTerminal()) {
            operationTokens.put(token.operationId(), token.transition(MachineOperationToken.State.CONTAMINATED));
        }
        markRecoveryPending(OPERATION_QUARANTINE_PREFIX + reason);
        return true;
    }

    /**
     * Stops every QIO processing operation on this machine after output collection becomes
     * unreliable. The leases remain as durable audit records, but ordinary machine ejection is
     * allowed to resume while the central processing service handles the contaminated tokens.
     */
    public boolean pauseProcessingOutputCollection(@Nonnull String reason) {
        String pauseReason = boundedDiagnostic(PROCESSING_OUTPUT_PAUSE_PREFIX +
              Objects.requireNonNull(reason, "Pause reason cannot be null"));
        boolean foundProcessingOwnership = false;
        for (MachineOperationLease snapshot : new ArrayList<>(leases.values())) {
            if (snapshot.mode() != MachineOperationLease.Mode.PROCESSING_EXCLUSIVE ||
                  snapshot.state() == MachineOperationLease.State.RELEASED) {
                continue;
            }
            foundProcessingOwnership = true;
            MachineOperationToken token = operationTokens.get(snapshot.ownerOperationId());
            if (token == null && snapshot.state() == MachineOperationLease.State.ACQUIRED) {
                releaseUnattachedLease(snapshot.leaseId());
            } else if (snapshot.state() != MachineOperationLease.State.CONTAMINATED &&
                  (token == null || !token.state().isTerminal())) {
                contaminateLease(snapshot.leaseId(), pauseReason);
            }
        }
        if (!foundProcessingOwnership) {
            return false;
        }
        // contaminateLease records a per-operation diagnostic. Replace it with the machine-wide
        // marker so new QIO work stays blocked while ordinary ejection remains available.
        markRecoveryPending(OPERATION_QUARANTINE_PREFIX + pauseReason);
        if (frequencyReference != null) {
            QIOProcessingExecutionService.INSTANCE.wakeDeviceContents(
                  frequencyReference.getFrequencyUUID(), persistentDeviceUUID);
        }
        return true;
    }

    /** Returns whether a collection failure has paused QIO processing for this whole machine. */
    public boolean isProcessingOutputCollectionPaused() {
        return recoveryState == RecoveryState.QUARANTINED && dataError != null &&
              dataError.startsWith(OPERATION_QUARANTINE_PREFIX +
                    PROCESSING_OUTPUT_PAUSE_PREFIX);
    }

    /**
     * Returns whether an automatic-output recovery quarantine has yielded the machine back to
     * its ordinary ejector.  The output lease is intentionally retained for durable recovery,
     * but it must not keep a transiently broken QIO endpoint from accepting external output.
     */
    public boolean isAutomaticOutputCollectionPaused() {
        return isKnownOutputQuarantine();
    }

    /**
     * Pauses only automatic-output recovery while retaining every buffered ownership record.
     * Ordinary machine ejection is allowed to continue; the durable buffer/transfer is retried
     * when the QIO endpoint becomes observable again.
     */
    public boolean pauseAutomaticOutputCollection(@Nonnull String reason) {
        String boundedReason = boundedDiagnostic(Objects.requireNonNull(reason,
              "Pause reason cannot be null"));
        String pauseReason = boundedDiagnostic(OUTPUT_QUARANTINE_PREFIX + boundedReason);
        boolean found = false;
        int preparedBuffers = 0;
        for (QIOOutputBufferEntry entry : new ArrayList<>(outputBuffers.values())) {
            MachineOperationLease lease = leases.get(entry.leaseId());
            if (lease == null || lease.state() == MachineOperationLease.State.RELEASED) {
                continue;
            }
            if (entry.phase() == QIOOutputBufferEntry.Phase.PREPARED) {
                preparedBuffers++;
                ordinaryOutputHandoffOperations.add(entry.operationId());
            }
            found |= contaminateLease(lease.leaseId(), boundedReason);
        }
        if (found) {
            // The strict deferred-recovery parser is intentionally single-buffer: it can make
            // an exact baseline decision without choosing between competing output lanes. When
            // several buffers are live, leave this flag clear and let the normal controller
            // reconcile each durable entry independently instead of returning BLOCKED forever.
            if (preparedBuffers != 1 || outputBuffers.size() != 1) {
                pendingRuntimeOutputRecovery = false;
            }
            markRecoveryPending(pauseReason);
            QIOAutomationDeviceRegistry.INSTANCE.trackPending(this);
        }
        return found;
    }

    /** Returns whether this prepared output was explicitly yielded to the ordinary ejector. */
    boolean isOrdinaryOutputHandoffAllowed(@Nonnull UUID operationId) {
        return ordinaryOutputHandoffOperations.contains(Objects.requireNonNull(operationId,
              "Operation id cannot be null"));
    }

    /**
     * 释放已经由外部恢复流程确认不再拥有资源的污染操作。
     *
     * @param operationId 操作标识
     * @return 满足污染状态且成功释放时返回 true
     */
    @Override
    public boolean releaseContaminatedOperation(@Nonnull UUID operationId) {
        UUID checked = Objects.requireNonNull(operationId, "Operation id cannot be null");
        MachineOperationToken token = operationTokens.get(checked);
        MachineOperationLease lease = token == null ? null : leases.get(token.leaseId());
        if (token == null || lease == null || token.state() != MachineOperationToken.State.CONTAMINATED ||
            lease.state() != MachineOperationLease.State.CONTAMINATED ||
            !lease.ownerOperationId().equals(checked) ||
            outputBuffers.values().stream().anyMatch(entry -> entry.operationId().equals(checked))) {
            return false;
        }
        try {
            leases.put(lease.leaseId(), lease.transition(MachineOperationLease.State.RELEASED));
            activitySnapshots.remove(token.laneId());
            markDirty();
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    /** 放弃一个计划合成操作，但不擅自领取机器输出或退回已进入机器的输入。 */
    @Override
    public boolean abandonJobOperation(@Nonnull UUID operationId) {
        Objects.requireNonNull(operationId, "Operation id cannot be null");
        MachineOperationToken token = operationTokens.get(operationId);
        if (token == null || token.kind() != MachineOperationToken.Kind.JOB ||
              token.state() == MachineOperationToken.State.COLLECTING ||
              token.state() == MachineOperationToken.State.COMPLETED) {
            return false;
        }
        MachineOperationLease lease = leases.get(token.leaseId());
        if (lease == null || !lease.ownerOperationId().equals(operationId)) {
            markRecoveryPending("Operation token references a missing or mismatched lease");
            return false;
        }
        try {
            MachineOperationToken settledToken = token.state().isTerminal() ? token :
                  token.transition(MachineOperationToken.State.CANCELLED);
            MachineOperationLease releasedLease = lease.state() ==
                  MachineOperationLease.State.RELEASED ? lease :
                  lease.transition(MachineOperationLease.State.RELEASED);
            operationTokens.put(operationId, settledToken);
            leases.put(releasedLease.leaseId(), releasedLease);
            activitySnapshots.remove(token.laneId());
            markDirty();
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    /** 在尚未附加 token 时释放已取得的租约。 */
    @Override
    public boolean releaseUnattachedLease(@Nonnull UUID leaseId) {
        MachineOperationLease lease = leases.get(Objects.requireNonNull(leaseId, "Lease id cannot be null"));
        if (lease == null || lease.state() != MachineOperationLease.State.ACQUIRED ||
              operationTokens.containsKey(lease.ownerOperationId())) {
            return false;
        }
        leases.remove(leaseId);
        markDirty();
        return true;
    }

    /** 写入自动输出的 PREPARED 缓冲，并校验其 token、租约和端口基线一致。 */
    boolean prepareOutputBuffer(@Nonnull QIOOutputBufferEntry entry) {
        Objects.requireNonNull(entry, "Output buffer entry cannot be null");
        MachineOperationToken token = operationTokens.get(entry.operationId());
        MachineOperationLease lease = leases.get(entry.leaseId());
        if (entry.phase() != QIOOutputBufferEntry.Phase.PREPARED || outputBuffers.containsKey(entry.bufferId()) ||
            outputBuffers.size() >= MAX_OUTPUT_BUFFERS || token == null || lease == null ||
            token.kind() != MachineOperationToken.Kind.OUTPUT_DRAIN || token.state() != MachineOperationToken.State.COLLECTING ||
            !token.leaseId().equals(entry.leaseId()) || lease.state() != MachineOperationLease.State.COLLECTING ||
            !entry.baseline().portGroupId().equals(lease.baselines().get(0).portGroupId())) {
            return false;
        }
        outputBuffers.put(entry.bufferId(), entry);
        markDirty();
        return true;
    }

    /**
     * Drops an automatic-output operation which never acquired an external resource.
     *
     * <p>This is intentionally narrower than {@link #forceRecoverAfterDataError()} and is
     * used only by the automatic-output controller after it has proved that there is no
     * output buffer, receipt, or durable transfer for the operation.  A contaminated
     * processing/job operation must continue through its owning job/passive recovery path.
     * Keeping this operation local also prevents a failed pre-buffer preparation from
     * becoming an orphaned CONTAMINATED token which no execution service can settle.</p>
     */
    boolean discardUnownedOutputOperation(@Nonnull UUID operationId) {
        Objects.requireNonNull(operationId, "Operation id cannot be null");
        MachineOperationToken token = operationTokens.get(operationId);
        if (token == null || token.kind() != MachineOperationToken.Kind.OUTPUT_DRAIN ||
              !token.transferReceipts().isEmpty() ||
              outputBuffers.values().stream().anyMatch(entry ->
                    entry.operationId().equals(operationId))) {
            return false;
        }
        MachineOperationLease lease = leases.get(token.leaseId());
        if (lease == null || lease.mode() != MachineOperationLease.Mode.OUTPUT_DRAIN ||
              !lease.ownerOperationId().equals(operationId) ||
              (lease.state() != MachineOperationLease.State.ACQUIRED &&
                    lease.state() != MachineOperationLease.State.COLLECTING &&
                    lease.state() != MachineOperationLease.State.CONTAMINATED) ||
              (token.state() != MachineOperationToken.State.ALLOCATED &&
                    token.state() != MachineOperationToken.State.COLLECTING &&
                    token.state() != MachineOperationToken.State.CONTAMINATED)) {
            return false;
        }
        operationTokens.remove(operationId);
        leases.remove(lease.leaseId());
        ordinaryOutputHandoffOperations.remove(operationId);
        activitySnapshots.remove(token.laneId());
        clearKnownQuarantineIfUnowned();
        markDirty();
        return true;
    }

    /**
     * Releases a PREPARED output intent after the ordinary ejector has taken the exact stack.
     * At this point the durable machine transfer is either absent or still PREPARED, so QIO has
     * no physical receipt and must not recreate the stack in its storage.  This is deliberately
     * separate from {@link #discardUnownedOutputOperation(UUID)} because the live port no longer
     * matches its original baseline after the external handoff.
     */
    boolean discardPreparedOutputAfterExternalHandoff(@Nonnull UUID operationId,
          @Nonnull UUID bufferId) {
        Objects.requireNonNull(operationId, "Operation id cannot be null");
        Objects.requireNonNull(bufferId, "Output buffer id cannot be null");
        MachineOperationToken token = operationTokens.get(operationId);
        QIOOutputBufferEntry entry = outputBuffers.get(bufferId);
        if (token == null || token.kind() != MachineOperationToken.Kind.OUTPUT_DRAIN ||
              !token.transferReceipts().isEmpty() || entry == null ||
              entry.phase() != QIOOutputBufferEntry.Phase.PREPARED ||
              !entry.operationId().equals(operationId)) {
            return false;
        }
        MachineOperationLease lease = leases.get(token.leaseId());
        if (lease == null || lease.mode() != MachineOperationLease.Mode.OUTPUT_DRAIN ||
              !lease.ownerOperationId().equals(operationId) ||
              !entry.leaseId().equals(lease.leaseId()) ||
              (token.state() != MachineOperationToken.State.COLLECTING &&
                    token.state() != MachineOperationToken.State.CONTAMINATED) ||
              (lease.state() != MachineOperationLease.State.COLLECTING &&
                    lease.state() != MachineOperationLease.State.CONTAMINATED)) {
            return false;
        }
        outputBuffers.remove(bufferId);
        operationTokens.remove(operationId);
        leases.remove(lease.leaseId());
        ordinaryOutputHandoffOperations.remove(operationId);
        persistedCompletedOperations.remove(operationId);
        persistedTransferReceipts.removeAll(token.transferReceipts());
        activitySnapshots.remove(token.laneId());
        clearKnownQuarantineIfUnowned();
        markDirty();
        if (frequencyReference != null) {
            QIOProcessingExecutionService.INSTANCE.wakeDevice(
                  frequencyReference.getFrequencyUUID(), persistentDeviceUUID);
        }
        return true;
    }

    /** 将已从机器取出的资源写入输出缓冲，进入等待投递阶段。 */
    boolean holdOutput(@Nonnull UUID bufferId, @Nonnull PortableResourceDescriptor resource, long amount) {
        QIOOutputBufferEntry entry = outputBuffers.get(Objects.requireNonNull(bufferId, "Output buffer id cannot be null"));
        if (entry == null || entry.phase() != QIOOutputBufferEntry.Phase.PREPARED) {
            return false;
        }
        outputBuffers.put(bufferId, entry.hold(resource, amount));
        markDirty();
        return true;
    }

    /** 为输出缓冲创建 QIO 投递记录并记录请求数量。 */
    boolean beginOutputDelivery(@Nonnull UUID bufferId, @Nonnull UUID transferId, long requestedAmount) {
        QIOOutputBufferEntry entry = outputBuffers.get(Objects.requireNonNull(bufferId, "Output buffer id cannot be null"));
        if (entry == null || entry.phase() != QIOOutputBufferEntry.Phase.HELD) {
            return false;
        }
        outputBuffers.put(bufferId, entry.beginDelivery(transferId, requestedAmount));
        markDirty();
        return true;
    }

    /** 应用一次输出投递回执；完全投递后同时结算 token 和租约。 */
    boolean applyOutputDeliveryReceipt(@Nonnull UUID bufferId, @Nonnull UUID transferId, long transferredAmount) {
        QIOOutputBufferEntry entry = outputBuffers.get(Objects.requireNonNull(bufferId, "Output buffer id cannot be null"));
        if (entry == null || entry.phase() != QIOOutputBufferEntry.Phase.DELIVERING) {
            return false;
        }
        MachineOperationToken token = operationTokens.get(entry.operationId());
        MachineOperationLease lease = leases.get(entry.leaseId());
        if (token == null || lease == null || token.state() != MachineOperationToken.State.COLLECTING ||
            lease.state() != MachineOperationLease.State.COLLECTING) {
            markRecoveryPending("Output buffer lost its collecting operation ownership");
            pendingRuntimeOutputRecovery = entry.phase() == QIOOutputBufferEntry.Phase.PREPARED;
            return false;
        }
        QIOOutputBufferEntry remaining = entry.applyDeliveryReceipt(transferId, transferredAmount);
        operationTokens.put(token.operationId(), token.withTransferReceipt(transferId));
        if (remaining == null) {
            outputBuffers.remove(bufferId);
            ordinaryOutputHandoffOperations.remove(entry.operationId());
            MachineOperationToken completedToken = operationTokens.get(token.operationId())
                  .transition(MachineOperationToken.State.COMPLETED);
            MachineOperationLease completedLease = lease.transition(MachineOperationLease.State.COMPLETED)
                  .transition(MachineOperationLease.State.RELEASED);
            operationTokens.put(completedToken.operationId(), completedToken);
            leases.put(completedLease.leaseId(), completedLease);
        } else {
            outputBuffers.put(bufferId, remaining);
        }
        if (remaining == null && isKnownOutputQuarantine() &&
              quarantinedData == null && !hasDeferredOutputRecovery()) {
            clearRecoveryPending();
        }
        markDirty();
        if (remaining == null && tile != null) {
            QIOEndpointPersistenceService.INSTANCE.requestAutomationHost(tile, this,
                  token.operationId());
        }
        return true;
    }

    /** 删除已完成、已释放且所有回执已持久化的操作。 */
    @Override
    public boolean forgetSettledOperation(@Nonnull UUID operationId) {
        MachineOperationToken token = operationTokens.get(Objects.requireNonNull(operationId, "Operation id cannot be null"));
        if (token == null || !token.state().isTerminal()) {
            return false;
        }
        MachineOperationLease lease = leases.get(token.leaseId());
        if (lease == null || lease.state() != MachineOperationLease.State.RELEASED ||
            outputBuffers.values().stream().anyMatch(entry -> entry.operationId().equals(operationId))) {
            return false;
        }
        operationTokens.remove(operationId);
        ordinaryOutputHandoffOperations.remove(operationId);
        persistedCompletedOperations.remove(operationId);
        persistedTransferReceipts.removeAll(token.transferReceipts());
        leases.remove(lease.leaseId());
        activitySnapshots.remove(token.laneId());
        clearKnownOperationQuarantineIfSettled();
        markDirty();
        if (frequencyReference != null) {
            QIOProcessingExecutionService.INSTANCE.wakeDevice(
                  frequencyReference.getFrequencyUUID(), persistentDeviceUUID);
        }
        return true;
    }

    /** 返回完成操作是否已在 endpoint 快照中确认持久化。 */
    public boolean isPersistedCompletedOperation(@Nonnull UUID operationId) {
        return persistedCompletedOperations.contains(Objects.requireNonNull(operationId,
              "Operation id cannot be null"));
    }

    /** 返回指定传输回执是否已写入 endpoint 快照。 */
    @Override
    public boolean isTransferReceiptPersisted(@Nonnull UUID operationId,
          @Nonnull UUID transferId) {
        MachineOperationToken token = operationTokens.get(Objects.requireNonNull(operationId,
              "operationId"));
        UUID checkedTransfer = Objects.requireNonNull(transferId, "transferId");
        return token != null && token.hasTransferReceipt(checkedTransfer) &&
              persistedTransferReceipts.contains(checkedTransfer);
    }

    /** Confirms one operation receipt was included in a completed endpoint snapshot. */
    @Override
    public void confirmTransferReceiptPersisted(@Nonnull UUID operationId,
          @Nonnull UUID transferId) {
        MachineOperationToken token = operationTokens.get(Objects.requireNonNull(operationId,
              "operationId"));
        UUID checkedTransfer = Objects.requireNonNull(transferId, "transferId");
        if (token != null && token.hasTransferReceipt(checkedTransfer)) {
            persistedTransferReceipts.add(checkedTransfer);
            wakeExecution();
        }
    }

    /** Confirms one completed operation was included in an endpoint persistence snapshot. */
    @Override
    public void confirmCompletedOperationPersisted(@Nonnull UUID operationId) {
        UUID checked = Objects.requireNonNull(operationId, "operationId");
        MachineOperationToken token = operationTokens.get(checked);
        MachineOperationLease lease = token == null ? null : leases.get(token.leaseId());
        if (token != null && token.state() == MachineOperationToken.State.COMPLETED &&
              lease != null && lease.state() == MachineOperationLease.State.RELEASED &&
              lease.ownerOperationId().equals(checked)) {
            persistedCompletedOperations.add(checked);
            wakeExecution();
        }
    }

    /** 批量确认当前所有已完成且已释放的操作都已进入 endpoint 快照。 */
    public void confirmCompletedOperationsPersisted() {
        int before = persistedCompletedOperations.size();
        operationTokens.values().stream()
              .filter(token -> token.state() == MachineOperationToken.State.COMPLETED)
              .filter(token -> {
                  MachineOperationLease lease = leases.get(token.leaseId());
                  return lease != null && lease.state() == MachineOperationLease.State.RELEASED &&
                        lease.ownerOperationId().equals(token.operationId());
              })
              .map(MachineOperationToken::operationId)
              .forEach(persistedCompletedOperations::add);
        if (persistedCompletedOperations.size() != before) {
            wakeExecution();
        }
    }

    /** 更新终端和管理界面使用的机器活动快照。 */
    @Override
    public void updateActivitySnapshot(@Nonnull MachineActivitySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Activity snapshot cannot be null");
        activitySnapshots.put(snapshot.laneId(), snapshot);
    }

    /** 将主机状态按稳定顺序序列化为 NBT；旧 DATA_ERROR 只作为兼容输入读取。 */
    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger(SCHEMA, SCHEMA_VERSION);
        data.setString(DEVICE_UUID, persistentDeviceUUID.toString());
        if (frequencyReference != null) {
            data.setTag(FREQUENCY, frequencyReference.write());
        }
        if (enabledMode != null) {
            data.setString(ENABLED_MODE, enabledMode.name());
        }
        data.setBoolean(CLEAR_MODE_WHEN_DRAINED, clearModeWhenDrained);
        // DATA_ERROR was an older lifecycle value. Keep accepting it while reading old
        // saves, but never emit it again: recovery diagnostics are represented by the
        // independent RecoveryState and the endpoint remains in its normal lifecycle.
        State persistedState = state == State.DATA_ERROR ?
              (frequencyReference == null ? State.UNBOUND : State.WAITING_ACCESS) : state;
        data.setString(STATE, persistedState.name());
        data.setLong(CONFIGURATION_REVISION, configurationRevision);
        data.setBoolean(MANAGEMENT_PAUSED, managementPaused);
        NBTTagList leaseList = new NBTTagList();
        sortedLeases().forEach(lease -> leaseList.appendTag(lease.write()));
        data.setTag(LEASES, leaseList);
        NBTTagList tokenList = new NBTTagList();
        sortedTokens().forEach(token -> tokenList.appendTag(token.write()));
        data.setTag(TOKENS, tokenList);
        NBTTagList outputBufferList = new NBTTagList();
        sortedOutputBuffers().forEach(entry -> outputBufferList.appendTag(entry.write()));
        data.setTag(OUTPUT_BUFFERS, outputBufferList);
        if (recoveryState != RecoveryState.NONE) {
            data.setString(RECOVERY_STATE, recoveryState.name());
        }
        if (dataError != null) {
            data.setString(DATA_ERROR, dataError);
            data.setString(RECOVERY_DIAGNOSTIC, dataError);
        }
        if (quarantinedData != null) {
            data.setTag(QUARANTINED_DATA, quarantinedData.copy());
        }
        return data;
    }

    /** 读取主机 NBT；结构损坏时只保留可验证的绑定外壳并进入诊断隔离。 */
    @Override
    public void deserializeNBT(NBTTagCompound data) {
        // Handoff markers describe only the live tick in which a quarantined output was
        // explicitly yielded to the ordinary ejector; never carry them across a tile reload.
        ordinaryOutputHandoffOperations.clear();
        if (data == null || data.getKeySet().isEmpty()) {
            resetToUnbound();
            return;
        }
        pendingLegacyOutputRecovery = null;
        pendingRuntimeOutputRecovery = false;
        try {
            ParsedState parsed = parse(data, false);
            applyParsedState(parsed, data.getBoolean(MANAGEMENT_PAUSED));
            if (isRuntimeOutputRecoveryCandidate(parsed)) {
                pendingRuntimeOutputRecovery = true;
            } else if (parsed.recoveryState == RecoveryState.QUARANTINED &&
                  parsed.quarantinedData != null) {
                pendingLegacyOutputRecovery = tryParseLegacyOutputCandidate(parsed.quarantinedData);
            }
            completePendingModeClearIfReady();
        } catch (RuntimeException e) {
            ParsedState legacy = tryParseLegacyOutputCandidate(data);
            if (legacy != null) {
                installPendingLegacyOutputRecovery(legacy, data);
                return;
            }
            preserveSafeBindingMetadata(data);
            clearModeWhenDrained = false;
            managementPaused = false;
            leases = new LinkedHashMap<>();
            operationTokens = new LinkedHashMap<>();
            outputBuffers = new LinkedHashMap<>();
            persistedCompletedOperations.clear();
            persistedTransferReceipts.clear();
            activitySnapshots.clear();
            quarantinedData = data.copy();
            quarantinedData.removeTag(QUARANTINED_DATA);
            enterDataError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /**
     * Salvages only the host identity/binding envelope after a schema failure. Operation
     * maps, leases, buffers and receipts are intentionally never reconstructed here.
     */
    private void preserveSafeBindingMetadata(@Nonnull NBTTagCompound data) {
        if (data.hasKey(DEVICE_UUID, NBT.TAG_STRING)) {
            try {
                persistentDeviceUUID = parseUUID(data.getString(DEVICE_UUID), DEVICE_UUID);
            } catch (RuntimeException ignored) {
                // Keep the generated identity when the stored UUID is malformed.
            }
        }
        frequencyReference = null;
        if (data.hasKey(FREQUENCY, NBT.TAG_COMPOUND)) {
            try {
                frequencyReference = QIOFrequencyReference.read(data.getCompoundTag(FREQUENCY));
            } catch (RuntimeException ignored) {
                // A malformed reference must not be trusted or reattached.
            }
        }
        enabledMode = null;
        if (data.hasKey(ENABLED_MODE, NBT.TAG_STRING)) {
            try {
                enabledMode = QIOAutomationMode.valueOf(data.getString(ENABLED_MODE));
            } catch (RuntimeException ignored) {
                // Leave the mode unset; the player can install a fresh upgrade.
            }
        }
        configurationRevision = data.hasKey(CONFIGURATION_REVISION, NBT.TAG_LONG) ?
              Math.max(0, data.getLong(CONFIGURATION_REVISION)) : 0;
        state = frequencyReference != null && enabledMode != null ?
              State.WAITING_ACCESS : State.UNBOUND;
    }

    private void applyParsedState(ParsedState parsed, boolean paused) {
        persistentDeviceUUID = parsed.deviceUUID;
        frequencyReference = parsed.frequency;
        enabledMode = parsed.enabledMode;
        clearModeWhenDrained = parsed.clearModeWhenDrained;
        state = isRuntimeOutputRecoveryCandidate(parsed) ?
              (parsed.frequency == null ? State.UNBOUND : State.WAITING_ACCESS) : parsed.state;
        configurationRevision = parsed.configurationRevision;
        managementPaused = paused;
        leases = new LinkedHashMap<>(parsed.leases);
        operationTokens = new LinkedHashMap<>(parsed.tokens);
        outputBuffers = new LinkedHashMap<>(parsed.outputBuffers);
        persistedCompletedOperations.clear();
        persistedTransferReceipts.clear();
        parsed.tokens.values().stream()
              .filter(token -> token.state() == MachineOperationToken.State.COMPLETED)
              .filter(token -> {
                  MachineOperationLease lease = parsed.leases.get(token.leaseId());
                  return lease != null && lease.state() == MachineOperationLease.State.RELEASED &&
                        lease.ownerOperationId().equals(token.operationId());
              })
              .map(MachineOperationToken::operationId)
              .forEach(persistedCompletedOperations::add);
        parsed.tokens.values().stream().flatMap(token -> token.transferReceipts().stream())
              .forEach(persistedTransferReceipts::add);
        dataError = parsed.dataError;
        recoveryState = parsed.recoveryState;
        quarantinedData = parsed.quarantinedData;
        activitySnapshots.clear();
    }

    @Nullable
    private ParsedState tryParseLegacyOutputCandidate(NBTTagCompound raw) {
        try {
            ParsedState candidate = parse(raw, true);
            return candidate.legacyOutputRecoveryCandidate ? candidate : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void installPendingLegacyOutputRecovery(ParsedState candidate,
          NBTTagCompound raw) {
        persistentDeviceUUID = candidate.deviceUUID;
        frequencyReference = candidate.frequency;
        enabledMode = candidate.enabledMode;
        clearModeWhenDrained = candidate.clearModeWhenDrained;
        state = candidate.frequency == null ? State.UNBOUND : State.WAITING_ACCESS;
        configurationRevision = candidate.configurationRevision;
        managementPaused = false;
        leases = new LinkedHashMap<>();
        operationTokens = new LinkedHashMap<>();
        outputBuffers = new LinkedHashMap<>();
        persistedCompletedOperations.clear();
        persistedTransferReceipts.clear();
        activitySnapshots.clear();
        dataError = LEGACY_OUTPUT_RECOVERY_PENDING;
        recoveryState = RecoveryState.QUARANTINED;
        quarantinedData = raw.copy();
        quarantinedData.removeTag(QUARANTINED_DATA);
        pendingLegacyOutputRecovery = candidate;
        pendingRuntimeOutputRecovery = false;
    }

    /** Restores the clean default used when Forge supplied an empty capability compound. */
    private void resetToUnbound() {
        persistentDeviceUUID = UUID.randomUUID();
        frequencyReference = null;
        enabledMode = null;
        clearModeWhenDrained = false;
        state = State.UNBOUND;
        configurationRevision = 0;
        managementPaused = false;
        leases = new LinkedHashMap<>();
        operationTokens = new LinkedHashMap<>();
        outputBuffers = new LinkedHashMap<>();
        persistedCompletedOperations.clear();
        persistedTransferReceipts.clear();
        activitySnapshots.clear();
        dataError = null;
        recoveryState = RecoveryState.NONE;
        quarantinedData = null;
        pendingLegacyOutputRecovery = null;
        pendingRuntimeOutputRecovery = false;
    }

    private ParsedState parse(NBTTagCompound data, boolean allowLegacyOutputCandidate) {
        if (data.getInteger(SCHEMA) != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported QIO automation host schema " + data.getInteger(SCHEMA));
        }
        if (!data.hasKey(DEVICE_UUID, NBT.TAG_STRING) ||
              !data.hasKey(STATE, NBT.TAG_STRING) ||
              !data.hasKey(CONFIGURATION_REVISION, NBT.TAG_LONG) ||
              !data.hasKey(MANAGEMENT_PAUSED, NBT.TAG_BYTE) ||
              !data.hasKey(LEASES, NBT.TAG_LIST) ||
              !data.hasKey(TOKENS, NBT.TAG_LIST) ||
              !data.hasKey(OUTPUT_BUFFERS, NBT.TAG_LIST)) {
            throw new IllegalArgumentException(
                  "QIO automation host is missing current-schema state");
        }
        UUID deviceUUID = parseUUID(data.getString(DEVICE_UUID), DEVICE_UUID);
        QIOFrequencyReference frequency = data.hasKey(FREQUENCY, NBT.TAG_COMPOUND) ?
              QIOFrequencyReference.read(data.getCompoundTag(FREQUENCY)) : null;
        QIOAutomationMode mode = data.hasKey(ENABLED_MODE, NBT.TAG_STRING) ?
              QIOAutomationMode.valueOf(data.getString(ENABLED_MODE)) : null;
        State parsedState = State.valueOf(data.getString(STATE));
        boolean parsedClearModeWhenDrained = data.getBoolean(
              CLEAR_MODE_WHEN_DRAINED);
        long revision = data.getLong(CONFIGURATION_REVISION);
        if (revision < 0 || frequency != null && mode == null) {
            throw new IllegalArgumentException("Invalid automation binding or configuration revision");
        }
        boolean detachedDrain = frequency == null && parsedState == State.DRAINING_CHANGE &&
              parsedClearModeWhenDrained && mode != null;
        if (frequency == null && parsedState != State.UNBOUND &&
            parsedState != State.IDENTITY_CONFLICT && parsedState != State.DATA_ERROR &&
            !detachedDrain) {
            throw new IllegalArgumentException("An unbound automation host has an invalid state");
        }
        if (frequency != null && mode != null && parsedState == State.UNBOUND) {
            throw new IllegalArgumentException("A bound automation host cannot be unbound");
        }
        if (parsedClearModeWhenDrained !=
              (parsedState == State.DRAINING_CHANGE) ||
            parsedClearModeWhenDrained && mode == null) {
            throw new IllegalArgumentException("Invalid pending automation mode removal");
        }

        NBTTagList leaseList = data.getTagList(LEASES, NBT.TAG_COMPOUND);
        NBTTagList tokenList = data.getTagList(TOKENS, NBT.TAG_COMPOUND);
        NBTTagList outputBufferList = data.getTagList(OUTPUT_BUFFERS, NBT.TAG_COMPOUND);
        String parsedError = data.hasKey(RECOVERY_DIAGNOSTIC, NBT.TAG_STRING) ?
              data.getString(RECOVERY_DIAGNOSTIC) :
              data.hasKey(DATA_ERROR, NBT.TAG_STRING) ? data.getString(DATA_ERROR) : null;
        RecoveryState parsedRecoveryState = data.hasKey(RECOVERY_STATE, NBT.TAG_STRING) ?
              RecoveryState.valueOf(data.getString(RECOVERY_STATE)) :
              parsedState == State.DATA_ERROR || parsedError != null ?
                    RecoveryState.QUARANTINED : RecoveryState.NONE;
        if (leaseList.tagCount() > MAX_ACTIVE_LEASES || tokenList.tagCount() > MAX_OPERATION_TOKENS ||
            outputBufferList.tagCount() > MAX_OUTPUT_BUFFERS) {
            throw new IllegalArgumentException("Automation host contains too many operations");
        }
        Map<UUID, MachineOperationLease> parsedLeases = new LinkedHashMap<>();
        for (int index = 0; index < leaseList.tagCount(); index++) {
            MachineOperationLease lease = MachineOperationLease.read(leaseList.getCompoundTagAt(index));
            if (parsedLeases.put(lease.leaseId(), lease) != null) {
                throw new IllegalArgumentException("Duplicate machine lease id");
            }
        }
        Map<UUID, MachineOperationToken> parsedTokens = new LinkedHashMap<>();
        for (int index = 0; index < tokenList.tagCount(); index++) {
            MachineOperationToken token = MachineOperationToken.read(tokenList.getCompoundTagAt(index));
            if (parsedTokens.put(token.operationId(), token) != null) {
                throw new IllegalArgumentException("Duplicate machine operation id");
            }
            MachineOperationLease lease = parsedLeases.get(token.leaseId());
            if (lease == null || !lease.ownerOperationId().equals(token.operationId()) || lease.laneId() != token.laneId()) {
                throw new IllegalArgumentException("Machine operation token does not match its lease");
            }
            if (token.state() == MachineOperationToken.State.COMPLETED &&
                  lease.state() != MachineOperationLease.State.COMPLETED &&
                  lease.state() != MachineOperationLease.State.RELEASED) {
                throw new IllegalArgumentException("Completed machine operation has an active lease");
            }
        }
        Map<UUID, QIOOutputBufferEntry> parsedOutputBuffers = new LinkedHashMap<>();
        boolean legacyOutputRecoveryCandidate = false;
        for (int index = 0; index < outputBufferList.tagCount(); index++) {
            QIOOutputBufferEntry entry = QIOOutputBufferEntry.read(outputBufferList.getCompoundTagAt(index));
            if (parsedOutputBuffers.put(entry.bufferId(), entry) != null) {
                throw new IllegalArgumentException("Duplicate QIO output buffer id");
            }
            MachineOperationToken token = parsedTokens.get(entry.operationId());
            MachineOperationLease lease = parsedLeases.get(entry.leaseId());
            if (token == null || lease == null || token.kind() != MachineOperationToken.Kind.OUTPUT_DRAIN ||
                !token.leaseId().equals(entry.leaseId()) ||
                !token.leaseId().equals(lease.leaseId()) ||
                token.state() != MachineOperationToken.State.COLLECTING ||
                lease.state() != MachineOperationLease.State.COLLECTING) {
                if (!allowLegacyOutputCandidate || legacyOutputRecoveryCandidate ||
                      !isLegacyOutputRecoveryStructure(entry, token, lease, parsedState,
                            frequency, mode, parsedClearModeWhenDrained, parsedError)) {
                    throw new IllegalArgumentException("QIO output buffer does not match a collecting operation");
                }
                legacyOutputRecoveryCandidate = true;
            }
        }
        NBTTagCompound parsedQuarantine = data.hasKey(QUARANTINED_DATA, NBT.TAG_COMPOUND) ?
              data.getCompoundTag(QUARANTINED_DATA).copy() : null;
        if (parsedState == State.DATA_ERROR && (parsedError == null || parsedError.isEmpty())) {
            throw new IllegalArgumentException("Data-error host is missing its diagnostic");
        }
        if (parsedRecoveryState == RecoveryState.QUARANTINED &&
              (parsedError == null || parsedError.isEmpty())) {
            throw new IllegalArgumentException("Quarantined automation recovery is missing its diagnostic");
        }
        if (parsedRecoveryState != RecoveryState.QUARANTINED &&
              (parsedError != null || parsedQuarantine != null)) {
            throw new IllegalArgumentException(
                  "Automation recovery diagnostic is not quarantined");
        }
        if (parsedRecoveryState == RecoveryState.RETRY_PENDING && parsedQuarantine != null) {
            throw new IllegalArgumentException(
                  "Retryable automation recovery cannot contain quarantined data");
        }
        // Every live binding is re-authorized after load before it can access QIO again.
        State restoredState = parsedState == State.ACTIVE || parsedState == State.DATA_ERROR ?
              (frequency == null ? State.UNBOUND : State.WAITING_ACCESS) : parsedState;
        return new ParsedState(deviceUUID, frequency, mode, parsedClearModeWhenDrained,
              restoredState, revision, parsedLeases, parsedTokens,
              parsedOutputBuffers, parsedRecoveryState, parsedError, parsedQuarantine,
              legacyOutputRecoveryCandidate);
    }

    private static boolean isLegacyOutputRecoveryStructure(QIOOutputBufferEntry entry,
          @Nullable MachineOperationToken token, @Nullable MachineOperationLease lease,
          State parsedState, @Nullable QIOFrequencyReference frequency,
          @Nullable QIOAutomationMode mode, boolean clearModeWhenDrained,
          @Nullable String parsedError) {
        boolean recognizedState = parsedState == State.ACTIVE || parsedState == State.WAITING_ACCESS ||
              parsedState == State.DATA_ERROR && parsedError != null &&
                    (parsedError.contains("Automatic output extraction") ||
                          parsedError.startsWith(OUTPUT_QUARANTINE_PREFIX));
        return token != null && lease != null && frequency != null &&
              mode == QIOAutomationMode.OUTPUT_ONLY && !clearModeWhenDrained &&
              recognizedState &&
              entry.phase() == QIOOutputBufferEntry.Phase.PREPARED && entry.resource() == null &&
              entry.amount() == 0 && entry.qioTransferId() == null &&
              entry.qioRequestedAmount() == 0 &&
              token.kind() == MachineOperationToken.Kind.OUTPUT_DRAIN &&
              token.state() == MachineOperationToken.State.CONTAMINATED &&
              token.transferReceipts().isEmpty() &&
              lease.mode() == MachineOperationLease.Mode.OUTPUT_DRAIN &&
              lease.state() == MachineOperationLease.State.CONTAMINATED &&
              lease.contaminationReason() != null &&
              lease.contaminationReason().contains("Automatic output extraction") &&
              token.leaseId().equals(lease.leaseId()) &&
              lease.ownerOperationId().equals(token.operationId()) &&
              entry.operationId().equals(token.operationId()) &&
              entry.leaseId().equals(lease.leaseId()) &&
              lease.baselines().equals(Collections.singletonList(entry.baseline()));
    }

    private static boolean isRuntimeOutputRecoveryCandidate(ParsedState parsed) {
        if (parsed.recoveryState == RecoveryState.NONE || parsed.dataError == null ||
              !parsed.dataError.startsWith(OUTPUT_QUARANTINE_PREFIX) ||
              parsed.outputBuffers.size() != 1) {
            return false;
        }
        QIOOutputBufferEntry entry = parsed.outputBuffers.values().iterator().next();
        MachineOperationToken token = parsed.tokens.get(entry.operationId());
        MachineOperationLease lease = parsed.leases.get(entry.leaseId());
        return entry.phase() == QIOOutputBufferEntry.Phase.PREPARED && entry.resource() == null &&
              entry.amount() == 0 && entry.qioTransferId() == null &&
              entry.qioRequestedAmount() == 0 && token != null && lease != null &&
              token.kind() == MachineOperationToken.Kind.OUTPUT_DRAIN &&
              token.state() == MachineOperationToken.State.COLLECTING &&
              token.transferReceipts().isEmpty() &&
              lease.mode() == MachineOperationLease.Mode.OUTPUT_DRAIN &&
              lease.state() == MachineOperationLease.State.COLLECTING &&
              token.leaseId().equals(lease.leaseId()) &&
              lease.ownerOperationId().equals(token.operationId()) &&
              lease.baselines().equals(Collections.singletonList(entry.baseline()));
    }

    enum DeferredOutputRecoveryResult {
        NONE,
        RETRY_LATER,
        RECOVERED,
        BLOCKED
    }

    /** 返回已知的自动输出隔离是否正在等待严格重试。 */
    public boolean hasDeferredOutputRecovery() {
        return pendingLegacyOutputRecovery != null || pendingRuntimeOutputRecovery;
    }

    /**
     * 判断重新安装同一种自动化升级是否可以安全重新启用主机。
     * 原始隔离数据仍会保留，但重新安装允许重新启动后台解析/对账流程。
     *
     * @param mode 要重新安装的自动化模式
     * @return 可以重新启用时返回 true
     */
    public boolean canRearmAfterUpgrade(@Nonnull QIOAutomationMode mode) {
        Objects.requireNonNull(mode, "Automation mode cannot be null");
        if ((!hasRecoveryPending() && state != State.DATA_ERROR) || enabledMode != mode) {
            return false;
        }
        if (hasDeferredOutputRecovery()) {
            return true;
        }
        return leases.isEmpty() && operationTokens.isEmpty() && outputBuffers.isEmpty() &&
               persistedCompletedOperations.isEmpty() && persistedTransferReceipts.isEmpty();
    }

    /** 重新启用安全恢复记录，或把已知输出隔离重新加入重试队列。 */
    public boolean rearmAfterUpgrade(@Nonnull QIOAutomationMode mode) {
        if (!canRearmAfterUpgrade(mode)) {
            return false;
        }
        if (hasDeferredOutputRecovery()) {
            QIOAutomationDeviceRegistry.INSTANCE.trackPending(this);
            return true;
        }
        if (quarantinedData != null) {
            // A schema diagnostic with no serialized ownership can be safely dismissed when
            // the player explicitly reinstalls the same upgrade. Raw operation-bearing data
            // remains quarantined and is still routed through the audited recovery tab; never
            // turn an unknown transfer into a silent discard just because an upgrade changed.
            if (!isDiagnosticOnlyQuarantine()) {
                QIOAutomationDeviceRegistry.INSTANCE.trackPending(this);
                return true;
            }
            clearRecoveryPending();
            state = frequencyReference == null ? State.UNBOUND : State.WAITING_ACCESS;
            incrementConfigurationRevision();
            return true;
        }
        dataError = null;
        recoveryState = RecoveryState.NONE;
        state = frequencyReference == null ? State.UNBOUND : State.WAITING_ACCESS;
        incrementConfigurationRevision();
        return true;
    }

    private boolean isDiagnosticOnlyQuarantine() {
        if (quarantinedData == null) {
            return true;
        }
        // Keep this whitelist deliberately narrow. Any unrecognised field may be an
        // ownership record from a newer schema and must remain available for audit.
        java.util.Set<String> allowed = new java.util.HashSet<>(java.util.Arrays.asList(
              SCHEMA, DEVICE_UUID, FREQUENCY, ENABLED_MODE, STATE,
              CONFIGURATION_REVISION, MANAGEMENT_PAUSED, CLEAR_MODE_WHEN_DRAINED,
              DATA_ERROR, RECOVERY_STATE, RECOVERY_DIAGNOSTIC));
        for (String key : quarantinedData.getKeySet()) {
            if (LEASES.equals(key) || TOKENS.equals(key) || OUTPUT_BUFFERS.equals(key)) {
                if (!quarantinedData.hasKey(key, NBT.TAG_LIST) ||
                      quarantinedData.getTagList(key, NBT.TAG_COMPOUND).tagCount() != 0) {
                    return false;
                }
                continue;
            }
            if (!allowed.contains(key)) {
                return false;
            }
        }
        // An empty capability map is not proof that the frequency has no ownership. A
        // schema failure can discard the local token list while the network still has a
        // job assignment, passive operation, configuration exchange, or durable transfer.
        // Only auto-dismiss the diagnostic when the loaded network also has no reference to
        // this device. If the network is not loaded yet, remain conservative and require the
        // audited recovery path.
        if (frequencyReference == null) {
            return true;
        }
        try {
            if (!QIOProcessingNetworkManager.INSTANCE.isLoaded()) {
                return false;
            }
            QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
                  frequencyReference.getFrequencyUUID());
            if (network == null) {
                return true;
            }
            UUID deviceUUID = persistentDeviceUUID;
            String machinePrefix = "machine/" + deviceUUID + '/';
            String bufferPrefix = "output-buffer/" + deviceUUID + '/';
            for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
                if (transfer.getSource().startsWith(machinePrefix) ||
                      transfer.getDestination().startsWith(machinePrefix) ||
                      transfer.getSource().startsWith(bufferPrefix) ||
                      transfer.getDestination().startsWith(bufferPrefix)) {
                    return false;
                }
            }
            for (mekanism.qioprocessing.common.content.transfer.QIOConfigurationExchangeRecord exchange :
                  network.getConfigurationExchanges()) {
                if (deviceUUID.equals(exchange.getDeviceUUID())) {
                    return false;
                }
            }
            for (mekanism.qioprocessing.common.content.passive.QIOPassiveOperation operation :
                  network.getPassiveOperations()) {
                if (deviceUUID.equals(operation.getDeviceUUID())) {
                    return false;
                }
            }
            for (mekanism.qioprocessing.common.content.job.QIOCraftingJob job : network.getJobs()) {
                for (mekanism.qioprocessing.common.content.job.QIOStepRuntime runtime :
                      job.getStepRuntimes().values()) {
                    for (mekanism.qioprocessing.common.content.job.QIOOperationAssignment assignment :
                          runtime.getActiveOperations().values()) {
                        if (deviceUUID.equals(assignment.getDeviceUUID())) {
                            return false;
                        }
                    }
                }
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 网络记录受影响操作后，供恢复界面调用的强制清理入口。 */
    public boolean forceRecoverAfterDataError() {
        if (state != State.DATA_ERROR && !hasRecoveryPending()) {
            return false;
        }
        dataError = null;
        recoveryState = RecoveryState.NONE;
        quarantinedData = null;
        clearModeWhenDrained = false;
        leases.clear();
        operationTokens.clear();
        outputBuffers.clear();
        ordinaryOutputHandoffOperations.clear();
        persistedCompletedOperations.clear();
        persistedTransferReceipts.clear();
        activitySnapshots.clear();
        pendingLegacyOutputRecovery = null;
        pendingRuntimeOutputRecovery = false;
        managementPaused = false;
        state = frequencyReference == null ? State.UNBOUND : State.WAITING_ACCESS;
        incrementConfigurationRevision();
        markDirty();
        return true;
    }

    /** 仅在升级卸载服务完成外部所有权持久化审计后调用。 */
    boolean forceClearAfterUpgradeRemoval() {
        clearAutomationStateAfterUpgradeRemoval();
        markDirty();
        return true;
    }

    /** 设备注册表在 TileEntity.readCustomNBT 恢复槽位/储罐后调用。 */
    DeferredOutputRecoveryResult attemptDeferredOutputRecovery() {
        if (!hasDeferredOutputRecovery()) {
            return DeferredOutputRecoveryResult.NONE;
        }
        if (tile == null || !QIOProcessingNetworkManager.INSTANCE.isLoaded()) {
            return DeferredOutputRecoveryResult.RETRY_LATER;
        }
        boolean legacy = pendingLegacyOutputRecovery != null;
        ParsedState candidate = legacy ? pendingLegacyOutputRecovery : null;
        UUID candidateDeviceUUID = legacy ? candidate.deviceUUID : persistentDeviceUUID;
        QIOFrequencyReference candidateFrequency = legacy ? candidate.frequency : frequencyReference;
        QIOAutomationMode candidateMode = legacy ? candidate.enabledMode : enabledMode;
        long candidateRevision = legacy ? candidate.configurationRevision : configurationRevision;
        Map<UUID, MachineOperationLease> candidateLeases = legacy ? candidate.leases : leases;
        Map<UUID, MachineOperationToken> candidateTokens = legacy ? candidate.tokens : operationTokens;
        Map<UUID, QIOOutputBufferEntry> candidateBuffers = legacy ? candidate.outputBuffers : outputBuffers;
        if (candidateFrequency == null || candidateMode != QIOAutomationMode.OUTPUT_ONLY ||
              candidateBuffers.size() != 1) {
            return DeferredOutputRecoveryResult.BLOCKED;
        }
        if (state == State.IDENTITY_CONFLICT) {
            return DeferredOutputRecoveryResult.BLOCKED;
        }
        if (QIOProcessingNetworkManager.INSTANCE.getIsolationStatus(
              candidateFrequency.getFrequencyUUID()) != null) {
            return DeferredOutputRecoveryResult.RETRY_LATER;
        }
        if (!QIOAutomationDeviceRegistry.INSTANCE.isRecoveryIdentityUnique(
              candidateDeviceUUID, this)) {
            return DeferredOutputRecoveryResult.BLOCKED;
        }
        QIOOutputBufferEntry entry = candidateBuffers.values().iterator().next();
        MachineOperationToken token = candidateTokens.get(entry.operationId());
        MachineOperationLease lease = candidateLeases.get(entry.leaseId());
        if (!recoveryOwnershipMatches(entry, token, lease, legacy)) {
            return DeferredOutputRecoveryResult.BLOCKED;
        }
        MachineRecipeProviderRegistry.BoundProvider provider;
        try {
            provider = MachineRecipeProviderRegistry.find(tile);
            if (provider == null) {
                // Provider registration can be rebuilt independently from the tile capability.
                // A missing provider is therefore an availability gap, not proof that the
                // persisted machine ownership is malformed.
                return DeferredOutputRecoveryResult.RETRY_LATER;
            }
            if (!provider.validateQIOConformance(
                  QIOAutomationMode.OUTPUT_ONLY).isConformant()) {
                return DeferredOutputRecoveryResult.BLOCKED;
            }
        } catch (RuntimeException e) {
            // Dynamic provider construction is allowed to be temporarily unavailable during
            // tile load. Keep the deferred record intact and retry on a later tick.
            return DeferredOutputRecoveryResult.RETRY_LATER;
        }
        final List<MachinePort> providerPorts;
        try {
            providerPorts = provider.getPorts();
        } catch (RuntimeException e) {
            return DeferredOutputRecoveryResult.RETRY_LATER;
        }
        MachinePort outputPort = null;
        for (MachinePort port : providerPorts) {
            if (port != null && !port.isConfiguration() && port.role().allowsOutput() &&
                  entry.baseline().portId().equals(port.portId())) {
                if (outputPort != null) {
                    return DeferredOutputRecoveryResult.BLOCKED;
                }
                outputPort = port;
            }
        }
        if (outputPort == null || !entry.baseline().portGroupId().equals(outputPort.portGroupId()) ||
              entry.baseline().kind() != outputPort.kind()) {
            return DeferredOutputRecoveryResult.BLOCKED;
        }
        boolean sourceIntact = entry.baseline().matches(outputPort);
        boolean sourceExtracted = entry.baseline().matchesAfterExtraction(outputPort,
              entry.extraction());
        // A legacy contaminated capability is read before TileEntity custom NBT in Forge
        // 1.12.  Its PREPARED transfer has no ownership receipt, so a post-extraction
        // looking port cannot distinguish a legitimate completed extraction from the
        // temporarily empty inventory seen during that load ordering.  Require the exact
        // saved baseline for this migration.  Runtime quarantine has an independent,
        // already-loaded endpoint observation and may use the post-extraction path below.
        if (legacy && !sourceIntact) {
            return DeferredOutputRecoveryResult.BLOCKED;
        }
        PortableResourceDescriptor resource = describe(entry.extraction());
        if (resource == null) {
            return DeferredOutputRecoveryResult.BLOCKED;
        }
        QIOProcessingNetworkData network;
        try {
            network = QIOProcessingNetworkManager.INSTANCE.get(
                  candidateFrequency.getFrequencyUUID());
            if (network == null) {
                return DeferredOutputRecoveryResult.RETRY_LATER;
            }
        } catch (RuntimeException ignored) {
            return DeferredOutputRecoveryResult.RETRY_LATER;
        }
        QIODurableTransferRecord transfer = network.getDurableTransfer(entry.bufferId());
        int ownedTransfers = 0;
        for (QIODurableTransferRecord possible : network.getDurableTransfers()) {
            if (entry.operationId().equals(possible.getOwnerOperationId())) {
                ownedTransfers++;
                if (!entry.bufferId().equals(possible.getTransferId())) {
                    return DeferredOutputRecoveryResult.BLOCKED;
                }
            }
        }
        boolean externalPreparedHandoff = false;
        if (!legacy && !sourceIntact && token.transferReceipts().isEmpty() &&
              (transfer == null || transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) &&
              (sourceExtracted || isOrdinaryOutputHandoffAllowed(entry.operationId()))) {
            // The ordinary ejector is allowed to run while a runtime output quarantine is
            // active.  An exact post-extraction baseline, or a mutation explicitly marked by
            // pauseAutomaticOutputCollection (needed for partial fluid/gas handoffs), proves
            // that this PREPARED intent was yielded externally.  Unmarked arbitrary mutations
            // remain blocked for manual audit; a debited/credited phase stays owned by QIO.
            if (transfer != null && (ownedTransfers != 1 ||
                  !automaticOutputTransferMatches(transfer, candidateDeviceUUID, entry, resource,
                        false, true, false, candidateRevision))) {
                return DeferredOutputRecoveryResult.BLOCKED;
            }
            try {
                if (transfer != null) {
                    network.discardPreparedGenericTransfer(transfer.getTransferId());
                    QIOProcessingNetworkManager.INSTANCE.checkpointNetwork(
                          candidateFrequency.getFrequencyUUID());
                }
                if (!discardPreparedOutputAfterExternalHandoff(entry.operationId(),
                      entry.bufferId())) {
                    return DeferredOutputRecoveryResult.BLOCKED;
                }
                externalPreparedHandoff = true;
            } catch (IOException | RuntimeException e) {
                // Keep the quarantine and retry when the network checkpoint becomes writable.
                return DeferredOutputRecoveryResult.RETRY_LATER;
            }
        }
        if (!sourceIntact && !sourceExtracted) {
            return DeferredOutputRecoveryResult.BLOCKED;
        }
        if (!externalPreparedHandoff) {
            if (transfer == null) {
                if (!sourceIntact || ownedTransfers != 0) {
                    return DeferredOutputRecoveryResult.BLOCKED;
                }
            } else if (ownedTransfers != 1 || !automaticOutputTransferMatches(transfer,
                  candidateDeviceUUID, entry, resource, legacy, sourceIntact, sourceExtracted,
                  candidateRevision)) {
                return DeferredOutputRecoveryResult.BLOCKED;
            }
        }

        if (legacy) {
            try {
                Map<UUID, MachineOperationToken> recoveredTokens = new LinkedHashMap<>(candidate.tokens);
                Map<UUID, MachineOperationLease> recoveredLeases = new LinkedHashMap<>(candidate.leases);
                recoveredTokens.put(token.operationId(),
                      token.recoverCollectingAfterOutputRollback());
                recoveredLeases.put(lease.leaseId(),
                      lease.recoverCollectingAfterOutputRollback());
                persistentDeviceUUID = candidate.deviceUUID;
                frequencyReference = candidate.frequency;
                enabledMode = candidate.enabledMode;
                clearModeWhenDrained = false;
                configurationRevision = candidate.configurationRevision == Long.MAX_VALUE ?
                      Long.MAX_VALUE : candidate.configurationRevision + 1;
                managementPaused = false;
                leases = recoveredLeases;
                operationTokens = recoveredTokens;
                outputBuffers = new LinkedHashMap<>(candidate.outputBuffers);
                persistedCompletedOperations.clear();
                persistedTransferReceipts.clear();
            } catch (RuntimeException ignored) {
                return DeferredOutputRecoveryResult.BLOCKED;
            }
        } else {
            if (configurationRevision != Long.MAX_VALUE) {
                configurationRevision++;
            }
        }
        state = State.WAITING_ACCESS;
        dataError = null;
        recoveryState = RecoveryState.NONE;
        quarantinedData = null;
        pendingLegacyOutputRecovery = null;
        pendingRuntimeOutputRecovery = false;
        activitySnapshots.clear();
        markDirty();
        return DeferredOutputRecoveryResult.RECOVERED;
    }

    private static boolean recoveryOwnershipMatches(QIOOutputBufferEntry entry,
          @Nullable MachineOperationToken token, @Nullable MachineOperationLease lease,
          boolean legacy) {
        if (entry.phase() != QIOOutputBufferEntry.Phase.PREPARED || entry.resource() != null ||
              entry.amount() != 0 || entry.qioTransferId() != null ||
              entry.qioRequestedAmount() != 0 || token == null || lease == null ||
              token.kind() != MachineOperationToken.Kind.OUTPUT_DRAIN ||
              !token.transferReceipts().isEmpty() ||
              lease.mode() != MachineOperationLease.Mode.OUTPUT_DRAIN ||
              !token.leaseId().equals(entry.leaseId()) ||
              !token.leaseId().equals(lease.leaseId()) ||
              !lease.ownerOperationId().equals(entry.operationId()) ||
              !lease.baselines().equals(Collections.singletonList(entry.baseline()))) {
            return false;
        }
        if (legacy) {
            return token.state() == MachineOperationToken.State.CONTAMINATED &&
                  lease.state() == MachineOperationLease.State.CONTAMINATED &&
                  lease.contaminationReason() != null &&
                  lease.contaminationReason().contains("Automatic output extraction");
        }
        return token.state() == MachineOperationToken.State.COLLECTING &&
              lease.state() == MachineOperationLease.State.COLLECTING;
    }

    private static boolean automaticOutputTransferMatches(QIODurableTransferRecord transfer,
          UUID deviceUUID, QIOOutputBufferEntry entry, PortableResourceDescriptor resource,
          boolean legacy, boolean sourceIntact, boolean sourceExtracted, long expectedRevision) {
        if (!transfer.getTransferId().equals(entry.bufferId()) ||
              transfer.getType() != QIODurableTransferRecord.Type.MACHINE_TO_JOB ||
              transfer.getOwnerJobId() != null ||
              !entry.operationId().equals(transfer.getOwnerOperationId()) ||
              transfer.getPlanRevision() != 0 ||
              !transfer.getNodeId().equals("output/" + entry.baseline().portId()) ||
              !entry.leaseId().equals(transfer.getLeaseId()) ||
              !transfer.getSource().equals("machine/" + deviceUUID + '/' +
                    entry.baseline().portGroupId()) ||
              !transfer.getDestination().equals("output-buffer/" + deviceUUID + '/' +
                    entry.bufferId()) ||
              !transfer.getResources().equals(Collections.singletonMap(resource,
                    entry.extraction().amount())) ||
              !transfer.getQIOResourceUUIDs().isEmpty() || !transfer.getQIOBaselines().isEmpty() ||
              !transfer.getMachineBaselines().equals(Collections.singletonList(entry.baseline())) ||
              transfer.getExpectedContentsRevision() != -1 ||
              transfer.getExpectedClaimRevision() != -1 ||
              transfer.getCompensationTransferId() != null) {
            return false;
        }

        String sourceReceipt = "machine/" + deviceUUID + "/lease/" + entry.leaseId();
        String destinationReceipt = "output-buffer/" + deviceUUID + '/' + entry.bufferId();
        if (legacy) {
            // Legacy capability data was read before the machine inventory.  It is only
            // safe to migrate while the original machine-side baseline is still present;
            // there is no durable receipt proving that an extraction happened.
            return sourceIntact && transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                  transfer.getSourceReceipt() == null && transfer.getDestinationReceipt() == null &&
                  transfer.getSourceStateRevision() == -1;
        }
        if (sourceIntact) {
            // A transfer that claims any source-side progress while the machine is still at
            // its baseline cannot be replayed safely.  Re-open only a completely prepared
            // handoff; the normal controller will perform the extraction once.
            return transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                  transfer.getSourceReceipt() == null && transfer.getDestinationReceipt() == null &&
                  transfer.getSourceStateRevision() == -1;
        }
        if (!sourceExtracted) {
            return false;
        }
        // Runtime quarantine may be observed after the machine changed but before the
        // capability buffer was advanced.  The exact post-extraction baseline and the
        // receipt chain below prove ownership, so resuming is idempotent.
        switch (transfer.getPhase()) {
            case PREPARED:
                return transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                      transfer.getSourceReceipt() == null && transfer.getDestinationReceipt() == null &&
                      transfer.getSourceStateRevision() == -1;
            case SOURCE_DEBITED:
                return transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                      sourceReceipt.equals(transfer.getSourceReceipt()) &&
                      transfer.getDestinationReceipt() == null &&
                      transfer.getSourceStateRevision() == expectedRevision;
            case DESTINATION_CREDITED:
                return transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE &&
                      sourceReceipt.equals(transfer.getSourceReceipt()) &&
                      destinationReceipt.equals(transfer.getDestinationReceipt()) &&
                      transfer.getSourceStateRevision() == expectedRevision;
            case COMMITTED:
                return transfer.getResolution() == QIODurableTransferRecord.Resolution.FORWARD_COMMITTED &&
                      sourceReceipt.equals(transfer.getSourceReceipt()) &&
                      destinationReceipt.equals(transfer.getDestinationReceipt()) &&
                      transfer.getSourceStateRevision() == expectedRevision;
            case ROLLBACK_REQUIRED:
                return false;
            default:
                return false;
        }
    }

    @Nullable
    private static PortableResourceDescriptor describe(MachineResourceStack stack) {
        try {
            return switch (stack.kind()) {
                case ITEM -> PortableResourceDescriptor.item(stack.itemStack());
                case FLUID -> PortableResourceDescriptor.fluid(stack.fluidStack());
                case GAS -> PortableResourceDescriptor.gas(stack.gasStack());
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean hasUnsettledOperations() {
        if (!outputBuffers.isEmpty()) {
            return true;
        }
        for (MachineOperationLease lease : leases.values()) {
            if (lease.state() != MachineOperationLease.State.RELEASED) {
                return true;
            }
        }
        for (MachineOperationToken token : operationTokens.values()) {
            if (!token.state().isTerminal()) {
                return true;
            }
        }
        return false;
    }

    /** Includes terminal ownership records which still await persistence/pruning. */
    private boolean hasRemovalOwnedState() {
        return hasResourceOwnershipState() ||
              quarantinedData != null || pendingLegacyOutputRecovery != null ||
              pendingRuntimeOutputRecovery;
    }

    private boolean hasResourceOwnershipState() {
        return !leases.isEmpty() || !operationTokens.isEmpty() || !outputBuffers.isEmpty() ||
              !persistedCompletedOperations.isEmpty() || !persistedTransferReceipts.isEmpty();
    }

    /**
     * Applies a player-requested mode/binding change without treating a diagnostic-only
     * recovery record as a permanent lockout. Structured operation ownership is still
     * protected; the explicit binding/removal services audit it before changing the
     * endpoint. A quarantine containing only malformed raw NBT is safe to discard here.
     */
    private boolean prepareForConfigurationMutation() {
        // Raw quarantined NBT may contain ownership records that could not be parsed.
        // Treat it as owned until the audited force-recovery path has accounted for it;
        // otherwise a mode callback could silently discard a job after a malformed load.
        boolean ownedRecovery = hasResourceOwnershipState() || hasDeferredOutputRecovery() ||
              quarantinedData != null;
        if (ownedRecovery && hasBlockingRecovery()) {
            if (!hasResourceOwnershipState() && quarantinedData != null &&
                  isDiagnosticOnlyQuarantine()) {
                clearRecoveryPending();
            } else {
                return false;
            }
        }
        if (!ownedRecovery && hasRecoveryPending()) {
            clearRecoveryPending();
        }
        return true;
    }

    /**
     * Unknown/raw ownership blocks new work. A known output quarantine keeps its
     * collecting lease, so the normal port-group conflict rules isolate only that
     * output while unrelated lanes remain available.
     */
    private boolean hasBlockingRecovery() {
        if (recoveryState != RecoveryState.QUARANTINED) {
            return false;
        }
        if (isProcessingOutputCollectionPaused()) {
            return true;
        }
        if (quarantinedData != null) {
            return true;
        }
        // A diagnostic without structured ownership is safe to retry or replace.  This is the
        // normal equivalent of an AE2 endpoint temporarily being unavailable: it must not turn
        // an otherwise idle machine into a permanent DATA_ERROR gate.
        if (!hasResourceOwnershipState() && !hasDeferredOutputRecovery()) {
            return false;
        }
        // A legacy candidate keeps its parsed ownership in the deferred object rather than in
        // the live maps until the tile's custom NBT has loaded.  Do not admit a new operation in
        // that window: the post-load recovery pass must establish the original baseline first.
        if (pendingLegacyOutputRecovery != null) {
            return true;
        }
        for (MachineOperationToken token : operationTokens.values()) {
            MachineOperationLease lease = leases.get(token.leaseId());
            if (lease == null || !lease.ownerOperationId().equals(token.operationId())) {
                return true;
            }
        }
        // Structured ownership is isolated by the existing port-group/lease rules.  Do not
        // block unrelated lanes merely because one operation is waiting for a retry or a
        // reconciliation pass.  An operation whose token/lease relationship is malformed was
        // rejected by the loop above and remains fail-closed.
        return false;
    }

    private boolean hasBufferedCollectingOwnership() {
        if (outputBuffers.isEmpty()) {
            return false;
        }
        for (QIOOutputBufferEntry entry : outputBuffers.values()) {
            MachineOperationToken token = operationTokens.get(entry.operationId());
            MachineOperationLease lease = leases.get(entry.leaseId());
            if (token == null || lease == null ||
                token.state() != MachineOperationToken.State.COLLECTING ||
                lease.state() != MachineOperationLease.State.COLLECTING ||
                !token.leaseId().equals(entry.leaseId()) ||
                !lease.ownerOperationId().equals(entry.operationId())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasOnlyStructuredContaminatedOperations() {
        if (operationTokens.isEmpty() || !outputBuffers.isEmpty()) {
            return false;
        }
        boolean contaminated = false;
        for (MachineOperationToken token : operationTokens.values()) {
            MachineOperationLease lease = leases.get(token.leaseId());
            if (lease == null || !lease.ownerOperationId().equals(token.operationId()) ||
                (token.state() == MachineOperationToken.State.CONTAMINATED) !=
                      (lease.state() == MachineOperationLease.State.CONTAMINATED)) {
                return false;
            }
            contaminated |= token.state() == MachineOperationToken.State.CONTAMINATED;
        }
        return contaminated && leases.size() == operationTokens.size();
    }

    private boolean isKnownOutputQuarantine() {
        return recoveryState == RecoveryState.QUARANTINED && dataError != null &&
              dataError.startsWith(OUTPUT_QUARANTINE_PREFIX);
    }

    private boolean isKnownOperationQuarantine() {
        return recoveryState == RecoveryState.QUARANTINED && dataError != null &&
              dataError.startsWith(OPERATION_QUARANTINE_PREFIX);
    }

    private void clearKnownOperationQuarantineIfSettled() {
        if (!isKnownOperationQuarantine()) {
            return;
        }
        boolean contaminated = operationTokens.values().stream().anyMatch(token ->
              token.state() == MachineOperationToken.State.CONTAMINATED);
        if (!contaminated) {
            clearRecoveryPending();
        }
    }

    /** Clears a runtime diagnostic after its last unowned operation is removed. */
    private void clearKnownQuarantineIfUnowned() {
        if ((!isKnownOutputQuarantine() && !isKnownOperationQuarantine()) ||
              quarantinedData != null ||
              !leases.isEmpty() || !operationTokens.isEmpty() || !outputBuffers.isEmpty() ||
              !persistedCompletedOperations.isEmpty() || !persistedTransferReceipts.isEmpty()) {
            return;
        }
        clearRecoveryPending();
    }

    private void completePendingModeClearIfReady() {
        if (!clearModeWhenDrained || hasRemovalOwnedState()) {
            return;
        }
        clearAutomationStateAfterUpgradeRemoval();
    }

    private void clearAutomationStateAfterUpgradeRemoval() {
        clearModeWhenDrained = false;
        enabledMode = null;
        frequencyReference = null;
        state = State.UNBOUND;
        managementPaused = false;
        leases.clear();
        operationTokens.clear();
        outputBuffers.clear();
        ordinaryOutputHandoffOperations.clear();
        persistedCompletedOperations.clear();
        persistedTransferReceipts.clear();
        activitySnapshots.clear();
        dataError = null;
        recoveryState = RecoveryState.NONE;
        quarantinedData = null;
        pendingLegacyOutputRecovery = null;
        pendingRuntimeOutputRecovery = false;
        incrementConfigurationRevision();
    }

    private boolean modeMatchesEnabledAutomation(MachineOperationLease.Mode mode) {
        return enabledMode == QIOAutomationMode.OUTPUT_ONLY ? mode == MachineOperationLease.Mode.OUTPUT_DRAIN :
              enabledMode != null && mode == MachineOperationLease.Mode.PROCESSING_EXCLUSIVE;
    }

    private boolean tokenMatchesLeaseMode(MachineOperationToken token, MachineOperationLease lease) {
        if (lease.mode() == MachineOperationLease.Mode.OUTPUT_DRAIN) {
            return token.kind() == MachineOperationToken.Kind.OUTPUT_DRAIN;
        }
        return enabledMode == QIOAutomationMode.SCHEDULED && token.kind() == MachineOperationToken.Kind.JOB ||
              enabledMode == QIOAutomationMode.PASSIVE && token.kind() == MachineOperationToken.Kind.PASSIVE;
    }

    private static boolean sharesPortGroup(Set<String> occupied, Collection<MachinePortBaseline> requested) {
        for (MachinePortBaseline baseline : requested) {
            if (occupied.contains(baseline.portGroupId())) {
                return true;
            }
        }
        return false;
    }

    private List<MachineOperationLease> sortedLeases() {
        List<MachineOperationLease> values = new ArrayList<>(leases.values());
        values.sort((first, second) -> first.leaseId().compareTo(second.leaseId()));
        return values;
    }

    private List<MachineOperationToken> sortedTokens() {
        List<MachineOperationToken> values = new ArrayList<>(operationTokens.values());
        values.sort((first, second) -> first.operationId().compareTo(second.operationId()));
        return values;
    }

    private List<QIOOutputBufferEntry> sortedOutputBuffers() {
        List<QIOOutputBufferEntry> values = new ArrayList<>(outputBuffers.values());
        values.sort((first, second) -> first.bufferId().compareTo(second.bufferId()));
        return values;
    }

    private void incrementConfigurationRevision() {
        if (configurationRevision == Long.MAX_VALUE) {
            // A revision is an optimistic-concurrency marker, not an operation counter. Once
            // it reaches its representable maximum, saturating it is safer than creating a new
            // recovery quarantine while clearing an upgrade or a binding.
            markDirty();
            return;
        }
        configurationRevision++;
        markDirty();
        if (tile != null) {
            QIOAutomationDeviceRegistry.INSTANCE.trackPending(this);
        }
    }

    /**
     * 记录可恢复的事务问题，不让整个机器端点下线。
     * 相关租约/缓冲仍是权威状态，由设备注册表继续重试；这不是 NBT 损坏路径。
     *
     * @param reason 面向诊断界面的原因文本
     */
    void markRecoveryPending(@Nonnull String reason) {
        String diagnostic = boundedDiagnostic(reason);
        boolean changed = recoveryState != RecoveryState.QUARANTINED ||
              !Objects.equals(dataError, diagnostic);
        dataError = diagnostic;
        recoveryState = RecoveryState.QUARANTINED;
        if (state == State.DATA_ERROR) {
            state = frequencyReference == null ? State.UNBOUND : State.WAITING_ACCESS;
        }
        if (changed) {
            markDirty();
        }
        if (tile != null) {
            QIOAutomationDeviceRegistry.INSTANCE.trackPending(this);
        }
    }

    /** 记录可重试的 Provider/存储不可用，不把主机暴露为错误状态。 */
    /** 在成功观测到端点后清除可重试标记。 */
    @Override
    public void markRetryPending() {
        if (recoveryState == RecoveryState.NONE) {
            recoveryState = RecoveryState.RETRY_PENDING;
            markDirty();
        }
    }

    @Override
    public void clearRetryPending() {
        if (recoveryState == RecoveryState.RETRY_PENDING) {
            recoveryState = RecoveryState.NONE;
            markDirty();
        }
    }

    /** 在确认恢复后清除内部恢复标记，不触碰外部所有权。 */
    void clearRecoveryPending() {
        recoveryState = RecoveryState.NONE;
        dataError = null;
        pendingLegacyOutputRecovery = null;
        pendingRuntimeOutputRecovery = false;
        quarantinedData = null;
        if (state == State.DATA_ERROR) {
            state = frequencyReference == null ? State.UNBOUND : State.WAITING_ACCESS;
        }
        markDirty();
    }

    /** 将无法确认的诊断写入非阻塞恢复隔离，并保留原始数据供审计。 */
    void enterDataError(String reason) {
        dataError = boundedDiagnostic(reason);
        recoveryState = RecoveryState.QUARANTINED;
        // DATA_ERROR remains readable for old NBT/API compatibility, but a newly
        // detected corruption is represented as an online lifecycle plus a
        // quarantined recovery record. It must not permanently disable binding or
        // upgrade management.
        if (state == State.DATA_ERROR) {
            state = frequencyReference == null ? State.UNBOUND : State.WAITING_ACCESS;
        }
        markDirty();
    }

    private static String boundedDiagnostic(@Nullable String reason) {
        return reason == null || reason.isEmpty() ? "unknown automation host data error" :
              reason.substring(0, Math.min(512, reason.length()));
    }

    private void markDirty() {
        if (clearModeWhenDrained && !hasRemovalOwnedState()) {
            completePendingModeClearIfReady();
            return;
        }
        if (tile != null) {
            tile.markDirty();
        }
    }

    private void wakeExecution() {
        if (frequencyReference != null) {
            QIOProcessingExecutionService.INSTANCE.wakeDevice(
                  frequencyReference.getFrequencyUUID(), persistentDeviceUUID);
        }
    }

    private static UUID parseUUID(String value, String key) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("Non-canonical UUID in " + key);
        }
        return parsed;
    }

    private static final class ParsedState {

        private final UUID deviceUUID;
        @Nullable
        private final QIOFrequencyReference frequency;
        @Nullable
        private final QIOAutomationMode enabledMode;
        private final boolean clearModeWhenDrained;
        private final State state;
        private final long configurationRevision;
        private final Map<UUID, MachineOperationLease> leases;
        private final Map<UUID, MachineOperationToken> tokens;
        private final Map<UUID, QIOOutputBufferEntry> outputBuffers;
        private final RecoveryState recoveryState;
        @Nullable
        private final String dataError;
        @Nullable
        private final NBTTagCompound quarantinedData;
        private final boolean legacyOutputRecoveryCandidate;

        private ParsedState(UUID deviceUUID, @Nullable QIOFrequencyReference frequency,
              @Nullable QIOAutomationMode enabledMode, boolean clearModeWhenDrained,
              State state, long configurationRevision,
              Map<UUID, MachineOperationLease> leases, Map<UUID, MachineOperationToken> tokens,
              Map<UUID, QIOOutputBufferEntry> outputBuffers, RecoveryState recoveryState,
              @Nullable String dataError,
              @Nullable NBTTagCompound quarantinedData,
              boolean legacyOutputRecoveryCandidate) {
            this.deviceUUID = deviceUUID;
            this.frequency = frequency;
            this.enabledMode = enabledMode;
            this.clearModeWhenDrained = clearModeWhenDrained;
            this.state = state;
            this.configurationRevision = configurationRevision;
            this.leases = leases;
            this.tokens = tokens;
            this.outputBuffers = outputBuffers;
            this.recoveryState = recoveryState;
            this.dataError = dataError;
            this.quarantinedData = quarantinedData;
            this.legacyOutputRecoveryCandidate = legacyOutputRecoveryCandidate;
        }
    }
}
