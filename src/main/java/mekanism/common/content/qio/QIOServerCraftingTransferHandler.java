package mekanism.common.content.qio;

import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import mekanism.api.Action;
import mekanism.common.Mekanism;
import mekanism.common.content.qio.QIOCraftingTransferHelper.SingularHashedItemSource;
import mekanism.common.inventory.container.IQIOItemViewerContainer;
import mekanism.common.inventory.container.slot.VirtualCraftingSlot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates and executes a JEI crafting transfer as a two-phase transaction.
 * The client supplies only source ids and amounts; all stacks, recipe matches,
 * permissions, and available counts are resolved again on the server.
 */
public final class QIOServerCraftingTransferHandler {

    private QIOServerCraftingTransferHandler() {
    }

    public static void tryTransfer(@Nonnull IQIOItemViewerContainer container, byte selectedWindow,
          @Nonnull EntityPlayer player, @Nullable IRecipe requestedRecipe,
          @Nullable Byte2ObjectMap<List<SingularHashedItemSource>> sources) {
        if (sources == null || sources.isEmpty() || selectedWindow < 0 || selectedWindow >= IQIOCraftingWindowHolder.MAX_CRAFTING_WINDOWS ||
              !(container instanceof Container) || player.openContainer != container || !((Container) container).canInteractWith(player)) {
            return;
        }
        QIOCraftingWindow window = container.getCraftingWindow(selectedWindow);
        QIOFrequency frequency = container.getServerFrequency();
        if (window == null || frequency != null && (frequency.isRemoved() || !frequency.isValid())) {
            return;
        }
        new Transfer(container, window, player, requestedRecipe, frequency).run(sources);
    }

    private static final class Transfer {

        private final IQIOItemViewerContainer container;
        private final QIOCraftingWindow window;
        private final EntityPlayer player;
        @Nullable
        private final IRecipe requestedRecipe;
        @Nullable
        private final QIOFrequency frequency;
        private final Map<SourceKey, AvailableSource> available = new HashMap<>();
        private final NonNullList<ItemStack> targetContents = NonNullList.withSize(9, ItemStack.EMPTY);
        private final NonNullList<ItemStack> windowRemainder = NonNullList.withSize(9, ItemStack.EMPTY);
        private final List<ExtractedSource> extracted = new ArrayList<>();
        private NonNullList<ItemStack> originalWindow;
        private boolean windowCommitted;

        private Transfer(IQIOItemViewerContainer container, QIOCraftingWindow window, EntityPlayer player,
              @Nullable IRecipe requestedRecipe, @Nullable QIOFrequency frequency) {
            this.container = container;
            this.window = window;
            this.player = player;
            this.requestedRecipe = requestedRecipe;
            this.frequency = frequency;
        }

        private void run(Byte2ObjectMap<List<SingularHashedItemSource>> sources) {
            if (!simulateSources(sources) || !simulateWindowReturn(sources)) {
                return;
            }
            execute(sources);
        }

        private boolean simulateSources(Byte2ObjectMap<List<SingularHashedItemSource>> sources) {
            if (sources.size() > 9) {
                return false;
            }
            for (Byte2ObjectMap.Entry<List<SingularHashedItemSource>> entry : sources.byte2ObjectEntrySet()) {
                int target = entry.getByteKey();
                if (target < 0 || target >= 9 || entry.getValue() == null || entry.getValue().isEmpty()) {
                    return false;
                }
                int currentCount = 0;
                for (SingularHashedItemSource source : entry.getValue()) {
                    if (source == null || source.getUsed() <= 0) {
                        return false;
                    }
                    AvailableSource state = resolveSource(source);
                    if (state == null || state.remaining < source.getUsed()) {
                        return false;
                    }
                    ItemStack sourceStack = state.stack;
                    ItemStack targetStack = targetContents.get(target);
                    if (targetStack.isEmpty()) {
                        int used = Math.min(source.getUsed(), sourceStack.getMaxStackSize());
                        if (used != source.getUsed()) {
                            return false;
                        }
                        targetStack = sourceStack.copy();
                        targetStack.setCount(used);
                        targetContents.set(target, targetStack);
                        currentCount = used;
                    } else {
                        if (!ItemHandlerHelper.canItemStacksStack(targetStack, sourceStack)) {
                            return false;
                        }
                        int max = Math.min(targetStack.getMaxStackSize(), 64);
                        if (currentCount + source.getUsed() > max) {
                            return false;
                        }
                        targetStack.grow(source.getUsed());
                        currentCount += source.getUsed();
                    }
                    state.remaining -= source.getUsed();
                }
            }
            InventoryCrafting dummy = createDummy(targetContents);
            IRecipe recipe = requestedRecipe;
            if (recipe == null) {
                recipe = CraftingManager.findMatchingRecipe(dummy, player.world);
            }
            if (recipe == null || !recipe.matches(dummy, player.world)) {
                return false;
            }
            ItemStack output = recipe.getCraftingResult(dummy);
            return output != null && !output.isEmpty();
        }

        private boolean simulateWindowReturn(Byte2ObjectMap<List<SingularHashedItemSource>> sources) {
            Map<Integer, Integer> consumedFromWindow = new HashMap<>();
            for (List<SingularHashedItemSource> list : sources.values()) {
                for (SingularHashedItemSource source : list) {
                    if (source.getSlot() >= 0 && source.getSlot() < 9) {
                        consumedFromWindow.merge((int) source.getSlot(), source.getUsed(), Integer::sum);
                    }
                }
            }
            SimulatedInventory simulated = new SimulatedInventory(container, player);
            List<ItemStack> qioRemainders = new ArrayList<>();
            for (int slot = 0; slot < 9; slot++) {
                ItemStack current = window.getCraftingInventory().getStackInSlot(slot);
                if (current.isEmpty()) {
                    continue;
                }
                int remaining = current.getCount() - consumedFromWindow.getOrDefault(slot, 0);
                if (remaining < 0) {
                    return false;
                }
                if (remaining > 0) {
                    ItemStack stack = current.copy();
                    stack.setCount(remaining);
                    windowRemainder.set(slot, stack);
                    ItemStack inventoryRemainder = simulated.insert(stack);
                    if (!inventoryRemainder.isEmpty()) {
                        qioRemainders.add(inventoryRemainder);
                    }
                }
            }
            return qioRemainders.isEmpty() || frequency != null && frequency.canInsertAllItems(qioRemainders);
        }

        private void execute(Byte2ObjectMap<List<SingularHashedItemSource>> sources) {
            originalWindow = NonNullList.withSize(9, ItemStack.EMPTY);
            for (int slot = 0; slot < 9; slot++) {
                ItemStack current = window.getCraftingInventory().getStackInSlot(slot);
                originalWindow.set(slot, current.isEmpty() ? ItemStack.EMPTY : current.copy());
            }
            try {
                // Extract every requested source first. No window contents are
                // touched until all source checks have succeeded.
                for (Byte2ObjectMap.Entry<List<SingularHashedItemSource>> entry : sources.byte2ObjectEntrySet()) {
                    for (SingularHashedItemSource source : entry.getValue()) {
                        ItemStack stack = extract(source);
                        if (!stack.isEmpty()) {
                            extracted.add(new ExtractedSource(source, stack));
                        }
                        AvailableSource expected = available.get(SourceKey.of(source));
                        if (stack.isEmpty() || stack.getCount() != source.getUsed() || expected == null
                              || !ItemHandlerHelper.canItemStacksStack(expected.stack, stack)) {
                            rollbackBeforeCommit();
                            return;
                        }
                    }
                }
                for (int slot = 0; slot < 9; slot++) {
                    ItemStack target = targetContents.get(slot);
                    window.getCraftingInventory().setInventorySlotContents(slot,
                          target.isEmpty() ? ItemStack.EMPTY : target.copy());
                }
                // All extracted resources are now represented by the target
                // stacks in the crafting window. From here on, rolling them
                // back would duplicate the committed window contents.
                extracted.clear();
                windowCommitted = true;
                // Return old contents that were not consumed by the transfer.
                for (ItemStack remainder : windowRemainder) {
                    if (!remainder.isEmpty()) {
                        returnStack(remainder.copy());
                    }
                }
                window.onContentsChanged();
                player.inventory.markDirty();
                Container mcContainer = (Container) container;
                mcContainer.detectAndSendChanges();
                container.sendViewerSync(player);
                if (player instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) player).sendContainerToPlayer(mcContainer);
                }
            } catch (RuntimeException ex) {
                if (windowCommitted) {
                    Mekanism.logger.error("QIO crafting transfer failed after the crafting window was committed", ex);
                } else {
                    Mekanism.logger.warn("QIO crafting transfer failed; rolling back", ex);
                    rollbackBeforeCommit();
                }
            }
        }

        @Nullable
        private AvailableSource resolveSource(SingularHashedItemSource source) {
            SourceKey key = SourceKey.of(source);
            AvailableSource existing = available.get(key);
            if (existing != null) {
                return existing;
            }
            if (source.getSlot() == -1) {
                UUID uuid = source.getQioSource();
                QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(uuid);
                if (frequency == null || type == null || type.getKind() != QIOResourceKind.ITEM || frequency.getStored(uuid) <= 0) {
                    return null;
                }
                AvailableSource created = new AvailableSource(type.createItemStack(1),
                      (int) Math.min(Integer.MAX_VALUE, frequency.getStored(uuid)), uuid, (byte) -1);
                available.put(key, created);
                return created;
            }
            Slot slot = getSourceSlot(source.getSlot());
            if (slot == null || !slot.canTakeStack(player) || !slot.getHasStack()) {
                return null;
            }
            AvailableSource created = new AvailableSource(slot.getStack().copy(), slot.getStack().getCount(), null, source.getSlot());
            available.put(key, created);
            return created;
        }

        private ItemStack extract(SingularHashedItemSource source) {
            if (source.getSlot() == -1) {
                if (frequency == null) {
                    return ItemStack.EMPTY;
                }
                UUID uuid = source.getQioSource();
                long amount = frequency.massExtract(uuid, source.getUsed(), Action.EXECUTE);
                if (amount != source.getUsed()) {
                    if (amount > 0) {
                        restoreQioItem(uuid, amount, "partial crafting source extraction");
                    }
                    return ItemStack.EMPTY;
                }
                return QIOResourceTypeRegistry.INSTANCE.createItemStack(uuid, source.getUsed());
            }
            Slot slot = getSourceSlot(source.getSlot());
            if (slot == null || !slot.canTakeStack(player) || !slot.getHasStack()) {
                return ItemStack.EMPTY;
            }
            ItemStack extractedStack = slot.decrStackSize(source.getUsed());
            slot.onSlotChanged();
            return extractedStack;
        }

        @Nullable
        private Slot getSourceSlot(byte sourceSlot) {
            if (sourceSlot < 0 || sourceSlot >= 45) {
                return null;
            }
            if (sourceSlot < 9) {
                VirtualCraftingSlot virtual = container.getCraftingWindowSlot(window.getWindowIndex(), sourceSlot);
                return virtual == null ? null : virtual.getSlot();
            }
            int elementStart = container.getSlotElementStartIndex();
            int index = sourceSlot < 18 ? elementStart + 27 + (sourceSlot - 9) : elementStart + sourceSlot - 18;
            if (!(container instanceof Container) || index < 0 || index >= ((Container) container).inventorySlots.size()) {
                return null;
            }
            return ((Container) container).inventorySlots.get(index);
        }

        private void rollbackBeforeCommit() {
            for (ExtractedSource source : extracted) {
                if (source.source.getSlot() == -1) {
                    UUID uuid = source.source.getQioSource();
                    restoreQioItem(uuid, source.stack.getCount(), "crafting source rollback");
                } else if (source.source.getSlot() >= 9) {
                    returnStack(source.stack.copy());
                }
            }
            extracted.clear();
            if (originalWindow != null) {
                for (int slot = 0; slot < originalWindow.size(); slot++) {
                    ItemStack original = originalWindow.get(slot);
                    window.getCraftingInventory().setInventorySlotContents(slot,
                          original.isEmpty() ? ItemStack.EMPTY : original.copy());
                }
                window.onContentsChanged();
            }
        }

        private void restoreQioItem(@Nullable UUID uuid, long amount, String operation) {
            if (uuid == null || amount <= 0) {
                return;
            }
            long restored = frequency == null ? 0 : QIORollback.restore(frequency, uuid, amount, operation);
            long unresolved = amount - restored;
            while (unresolved > 0) {
                int batch = (int) Math.min(Integer.MAX_VALUE, unresolved);
                ItemStack stack = QIOResourceTypeRegistry.INSTANCE.createItemStack(uuid, batch);
                if (stack.isEmpty()) {
                    Mekanism.logger.error("Unable to materialize {} unresolved items while rolling back QIO {}", unresolved, operation);
                    return;
                }
                returnStack(stack);
                unresolved -= batch;
            }
        }

        private void returnStack(ItemStack stack) {
            if (stack.isEmpty()) {
                return;
            }
            ItemStack remainder = container.insertIntoPlayerInventory(player, stack.copy(), false);
            if (!remainder.isEmpty() && frequency != null) {
                remainder = frequency.addItem(remainder);
            }
            if (!remainder.isEmpty()) {
                player.dropItem(remainder, false);
            }
        }
    }

    private static InventoryCrafting createDummy(NonNullList<ItemStack> contents) {
        InventoryCrafting dummy = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(@Nonnull EntityPlayer player) {
                return true;
            }
        }, 3, 3);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = contents.get(slot);
            dummy.setInventorySlotContents(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return dummy;
    }

    private static final class SourceKey {
        @Nullable
        private final UUID uuid;
        private final int slot;

        private SourceKey(@Nullable UUID uuid, int slot) {
            this.uuid = uuid;
            this.slot = slot;
        }

        private static SourceKey of(SingularHashedItemSource source) {
            return new SourceKey(source.getQioSource(), source.getSlot());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SourceKey)) {
                return false;
            }
            SourceKey other = (SourceKey) obj;
            return slot == other.slot && java.util.Objects.equals(uuid, other.uuid);
        }

        @Override
        public int hashCode() {
            return 31 * slot + (uuid == null ? 0 : uuid.hashCode());
        }
    }

    private static final class AvailableSource {
        private final ItemStack stack;
        private int remaining;
        @Nullable
        private final UUID uuid;
        private final byte slot;

        private AvailableSource(ItemStack stack, int remaining, @Nullable UUID uuid, byte slot) {
            this.stack = stack;
            this.remaining = remaining;
            this.uuid = uuid;
            this.slot = slot;
        }
    }

    private static final class ExtractedSource {
        private final SingularHashedItemSource source;
        private final ItemStack stack;

        private ExtractedSource(SingularHashedItemSource source, ItemStack stack) {
            this.source = source;
            this.stack = stack;
        }
    }

    /** Lightweight inventory simulation used for the preflight room check. */
    private static final class SimulatedInventory {
        private final List<ItemStack> stacks = new ArrayList<>();
        private final List<Integer> limits = new ArrayList<>();

        private SimulatedInventory(IQIOItemViewerContainer container, EntityPlayer player) {
            if (!(container instanceof Container)) {
                return;
            }
            for (Slot slot : ((Container) container).inventorySlots) {
                if (slot.inventory == player.inventory && slot.canTakeStack(player)) {
                    ItemStack stack = slot.getStack();
                    stacks.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
                    limits.add(Math.min(slot.getSlotStackLimit(), stack.isEmpty() ? 64 : stack.getMaxStackSize()));
                }
            }
        }

        private ItemStack insert(ItemStack input) {
            if (input.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack remaining = input.copy();
            for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                for (int i = 0; i < stacks.size() && !remaining.isEmpty(); i++) {
                    ItemStack current = stacks.get(i);
                    if (pass == 0 && (current.isEmpty() || !ItemHandlerHelper.canItemStacksStack(current, remaining))) {
                        continue;
                    }
                    if (pass == 1 && !current.isEmpty()) {
                        continue;
                    }
                    int capacity = limits.get(i) - (current.isEmpty() ? 0 : current.getCount());
                    int add = Math.min(remaining.getCount(), Math.min(capacity, remaining.getMaxStackSize()));
                    if (add <= 0) {
                        continue;
                    }
                    if (current.isEmpty()) {
                        ItemStack created = remaining.copy();
                        created.setCount(add);
                        stacks.set(i, created);
                    } else {
                        current.grow(add);
                    }
                    remaining.shrink(add);
                }
            }
            return remaining;
        }
    }
}
