package mekanism.generators.common.slot;

import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class ReactorInventorySlot extends BasicInventorySlot {

    public static ReactorInventorySlot at(Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        return at(validator, alwaysTrueBi, listener, x, y);
    }

    public static ReactorInventorySlot at(Predicate<ItemStack> validator, BiPredicate<ItemStack, AutomationType> canInsert,
          @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(validator, "Item validity check cannot be null");
        Objects.requireNonNull(canInsert, "Item insertion check cannot be null");
        return new ReactorInventorySlot(validator, canInsert, listener, x, y);
    }

    protected ReactorInventorySlot(Predicate<ItemStack> validator, BiPredicate<ItemStack, AutomationType> canInsert,
          @Nullable IContentsListener listener, int x, int y) {
        super(notExternal, canInsert, validator, listener, x, y);
    }
}
