package mekanism.common.capabilities.holder.heat;

import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.capabilities.holder.QuantumEntangloporterConfigHolder;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
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
        return entangloporter.hasFrequency() ? entangloporter.getFreq().getHeatCapacitors(null) : Collections.emptyList();
    }
}
