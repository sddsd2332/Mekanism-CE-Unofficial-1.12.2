package mekanism.qioprocessing.common.processor;

import mekanism.api.processing.MachineOperationToken;
import mekanism.api.qio.external.QIOFrequencyReference;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Server-side directory of explicitly registered Processor TileEntities.
 *
 * <p>The directory never opens a QIO frequency and never scans arbitrary worlds. A caller must
 * first perform frequency authorization and provide the exact persisted endpoint reference.</p>
 */
public final class QIOCraftingProcessorEndpointDirectory {
    public static final QIOCraftingProcessorEndpointDirectory INSTANCE =
            new QIOCraftingProcessorEndpointDirectory();

    private final Map<QIOCraftingProcessorTile, UUID> identities = new IdentityHashMap<>();

    public enum ResolutionStatus {
        WAITING_FOR_TILE,
        CONFLICT,
        PROCESSING,
        READY_OUTPUT,
        COMPLETED,
        CANCELLED
    }

    private QIOCraftingProcessorEndpointDirectory() {
    }

    public synchronized void register(QIOCraftingProcessorTile tile) {
        Objects.requireNonNull(tile, "tile");
        identities.put(tile, tile.processorState().processorId());
    }

    public synchronized void unregister(QIOCraftingProcessorTile tile) {
        if (tile != null) identities.remove(tile);
    }

    public synchronized void clearForTests() {
        identities.clear();
    }

    public synchronized List<LoadedProcessor> available(QIOFrequencyReference reference) {
        Objects.requireNonNull(reference, "reference");
        Map<UUID, Integer> occurrences = new java.util.HashMap<>();
        for (UUID identity : identities.values()) {
            occurrences.merge(identity, 1, Integer::sum);
        }
        List<LoadedProcessor> result = new ArrayList<>();
        for (Map.Entry<QIOCraftingProcessorTile, UUID> entry : identities.entrySet()) {
            QIOCraftingProcessorTile tile = entry.getKey();
            if (occurrences.getOrDefault(entry.getValue(), 0) != 1) continue;
            if (!isAvailable(tile, reference)) continue;
            if (!tile.processorState().nextAvailableLane(tile.definition()).isPresent()) continue;
            result.add(new LoadedProcessor(entry.getValue(), tile, tile.endpointRevision()));
        }
        result.sort(Comparator.comparingInt(LoadedProcessor::dimension)
                .thenComparing(LoadedProcessor::position)
                .thenComparing(value -> value.processorId().toString()));
        return Collections.unmodifiableList(result);
    }

    public synchronized List<LoadedProcessor> bound(QIOFrequencyReference reference) {
        Objects.requireNonNull(reference, "reference");
        Map<UUID, Integer> occurrences = new java.util.HashMap<>();
        for (UUID identity : identities.values()) occurrences.merge(identity, 1, Integer::sum);
        List<LoadedProcessor> result = new ArrayList<>();
        for (Map.Entry<QIOCraftingProcessorTile, UUID> entry : identities.entrySet()) {
            QIOCraftingProcessorTile tile = entry.getKey();
            if (occurrences.getOrDefault(entry.getValue(), 0) != 1 || !isLoaded(tile)
                    || !reference.equals(tile.endpointReference())) continue;
            result.add(new LoadedProcessor(entry.getValue(), tile, tile.endpointRevision()));
        }
        result.sort(Comparator.comparingInt(LoadedProcessor::dimension)
                .thenComparing(LoadedProcessor::position)
                .thenComparing(value -> value.processorId().toString()));
        return Collections.unmodifiableList(result);
    }

    public synchronized Optional<Allocation> allocate(QIOFrequencyReference reference,
                                                      UUID operationId, String sourceId, String routeId, String recipeKey,
                                                      int configurationRevision, long baseTicks) {
        List<LoadedProcessor> candidates = available(reference);
        if (candidates.isEmpty()) return Optional.empty();
        LoadedProcessor selected = candidates.get(0);
        MachineOperationToken token = selected.tile().allocateOperation(operationId, sourceId,
                routeId, recipeKey, configurationRevision, baseTicks);
        return Optional.of(new Allocation(selected, token));
    }

    /**
     * Resolves a persisted allocation without allocating a second lane.
     */
    public synchronized Resolution resolve(QIOFrequencyReference reference,
                                           QIOProcessorAllocationRecord record) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(record, "record");
        MachineOperationToken token = record.token();
        if (token.state() == MachineOperationToken.State.CANCELLED
                || token.state() == MachineOperationToken.State.CONTAMINATED) {
            return new Resolution(ResolutionStatus.CANCELLED, null, null);
        }
        boolean durableCompleted = token.state() == MachineOperationToken.State.COMPLETED;
        boolean identitySeen = false;
        for (Map.Entry<QIOCraftingProcessorTile, UUID> entry : identities.entrySet()) {
            if (!record.processorId().equals(entry.getValue())) continue;
            identitySeen = true;
            QIOCraftingProcessorTile tile = entry.getKey();
            if (!isLoaded(tile)) continue;
            if (!reference.equals(tile.endpointReference())
                    || tile.endpointRevision() != record.endpointRevision()) {
                if (durableCompleted) continue;
                return new Resolution(ResolutionStatus.CONFLICT, tile, null);
            }
            QIOCraftingProcessorState.Lane lane = tile.processorState().lanes().get(token.laneId());
            if (lane == null || !record.operationId().equals(lane.operationId())
                    || lane.token().laneId() != token.laneId()) {
                return new Resolution(ResolutionStatus.CONFLICT, tile, lane);
            }
            switch (lane.state()) {
                case COLLECTING:
                    return new Resolution(ResolutionStatus.READY_OUTPUT, tile, lane);
                case COMPLETED:
                    return new Resolution(ResolutionStatus.COMPLETED, tile, lane);
                case CANCELLED:
                case NEEDS_RECONCILIATION:
                    return new Resolution(ResolutionStatus.CANCELLED, tile, lane);
                default:
                    return new Resolution(ResolutionStatus.PROCESSING, tile, lane);
            }
        }
        return new Resolution(durableCompleted ? ResolutionStatus.COMPLETED
                : ResolutionStatus.WAITING_FOR_TILE, null, null);
    }

    private static boolean isLoaded(QIOCraftingProcessorTile tile) {
        return tile != null && !tile.isInvalid() && tile.getWorld() != null && !tile.getWorld().isRemote
                && tile.getWorld().isBlockLoaded(tile.getPos(), false)
                && tile.getWorld().getTileEntity(tile.getPos()) == tile;
    }

    public static final class Resolution {
        private final ResolutionStatus status;
        private final QIOCraftingProcessorTile tile;
        private final QIOCraftingProcessorState.Lane lane;

        private Resolution(ResolutionStatus status, QIOCraftingProcessorTile tile,
                           QIOCraftingProcessorState.Lane lane) {
            this.status = status;
            this.tile = tile;
            this.lane = lane;
        }

        public ResolutionStatus status() {
            return status;
        }

        public QIOCraftingProcessorTile tile() {
            return tile;
        }

        public QIOCraftingProcessorState.Lane lane() {
            return lane;
        }
    }

    private static boolean isAvailable(QIOCraftingProcessorTile tile, QIOFrequencyReference reference) {
        if (tile == null || tile.isInvalid() || tile.getWorld() == null || tile.getWorld().isRemote
                || !tile.getWorld().isBlockLoaded(tile.getPos(), false)
                || tile.getWorld().getTileEntity(tile.getPos()) != tile
                || tile.processorState().state() != QIOCraftingProcessorState.State.ACTIVE) {
            return false;
        }
        return reference.equals(tile.endpointReference());
    }

    public static final class LoadedProcessor {
        private final UUID processorId;
        private final QIOCraftingProcessorTile tile;
        private final long endpointRevision;

        private LoadedProcessor(UUID processorId, QIOCraftingProcessorTile tile, long endpointRevision) {
            this.processorId = processorId;
            this.tile = tile;
            this.endpointRevision = endpointRevision;
        }

        public UUID processorId() {
            return processorId;
        }

        public QIOCraftingProcessorTile tile() {
            return tile;
        }

        public long endpointRevision() {
            return endpointRevision;
        }

        public int dimension() {
            return tile.getWorld().provider.getDimension();
        }

        public BlockPos position() {
            return tile.getPos();
        }
    }

    public static final class Allocation {
        private final LoadedProcessor processor;
        private final MachineOperationToken token;

        private Allocation(LoadedProcessor processor, MachineOperationToken token) {
            this.processor = processor;
            this.token = token;
        }

        public LoadedProcessor processor() {
            return processor;
        }

        public MachineOperationToken token() {
            return token;
        }

        public QIOProcessorAllocationRecord record() {
            return new QIOProcessorAllocationRecord(token.operationId(), processor.processorId(),
                    processor.endpointRevision(), token);
        }
    }
}

