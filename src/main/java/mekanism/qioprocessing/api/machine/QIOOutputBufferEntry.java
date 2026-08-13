package mekanism.qioprocessing.api.machine;

import mekanism.api.processing.MachineResourceStack;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Persistent machine-to-QIO intermediate ownership for one output port extraction. */
public final class QIOOutputBufferEntry {

    public enum Phase {
        PREPARED,
        HELD,
        DELIVERING
    }

    private final UUID bufferId;
    private final UUID operationId;
    private final UUID leaseId;
    private final MachinePortBaseline baseline;
    private final MachineResourceStack extraction;
    private final long createdAt;
    private final Phase phase;
    @Nullable
    private final PortableResourceDescriptor resource;
    private final long amount;
    @Nullable
    private final UUID qioTransferId;
    private final long qioRequestedAmount;

    private QIOOutputBufferEntry(UUID bufferId, UUID operationId, UUID leaseId, MachinePortBaseline baseline,
          MachineResourceStack extraction, long createdAt, Phase phase, @Nullable PortableResourceDescriptor resource, long amount,
          @Nullable UUID qioTransferId, long qioRequestedAmount) {
        this.bufferId = Objects.requireNonNull(bufferId, "Output buffer id cannot be null");
        this.operationId = Objects.requireNonNull(operationId, "Output operation id cannot be null");
        this.leaseId = Objects.requireNonNull(leaseId, "Output lease id cannot be null");
        this.baseline = Objects.requireNonNull(baseline, "Output baseline cannot be null");
        this.extraction = Objects.requireNonNull(extraction, "Output extraction cannot be null");
        MachineResourceStack baselineContents = baseline.contents();
        if (baselineContents == null || extraction.kind() != baseline.kind() ||
            !extraction.portId().equals(baseline.portId()) ||
            extraction.amount() > baselineContents.amount() || !extraction.sameResource(baselineContents)) {
            throw new IllegalArgumentException("Output extraction does not belong to its prepared baseline");
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("Output buffer creation tick cannot be negative");
        }
        this.createdAt = createdAt;
        this.phase = Objects.requireNonNull(phase, "Output buffer phase cannot be null");
        if (phase == Phase.PREPARED) {
            if (resource != null || amount != 0 || qioTransferId != null || qioRequestedAmount != 0) {
                throw new IllegalArgumentException("Prepared output buffer cannot own resources or a QIO transfer");
            }
        } else {
            if (resource == null || amount <= 0) {
                throw new IllegalArgumentException("Held output buffer requires a positive resource amount");
            }
            if (amount > extraction.amount()) {
                throw new IllegalArgumentException("Output buffer contains more than its prepared extraction");
            }
            if (phase == Phase.DELIVERING && (qioTransferId == null || qioRequestedAmount <= 0 ||
                                              qioRequestedAmount > amount) ||
                phase == Phase.HELD && (qioTransferId != null || qioRequestedAmount != 0)) {
                throw new IllegalArgumentException("Output buffer delivery state is inconsistent");
            }
        }
        this.resource = resource;
        this.amount = amount;
        this.qioTransferId = qioTransferId;
        this.qioRequestedAmount = qioRequestedAmount;
    }

    @Nonnull
    public static QIOOutputBufferEntry prepared(@Nonnull UUID bufferId, @Nonnull UUID operationId,
          @Nonnull UUID leaseId, @Nonnull MachinePortBaseline baseline, long createdAt) {
        MachineResourceStack contents = baseline.contents();
        if (contents == null) {
            throw new IllegalArgumentException("Prepared output baseline must contain a resource");
        }
        return prepared(bufferId, operationId, leaseId, baseline, contents, createdAt);
    }

    @Nonnull
    public static QIOOutputBufferEntry prepared(@Nonnull UUID bufferId, @Nonnull UUID operationId,
          @Nonnull UUID leaseId, @Nonnull MachinePortBaseline baseline,
          @Nonnull MachineResourceStack extraction, long createdAt) {
        return new QIOOutputBufferEntry(bufferId, operationId, leaseId, baseline, extraction, createdAt, Phase.PREPARED,
              null, 0, null, 0);
    }

    @Nonnull
    public UUID bufferId() {
        return bufferId;
    }

    @Nonnull
    public UUID operationId() {
        return operationId;
    }

    @Nonnull
    public UUID leaseId() {
        return leaseId;
    }

    @Nonnull
    public MachinePortBaseline baseline() {
        return baseline;
    }

    @Nonnull
    public MachineResourceStack extraction() {
        return extraction;
    }

    public long createdAt() {
        return createdAt;
    }

    @Nonnull
    public Phase phase() {
        return phase;
    }

    @Nullable
    public PortableResourceDescriptor resource() {
        return resource;
    }

    public long amount() {
        return amount;
    }

    @Nullable
    public UUID qioTransferId() {
        return qioTransferId;
    }

    public long qioRequestedAmount() {
        return qioRequestedAmount;
    }

    @Nonnull
    public QIOOutputBufferEntry hold(@Nonnull PortableResourceDescriptor resource, long amount) {
        if (phase != Phase.PREPARED) {
            throw new IllegalStateException("Only a prepared output buffer can receive machine resources");
        }
        return new QIOOutputBufferEntry(bufferId, operationId, leaseId, baseline, extraction, createdAt, Phase.HELD,
              resource, amount, null, 0);
    }

    @Nonnull
    public QIOOutputBufferEntry beginDelivery(@Nonnull UUID transferId, long requestedAmount) {
        if (phase != Phase.HELD) {
            throw new IllegalStateException("Only a held output buffer can begin QIO delivery");
        }
        return new QIOOutputBufferEntry(bufferId, operationId, leaseId, baseline, extraction, createdAt, Phase.DELIVERING,
              resource, amount, transferId, requestedAmount);
    }

    @Nullable
    public QIOOutputBufferEntry applyDeliveryReceipt(@Nonnull UUID transferId, long transferredAmount) {
        if (phase != Phase.DELIVERING || !Objects.requireNonNull(transferId, "Transfer id cannot be null").equals(qioTransferId)) {
            throw new IllegalStateException("QIO delivery receipt does not match the pending transfer");
        }
        if (transferredAmount <= 0 || transferredAmount != qioRequestedAmount || transferredAmount > amount) {
            throw new IllegalArgumentException("QIO delivery receipt has an unexpected amount");
        }
        long remaining = amount - transferredAmount;
        return remaining == 0 ? null : new QIOOutputBufferEntry(bufferId, operationId, leaseId, baseline, extraction, createdAt,
              Phase.HELD, resource, remaining, null, 0);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("bufferId", bufferId.toString());
        data.setString("operationId", operationId.toString());
        data.setString("leaseId", leaseId.toString());
        data.setTag("baseline", baseline.write());
        data.setTag("extraction", extraction.write(new NBTTagCompound()));
        data.setLong("createdAt", createdAt);
        data.setString("phase", phase.name());
        if (resource != null) {
            data.setTag("resource", resource.write());
            data.setLong("amount", amount);
        }
        if (qioTransferId != null) {
            data.setString("qioTransferId", qioTransferId.toString());
            data.setLong("qioRequestedAmount", qioRequestedAmount);
        }
        return data;
    }

    @Nonnull
    public static QIOOutputBufferEntry read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "Output buffer data cannot be null");
        try {
            Phase phase = Phase.valueOf(data.getString("phase"));
            PortableResourceDescriptor resource = data.hasKey("resource", NBT.TAG_COMPOUND) ?
                  PortableResourceDescriptor.read(data.getCompoundTag("resource")) : null;
            UUID qioTransferId = data.hasKey("qioTransferId", NBT.TAG_STRING) ?
                  parseUUID(data.getString("qioTransferId"), "qioTransferId") : null;
            if (!data.hasKey("extraction", NBT.TAG_COMPOUND)) {
                throw new IllegalArgumentException("QIO output buffer is missing its extraction intent");
            }
            MachinePortBaseline baseline = MachinePortBaseline.read(data.getCompoundTag("baseline"));
            MachineResourceStack extraction = MachineResourceStack.read(
                  data.getCompoundTag("extraction"));
            return new QIOOutputBufferEntry(parseUUID(data.getString("bufferId"), "bufferId"),
                  parseUUID(data.getString("operationId"), "operationId"),
                  parseUUID(data.getString("leaseId"), "leaseId"),
                  baseline, extraction, data.getLong("createdAt"), phase,
                  resource, data.getLong("amount"), qioTransferId, data.getLong("qioRequestedAmount"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid QIO output buffer entry", e);
        }
    }

    private static UUID parseUUID(String value, String key) {
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
        if (!(obj instanceof QIOOutputBufferEntry other)) {
            return false;
        }
        return createdAt == other.createdAt && amount == other.amount && qioRequestedAmount == other.qioRequestedAmount &&
              bufferId.equals(other.bufferId) && operationId.equals(other.operationId) && leaseId.equals(other.leaseId) &&
              baseline.equals(other.baseline) && extraction.equals(other.extraction) && phase == other.phase && Objects.equals(resource, other.resource) &&
              Objects.equals(qioTransferId, other.qioTransferId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bufferId, operationId, leaseId, baseline, extraction, createdAt, phase, resource, amount,
              qioTransferId, qioRequestedAmount);
    }
}
