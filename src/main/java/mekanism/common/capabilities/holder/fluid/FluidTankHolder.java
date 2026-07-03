package mekanism.common.capabilities.holder.fluid;

import mekanism.api.RelativeSide;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.capabilities.holder.BasicHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

public class FluidTankHolder extends BasicHolder<IExtendedFluidTank> implements IFluidTankHolder {

    FluidTankHolder(Supplier<EnumFacing> facingSupplier) {
        super(facingSupplier);
    }

    void addTank(@Nonnull IExtendedFluidTank tank, RelativeSide... sides) {
        addSlotInternal(tank, sides);
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getTanks(@Nullable EnumFacing direction) {
        return getSlots(direction);
    }
}
