package mekanism.common.capabilities.holder.energy;

import mekanism.api.energy.IEnergyContainer;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface IEnergyContainerHolder extends IHolder {

    @Nonnull
    List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing direction);
}
