package mekanism.common.capabilities.holder.gas;

import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface IGasTankHolder extends IHolder {

    @Nonnull
    List<IExtendedGasTank> getTanks(@Nullable EnumFacing direction);

    default boolean canInsert(@Nullable EnumFacing direction, @Nonnull IExtendedGasTank tank) {
        return direction == null || canInsert(direction);
    }

    default boolean canExtract(@Nullable EnumFacing direction, @Nonnull IExtendedGasTank tank) {
        return direction == null || canExtract(direction);
    }

    @Nonnull
    default List<IExtendedGasTank> getTanksForInsert(@Nullable EnumFacing direction) {
        return getTanks(direction);
    }

    @Nonnull
    default List<IExtendedGasTank> getTanksForExtract(@Nullable EnumFacing direction) {
        return getTanks(direction);
    }
}
