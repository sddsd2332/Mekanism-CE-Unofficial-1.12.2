package mekanism.common.tile.component.config;

import mekanism.api.RelativeSide;
import mekanism.common.tile.component.config.slot.*;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ConfigInfo {

    private final Supplier<EnumFacing> facingSupplier;
    private final Map<RelativeSide, DataType> sideConfig = new EnumMap<>(RelativeSide.class);
    private final Map<DataType, ISlotInfo> slotInfo = new EnumMap<>(DataType.class);
    private final Map<Object, List<DataType>> containerTypeMapping = new HashMap<>();
    private Set<DataType> supportedDataTypes;
    private boolean canEject = true;
    private boolean ejecting;
    @Nullable
    private Set<RelativeSide> disabledSides;

    public ConfigInfo(@Nonnull Supplier<EnumFacing> facingSupplier) {
        this.facingSupplier = facingSupplier;
        for (RelativeSide side : RelativeSide.SIDES) {
            sideConfig.put(side, DataType.NONE);
        }
    }

    public boolean canEject() {
        return canEject;
    }

    public void setCanEject(boolean canEject) {
        this.canEject = canEject;
    }

    public boolean isEjecting() {
        return ejecting;
    }

    public void setEjecting(boolean ejecting) {
        this.ejecting = ejecting;
    }

    public ConfigInfo copy() {
        ConfigInfo copy = new ConfigInfo(facingSupplier);
        copy.canEject = canEject;
        copy.ejecting = ejecting;
        copy.sideConfig.clear();
        copy.sideConfig.putAll(sideConfig);
        copy.slotInfo.putAll(slotInfo);
        copy.containerTypeMapping.putAll(containerTypeMapping);
        if (supportedDataTypes != null) {
            copy.supportedDataTypes = EnumSet.copyOf(supportedDataTypes);
        }
        if (disabledSides != null) {
            copy.disabledSides = EnumSet.copyOf(disabledSides);
        }
        return copy;
    }

    public void copyConfigFrom(ConfigInfo other) {
        canEject = other.canEject;
        ejecting = other.ejecting;
        sideConfig.clear();
        sideConfig.putAll(other.sideConfig);
        disabledSides = other.disabledSides == null ? null : EnumSet.copyOf(other.disabledSides);
    }

    public void addDisabledSides(@Nonnull RelativeSide... sides) {
        if (disabledSides == null) {
            disabledSides = EnumSet.noneOf(RelativeSide.class);
        }
        for (RelativeSide side : sides) {
            disabledSides.add(side);
            sideConfig.put(side, DataType.NONE);
        }
    }

    public boolean isSideEnabled(@Nonnull RelativeSide side) {
        return disabledSides == null || !disabledSides.contains(side);
    }

    @Nonnull
    public DataType getDataType(@Nonnull RelativeSide side) {
        return sideConfig.getOrDefault(side, DataType.NONE);
    }

    public Set<Map.Entry<RelativeSide, DataType>> getSideConfig() {
        return sideConfig.entrySet();
    }

    public boolean setDataType(@Nonnull DataType dataType, @Nonnull RelativeSide side) {
        return isSideEnabled(side) && sideConfig.put(side, dataType) != dataType;
    }

    public void setDataType(@Nonnull DataType dataType, @Nonnull RelativeSide... sides) {
        for (RelativeSide side : sides) {
            setDataType(dataType, side);
        }
    }

    @Nonnull
    public Set<DataType> getSupportedDataTypes() {
        if (supportedDataTypes == null) {
            supportedDataTypes = EnumSet.of(DataType.NONE);
            supportedDataTypes.addAll(slotInfo.keySet());
        }
        return supportedDataTypes;
    }

    public void fill(@Nonnull DataType dataType) {
        for (RelativeSide side : RelativeSide.SIDES) {
            setDataType(dataType, side);
        }
    }

    @Nullable
    public ISlotInfo getSlotInfo(@Nonnull RelativeSide side) {
        return getSlotInfo(getDataType(side));
    }

    @Nullable
    public ISlotInfo getSlotInfo(@Nonnull DataType dataType) {
        return slotInfo.get(dataType);
    }

    public void addSlotInfo(@Nonnull DataType dataType, @Nonnull ISlotInfo info) {
        removeContainerTypeMapping(dataType);
        slotInfo.put(dataType, info);
        if (supportedDataTypes != null) {
            supportedDataTypes.add(dataType);
        }
        if (info instanceof GasSlotInfo gasSlotInfo) {
            gasSlotInfo.getTanks().forEach(tank -> addContainerTypeMapping(tank, dataType));
        } else if (info instanceof FluidSlotInfo fluidSlotInfo) {
            fluidSlotInfo.getTanks().forEach(tank -> addContainerTypeMapping(tank, dataType));
        } else if (info instanceof EnergySlotInfo energySlotInfo) {
            energySlotInfo.getContainers().forEach(container -> addContainerTypeMapping(container, dataType));
        } else if (info instanceof HeatSlotInfo heatSlotInfo) {
            heatSlotInfo.getHeatCapacitors().forEach(capacitor -> addContainerTypeMapping(capacitor, dataType));
        } else if (info instanceof InventorySlotInfo inventorySlotInfo) {
            inventorySlotInfo.getSlots().forEach(slot -> addContainerTypeMapping(slot, dataType));
        }
    }

    public void removeSlotInfo(@Nonnull DataType dataType) {
        slotInfo.remove(dataType);
        removeContainerTypeMapping(dataType);
        supportedDataTypes = null;
    }

    private void removeContainerTypeMapping(DataType dataType) {
        containerTypeMapping.entrySet().removeIf(entry -> {
            entry.getValue().remove(dataType);
            return entry.getValue().isEmpty();
        });
    }

    private void addContainerTypeMapping(Object container, DataType dataType) {
        containerTypeMapping.computeIfAbsent(container, ignored -> new ArrayList<>()).add(dataType);
    }

    public List<DataType> getDataTypeForContainer(Object container) {
        return containerTypeMapping.getOrDefault(container, Collections.emptyList());
    }

    public boolean supports(DataType dataType) {
        return dataType == DataType.NONE || slotInfo.containsKey(dataType);
    }

    public void setDefaults() {
        if (slotInfo.containsKey(DataType.INPUT)) {
            fill(DataType.INPUT);
        }
        if (slotInfo.containsKey(DataType.OUTPUT)) {
            setDataType(DataType.OUTPUT, RelativeSide.RIGHT);
        }
        if (slotInfo.containsKey(DataType.EXTRA)) {
            setDataType(DataType.EXTRA, RelativeSide.BOTTOM);
        }
        if (slotInfo.containsKey(DataType.ENERGY)) {
            setDataType(DataType.ENERGY, RelativeSide.BACK);
        }
    }

    public Set<EnumFacing> getSidesForData(@Nonnull DataType dataType) {
        return getSides(type -> type == dataType);
    }

    public Set<EnumFacing> getSides(Predicate<DataType> predicate) {
        EnumFacing facing = facingSupplier.get();
        Set<EnumFacing> directions = null;
        for (Map.Entry<RelativeSide, DataType> entry : sideConfig.entrySet()) {
            if (predicate.test(entry.getValue())) {
                if (directions == null) {
                    directions = EnumSet.noneOf(EnumFacing.class);
                }
                directions.add(entry.getKey().getDirection(facing));
            }
        }
        return directions == null ? Collections.emptySet() : directions;
    }

    public Set<EnumFacing> getAllOutputtingSides() {
        return getSides(DataType::canOutput);
    }

    public Set<EnumFacing> getSidesForOutput(DataType outputType) {
        return getSides(type -> type == outputType || type == DataType.INPUT_OUTPUT || type == DataType.INPUT_EXTRA_OUTPUT);
    }

    @Nonnull
    public DataType incrementDataType(@Nonnull RelativeSide relativeSide) {
        DataType current = getDataType(relativeSide);
        if (isSideEnabled(relativeSide)) {
            Set<DataType> supportedDataTypes = getSupportedDataTypes();
            DataType newType = current.getNext(supportedDataTypes::contains);
            sideConfig.put(relativeSide, newType);
            return newType;
        }
        return current;
    }

    @Nonnull
    public DataType decrementDataType(@Nonnull RelativeSide relativeSide) {
        DataType current = getDataType(relativeSide);
        if (isSideEnabled(relativeSide)) {
            Set<DataType> supportedDataTypes = getSupportedDataTypes();
            DataType newType = current.getPrevious(supportedDataTypes::contains);
            sideConfig.put(relativeSide, newType);
            return newType;
        }
        return current;
    }
}
