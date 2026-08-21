package mekanism.qioprocessing.common.content.transfer;

import mekanism.api.processing.MachineResourceStack;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** Crash-recoverable ownership ledger for replacing one retained machine configuration port. */
/**
 * QIO 处理模块中的 QIOConfigurationExchangeRecord 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOConfigurationExchangeRecord {

    /**
     * Version three adds an explicit, idempotent return identity for a target template which
     * was debited from QIO but never installed in the machine.  Older records are still
     * readable; their target disposition is conservatively inferred from the saved phase.
     */
    public static final int SCHEMA_VERSION = 3;
    public static final String FORCE_RECOVERY_PENDING_PREFIX =
          "QIO_FORCE_RECOVERY_PENDING_CLAIM: ";

    public enum Phase {
        CLAIM_PREPARED,
        CLAIMED,
        TARGET_DEBIT_PREPARED,
        TARGET_DEBITED,
        OLD_RETURN_PREPARED,
        OLD_EXTRACTED,
        OLD_QIO_CREDITED,
        NEW_INSTALLED,
        COMMITTED,
        CANCELLED,
        CONTAMINATED;

        public boolean isTerminal() {
            return isSettled() || this == CONTAMINATED;
        }

        public boolean isSettled() {
            return this == COMMITTED || this == CANCELLED;
        }
    }

    private final UUID exchangeId;
    private final UUID operationId;
    @Nullable
    private final UUID ownerJobId;
    private final UUID deviceUUID;
    private final UUID leaseId;
    private final String portId;
    private final String portGroupId;
    private final long laneId;
    private final MachineResourceStack target;
    @Nullable
    private final MachineResourceStack original;
    private final UUID claimId;
    private UUID claimRequestId;
    private UUID consumeRequestId;
    private final UUID releaseRequestId;
    private UUID targetDebitTransferId;
    private final UUID targetReturnTransferId;
    private UUID oldQioTransferId;
    private final UUID oldExtractionReceiptId;
    private final UUID newInstallationReceiptId;
    private final UUID targetResourceUUID;
    private Phase phase;
    @Nullable
    private BigInteger targetQioBaseline;
    @Nullable
    private BigInteger oldQioBaseline;
    @Nullable
    private BigInteger targetReturnQioBaseline;
    private boolean targetInstalled;
    private boolean targetReturned;
    /** Phase observed immediately before a new contamination marker was written. */
    @Nullable
    private Phase contaminatedFromPhase;
    @Nullable
    private String diagnostic;
    private long lastRetryContentsRevision = -1;
    private long lastRetryClaimRevision = -1;
    private boolean claimOutstanding;

    /** 创建一个机器端口配置交换记录。 */
    public QIOConfigurationExchangeRecord(@Nonnull UUID exchangeId,
          @Nonnull UUID operationId, @Nullable UUID ownerJobId, @Nonnull UUID deviceUUID,
          @Nonnull UUID leaseId, @Nonnull String portId, @Nonnull String portGroupId,
          long laneId, @Nonnull MachineResourceStack target,
          @Nullable MachineResourceStack original, @Nonnull UUID targetResourceUUID) {
        this(exchangeId, operationId, ownerJobId, deviceUUID, leaseId, portId, portGroupId,
              laneId, target, original, UUID.randomUUID(), UUID.randomUUID(),
              UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
              UUID.randomUUID(), UUID.randomUUID(), targetResourceUUID, Phase.CLAIM_PREPARED,
              null, null, null, false, UUID.randomUUID(), null, false, false, null);
    }

    /** 创建复用旧 QIO 条目的配置交换记录。 */
    @Nonnull
    public static QIOConfigurationExchangeRecord reused(@Nonnull UUID exchangeId,
          @Nonnull UUID operationId, @Nullable UUID ownerJobId, @Nonnull UUID deviceUUID,
          @Nonnull UUID leaseId, @Nonnull String portId, @Nonnull String portGroupId,
          long laneId, @Nonnull MachineResourceStack target,
          @Nonnull MachineResourceStack installed) {
        if (!target.sameResource(installed)) {
            throw new IllegalArgumentException("Reused configuration does not match the route target");
        }
        return new QIOConfigurationExchangeRecord(exchangeId, operationId, ownerJobId,
              deviceUUID, leaseId, portId, portGroupId, laneId, target, installed,
              UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
              UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
              new UUID(0, 0), Phase.COMMITTED, null, null, null, false,
              UUID.randomUUID(), null, true, false, null);
    }

    /** 创建带显式旧传输标识的复用配置交换记录。 */
    @Nonnull
    public static QIOConfigurationExchangeRecord reused(@Nonnull UUID exchangeId,
          @Nonnull UUID operationId, @Nullable UUID ownerJobId, @Nonnull UUID deviceUUID,
          @Nonnull UUID leaseId, @Nonnull String portId, @Nonnull String portGroupId,
          long laneId, @Nonnull MachineResourceStack target) {
        return reused(exchangeId, operationId, ownerJobId, deviceUUID, leaseId, portId,
              portGroupId, laneId, target, target);
    }

    private QIOConfigurationExchangeRecord(UUID exchangeId, UUID operationId,
          @Nullable UUID ownerJobId, UUID deviceUUID, UUID leaseId, String portId,
          String portGroupId, long laneId, MachineResourceStack target,
          @Nullable MachineResourceStack original, UUID claimId, UUID claimRequestId,
          UUID consumeRequestId, UUID releaseRequestId, UUID targetDebitTransferId,
          UUID oldQioTransferId,
          UUID oldExtractionReceiptId, UUID newInstallationReceiptId,
          UUID targetResourceUUID, Phase phase, @Nullable BigInteger targetQioBaseline,
          @Nullable BigInteger oldQioBaseline, @Nullable String diagnostic,
          boolean claimOutstanding, UUID targetReturnTransferId,
          @Nullable BigInteger targetReturnQioBaseline, boolean targetInstalled,
          boolean targetReturned, @Nullable Phase contaminatedFromPhase) {
        this.exchangeId = Objects.requireNonNull(exchangeId, "exchangeId");
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.ownerJobId = ownerJobId;
        this.deviceUUID = Objects.requireNonNull(deviceUUID, "deviceUUID");
        this.leaseId = Objects.requireNonNull(leaseId, "leaseId");
        this.portId = requireId(portId, "portId");
        this.portGroupId = requireId(portGroupId, "portGroupId");
        this.laneId = QIOProcessingNbt.requireNonNegative(laneId, "laneId");
        this.target = requirePortStack(target, this.portId, 1, "target");
        this.original = original == null ? null : requirePortStack(original, this.portId,
              original.amount(), "original");
        if (this.original != null && this.original.sameResource(this.target) &&
              phase != Phase.COMMITTED) {
            throw new IllegalArgumentException("A configuration exchange cannot replace an identical resource");
        }
        this.claimId = Objects.requireNonNull(claimId, "claimId");
        this.claimRequestId = Objects.requireNonNull(claimRequestId, "claimRequestId");
        this.consumeRequestId = Objects.requireNonNull(consumeRequestId, "consumeRequestId");
        this.releaseRequestId = Objects.requireNonNull(releaseRequestId, "releaseRequestId");
        this.targetDebitTransferId = Objects.requireNonNull(targetDebitTransferId,
              "targetDebitTransferId");
        this.targetReturnTransferId = Objects.requireNonNull(targetReturnTransferId,
              "targetReturnTransferId");
        this.oldQioTransferId = Objects.requireNonNull(oldQioTransferId, "oldQioTransferId");
        this.oldExtractionReceiptId = Objects.requireNonNull(oldExtractionReceiptId,
              "oldExtractionReceiptId");
        this.newInstallationReceiptId = Objects.requireNonNull(newInstallationReceiptId,
              "newInstallationReceiptId");
        this.targetResourceUUID = Objects.requireNonNull(targetResourceUUID,
              "targetResourceUUID");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.targetQioBaseline = checkedAmount(targetQioBaseline, "targetQioBaseline");
        this.oldQioBaseline = checkedAmount(oldQioBaseline, "oldQioBaseline");
        this.targetReturnQioBaseline = checkedAmount(targetReturnQioBaseline,
              "targetReturnQioBaseline");
        this.targetInstalled = targetInstalled;
        this.targetReturned = targetReturned;
        this.contaminatedFromPhase = contaminatedFromPhase;
        this.diagnostic = diagnostic == null ? null : requireDiagnostic(diagnostic);
        this.claimOutstanding = claimOutstanding;
        validatePhase();
    }

    @Nonnull public UUID getExchangeId() { return exchangeId; }
    @Nonnull public UUID getOperationId() { return operationId; }
    @Nullable public UUID getOwnerJobId() { return ownerJobId; }
    @Nonnull public UUID getDeviceUUID() { return deviceUUID; }
    @Nonnull public UUID getLeaseId() { return leaseId; }
    @Nonnull public String getPortId() { return portId; }
    @Nonnull public String getPortGroupId() { return portGroupId; }
    /** 返回目标机器通道编号。 */
    public long getLaneId() { return laneId; }
    @Nonnull public MachineResourceStack getTarget() { return target; }
    @Nullable public MachineResourceStack getOriginal() { return original; }
    @Nonnull public PortableResourceDescriptor getTargetResource() { return describe(target); }
    @Nullable public PortableResourceDescriptor getOriginalResource() {
        return original == null ? null : describe(original);
    }
    @Nonnull public UUID getClaimId() { return claimId; }
    @Nonnull public UUID getClaimRequestId() { return claimRequestId; }
    @Nonnull public UUID getConsumeRequestId() { return consumeRequestId; }
    @Nonnull public UUID getReleaseRequestId() { return releaseRequestId; }
    @Nonnull public UUID getTargetDebitTransferId() { return targetDebitTransferId; }
    /** Stable idempotency key for returning a debited target template to QIO. */
    @Nonnull public UUID getTargetReturnTransferId() { return targetReturnTransferId; }
    @Nonnull public UUID getOldQioTransferId() { return oldQioTransferId; }
    @Nonnull public UUID getOldExtractionReceiptId() { return oldExtractionReceiptId; }
    @Nonnull public UUID getNewInstallationReceiptId() { return newInstallationReceiptId; }
    @Nonnull public UUID getTargetResourceUUID() { return targetResourceUUID; }
    @Nonnull public Phase getPhase() { return phase; }
    @Nullable public BigInteger getTargetQioBaseline() { return targetQioBaseline; }
    @Nullable public BigInteger getOldQioBaseline() { return oldQioBaseline; }
    @Nullable public BigInteger getTargetReturnQioBaseline() { return targetReturnQioBaseline; }
    @Nullable public String getDiagnostic() { return diagnostic; }
    /** 返回新配置是否已安装到机器端口。 */
    public boolean isTargetInstalled() { return targetInstalled; }
    /** 返回新配置是否已从机器退回 QIO。 */
    public boolean isTargetReturned() { return targetReturned; }
    @Nullable public Phase getContaminatedFromPhase() { return contaminatedFromPhase; }
    /** True when a target debit created an external ownership obligation. */
    /** 判断取消/恢复时是否仍必须退回新配置资源。 */
    public boolean requiresTargetReturn() {
        return targetQioBaseline != null && !targetInstalled && !targetReturned;
    }
    /** Whether a contaminated exchange has no unresolved old-template handoff. */
    /** 判断当前阶段是否允许恢复服务取消该交换。 */
    public boolean canCancelAfterRecovery() {
        return contaminatedFromPhase == Phase.CLAIM_PREPARED ||
              contaminatedFromPhase == Phase.CLAIMED ||
              contaminatedFromPhase == Phase.TARGET_DEBIT_PREPARED ||
              contaminatedFromPhase == Phase.TARGET_DEBITED ||
              contaminatedFromPhase == Phase.OLD_QIO_CREDITED ||
              contaminatedFromPhase == Phase.NEW_INSTALLED;
    }
    /** 返回是否仍持有 QIO claim。 */
    public boolean hasOutstandingClaim() { return claimOutstanding; }
    /** 返回是否已标记为等待强制恢复。 */
    public boolean isForceRecoveryPending() {
        return diagnostic != null && diagnostic.startsWith(FORCE_RECOVERY_PENDING_PREFIX);
    }

    /** 记录 claim 已成功建立。 */
    public void markClaimed() {
        requirePhase(Phase.CLAIM_PREPARED);
        claimOutstanding = true;
        phase = Phase.CLAIMED;
    }

    /** 在存储版本变化后重试 claim；版本未变化时避免重复请求。 */
    public boolean retryClaim(long contentsRevision, long claimRevision) {
        requirePhase(Phase.CLAIM_PREPARED);
        if (!markRetryRevision(contentsRevision, claimRevision)) return false;
        claimRequestId = UUID.randomUUID();
        return true;
    }

    /** 记录目标配置即将从 QIO 扣除的数量基线。 */
    public void prepareTargetDebit(@Nonnull BigInteger baseline) {
        requirePhase(Phase.CLAIMED);
        targetQioBaseline = checkedAmount(Objects.requireNonNull(baseline, "baseline"),
              "targetQioBaseline");
        if (targetQioBaseline.signum() <= 0) {
            throw new IllegalArgumentException("Target QIO baseline must contain the template");
        }
        phase = Phase.TARGET_DEBIT_PREPARED;
    }

    /** 记录目标配置已从 QIO 扣除。 */
    public void markTargetDebited() {
        requirePhase(Phase.TARGET_DEBIT_PREPARED);
        claimOutstanding = false;
        targetInstalled = false;
        targetReturned = false;
        phase = Phase.TARGET_DEBITED;
    }

    /** 记录 claim 已释放。 */
    public void markClaimReleased() {
        if (!claimOutstanding || phase != Phase.CONTAMINATED &&
              phase != Phase.CLAIMED && phase != Phase.TARGET_DEBIT_PREPARED) {
            throw new IllegalStateException("Configuration exchange has no releasable claim");
        }
        claimOutstanding = false;
    }

    /** 在目标尚未扣除前取消交换。 */
    public void cancelBeforeTargetDebit() {
        if (claimOutstanding || phase != Phase.CLAIM_PREPARED && phase != Phase.CLAIMED &&
              phase != Phase.TARGET_DEBIT_PREPARED) {
            throw new IllegalStateException("Configuration exchange cannot cancel from " + phase);
        }
        phase = Phase.CANCELLED;
    }

    /** Records the exact QIO baseline used by the compensating target return. */
    /** 记录目标配置退回 QIO 前的数量基线。 */
    public void prepareTargetReturn(@Nonnull BigInteger baseline) {
        if (!requiresTargetReturn()) {
            throw new IllegalStateException("Configuration exchange has no debited target to return");
        }
        BigInteger checked = checkedAmount(Objects.requireNonNull(baseline, "baseline"),
              "targetReturnQioBaseline");
        targetReturnQioBaseline = checked;
    }

    /** Marks the idempotent target return as durably credited to QIO. */
    /** 记录目标配置已退回 QIO。 */
    public void markTargetReturned() {
        if (!requiresTargetReturn() || targetReturnQioBaseline == null) {
            throw new IllegalStateException("Configuration target return is not prepared");
        }
        targetReturned = true;
    }

    /** Settles a contaminated exchange after its target ownership has been accounted for. */
    /** 在恢复已完成后取消交换并释放本地所有权。 */
    public void cancelAfterRecovery() {
        if (phase != Phase.CONTAMINATED || claimOutstanding || requiresTargetReturn() ||
              !canCancelAfterRecovery()) {
            throw new IllegalStateException("Configuration exchange cannot be settled after recovery");
        }
        phase = Phase.CANCELLED;
        diagnostic = null;
        contaminatedFromPhase = null;
    }

    /** 在容量/版本变化后重试目标配置扣除。 */
    public boolean retryTargetDebit(@Nonnull BigInteger baseline, long contentsRevision,
          long claimRevision) {
        requirePhase(Phase.TARGET_DEBIT_PREPARED);
        BigInteger checked = checkedAmount(Objects.requireNonNull(baseline, "baseline"),
              "targetQioBaseline");
        if (checked.signum() <= 0) {
            throw new IllegalArgumentException("Target QIO baseline must contain the template");
        }
        if (!markRetryRevision(contentsRevision, claimRevision)) return false;
        targetQioBaseline = checked;
        consumeRequestId = UUID.randomUUID();
        targetDebitTransferId = UUID.randomUUID();
        return true;
    }

    /** 记录旧配置退回 QIO 前的数量基线。 */
    public void prepareOldReturn(@Nonnull BigInteger baseline) {
        requirePhase(Phase.TARGET_DEBITED);
        if (original == null) {
            throw new IllegalStateException("An empty configuration port has nothing to return");
        }
        oldQioBaseline = checkedAmount(Objects.requireNonNull(baseline, "baseline"),
              "oldQioBaseline");
        phase = Phase.OLD_RETURN_PREPARED;
    }

    /** 记录旧配置已从机器抽取。 */
    public void markOldExtracted() {
        requirePhase(Phase.OLD_RETURN_PREPARED);
        phase = Phase.OLD_EXTRACTED;
    }

    /** 记录旧配置已写回 QIO。 */
    public void markOldQioCredited() {
        requirePhase(Phase.OLD_EXTRACTED);
        phase = Phase.OLD_QIO_CREDITED;
    }

    /**
     * Completes the old-template QIO credit while the exchange is already quarantined.  The
     * credit itself is idempotent; this marker is only advanced after that receipt succeeds.
     */
    /** 从持久化恢复路径确认旧配置已写回 QIO。 */
    public void recoverOldQioCredited() {
        if (phase != Phase.CONTAMINATED || contaminatedFromPhase != Phase.OLD_EXTRACTED) {
            throw new IllegalStateException("Contaminated exchange is not waiting for old-template credit");
        }
        contaminatedFromPhase = Phase.OLD_QIO_CREDITED;
    }

    /** Marks OLD_RETURN_PREPARED as safely not extracted after an exact port check. */
    /** 从恢复路径确认旧配置仍未从机器抽取。 */
    public void recoverOldReturnNotExtracted() {
        if (phase != Phase.CONTAMINATED || contaminatedFromPhase != Phase.OLD_RETURN_PREPARED) {
            throw new IllegalStateException("Contaminated exchange is not waiting for old-template extraction");
        }
        contaminatedFromPhase = Phase.TARGET_DEBITED;
    }

    /** Marks OLD_RETURN_PREPARED as extracted after a persisted machine receipt check. */
    /** 从恢复路径确认旧配置已抽取但尚未结算。 */
    public void recoverOldReturnExtracted() {
        if (phase != Phase.CONTAMINATED || contaminatedFromPhase != Phase.OLD_RETURN_PREPARED) {
            throw new IllegalStateException("Contaminated exchange is not waiting for old-template extraction");
        }
        contaminatedFromPhase = Phase.OLD_EXTRACTED;
    }

    /** 记录新配置已安装到机器。 */
    public void markNewInstalled() {
        if (phase != Phase.TARGET_DEBITED && phase != Phase.OLD_QIO_CREDITED) {
            throw new IllegalStateException("New configuration cannot be installed from " + phase);
        }
        if (targetReturned) {
            throw new IllegalStateException("A returned target cannot also be installed");
        }
        targetInstalled = true;
        phase = Phase.NEW_INSTALLED;
    }

    /** 结算已完成的配置交换并关闭 claim。 */
    public void commit() {
        requirePhase(Phase.NEW_INSTALLED);
        phase = Phase.COMMITTED;
    }

    /** 标记交换为污染并保留恢复诊断。 */
    public void contaminate(@Nonnull String reason) {
        if (phase == Phase.COMMITTED && !claimOutstanding) {
            throw new IllegalStateException("A committed configuration exchange cannot be contaminated");
        }
        if (phase != Phase.CONTAMINATED) {
            contaminatedFromPhase = phase;
        }
        diagnostic = requireDiagnostic(reason);
        phase = Phase.CONTAMINATED;
    }

    @Nonnull
    /** 将配置交换阶段、claim 和数量基线写入 NBT。 */
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "exchangeId", exchangeId);
        QIOProcessingNbt.writeUUID(data, "operationId", operationId);
        if (ownerJobId != null) QIOProcessingNbt.writeUUID(data, "ownerJobId", ownerJobId);
        QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
        QIOProcessingNbt.writeUUID(data, "leaseId", leaseId);
        data.setString("portId", portId);
        data.setString("portGroupId", portGroupId);
        data.setLong("laneId", laneId);
        data.setTag("target", target.write(new NBTTagCompound()));
        if (original != null) data.setTag("original", original.write(new NBTTagCompound()));
        QIOProcessingNbt.writeUUID(data, "claimId", claimId);
        QIOProcessingNbt.writeUUID(data, "claimRequestId", claimRequestId);
        QIOProcessingNbt.writeUUID(data, "consumeRequestId", consumeRequestId);
        QIOProcessingNbt.writeUUID(data, "releaseRequestId", releaseRequestId);
        QIOProcessingNbt.writeUUID(data, "targetDebitTransferId", targetDebitTransferId);
        QIOProcessingNbt.writeUUID(data, "targetReturnTransferId", targetReturnTransferId);
        QIOProcessingNbt.writeUUID(data, "oldQioTransferId", oldQioTransferId);
        QIOProcessingNbt.writeUUID(data, "oldExtractionReceiptId", oldExtractionReceiptId);
        QIOProcessingNbt.writeUUID(data, "newInstallationReceiptId", newInstallationReceiptId);
        QIOProcessingNbt.writeUUID(data, "targetResourceUUID", targetResourceUUID);
        data.setString("phase", phase.name());
        if (targetQioBaseline != null) data.setString("targetQioBaseline", targetQioBaseline.toString());
        if (oldQioBaseline != null) data.setString("oldQioBaseline", oldQioBaseline.toString());
        if (targetReturnQioBaseline != null) {
            data.setString("targetReturnQioBaseline", targetReturnQioBaseline.toString());
        }
        data.setBoolean("targetInstalled", targetInstalled);
        data.setBoolean("targetReturned", targetReturned);
        if (contaminatedFromPhase != null) {
            data.setString("contaminatedFromPhase", contaminatedFromPhase.name());
        }
        if (diagnostic != null) data.setString("diagnostic", diagnostic);
        data.setBoolean("claimOutstanding", claimOutstanding);
        if (lastRetryContentsRevision >= 0) {
            data.setLong("lastRetryContentsRevision", lastRetryContentsRevision);
            data.setLong("lastRetryClaimRevision", lastRetryClaimRevision);
        }
        return data;
    }

    @Nonnull
    /** 从 NBT 读取并校验交换阶段与 claim 所有权关系。 */
    public static QIOConfigurationExchangeRecord read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        int schema = data.getInteger("schema");
        if (schema != 1 && schema != 2 && schema != SCHEMA_VERSION) {
            throw new QIOProcessingDataException("Unsupported QIO configuration exchange schema");
        }
        try {
            MachineResourceStack target = MachineResourceStack.read(data.getCompoundTag("target"));
            MachineResourceStack original = data.hasKey("original", NBT.TAG_COMPOUND) ?
                  MachineResourceStack.read(data.getCompoundTag("original")) : null;
            if (target == null || data.hasKey("original", NBT.TAG_COMPOUND) && original == null) {
                throw new IllegalArgumentException("Invalid configuration exchange resource");
            }
            Phase phase = QIOProcessingNbt.readEnum(data, "phase", Phase.class);
            // A pre-v3 exchange did not persist the target disposition.  Both NEW_INSTALLED
            // and COMMITTED prove that the target reached the machine; a committed exchange
            // may have replaced a different original resource, so do not key this inference
            // on resource equality.
            boolean inferredInstalled = phase == Phase.NEW_INSTALLED || phase == Phase.COMMITTED;
            QIOConfigurationExchangeRecord record = new QIOConfigurationExchangeRecord(
                  QIOProcessingNbt.readUUID(data, "exchangeId"),
                  QIOProcessingNbt.readUUID(data, "operationId"),
                  QIOProcessingNbt.readOptionalUUID(data, "ownerJobId"),
                  QIOProcessingNbt.readUUID(data, "deviceUUID"),
                  QIOProcessingNbt.readUUID(data, "leaseId"), data.getString("portId"),
                  data.getString("portGroupId"), data.getLong("laneId"), target, original,
                  QIOProcessingNbt.readUUID(data, "claimId"),
                  QIOProcessingNbt.readUUID(data, "claimRequestId"),
                  QIOProcessingNbt.readUUID(data, "consumeRequestId"),
                  schema >= 2 ? QIOProcessingNbt.readUUID(data, "releaseRequestId") :
                        UUID.randomUUID(),
                  QIOProcessingNbt.readUUID(data, "targetDebitTransferId"),
                  QIOProcessingNbt.readUUID(data, "oldQioTransferId"),
                  QIOProcessingNbt.readUUID(data, "oldExtractionReceiptId"),
                  QIOProcessingNbt.readUUID(data, "newInstallationReceiptId"),
                  QIOProcessingNbt.readUUID(data, "targetResourceUUID"),
                  phase,
                  readAmount(data, "targetQioBaseline"), readAmount(data, "oldQioBaseline"),
                  data.hasKey("diagnostic", NBT.TAG_STRING) ? data.getString("diagnostic") : null,
                  schema >= 2 ? data.getBoolean("claimOutstanding") : phaseOwnsClaim(
                        phase),
                  schema >= 3 ? QIOProcessingNbt.readUUID(data, "targetReturnTransferId") :
                        UUID.randomUUID(),
                  schema >= 3 ? readAmount(data, "targetReturnQioBaseline") : null,
                  schema >= 3 ? data.getBoolean("targetInstalled") : inferredInstalled,
                  schema >= 3 && data.getBoolean("targetReturned"),
                  schema >= 3 && data.hasKey("contaminatedFromPhase", NBT.TAG_STRING) ?
                        QIOProcessingNbt.readEnum(data, "contaminatedFromPhase", Phase.class) : null);
            if (data.hasKey("lastRetryContentsRevision", NBT.TAG_LONG) ||
                  data.hasKey("lastRetryClaimRevision", NBT.TAG_LONG)) {
                if (!data.hasKey("lastRetryContentsRevision", NBT.TAG_LONG) ||
                      !data.hasKey("lastRetryClaimRevision", NBT.TAG_LONG)) {
                    throw new IllegalArgumentException("Incomplete configuration retry revision");
                }
                record.lastRetryContentsRevision = QIOProcessingNbt.requireNonNegative(
                      data.getLong("lastRetryContentsRevision"), "lastRetryContentsRevision");
                record.lastRetryClaimRevision = QIOProcessingNbt.requireNonNegative(
                      data.getLong("lastRetryClaimRevision"), "lastRetryClaimRevision");
            }
            return record;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO configuration exchange record", e);
        }
    }

    private void validatePhase() {
        boolean reused = phase == Phase.COMMITTED && original != null &&
              original.sameResource(target) && targetResourceUUID.equals(new UUID(0, 0));
        boolean targetBaselineRequired = !reused && phase.ordinal() >=
              Phase.TARGET_DEBIT_PREPARED.ordinal() && phase != Phase.CONTAMINATED &&
              phase != Phase.CANCELLED;
        boolean oldBaselineRequired = !reused && original != null &&
              phase.ordinal() >= Phase.OLD_RETURN_PREPARED.ordinal() &&
              phase != Phase.CONTAMINATED && phase != Phase.CANCELLED;
        // A cancelled exchange may retain the baselines used by a completed debit/return.
        // They are historical ownership evidence and are needed for idempotent receipt
        // reconciliation after a restart.  Only active phases require the exact presence
        // implied by their state machine phase; cancellation still validates the cross-field
        // relationships below (for example, a return baseline requires a target debit).
        if (phase != Phase.CONTAMINATED && phase != Phase.CANCELLED &&
              (targetBaselineRequired != (targetQioBaseline != null) ||
                    oldBaselineRequired && oldQioBaseline == null) ||
              phase == Phase.CONTAMINATED && diagnostic == null) {
            throw new IllegalArgumentException("Configuration exchange phase disagrees with its baselines: " +
                  phase + ", targetBaseline=" + targetQioBaseline + ", oldBaseline=" +
                  oldQioBaseline + ", diagnostic=" + diagnostic);
        }
        if (targetInstalled && targetReturned) {
            throw new IllegalArgumentException("Configuration target cannot be both installed and returned");
        }
        if (targetReturnQioBaseline != null && targetQioBaseline == null) {
            throw new IllegalArgumentException("Configuration target return has no target debit baseline");
        }
        if (oldQioBaseline != null && original == null) {
            throw new IllegalArgumentException("Configuration old-template baseline has no original resource");
        }
        if (targetReturned && targetReturnQioBaseline == null) {
            throw new IllegalArgumentException("Returned configuration target is missing its QIO baseline");
        }
        if (phase == Phase.CANCELLED && targetReturnQioBaseline != null && !targetReturned) {
            throw new IllegalArgumentException(
                  "Cancelled configuration exchange still has an unsettled target return");
        }
        if (phase == Phase.COMMITTED && !targetInstalled && !reused) {
            throw new IllegalArgumentException("Committed configuration exchange has no installed target");
        }
        if (phase != Phase.CONTAMINATED && contaminatedFromPhase != null) {
            throw new IllegalArgumentException("Non-contaminated exchange has a contamination origin");
        }
        if (claimOutstanding && !phaseOwnsClaim(phase) && phase != Phase.CONTAMINATED) {
            throw new IllegalArgumentException("Configuration claim ownership disagrees with its phase");
        }
    }

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Configuration exchange is " + phase + ", expected " + expected);
        }
    }

    private boolean markRetryRevision(long contentsRevision, long claimRevision) {
        long checkedContents = QIOProcessingNbt.requireNonNegative(contentsRevision,
              "contentsRevision");
        long checkedClaim = QIOProcessingNbt.requireNonNegative(claimRevision,
              "claimRevision");
        if (lastRetryContentsRevision == checkedContents &&
              lastRetryClaimRevision == checkedClaim) return false;
        lastRetryContentsRevision = checkedContents;
        lastRetryClaimRevision = checkedClaim;
        return true;
    }

    private static boolean phaseOwnsClaim(Phase phase) {
        return phase == Phase.CLAIMED || phase == Phase.TARGET_DEBIT_PREPARED;
    }

    private static MachineResourceStack requirePortStack(MachineResourceStack stack,
          String portId, long amount, String name) {
        Objects.requireNonNull(stack, name);
        if (!portId.equals(stack.portId()) || stack.amount() != amount) {
            throw new IllegalArgumentException(name + " does not match the configuration port");
        }
        return stack;
    }

    private static PortableResourceDescriptor describe(MachineResourceStack stack) {
        return switch (stack.kind()) {
            case ITEM -> PortableResourceDescriptor.item(stack.itemStack());
            case FLUID -> PortableResourceDescriptor.fluid(Objects.requireNonNull(stack.fluidStack()));
            case GAS -> PortableResourceDescriptor.gas(Objects.requireNonNull(stack.gasStack()));
        };
    }

    private static String requireId(String value, String name) {
        String checked = Objects.requireNonNull(value, name).trim();
        if (checked.isEmpty() || checked.length() > 128) {
            throw new IllegalArgumentException(name + " must contain 1..128 characters");
        }
        return checked;
    }

    private static String requireDiagnostic(String value) {
        String checked = Objects.requireNonNull(value, "diagnostic").trim();
        if (checked.isEmpty() || checked.length() > 512) {
            throw new IllegalArgumentException("diagnostic must contain 1..512 characters");
        }
        return checked;
    }

    @Nullable
    private static BigInteger checkedAmount(@Nullable BigInteger value, String name) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }

    @Nullable
    private static BigInteger readAmount(NBTTagCompound data, String key) {
        return data.hasKey(key, NBT.TAG_STRING) ? new BigInteger(data.getString(key)) : null;
    }
}
