package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.common.processor.QIOProcessorAllocationRecord;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.math.BigInteger;
import java.util.*;

/**
 * Durable progress and replay inputs for one workbench execution.
 */
public final class QIOWorkbenchExecutionState {
    public static final int VERSION = 1;
    private static final int MAX_INPUTS = 4096;
    private static final int MAX_OUTPUTS = 4096;

    public enum Status {READY, IN_FLIGHT, COMPLETED, FAILED, NEEDS_RECONCILIATION}

    public enum InputMode {CLAIM, EXTRACT}

    public static final class Input {
        private final UUID resourceId;
        private final RecipeItemKey item;
        private final long amount;
        private final BigInteger expectedStoredAmount;
        private final InputMode mode;

        public Input(UUID resourceId, RecipeItemKey item, long amount, BigInteger expectedStoredAmount,
                     InputMode mode) {
            this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
            this.item = Objects.requireNonNull(item, "item");
            if (amount <= 0) throw new IllegalArgumentException("Execution input amount must be positive");
            this.amount = amount;
            this.expectedStoredAmount = requireNonNegative(expectedStoredAmount, "expectedStoredAmount");
            if (this.expectedStoredAmount.compareTo(BigInteger.valueOf(amount)) < 0) {
                throw new IllegalArgumentException("Execution input baseline is smaller than its amount");
            }
            this.mode = Objects.requireNonNull(mode, "mode");
        }

        public UUID resourceId() {
            return resourceId;
        }

        public RecipeItemKey item() {
            return item;
        }

        public long amount() {
            return amount;
        }

        public BigInteger expectedStoredAmount() {
            return expectedStoredAmount;
        }

        public InputMode mode() {
            return mode;
        }
    }

    public static final class Output {
        private final RecipeItemKey item;
        private final long amount;
        private final BigInteger expectedStoredAmount;

        public Output(RecipeItemKey item, long amount, BigInteger expectedStoredAmount) {
            this.item = Objects.requireNonNull(item, "item");
            if (amount <= 0) throw new IllegalArgumentException("Execution output amount must be positive");
            this.amount = amount;
            this.expectedStoredAmount = requireNonNegative(expectedStoredAmount, "expectedStoredAmount");
        }

        public RecipeItemKey item() {
            return item;
        }

        public long amount() {
            return amount;
        }

        public BigInteger expectedStoredAmount() {
            return expectedStoredAmount;
        }
    }

    public static final class Operation {
        private final UUID operationId;
        private final int stepIndex;
        private final long operationIndex;
        private final long expectedContentsRevision;
        private final long expectedClaimRevision;
        private final Map<UUID, Input> inputs;
        private final Map<RecipeItemKey, Output> outputs;
        private final QIOProcessorAllocationRecord processorAllocation;

        public Operation(UUID operationId, int stepIndex, long operationIndex,
                         long expectedContentsRevision, long expectedClaimRevision,
                         Map<UUID, Input> inputs, Map<RecipeItemKey, Output> outputs) {
            this(operationId, stepIndex, operationIndex, expectedContentsRevision, expectedClaimRevision,
                    inputs, outputs, null);
        }

        public Operation(UUID operationId, int stepIndex, long operationIndex,
                         long expectedContentsRevision, long expectedClaimRevision,
                         Map<UUID, Input> inputs, Map<RecipeItemKey, Output> outputs,
                         QIOProcessorAllocationRecord processorAllocation) {
            this.operationId = Objects.requireNonNull(operationId, "operationId");
            if (stepIndex < 0 || operationIndex < 0) throw new IllegalArgumentException("Invalid operation position");
            if (expectedContentsRevision < 0 || expectedClaimRevision < 0)
                throw new IllegalArgumentException("Invalid operation revisions");
            this.stepIndex = stepIndex;
            this.operationIndex = operationIndex;
            this.expectedContentsRevision = expectedContentsRevision;
            this.expectedClaimRevision = expectedClaimRevision;
            this.inputs = immutableInputs(inputs);
            this.outputs = immutableOutputs(outputs);
            if (processorAllocation != null && !operationId.equals(processorAllocation.operationId())) {
                throw new IllegalArgumentException("Execution Processor allocation operation mismatch");
            }
            this.processorAllocation = processorAllocation;
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

        public long expectedContentsRevision() {
            return expectedContentsRevision;
        }

        public long expectedClaimRevision() {
            return expectedClaimRevision;
        }

        public Map<UUID, Input> inputs() {
            return inputs;
        }

        public Map<RecipeItemKey, Output> outputs() {
            return outputs;
        }

        public QIOProcessorAllocationRecord processorAllocation() {
            return processorAllocation;
        }

        public Operation withProcessorAllocation(QIOProcessorAllocationRecord allocation) {
            return new Operation(operationId, stepIndex, operationIndex, expectedContentsRevision,
                    expectedClaimRevision, inputs, outputs, allocation);
        }
    }

    private final UUID orderId;
    private final Status status;
    private final int nextStep;
    private final long nextOperation;
    private final Operation operation;

    private QIOWorkbenchExecutionState(UUID orderId, Status status, int nextStep, long nextOperation,
                                       Operation operation) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.status = Objects.requireNonNull(status, "status");
        if (nextStep < 0 || nextOperation < 0) throw new IllegalArgumentException("Invalid execution cursor");
        this.nextStep = nextStep;
        this.nextOperation = nextOperation;
        if (status == Status.IN_FLIGHT && operation == null)
            throw new IllegalArgumentException("In-flight execution requires an operation");
        if (status != Status.IN_FLIGHT && operation != null)
            throw new IllegalArgumentException("Only in-flight execution may have an operation");
        this.operation = operation;
    }

    public static QIOWorkbenchExecutionState ready(UUID orderId) {
        return new QIOWorkbenchExecutionState(orderId, Status.READY, 0, 0, null);
    }

    public QIOWorkbenchExecutionState inFlight(Operation operation) {
        return new QIOWorkbenchExecutionState(orderId, Status.IN_FLIGHT, nextStep, nextOperation, operation);
    }

    public QIOWorkbenchExecutionState advance(int nextStep, long nextOperation) {
        return new QIOWorkbenchExecutionState(orderId, Status.READY, nextStep, nextOperation, null);
    }

    public QIOWorkbenchExecutionState completed() {
        return new QIOWorkbenchExecutionState(orderId, Status.COMPLETED, nextStep, nextOperation, null);
    }

    public QIOWorkbenchExecutionState failed() {
        return new QIOWorkbenchExecutionState(orderId, Status.FAILED, nextStep, nextOperation, null);
    }

    public QIOWorkbenchExecutionState needsReconciliation() {
        return new QIOWorkbenchExecutionState(orderId, Status.NEEDS_RECONCILIATION, nextStep, nextOperation, null);
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
        tag.setString("format", "mekanismqioprocessing:workbench_execution_state");
        tag.setInteger("version", VERSION);
        tag.setUniqueId("order", orderId);
        tag.setString("status", status.name());
        tag.setInteger("nextStep", nextStep);
        tag.setLong("nextOperation", nextOperation);
        if (operation != null) tag.setTag("operation", writeOperation(operation));
        return tag;
    }

    public static QIOWorkbenchExecutionState read(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("format", 8)
                || !"mekanismqioprocessing:workbench_execution_state".equals(tag.getString("format"))
                || !tag.hasKey("version", 3) || tag.getInteger("version") != VERSION
                || !tag.hasUniqueId("order") || !tag.hasKey("status", 8)
                || !tag.hasKey("nextStep", 3) || !tag.hasKey("nextOperation", 4)) {
            throw new IllegalArgumentException("Invalid execution state header");
        }
        Status status = Status.valueOf(tag.getString("status"));
        Operation operation = tag.hasKey("operation", 10) ? readOperation(tag.getCompoundTag("operation")) : null;
        return new QIOWorkbenchExecutionState(tag.getUniqueId("order"), status,
                tag.getInteger("nextStep"), tag.getLong("nextOperation"), operation);
    }

    private static NBTTagCompound writeOperation(Operation operation) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("id", operation.operationId());
        tag.setInteger("step", operation.stepIndex());
        tag.setLong("index", operation.operationIndex());
        tag.setLong("contentsRevision", operation.expectedContentsRevision());
        tag.setLong("claimRevision", operation.expectedClaimRevision());
        if (operation.processorAllocation() != null) {
            tag.setTag("processorAllocation", operation.processorAllocation().write());
        }
        NBTTagList inputs = new NBTTagList();
        for (Input input : operation.inputs().values()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setUniqueId("resource", input.resourceId());
            row.setTag("item", input.item().displayNBT());
            row.setLong("amount", input.amount());
            row.setString("baseline", input.expectedStoredAmount().toString());
            row.setString("mode", input.mode().name());
            inputs.appendTag(row);
        }
        tag.setTag("inputs", inputs);
        NBTTagList outputs = new NBTTagList();
        for (Output output : operation.outputs().values()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setTag("item", output.item().displayNBT());
            row.setLong("amount", output.amount());
            row.setString("baseline", output.expectedStoredAmount().toString());
            outputs.appendTag(row);
        }
        tag.setTag("outputs", outputs);
        return tag;
    }

    private static Operation readOperation(NBTTagCompound tag) {
        if (!tag.hasUniqueId("id") || !tag.hasKey("step", 3) || !tag.hasKey("index", 4)
                || !tag.hasKey("contentsRevision", 4) || !tag.hasKey("claimRevision", 4)
                || !tag.hasKey("inputs", 9) || !tag.hasKey("outputs", 9)) {
            throw new IllegalArgumentException("Invalid execution operation");
        }
        NBTTagList inputTags = tag.getTagList("inputs", 10), outputTags = tag.getTagList("outputs", 10);
        if (inputTags.tagCount() > MAX_INPUTS || outputTags.tagCount() > MAX_OUTPUTS) {
            throw new IllegalArgumentException("Execution operation quota exceeded");
        }
        Map<UUID, Input> inputs = new LinkedHashMap<>();
        for (int index = 0; index < inputTags.tagCount(); index++) {
            NBTTagCompound row = inputTags.getCompoundTagAt(index);
            if (!row.hasUniqueId("resource") || !row.hasKey("item", 10) || !row.hasKey("amount", 4)
                    || !row.hasKey("baseline", 8) || !row.hasKey("mode", 8))
                throw new IllegalArgumentException("Invalid execution input");
            UUID resource = row.getUniqueId("resource");
            Input input = new Input(resource, RecipeItemKey.capture(new ItemStack(row.getCompoundTag("item"))),
                    row.getLong("amount"), decimal(row.getString("baseline")), InputMode.valueOf(row.getString("mode")));
            if (inputs.putIfAbsent(resource, input) != null)
                throw new IllegalArgumentException("Duplicate execution input resource");
        }
        Map<RecipeItemKey, Output> outputs = new LinkedHashMap<>();
        for (int index = 0; index < outputTags.tagCount(); index++) {
            NBTTagCompound row = outputTags.getCompoundTagAt(index);
            if (!row.hasKey("item", 10) || !row.hasKey("amount", 4) || !row.hasKey("baseline", 8))
                throw new IllegalArgumentException("Invalid execution output");
            Output output = new Output(RecipeItemKey.capture(new ItemStack(row.getCompoundTag("item"))),
                    row.getLong("amount"), decimal(row.getString("baseline")));
            if (outputs.putIfAbsent(output.item(), output) != null)
                throw new IllegalArgumentException("Duplicate execution output item");
        }
        QIOProcessorAllocationRecord allocation = tag.hasKey("processorAllocation", 10)
                ? QIOProcessorAllocationRecord.read(tag.getCompoundTag("processorAllocation")) : null;
        return new Operation(tag.getUniqueId("id"), tag.getInteger("step"), tag.getLong("index"),
                tag.getLong("contentsRevision"), tag.getLong("claimRevision"), inputs, outputs, allocation);
    }

    private static BigInteger decimal(String value) {
        try {
            BigInteger result = new BigInteger(value);
            return requireNonNegative(result, "decimal");
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid execution decimal", error);
        }
    }

    private static BigInteger requireNonNegative(BigInteger value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) throw new IllegalArgumentException(name + " cannot be negative");
        return value;
    }

    private static Map<UUID, Input> immutableInputs(Map<UUID, Input> values) {
        Objects.requireNonNull(values, "inputs");
        if (values.size() > MAX_INPUTS) throw new IllegalArgumentException("Execution input quota exceeded");
        Map<UUID, Input> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (!Objects.requireNonNull(key).equals(Objects.requireNonNull(value).resourceId()))
                throw new IllegalArgumentException("Execution input key mismatch");
            if (copy.putIfAbsent(key, value) != null)
                throw new IllegalArgumentException("Duplicate execution input resource");
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<RecipeItemKey, Output> immutableOutputs(Map<RecipeItemKey, Output> values) {
        Objects.requireNonNull(values, "outputs");
        if (values.size() > MAX_OUTPUTS) throw new IllegalArgumentException("Execution output quota exceeded");
        Map<RecipeItemKey, Output> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (!Objects.requireNonNull(key).equals(Objects.requireNonNull(value).item()))
                throw new IllegalArgumentException("Execution output key mismatch");
            if (copy.putIfAbsent(key, value) != null)
                throw new IllegalArgumentException("Duplicate execution output item");
        });
        return Collections.unmodifiableMap(copy);
    }
}

