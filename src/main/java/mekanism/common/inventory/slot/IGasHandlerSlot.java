package mekanism.common.inventory.slot;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

public interface IGasHandlerSlot extends IInventorySlot {

    IExtendedGasTank getGasTank();

    boolean isDraining();

    boolean isFilling();

    void setDraining(boolean draining);

    void setFilling(boolean filling);

    default boolean isGasValidForSlot(@Nullable GasStack stack) {
        return stack != null && getGasTank().isValid(stack);
    }

    static boolean isGasItem(ItemStack stack) {
        return GasInventorySlot.isGasContainerItem(stack);
    }

    @Nullable
    static GasStack getGasContained(ItemStack stack) {
        return GasInventorySlot.getContainedGas(stack);
    }

    default boolean fillGasTank() {
        return GasInventorySlot.fillTank(this, getGasTank());
    }

    default boolean drainGasTank() {
        return drainGasTank(true);
    }

    default boolean drainGasTank(boolean doDraw) {
        return GasInventorySlot.drainTank(this, getGasTank(), doDraw);
    }

    default boolean fillGasTank(IInventorySlot outputSlot) {
        return GasInventorySlot.fillTank(this, getGasTank(), outputSlot);
    }

    default boolean drainGasTank(IInventorySlot outputSlot) {
        return GasInventorySlot.drainTank(this, getGasTank(), outputSlot);
    }
}
