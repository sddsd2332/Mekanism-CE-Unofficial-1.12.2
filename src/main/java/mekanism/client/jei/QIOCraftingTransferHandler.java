package mekanism.client.jei;

import it.unimi.dsi.fastutil.bytes.Byte2ObjectArrayMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import mekanism.common.Mekanism;
import mekanism.common.content.qio.QIOCraftingTransferHelper.SingularHashedItemSource;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.network.qio.PacketQIOFillCraftingWindow;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IStackHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JEI transfer adapter for the server-validated QIO crafting window. */
public class QIOCraftingTransferHandler<CONTAINER extends QIOItemViewerContainer> implements IRecipeTransferHandler<CONTAINER> {

    private final Class<CONTAINER> containerClass;
    private final IRecipeTransferHandlerHelper handlerHelper;
    private final IStackHelper stackHelper;

    public QIOCraftingTransferHandler(Class<CONTAINER> containerClass, IRecipeTransferHandlerHelper handlerHelper, IStackHelper stackHelper) {
        this.containerClass = containerClass;
        this.handlerHelper = handlerHelper;
        this.stackHelper = stackHelper;
    }

    @Override
    public Class<CONTAINER> getContainerClass() {
        return containerClass;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(CONTAINER container, IRecipeLayout recipeLayout, EntityPlayer player,
          boolean maxTransfer, boolean doTransfer) {
        // JEI and HEI invoke this handler on the client. The UUID overload is
        // server-only and intentionally has no client-side selection map.
        byte selected = container.getSelectedCraftingGrid();
        if (selected < 0) {
            //Match modern Mekanism: this is not a transfer failure. There is
            //simply no active crafting target, so JEI/HEI should hide the
            //transfer button instead of displaying an unknown-error tooltip.
            return handlerHelper.createInternalError();
        }
        List<RecipeInput> inputs = getRecipeInputs(recipeLayout);
        if (inputs == null) {
            return handlerHelper.createInternalError();
        }
        List<Source> available = getAvailableSources(container, selected, player);
        Byte2ObjectMap<List<SingularHashedItemSource>> sources = new Byte2ObjectArrayMap<>(inputs.size());
        List<Integer> missing = new ArrayList<>();
        int maxSets = maxTransfer ? 64 : 1;
        for (int set = 0; set < maxSets; set++) {
            List<Source> trialAvailable = copySources(available);
            Byte2ObjectMap<List<SingularHashedItemSource>> trialSources = copyUsedSources(sources);
            boolean matched = true;
            for (RecipeInput input : inputs) {
                if (getUsedForTarget(trialSources, input.targetSlot) + input.needed > input.maxStackSize) {
                    matched = false;
                    break;
                }
                List<SingularHashedItemSource> used = useSources(trialAvailable, input.ingredientStacks, input.needed);
                if (used.isEmpty()) {
                    if (set == 0) {
                        missing.add(input.guiSlot);
                    }
                    matched = false;
                    break;
                }
                List<SingularHashedItemSource> target = trialSources.computeIfAbsent(input.targetSlot, ignored -> new ArrayList<>());
                for (SingularHashedItemSource source : used) {
                    addSource(target, source);
                }
            }
            if (!matched) {
                break;
            }
            available = trialAvailable;
            sources = trialSources;
        }
        if (!missing.isEmpty()) {
            return handlerHelper.createUserErrorForSlots(I18n.translateToLocal("jei.tooltip.error.recipe.transfer.missing"), missing);
        }
        if (doTransfer) {
            boolean expanded = maxTransfer || requiresExpandedPacket(sources);
            Mekanism.packetHandler.sendToServer(new PacketQIOFillCraftingWindow.Message(container.windowId, null, expanded, sources));
        }
        return null;
    }

    @Nullable
    static List<RecipeInput> getRecipeInputs(IRecipeLayout layout) {
        List<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>> entries = new ArrayList<>(layout.getItemStacks().getGuiIngredients().entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getKey));
        List<RecipeInput> inputs = new ArrayList<>();
        boolean[] seenTargets = new boolean[9];
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : entries) {
            IGuiIngredient<ItemStack> ingredient = entry.getValue();
            if (!ingredient.isInput()) {
                continue;
            }
            int target = entry.getKey() - 1;
            if (target < 0 || target >= 9 || seenTargets[target]) {
                return null;
            }
            seenTargets[target] = true;
            List<ItemStack> stacks = ingredient.getAllIngredients();
            if (!stacks.isEmpty()) {
                inputs.add(new RecipeInput(entry.getKey(), (byte) target, stacks, getNeeded(stacks), getMaxStackSize(stacks)));
            }
        }
        return inputs;
    }

    private List<Source> getAvailableSources(CONTAINER container, byte selected, EntityPlayer player) {
        List<Source> sources = new ArrayList<>();
        for (byte slot = 0; slot < 9; slot++) {
            Slot crafting = container.getCraftingWindowSlot(selected, slot);
            if (crafting != null && crafting.getHasStack() && crafting.canTakeStack(player)) {
                sources.add(new Source(crafting.getStack().copy(), slot, null));
            }
        }
        if (!(container instanceof Container)) {
            return sources;
        }
        int start = container.getSlotElementStartIndex();
        Container mcContainer = container;
        for (byte hotbar = 0; hotbar < 9; hotbar++) {
            addInventorySource(sources, mcContainer, start + 27 + hotbar, (byte) (9 + hotbar), player);
        }
        for (byte main = 0; main < 27; main++) {
            addInventorySource(sources, mcContainer, start + main, (byte) (18 + main), player);
        }
        for (QIOResourceEntry entry : container.getResourceEntries()) {
            if (entry.getKind() == mekanism.common.content.qio.QIOResourceKind.ITEM && entry.getAmount() > 0) {
                sources.add(new Source(entry.createItemStack((int) Math.min(Integer.MAX_VALUE, entry.getAmount())), (byte) -1, entry.getUUID()));
            }
        }
        return sources;
    }

    private void addInventorySource(List<Source> sources, Container container, int index, byte sourceSlot, EntityPlayer player) {
        if (index >= 0 && index < container.inventorySlots.size()) {
            Slot slot = container.inventorySlots.get(index);
            if (slot != null && slot.getHasStack() && slot.canTakeStack(player)) {
                sources.add(new Source(slot.getStack().copy(), sourceSlot, null));
            }
        }
    }

    private List<Source> copySources(List<Source> sources) {
        List<Source> copy = new ArrayList<>(sources.size());
        for (Source source : sources) {
            copy.add(new Source(source));
        }
        return copy;
    }

    private Byte2ObjectMap<List<SingularHashedItemSource>> copyUsedSources(Byte2ObjectMap<List<SingularHashedItemSource>> sources) {
        Byte2ObjectMap<List<SingularHashedItemSource>> copy = new Byte2ObjectArrayMap<>(sources.size());
        for (Byte2ObjectMap.Entry<List<SingularHashedItemSource>> entry : sources.byte2ObjectEntrySet()) {
            List<SingularHashedItemSource> list = new ArrayList<>(entry.getValue().size());
            for (SingularHashedItemSource source : entry.getValue()) {
                list.add(source.getSlot() == -1 ? new SingularHashedItemSource(source.getQioSource(), source.getUsed()) :
                      new SingularHashedItemSource(source.getSlot(), source.getUsed()));
            }
            copy.put(entry.getByteKey(), list);
        }
        return copy;
    }

    private List<SingularHashedItemSource> useSources(List<Source> available, List<ItemStack> ingredients, int needed) {
        List<SingularHashedItemSource> used = new ArrayList<>();
        for (Source source : available) {
            if (needed <= 0) {
                break;
            }
            if (source.remaining <= 0 || !matches(source.stack, ingredients)) {
                continue;
            }
            int amount = Math.min(needed, source.remaining);
            source.remaining -= amount;
            needed -= amount;
            used.add(source.uuid == null ? new SingularHashedItemSource(source.slot, amount) : new SingularHashedItemSource(source.uuid, amount));
        }
        return needed <= 0 ? used : java.util.Collections.emptyList();
    }

    private void addSource(List<SingularHashedItemSource> sources, SingularHashedItemSource add) {
        for (int i = 0; i < sources.size(); i++) {
            SingularHashedItemSource existing = sources.get(i);
            if (existing.getSlot() == add.getSlot() && java.util.Objects.equals(existing.getQioSource(), add.getQioSource())) {
                int amount = existing.getUsed() + add.getUsed();
                sources.set(i, existing.getSlot() == -1 ? new SingularHashedItemSource(existing.getQioSource(), amount) :
                      new SingularHashedItemSource(existing.getSlot(), amount));
                return;
            }
        }
        sources.add(add);
    }

    private int getUsedForTarget(Byte2ObjectMap<List<SingularHashedItemSource>> sources, byte target) {
        int used = 0;
        List<SingularHashedItemSource> list = sources.get(target);
        if (list != null) {
            for (SingularHashedItemSource source : list) {
                used += source.getUsed();
            }
        }
        return used;
    }

    private boolean requiresExpandedPacket(Byte2ObjectMap<List<SingularHashedItemSource>> sources) {
        for (List<SingularHashedItemSource> list : sources.values()) {
            if (list.size() != 1 || list.get(0).getUsed() != 1) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(ItemStack stored, List<ItemStack> ingredients) {
        if (stored.isEmpty()) {
            return false;
        }
        for (ItemStack ingredient : ingredients) {
            if (!ingredient.isEmpty() && (stackHelper.isEquivalent(stored, ingredient) ||
                  OreDictionary.itemMatches(ingredient, stored, false) && ItemStack.areItemStackTagsEqual(ingredient, stored))) {
                return true;
            }
        }
        return false;
    }

    private static int getNeeded(Collection<ItemStack> stacks) {
        int needed = 1;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                needed = Math.max(needed, stack.getCount());
            }
        }
        return needed;
    }

    private static int getMaxStackSize(Collection<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                return Math.max(1, stack.getMaxStackSize());
            }
        }
        return 64;
    }

    static final class RecipeInput {
        final int guiSlot;
        final byte targetSlot;
        final List<ItemStack> ingredientStacks;
        final int needed;
        final int maxStackSize;

        RecipeInput(int guiSlot, byte targetSlot, List<ItemStack> ingredientStacks, int needed, int maxStackSize) {
            this.guiSlot = guiSlot;
            this.targetSlot = targetSlot;
            this.ingredientStacks = ingredientStacks;
            this.needed = needed;
            this.maxStackSize = maxStackSize;
        }
    }

    private static final class Source {
        private final ItemStack stack;
        private final byte slot;
        @Nullable
        private final UUID uuid;
        private int remaining;

        private Source(ItemStack stack, byte slot, @Nullable UUID uuid) {
            this.stack = stack;
            this.slot = slot;
            this.uuid = uuid;
            this.remaining = stack.getCount();
        }

        private Source(Source source) {
            this.stack = source.stack;
            this.slot = source.slot;
            this.uuid = source.uuid;
            this.remaining = source.remaining;
        }
    }
}
