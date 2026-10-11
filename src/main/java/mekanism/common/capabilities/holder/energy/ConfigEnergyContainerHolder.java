package mekanism.common.capabilities.holder.energy;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.capabilities.holder.ConfigHolder;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.slot.EnergySlotInfo;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class ConfigEnergyContainerHolder extends ConfigHolder<IEnergyContainer> implements IEnergyContainerHolder {

    public ConfigEnergyContainerHolder(ISideConfiguration sideConfiguration) {
        super(sideConfiguration);
    }

    public ConfigEnergyContainerHolder(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        super(facingSupplier, configSupplier);
    }

    void addContainer(@Nonnull IEnergyContainer container) {
        slots.add(container);
    }

    @Override
    protected TransmissionType getTransmissionType() {
        return TransmissionType.ENERGY;
    }

    /**
     * Resolve the native holder's containers and input permission from one current side view.
     */
    public boolean hasInputEnergyContainers(@Nullable EnumFacing direction) {
        if (direction == null) {
            return false;
        }
        ISlotInfo slotInfo = getSlotInfo(direction);
        if (isNoConfig(slotInfo)) {
            return !slots.isEmpty();
        }
        if (slotInfo instanceof EnergySlotInfo energyInfo) {
            if (energyInfo.getClass() == EnergySlotInfo.class) {
                return !energyInfo.getContainers().isEmpty() && energyInfo.canInput();
            }
            // Custom slot getters may notify a config change; retain the second live lookup.
            return !getEnergyContainers(direction).isEmpty() && canInsert(direction);
        }
        return false;
    }

    @Nonnull
    @Override
    public List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing direction) {
        return getSlots(direction, slotInfo -> slotInfo instanceof EnergySlotInfo ? ((EnergySlotInfo) slotInfo).getContainers() : Collections.emptyList());
    }
}
