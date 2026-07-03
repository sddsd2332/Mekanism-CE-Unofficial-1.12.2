package mekanism.common.content.transporter;

import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.common.Mekanism;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.content.transporter.TransporterStack.Path;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.ItemData;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.util.InventoryUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TransporterManager {

    private static Map<Coord4D, Set<TransporterStack>> flowingStacks = new ConcurrentHashMap<>();

    public static void reset() {
        flowingStacks.clear();
    }

    public static void add(TransporterStack stack) {
        flowingStacks.computeIfAbsent(stack.getDest(), k -> ConcurrentHashMap.newKeySet()).add(stack);
    }

    public static void remove(TransporterStack stack) {
        if (stack.hasPath() && stack.getPathType() != Path.NONE) {
            Coord4D dest = stack.getDest();
            Set<TransporterStack> stacks = flowingStacks.get(dest);
            if (stacks != null) {
                stacks.remove(stack);
                if (stacks.isEmpty()) {
                    flowingStacks.remove(dest, stacks);
                }
            }
        }
    }

    private static int simulateInsert(IItemHandler handler, InventoryInfo inventoryInfo, ItemStack stack, int count, boolean inFlight) {
        int maxStackSize = stack.getMaxStackSize();
        for (int i = 0; i < inventoryInfo.slots; i++) {
            if (count == 0) {
                // Nothing more to insert
                break;
            }

            int max = inventoryInfo.getSlotLimit(handler, i);
            //If no items are allowed in the slot, pass it up before checking anything about the items
            if (max == 0) {
                continue;
            }

            // Make sure that the item is valid for the handler
            if (!handler.isItemValid(i, stack)) {
                continue;
            }

            // Simulate the insert; note that we can't depend solely on the "normal" simulate, since it would only tell us about
            // _this_ stack, not the cumulative set of stacks. Use our best guess about stacking/maxes to figure out
            // how the inventory would look after the insertion

            // Number of items in the destination
            int destCount = inventoryInfo.stackSizes[i];

            int mergedCount = count + destCount;
            int toAccept = count;
            boolean needsSimulation = false;
            if (destCount > 0) {
                if (destCount >= max || !InventoryUtils.areItemsStackable(inventoryInfo.inventory[i], stack)) {
                    continue;
                } else if (max > maxStackSize && mergedCount > maxStackSize) {
                    needsSimulation = true;
                    if (count <= maxStackSize) {
                        if (stack.getCount() <= maxStackSize) {
                            stack = StackUtils.size(stack, maxStackSize + 1);
                        }
                        toAccept = stack.getCount();
                    } else if (stack.getCount() <= maxStackSize) {
                        stack = StackUtils.size(stack, count);
                    }
                } else if (!inFlight) {
                    needsSimulation = true;
                }
            } else {
                needsSimulation = true;
            }
            if (needsSimulation) {
                ItemStack simulatedRemainder = handler.insertItem(i, stack, true);
                int accepted = stack.getCount() - simulatedRemainder.getCount();
                if (accepted == 0) {
                    continue;
                } else if (accepted < toAccept) {
                    max = inventoryInfo.actualStackSizes[i] + accepted;
                }
                if (destCount == 0) {
                    inventoryInfo.inventory[i] = stack;
                }
            }

            if (mergedCount > max) {
                // Not all the items will fit; put max in and save leftovers
                inventoryInfo.stackSizes[i] = max;
                count = mergedCount - max;
            } else {
                // All items will fit; set the destination count as the new combined amount
                inventoryInfo.stackSizes[i] = mergedCount;
                return 0;
            }
        }
        return count;
    }

    public static boolean didEmit(ItemStack stack, ItemStack returned) {
        return returned.isEmpty() || returned.getCount() < stack.getCount();
    }

    public static ItemStack getToUse(ItemStack stack, ItemStack returned) {
        return returned.isEmpty() ? stack : StackUtils.size(stack, stack.getCount() - returned.getCount());
    }

    /**
     * @return TransitResponse of expected items to use
     */
    public static TransitResponse getPredictedInsert(TileEntity tileEntity, EnumColor color, TransitRequest request, EnumFacing side) {
        return getPredictedInsert(tileEntity, color, request, side, Collections.emptyMap());
    }

    public static TransitResponse getPredictedInsert(TileEntity tileEntity, EnumColor color, TransitRequest request, EnumFacing side,
          Map<Coord4D, Set<TransporterStack>> additionalFlowingStacks) {
        // If the TE in question implements the mekanism interface, check that the color matches and bail
        // fast if it doesn't
        if (tileEntity instanceof ISideConfiguration config) {
            if (config.getEjector().hasStrictInput()) {
                EnumFacing tileSide = config.getOrientation();
                EnumColor configColor = config.getEjector().getInputColor(MekanismUtils.getBaseOrientation(side, tileSide).getOpposite());
                if (configColor != null && configColor != color) {
                    return request.getEmptyResponse();
                }
            }
        }

        // Get the item handler for the TE; fail if it's not an item handler (and log for good measure --
        // there shouldn't be anything that's not an IItemHandler anymore)
        IItemHandler handler = InventoryUtils.getItemHandler(tileEntity, side.getOpposite());
        if (handler == null) {
            Mekanism.logger.error("Failed to predict insert; not an IItemHandler: {}", tileEntity);
            return request.getEmptyResponse();
        }

        // Before we see if this item can fit in the destination, we must first check the stacks that are
        // en-route. Note that we also have to simulate the current inventory after each stack; we'll keep
        // track of the initial size of the inventory and then simulate each in-flight addition. If any
        // in-flight stack can't be inserted, that we can fail fast.

        //Information about the inventory, keeps track of the size of a stack a slot will have, and
        // a cache of what getStackInSlot returns (as it has to call it anyways to get the stack size).
        // This cache allows potentially expensive getStackInSlot implementations to only have to be called
        // once instead of potentially many times.
        InventoryInfo inventoryInfo = new InventoryInfo(handler);

        //For each of the in-flight stacks, simulate their insert into the tile entity. Note that stackSizes
        // for inventoryInfo is updated each time
        Coord4D position = Coord4D.get(tileEntity);
        if (!predictFlowing(position, side, handler, inventoryInfo, flowingStacks) ||
              !predictFlowing(position, side, handler, inventoryInfo, additionalFlowingStacks)) {
            return request.getEmptyResponse();
        }

        // Now for each of the items in the request, simulate the insert, using the state from all the in-flight
        // items to ensure we have an accurate model of what will happen in future. We try each stack in the
        // request; it might be possible to not send the first item, but the second could work, etc.
        for (ItemData data : request) {
            // Create a sending ItemStack with the hashed item type and total item count within the request
            ItemStack stack = data.getItemType().getInternalStack();
            int numToSend = data.getTotalCount();
            //Directly pass the stack AND the actual amount we want, so that it does not need to copy the stack if there is no room
            int numLeftOver = simulateInsert(handler, inventoryInfo, stack, numToSend, false);

            // If leftovers is unchanged from the simulation, there's no room at all; move on to the next stack
            if (numLeftOver == numToSend) {
                continue;
            }

            // Otherwise, construct the appropriately size stack to send and return that
            return request.createResponse(StackUtils.size(stack, numToSend - numLeftOver), data);
        }
        return request.getEmptyResponse();
    }

    private static boolean predictFlowing(Coord4D position, EnumFacing side, IItemHandler handler, InventoryInfo inventoryInfo,
          Map<Coord4D, Set<TransporterStack>> flowingStacks) {
        Set<TransporterStack> transporterStacks = flowingStacks.get(position);
        if (transporterStacks != null) {
            for (TransporterStack stack : transporterStacks) {
                if (stack != null && stack.getPathType().hasTarget()) {
                    int numLeftOver = simulateInsert(handler, inventoryInfo, stack.itemStack, stack.itemStack.getCount(), true);
                    if (numLeftOver > 0) {
                        if (numLeftOver == stack.itemStack.getCount() && side.getOpposite() != stack.getSideOfDest()) {
                            continue;
                        }
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static class InventoryInfo {

        public final ItemStack[] inventory;
        public final int[] stackSizes;
        public final int[] actualStackSizes;
        public final int[] slotLimits;
        public final int slots;

        public InventoryInfo(IItemHandler handler) {
            slots = handler.getSlots();
            inventory = new ItemStack[slots];
            stackSizes = new int[slots];
            actualStackSizes = new int[slots];
            slotLimits = new int[slots];
            Arrays.fill(slotLimits, -1);
            for (int i = 0; i < slots; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                inventory[i] = stack;
                actualStackSizes[i] = stackSizes[i] = stack.getCount();
            }
        }

        public int getSlotLimit(IItemHandler handler, int slot) {
            int limit = slotLimits[slot];
            if (limit == -1) {
                limit = handler.getSlotLimit(slot);
                slotLimits[slot] = limit;
            }
            return limit;
        }
    }
}
