package mekanism.qioprocessing.common.content.processor;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Persistent state for one actually occupied logical lane of a workbench processor. */
public final class QIOProcessorLaneRuntime {

    public static final int SCHEMA_VERSION = 2;
    private static final int MAX_RESOURCE_ENTRIES = 4_096;
    private static final int MAX_TRANSFER_RECEIPTS = 4_096;
    private static final int MAX_RECEIPT_RESOURCE_ENTRIES = 256;
    private static final int MAX_ROUTE_KEY_LENGTH = 512;

    public enum State {
        LOADING,
        READY,
        PROCESSING,
        OUTPUT_BLOCKED,
        RETURNING,
        SETTLED,
        DATA_ERROR
    }

    public enum TransferDirection {
        INPUT,
        OUTPUT,
        RETURN
    }

    public enum Settlement {
        OUTPUT,
        RETURN
    }

    private final long laneId;
    private final UUID operationId;
    private final UUID jobId;
    private final int planRevision;
    private final String routeKey;
    private final long operationCount;
    private final Map<PortableResourceDescriptor, Long> input = new LinkedHashMap<>();
    private final Map<PortableResourceDescriptor, Long> output = new LinkedHashMap<>();
    private final Map<UUID, TransferReceipt> transferReceipts = new LinkedHashMap<>();
    private State state;
    @Nullable
    private Settlement settlement;
    private long currentTick;
    private long totalTicks;
    private long runtimeRevision;
    private String dataError;
    private transient Runnable dirtyListener;

    private QIOProcessorLaneRuntime(long laneId, UUID operationId, UUID jobId,
          int planRevision, String routeKey, long operationCount) {
        this.laneId = requireLaneId(laneId);
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.planRevision = QIOProcessingNbt.requirePositive(planRevision, "planRevision");
        this.routeKey = requireRouteKey(routeKey);
        if (operationCount <= 0) {
            throw new IllegalArgumentException("QIO processor lane operation count must be positive");
        }
        this.operationCount = operationCount;
        state = State.LOADING;
    }

    @Nonnull
    public static QIOProcessorLaneRuntime loading(long laneId, @Nonnull UUID operationId,
          @Nonnull UUID jobId, int planRevision, @Nonnull String routeKey) {
        return loading(laneId, operationId, jobId, planRevision, routeKey, 1);
    }

    @Nonnull
    public static QIOProcessorLaneRuntime loading(long laneId, @Nonnull UUID operationId,
          @Nonnull UUID jobId, int planRevision, @Nonnull String routeKey,
          long operationCount) {
        return new QIOProcessorLaneRuntime(laneId, operationId, jobId, planRevision, routeKey,
              operationCount);
    }

    public long getLaneId() {
        return laneId;
    }

    @Nonnull
    public UUID getOperationId() {
        return operationId;
    }

    @Nonnull
    public UUID getJobId() {
        return jobId;
    }

    public int getPlanRevision() {
        return planRevision;
    }

    @Nonnull
    public String getRouteKey() {
        return routeKey;
    }

    public long getOperationCount() {
        return operationCount;
    }

    @Nonnull
    public State getState() {
        return state;
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public long getTotalTicks() {
        return totalTicks;
    }

    public long getRuntimeRevision() {
        return runtimeRevision;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getInput() {
        return immutableCopy(input);
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getOutput() {
        return immutableCopy(output);
    }

    public int getTransferReceiptCount() {
        return transferReceipts.size();
    }

    public boolean hasTransferReceipt(@Nonnull UUID transferId) {
        return transferReceipts.containsKey(Objects.requireNonNull(transferId, "transferId"));
    }

    public boolean isSettled() {
        return state == State.SETTLED && input.isEmpty() && output.isEmpty();
    }

    @Nullable
    public Settlement getSettlement() {
        return settlement;
    }

    void setDirtyListener(@Nullable Runnable dirtyListener) {
        this.dirtyListener = dirtyListener;
    }

    /** Credits an idempotent JOB_TO_MACHINE transfer to the lane input buffer. */
    public boolean creditInput(@Nonnull UUID transferId,
          @Nonnull Map<PortableResourceDescriptor, Long> resources) {
        Map<PortableResourceDescriptor, Long> checked = checkedAmounts(resources, false, "lane input");
        TransferReceipt receipt = prepareReceipt(transferId, TransferDirection.INPUT, checked);
        if (receipt == null) {
            return false;
        }
        requireState(State.LOADING);
        validateAddition(input, checked);
        addAmounts(input, checked);
        transferReceipts.put(receipt.transferId, receipt);
        incrementRevision();
        return true;
    }

    public void markReady() {
        requireState(State.LOADING);
        if (input.isEmpty()) {
            throw new IllegalStateException("A QIO processor lane cannot become ready without input");
        }
        state = State.READY;
        incrementRevision();
    }

    public void beginProcessing(long totalTicks) {
        requireState(State.READY);
        if (totalTicks <= 0) {
            throw new IllegalArgumentException("QIO processor total ticks must be positive");
        }
        this.totalTicks = totalTicks;
        currentTick = 0;
        state = State.PROCESSING;
        incrementRevision();
    }

    public long advanceProcessing(long ticks) {
        requireState(State.PROCESSING);
        if (ticks <= 0) {
            throw new IllegalArgumentException("QIO processor progress delta must be positive");
        }
        long remaining = totalTicks - currentTick;
        long advanced = Math.min(remaining, ticks);
        if (advanced > 0) {
            currentTick += advanced;
            incrementRevision();
        }
        return advanced;
    }

    /** Records the actual, server-validated craft result after the input has been consumed. */
    public void completeProcessing(@Nonnull Map<PortableResourceDescriptor, Long> actualOutput) {
        requireState(State.PROCESSING);
        if (currentTick != totalTicks) {
            throw new IllegalStateException("A QIO processor operation cannot complete before its progress is complete");
        }
        Map<PortableResourceDescriptor, Long> checked = checkedAmounts(actualOutput, false,
              "lane output");
        input.clear();
        output.putAll(checked);
        state = State.OUTPUT_BLOCKED;
        incrementRevision();
    }

    public void abortProcessing() {
        requireState(State.PROCESSING);
        currentTick = 0;
        totalTicks = 0;
        state = input.isEmpty() ? State.SETTLED : State.RETURNING;
        if (state == State.SETTLED) {
            settlement = Settlement.RETURN;
        }
        incrementRevision();
    }

    /** Debits an idempotent MACHINE_TO_JOB transfer from the actual output buffer. */
    public boolean debitOutput(@Nonnull UUID transferId,
          @Nonnull Map<PortableResourceDescriptor, Long> resources) {
        Map<PortableResourceDescriptor, Long> checked = checkedAmounts(resources, false, "lane output debit");
        TransferReceipt receipt = prepareReceipt(transferId, TransferDirection.OUTPUT, checked);
        if (receipt == null) {
            return false;
        }
        requireState(State.OUTPUT_BLOCKED);
        validateRemoval(output, checked);
        removeAmounts(output, checked);
        transferReceipts.put(receipt.transferId, receipt);
        if (output.isEmpty()) {
            state = State.SETTLED;
            settlement = Settlement.OUTPUT;
        }
        incrementRevision();
        return true;
    }

    public void beginReturn() {
        if (state != State.LOADING && state != State.READY) {
            throw new IllegalStateException("Only unconsumed QIO processor input can be returned");
        }
        if (input.isEmpty()) {
            state = State.SETTLED;
            settlement = Settlement.RETURN;
        } else {
            state = State.RETURNING;
        }
        incrementRevision();
    }

    /** Debits an idempotent MACHINE_TO_JOB compensation transfer from unconsumed input. */
    public boolean debitReturn(@Nonnull UUID transferId,
          @Nonnull Map<PortableResourceDescriptor, Long> resources) {
        Map<PortableResourceDescriptor, Long> checked = checkedAmounts(resources, false, "lane return debit");
        TransferReceipt receipt = prepareReceipt(transferId, TransferDirection.RETURN, checked);
        if (receipt == null) {
            return false;
        }
        requireState(State.RETURNING);
        validateRemoval(input, checked);
        removeAmounts(input, checked);
        transferReceipts.put(receipt.transferId, receipt);
        if (input.isEmpty()) {
            state = State.SETTLED;
            settlement = Settlement.RETURN;
        }
        incrementRevision();
        return true;
    }

    public void enterDataError(@Nonnull String reason) {
        dataError = requireDiagnostic(reason);
        state = State.DATA_ERROR;
        incrementRevision();
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("laneSchemaVersion", SCHEMA_VERSION);
        data.setLong("laneId", laneId);
        QIOProcessingNbt.writeUUID(data, "operationId", operationId);
        QIOProcessingNbt.writeUUID(data, "jobId", jobId);
        data.setInteger("planRevision", planRevision);
        data.setString("routeKey", routeKey);
        data.setLong("operationCount", operationCount);
        data.setString("state", state.name());
        if (settlement != null) {
            data.setString("settlement", settlement.name());
        }
        data.setLong("currentTick", currentTick);
        data.setLong("totalTicks", totalTicks);
        data.setLong("runtimeRevision", runtimeRevision);
        data.setTag("input", QIOProcessingNbt.writeAmounts(input));
        data.setTag("output", QIOProcessingNbt.writeAmounts(output));
        NBTTagList receipts = new NBTTagList();
        transferReceipts.values().stream()
              .sorted((left, right) -> left.transferId.compareTo(right.transferId))
              .forEach(receipt -> receipts.appendTag(receipt.write()));
        data.setTag("transferReceipts", receipts);
        if (dataError != null) {
            data.setString("dataError", dataError);
        }
        return data;
    }

    @Nonnull
    public static QIOProcessorLaneRuntime read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        if (data.getInteger("laneSchemaVersion") != SCHEMA_VERSION) {
            throw new QIOProcessingDataException("Unsupported QIO processor lane schema " +
                  data.getInteger("laneSchemaVersion"));
        }
        if (!data.hasKey("state", NBT.TAG_STRING) ||
              !data.hasKey("currentTick", NBT.TAG_LONG) ||
              !data.hasKey("totalTicks", NBT.TAG_LONG) ||
              !data.hasKey("runtimeRevision", NBT.TAG_LONG) ||
              !data.hasKey("input", NBT.TAG_LIST) ||
              !data.hasKey("output", NBT.TAG_LIST) ||
              !data.hasKey("transferReceipts", NBT.TAG_LIST)) {
            throw new QIOProcessingDataException(
                  "QIO processor lane is missing current-schema fields");
        }
        try {
            QIOProcessorLaneRuntime lane = new QIOProcessorLaneRuntime(data.getLong("laneId"),
                  QIOProcessingNbt.readUUID(data, "operationId"),
                  QIOProcessingNbt.readUUID(data, "jobId"), data.getInteger("planRevision"),
                  data.getString("routeKey"), data.getLong("operationCount"));
            lane.state = QIOProcessingNbt.readEnum(data, "state", State.class);
            lane.currentTick = QIOProcessingNbt.requireNonNegative(data.getLong("currentTick"),
                  "currentTick");
            lane.totalTicks = QIOProcessingNbt.requireNonNegative(data.getLong("totalTicks"),
                  "totalTicks");
            lane.runtimeRevision = QIOProcessingNbt.requireNonNegative(
                  data.getLong("runtimeRevision"), "runtimeRevision");
            lane.input.putAll(QIOProcessingNbt.readAmounts(data, "input", MAX_RESOURCE_ENTRIES));
            lane.output.putAll(QIOProcessingNbt.readAmounts(data, "output", MAX_RESOURCE_ENTRIES));
            NBTTagList receipts = data.getTagList("transferReceipts", NBT.TAG_COMPOUND);
            if (receipts.tagCount() > MAX_TRANSFER_RECEIPTS) {
                throw new QIOProcessingDataException("QIO processor lane contains too many transfer receipts");
            }
            for (int index = 0; index < receipts.tagCount(); index++) {
                TransferReceipt receipt = TransferReceipt.read(receipts.getCompoundTagAt(index));
                if (lane.transferReceipts.put(receipt.transferId, receipt) != null) {
                    throw new QIOProcessingDataException("Duplicate QIO processor lane transfer receipt");
                }
            }
            lane.settlement = data.hasKey("settlement", NBT.TAG_STRING) ?
                  QIOProcessingNbt.readEnum(data, "settlement", Settlement.class) : null;
            lane.dataError = data.hasKey("dataError", NBT.TAG_STRING) ?
                  requireDiagnostic(data.getString("dataError")) : null;
            lane.validateState();
            return lane;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO processor lane runtime", e);
        }
    }

    @Nullable
    private TransferReceipt prepareReceipt(UUID transferId, TransferDirection direction,
          Map<PortableResourceDescriptor, Long> resources) {
        Objects.requireNonNull(transferId, "transferId");
        TransferReceipt existing = transferReceipts.get(transferId);
        if (existing != null) {
            if (existing.direction != direction || !existing.resources.equals(resources)) {
                throw new IllegalStateException("QIO processor transfer id was reused with different contents");
            }
            return null;
        }
        if (transferReceipts.size() >= MAX_TRANSFER_RECEIPTS) {
            throw new IllegalStateException("QIO processor lane transfer receipt limit reached");
        }
        return new TransferReceipt(transferId, direction, resources);
    }

    private void validateState() throws QIOProcessingDataException {
        if (currentTick > totalTicks) {
            throw new QIOProcessingDataException("QIO processor lane progress exceeds its total");
        }
        boolean valid = switch (state) {
            case LOADING -> output.isEmpty() && currentTick == 0 && totalTicks == 0;
            case READY -> !input.isEmpty() && output.isEmpty() && currentTick == 0 && totalTicks == 0;
            case PROCESSING -> !input.isEmpty() && output.isEmpty() && totalTicks > 0;
            case OUTPUT_BLOCKED -> input.isEmpty() && !output.isEmpty() && totalTicks > 0 && currentTick == totalTicks;
            case RETURNING -> !input.isEmpty() && output.isEmpty();
            case SETTLED -> input.isEmpty() && output.isEmpty();
            case DATA_ERROR -> dataError != null;
        };
        if (!valid || (state == State.SETTLED) != (settlement != null) ||
              (state != State.DATA_ERROR && dataError != null)) {
            throw new QIOProcessingDataException("QIO processor lane state does not match its buffers");
        }
    }

    private void requireState(State expected) {
        if (state != expected) {
            throw new IllegalStateException("QIO processor lane is " + state + ", expected " + expected);
        }
    }

    private void incrementRevision() {
        if (runtimeRevision == Long.MAX_VALUE) {
            dataError = "QIO processor lane runtime revision exhausted";
            state = State.DATA_ERROR;
            if (dirtyListener != null) {
                dirtyListener.run();
            }
            return;
        }
        runtimeRevision++;
        if (dirtyListener != null) {
            dirtyListener.run();
        }
    }

    private static void addAmounts(Map<PortableResourceDescriptor, Long> destination,
          Map<PortableResourceDescriptor, Long> source) {
        for (Map.Entry<PortableResourceDescriptor, Long> entry : source.entrySet()) {
            destination.put(entry.getKey(), Math.addExact(destination.getOrDefault(entry.getKey(), 0L),
                  entry.getValue()));
        }
    }

    private static void validateAddition(Map<PortableResourceDescriptor, Long> destination,
          Map<PortableResourceDescriptor, Long> source) {
        for (Map.Entry<PortableResourceDescriptor, Long> entry : source.entrySet()) {
            Math.addExact(destination.getOrDefault(entry.getKey(), 0L), entry.getValue());
        }
    }

    private static void validateRemoval(Map<PortableResourceDescriptor, Long> destination,
          Map<PortableResourceDescriptor, Long> requested) {
        for (Map.Entry<PortableResourceDescriptor, Long> entry : requested.entrySet()) {
            if (destination.getOrDefault(entry.getKey(), 0L) < entry.getValue()) {
                throw new IllegalStateException("QIO processor transfer exceeds its owned lane buffer");
            }
        }
    }

    private static void removeAmounts(Map<PortableResourceDescriptor, Long> destination,
          Map<PortableResourceDescriptor, Long> requested) {
        for (Map.Entry<PortableResourceDescriptor, Long> entry : requested.entrySet()) {
            long remaining = destination.get(entry.getKey()) - entry.getValue();
            if (remaining == 0) {
                destination.remove(entry.getKey());
            } else {
                destination.put(entry.getKey(), remaining);
            }
        }
    }

    private static Map<PortableResourceDescriptor, Long> checkedAmounts(
          Map<PortableResourceDescriptor, Long> amounts, boolean allowEmpty, String name) {
        Map<PortableResourceDescriptor, Long> checked = QIOProcessingNbt.copyAmounts(amounts,
              allowEmpty, name);
        if (checked.size() > MAX_RESOURCE_ENTRIES) {
            throw new IllegalArgumentException(name + " contains too many resource entries");
        }
        return checked;
    }

    private static Map<PortableResourceDescriptor, Long> immutableCopy(
          Map<PortableResourceDescriptor, Long> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static long requireLaneId(long laneId) {
        if (laneId < 0) {
            throw new IllegalArgumentException("QIO processor lane id cannot be negative");
        }
        return laneId;
    }

    private static String requireRouteKey(String routeKey) {
        String checked = Objects.requireNonNull(routeKey, "routeKey").trim();
        if (checked.isEmpty() || checked.length() > MAX_ROUTE_KEY_LENGTH) {
            throw new IllegalArgumentException("QIO processor route key must contain 1.." +
                  MAX_ROUTE_KEY_LENGTH + " characters");
        }
        return checked;
    }

    private static String requireDiagnostic(String reason) {
        String checked = Objects.requireNonNull(reason, "reason").trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("QIO processor data error cannot be empty");
        }
        return checked.substring(0, Math.min(512, checked.length()));
    }

    private static final class TransferReceipt {

        private final UUID transferId;
        private final TransferDirection direction;
        private final Map<PortableResourceDescriptor, Long> resources;

        private TransferReceipt(UUID transferId, TransferDirection direction,
              Map<PortableResourceDescriptor, Long> resources) {
            this.transferId = Objects.requireNonNull(transferId, "transferId");
            this.direction = Objects.requireNonNull(direction, "direction");
            this.resources = checkedAmounts(resources, false, "lane transfer receipt");
            if (this.resources.size() > MAX_RECEIPT_RESOURCE_ENTRIES) {
                throw new IllegalArgumentException("QIO processor transfer receipt contains too many resources");
            }
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            QIOProcessingNbt.writeUUID(data, "transferId", transferId);
            data.setString("direction", direction.name());
            data.setTag("resources", QIOProcessingNbt.writeAmounts(resources));
            return data;
        }

        private static TransferReceipt read(NBTTagCompound data) throws QIOProcessingDataException {
            return new TransferReceipt(QIOProcessingNbt.readUUID(data, "transferId"),
                  QIOProcessingNbt.readEnum(data, "direction", TransferDirection.class),
                  QIOProcessingNbt.readAmounts(data, "resources", MAX_RECEIPT_RESOURCE_ENTRIES));
        }
    }
}
