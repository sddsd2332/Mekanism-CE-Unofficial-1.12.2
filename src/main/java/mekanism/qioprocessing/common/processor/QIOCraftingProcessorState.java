package mekanism.qioprocessing.common.processor;

import mekanism.api.processing.MachineOperationToken;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorDefinition;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import java.util.*;

/**
 * Durable identity and lane runtime for a QIO crafting processor host.
 */
public final class QIOCraftingProcessorState {
    public static final int VERSION = 1;
    private static final int MAX_LANES = 256;

    public enum State {UNBOUND, ACTIVE, WAITING_ACCESS, IDENTITY_CONFLICT, QUARANTINED}

    public enum LaneState {ALLOCATED, LOADING, PROCESSING, COLLECTING, COMPLETED, CANCELLED, NEEDS_RECONCILIATION}

    public static final class Lane {
        private final long laneId;
        private final UUID operationId;
        private final MachineOperationToken token;
        private final long currentTick;
        private final long totalTicks;
        private final LaneState state;
        private final String diagnostic;

        public Lane(long laneId, UUID operationId, MachineOperationToken token,
                    long currentTick, long totalTicks, LaneState state, String diagnostic) {
            if (laneId < 0 || currentTick < 0 || totalTicks <= 0 || currentTick > totalTicks) {
                throw new IllegalArgumentException("Invalid processor lane progress");
            }
            this.laneId = laneId;
            this.operationId = Objects.requireNonNull(operationId, "operationId");
            this.token = Objects.requireNonNull(token, "token");
            if (token.laneId() != laneId || !operationId.equals(token.operationId())) {
                throw new IllegalArgumentException("Processor lane token mismatch");
            }
            this.currentTick = currentTick;
            this.totalTicks = totalTicks;
            this.state = Objects.requireNonNull(state, "state");
            if (!matchesTokenState(this.state, token.state())) {
                throw new IllegalArgumentException("Processor lane state and Token state disagree");
            }
            this.diagnostic = diagnostic == null ? "" : diagnostic;
            if (this.diagnostic.length() > 512)
                throw new IllegalArgumentException("Processor lane diagnostic is too long");
        }

        public long laneId() {
            return laneId;
        }

        public UUID operationId() {
            return operationId;
        }

        public MachineOperationToken token() {
            return token;
        }

        public long currentTick() {
            return currentTick;
        }

        public long totalTicks() {
            return totalTicks;
        }

        public LaneState state() {
            return state;
        }

        public String diagnostic() {
            return diagnostic;
        }

        public Lane advance(long tick, MachineOperationToken nextToken, LaneState next, String reason) {
            return new Lane(laneId, operationId, nextToken, tick, totalTicks, next, reason);
        }
    }

    private final UUID processorId;
    private final ResourceLocation hostId;
    private final ResourceLocation definitionId;
    private final String definitionSignature;
    private final State state;
    private final long runtimeRevision;
    private final long nextLaneCandidate;
    private final String diagnostic;
    private final Map<Long, Lane> lanes;

    private QIOCraftingProcessorState(UUID processorId, ResourceLocation hostId,
                                      ResourceLocation definitionId, String definitionSignature, State state,
                                      long runtimeRevision, long nextLaneCandidate, String diagnostic, Map<Long, Lane> lanes) {
        this.processorId = Objects.requireNonNull(processorId, "processorId");
        this.hostId = Objects.requireNonNull(hostId, "hostId");
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.definitionSignature = text(definitionSignature, "definitionSignature");
        this.state = Objects.requireNonNull(state, "state");
        if (runtimeRevision < 0 || nextLaneCandidate < 0)
            throw new IllegalArgumentException("Invalid processor revision");
        this.runtimeRevision = runtimeRevision;
        this.nextLaneCandidate = nextLaneCandidate;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
        if (this.diagnostic.length() > 512) throw new IllegalArgumentException("Processor diagnostic is too long");
        if (lanes.size() > MAX_LANES) throw new IllegalArgumentException("Too many processor lanes");
        this.lanes = Collections.unmodifiableMap(new LinkedHashMap<>(lanes));
    }

    public static QIOCraftingProcessorState create(UUID processorId, ResourceLocation hostId,
                                                   QIOCraftingProcessorDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new QIOCraftingProcessorState(processorId, hostId, definition.getId(),
                definition.getSignature(), State.UNBOUND, 0, 0, "", Collections.emptyMap());
    }

    public QIOCraftingProcessorState activate() {
        return copy(State.ACTIVE, "");
    }

    public QIOCraftingProcessorState waitingAccess(String reason) {
        return copy(State.WAITING_ACCESS, reason);
    }

    public QIOCraftingProcessorState quarantine(String reason) {
        return copy(State.QUARANTINED, reason);
    }

    public QIOCraftingProcessorState identityConflict(String reason) {
        return copy(State.IDENTITY_CONFLICT, reason);
    }

    public QIOCraftingProcessorState startLane(long laneId, MachineOperationToken token, long totalTicks) {
        if (state != State.ACTIVE) throw new IllegalStateException("Processor is not active");
        if (laneId < 0 || lanes.containsKey(laneId))
            throw new IllegalArgumentException("Processor lane is unavailable");
        if (token.state() != MachineOperationToken.State.ALLOCATED) {
            throw new IllegalArgumentException("A new processor lane requires an allocated token");
        }
        Map<Long, Lane> next = new LinkedHashMap<>(lanes);
        Lane lane = new Lane(laneId, token.operationId(), token, 0, totalTicks,
                LaneState.ALLOCATED, "");
        next.put(laneId, lane);
        return next(next, Math.max(nextLaneCandidate, laneId + 1), "");
    }

    public OptionalLong nextAvailableLane(QIOCraftingProcessorDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        long count = definition.getLaneCount();
        if (count <= 0) return OptionalLong.empty();
        long start = nextLaneCandidate % count;
        for (long offset = 0; offset < count; offset++) {
            long laneId = (start + offset) % count;
            if (!lanes.containsKey(laneId)) return OptionalLong.of(laneId);
        }
        return OptionalLong.empty();
    }

    public QIOCraftingProcessorState startNextLane(QIOCraftingProcessorDefinition definition,
                                                   MachineOperationToken token, long totalTicks) {
        OptionalLong lane = nextAvailableLane(definition);
        if (!lane.isPresent()) throw new IllegalStateException("All processor lanes are occupied");
        return startLane(lane.getAsLong(), token, totalTicks);
    }

    public QIOCraftingProcessorState updateLane(long laneId, long currentTick,
                                                LaneState laneState, String reason) {
        Lane lane = requireLane(laneId);
        if (currentTick < lane.currentTick()) {
            throw new IllegalArgumentException("Processor lane progress cannot move backwards");
        }
        MachineOperationToken nextToken = transitionToken(lane.token(), lane.state(), laneState);
        Map<Long, Lane> next = new LinkedHashMap<>(lanes);
        next.put(laneId, lane.advance(currentTick, nextToken, laneState, reason));
        return next(next, nextLaneCandidate, diagnostic);
    }

    /**
     * Removes a lane only after its durable operation reached a terminal state.
     */
    public QIOCraftingProcessorState releaseLane(long laneId) {
        Lane lane = requireLane(laneId);
        if (lane.state() != LaneState.COMPLETED && lane.state() != LaneState.CANCELLED) {
            throw new IllegalStateException("Processor lane is not terminal");
        }
        return removeLane(laneId);
    }

    public QIOCraftingProcessorState removeLane(long laneId) {
        if (!lanes.containsKey(laneId)) return this;
        Map<Long, Lane> next = new LinkedHashMap<>(lanes);
        next.remove(laneId);
        return next(next, nextLaneCandidate, diagnostic);
    }

    public UUID processorId() {
        return processorId;
    }

    public ResourceLocation hostId() {
        return hostId;
    }

    public ResourceLocation definitionId() {
        return definitionId;
    }

    public String definitionSignature() {
        return definitionSignature;
    }

    public State state() {
        return state;
    }

    public long runtimeRevision() {
        return runtimeRevision;
    }

    public long nextLaneCandidate() {
        return nextLaneCandidate;
    }

    public String diagnostic() {
        return diagnostic;
    }

    public Map<Long, Lane> lanes() {
        return lanes;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", VERSION);
        tag.setUniqueId("processor", processorId);
        tag.setString("host", hostId.toString());
        tag.setString("definition", definitionId.toString());
        tag.setString("definitionSignature", definitionSignature);
        tag.setString("state", state.name());
        tag.setLong("runtimeRevision", runtimeRevision);
        tag.setLong("nextLaneCandidate", nextLaneCandidate);
        tag.setString("diagnostic", diagnostic);
        NBTTagList stored = new NBTTagList();
        lanes.values().forEach(lane -> {
            NBTTagCompound row = new NBTTagCompound();
            row.setLong("lane", lane.laneId());
            row.setUniqueId("operation", lane.operationId());
            row.setTag("token", lane.token().write());
            row.setLong("currentTick", lane.currentTick());
            row.setLong("totalTicks", lane.totalTicks());
            row.setString("state", lane.state().name());
            row.setString("diagnostic", lane.diagnostic());
            stored.appendTag(row);
        });
        tag.setTag("lanes", stored);
        return tag;
    }

    public static QIOCraftingProcessorState read(NBTTagCompound tag) {
        if (tag == null || tag.getInteger("version") != VERSION || !tag.hasUniqueId("processor")
                || !tag.hasKey("host", 8) || !tag.hasKey("definition", 8)
                || !tag.hasKey("definitionSignature", 8) || !tag.hasKey("state", 8)
                || !tag.hasKey("runtimeRevision", 4) || !tag.hasKey("nextLaneCandidate", 4)
                || !tag.hasKey("lanes", 9)) throw new IllegalArgumentException("Invalid processor state");
        try {
            NBTTagList stored = tag.getTagList("lanes", 10);
            if (stored.tagCount() > MAX_LANES) throw new IllegalArgumentException("Too many processor lanes");
            Map<Long, Lane> lanes = new LinkedHashMap<>();
            for (int index = 0; index < stored.tagCount(); index++) {
                NBTTagCompound row = stored.getCompoundTagAt(index);
                Lane lane = new Lane(row.getLong("lane"), row.getUniqueId("operation"),
                        MachineOperationToken.read(row.getCompoundTag("token")), row.getLong("currentTick"),
                        row.getLong("totalTicks"), LaneState.valueOf(row.getString("state")), row.getString("diagnostic"));
                if (lanes.putIfAbsent(lane.laneId(), lane) != null)
                    throw new IllegalArgumentException("Duplicate processor lane");
            }
            return new QIOCraftingProcessorState(tag.getUniqueId("processor"),
                    new ResourceLocation(tag.getString("host")), new ResourceLocation(tag.getString("definition")),
                    tag.getString("definitionSignature"), State.valueOf(tag.getString("state")),
                    tag.getLong("runtimeRevision"), tag.getLong("nextLaneCandidate"), tag.getString("diagnostic"), lanes);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid processor state", error);
        }
    }

    private QIOCraftingProcessorState copy(State nextState, String reason) {
        return next(lanes, nextLaneCandidate, reason, nextState);
    }

    private QIOCraftingProcessorState next(Map<Long, Lane> next, long nextCandidate, String reason) {
        return next(next, nextCandidate, reason, state);
    }

    private QIOCraftingProcessorState next(Map<Long, Lane> next, long nextCandidate,
                                           String reason, State nextState) {
        return new QIOCraftingProcessorState(processorId, hostId, definitionId, definitionSignature,
                nextState, runtimeRevision + 1, nextCandidate, reason, next);
    }

    private Lane requireLane(long laneId) {
        Lane lane = lanes.get(laneId);
        if (lane == null) throw new IllegalArgumentException("Unknown processor lane");
        return lane;
    }

    private static MachineOperationToken transitionToken(MachineOperationToken token,
                                                         LaneState current, LaneState next) {
        if (next == current) return token;
        if (next == LaneState.NEEDS_RECONCILIATION) {
            return token.state() == MachineOperationToken.State.CONTAMINATED ? token
                    : token.transition(MachineOperationToken.State.CONTAMINATED);
        }
        MachineOperationToken.State target;
        switch (next) {
            case ALLOCATED:
                target = MachineOperationToken.State.ALLOCATED;
                break;
            case LOADING:
                target = MachineOperationToken.State.LOADING;
                break;
            case PROCESSING:
                target = MachineOperationToken.State.PROCESSING;
                break;
            case COLLECTING:
                target = MachineOperationToken.State.COLLECTING;
                break;
            case COMPLETED:
                target = MachineOperationToken.State.COMPLETED;
                break;
            case CANCELLED:
                target = MachineOperationToken.State.CANCELLED;
                break;
            default:
                throw new IllegalArgumentException("Unknown processor lane state");
        }
        if (token.state() == target) return token;
        return token.transition(target);
    }

    private static boolean matchesTokenState(LaneState laneState, MachineOperationToken.State tokenState) {
        switch (laneState) {
            case ALLOCATED:
                return tokenState == MachineOperationToken.State.ALLOCATED;
            case LOADING:
                return tokenState == MachineOperationToken.State.LOADING;
            case PROCESSING:
                return tokenState == MachineOperationToken.State.PROCESSING;
            case COLLECTING:
                return tokenState == MachineOperationToken.State.COLLECTING;
            case COMPLETED:
                return tokenState == MachineOperationToken.State.COMPLETED;
            case CANCELLED:
                return tokenState == MachineOperationToken.State.CANCELLED;
            case NEEDS_RECONCILIATION:
                return tokenState == MachineOperationToken.State.CONTAMINATED;
            default:
                return false;
        }
    }

    private static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty() || value.length() > 256) throw new IllegalArgumentException("Invalid " + name);
        return value;
    }
}

