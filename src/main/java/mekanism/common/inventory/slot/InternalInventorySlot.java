package mekanism.common.inventory.slot;

import mekanism.api.IContentsListener;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;

import javax.annotation.Nullable;

public class InternalInventorySlot extends BasicInventorySlot {

    public static InternalInventorySlot create(@Nullable IContentsListener listener) {
        return new InternalInventorySlot(listener);
    }

    private InternalInventorySlot(@Nullable IContentsListener listener) {
        super(internalOnly, internalOnly, alwaysTrue, listener, 0, 0);
    }

    @Nullable
    @Override
    public InventoryContainerSlot createContainerSlot() {
        return null;
    }
}
