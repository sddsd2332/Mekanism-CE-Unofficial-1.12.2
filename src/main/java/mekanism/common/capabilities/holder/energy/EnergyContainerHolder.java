package mekanism.common.capabilities.holder.energy;

import mekanism.api.RelativeSide;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.capabilities.holder.BasicHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

public class EnergyContainerHolder extends BasicHolder<IEnergyContainer> implements IEnergyContainerHolder {

    EnergyContainerHolder(Supplier<EnumFacing> facingSupplier) {
        super(facingSupplier);
    }

    void addContainer(@Nonnull IEnergyContainer container, RelativeSide... sides) {
        addSlotInternal(container, sides);
    }

    @Nonnull
    @Override
    public List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing direction) {
        return getSlots(direction);
    }
}
