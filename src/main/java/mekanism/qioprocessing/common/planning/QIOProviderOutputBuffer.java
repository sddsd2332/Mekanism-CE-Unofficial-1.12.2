package mekanism.qioprocessing.common.planning;

import mekanism.api.processing.MachinePortBaseline;
import mekanism.api.processing.MachineResourceStack;
import net.minecraft.nbt.NBTTagCompound;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable ownership handoff for one Provider machine-output extraction.
 */
public final class QIOProviderOutputBuffer {
    public enum Phase {PREPARED, HELD, DELIVERING}

    private final UUID bufferId;
    private final UUID operationId;
    private final MachinePortBaseline baseline;
    private final MachineResourceStack extraction;
    private final BigInteger qioBaseline;
    private final Phase phase;
    private final UUID qioTransferId;

    private QIOProviderOutputBuffer(UUID bufferId, UUID operationId,
                                    MachinePortBaseline baseline, MachineResourceStack extraction,
                                    BigInteger qioBaseline, Phase phase, UUID qioTransferId) {
        this.bufferId = Objects.requireNonNull(bufferId, "bufferId");
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.baseline = Objects.requireNonNull(baseline, "baseline");
        this.extraction = Objects.requireNonNull(extraction, "extraction");
        if (!baseline.portId().equals(extraction.portId()) || !baseline.resourceMatcher().accepts(extraction.descriptor())
                || baseline.amountOf(extraction) < extraction.amount()) {
            throw new IllegalArgumentException("Provider output extraction does not belong to its baseline");
        }
        this.qioBaseline = nonNegative(qioBaseline);
        this.phase = Objects.requireNonNull(phase, "phase");
        if (phase == Phase.DELIVERING && qioTransferId == null)
            throw new IllegalArgumentException("Delivering buffer requires a transfer id");
        if (phase != Phase.DELIVERING && qioTransferId != null)
            throw new IllegalArgumentException("Non-delivering buffer cannot have a transfer id");
        this.qioTransferId = qioTransferId;
    }

    public static QIOProviderOutputBuffer prepared(UUID bufferId, UUID operationId,
                                                   MachinePortBaseline baseline, MachineResourceStack extraction, BigInteger qioBaseline) {
        return new QIOProviderOutputBuffer(bufferId, operationId, baseline, extraction,
                qioBaseline, Phase.PREPARED, null);
    }

    public UUID bufferId() {
        return bufferId;
    }

    public UUID operationId() {
        return operationId;
    }

    public MachinePortBaseline baseline() {
        return baseline;
    }

    public MachineResourceStack extraction() {
        return extraction;
    }

    public BigInteger qioBaseline() {
        return qioBaseline;
    }

    public Phase phase() {
        return phase;
    }

    public UUID qioTransferId() {
        return qioTransferId;
    }

    public QIOProviderOutputBuffer held() {
        if (phase != Phase.PREPARED) throw new IllegalStateException("Only a prepared output buffer can be held");
        return new QIOProviderOutputBuffer(bufferId, operationId, baseline, extraction, qioBaseline, Phase.HELD, null);
    }

    public QIOProviderOutputBuffer delivering(UUID transferId) {
        if (phase != Phase.HELD) throw new IllegalStateException("Only a held output buffer can be delivered");
        return new QIOProviderOutputBuffer(bufferId, operationId, baseline, extraction, qioBaseline,
                Phase.DELIVERING, Objects.requireNonNull(transferId, "transferId"));
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("bufferId", bufferId);
        tag.setUniqueId("operationId", operationId);
        tag.setTag("baseline", baseline.write());
        tag.setTag("extraction", extraction.write(new NBTTagCompound()));
        tag.setString("qioBaseline", qioBaseline.toString());
        tag.setString("phase", phase.name());
        if (qioTransferId != null) tag.setUniqueId("qioTransferId", qioTransferId);
        return tag;
    }

    public static QIOProviderOutputBuffer read(NBTTagCompound tag) {
        if (tag == null || !tag.hasUniqueId("bufferId") || !tag.hasUniqueId("operationId")
                || !tag.hasKey("baseline", 10) || !tag.hasKey("extraction", 10)
                || !tag.hasKey("qioBaseline", 8) || !tag.hasKey("phase", 8)) {
            throw new IllegalArgumentException("Invalid Provider output buffer");
        }
        try {
            UUID transfer = tag.hasUniqueId("qioTransferId") ? tag.getUniqueId("qioTransferId") : null;
            return new QIOProviderOutputBuffer(tag.getUniqueId("bufferId"), tag.getUniqueId("operationId"),
                    MachinePortBaseline.read(tag.getCompoundTag("baseline")),
                    MachineResourceStack.read(tag.getCompoundTag("extraction")),
                    nonNegative(new BigInteger(tag.getString("qioBaseline"))),
                    Phase.valueOf(tag.getString("phase")), transfer);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid Provider output buffer", error);
        }
    }

    private static BigInteger nonNegative(BigInteger value) {
        Objects.requireNonNull(value, "qioBaseline");
        if (value.signum() < 0) throw new IllegalArgumentException("Negative Provider output QIO baseline");
        return value;
    }
}

