package mekanism.common.util;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.EnumColor;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.Mekanism;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.content.transporter.TransporterManager;
import mekanism.common.lib.inventory.HandlerTransitRequest;
import mekanism.common.lib.inventory.IAdvancedTransportEjector;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class InventoryUtils {

    public static final int[] EMPTY = new int[]{};

    public static int[] getIntRange(int start, int end) {
        int[] ret = new int[1 + end - start];
        for (int i = start; i <= end; i++) {
            ret[i - start] = i;
        }
        return ret;
    }

    public static TransitResponse putStackInInventory(TileEntity tile, TransitRequest request, EnumFacing side, boolean force) {
        return request.addToInventory(tile, side, 0, force);
    }

    /**
     * Like {@link ItemHandlerHelper#canItemStacksStack(ItemStack, ItemStack)} but empty stacks mean equal (either param). Thiakil: not sure why.
     *
     * @param toInsert stack a
     * @param inSlot   stack b
     * @return true if they are compatible
     */
    public static boolean areItemsStackable(ItemStack toInsert, ItemStack inSlot) {
        if (toInsert.isEmpty() || inSlot.isEmpty()) {
            return true;
        }
        return ItemHandlerHelper.canItemStacksStack(inSlot, toInsert);
    }

    public static boolean canInsert(TileEntity tileEntity, EnumColor color, ItemStack itemStack, EnumFacing side, boolean force) {
        if (force && tileEntity instanceof IAdvancedTransportEjector ejector) {
            return ejector.canSendHome(itemStack);
        }
        if (!force && tileEntity instanceof ISideConfiguration config) {
            if (config.getEjector().hasStrictInput()) {
                EnumFacing tileSide = config.getOrientation();
                EnumColor configColor = config.getEjector().getInputColor(MekanismUtils.getBaseOrientation(side, tileSide).getOpposite());
                if (configColor != null && configColor != color) {
                    return false;
                }
            }
        }
        if (!isItemHandler(tileEntity, side.getOpposite())) {
            return false;
        }

        IItemHandler inventory = getItemHandler(tileEntity, side.getOpposite());
        for (int i = 0; i < inventory.getSlots(); i++) {
            // Check validation
            if (inventory.isItemValid(i, itemStack)) {
                // Simulate insert
                ItemStack rejects = inventory.insertItem(i, itemStack, true);
                if (TransporterManager.didEmit(itemStack, rejects)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean assertItemHandler(String desc, TileEntity tileEntity, EnumFacing side) {
        if (!isItemHandler(tileEntity, side)) {
            Mekanism.logger.warn("'" + desc + "' was wrapped around a non-IItemHandler inventory. This should not happen!", new Exception());
            if (tileEntity == null) {
                Mekanism.logger.warn(" - null tile");
            } else {
                Mekanism.logger.warn(" - details: " + tileEntity + " " + tileEntity.getPos());
            }
            return false;
        }
        return true;
    }

    public static boolean isItemHandler(TileEntity tile, EnumFacing side) {
        return CapabilityUtils.hasCapability(tile, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
    }

    public static IItemHandler getItemHandler(TileEntity tile, EnumFacing side) {
        return CapabilityUtils.getCapability(tile, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
    }

    public static HandlerTransitRequest getEjectItemMap(IItemHandler handler, List<IInventorySlot> slots) {
        return getEjectItemMap(new HandlerTransitRequest(handler), slots);
    }

    public static <REQUEST extends HandlerTransitRequest> REQUEST getEjectItemMap(REQUEST request, List<IInventorySlot> slots) {
        List<IInventorySlot> shuffled = new ArrayList<>(slots);
        Collections.shuffle(shuffled);
        for (IInventorySlot slot : shuffled) {
            ItemStack simulatedExtraction = slot.extractItem(slot.getCount(), Action.SIMULATE, AutomationType.EXTERNAL);
            if (!simulatedExtraction.isEmpty()) {
                request.addItem(simulatedExtraction, slots.indexOf(slot));
            }
        }
        return request;
    }

    //TODO: Check what the difference between this method and areItemsStackable is
    public static boolean canStack(ItemStack stack1, ItemStack stack2) {
        return stack1.isEmpty() || stack2.isEmpty() ||
                stack1.getItem() == stack2.getItem() && (!stack2.getHasSubtypes() || stack2.getItemDamage() == stack1.getItemDamage())
                        && ItemStack.areItemStackTagsEqual(stack2, stack1) && stack1.isStackable();
    }

    /**
     * First inserts into matching non-empty slots, then into empty slots, matching high-version inventory helper behavior.
     */
    public static ItemStack insertItem(List<? extends IInventorySlot> slots, ItemStack stack, Action action, AutomationType automationType) {
        stack = insertItem(slots, stack, true, false, action, automationType);
        return insertItem(slots, stack, false, false, action, automationType);
    }

    public static ItemStack insertItem(List<? extends IInventorySlot> slots, ItemStack stack, boolean ignoreEmpty, boolean checkAll, Action action,
          AutomationType automationType) {
        if (stack.isEmpty()) {
            return stack;
        }
        for (IInventorySlot slot : slots) {
            if (!checkAll && ignoreEmpty == slot.isEmpty()) {
                continue;
            }
            stack = slot.insertItem(stack, action, automationType);
            if (stack.isEmpty()) {
                break;
            }
        }
        return stack;
    }

    public static void dropStack(ItemStack stack, Consumer<ItemStack> dropper) {
        int count = stack.getCount();
        int max = stack.getMaxStackSize();
        if (count > max) {
            //If we have more than a stack of the item (such as we are a bin) or some other thing that allows for compressing
            // stack counts, drop as many stacks as we need at their max size
            while (count > max) {
                dropper.accept(copyWithCount(stack,max));
                count -= max;
            }
            if (count > 0) {
                //If we have anything left to drop afterward, do so
                dropper.accept(copyWithCount(stack,count));
            }
        } else {
            //If we have a valid stack, we can just directly drop that instead without requiring any copies
            dropper.accept(stack);
        }
    }

    public static ItemStack copyWithCount(ItemStack stack, int pCount) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }else {
            ItemStack itemstack = stack.copy();
            itemstack.setCount(pCount);
            return itemstack;
        }
    }

}
