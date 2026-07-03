package mekanism.common.capabilities.resolver.manager;

import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.ISidedItemHandler;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.proxy.ProxyItemHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ItemHandlerManager extends CapabilityHandlerManager<IInventorySlotHolder, IInventorySlot, IItemHandler, ISidedItemHandler> {

    public ItemHandlerManager(@Nullable IInventorySlotHolder holder, @Nonnull ISidedItemHandler baseHandler) {
        super(holder, baseHandler, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, ProxyItemHandler::new, IInventorySlotHolder::getInventorySlots);
    }
}
