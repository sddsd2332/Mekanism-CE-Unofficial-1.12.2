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
public final class QIOConfigurationExchangeRecord {

    public static final int SCHEMA_VERSION = 2;

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
    private String diagnostic;
    private long lastRetryContentsRevision = -1;
    private long lastRetryClaimRevision = -1;
    private boolean claimOutstanding;

    public QIOConfigurationExchangeRecord(@Nonnull UUID exchangeId,
          @Nonnull UUID operationId, @Nullable UUID ownerJobId, @Nonnull UUID deviceUUID,
          @Nonnull UUID leaseId, @Nonnull String portId, @Nonnull String portGroupId,
          long laneId, @Nonnull MachineResourceStack target,
          @Nullable MachineResourceStack original, @Nonnull UUID targetResourceUUID) {
        this(exchangeId, operationId, ownerJobId, deviceUUID, leaseId, portId, portGroupId,
              laneId, target, original, UUID.randomUUID(), UUID.randomUUID(),
              UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
              UUID.randomUUID(), UUID.randomUUID(), targetResourceUUID, Phase.CLAIM_PREPARED,
              null, null, null, false);
    }

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
              new UUID(0, 0), Phase.COMMITTED, null, null, null, false);
    }

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
          boolean claimOutstanding) {
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
    @Nonnull public UUID getOldQioTransferId() { return oldQioTransferId; }
    @Nonnull public UUID getOldExtractionReceiptId() { return oldExtractionReceiptId; }
    @Nonnull public UUID getNewInstallationReceiptId() { return newInstallationReceiptId; }
    @Nonnull public UUID getTargetResourceUUID() { return targetResourceUUID; }
    @Nonnull public Phase getPhase() { return phase; }
    @Nullable public BigInteger getTargetQioBaseline() { return targetQioBaseline; }
    @Nullable public BigInteger getOldQioBaseline() { return oldQioBaseline; }
    @Nullable public String getDiagnostic() { return diagnostic; }
    public boolean hasOutstandingClaim() { return claimOutstanding; }

    public void markClaimed() {
        requirePhase(Phase.CLAIM_PREPARED);
        claimOutstanding = true;
        phase = Phase.CLAIMED;
    }

    public boolean retryClaim(long contentsRevision, long claimRevision) {
        requirePhase(Phase.CLAIM_PREPARED);
        if (!markRetryRevision(contentsRevision, claimRevision)) return false;
        claimRequestId = UUID.randomUUID();
        return true;
    }

    public void prepareTargetDebit(@Nonnull BigInteger baseline) {
        requirePhase(Phase.CLAIMED);
        targetQioBaseline = checkedAmount(Objects.requireNonNull(baseline, "baseline"),
              "targetQioBaseline");
        if (targetQioBaseline.signum() <= 0) {
            throw new IllegalArgumentException("Target QIO baseline must contain the template");
        }
        phase = Phase.TARGET_DEBIT_PREPARED;
    }

    public void markTargetDebited() {
        requirePhase(Phase.TARGET_DEBIT_PREPARED);
        claimOutstanding = false;
        phase = Phase.TARGET_DEBITED;
    }

    public void markClaimReleased() {
        if (!claimOutstanding || phase != Phase.CONTAMINATED &&
              phase != Phase.CLAIMED && phase != Phase.TARGET_DEBIT_PREPARED) {
            throw new IllegalStateException("Configuration exchange has no releasable claim");
        }
        claimOutstanding = false;
    }

    public void cancelBeforeTargetDebit() {
        if (claimOutstanding || phase != Phase.CLAIM_PREPARED && phase != Phase.CLAIMED &&
              phase != Phase.TARGET_DEBIT_PREPARED) {
            throw new IllegalStateException("Configuration exchange cannot cancel from " + phase);
        }
        phase = Phase.CANCELLED;
    }

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

    public void prepareOldReturn(@Nonnull BigInteger baseline) {
        requirePhase(Phase.TARGET_DEBITED);
        if (original == null) {
            throw new IllegalStateException("An empty configuration port has nothing to return");
        }
        oldQioBaseline = checkedAmount(Objects.requireNonNull(baseline, "baseline"),
              "oldQioBaseline");
        phase = Phase.OLD_RETURN_PREPARED;
    }

    public void markOldExtracted() {
        requirePhase(Phase.OLD_RETURN_PREPARED);
        phase = Phase.OLD_EXTRACTED;
    }

    public void markOldQioCredited() {
        requirePhase(Phase.OLD_EXTRACTED);
        phase = Phase.OLD_QIO_CREDITED;
    }

    public void markNewInstalled() {
        if (phase != Phase.TARGET_DEBITED && phase != Phase.OLD_QIO_CREDITED) {
            throw new IllegalStateException("New configuration cannot be installed from " + phase);
        }
        phase = Phase.NEW_INSTALLED;
    }

    public void commit() {
        requirePhase(Phase.NEW_INSTALLED);
        phase = Phase.COMMITTED;
    }

    public void contaminate(@Nonnull String reason) {
        if (phase == Phase.COMMITTED) {
            throw new IllegalStateException("A committed configuration exchange cannot be contaminated");
        }
        diagnostic = requireDiagnostic(reason);
        phase = Phase.CONTAMINATED;
    }

    @Nonnull
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
        QIOProcessingNbt.writeUUID(data, "oldQioTransferId", oldQioTransferId);
        QIOProcessingNbt.writeUUID(data, "oldExtractionReceiptId", oldExtractionReceiptId);
        QIOProcessingNbt.writeUUID(data, "newInstallationReceiptId", newInstallationReceiptId);
        QIOProcessingNbt.writeUUID(data, "targetResourceUUID", targetResourceUUID);
        data.setString("phase", phase.name());
        if (targetQioBaseline != null) data.setString("targetQioBaseline", targetQioBaseline.toString());
        if (oldQioBaseline != null) data.setString("oldQioBaseline", oldQioBaseline.toString());
        if (diagnostic != null) data.setString("diagnostic", diagnostic);
        data.setBoolean("claimOutstanding", claimOutstanding);
        if (lastRetryContentsRevision >= 0) {
            data.setLong("lastRetryContentsRevision", lastRetryContentsRevision);
            data.setLong("lastRetryClaimRevision", lastRetryClaimRevision);
        }
        return data;
    }

    @Nonnull
    public static QIOConfigurationExchangeRecord read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        int schema = data.getInteger("schema");
        if (schema != 1 && schema != SCHEMA_VERSION) {
            throw new QIOProcessingDataException("Unsupported QIO configuration exchange schema");
        }
        try {
            MachineResourceStack target = MachineResourceStack.read(data.getCompoundTag("target"));
            MachineResourceStack original = data.hasKey("original", NBT.TAG_COMPOUND) ?
                  MachineResourceStack.read(data.getCompoundTag("original")) : null;
            if (target == null || data.hasKey("original", NBT.TAG_COMPOUND) && original == null) {
                throw new IllegalArgumentException("Invalid configuration exchange resource");
            }
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
                  QIOProcessingNbt.readEnum(data, "phase", Phase.class),
                  readAmount(data, "targetQioBaseline"), readAmount(data, "oldQioBaseline"),
                  data.hasKey("diagnostic", NBT.TAG_STRING) ? data.getString("diagnostic") : null,
                  schema >= 2 ? data.getBoolean("claimOutstanding") : phaseOwnsClaim(
                        QIOProcessingNbt.readEnum(data, "phase", Phase.class)));
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
        boolean targetBaselineRequired = !reused && phase != Phase.CANCELLED &&
              phase.ordinal() >= Phase.TARGET_DEBIT_PREPARED.ordinal() &&
              phase != Phase.CONTAMINATED;
        boolean oldBaselineRequired = !reused && phase != Phase.CANCELLED && original != null &&
              phase.ordinal() >= Phase.OLD_RETURN_PREPARED.ordinal() &&
              phase != Phase.CONTAMINATED;
        if (phase != Phase.CONTAMINATED &&
              (targetBaselineRequired != (targetQioBaseline != null) ||
                    oldBaselineRequired && oldQioBaseline == null) ||
              phase == Phase.CONTAMINATED && diagnostic == null) {
            throw new IllegalArgumentException("Configuration exchange phase disagrees with its baselines");
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
