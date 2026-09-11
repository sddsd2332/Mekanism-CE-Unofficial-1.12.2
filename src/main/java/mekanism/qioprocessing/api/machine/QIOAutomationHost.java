package mekanism.qioprocessing.api.machine;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 已接入 QIO 自动化系统的机器主机状态契约。
 *
 * <p>该接口同时承担三类职责：保存机器的频率和模式绑定、记录机器操作的持久化所有权，
 * 以及在 Provider 或网络暂时不可用时提供可重试的恢复状态。实现必须保证这些状态可以
 * 跨服务器 tick 和 NBT 保存边界恢复。</p>
 *
 * <p>所有改变状态的方法都在服务端执行；客户端只能读取由容器或终端同步的快照。</p>
 */
public interface QIOAutomationHost extends INBTSerializable<NBTTagCompound> {

    /**
     * 主机的运行生命周期。
     *
     * <p>{@link #DATA_ERROR} 仅为旧存档兼容值。新实现应将它转换为正常生命周期状态，
     * 并通过 {@link RecoveryState} 表示诊断或恢复信息。</p>
     */
    enum State {
        UNBOUND,
        ACTIVE,
        DRAINING_CHANGE,
        WAITING_ACCESS,
        IDENTITY_CONFLICT,
        /**
         * Legacy serialized value. New hosts expose recovery through
         * {@link RecoveryState} and must normalize this value to WAITING_ACCESS or
         * UNBOUND before returning it from the runtime API.
         */
        @Deprecated
        DATA_ERROR;

        /** 返回当前生命周期是否允许创建新的机器操作。 */
        public boolean acceptsNewOperations() {
            return this == ACTIVE;
        }
    }

    /**
     * Internal recovery status for a host-owned transaction. This is deliberately
     * separate from {@link State}: a machine can remain online and usable while one
     * output handoff is being retried or reconciled. Older implementations may return
     * {@link RecoveryState#NONE}; the default methods keep the API source compatible.
     */
    enum RecoveryState {
        NONE,
        RETRY_PENDING,
        QUARANTINED
    }

    /** 返回跨 NBT 保存边界保持不变的机器 UUID。 */
    @Nonnull
    UUID getPersistentDeviceUUID();

    /** 返回当前绑定的 QIO 频率引用；未绑定时返回 null。 */
    @Nullable
    QIOFrequencyReference getFrequencyReference();

    /** 返回当前安装并启用的自动化模式；未选择模式时返回 null。 */
    @Nullable
    QIOAutomationMode getEnabledMode();

    /** 返回主机的生命周期状态。 */
    @Nonnull
    State getState();

    /** 返回当前持久化事务的内部恢复状态。 */
    @Nonnull
    default RecoveryState getRecoveryState() {
        return RecoveryState.NONE;
    }

    /** 返回是否存在等待自动恢复或重试的持久化操作。 */
    default boolean hasRecoveryPending() {
        return getRecoveryState() != RecoveryState.NONE;
    }

    /** 返回用于日志和诊断界面的有限长度说明；该值不能用于路由选择。 */
    @Nullable
    default String getRecoveryDiagnostic() {
        return null;
    }

    /** 记录 Provider 或存储暂时不可用，但不隔离已有资源所有权。 */
    default void markRetryPending() {
    }

    /** 在端点再次成功观测后清除暂时性重试标记。 */
    default void clearRetryPending() {
    }

    /** 返回用于端点快照和并发校验的配置版本号。 */
    long getConfigurationRevision();

    /** 返回管理端是否暂时暂停了该机器。 */
    default boolean isManagementPaused() {
        return false;
    }

    /** 设置管理暂停标志；不支持该能力的旧实现返回 false。 */
    default boolean setManagementPaused(boolean paused) {
        return false;
    }

    /** 应用已由机器升级物品校验过的自动化模式选择。 */
    boolean selectMode(@Nonnull QIOAutomationMode mode);

    /** 在最后一个 QIO 自动化升级被移除后清除已选择的模式。 */
    boolean clearMode();

    /**
     * 清除已选择的模式。
     *
     * @param detachFrequencyImmediately 为 true 时，在已有操作排空前立即解除频率绑定；
     *                                   为 false 时等待所有本地主有操作完成
     * @return 是否接受了清理请求
     */
    default boolean clearMode(boolean detachFrequencyImmediately) {
        return clearMode();
    }

    /** 返回当前所有 lease 的只读视图。 */
    @Nonnull
    Map<UUID, MachineOperationLease> getLeases();

    /** 返回当前所有操作 token 的只读视图。 */
    @Nonnull
    Map<UUID, MachineOperationToken> getOperationTokens();

    /** 返回按机器通道编号索引的活动快照只读视图。 */
    @Nonnull
    Map<Long, MachineActivitySnapshot> getActivitySnapshots();

    /** 返回自动输出中间 buffer 的只读视图。 */
    @Nonnull
    Map<UUID, QIOOutputBufferEntry> getOutputBufferEntries();

    @Nullable
    /**
     * 为一次机器操作申请端口组所有权。
     *
     * @param leaseId 新 lease 的持久化标识
     * @param ownerOperationId 拥有该 lease 的操作标识
     * @param mode 操作类型，例如处理独占或自动输出排空
     * @param laneId 机器 Provider 暴露的通道编号
     * @param createdAt 创建操作时使用的 tick
     * @param baselines 创建 lease 时记录的端口基线
     * @return 成功取得的 lease；冲突、恢复隔离或状态不允许时返回 null
     */
    MachineOperationLease tryAcquireLease(@Nonnull UUID leaseId, @Nonnull UUID ownerOperationId,
          @Nonnull MachineOperationLease.Mode mode, long laneId, long createdAt,
          @Nonnull Collection<MachinePortBaseline> baselines);

    /** 将操作 token 绑定到已经取得的 lease。 */
    boolean attachOperationToken(@Nonnull MachineOperationToken token);

    /**
     * 原子推进操作 token 和 lease 的生命周期。
     *
     * @param operationId 要推进的操作标识
     * @param tokenState token 的目标状态
     * @param leaseState lease 的目标状态
     * @return 状态转换成功时返回 true
     */
    boolean transitionOperation(@Nonnull UUID operationId, @Nonnull MachineOperationToken.State tokenState,
          @Nonnull MachineOperationLease.State leaseState);

    /** 记录一次已写入 durable transfer 的传输回执，保证重启后幂等恢复。 */
    boolean recordTransferReceipt(@Nonnull UUID operationId, @Nonnull UUID transferId);

    /** 仅当包含该回执的端点快照已经写入持久存储后返回 true。 */
    default boolean isTransferReceiptPersisted(@Nonnull UUID operationId,
          @Nonnull UUID transferId) {
        return false;
    }

    /** 在包含该回执的端点快照完成持久化后确认该回执。 */
    default void confirmTransferReceiptPersisted(@Nonnull UUID operationId,
          @Nonnull UUID transferId) {
    }

    /** 在已完成且已释放的操作快照完成持久化后确认该操作。 */
    default void confirmCompletedOperationPersisted(@Nonnull UUID operationId) {
    }

    boolean contaminateLease(@Nonnull UUID leaseId, @Nonnull String reason);

    /**
     * 在外部所有者已经持久化记录失败后释放结构仍然有效的污染操作。
     * 该方法不会自行丢弃输出 buffer，也不会替任何传输操作确认资源已经完成。
     *
     * @param operationId 要释放的污染操作标识
     * @return 操作满足污染状态且成功释放时返回 true
     */
    default boolean releaseContaminatedOperation(@Nonnull UUID operationId) {
        return false;
    }

    /**
     * 放弃一个计划合成操作。
     *
     * <p>该方法不会擅自领取机器输出，也不会退回可能已经进入实体机器的输入资源。</p>
     */
    boolean abandonJobOperation(@Nonnull UUID operationId);

    /** 在尚未附加操作 token 时释放处于 ACQUIRED 状态的 lease。 */
    boolean releaseUnattachedLease(@Nonnull UUID leaseId);

    /** 在所有传输结算完成后删除终止状态的 token 及其 released lease。 */
    boolean forgetSettledOperation(@Nonnull UUID operationId);

    /** 更新用于终端和管理界面显示的机器活动快照。 */
    void updateActivitySnapshot(@Nonnull MachineActivitySnapshot snapshot);
}
