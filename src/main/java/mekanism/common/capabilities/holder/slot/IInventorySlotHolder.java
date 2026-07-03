package mekanism.common.capabilities.holder.slot;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface IInventorySlotHolder extends IHolder {

    @Nonnull
    List<IInventorySlot> getInventorySlots(@Nullable EnumFacing side);

    default boolean canInsert(@Nullable EnumFacing side, @Nonnull IInventorySlot slot) {
        return side == null || canInsert(side) && getInventorySlots(side).contains(slot);
    }

    default boolean canExtract(@Nullable EnumFacing side, @Nonnull IInventorySlot slot) {
        return side == null || canExtract(side) && getInventorySlots(side).contains(slot);
    }
}
