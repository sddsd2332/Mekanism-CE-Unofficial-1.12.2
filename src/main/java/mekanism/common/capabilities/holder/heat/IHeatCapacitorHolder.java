package mekanism.common.capabilities.holder.heat;

import mekanism.api.heat.IHeatCapacitor;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface IHeatCapacitorHolder extends IHolder {

    @Nonnull
    List<IHeatCapacitor> getHeatCapacitors(@Nullable EnumFacing side);
}
