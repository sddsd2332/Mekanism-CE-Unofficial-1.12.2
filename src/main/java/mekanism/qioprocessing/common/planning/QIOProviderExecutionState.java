package mekanism.qioprocessing.common.planning;

import mekanism.api.processing.MachineOperationToken;
import mekanism.api.processing.MachinePortBaseline;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.math.BigInteger;
import java.util.*;

/**
 * Durable progress for one Provider order and one physical machine operation.
 */
public final class QIOProviderExecutionState {
    public static final int VERSION = 1;
    private static final int MAX_PORTS = 256;
    private static final int MAX_BASELINES = 512;
    private static final int MAX_OUTPUT_BUFFERS = 512;

    public enum Status {
        READY,
        LOADING,
        PROCESSING,
        COLLECTING,
        COMPLETED,
        FAILED,
        NEEDS_RECONCILIATION
    }

    public static final class Operation {
        private final UUID operationId;
        private final int stepIndex;
        private final long operationIndex;
        private final MachineOperationToken token;
        private final Map<String, MachinePortBaseline> portBaselines;
        private final Map<UUID, BigInteger> transferBaselines;
        private final Map<UUID, QIOProviderOutputBuffer> outputBuffers;

        public Operation(UUID operationId, int stepIndex, long operationIndex,
                         MachineOperationToken token, Map<String, MachinePortBaseline> portBaselines,
                         Map<UUID, BigInteger> transferBaselines) {
            this(operationId, stepIndex, operationIndex, token, portBaselines, transferBaselines,
                    Collections.emptyMap());
        }

        public Operation(UUID operationId, int stepIndex, long operationIndex,
                         MachineOperationToken token, Map<String, MachinePortBaseline> portBaselines,
                         Map<UUID, BigInteger> transferBaselines,
                         Map<UUID, QIOProviderOutputBuffer> outputBuffers) {
            this.operationId = Objects.requireNonNull(operationId, "operationId");
            if (stepIndex < 0 || operationIndex < 0)
                throw new IllegalArgumentException("Invalid Provider operation position");
            this.stepIndex = stepIndex;
            this.operationIndex = operationIndex;
            this.token = Objects.requireNonNull(token, "token");
            if (!operationId.equals(token.operationId()))
                throw new IllegalArgumentException("Provider token identity mismatch");
            this.portBaselines = immutablePorts(portBaselines);
            this.transferBaselines = immutableTransfers(transferBaselines);
            this.outputBuffers = immutableBuffers(outputBuffers);
        }

        public UUID operationId() {
            return operationId;
        }

        public int stepIndex() {
            return stepIndex;
        }

        public long operationIndex() {
            return operationIndex;
        }

        public MachineOperationToken token() {
            return token;
        }

        public Map<String, MachinePortBaseline> portBaselines() {
            return portBaselines;
        }

        public Map<UUID, BigInteger> transferBaselines() {
            return transferBaselines;
        }

        public Map<UUID, QIOProviderOutputBuffer> outputBuffers() {
            return outputBuffers;
        }

        public Operation withToken(MachineOperationToken next) {
            return new Operation(operationId, stepIndex, operationIndex, next, portBaselines, transferBaselines, outputBuffers);
        }

        public Operation withTransferBaseline(UUID transferId, BigInteger baseline) {
            Map<UUID, BigInteger> next = new LinkedHashMap<>(transferBaselines);
            next.put(Objects.requireNonNull(transferId, "transferId"), nonNegative(baseline));
            return new Operation(operationId, stepIndex, operationIndex, token, portBaselines, next, outputBuffers);
        }

        public Operation withPortBaseline(MachinePortBaseline baseline) {
            Objects.requireNonNull(baseline, "baseline");
            Map<String, MachinePortBaseline> next = new LinkedHashMap<>(portBaselines);
            next.put(baseline.portId(), baseline);
            return new Operation(operationId, stepIndex, operationIndex, token, next, transferBaselines, outputBuffers);
        }

        public Operation withOutputBuffer(QIOProviderOutputBuffer buffer) {
            Objects.requireNonNull(buffer, "buffer");
            if (!operationId.equals(buffer.operationId()))
                throw new IllegalArgumentException("Provider output buffer operation mismatch");
            Map<UUID, QIOProviderOutputBuffer> next = new LinkedHashMap<>(outputBuffers);
            next.put(buffer.bufferId(), buffer);
            return new Operation(operationId, stepIndex, operationIndex, token, portBaselines, transferBaselines, next);
        }

        public Operation withoutOutputBuffer(UUID bufferId) {
            Map<UUID, QIOProviderOutputBuffer> next = new LinkedHashMap<>(outputBuffers);
            next.remove(Objects.requireNonNull(bufferId, "bufferId"));
            return new Operation(operationId, stepIndex, operationIndex, token, portBaselines, transferBaselines, next);
        }

        private static Map<String, MachinePortBaseline> immutablePorts(Map<String, MachinePortBaseline> values) {
            Objects.requireNonNull(values, "portBaselines");
            if (values.size() > MAX_PORTS) throw new IllegalArgumentException("Too many Provider port baselines");
            Map<String, MachinePortBaseline> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (key == null || value == null || !key.equals(value.portId()) || copy.putIfAbsent(key, value) != null) {
                    throw new IllegalArgumentException("Invalid Provider port baseline");
                }
            });
            return Collections.unmodifiableMap(copy);
        }

        private static Map<UUID, BigInteger> immutableTransfers(Map<UUID, BigInteger> values) {
            Objects.requireNonNull(values, "transferBaselines");
            if (values.size() > MAX_BASELINES)
                throw new IllegalArgumentException("Too many Provider transfer baselines");
            Map<UUID, BigInteger> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (key == null || value == null || value.signum() < 0 || copy.putIfAbsent(key, value) != null) {
                    throw new IllegalArgumentException("Invalid Provider transfer baseline");
                }
            });
            return Collections.unmodifiableMap(copy);
        }

        private static Map<UUID, QIOProviderOutputBuffer> immutableBuffers(Map<UUID, QIOProviderOutputBuffer> values) {
            Objects.requireNonNull(values, "outputBuffers");
            if (values.size() > MAX_OUTPUT_BUFFERS)
                throw new IllegalArgumentException("Too many Provider output buffers");
            Map<UUID, QIOProviderOutputBuffer> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (key == null || value == null || !key.equals(value.bufferId()) || copy.putIfAbsent(key, value) != null) {
                    throw new IllegalArgumentException("Invalid Provider output buffer");
                }
            });
            return Collections.unmodifiableMap(copy);
        }
    }

    private final UUID orderId;
    private final Status status;
    private final int nextStep;
    private final long nextOperation;
    private final Operation operation;

    private QIOProviderExecutionState(UUID orderId, Status status, int nextStep,
                                      long nextOperation, Operation operation) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.status = Objects.requireNonNull(status, "status");
        if (nextStep < 0 || nextOperation < 0) throw new IllegalArgumentException("Invalid Provider execution cursor");
        if (status == Status.READY && operation != null)
            throw new IllegalArgumentException("Ready Provider state cannot have an operation");
        if (status != Status.READY && status != Status.COMPLETED && status != Status.FAILED
                && status != Status.NEEDS_RECONCILIATION && operation == null) {
            throw new IllegalArgumentException("Active Provider state requires an operation");
        }
        this.nextStep = nextStep;
        this.nextOperation = nextOperation;
        this.operation = operation;
    }

    public static QIOProviderExecutionState ready(UUID orderId) {
        return new QIOProviderExecutionState(orderId, Status.READY, 0, 0, null);
    }

    public QIOProviderExecutionState loading(Operation operation) {
        return active(Status.LOADING, operation);
    }

    public QIOProviderExecutionState processing(Operation operation) {
        return active(Status.PROCESSING, operation);
    }

    public QIOProviderExecutionState collecting(Operation operation) {
        return active(Status.COLLECTING, operation);
    }

    public QIOProviderExecutionState advance(int step, long operation) {
        return new QIOProviderExecutionState(orderId, Status.READY, step, operation, null);
    }

    public QIOProviderExecutionState completed() {
        return new QIOProviderExecutionState(orderId, Status.COMPLETED, nextStep, nextOperation, null);
    }

    public QIOProviderExecutionState failed() {
        return new QIOProviderExecutionState(orderId, Status.FAILED, nextStep, nextOperation, null);
    }

    public QIOProviderExecutionState needsReconciliation() {
        return new QIOProviderExecutionState(orderId, Status.NEEDS_RECONCILIATION, nextStep, nextOperation, null);
    }

    private QIOProviderExecutionState active(Status state, Operation operation) {
        return new QIOProviderExecutionState(orderId, state, nextStep, nextOperation, operation);
    }

    public UUID orderId() {
        return orderId;
    }

    public Status status() {
        return status;
    }

    public int nextStep() {
        return nextStep;
    }

    public long nextOperation() {
        return nextOperation;
    }

    public Operation operation() {
        return operation;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("format", "mekanismqioprocessing:provider_execution_state");
        tag.setInteger("version", VERSION);
        tag.setUniqueId("order", orderId);
        tag.setString("status", status.name());
        tag.setInteger("nextStep", nextStep);
        tag.setLong("nextOperation", nextOperation);
        if (operation != null) tag.setTag("operation", writeOperation(operation));
        return tag;
    }

    public static QIOProviderExecutionState read(NBTTagCompound tag) {
        if (tag == null || !"mekanismqioprocessing:provider_execution_state".equals(tag.getString("format"))
                || tag.getInteger("version") != VERSION || !tag.hasUniqueId("order")
                || !tag.hasKey("status", 8) || !tag.hasKey("nextStep", 3) || !tag.hasKey("nextOperation", 4)) {
            throw new IllegalArgumentException("Invalid Provider execution state header");
        }
        try {
            Status status = Status.valueOf(tag.getString("status"));
            Operation operation = tag.hasKey("operation", 10) ? readOperation(tag.getCompoundTag("operation")) : null;
            return new QIOProviderExecutionState(tag.getUniqueId("order"), status,
                    tag.getInteger("nextStep"), tag.getLong("nextOperation"), operation);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid Provider execution state", error);
        }
    }

    private static NBTTagCompound writeOperation(Operation operation) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("id", operation.operationId());
        tag.setInteger("step", operation.stepIndex());
        tag.setLong("index", operation.operationIndex());
        tag.setTag("token", operation.token().write());
        NBTTagList ports = new NBTTagList();
        operation.portBaselines().forEach((id, baseline) -> ports.appendTag(baseline.write()));
        tag.setTag("ports", ports);
        NBTTagList transfers = new NBTTagList();
        operation.transferBaselines().forEach((id, baseline) -> {
            NBTTagCompound row = new NBTTagCompound();
            row.setUniqueId("id", id);
            row.setString("baseline", baseline.toString());
            transfers.appendTag(row);
        });
        tag.setTag("transfers", transfers);
        NBTTagList buffers = new NBTTagList();
        operation.outputBuffers().values().forEach(buffer -> buffers.appendTag(buffer.write()));
        tag.setTag("outputBuffers", buffers);
        return tag;
    }

    private static Operation readOperation(NBTTagCompound tag) {
        if (!tag.hasUniqueId("id") || !tag.hasKey("step", 3) || !tag.hasKey("index", 4)
                || !tag.hasKey("token", 10) || !tag.hasKey("ports", 9) || !tag.hasKey("transfers", 9)) {
            throw new IllegalArgumentException("Invalid Provider execution operation");
        }
        NBTTagList rawPorts = tag.getTagList("ports", 10);
        if (rawPorts.tagCount() > MAX_PORTS) throw new IllegalArgumentException("Too many Provider port baselines");
        Map<String, MachinePortBaseline> ports = new LinkedHashMap<>();
        for (int index = 0; index < rawPorts.tagCount(); index++) {
            MachinePortBaseline baseline = MachinePortBaseline.read(rawPorts.getCompoundTagAt(index));
            if (ports.putIfAbsent(baseline.portId(), baseline) != null)
                throw new IllegalArgumentException("Duplicate Provider port baseline");
        }
        NBTTagList rawTransfers = tag.getTagList("transfers", 10);
        if (rawTransfers.tagCount() > MAX_BASELINES)
            throw new IllegalArgumentException("Too many Provider transfer baselines");
        Map<UUID, BigInteger> transfers = new LinkedHashMap<>();
        for (int index = 0; index < rawTransfers.tagCount(); index++) {
            NBTTagCompound row = rawTransfers.getCompoundTagAt(index);
            if (!row.hasUniqueId("id") || !row.hasKey("baseline", 8))
                throw new IllegalArgumentException("Invalid Provider transfer baseline");
            BigInteger baseline = new BigInteger(row.getString("baseline"));
            if (transfers.putIfAbsent(row.getUniqueId("id"), nonNegative(baseline)) != null) {
                throw new IllegalArgumentException("Duplicate Provider transfer baseline");
            }
        }
        NBTTagList rawBuffers = tag.hasKey("outputBuffers", 9)
                ? tag.getTagList("outputBuffers", 10) : new NBTTagList();
        if (rawBuffers.tagCount() > MAX_OUTPUT_BUFFERS)
            throw new IllegalArgumentException("Too many Provider output buffers");
        Map<UUID, QIOProviderOutputBuffer> buffers = new LinkedHashMap<>();
        for (int index = 0; index < rawBuffers.tagCount(); index++) {
            QIOProviderOutputBuffer buffer = QIOProviderOutputBuffer.read(rawBuffers.getCompoundTagAt(index));
            if (buffers.putIfAbsent(buffer.bufferId(), buffer) != null)
                throw new IllegalArgumentException("Duplicate Provider output buffer");
        }
        return new Operation(tag.getUniqueId("id"), tag.getInteger("step"), tag.getLong("index"),
                MachineOperationToken.read(tag.getCompoundTag("token")), ports, transfers, buffers);
    }

    private static BigInteger nonNegative(BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) throw new IllegalArgumentException("Negative Provider baseline");
        return value;
    }
}

