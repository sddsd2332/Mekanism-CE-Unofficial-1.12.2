package mekanism.common.inventory.slot;

import mekanism.api.IContentsListener;
import mekanism.common.tile.factory.TileEntityFactory;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Predicate;

public class FactoryExtraInventorySlot extends InputInventorySlot {

    public static FactoryExtraInventorySlot create(TileEntityFactory factory, Predicate<ItemStack> insertPredicate,
                                                   Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(factory, "Factory cannot be null");
        Objects.requireNonNull(insertPredicate, "Insertion check cannot be null");
        Objects.requireNonNull(validator, "Item validity check cannot be null");
        return new FactoryExtraInventorySlot(factory, insertPredicate, validator, listener, x, y);
    }

    private final TileEntityFactory factory;

    private FactoryExtraInventorySlot(TileEntityFactory factory, Predicate<ItemStack> insertPredicate, Predicate<ItemStack> validator,
                                      @Nullable IContentsListener listener, int x, int y) {
        super(insertPredicate, validator, listener, x, y);
        this.factory = factory;
    }

    @Override
    public int getLimit(@Nonnull ItemStack stack) {
        if (factory.getExtraSlotLimitMultiplier()) {
            return super.getLimit(stack) * factory.getProcessCount();
        }
        return super.getLimit(stack);
    }
}
