package mekanism.common.capabilities.holder.fluid;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.capabilities.holder.QuantumEntangloporterConfigHolder;
import mekanism.common.tile.TileEntityQuantumEntangloporter;
import mekanism.common.tile.component.config.slot.FluidSlotInfo;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class QuantumEntangloporterFluidTankHolder extends QuantumEntangloporterConfigHolder<IExtendedFluidTank> implements IFluidTankHolder {

    public QuantumEntangloporterFluidTankHolder(TileEntityQuantumEntangloporter entangloporter) {
        super(entangloporter);
    }

    @Override
    protected TransmissionType getTransmissionType() {
        return TransmissionType.FLUID;
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getTanks(@Nullable EnumFacing side) {
        return entangloporter.hasFrequency() ? entangloporter.getFreq().getFluidTanks(side) : Collections.emptyList();
    }

    @Override
    public boolean canInsert(@Nullable EnumFacing side, @Nonnull IExtendedFluidTank tank) {
        return canInteract(side, tank, true);
    }

    @Override
    public boolean canExtract(@Nullable EnumFacing side, @Nonnull IExtendedFluidTank tank) {
        return canInteract(side, tank, false);
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getTanksForInsert(@Nullable EnumFacing side) {
        return getTanksForInteract(side, true);
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getTanksForExtract(@Nullable EnumFacing side) {
        return getTanksForInteract(side, false);
    }

    @Nonnull
    private List<IExtendedFluidTank> getTanksForInteract(@Nullable EnumFacing side, boolean insert) {
        if (!entangloporter.hasFrequency()) {
            return Collections.emptyList();
        } else if (side == null) {
            return getTanks(null);
        }
        ISlotInfo slotInfo = getSlotInfo(side);
        if (isNoConfig(slotInfo)) {
            return getTanks(null);
        } else if (!(slotInfo instanceof FluidSlotInfo)) {
            return Collections.emptyList();
        }
        FluidSlotInfo fluidSlotInfo = (FluidSlotInfo) slotInfo;
        return insert ? fluidSlotInfo.getInputTanks() : fluidSlotInfo.getOutputTanks();
    }

    private boolean canInteract(@Nullable EnumFacing side, @Nonnull IExtendedFluidTank tank, boolean insert) {
        return getTanksForInteract(side, insert).contains(tank);
    }
}
