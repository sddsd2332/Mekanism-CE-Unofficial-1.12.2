package mekanism.qioprocessing.common.processor;

import mekanism.api.processing.MachineOperationToken;
import mekanism.qioprocessing.api.processor.QIOCraftingProcessorDefinition;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import java.util.*;

/**
 * Immutable read-only processor monitor payload; it contains no live TileEntity reference.
 */
public final class QIOCraftingProcessorSnapshot {
    public static final int MAX_LANES = 256;

    public static final class Lane {
        private final long laneId;
        private final UUID operationId;
        private final MachineOperationToken.State tokenState;
        private final long currentTick;
        private final long totalTicks;
        private final QIOCraftingProcessorState.LaneState state;
        private final String diagnostic;

        private Lane(QIOCraftingProcessorState.Lane source) {
            laneId = source.laneId();
            operationId = source.operationId();
            tokenState = source.token().state();
            currentTick = source.currentTick();
            totalTicks = source.totalTicks();
            state = source.state();
            diagnostic = source.diagnostic();
        }

        private Lane(long laneId, UUID operationId, MachineOperationToken.State tokenState,
                     long currentTick, long totalTick, QIOCraftingProcessorState.LaneState state,
                     String diagnostic) {
            this.laneId = laneId;
            this.operationId = Objects.requireNonNull(operationId, "operationId");
            this.tokenState = Objects.requireNonNull(tokenState, "tokenState");
            this.currentTick = currentTick;
            this.totalTicks = totalTick;
            this.state = Objects.requireNonNull(state, "state");
            this.diagnostic = diagnostic == null ? "" : diagnostic;
        }

        public long laneId() {
            return laneId;
        }

        public UUID operationId() {
            return operationId;
        }

        public MachineOperationToken.State tokenState() {
            return tokenState;
        }

        public long currentTick() {
            return currentTick;
        }

        public long totalTicks() {
            return totalTicks;
        }

        public QIOCraftingProcessorState.LaneState state() {
            return state;
        }

        public String diagnostic() {
            return diagnostic;
        }
    }

    private final UUID processorId;
    private final ResourceLocation hostId;
    private final ResourceLocation definitionId;
    private final QIOCraftingProcessorState.State state;
    private final long runtimeRevision;
    private final long endpointRevision;
    private final String diagnostic;
    private final double energy;
    private final double maxEnergy;
    private final int speedUpgrades;
    private final int energyUpgrades;
    private final int stackingUpgrades;
    private final List<Lane> lanes;

    private QIOCraftingProcessorSnapshot(QIOCraftingProcessorTile tile) {
        QIOCraftingProcessorState current = tile.processorState();
        QIOCraftingProcessorDefinition definition = tile.definition();
        processorId = current.processorId();
        hostId = current.hostId();
        definitionId = definition.getId();
        state = current.state();
        runtimeRevision = current.runtimeRevision();
        endpointRevision = tile.endpointRevision();
        diagnostic = current.diagnostic().isEmpty() ? tile.loadDiagnostic() : current.diagnostic();
        energy = tile.getEnergy();
        maxEnergy = tile.getMaxEnergy();
        speedUpgrades = tile.speedUpgrades();
        energyUpgrades = tile.energyUpgrades();
        stackingUpgrades = tile.stackingUpgrades();
        List<Lane> copy = new ArrayList<>();
        current.lanes().values().forEach(lane -> copy.add(new Lane(lane)));
        lanes = Collections.unmodifiableList(copy);
    }

    private QIOCraftingProcessorSnapshot(UUID processorId, ResourceLocation hostId,
                                         ResourceLocation definitionId, QIOCraftingProcessorState.State state,
                                         long runtimeRevision, long endpointRevision, String diagnostic, double energy,
                                         double maxEnergy, int speedUpgrades, int energyUpgrades, int stackingUpgrades,
                                         List<Lane> lanes) {
        this.processorId = Objects.requireNonNull(processorId, "processorId");
        this.hostId = Objects.requireNonNull(hostId, "hostId");
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.state = Objects.requireNonNull(state, "state");
        if (runtimeRevision < 0 || endpointRevision < 0 || !Double.isFinite(energy)
                || !Double.isFinite(maxEnergy) || energy < 0 || maxEnergy < 0 || energy > maxEnergy) {
            throw new IllegalArgumentException("Invalid Processor snapshot values");
        }
        if (speedUpgrades < 0 || energyUpgrades < 0 || stackingUpgrades < 0
                || lanes.size() > MAX_LANES) throw new IllegalArgumentException("Invalid Processor snapshot bounds");
        this.runtimeRevision = runtimeRevision;
        this.endpointRevision = endpointRevision;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
        if (this.diagnostic.length() > 512)
            throw new IllegalArgumentException("Processor snapshot diagnostic is too long");
        this.energy = energy;
        this.maxEnergy = maxEnergy;
        this.speedUpgrades = speedUpgrades;
        this.energyUpgrades = energyUpgrades;
        this.stackingUpgrades = stackingUpgrades;
        this.lanes = Collections.unmodifiableList(new ArrayList<>(lanes));
    }

    public static QIOCraftingProcessorSnapshot capture(QIOCraftingProcessorTile tile) {
        return new QIOCraftingProcessorSnapshot(Objects.requireNonNull(tile, "tile"));
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

    public QIOCraftingProcessorState.State state() {
        return state;
    }

    public long runtimeRevision() {
        return runtimeRevision;
    }

    public long endpointRevision() {
        return endpointRevision;
    }

    public String diagnostic() {
        return diagnostic;
    }

    public double energy() {
        return energy;
    }

    public double maxEnergy() {
        return maxEnergy;
    }

    public int speedUpgrades() {
        return speedUpgrades;
    }

    public int energyUpgrades() {
        return energyUpgrades;
    }

    public int stackingUpgrades() {
        return stackingUpgrades;
    }

    public List<Lane> lanes() {
        return lanes;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", 1);
        tag.setUniqueId("processor", processorId);
        tag.setString("host", hostId.toString());
        tag.setString("definition", definitionId.toString());
        tag.setString("state", state.name());
        tag.setLong("runtimeRevision", runtimeRevision);
        tag.setLong("endpointRevision", endpointRevision);
        tag.setString("diagnostic", diagnostic);
        tag.setDouble("energy", energy);
        tag.setDouble("maxEnergy", maxEnergy);
        tag.setInteger("speedUpgrades", speedUpgrades);
        tag.setInteger("energyUpgrades", energyUpgrades);
        tag.setInteger("stackingUpgrades", stackingUpgrades);
        NBTTagList stored = new NBTTagList();
        for (Lane lane : lanes) {
            NBTTagCompound row = new NBTTagCompound();
            row.setLong("lane", lane.laneId());
            row.setUniqueId("operation", lane.operationId());
            row.setString("tokenState", lane.tokenState().name());
            row.setLong("currentTick", lane.currentTick());
            row.setLong("totalTicks", lane.totalTicks());
            row.setString("state", lane.state().name());
            row.setString("diagnostic", lane.diagnostic());
            stored.appendTag(row);
        }
        tag.setTag("lanes", stored);
        return tag;
    }

    public static QIOCraftingProcessorSnapshot read(NBTTagCompound tag) {
        if (tag == null || tag.getInteger("version") != 1 || !tag.hasUniqueId("processor")
                || !tag.hasKey("host", 8) || !tag.hasKey("definition", 8) || !tag.hasKey("state", 8)
                || !tag.hasKey("runtimeRevision", 4) || !tag.hasKey("endpointRevision", 4)
                || !tag.hasKey("lanes", 9)) throw new IllegalArgumentException("Invalid Processor snapshot");
        try {
            NBTTagList stored = tag.getTagList("lanes", 10);
            if (stored.tagCount() > MAX_LANES) throw new IllegalArgumentException("Too many snapshot lanes");
            List<Lane> lanes = new ArrayList<>();
            for (int index = 0; index < stored.tagCount(); index++) {
                NBTTagCompound row = stored.getCompoundTagAt(index);
                if (!row.hasUniqueId("operation") || !row.hasKey("tokenState", 8)
                        || !row.hasKey("state", 8) || !row.hasKey("currentTick", 4)
                        || !row.hasKey("totalTicks", 4)) throw new IllegalArgumentException("Invalid snapshot lane");
                long current = row.getLong("currentTick");
                long total = row.getLong("totalTicks");
                if (row.getLong("lane") < 0 || current < 0 || total <= 0 || current > total) {
                    throw new IllegalArgumentException("Invalid snapshot lane progress");
                }
                String diagnostic = row.getString("diagnostic");
                if (diagnostic.length() > 512)
                    throw new IllegalArgumentException("Snapshot lane diagnostic is too long");
                lanes.add(new Lane(row.getLong("lane"), row.getUniqueId("operation"),
                        MachineOperationToken.State.valueOf(row.getString("tokenState")), current, total,
                        QIOCraftingProcessorState.LaneState.valueOf(row.getString("state")), diagnostic));
            }
            return new QIOCraftingProcessorSnapshot(tag.getUniqueId("processor"),
                    new ResourceLocation(tag.getString("host")), new ResourceLocation(tag.getString("definition")),
                    QIOCraftingProcessorState.State.valueOf(tag.getString("state")),
                    tag.getLong("runtimeRevision"), tag.getLong("endpointRevision"), tag.getString("diagnostic"),
                    tag.getDouble("energy"), tag.getDouble("maxEnergy"), tag.getInteger("speedUpgrades"),
                    tag.getInteger("energyUpgrades"), tag.getInteger("stackingUpgrades"), lanes);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid Processor snapshot", error);
        }
    }

}

