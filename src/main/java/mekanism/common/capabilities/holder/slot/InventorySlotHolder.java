package mekanism.common.capabilities.holder.slot;

import mekanism.api.RelativeSide;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.BasicHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class InventorySlotHolder extends BasicHolder<IInventorySlot> implements IInventorySlotHolder {

    @Nullable
    private final Predicate<RelativeSide> insertPredicate;
    @Nullable
    private final Predicate<RelativeSide> extractPredicate;

    InventorySlotHolder(Supplier<EnumFacing> facingSupplier, @Nullable Predicate<RelativeSide> insertPredicate, @Nullable Predicate<RelativeSide> extractPredicate) {
        super(facingSupplier);
        this.insertPredicate = insertPredicate;
        this.extractPredicate = extractPredicate;
    }

    void addSlot(@Nonnull IInventorySlot slot, RelativeSide... sides) {
        addSlotInternal(slot, sides);
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing direction) {
        return getSlots(direction);
    }

    @Override
    public boolean canInsert(@Nullable EnumFacing direction) {
        return direction != null && (insertPredicate == null || insertPredicate.test(RelativeSide.fromDirections(facingSupplier.get(), direction)));
    }

    @Override
    public boolean canExtract(@Nullable EnumFacing direction) {
        return direction != null && (extractPredicate == null || extractPredicate.test(RelativeSide.fromDirections(facingSupplier.get(), direction)));
    }
}
