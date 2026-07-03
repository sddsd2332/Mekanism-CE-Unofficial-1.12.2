package mekanism.common.inventory.slot;

import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;

public interface IRecipeInputInventorySlot extends IInventorySlot {

    boolean canAutoPull(@Nonnull ItemStack stack, @Nonnull EnumFacing side);
}
