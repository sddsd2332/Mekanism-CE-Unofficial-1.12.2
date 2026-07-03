package mekanism.common.capabilities.holder.energy;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.capabilities.holder.QuantumEntangloporterConfigHolder;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class QuantumEntangloporterEnergyContainerHolder extends QuantumEntangloporterConfigHolder<IEnergyContainer> implements IEnergyContainerHolder {

    public QuantumEntangloporterEnergyContainerHolder(TileEntityQuantumEntangloporter entangloporter) {
        super(entangloporter);
    }

    @Override
    protected TransmissionType getTransmissionType() {
        return TransmissionType.ENERGY;
    }

    @Nonnull
    @Override
    public List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing side) {
        return entangloporter.hasFrequency() ? entangloporter.getFreq().getEnergyContainers(null) : Collections.emptyList();
    }
}
