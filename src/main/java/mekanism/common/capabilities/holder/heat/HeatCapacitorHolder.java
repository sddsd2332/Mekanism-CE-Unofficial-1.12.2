package mekanism.common.capabilities.holder.heat;

import mekanism.api.RelativeSide;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.common.capabilities.holder.BasicHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

public class HeatCapacitorHolder extends BasicHolder<IHeatCapacitor> implements IHeatCapacitorHolder {

    HeatCapacitorHolder(Supplier<EnumFacing> facingSupplier) {
        super(facingSupplier);
    }

    void addCapacitor(@Nonnull IHeatCapacitor capacitor, RelativeSide... sides) {
        addSlotInternal(capacitor, sides);
    }

    @Nonnull
    @Override
    public List<IHeatCapacitor> getHeatCapacitors(@Nullable EnumFacing side) {
        return getSlots(side);
    }
}
