package mekanism.generators.common.slot;

import mekanism.api.IContentsListener;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Predicate;

public class ReactorInventorySlot extends BasicInventorySlot {

    public static ReactorInventorySlot at(Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(validator, "Item validity check cannot be null");
        return new ReactorInventorySlot(validator, listener, x, y);
    }

    protected ReactorInventorySlot(Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(notExternal, alwaysTrueBi, validator, listener, x, y);
    }
}
