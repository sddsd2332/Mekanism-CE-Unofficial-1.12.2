package mekanism.common.capabilities.holder.slot;

import mekanism.api.inventory.IInventorySlot;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReadOnlyInventorySlotHolder implements IInventorySlotHolder {

    private final List<IInventorySlot> inventorySlots = new ArrayList<>();

    ReadOnlyInventorySlotHolder() {
    }

    void addSlot(@Nonnull IInventorySlot slot) {
        inventorySlots.add(slot);
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing direction) {
        return direction == null ? inventorySlots : Collections.emptyList();
    }

    @Override
    public boolean canInsert(@Nullable EnumFacing direction) {
        return false;
    }

    @Override
    public boolean canExtract(@Nullable EnumFacing direction) {
        return false;
    }
}
