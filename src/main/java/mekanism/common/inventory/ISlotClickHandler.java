package mekanism.common.inventory;

import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.lib.inventory.HashedItem.UUIDAwareHashedItem;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

public interface ISlotClickHandler {

    void onClick(Supplier<@Nullable IScrollableSlot> slotProvider, int button, boolean hasShiftDown, ItemStack heldItem);

    interface IScrollableSlot {

        default HashedItem asRawHashedItem() {
            HashedItem item = item();
            return item instanceof UUIDAwareHashedItem ? ((UUIDAwareHashedItem) item).asRawHashedItem() : item;
        }

        HashedItem item();

        UUID itemUUID();

        long count();

        default String getDisplayName() {
            return getInternalStack().getDisplayName();
        }

        default String getModID() {
            return MekanismUtils.getModId(getInternalStack());
        }

        default ItemStack getInternalStack() {
            return item().getInternalStack();
        }
    }
}
