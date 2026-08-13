package mekanism.common.inventory.container.item;

import javax.annotation.Nonnull;

/** Neutral marker for containers whose authority is one exact player-held ItemStack. */
public interface IItemStackBackedContainer {

    @Nonnull
    ItemStackSlotAccess getItemAccess();
}
