package mekanism.common.capabilities.holder.fluid;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface IFluidTankHolder extends IHolder {

    @Nonnull
    List<IExtendedFluidTank> getTanks(@Nullable EnumFacing direction);

    default boolean canInsert(@Nullable EnumFacing direction, @Nonnull IExtendedFluidTank tank) {
        return direction == null || canInsert(direction);
    }

    default boolean canExtract(@Nullable EnumFacing direction, @Nonnull IExtendedFluidTank tank) {
        return direction == null || canExtract(direction);
    }

    @Nonnull
    default List<IExtendedFluidTank> getTanksForInsert(@Nullable EnumFacing direction) {
        return getTanks(direction);
    }

    @Nonnull
    default List<IExtendedFluidTank> getTanksForExtract(@Nullable EnumFacing direction) {
        return getTanks(direction);
    }
}
