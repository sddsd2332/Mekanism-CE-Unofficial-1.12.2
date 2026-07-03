package mekanism.common.inventory.slot;

import mekanism.api.IContentsListener;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EntangloporterInventorySlot extends BasicInventorySlot {

    @Nonnull
    public static EntangloporterInventorySlot create(@Nullable IContentsListener listener) {
        return new EntangloporterInventorySlot(listener);
    }

    private EntangloporterInventorySlot(@Nullable IContentsListener listener) {
        super(alwaysTrueBi, alwaysTrueBi, alwaysTrue, listener, 0, 0);
    }

    @Nullable
    @Override
    public InventoryContainerSlot createContainerSlot() {
        return null;
    }
}
