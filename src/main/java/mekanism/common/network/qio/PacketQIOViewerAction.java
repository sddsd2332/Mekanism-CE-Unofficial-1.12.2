package mekanism.common.network.qio;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.common.content.qio.QIOResourceType;
import mekanism.common.content.qio.QIOResourceTypeRegistry;
import mekanism.common.content.qio.QIORollback;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.inventory.container.PortableQIODashboardContainer;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.GasUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.ItemHandlerHelper;
import mekanism.api.gas.GasStack;

import javax.annotation.Nullable;
import java.util.UUID;

/** Server-authoritative resource viewer action. */
public class PacketQIOViewerAction implements IMessageHandler<PacketQIOViewerAction.Message, IMessage> {

    @Override
    @Nullable
    public IMessage onMessage(Message message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || player.world.isRemote) {
            return null;
        }
        PacketHandler.handlePacket(() -> handleMessage(message, player), player);
        return null;
    }

    /** Shared server entry point for legacy slot-interaction packets. */
    public static void handleMessage(Message message, EntityPlayer player) {
        if (!message.valid || !(player.openContainer instanceof QIOItemViewerContainer) || message.amount <= 0) {
            return;
        }
        QIOItemViewerContainer container = (QIOItemViewerContainer) player.openContainer;
        if (message.windowId != container.windowId || !container.canInteractWith(player)) {
            return;
        }
        QIOFrequency frequency = container.getFrequency();
        if (frequency == null || frequency.isRemoved() || !frequency.isValid()) {
            return;
        }
        boolean changed;
        switch (message.action) {
            case PUT:
                changed = insertHeld(player, frequency, message.amount);
                break;
            case TAKE:
                changed = takeToCursor(player, frequency, message.resource, message.amount);
                break;
            case SHIFT_TAKE:
                changed = takeToInventory(player, frequency, message.resource, message.amount);
                break;
            default:
                changed = false;
        }
        if (changed) {
            syncAfterTransfer(player, container);
        }
    }

    private static boolean insertHeld(EntityPlayer player, QIOFrequency frequency, long requested) {
        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) {
            return false;
        }
        if (player.openContainer instanceof PortableQIODashboardContainer) {
            PortableQIODashboardContainer portable = (PortableQIODashboardContainer) player.openContainer;
            if (player.getHeldItem(portable.getHand()) == held) {
                return false;
            }
        }
        boolean hasFluid = hasFluidToInsert(held);
        boolean hasGas = !hasFluid && hasGasToInsert(held);
        if (hasFluid || hasGas) {
            return hasFluid ? insertHeldFluid(player, frequency, held, requested)
                  : insertHeldGas(player, frequency, held, requested);
        }
        boolean inserted = insertHeldItem(held, frequency, requested);
        if (inserted && held.isEmpty()) {
            player.inventory.setItemStack(ItemStack.EMPTY);
        }
        return inserted;
    }

    static boolean insertHeldItem(ItemStack held, QIOFrequency frequency, long requested) {
        if (held.isEmpty() || requested <= 0) {
            return false;
        }
        long amount = Math.min(requested, held.getCount());
        long inserted = frequency.massInsert(held, amount, Action.EXECUTE);
        if (inserted > 0) {
            held.shrink((int) Math.min(Integer.MAX_VALUE, inserted));
            return true;
        }
        return false;
    }

    private static boolean takeToCursor(EntityPlayer player, QIOFrequency frequency, @Nullable UUID resource, long requested) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        if (type == null) {
            return false;
        }
        if (type.getKind() != QIOResourceKind.ITEM) {
            if (type.getKind() == QIOResourceKind.FLUID) {
                return takeFluidToCursor(player, frequency, resource, type, requested);
            } else if (type.getKind() == QIOResourceKind.GAS) {
                return takeGasToCursor(player, frequency, resource, type, requested);
            }
            return false;
        }
        ItemStack held = player.inventory.getItemStack();
        ItemStack template = type.createItemStack(1);
        if (!held.isEmpty() && !ItemHandlerHelper.canItemStacksStack(template, held)) {
            return false;
        }
        int free = held.isEmpty() ? template.getMaxStackSize() : held.getMaxStackSize() - held.getCount();
        if (free <= 0) {
            return false;
        }
        long extracted = frequency.massExtract(resource, Math.min(requested, free), Action.EXECUTE);
        if (extracted <= 0) {
            return false;
        }
        ItemStack result = type.createItemStack((int) Math.min(Integer.MAX_VALUE, extracted));
        if (held.isEmpty()) {
            player.inventory.setItemStack(result);
        } else {
            held.grow(result.getCount());
        }
        return true;
    }

    private static boolean takeToInventory(EntityPlayer player, QIOFrequency frequency, @Nullable UUID resource, long requested) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(resource);
        if (type == null) {
            return false;
        }
        if (type.getKind() != QIOResourceKind.ITEM) {
            // Shift-clicking a non-item resource uses a compatible container
            // already present in the player's inventory.
            if (type.getKind() == QIOResourceKind.FLUID) {
                return takeFluidToInventory(player, frequency, resource, type, requested);
            } else if (type.getKind() == QIOResourceKind.GAS) {
                return takeGasToInventory(player, frequency, resource, type, requested);
            }
            return false;
        }
        long remaining = Math.min(requested, frequency.getStored(resource));
        boolean changed = false;
        while (remaining > 0) {
            int batch = (int) Math.min(64, remaining);
            ItemStack result = type.createItemStack(batch);
            if (!canAddToInventory(player, result)) {
                break;
            }
            long extracted = frequency.massExtract(resource, batch, Action.EXECUTE);
            if (extracted <= 0) {
                break;
            }
            ItemStack extractedStack = type.createItemStack((int) extracted);
            if (!addFullyToInventory(player.inventory, extractedStack)) {
                QIORollback.restore(frequency, resource, extracted, "item inventory transfer");
                break;
            }
            changed = true;
            remaining -= extracted;
            if (extracted < batch) {
                break;
            }
        }
        return changed;
    }

    private static boolean insertHeldFluid(EntityPlayer player, QIOFrequency frequency, ItemStack held, long requested) {
        IFluidHandlerItem simulatedHandler = FluidContainerUtils.getUnstackedFluidHandlerCapability(held);
        if (simulatedHandler == null) {
            return false;
        }
        FluidStack available = simulatedHandler.drain(Integer.MAX_VALUE, false);
        if (available == null || available.amount <= 0 || available.getFluid() == null) {
            return false;
        }
        int amount = (int) Math.min(Math.min((long) available.amount, requested), Integer.MAX_VALUE);
        FluidStack toInsert = new FluidStack(available, amount);
        long accepted = frequency.massInsert(toInsert, amount, Action.SIMULATE);
        if (accepted <= 0) {
            return false;
        }
        int transfer = (int) Math.min(amount, accepted);
        ItemStack previewStack = held.copy();
        previewStack.setCount(1);
        IFluidHandlerItem previewHandler = FluidContainerUtils.getFluidHandlerCapability(previewStack);
        if (previewHandler == null) {
            return false;
        }
        FluidStack previewDrained = previewHandler.drain(new FluidStack(available, transfer), true);
        if (previewDrained == null || previewDrained.amount <= 0 || !previewDrained.isFluidEqual(available)) {
            return false;
        }
        ItemStack previewResult = previewHandler.getContainer();
        if (!canReturnContainer(player, held, previewResult)) {
            return false;
        }
        long inserted = frequency.massInsert(previewDrained, previewDrained.amount, Action.EXECUTE);
        if (inserted <= 0 || inserted > previewDrained.amount) {
            rollbackFluidInsertion(frequency, previewDrained, inserted);
            return false;
        }
        int committed = (int) inserted;
        ItemStack working = held.copy();
        working.setCount(1);
        IFluidHandlerItem handler = FluidContainerUtils.getFluidHandlerCapability(working);
        FluidStack drained = handler == null ? null : handler.drain(new FluidStack(previewDrained, committed), true);
        if (drained == null || drained.amount != committed || !drained.isFluidEqual(previewDrained)) {
            rollbackFluidInsertion(frequency, previewDrained, inserted);
            return false;
        }
        ItemStack result = handler.getContainer();
        if (!canReturnContainer(player, held, result) || !commitContainerResult(player, held, result)) {
            rollbackFluidInsertion(frequency, previewDrained, inserted);
            return false;
        }
        return true;
    }

    private static boolean hasFluidToInsert(ItemStack stack) {
        IFluidHandlerItem handler = FluidContainerUtils.getUnstackedFluidHandlerCapability(stack);
        FluidStack fluid = handler == null ? null : handler.drain(Integer.MAX_VALUE, false);
        return fluid != null && fluid.amount > 0 && fluid.getFluid() != null;
    }

    private static boolean hasGasToInsert(ItemStack stack) {
        GasStack gas = GasUtils.getGasContained(stack);
        return gas != null && gas.amount > 0 && gas.getGas() != null;
    }

    private static boolean insertHeldGas(EntityPlayer player, QIOFrequency frequency, ItemStack held, long requested) {
        GasStack available = GasUtils.getGasContained(held);
        if (available == null || available.amount <= 0 || available.getGas() == null) {
            return false;
        }
        int amount = (int) Math.min(Math.min((long) available.amount, requested), Integer.MAX_VALUE);
        ItemStack simulatedStack = held.copy();
        simulatedStack.setCount(1);
        GasStack simulated = GasInventorySlot.useGas(simulatedStack, available.getGas(), amount);
        if (simulated == null || simulated.amount <= 0) {
            return false;
        }
        long accepted = frequency.massInsert(simulated, simulated.amount, Action.SIMULATE);
        if (accepted <= 0) {
            return false;
        }
        int transfer = (int) Math.min(simulated.amount, accepted);
        ItemStack previewStack = held.copy();
        previewStack.setCount(1);
        GasStack previewExtracted = GasInventorySlot.useGas(previewStack, available.getGas(), transfer);
        if (previewExtracted == null || previewExtracted.amount <= 0 || !previewExtracted.isGasEqual(available)) {
            return false;
        }
        ItemStack previewResult = previewStack.copy();
        if (!canReturnContainer(player, held, previewResult)) {
            return false;
        }
        long inserted = frequency.massInsert(previewExtracted, previewExtracted.amount, Action.EXECUTE);
        if (inserted <= 0 || inserted > previewExtracted.amount) {
            rollbackGasInsertion(frequency, previewExtracted, inserted);
            return false;
        }
        int committed = (int) inserted;
        ItemStack working = held.copy();
        working.setCount(1);
        GasStack extracted = GasInventorySlot.useGas(working, previewExtracted.getGas(), committed);
        if (extracted == null || extracted.amount != committed || !extracted.isGasEqual(previewExtracted)) {
            rollbackGasInsertion(frequency, previewExtracted, inserted);
            return false;
        }
        if (!canReturnContainer(player, held, working) || !commitContainerResult(player, held, working)) {
            rollbackGasInsertion(frequency, previewExtracted, inserted);
            return false;
        }
        return true;
    }

    private static void rollbackFluidInsertion(QIOFrequency frequency, FluidStack type, long amount) {
        if (amount <= 0) {
            return;
        }
        long rolledBack = frequency.massExtract(type, amount, Action.EXECUTE);
        if (rolledBack != amount) {
            Mekanism.logger.warn("Unable to fully roll back QIO fluid insertion: expected {}, extracted {}", amount, rolledBack);
        }
    }

    private static void rollbackGasInsertion(QIOFrequency frequency, GasStack type, long amount) {
        if (amount <= 0) {
            return;
        }
        long rolledBack = frequency.massExtract(type, amount, Action.EXECUTE);
        if (rolledBack != amount) {
            Mekanism.logger.warn("Unable to fully roll back QIO gas insertion: expected {}, extracted {}", amount, rolledBack);
        }
    }

    private static boolean takeFluidToCursor(EntityPlayer player, QIOFrequency frequency, UUID resource, QIOResourceType type,
          long requested) {
        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) {
            return false;
        }
        IFluidHandlerItem simulatedHandler = FluidContainerUtils.getUnstackedFluidHandlerCapability(held);
        FluidStack template = type.createFluidStack(1);
        if (simulatedHandler == null || template == null) {
            return false;
        }
        int amount = (int) Math.min(Math.min(requested, Integer.MAX_VALUE), frequency.getStored(resource));
        if (amount <= 0) {
            return false;
        }
        int accepted = simulatedHandler.fill(new FluidStack(template, amount), false);
        if (accepted <= 0) {
            return false;
        }
        ItemStack preview = held.copy();
        preview.setCount(1);
        IFluidHandlerItem previewHandler = FluidContainerUtils.getFluidHandlerCapability(preview);
        int previewFilled = previewHandler == null ? 0 : Math.max(0, Math.min(accepted,
              previewHandler.fill(new FluidStack(template, accepted), true)));
        if (previewFilled <= 0 || !canReturnContainer(player, held, previewHandler.getContainer())) {
            return false;
        }
        long extracted = frequency.massExtract(resource, previewFilled, Action.EXECUTE);
        if (extracted <= 0) {
            return false;
        }
        ItemStack working = held.copy();
        working.setCount(1);
        IFluidHandlerItem handler = FluidContainerUtils.getFluidHandlerCapability(working);
        if (handler == null) {
            rollbackExtraction(frequency, resource, extracted, "fluid cursor transfer");
            return false;
        }
        int filled = Math.max(0, Math.min((int) extracted, handler.fill(new FluidStack(template, (int) extracted), true)));
        if (filled < extracted) {
            rollbackExtraction(frequency, resource, extracted - filled, "fluid cursor transfer");
        }
        if (filled <= 0) {
            return false;
        }
        if (!commitContainerResult(player, held, handler.getContainer())) {
            rollbackExtraction(frequency, resource, filled, "fluid cursor container commit");
            return false;
        }
        return true;
    }

    private static boolean takeGasToCursor(EntityPlayer player, QIOFrequency frequency, UUID resource, QIOResourceType type,
          long requested) {
        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) {
            return false;
        }
        GasStack template = type.createGasStack(1);
        if (template == null) {
            return false;
        }
        ItemStack simulatedStack = held.copy();
        int amount = (int) Math.min(Math.min(requested, Integer.MAX_VALUE), frequency.getStored(resource));
        if (amount <= 0) {
            return false;
        }
        int accepted = GasInventorySlot.insertGas(simulatedStack, new GasStack(template.getGas(), amount), false);
        if (accepted <= 0) {
            return false;
        }
        ItemStack preview = held.copy();
        preview.setCount(1);
        int previewFilled = Math.max(0, Math.min(accepted,
              GasInventorySlot.insertGas(preview, new GasStack(template.getGas(), accepted), true)));
        if (previewFilled <= 0 || !canReturnContainer(player, held, preview)) {
            return false;
        }
        long extracted = frequency.massExtract(resource, previewFilled, Action.EXECUTE);
        if (extracted <= 0) {
            return false;
        }
        ItemStack working = held.copy();
        working.setCount(1);
        int filled = Math.max(0, Math.min((int) extracted,
              GasInventorySlot.insertGas(working, new GasStack(template.getGas(), (int) extracted), true)));
        if (filled < extracted) {
            rollbackExtraction(frequency, resource, extracted - filled, "gas cursor transfer");
        }
        if (filled <= 0) {
            return false;
        }
        if (!commitContainerResult(player, held, working)) {
            rollbackExtraction(frequency, resource, filled, "gas cursor container commit");
            return false;
        }
        return true;
    }

    private static boolean takeFluidToInventory(EntityPlayer player, QIOFrequency frequency, UUID resource, QIOResourceType type,
          long requested) {
        return takeFluidToInventory(player.inventory, frequency, resource, type, requested);
    }

    static boolean takeFluidToInventory(InventoryPlayer inventory, QIOFrequency frequency, UUID resource, QIOResourceType type,
          long requested) {
        FluidStack template = type.createFluidStack(1);
        if (template == null) {
            return false;
        }
        long remaining = Math.min(requested, frequency.getStored(resource));
        boolean changed = false;
        for (int slot = 0; slot < getContainerSlotCount(inventory); slot++) {
            while (remaining > 0) {
                ItemStack container = getContainerSlot(inventory, slot);
                if (container.isEmpty()) {
                    break;
                }
                IFluidHandlerItem handler = FluidContainerUtils.getUnstackedFluidHandlerCapability(container);
                if (handler == null) {
                    break;
                }
                int amount = (int) Math.min(remaining, Integer.MAX_VALUE);
                int accepted = Math.max(0, Math.min(amount, handler.fill(new FluidStack(template, amount), false)));
                if (accepted <= 0) {
                    break;
                }
                long extracted = frequency.massExtract(resource, accepted, Action.EXECUTE);
                if (extracted <= 0) {
                    return changed;
                }
                ItemStack working = container.copy();
                working.setCount(1);
                IFluidHandlerItem actual = FluidContainerUtils.getFluidHandlerCapability(working);
                int filled = actual == null ? 0 : Math.max(0, Math.min((int) extracted,
                      actual.fill(new FluidStack(template, (int) extracted), true)));
                if (filled < extracted) {
                    rollbackExtraction(frequency, resource, extracted - filled, "fluid inventory transfer");
                }
                if (filled <= 0) {
                    break;
                }
                if (!commitInventoryContainerResult(inventory, slot, container, actual.getContainer())) {
                    rollbackExtraction(frequency, resource, filled, "fluid inventory container commit");
                    break;
                }
                remaining -= filled;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean takeGasToInventory(EntityPlayer player, QIOFrequency frequency, UUID resource, QIOResourceType type,
          long requested) {
        return takeGasToInventory(player.inventory, frequency, resource, type, requested);
    }

    static boolean takeGasToInventory(InventoryPlayer inventory, QIOFrequency frequency, UUID resource, QIOResourceType type,
          long requested) {
        GasStack template = type.createGasStack(1);
        if (template == null) {
            return false;
        }
        long remaining = Math.min(requested, frequency.getStored(resource));
        boolean changed = false;
        for (int slot = 0; slot < getContainerSlotCount(inventory); slot++) {
            while (remaining > 0) {
                ItemStack container = getContainerSlot(inventory, slot);
                if (container.isEmpty()) {
                    break;
                }
                ItemStack simulation = container.copy();
                simulation.setCount(1);
                int amount = (int) Math.min(remaining, Integer.MAX_VALUE);
                int accepted = Math.max(0, Math.min(amount,
                      GasInventorySlot.insertGas(simulation, new GasStack(template.getGas(), amount), false)));
                if (accepted <= 0) {
                    break;
                }
                long extracted = frequency.massExtract(resource, accepted, Action.EXECUTE);
                if (extracted <= 0) {
                    return changed;
                }
                ItemStack working = container.copy();
                working.setCount(1);
                int filled = Math.max(0, Math.min((int) extracted,
                      GasInventorySlot.insertGas(working, new GasStack(template.getGas(), (int) extracted), true)));
                if (filled < extracted) {
                    rollbackExtraction(frequency, resource, extracted - filled, "gas inventory transfer");
                }
                if (filled <= 0) {
                    break;
                }
                if (!commitInventoryContainerResult(inventory, slot, container, working)) {
                    rollbackExtraction(frequency, resource, filled, "gas inventory container commit");
                    break;
                }
                remaining -= filled;
                changed = true;
            }
        }
        return changed;
    }

    private static void rollbackExtraction(QIOFrequency frequency, UUID resource, long amount, String operation) {
        QIORollback.restore(frequency, resource, amount, operation);
    }

    private static int getContainerSlotCount(InventoryPlayer inventory) {
        return inventory.mainInventory.size() + inventory.offHandInventory.size();
    }

    private static ItemStack getContainerSlot(InventoryPlayer inventory, int index) {
        if (index < inventory.mainInventory.size()) {
            return inventory.mainInventory.get(index);
        }
        int offhand = index - inventory.mainInventory.size();
        return offhand < inventory.offHandInventory.size() ? inventory.offHandInventory.get(offhand) : ItemStack.EMPTY;
    }

    private static void setContainerSlot(InventoryPlayer inventory, int index, ItemStack stack) {
        if (index < inventory.mainInventory.size()) {
            inventory.mainInventory.set(index, stack);
        } else {
            int offhand = index - inventory.mainInventory.size();
            if (offhand >= 0 && offhand < inventory.offHandInventory.size()) {
                inventory.offHandInventory.set(offhand, stack);
            }
        }
    }

    /** Commits one transformed container from a player inventory slot without allowing partial inventory mutations. */
    private static boolean commitInventoryContainerResult(InventoryPlayer inventory, int index, ItemStack expected,
          @Nullable ItemStack result) {
        if (index < 0 || index >= getContainerSlotCount(inventory) || expected == null || expected.isEmpty()) {
            return false;
        }
        ItemStack current = getContainerSlot(inventory, index);
        if (current.isEmpty() || current.getCount() != expected.getCount() || !ItemStack.areItemStacksEqual(current, expected)) {
            return false;
        }

        java.util.List<ItemStack> mainSnapshot = copyInventory(inventory.mainInventory);
        java.util.List<ItemStack> offhandSnapshot = copyInventory(inventory.offHandInventory);
        ItemStack remainingContainers = current.copy();
        remainingContainers.shrink(1);
        setContainerSlot(inventory, index, remainingContainers.isEmpty() ? ItemStack.EMPTY : remainingContainers);

        ItemStack returned = result == null ? ItemStack.EMPTY : result.copy();
        if (!returned.isEmpty()) {
            ItemStack slotStack = getContainerSlot(inventory, index);
            int inventoryLimit = inventory.getInventoryStackLimit();
            int slotLimit = Math.min(inventoryLimit, returned.getMaxStackSize());
            if (slotStack.isEmpty()) {
                int toSlot = Math.min(returned.getCount(), slotLimit);
                if (toSlot > 0) {
                    ItemStack slotResult = returned.copy();
                    slotResult.setCount(toSlot);
                    setContainerSlot(inventory, index, slotResult);
                    returned.shrink(toSlot);
                }
            } else if (canMergeInto(slotStack, returned, Math.min(inventoryLimit, slotStack.getMaxStackSize()))) {
                int max = Math.min(inventoryLimit, slotStack.getMaxStackSize());
                int toSlot = Math.min(returned.getCount(), max - slotStack.getCount());
                slotStack.grow(toSlot);
                returned.shrink(toSlot);
            }
            if (!returned.isEmpty() && !addFullyToInventory(inventory, returned)) {
                restoreInventory(inventory, mainSnapshot, offhandSnapshot);
                return false;
            }
        }
        inventory.markDirty();
        return true;
    }

    private static boolean canAddToInventory(EntityPlayer player, ItemStack stack) {
        return canAddToInventory(player.inventory, stack);
    }

    static boolean canMergeInto(ItemStack existing, ItemStack requested, int max) {
        return !existing.isEmpty() && existing.isStackable() && ItemHandlerHelper.canItemStacksStack(existing, requested)
              && existing.getCount() < Math.min(max, existing.getMaxStackSize());
    }

    public static boolean canTakeIntoHeldStack(@Nullable QIOResourceEntry entry, @Nullable ItemStack held) {
        return entry != null && entry.getKind() == QIOResourceKind.ITEM && held != null && !held.isEmpty() &&
              held.getCount() < held.getMaxStackSize() && ItemHandlerHelper.canItemStacksStack(entry.getItem(), held);
    }

    public static boolean canTakeFluidIntoHeldStack(@Nullable QIOResourceEntry entry, @Nullable ItemStack held) {
        if (entry == null || entry.getKind() != QIOResourceKind.FLUID || held == null || held.isEmpty()) {
            return false;
        }
        FluidStack fluid = entry.createFluidStack((int) Math.min(Integer.MAX_VALUE, entry.getAmount()));
        IFluidHandlerItem handler = FluidContainerUtils.getUnstackedFluidHandlerCapability(held);
        return fluid != null && handler != null && handler.fill(fluid, false) > 0;
    }

    public static boolean canTakeGasIntoHeldStack(@Nullable QIOResourceEntry entry, @Nullable ItemStack held) {
        if (entry == null || entry.getKind() != QIOResourceKind.GAS || held == null || held.isEmpty()) {
            return false;
        }
        GasStack gas = entry.createGasStack((int) Math.min(Integer.MAX_VALUE, entry.getAmount()));
        ItemStack simulation = held.copy();
        simulation.setCount(1);
        return gas != null && GasInventorySlot.insertGas(simulation, gas, false) > 0;
    }

    /** Returns the resource-unit amount represented by a Viewer PUT click. */
    public static long getHeldPutAmount(@Nullable ItemStack held, boolean singleItem) {
        if (held == null || held.isEmpty()) {
            return 0;
        }
        FluidStack fluid = FluidContainerUtils.getFluidContained(held);
        if (fluid != null && fluid.amount > 0) {
            return fluid.amount;
        }
        GasStack gas = GasUtils.getGasContained(held);
        if (gas != null && gas.amount > 0) {
            return gas.amount;
        }
        return singleItem ? 1 : held.getCount();
    }

    private static boolean canReturnContainer(EntityPlayer player, ItemStack held, @Nullable ItemStack result) {
        if (held == null || held.isEmpty()) {
            return false;
        }
        ItemStack returned = result == null ? ItemStack.EMPTY : result.copy();
        if (returned.isEmpty()) {
            return true;
        }
        ItemStack cursor = held.copy();
        cursor.shrink(1);
        int inventoryLimit = player.inventory.getInventoryStackLimit();
        int cursorLimit = Math.min(inventoryLimit, returned.getMaxStackSize());
        if (cursor.isEmpty()) {
            int toCursor = Math.min(returned.getCount(), cursorLimit);
            returned.shrink(toCursor);
        } else if (canMergeInto(cursor, returned, Math.min(inventoryLimit, cursor.getMaxStackSize()))) {
            int max = Math.min(inventoryLimit, cursor.getMaxStackSize());
            int toCursor = Math.min(returned.getCount(), max - cursor.getCount());
            returned.shrink(toCursor);
        }
        return returned.isEmpty() || canAddToInventory(player, returned);
    }

    /** Commits a previously validated cursor/container transition. */
    private static boolean commitContainerResult(EntityPlayer player, ItemStack held, @Nullable ItemStack result) {
        ItemStack current = player.inventory.getItemStack();
        if (current.isEmpty() || held == null || held.isEmpty() || current.getCount() != held.getCount()
              || !ItemStack.areItemStacksEqual(current, held) || !canReturnContainer(player, held, result)) {
            return false;
        }
        ItemStack cursor = current.copy();
        cursor.shrink(1);
        ItemStack returned = result == null ? ItemStack.EMPTY : result.copy();
        if (!returned.isEmpty()) {
            int inventoryLimit = player.inventory.getInventoryStackLimit();
            if (cursor.isEmpty()) {
                int toCursor = Math.min(returned.getCount(), Math.min(inventoryLimit, returned.getMaxStackSize()));
                ItemStack cursorResult = returned.copy();
                cursorResult.setCount(toCursor);
                cursor = cursorResult;
                returned.shrink(toCursor);
            } else if (canMergeInto(cursor, returned, Math.min(inventoryLimit, cursor.getMaxStackSize()))) {
                int max = Math.min(inventoryLimit, cursor.getMaxStackSize());
                int toCursor = Math.min(returned.getCount(), max - cursor.getCount());
                cursor.grow(toCursor);
                returned.shrink(toCursor);
            }
            if (!returned.isEmpty()) {
                if (!addFullyToInventory(player.inventory, returned)) {
                    return false;
                }
            }
        }
        player.inventory.setItemStack(cursor);
        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
        return true;
    }

    /** Adds a complete stack or restores the inventory snapshot on failure. */
    private static boolean addFullyToInventory(InventoryPlayer inventory, ItemStack stack) {
        if (!canAddToInventory(inventory, stack)) {
            return false;
        }
        java.util.List<ItemStack> mainSnapshot = copyInventory(inventory.mainInventory);
        java.util.List<ItemStack> offhandSnapshot = copyInventory(inventory.offHandInventory);
        ItemStack toInsert = stack.copy();
        inventory.addItemStackToInventory(toInsert);
        if (toInsert.isEmpty()) {
            return true;
        }
        restoreInventory(inventory, mainSnapshot, offhandSnapshot);
        return false;
    }

    private static boolean canAddToInventory(InventoryPlayer inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack remaining = stack.copy();
        if (remaining.isItemDamaged()) {
            return inventory.getFirstEmptyStack() != -1;
        }
        // InventoryPlayer searches the selected hotbar slot, offhand, then
        // the remaining main-inventory slots for mergeable stacks.
        int inventoryLimit = inventory.getInventoryStackLimit();
        int max = Math.min(remaining.getMaxStackSize(), inventoryLimit);
        int left = remaining.getCount();
        int current = inventory.currentItem;
        ItemStack selected = current >= 0 && current < inventory.mainInventory.size()
              ? inventory.mainInventory.get(current) : ItemStack.EMPTY;
        if (canMergeInto(selected, remaining, max)) {
            left -= Math.min(left, max - selected.getCount());
        }
        ItemStack offhand = inventory.offHandInventory.isEmpty() ? ItemStack.EMPTY : inventory.offHandInventory.get(0);
        if (left > 0 && canMergeInto(offhand, remaining, max)) {
            left -= Math.min(left, max - offhand.getCount());
        }
        for (int slot = 0; left > 0 && slot < inventory.mainInventory.size(); slot++) {
            if (slot == current) {
                continue;
            }
            ItemStack existing = inventory.mainInventory.get(slot);
            if (canMergeInto(existing, remaining, max)) {
                left -= Math.min(left, max - existing.getCount());
            }
        }
        for (ItemStack existing : inventory.mainInventory) {
            if (left <= 0) {
                break;
            }
            if (existing.isEmpty()) {
                left -= Math.min(left, max);
            }
        }
        return left <= 0;
    }

    private static java.util.List<ItemStack> copyInventory(java.util.List<ItemStack> inventory) {
        java.util.List<ItemStack> snapshot = new java.util.ArrayList<>(inventory.size());
        for (ItemStack stack : inventory) {
            snapshot.add(stack.copy());
        }
        return snapshot;
    }

    private static void restoreInventory(InventoryPlayer inventory, java.util.List<ItemStack> mainSnapshot,
          java.util.List<ItemStack> offhandSnapshot) {
        for (int slot = 0; slot < mainSnapshot.size(); slot++) {
            inventory.mainInventory.set(slot, mainSnapshot.get(slot));
        }
        for (int slot = 0; slot < offhandSnapshot.size(); slot++) {
            inventory.offHandInventory.set(slot, offhandSnapshot.get(slot));
        }
        inventory.markDirty();
    }

    private static void syncAfterTransfer(EntityPlayer player, QIOItemViewerContainer container) {
        player.inventory.markDirty();
        container.detectAndSendChanges();
        container.sendViewerSync(player);
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP playerMP = (EntityPlayerMP) player;
            playerMP.connection.sendPacket(new SPacketSetSlot(-1, -1, player.inventory.getItemStack()));
            playerMP.sendContainerToPlayer(container);
        }
    }

    public static void sendTake(int windowId, UUID resource, long amount) {
        Mekanism.packetHandler.sendToServer(Message.take(windowId, resource, amount));
    }

    public static void sendShiftTake(int windowId, UUID resource, long amount) {
        Mekanism.packetHandler.sendToServer(Message.shiftTake(windowId, resource, amount));
    }

    public static void sendPut(int windowId, long amount) {
        Mekanism.packetHandler.sendToServer(Message.put(windowId, amount));
    }

    public enum ActionType {
        TAKE,
        SHIFT_TAKE,
        PUT
    }

    public static class Message implements IMessage {

        private int windowId = -1;
        private UUID resource;
        private long amount;
        private ActionType action = ActionType.PUT;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, @Nullable UUID resource, long amount, ActionType action) {
            this.windowId = windowId;
            this.resource = resource;
            this.amount = amount;
            this.action = action;
            valid = amount > 0 && action != null && (action == ActionType.PUT || resource != null);
        }

        public static Message take(int windowId, UUID resource, long amount) {
            return new Message(windowId, resource, amount, ActionType.TAKE);
        }

        public static Message shiftTake(int windowId, UUID resource, long amount) {
            return new Message(windowId, resource, amount, ActionType.SHIFT_TAKE);
        }

        public static Message put(int windowId, long amount) {
            return new Message(windowId, null, amount, ActionType.PUT);
        }

        public int getWindowId() {
            return windowId;
        }

        @Nullable
        public UUID getResource() {
            return resource;
        }

        public long getAmount() {
            return amount;
        }

        public ActionType getAction() {
            return action;
        }

        public boolean isValid() {
            return valid;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeByte(action.ordinal());
            buffer.writeBoolean(resource != null);
            if (resource != null) {
                PacketHandler.writeUUID(buffer, resource);
            }
            buffer.writeLong(amount);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                int actionOrdinal = buffer.readUnsignedByte();
                if (actionOrdinal < 0 || actionOrdinal >= ActionType.values().length) {
                    throw new IllegalArgumentException("Unknown QIO viewer action");
                }
                action = ActionType.values()[actionOrdinal];
                resource = buffer.readBoolean() ? PacketHandler.readUUID(buffer) : null;
                amount = buffer.readLong();
                valid = amount > 0 && (action == ActionType.PUT || resource != null);
            } catch (RuntimeException ex) {
                windowId = -1;
                resource = null;
                amount = -1;
                action = ActionType.PUT;
            }
        }
    }
}
