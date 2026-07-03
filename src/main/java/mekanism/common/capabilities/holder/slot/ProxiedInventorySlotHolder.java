package mekanism.common.capabilities.holder.slot;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.ProxiedHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class ProxiedInventorySlotHolder extends ProxiedHolder implements IInventorySlotHolder {

    private final Function<EnumFacing, List<IInventorySlot>> slotFunction;

    public static ProxiedInventorySlotHolder create(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IInventorySlot>> slotFunction) {
        return new ProxiedInventorySlotHolder(insertPredicate, extractPredicate, slotFunction);
    }

    private ProxiedInventorySlotHolder(Predicate<EnumFacing> insertPredicate, Predicate<EnumFacing> extractPredicate,
          Function<EnumFacing, List<IInventorySlot>> slotFunction) {
        super(insertPredicate, extractPredicate);
        this.slotFunction = slotFunction;
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing side) {
        return slotFunction.apply(side);
    }
}
