package mekanism.qioprocessing.common.processor;

import mekanism.api.processing.MachineOperationToken;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;
import java.util.UUID;

/**
 * Durable receipt tying one execution operation to one exact Processor endpoint and lane.
 */
public final class QIOProcessorAllocationRecord {
    public static final int VERSION = 1;

    private final UUID operationId;
    private final UUID processorId;
    private final long endpointRevision;
    private final MachineOperationToken token;

    public QIOProcessorAllocationRecord(UUID operationId, UUID processorId, long endpointRevision,
                                        MachineOperationToken token) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.processorId = Objects.requireNonNull(processorId, "processorId");
        if (endpointRevision < 0) throw new IllegalArgumentException("Invalid processor endpoint revision");
        this.endpointRevision = endpointRevision;
        this.token = Objects.requireNonNull(token, "token");
        if (!operationId.equals(token.operationId())) {
            throw new IllegalArgumentException("Processor allocation operation mismatch");
        }
    }

    public UUID operationId() {
        return operationId;
    }

    public UUID processorId() {
        return processorId;
    }

    public long endpointRevision() {
        return endpointRevision;
    }

    public MachineOperationToken token() {
        return token;
    }

    public QIOProcessorAllocationRecord withToken(MachineOperationToken nextToken) {
        return new QIOProcessorAllocationRecord(operationId, processorId, endpointRevision, nextToken);
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", VERSION);
        tag.setUniqueId("operation", operationId);
        tag.setUniqueId("processor", processorId);
        tag.setLong("endpointRevision", endpointRevision);
        tag.setTag("token", token.write());
        return tag;
    }

    public static QIOProcessorAllocationRecord read(NBTTagCompound tag) {
        if (tag == null || tag.getInteger("version") != VERSION || !tag.hasUniqueId("operation")
                || !tag.hasUniqueId("processor") || !tag.hasKey("endpointRevision", 4)
                || !tag.hasKey("token", 10)) {
            throw new IllegalArgumentException("Invalid Processor allocation record");
        }
        try {
            return new QIOProcessorAllocationRecord(tag.getUniqueId("operation"),
                    tag.getUniqueId("processor"), tag.getLong("endpointRevision"),
                    MachineOperationToken.read(tag.getCompoundTag("token")));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid Processor allocation record", error);
        }
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof QIOProcessorAllocationRecord)) return false;
        QIOProcessorAllocationRecord other = (QIOProcessorAllocationRecord) value;
        return endpointRevision == other.endpointRevision && operationId.equals(other.operationId)
                && processorId.equals(other.processorId) && token.equals(other.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationId, processorId, endpointRevision, token);
    }
}

