package mekanism.common.inventory.slot;

import mekanism.api.IContentsListener;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.item.ItemCraftingFormula;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Predicate;

public class FormulaInventorySlot extends BasicInventorySlot {

    private static final Predicate<ItemStack> validator = stack -> stack.getItem() instanceof ItemCraftingFormula;

    @Nonnull
    public static FormulaInventorySlot at(@Nullable IContentsListener listener, int x, int y) {
        return new FormulaInventorySlot(listener, x, y);
    }

    private FormulaInventorySlot(@Nullable IContentsListener listener, int x, int y) {
        super(1, alwaysTrueBi, alwaysTrueBi, validator, listener, x, y);
        setSlotOverlay(SlotOverlay.FORMULA);
    }
}
