package mekanism.common.capabilities.holder.gas;

import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.capabilities.holder.QuantumEntangloporterConfigHolder;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import mekanism.common.tile.component.config.slot.GasSlotInfo;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class QuantumEntangloporterGasTankHolder extends QuantumEntangloporterConfigHolder<IExtendedGasTank> implements IGasTankHolder {

    public QuantumEntangloporterGasTankHolder(TileEntityQuantumEntangloporter entangloporter) {
        super(entangloporter);
    }

    @Override
    protected TransmissionType getTransmissionType() {
        return TransmissionType.GAS;
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getTanks(@Nullable EnumFacing side) {
        return entangloporter.hasFrequency() ? entangloporter.getFreq().getGasTanks(side) : Collections.emptyList();
    }

    @Override
    public boolean canInsert(@Nullable EnumFacing side, @Nonnull IExtendedGasTank tank) {
        return canInteract(side, tank, true);
    }

    @Override
    public boolean canExtract(@Nullable EnumFacing side, @Nonnull IExtendedGasTank tank) {
        return canInteract(side, tank, false);
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getTanksForInsert(@Nullable EnumFacing side) {
        return getTanksForInteract(side, true);
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getTanksForExtract(@Nullable EnumFacing side) {
        return getTanksForInteract(side, false);
    }

    @Nonnull
    private List<IExtendedGasTank> getTanksForInteract(@Nullable EnumFacing side, boolean insert) {
        if (!entangloporter.hasFrequency()) {
            return Collections.emptyList();
        } else if (side == null) {
            return getTanks(null);
        }
        ISlotInfo slotInfo = getSlotInfo(side);
        if (isNoConfig(slotInfo)) {
            return getTanks(null);
        } else if (!(slotInfo instanceof GasSlotInfo)) {
            return Collections.emptyList();
        }
        GasSlotInfo gasSlotInfo = (GasSlotInfo) slotInfo;
        return insert ? gasSlotInfo.getInputTanks() : gasSlotInfo.getOutputTanks();
    }

    private boolean canInteract(@Nullable EnumFacing side, @Nonnull IExtendedGasTank tank, boolean insert) {
        return getTanksForInteract(side, insert).contains(tank);
    }
}
