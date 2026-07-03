package mekanism.common.capabilities.holder.energy;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.capabilities.holder.ConfigHolder;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.slot.EnergySlotInfo;
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

    @Nonnull
    @Override
    public List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing direction) {
        return getSlots(direction, slotInfo -> slotInfo instanceof EnergySlotInfo ? ((EnergySlotInfo) slotInfo).getContainers() : Collections.emptyList());
    }
}
