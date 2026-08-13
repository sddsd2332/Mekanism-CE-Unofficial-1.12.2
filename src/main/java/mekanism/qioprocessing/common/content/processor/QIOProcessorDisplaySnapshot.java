package mekanism.qioprocessing.common.content.processor;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded, display-only projection of the occupied workbench processor lanes. */
public final class QIOProcessorDisplaySnapshot {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_DISPLAY_LANES = 64;
    private static final int GRID_SIZE = 9;
    private static final int MAX_ROUTE_KEY_LENGTH = 512;

    private final int laneCount;
    private final Map<Integer, Lane> lanes;

    public QIOProcessorDisplaySnapshot(int laneCount, @Nonnull List<Lane> lanes) {
        if (laneCount < 0 || laneCount > MAX_DISPLAY_LANES) {
            throw new IllegalArgumentException("QIO processor display lane count is invalid");
        }
        Objects.requireNonNull(lanes, "lanes");
        Map<Integer, Lane> checked = new LinkedHashMap<>();
        for (Lane lane : lanes) {
            Objects.requireNonNull(lane, "lane");
            if (lane.getLaneId() < 0 || lane.getLaneId() >= laneCount) {
                throw new IllegalArgumentException("QIO processor display lane is outside the snapshot");
            }
            if (checked.put(lane.getLaneId(), lane) != null) {
                throw new IllegalArgumentException("Duplicate QIO processor display lane " +
                      lane.getLaneId());
            }
        }
        this.laneCount = laneCount;
        this.lanes = Collections.unmodifiableMap(checked);
    }

    @Nonnull
    public static QIOProcessorDisplaySnapshot empty(int laneCount) {
        return new QIOProcessorDisplaySnapshot(Math.max(0,
              Math.min(MAX_DISPLAY_LANES, laneCount)), Collections.emptyList());
    }

    public int getLaneCount() {
        return laneCount;
    }

    @Nullable
    public Lane getLane(int laneId) {
        return lanes.get(laneId);
    }

    @Nonnull
    public List<Lane> getOccupiedLanes() {
        return Collections.unmodifiableList(new ArrayList<>(lanes.values()));
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schemaVersion", SCHEMA_VERSION);
        data.setInteger("laneCount", laneCount);
        NBTTagList laneList = new NBTTagList();
        lanes.values().forEach(lane -> laneList.appendTag(lane.write()));
        data.setTag("lanes", laneList);
        return data;
    }

    @Nonnull
    public static QIOProcessorDisplaySnapshot read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        Objects.requireNonNull(data, "data");
        try {
            if (data.getInteger("schemaVersion") != SCHEMA_VERSION) {
                throw new QIOProcessingDataException("Unsupported QIO processor display schema");
            }
            int laneCount = data.getInteger("laneCount");
            if (laneCount < 0 || laneCount > MAX_DISPLAY_LANES) {
                throw new QIOProcessingDataException("QIO processor display lane count is invalid");
            }
            NBTTagList stored = data.getTagList("lanes", NBT.TAG_COMPOUND);
            if (stored.tagCount() > laneCount) {
                throw new QIOProcessingDataException("QIO processor display contains too many lanes");
            }
            List<Lane> lanes = new ArrayList<>(stored.tagCount());
            for (int index = 0; index < stored.tagCount(); index++) {
                lanes.add(Lane.read(stored.getCompoundTagAt(index)));
            }
            return new QIOProcessorDisplaySnapshot(laneCount, lanes);
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO processor display snapshot", e);
        }
    }

    public static final class Lane {

        private final int laneId;
        private final QIOProcessorLaneRuntime.State state;
        private final long operationCount;
        private final String routeKey;
        private final List<ItemStack> grid;
        private final ItemStack output;

        public Lane(int laneId, @Nonnull QIOProcessorLaneRuntime.State state,
              long operationCount, @Nonnull String routeKey, @Nonnull List<ItemStack> grid,
              @Nonnull ItemStack output) {
            if (laneId < 0 || laneId >= MAX_DISPLAY_LANES) {
                throw new IllegalArgumentException("QIO processor display lane id is invalid");
            }
            if (operationCount <= 0) {
                throw new IllegalArgumentException("QIO processor display operation count must be positive");
            }
            this.laneId = laneId;
            this.state = Objects.requireNonNull(state, "state");
            this.operationCount = operationCount;
            this.routeKey = requireRouteKey(routeKey);
            if (Objects.requireNonNull(grid, "grid").size() != GRID_SIZE) {
                throw new IllegalArgumentException("QIO processor display grid must contain nine slots");
            }
            List<ItemStack> copiedGrid = new ArrayList<>(GRID_SIZE);
            for (ItemStack stack : grid) {
                ItemStack copy = copy(stack);
                if (!copy.isEmpty()) {
                    copy.setCount(1);
                }
                copiedGrid.add(copy);
            }
            this.grid = Collections.unmodifiableList(copiedGrid);
            this.output = copy(output);
        }

        public int getLaneId() {
            return laneId;
        }

        @Nonnull
        public QIOProcessorLaneRuntime.State getState() {
            return state;
        }

        public long getOperationCount() {
            return operationCount;
        }

        @Nonnull
        public String getRouteKey() {
            return routeKey;
        }

        @Nonnull
        public ItemStack getGridStack(int slot) {
            return slot < 0 || slot >= GRID_SIZE ? ItemStack.EMPTY : grid.get(slot).copy();
        }

        @Nonnull
        public List<ItemStack> getGrid() {
            List<ItemStack> copy = new ArrayList<>(GRID_SIZE);
            grid.forEach(stack -> copy.add(stack.copy()));
            return Collections.unmodifiableList(copy);
        }

        @Nonnull
        public ItemStack getOutput() {
            return output.copy();
        }

        private NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            data.setInteger("laneId", laneId);
            data.setString("state", state.name());
            data.setLong("operationCount", operationCount);
            data.setString("routeKey", routeKey);
            NBTTagList storedGrid = new NBTTagList();
            grid.forEach(stack -> storedGrid.appendTag(writeStack(stack)));
            data.setTag("grid", storedGrid);
            data.setTag("output", writeStack(output));
            return data;
        }

        private static Lane read(NBTTagCompound data) throws QIOProcessingDataException {
            try {
                int laneId = data.getInteger("laneId");
                QIOProcessorLaneRuntime.State state = QIOProcessorLaneRuntime.State.valueOf(
                      data.getString("state"));
                long operationCount = data.getLong("operationCount");
                NBTTagList storedGrid = data.getTagList("grid", NBT.TAG_COMPOUND);
                if (storedGrid.tagCount() != GRID_SIZE) {
                    throw new QIOProcessingDataException(
                          "QIO processor display grid must contain nine slots");
                }
                List<ItemStack> grid = new ArrayList<>(GRID_SIZE);
                for (int slot = 0; slot < GRID_SIZE; slot++) {
                    grid.add(readStack(storedGrid.getCompoundTagAt(slot)));
                }
                return new Lane(laneId, state, operationCount, data.getString("routeKey"),
                      grid, readStack(data.getCompoundTag("output")));
            } catch (QIOProcessingDataException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new QIOProcessingDataException("Invalid QIO processor display lane", e);
            }
        }
    }

    private static String requireRouteKey(String routeKey) {
        String checked = Objects.requireNonNull(routeKey, "routeKey");
        if (checked.isEmpty() || checked.length() > MAX_ROUTE_KEY_LENGTH) {
            throw new IllegalArgumentException("QIO processor display route key is invalid");
        }
        return checked;
    }

    private static ItemStack copy(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private static NBTTagCompound writeStack(ItemStack stack) {
        return stack.isEmpty() ? new NBTTagCompound() :
              stack.writeToNBT(new NBTTagCompound());
    }

    private static ItemStack readStack(NBTTagCompound data) {
        return data.isEmpty() ? ItemStack.EMPTY : new ItemStack(data);
    }
}
