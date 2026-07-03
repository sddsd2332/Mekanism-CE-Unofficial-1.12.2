package mekanism.common.capabilities.holder.heat;

import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.capabilities.holder.ConfigHolder;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.slot.HeatSlotInfo;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class ConfigHeatCapacitorHolder extends ConfigHolder<IHeatCapacitor> implements IHeatCapacitorHolder {

    public ConfigHeatCapacitorHolder(ISideConfiguration sideConfiguration) {
        super(sideConfiguration);
    }

    public ConfigHeatCapacitorHolder(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        super(facingSupplier, configSupplier);
    }

    void addCapacitor(@Nonnull IHeatCapacitor capacitor) {
        slots.add(capacitor);
    }

    @Override
    protected TransmissionType getTransmissionType() {
        return TransmissionType.HEAT;
    }

    @Nonnull
    @Override
    public List<IHeatCapacitor> getHeatCapacitors(@Nullable EnumFacing side) {
        return getSlots(side, slotInfo -> slotInfo instanceof HeatSlotInfo ? ((HeatSlotInfo) slotInfo).getHeatCapacitors() : Collections.emptyList());
    }
}
