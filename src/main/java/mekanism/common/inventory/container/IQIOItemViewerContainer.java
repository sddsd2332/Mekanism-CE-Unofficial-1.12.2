package mekanism.common.inventory.container;

import mekanism.common.content.qio.IQIOCraftingWindowHolder;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.inventory.container.slot.VirtualCraftingSlot;
import mekanism.common.network.qio.PacketQIOClearCraftingWindow;
import mekanism.common.network.qio.PacketQIOViewerData;
import mekanism.common.util.InventoryUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Shared contract used by crafting packets, GUI windows, and JEI. */
public interface IQIOItemViewerContainer {

    List<QIOResourceEntry> getResourceEntries();

    VirtualCraftingSlot getCraftingWindowSlot(byte window, int slot);

    @Nullable
    QIOCraftingWindow getCraftingWindow(byte window);

    int getSlotElementStartIndex();

    @Nullable
    QIOFrequency getServerFrequency();

    @Nullable
    SelectedWindowData getSelectedWindow(UUID player);

    void setSelectedWindow(UUID player, @Nullable SelectedWindowData selectedWindow);

    default byte getSelectedCraftingGrid(UUID player) {
        SelectedWindowData selected = getSelectedWindow(player);
        return selected != null && selected.type == SelectedWindowData.WindowType.CRAFTING ? selected.extraData : -1;
    }

    default void clearCraftingWindow(byte window, boolean toPlayerInv) {
        if (this instanceof Container) {
            Container container = (Container) this;
            mekanism.common.Mekanism.packetHandler.sendToServer(new PacketQIOClearCraftingWindow.Message(container.windowId, window, toPlayerInv));
        }
    }

    default void sendViewerSync(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        QIOFrequency frequency = getServerFrequency();
        int windowId = this instanceof Container ? ((Container) this).windowId : -1;
        if (frequency == null) {
            PacketQIOViewerData.sendBatch((EntityPlayerMP) player, windowId, Collections.emptyList(), 0, 0);
        } else {
            PacketQIOViewerData.sendBatch((EntityPlayerMP) player, windowId, frequency.getResourceEntries(),
                  frequency.getTotalCountCapacity(), frequency.getTotalTypeCapacity());
        }
    }

    /** Inserts into the player's visible inventory slots without dropping data. */
    default ItemStack insertIntoPlayerInventory(EntityPlayer player, ItemStack stack, boolean simulate) {
        if (!(this instanceof Container) || stack.isEmpty()) {
            return stack;
        }
        Container container = (Container) this;
        ItemStack remaining = stack.copy();
        for (Slot slot : container.inventorySlots) {
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (slot.inventory != player.inventory || !slot.canTakeStack(player)) {
                continue;
            }
            ItemStack inSlot = slot.getStack();
            if (!inSlot.isEmpty() && InventoryUtils.areItemsStackable(remaining, inSlot)) {
                int toAdd = Math.min(remaining.getCount(), Math.min(slot.getSlotStackLimit(), inSlot.getMaxStackSize()) - inSlot.getCount());
                if (toAdd > 0) {
                    if (!simulate) {
                        inSlot.grow(toAdd);
                        slot.onSlotChanged();
                    }
                    remaining.shrink(toAdd);
                }
            }
        }
        for (Slot slot : container.inventorySlots) {
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (slot.inventory != player.inventory || !slot.canTakeStack(player) || !slot.isItemValid(remaining) || slot.getHasStack()) {
                continue;
            }
            int toAdd = Math.min(remaining.getCount(), Math.min(slot.getSlotStackLimit(), remaining.getMaxStackSize()));
            if (toAdd > 0) {
                if (!simulate) {
                    ItemStack inserted = remaining.copy();
                    inserted.setCount(toAdd);
                    slot.putStack(inserted);
                    slot.onSlotChanged();
                }
                remaining.shrink(toAdd);
            }
        }
        return remaining;
    }

    default boolean isQIOCraftingHolder() {
        return this instanceof IQIOCraftingWindowHolder;
    }
}
