package mekanism.common.capabilities.holder.fluid;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.capabilities.holder.ConfigHolder;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.slot.FluidSlotInfo;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class ConfigFluidTankHolder extends ConfigHolder<IExtendedFluidTank> implements IFluidTankHolder {

    public ConfigFluidTankHolder(ISideConfiguration sideConfiguration) {
        super(sideConfiguration);
    }

    public ConfigFluidTankHolder(Supplier<EnumFacing> facingSupplier, Supplier<TileComponentConfig> configSupplier) {
        super(facingSupplier, configSupplier);
    }

    void addTank(@Nonnull IExtendedFluidTank tank) {
        slots.add(tank);
    }

    @Override
    protected TransmissionType getTransmissionType() {
        return TransmissionType.FLUID;
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getTanks(@Nullable EnumFacing direction) {
        return getSlots(direction, slotInfo -> slotInfo instanceof FluidSlotInfo ? ((FluidSlotInfo) slotInfo).getTanks() : Collections.emptyList());
    }

    @Override
    public boolean canInsert(@Nullable EnumFacing direction, @Nonnull IExtendedFluidTank tank) {
        return canInteract(direction, tank, true);
    }

    @Override
    public boolean canExtract(@Nullable EnumFacing direction, @Nonnull IExtendedFluidTank tank) {
        return canInteract(direction, tank, false);
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getTanksForInsert(@Nullable EnumFacing direction) {
        return getSlots(direction, slotInfo -> slotInfo instanceof FluidSlotInfo ? ((FluidSlotInfo) slotInfo).getInputTanks() : Collections.emptyList());
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getTanksForExtract(@Nullable EnumFacing direction) {
        return getSlots(direction, slotInfo -> slotInfo instanceof FluidSlotInfo ? ((FluidSlotInfo) slotInfo).getOutputTanks() : Collections.emptyList());
    }

    private boolean canInteract(@Nullable EnumFacing direction, @Nonnull IExtendedFluidTank tank, boolean insert) {
        if (direction == null) {
            return slots.contains(tank);
        }
        ISlotInfo slotInfo = getSlotInfo(direction);
        if (isNoConfig(slotInfo)) {
            return slots.contains(tank);
        }
        if (!(slotInfo instanceof FluidSlotInfo fluidSlotInfo)) {
            return false;
        }
        return insert ? fluidSlotInfo.canInput(tank) : fluidSlotInfo.canOutput(tank);
    }
}
