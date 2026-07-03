package mekanism.common.inventory.slot;

import mekanism.api.IContentsListener;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class InputInventorySlot extends BasicInventorySlot implements IRecipeInputInventorySlot {

    public static InputInventorySlot at(@Nullable IContentsListener listener, int x, int y) {
        return at(alwaysTrue, listener, x, y);
    }

    public static InputInventorySlot at(Predicate<ItemStack> isItemValid, @Nullable IContentsListener listener, int x, int y) {
        return at(alwaysTrue, isItemValid, listener, x, y);
    }

    public static InputInventorySlot at(Predicate<ItemStack> insertPredicate, Predicate<ItemStack> isItemValid, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(insertPredicate, "Insertion check cannot be null");
        Objects.requireNonNull(isItemValid, "Item validity check cannot be null");
        return new InputInventorySlot(insertPredicate, isItemValid, listener, x, y);
    }

    private BiPredicate<ItemStack, EnumFacing> canAutoPull = (stack, side) -> true;

    protected InputInventorySlot(Predicate<ItemStack> insertPredicate, Predicate<ItemStack> isItemValid, @Nullable IContentsListener listener, int x, int y) {
        super(notExternal, (stack, automationType) -> insertPredicate.test(stack), isItemValid, listener, x, y);
        setSlotType(ContainerSlotType.INPUT);
    }

    public InputInventorySlot setAutoPullValidator(BiPredicate<ItemStack, EnumFacing> canAutoPull) {
        this.canAutoPull = Objects.requireNonNull(canAutoPull, "Auto-pull validator cannot be null");
        return this;
    }

    @Override
    public boolean canAutoPull(@Nonnull ItemStack stack, @Nonnull EnumFacing side) {
        return canAutoPull.test(stack, side);
    }
}
