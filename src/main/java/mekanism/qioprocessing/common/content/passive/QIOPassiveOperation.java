package mekanism.qioprocessing.common.content.passive;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Persistent standalone operation created by a QIO automatic-processing upgrade. */
public final class QIOPassiveOperation {

    public static final int SCHEMA_VERSION = 4;
    private static final int MAX_RESOURCES = 4_096;

    public enum State {
        CLAIM_PREPARED,
        CLAIMED,
        CONFIGURING,
        CONSUME_PREPARED,
        RELEASE_PREPARED,
        RESERVED,
        LOADING,
        ACTIVE,
        COLLECTING,
        DELIVERING,
        RETURNING,
        COMPLETED,
        FAILED;

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED;
        }
    }

    private final UUID operationId;
    private final UUID deviceUUID;
    private final UUID claimId;
    private final UUID claimRequestId;
    private final UUID consumeRequestId;
    private final UUID consumeTransferId;
    private final UUID releaseRequestId;
    private final String providerId;
    private final String routeId;
    private final String recipeKey;
    private final long operationCount;
    private final long createdAtTick;
    private final long expectedContentsRevision;
    private final long expectedClaimRevision;
    private final Map<PortableResourceDescriptor, UUID> resourceBindings;
    private final Map<PortableResourceDescriptor, Long> requiredInputs;
    private final Map<PortableResourceDescriptor, Long> claimedInputs = new LinkedHashMap<>();
    private final Map<PortableResourceDescriptor, BigInteger> consumeBaselines = new LinkedHashMap<>();
    private final Map<PortableResourceDescriptor, Long> inputBuffer = new LinkedHashMap<>();
    private final Map<PortableResourceDescriptor, Long> outputBuffer = new LinkedHashMap<>();
    private State state = State.CLAIM_PREPARED;
    private long observedClaimRevision = -1;
    @Nullable
    private UUID leaseId;
    private long laneId = -1;
    private long runtimeRevision;
    @Nullable
    private String diagnostic;

    public QIOPassiveOperation(@Nonnull UUID operationId, @Nonnull UUID deviceUUID,
          @Nonnull String providerId, @Nonnull String routeId, @Nonnull String recipeKey,
          long createdAtTick, long expectedContentsRevision, long expectedClaimRevision,
          @Nonnull Map<PortableResourceDescriptor, UUID> resourceBindings,
          @Nonnull Map<PortableResourceDescriptor, Long> requiredInputs) {
        this(operationId, deviceUUID, providerId, routeId, recipeKey, 1, createdAtTick,
              expectedContentsRevision, expectedClaimRevision, resourceBindings,
              requiredInputs);
    }

    public QIOPassiveOperation(@Nonnull UUID operationId, @Nonnull UUID deviceUUID,
          @Nonnull String providerId, @Nonnull String routeId, @Nonnull String recipeKey,
          long operationCount, long createdAtTick, long expectedContentsRevision,
          long expectedClaimRevision,
          @Nonnull Map<PortableResourceDescriptor, UUID> resourceBindings,
          @Nonnull Map<PortableResourceDescriptor, Long> requiredInputs) {
        this(operationId, deviceUUID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
              UUID.randomUUID(), UUID.randomUUID(), providerId, routeId, recipeKey,
              operationCount, createdAtTick, expectedContentsRevision,
              expectedClaimRevision, resourceBindings, requiredInputs);
    }

    private QIOPassiveOperation(UUID operationId, UUID deviceUUID, UUID claimId,
          UUID claimRequestId, UUID consumeRequestId, UUID consumeTransferId,
          UUID releaseRequestId,
          String providerId, String routeId, String recipeKey, long operationCount,
          long createdAtTick,
          long expectedContentsRevision, long expectedClaimRevision,
          Map<PortableResourceDescriptor, UUID> resourceBindings,
          Map<PortableResourceDescriptor, Long> requiredInputs) {
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.deviceUUID = Objects.requireNonNull(deviceUUID, "deviceUUID");
        this.claimId = Objects.requireNonNull(claimId, "claimId");
        this.claimRequestId = Objects.requireNonNull(claimRequestId, "claimRequestId");
        this.consumeRequestId = Objects.requireNonNull(consumeRequestId, "consumeRequestId");
        this.consumeTransferId = Objects.requireNonNull(consumeTransferId, "consumeTransferId");
        this.releaseRequestId = Objects.requireNonNull(releaseRequestId, "releaseRequestId");
        this.providerId = checkedText(providerId, "providerId");
        this.routeId = checkedText(routeId, "routeId");
        this.recipeKey = checkedText(recipeKey, "recipeKey");
        if (operationCount <= 0) {
            throw new IllegalArgumentException("operationCount must be positive");
        }
        this.operationCount = operationCount;
        this.createdAtTick = QIOProcessingNbt.requireNonNegative(createdAtTick, "createdAtTick");
        this.expectedContentsRevision = QIOProcessingNbt.requireNonNegative(
              expectedContentsRevision, "expectedContentsRevision");
        this.expectedClaimRevision = QIOProcessingNbt.requireNonNegative(expectedClaimRevision,
              "expectedClaimRevision");
        this.requiredInputs = checkedAmounts(requiredInputs, false, "passive inputs");
        this.resourceBindings = checkedBindings(resourceBindings, this.requiredInputs);
    }

    @Nonnull
    public UUID getOperationId() {
        return operationId;
    }

    @Nonnull
    public UUID getDeviceUUID() {
        return deviceUUID;
    }

    @Nonnull
    public UUID getClaimId() {
        return claimId;
    }

    @Nonnull
    public UUID getClaimRequestId() {
        return claimRequestId;
    }

    @Nonnull
    public UUID getConsumeRequestId() {
        return consumeRequestId;
    }

    @Nonnull
    public UUID getConsumeTransferId() {
        return consumeTransferId;
    }

    @Nonnull
    public UUID getReleaseRequestId() {
        return releaseRequestId;
    }

    @Nonnull
    public String getProviderId() {
        return providerId;
    }

    @Nonnull
    public String getRouteId() {
        return routeId;
    }

    @Nonnull
    public String getRecipeKey() {
        return recipeKey;
    }

    public long getOperationCount() {
        return operationCount;
    }

    public long getCreatedAtTick() {
        return createdAtTick;
    }

    public long getExpectedContentsRevision() {
        return expectedContentsRevision;
    }

    public long getExpectedClaimRevision() {
        return expectedClaimRevision;
    }

    public long getObservedClaimRevision() {
        return observedClaimRevision;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, UUID> getResourceBindings() {
        return resourceBindings;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getRequiredInputs() {
        return requiredInputs;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getClaimedInputs() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(claimedInputs));
    }

    @Nonnull
    public Map<PortableResourceDescriptor, BigInteger> getConsumeBaselines() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(consumeBaselines));
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getInputBuffer() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(inputBuffer));
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getOutputBuffer() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(outputBuffer));
    }

    @Nonnull
    public State getState() {
        return state;
    }

    @Nullable
    public UUID getLeaseId() {
        return leaseId;
    }

    public long getLaneId() {
        return laneId;
    }

    public long getRuntimeRevision() {
        return runtimeRevision;
    }

    @Nullable
    public String getDiagnostic() {
        return diagnostic;
    }

    public void markClaimed(long claimRevision) {
        requireState(State.CLAIM_PREPARED);
        observedClaimRevision = QIOProcessingNbt.requireNonNegative(claimRevision,
              "observedClaimRevision");
        claimedInputs.clear();
        claimedInputs.putAll(requiredInputs);
        state = State.CLAIMED;
        changed();
    }

    public void markPartialClaim(@Nonnull Map<PortableResourceDescriptor, Long> amounts,
          long claimRevision, @Nonnull String reason) {
        requireState(State.CLAIM_PREPARED);
        observedClaimRevision = QIOProcessingNbt.requireNonNegative(claimRevision,
              "observedClaimRevision");
        claimedInputs.clear();
        claimedInputs.putAll(checkedAmounts(amounts, true, "partial passive claim"));
        fail(reason);
    }

    public void prepareConsume(@Nonnull Map<PortableResourceDescriptor, BigInteger> baselines) {
        requireState(State.CONFIGURING);
        Map<PortableResourceDescriptor, BigInteger> checked = QIOProcessingNbt.copyExactAmounts(
              baselines, false, "passive consume baselines");
        if (!checked.keySet().equals(requiredInputs.keySet())) {
            throw new IllegalArgumentException("Passive consume baselines do not match required inputs");
        }
        for (Map.Entry<PortableResourceDescriptor, Long> input : requiredInputs.entrySet()) {
            if (checked.get(input.getKey()).compareTo(BigInteger.valueOf(input.getValue())) < 0) {
                throw new IllegalArgumentException(
                      "A passive consume baseline cannot be smaller than its input amount");
            }
        }
        consumeBaselines.clear();
        consumeBaselines.putAll(checked);
        state = State.CONSUME_PREPARED;
        changed();
    }

    public void markReserved(long claimRevision) {
        requireState(State.CONSUME_PREPARED);
        observedClaimRevision = QIOProcessingNbt.requireNonNegative(claimRevision,
              "observedClaimRevision");
        claimedInputs.clear();
        inputBuffer.clear();
        inputBuffer.putAll(requiredInputs);
        state = leaseId == null ? State.RESERVED : State.LOADING;
        changed();
    }

    public void markConfiguring(@Nonnull UUID leaseId, long laneId) {
        requireState(State.CLAIMED);
        this.leaseId = Objects.requireNonNull(leaseId, "leaseId");
        this.laneId = QIOProcessingNbt.requireNonNegative(laneId, "laneId");
        state = State.CONFIGURING;
        changed();
    }

    public void markClaimReleased() {
        requireState(State.RELEASE_PREPARED);
        claimedInputs.clear();
        state = State.FAILED;
        changed();
    }

    public void markLoading(@Nonnull UUID leaseId, long laneId) {
        requireState(State.RESERVED);
        this.leaseId = Objects.requireNonNull(leaseId, "leaseId");
        this.laneId = QIOProcessingNbt.requireNonNegative(laneId, "laneId");
        state = State.LOADING;
        changed();
    }

    public void markActive() {
        requireState(State.LOADING);
        inputBuffer.clear();
        state = State.ACTIVE;
        changed();
    }

    public void abandonLoading(@Nonnull String reason) {
        requireState(State.LOADING);
        if (inputBuffer.isEmpty()) {
            throw new IllegalStateException("A passive loading operation has no input to return");
        }
        leaseId = null;
        laneId = -1;
        fail(reason);
    }

    public void markCollecting(@Nonnull Map<PortableResourceDescriptor, Long> outputs) {
        requireState(State.ACTIVE);
        outputBuffer.clear();
        outputBuffer.putAll(checkedAmounts(outputs, false, "passive outputs"));
        state = State.COLLECTING;
        changed();
    }

    public void markDelivering() {
        if (state != State.COLLECTING && state != State.DELIVERING) {
            throw new IllegalStateException("Passive operation cannot deliver from " + state);
        }
        state = State.DELIVERING;
        changed();
    }

    public void debitDelivered(@Nonnull PortableResourceDescriptor resource, long amount) {
        if (state != State.DELIVERING || amount <= 0 ||
              outputBuffer.getOrDefault(resource, 0L) < amount) {
            throw new IllegalStateException("Invalid passive output delivery debit");
        }
        long remaining = outputBuffer.get(resource) - amount;
        if (remaining == 0) {
            outputBuffer.remove(resource);
        } else {
            outputBuffer.put(resource, remaining);
        }
        if (outputBuffer.isEmpty()) {
            state = State.COMPLETED;
        }
        changed();
    }

    public void debitReturned(@Nonnull PortableResourceDescriptor resource, long amount) {
        requireState(State.RETURNING);
        Map<PortableResourceDescriptor, Long> buffer = outputBuffer.containsKey(resource) ?
              outputBuffer : inputBuffer;
        long current = buffer.getOrDefault(resource, 0L);
        if (amount <= 0 || current < amount) {
            throw new IllegalStateException("Invalid passive resource return debit");
        }
        long remaining = current - amount;
        if (remaining == 0) {
            buffer.remove(resource);
        } else {
            buffer.put(resource, remaining);
        }
        if (inputBuffer.isEmpty() && outputBuffer.isEmpty()) {
            state = State.FAILED;
        }
        changed();
    }

    public void fail(@Nonnull String reason) {
        diagnostic = checkedText(reason, "diagnostic");
        if (!claimedInputs.isEmpty()) {
            state = State.RELEASE_PREPARED;
        } else if (!inputBuffer.isEmpty() || !outputBuffer.isEmpty()) {
            state = State.RETURNING;
        } else {
            state = State.FAILED;
        }
        changed();
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "operationId", operationId);
        QIOProcessingNbt.writeUUID(data, "deviceUUID", deviceUUID);
        QIOProcessingNbt.writeUUID(data, "claimId", claimId);
        QIOProcessingNbt.writeUUID(data, "claimRequestId", claimRequestId);
        QIOProcessingNbt.writeUUID(data, "consumeRequestId", consumeRequestId);
        QIOProcessingNbt.writeUUID(data, "consumeTransferId", consumeTransferId);
        QIOProcessingNbt.writeUUID(data, "releaseRequestId", releaseRequestId);
        data.setString("providerId", providerId);
        data.setString("routeId", routeId);
        data.setString("recipeKey", recipeKey);
        data.setLong("operationCount", operationCount);
        data.setLong("createdAtTick", createdAtTick);
        data.setLong("expectedContentsRevision", expectedContentsRevision);
        data.setLong("expectedClaimRevision", expectedClaimRevision);
        data.setLong("observedClaimRevision", observedClaimRevision);
        data.setString("state", state.name());
        data.setLong("runtimeRevision", runtimeRevision);
        data.setTag("requiredInputs", QIOProcessingNbt.writeAmounts(requiredInputs));
        data.setTag("claimedInputs", QIOProcessingNbt.writeAmounts(claimedInputs));
        data.setTag("consumeBaselines", QIOProcessingNbt.writeExactAmounts(consumeBaselines));
        data.setTag("inputBuffer", QIOProcessingNbt.writeAmounts(inputBuffer));
        data.setTag("outputBuffer", QIOProcessingNbt.writeAmounts(outputBuffer));
        NBTTagList bindings = new NBTTagList();
        resourceBindings.forEach((resource, uuid) -> {
            NBTTagCompound binding = new NBTTagCompound();
            binding.setTag("resource", resource.write());
            QIOProcessingNbt.writeUUID(binding, "qioResourceUUID", uuid);
            bindings.appendTag(binding);
        });
        data.setTag("resourceBindings", bindings);
        if (leaseId != null) {
            QIOProcessingNbt.writeUUID(data, "leaseId", leaseId);
            data.setLong("laneId", laneId);
        }
        if (diagnostic != null) {
            data.setString("diagnostic", diagnostic);
        }
        return data;
    }

    @Nonnull
    public static QIOPassiveOperation read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        int schema = data.getInteger("schema");
        if (schema != 3 && schema != SCHEMA_VERSION) {
            throw new QIOProcessingDataException("Unsupported passive operation schema");
        }
        try {
            Map<PortableResourceDescriptor, UUID> bindings = readBindings(data);
            QIOPassiveOperation operation = new QIOPassiveOperation(
                  QIOProcessingNbt.readUUID(data, "operationId"),
                  QIOProcessingNbt.readUUID(data, "deviceUUID"),
                  QIOProcessingNbt.readUUID(data, "claimId"),
                  QIOProcessingNbt.readUUID(data, "claimRequestId"),
                  QIOProcessingNbt.readUUID(data, "consumeRequestId"),
                  QIOProcessingNbt.readUUID(data, "consumeTransferId"),
                  QIOProcessingNbt.readUUID(data, "releaseRequestId"),
                  data.getString("providerId"), data.getString("routeId"),
                  data.getString("recipeKey"), data.getLong("operationCount"),
                  data.getLong("createdAtTick"),
                  data.getLong("expectedContentsRevision"),
                  data.getLong("expectedClaimRevision"), bindings,
                  QIOProcessingNbt.readAmounts(data, "requiredInputs", MAX_RESOURCES));
            operation.observedClaimRevision = QIOProcessingNbt.requireRevision(
                  data.getLong("observedClaimRevision"), "observedClaimRevision");
            operation.state = QIOProcessingNbt.readEnum(data, "state", State.class);
            operation.runtimeRevision = QIOProcessingNbt.requireNonNegative(
                  data.getLong("runtimeRevision"), "runtimeRevision");
            operation.claimedInputs.putAll(QIOProcessingNbt.readAmounts(data, "claimedInputs",
                  MAX_RESOURCES));
            operation.consumeBaselines.putAll(QIOProcessingNbt.readExactAmounts(data,
                  "consumeBaselines", MAX_RESOURCES));
            operation.inputBuffer.putAll(QIOProcessingNbt.readAmounts(data, "inputBuffer",
                  MAX_RESOURCES));
            operation.outputBuffer.putAll(QIOProcessingNbt.readAmounts(data, "outputBuffer",
                  MAX_RESOURCES));
            operation.leaseId = data.hasKey("leaseId", NBT.TAG_STRING) ?
                  QIOProcessingNbt.readUUID(data, "leaseId") : null;
            operation.laneId = operation.leaseId == null ? -1 :
                  QIOProcessingNbt.requireNonNegative(data.getLong("laneId"), "laneId");
            operation.diagnostic = data.hasKey("diagnostic", NBT.TAG_STRING) ?
                  checkedText(data.getString("diagnostic"), "diagnostic") : null;
            operation.validate();
            return operation;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid passive operation", e);
        }
    }

    private void validate() {
        boolean expectsClaim = state == State.CLAIMED || state == State.CONFIGURING ||
              state == State.CONSUME_PREPARED || state == State.RELEASE_PREPARED;
        boolean expectsInput = state == State.RESERVED || state == State.LOADING ||
              state == State.RETURNING && !inputBuffer.isEmpty();
        boolean expectsOutput = state == State.COLLECTING || state == State.DELIVERING ||
              state == State.RETURNING && !outputBuffer.isEmpty();
        boolean requiresConsumeBaseline = state == State.CONSUME_PREPARED ||
              state == State.RESERVED || state == State.LOADING || state == State.ACTIVE ||
              state == State.COLLECTING || state == State.DELIVERING ||
              state == State.RETURNING || state == State.COMPLETED;
        if (expectsClaim != !claimedInputs.isEmpty() ||
              !requiredInputs.keySet().containsAll(claimedInputs.keySet()) ||
              claimedInputs.entrySet().stream().anyMatch(entry ->
                    entry.getValue() > requiredInputs.get(entry.getKey())) ||
              !consumeBaselines.isEmpty() && (!consumeBaselines.keySet().equals(requiredInputs.keySet()) ||
                    consumeBaselines.entrySet().stream().anyMatch(entry ->
                          entry.getValue().compareTo(BigInteger.valueOf(
                                requiredInputs.get(entry.getKey()))) < 0)) ||
              requiresConsumeBaseline != !consumeBaselines.isEmpty() ||
              expectsInput != !inputBuffer.isEmpty() || expectsOutput != !outputBuffer.isEmpty() ||
              (leaseId == null) != (laneId < 0) || state == State.FAILED && diagnostic == null) {
            throw new IllegalArgumentException("Passive operation state disagrees with its buffers");
        }
    }

    private void requireState(State expected) {
        if (state != expected) {
            throw new IllegalStateException("Passive operation is " + state + ", expected " + expected);
        }
    }

    private void changed() {
        if (runtimeRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("Passive operation runtime revision exhausted");
        }
        runtimeRevision++;
    }

    private static Map<PortableResourceDescriptor, Long> checkedAmounts(
          Map<PortableResourceDescriptor, Long> amounts, boolean allowEmpty, String name) {
        Map<PortableResourceDescriptor, Long> checked = QIOProcessingNbt.copyAmounts(amounts,
              allowEmpty, name);
        if (checked.size() > MAX_RESOURCES) {
            throw new IllegalArgumentException(name + " contains too many resources");
        }
        return checked;
    }

    private static Map<PortableResourceDescriptor, UUID> checkedBindings(
          Map<PortableResourceDescriptor, UUID> bindings,
          Map<PortableResourceDescriptor, Long> required) {
        if (!bindings.keySet().equals(required.keySet())) {
            throw new IllegalArgumentException("Passive resource bindings do not match inputs");
        }
        Map<PortableResourceDescriptor, UUID> copy = new LinkedHashMap<>();
        bindings.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
              copy.put(Objects.requireNonNull(entry.getKey(), "resource"),
                    Objects.requireNonNull(entry.getValue(), "resource UUID")));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<PortableResourceDescriptor, UUID> readBindings(NBTTagCompound data)
          throws QIOProcessingDataException {
        NBTTagList list = data.getTagList("resourceBindings", NBT.TAG_COMPOUND);
        if (list.tagCount() > MAX_RESOURCES) {
            throw new QIOProcessingDataException("Passive operation has too many bindings");
        }
        Map<PortableResourceDescriptor, UUID> bindings = new LinkedHashMap<>();
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound stored = list.getCompoundTagAt(index);
            PortableResourceDescriptor resource = PortableResourceDescriptor.read(
                  stored.getCompoundTag("resource"));
            if (bindings.put(resource, QIOProcessingNbt.readUUID(stored,
                  "qioResourceUUID")) != null) {
                throw new QIOProcessingDataException("Duplicate passive resource binding");
            }
        }
        return bindings;
    }

    private static String checkedText(String value, String name) {
        String checked = Objects.requireNonNull(value, name).trim();
        if (checked.isEmpty() || checked.length() > 512) {
            throw new IllegalArgumentException(name + " has an invalid length");
        }
        return checked;
    }
}
