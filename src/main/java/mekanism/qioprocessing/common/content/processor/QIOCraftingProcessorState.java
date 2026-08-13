package mekanism.qioprocessing.common.content.processor;

import mekanism.qioprocessing.api.processor.QIOCraftingProcessorDefinition;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorRegistry;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

/** Durable identity and sparse occupied-lane state shared by all processor tiers. */
public final class QIOCraftingProcessorState {

    public static final int SCHEMA_VERSION = 2;

    public enum State {
        ACTIVE,
        UNRESOLVED_DEFINITION,
        DATA_ERROR
    }

    private UUID processorUUID;
    private ResourceLocation hostId;
    private ResourceLocation definitionId;
    private String definitionSignature;
    private State state;
    private String diagnostic;
    private long nextLaneCandidate;
    private long runtimeRevision;
    private final Map<Long, QIOProcessorLaneRuntime> activeLanes = new LinkedHashMap<>();
    private final Set<UUID> persistedSettledOperations = new LinkedHashSet<>();
    private NBTTagCompound quarantinedData;
    private transient Runnable dirtyListener;

    private QIOCraftingProcessorState(UUID processorUUID, ResourceLocation hostId,
          ResourceLocation definitionId, String definitionSignature) {
        this.processorUUID = Objects.requireNonNull(processorUUID, "processorUUID");
        this.hostId = Objects.requireNonNull(hostId, "hostId");
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.definitionSignature = requireSignature(definitionSignature);
        state = State.ACTIVE;
    }

    @Nonnull
    public static QIOCraftingProcessorState create(@Nonnull ResourceLocation hostId,
          @Nonnull QIOCraftingProcessorDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new QIOCraftingProcessorState(UUID.randomUUID(), hostId, definition.getId(),
              definition.getSignature());
    }

    @Nonnull
    public UUID getProcessorUUID() {
        return processorUUID;
    }

    @Nonnull
    public ResourceLocation getHostId() {
        return hostId;
    }

    @Nonnull
    public ResourceLocation getDefinitionId() {
        return definitionId;
    }

    @Nonnull
    public String getDefinitionSignature() {
        return definitionSignature;
    }

    @Nonnull
    public State getState() {
        return state;
    }

    @Nullable
    public String getDiagnostic() {
        return diagnostic;
    }

    public long getRuntimeRevision() {
        return runtimeRevision;
    }

    public void setDirtyListener(@Nullable Runnable dirtyListener) {
        this.dirtyListener = dirtyListener;
        activeLanes.values().forEach(lane -> lane.setDirtyListener(dirtyListener));
    }

    @Nullable
    public QIOCraftingProcessorDefinition getResolvedDefinition() {
        if (state != State.ACTIVE) {
            return null;
        }
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.get(definitionId);
        return definition != null && definition.getSignature().equals(definitionSignature) ? definition : null;
    }

    @Nonnull
    public Map<Long, QIOProcessorLaneRuntime> getActiveLanes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(activeLanes));
    }

    @Nullable
    public QIOProcessorLaneRuntime getLane(long laneId) {
        return activeLanes.get(laneId);
    }

    @Nullable
    public QIOProcessorLaneRuntime acquireLane(@Nonnull UUID operationId, @Nonnull UUID jobId,
          int planRevision, @Nonnull String routeKey) {
        return acquireLane(operationId, jobId, planRevision, routeKey, 1);
    }

    @Nullable
    public QIOProcessorLaneRuntime acquireLane(@Nonnull UUID operationId, @Nonnull UUID jobId,
          int planRevision, @Nonnull String routeKey, long operationCount) {
        QIOCraftingProcessorDefinition definition = getResolvedDefinition();
        if (definition == null || (long) activeLanes.size() >= definition.getLaneCount()) {
            return null;
        }
        Long candidate = findAvailableLaneId();
        return candidate == null ? null : acquireLane(candidate, operationId, jobId, planRevision,
              routeKey, operationCount);
    }

    @Nullable
    public Long findAvailableLaneId() {
        QIOCraftingProcessorDefinition definition = getResolvedDefinition();
        if (definition == null || (long) activeLanes.size() >= definition.getLaneCount()) {
            return null;
        }
        long candidate = nextLaneCandidate;
        int checks = activeLanes.size() + 1;
        for (int checked = 0; checked < checks; checked++) {
            if (candidate >= definition.getLaneCount()) {
                candidate = 0;
            }
            if (!activeLanes.containsKey(candidate)) {
                return candidate;
            }
            candidate = incrementLane(candidate, definition.getLaneCount());
        }
        return null;
    }

    @Nullable
    public QIOProcessorLaneRuntime acquireLane(long laneId, @Nonnull UUID operationId,
          @Nonnull UUID jobId, int planRevision, @Nonnull String routeKey) {
        return acquireLane(laneId, operationId, jobId, planRevision, routeKey, 1);
    }

    @Nullable
    public QIOProcessorLaneRuntime acquireLane(long laneId, @Nonnull UUID operationId,
          @Nonnull UUID jobId, int planRevision, @Nonnull String routeKey,
          long operationCount) {
        QIOCraftingProcessorDefinition definition = getResolvedDefinition();
        if (definition == null || laneId < 0 || laneId >= definition.getLaneCount() ||
              (long) activeLanes.size() >= definition.getLaneCount()) {
            return null;
        }
        QIOProcessorLaneRuntime existing = activeLanes.get(laneId);
        if (existing != null) {
            return existing.getOperationId().equals(operationId) &&
                  existing.getJobId().equals(jobId) && existing.getPlanRevision() == planRevision &&
                  existing.getRouteKey().equals(routeKey) &&
                  existing.getOperationCount() == operationCount ? existing : null;
        }
        QIOProcessorLaneRuntime lane = QIOProcessorLaneRuntime.loading(laneId, operationId, jobId,
              planRevision, routeKey, operationCount);
        activeLanes.put(laneId, lane);
        persistedSettledOperations.remove(operationId);
        lane.setDirtyListener(dirtyListener);
        nextLaneCandidate = incrementLane(laneId, definition.getLaneCount());
        incrementRevision();
        return lane;
    }

    public boolean removeSettledLane(long laneId, @Nonnull UUID operationId) {
        QIOProcessorLaneRuntime lane = activeLanes.get(laneId);
        if (lane == null || !lane.getOperationId().equals(Objects.requireNonNull(operationId,
              "operationId")) || !lane.isSettled()) {
            return false;
        }
        activeLanes.remove(laneId);
        persistedSettledOperations.remove(operationId);
        QIOCraftingProcessorDefinition definition = getResolvedDefinition();
        if (definition != null && laneId < nextLaneCandidate) {
            nextLaneCandidate = laneId;
        }
        incrementRevision();
        return true;
    }

    public boolean isPersistedSettledOperation(@Nonnull UUID operationId) {
        return persistedSettledOperations.contains(Objects.requireNonNull(operationId,
              "operationId"));
    }

    /** Confirms one settled lane was included in an endpoint persistence snapshot. */
    public boolean confirmSettledLanePersisted(@Nonnull UUID operationId) {
        UUID checked = Objects.requireNonNull(operationId, "operationId");
        QIOProcessorLaneRuntime lane = activeLanes.values().stream()
              .filter(candidate -> checked.equals(candidate.getOperationId()))
              .findFirst().orElse(null);
        if (lane != null && lane.isSettled()) {
            return persistedSettledOperations.add(checked);
        }
        return false;
    }

    public boolean confirmSettledLanesPersisted() {
        int before = persistedSettledOperations.size();
        activeLanes.values().stream().filter(QIOProcessorLaneRuntime::isSettled)
              .map(QIOProcessorLaneRuntime::getOperationId)
              .forEach(persistedSettledOperations::add);
        return persistedSettledOperations.size() != before;
    }

    /** Re-evaluates a previously missing addon definition without changing persisted identity. */
    public boolean reconcile(@Nonnull ResourceLocation expectedHostId,
          @Nonnull ResourceLocation expectedDefinitionId) {
        if (state == State.DATA_ERROR) {
            return false;
        }
        String problem = resolutionProblem(expectedHostId, expectedDefinitionId);
        State next = problem == null ? State.ACTIVE : State.UNRESOLVED_DEFINITION;
        boolean changed = state != next || !Objects.equals(diagnostic, problem);
        state = next;
        diagnostic = problem;
        if (changed) {
            incrementRevision();
        }
        return state == State.ACTIVE;
    }

    public void markUnresolved(@Nonnull String reason) {
        if (state == State.DATA_ERROR) {
            return;
        }
        String checked = requireDiagnostic(reason);
        if (state != State.UNRESOLVED_DEFINITION || !checked.equals(diagnostic)) {
            state = State.UNRESOLVED_DEFINITION;
            diagnostic = checked;
            incrementRevision();
        }
    }

    public void enterDataError(@Nonnull String reason) {
        state = State.DATA_ERROR;
        diagnostic = requireDiagnostic(reason);
        incrementRevision();
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("processorStateSchemaVersion", SCHEMA_VERSION);
        QIOProcessingNbt.writeUUID(data, "processorUUID", processorUUID);
        data.setString("hostId", hostId.toString());
        data.setString("definitionId", definitionId.toString());
        data.setString("definitionSignature", definitionSignature);
        data.setString("state", state.name());
        data.setLong("nextLaneCandidate", nextLaneCandidate);
        data.setLong("runtimeRevision", runtimeRevision);
        if (diagnostic != null) {
            data.setString("diagnostic", diagnostic);
        }
        NBTTagList lanes = new NBTTagList();
        activeLanes.values().stream().sorted((left, right) -> Long.compare(left.getLaneId(),
              right.getLaneId())).forEach(lane -> lanes.appendTag(lane.write()));
        data.setTag("activeLanes", lanes);
        if (quarantinedData != null) {
            data.setTag("quarantinedData", quarantinedData.copy());
        }
        return data;
    }

    @Nonnull
    public static QIOCraftingProcessorState read(@Nonnull NBTTagCompound data,
          @Nonnull ResourceLocation expectedHostId, @Nonnull ResourceLocation expectedDefinitionId)
          throws QIOProcessingDataException {
        Objects.requireNonNull(data, "data");
        if (data.getInteger("processorStateSchemaVersion") != SCHEMA_VERSION) {
            throw new QIOProcessingDataException("Unsupported QIO crafting processor state schema " +
                  data.getInteger("processorStateSchemaVersion"));
        }
        if (!data.hasKey("processorUUID", NBT.TAG_STRING) ||
              !data.hasKey("hostId", NBT.TAG_STRING) ||
              !data.hasKey("definitionId", NBT.TAG_STRING) ||
              !data.hasKey("definitionSignature", NBT.TAG_STRING) ||
              !data.hasKey("state", NBT.TAG_STRING) ||
              !data.hasKey("nextLaneCandidate", NBT.TAG_LONG) ||
              !data.hasKey("runtimeRevision", NBT.TAG_LONG) ||
              !data.hasKey("activeLanes", NBT.TAG_LIST)) {
            throw new QIOProcessingDataException(
                  "QIO processor state is missing current-schema fields");
        }
        try {
            QIOCraftingProcessorState processor = new QIOCraftingProcessorState(
                  QIOProcessingNbt.readUUID(data, "processorUUID"),
                  readResourceLocation(data, "hostId"), readResourceLocation(data, "definitionId"),
                  data.getString("definitionSignature"));
            processor.state = QIOProcessingNbt.readEnum(data, "state", State.class);
            processor.nextLaneCandidate = QIOProcessingNbt.requireNonNegative(
                  data.getLong("nextLaneCandidate"), "nextLaneCandidate");
            processor.runtimeRevision = QIOProcessingNbt.requireNonNegative(
                  data.getLong("runtimeRevision"), "runtimeRevision");
            processor.diagnostic = data.hasKey("diagnostic", NBT.TAG_STRING) ?
                  requireDiagnostic(data.getString("diagnostic")) : null;
            NBTTagList lanes = data.getTagList("activeLanes", NBT.TAG_COMPOUND);
            for (int index = 0; index < lanes.tagCount(); index++) {
                QIOProcessorLaneRuntime lane = QIOProcessorLaneRuntime.read(lanes.getCompoundTagAt(index));
                if (processor.activeLanes.put(lane.getLaneId(), lane) != null) {
                    throw new QIOProcessingDataException("Duplicate QIO crafting processor lane id " +
                          lane.getLaneId());
                }
                if (lane.isSettled()) {
                    processor.persistedSettledOperations.add(lane.getOperationId());
                }
                lane.setDirtyListener(processor.dirtyListener);
            }
            processor.quarantinedData = data.hasKey("quarantinedData", NBT.TAG_COMPOUND) ?
                  data.getCompoundTag("quarantinedData").copy() : null;
            if (processor.state == State.DATA_ERROR) {
                if (processor.diagnostic == null) {
                    throw new QIOProcessingDataException("QIO processor data error has no diagnostic");
                }
                return processor;
            }
            processor.reconcileWithoutRevision(expectedHostId, expectedDefinitionId);
            return processor;
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO crafting processor state", e);
        }
    }

    /** Builds an isolated state while retaining the unread bytes for diagnostics and future repair. */
    @Nonnull
    public static QIOCraftingProcessorState damaged(@Nonnull ResourceLocation expectedHostId,
          @Nonnull QIOCraftingProcessorDefinition expectedDefinition, @Nonnull NBTTagCompound rawData,
          @Nonnull String reason) {
        QIOCraftingProcessorState state = create(expectedHostId, expectedDefinition);
        try {
            if (rawData.hasKey("processorUUID", NBT.TAG_STRING)) {
                state.processorUUID = QIOProcessingNbt.readUUID(rawData, "processorUUID");
            }
        } catch (QIOProcessingDataException ignored) {
        }
        state.state = State.DATA_ERROR;
        state.diagnostic = requireDiagnostic(reason);
        state.quarantinedData = rawData.copy();
        return state;
    }

    private void reconcileWithoutRevision(ResourceLocation expectedHostId,
          ResourceLocation expectedDefinitionId) throws QIOProcessingDataException {
        String problem = resolutionProblem(expectedHostId, expectedDefinitionId);
        if (problem == null) {
            state = State.ACTIVE;
            diagnostic = null;
            QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.get(definitionId);
            if (nextLaneCandidate >= definition.getLaneCount()) {
                nextLaneCandidate = 0;
            }
        } else {
            state = State.UNRESOLVED_DEFINITION;
            diagnostic = problem;
        }
    }

    @Nullable
    private String resolutionProblem(ResourceLocation expectedHostId,
          ResourceLocation expectedDefinitionId) {
        Objects.requireNonNull(expectedHostId, "expectedHostId");
        Objects.requireNonNull(expectedDefinitionId, "expectedDefinitionId");
        if (!hostId.equals(expectedHostId)) {
            return "Persisted processor host " + hostId + " does not match block host " + expectedHostId;
        }
        if (!definitionId.equals(expectedDefinitionId)) {
            return "Persisted processor definition " + definitionId + " does not match block definition " +
                  expectedDefinitionId;
        }
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.get(definitionId);
        if (definition == null) {
            return "Processor definition is not registered: " + definitionId;
        }
        if (!definition.getSignature().equals(definitionSignature)) {
            return "Processor definition signature changed for " + definitionId;
        }
        for (long laneId : activeLanes.keySet()) {
            if (laneId < 0 || laneId >= definition.getLaneCount()) {
                return "Persisted lane " + laneId + " is outside definition " + definitionId;
            }
        }
        return null;
    }

    private void incrementRevision() {
        if (runtimeRevision == Long.MAX_VALUE) {
            state = State.DATA_ERROR;
            diagnostic = "QIO processor runtime revision exhausted";
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

    private static long incrementLane(long laneId, long laneCount) {
        return laneId == laneCount - 1 ? 0 : laneId + 1;
    }

    @Nonnull
    private static ResourceLocation readResourceLocation(NBTTagCompound data, String key)
          throws QIOProcessingDataException {
        String value = data.getString(key);
        try {
            ResourceLocation parsed = new ResourceLocation(value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException("ResourceLocation is not canonical");
            }
            return parsed;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid ResourceLocation in " + key + ": " + value, e);
        }
    }

    private static String requireSignature(String signature) {
        String checked = Objects.requireNonNull(signature, "definitionSignature");
        if (!checked.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("QIO processor definition signature must be lowercase SHA-256");
        }
        return checked;
    }

    private static String requireDiagnostic(String reason) {
        String checked = Objects.requireNonNull(reason, "reason").trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("QIO processor diagnostic cannot be empty");
        }
        return checked.substring(0, Math.min(512, checked.length()));
    }
}
