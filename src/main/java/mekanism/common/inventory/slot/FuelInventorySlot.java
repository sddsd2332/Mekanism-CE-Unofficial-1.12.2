package mekanism.common.inventory.slot;

import mekanism.api.Action;
import mekanism.api.IContentsListener;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public class FuelInventorySlot extends BasicInventorySlot {

    public static FuelInventorySlot forFuel(ToIntFunction<ItemStack> fuelValue, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(fuelValue, "Fuel value calculator cannot be null");
        return new FuelInventorySlot(stack -> fuelValue.applyAsInt(stack) == 0, stack -> fuelValue.applyAsInt(stack) > 0, alwaysTrue, fuelValue, listener, x, y);
    }

    private final ToIntFunction<ItemStack> fuelValue;

    private FuelInventorySlot(Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert, Predicate<ItemStack> validator, ToIntFunction<ItemStack> fuelValue,
          @Nullable IContentsListener listener, int x, int y) {
        super(canExtract, canInsert, validator, listener, x, y);
        this.fuelValue = fuelValue;
    }

    public int burn() {
        if (isEmpty()) {
            return 0;
        }
        int burnTime = fuelValue.applyAsInt(current);
        if (burnTime > 0) {
            ItemStack preShrunk = current.copy();
            if (current.getCount() == 1) {
                setStackUnchecked(preShrunk.getItem().getContainerItem(preShrunk));
            } else if (preShrunk.getItem().hasContainerItem(preShrunk)) {
                return 0;
            } else {
                shrinkStack(1, Action.EXECUTE);
            }
        }
        return burnTime;
    }
}
