package mekanism.common.capabilities.holder.heat;

import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.capabilities.holder.QuantumEntangloporterConfigHolder;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import mekanism.common.tile.component.config.slot.HeatSlotInfo;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class QuantumEntangloporterHeatCapacitorHolder extends QuantumEntangloporterConfigHolder<IHeatCapacitor> implements IHeatCapacitorHolder {

    public QuantumEntangloporterHeatCapacitorHolder(TileEntityQuantumEntangloporter entangloporter) {
        super(entangloporter);
    }

    @Override
    protected TransmissionType getTransmissionType() {
        return TransmissionType.HEAT;
    }

    @Nonnull
    @Override
    public List<IHeatCapacitor> getHeatCapacitors(@Nullable EnumFacing side) {
        if (!entangloporter.hasFrequency()) {
            return Collections.emptyList();
        } else if (side == null) {
            return entangloporter.getFreq().getHeatCapacitors(null);
        }
        ISlotInfo slotInfo = getSlotInfo(side);
        if (isNoConfig(slotInfo)) {
            return entangloporter.getFreq().getHeatCapacitors(null);
        } else if (slotInfo instanceof HeatSlotInfo heatSlotInfo) {
            return heatSlotInfo.getHeatCapacitors();
        }
        return Collections.emptyList();
    }
}
