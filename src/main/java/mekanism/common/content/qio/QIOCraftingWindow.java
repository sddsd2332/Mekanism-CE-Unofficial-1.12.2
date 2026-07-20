package mekanism.common.content.qio;

import mekanism.common.inventory.container.IQIOItemViewerContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.lib.inventory.HashedItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One persistent, server-authoritative 3x3 crafting window.
 *
 * <p>The inventory is intentionally backed by vanilla {@link InventoryCrafting}
 * so every 1.12 recipe (including custom {@link IRecipe} implementations) can
 * calculate its output. The window itself never trusts a client recipe or item
 * payload; packets only select a window and the server recomputes the recipe.</p>
 */
public class QIOCraftingWindow {

    private static final SelectedWindowData[] WINDOW_DATA = new SelectedWindowData[IQIOCraftingWindowHolder.MAX_CRAFTING_WINDOWS];

    static {
        for (byte i = 0; i < WINDOW_DATA.length; i++) {
            WINDOW_DATA[i] = new SelectedWindowData(WindowType.CRAFTING, i);
        }
    }

    private final IQIOCraftingWindowHolder holder;
    private final byte windowIndex;
    private final SelectedWindowData selectedWindow;
    private final InventoryCraftResult resultInventory = new InventoryCraftResult();
    private final InventoryCrafting craftingInventory;
    @Nullable
    private IRecipe lastRecipe;
    private boolean updating;

    public QIOCraftingWindow(@Nonnull IQIOCraftingWindowHolder holder, byte windowIndex) {
        if (windowIndex < 0 || windowIndex >= IQIOCraftingWindowHolder.MAX_CRAFTING_WINDOWS) {
            throw new IllegalArgumentException("Invalid QIO crafting window index: " + windowIndex);
        }
        this.holder = holder;
        this.windowIndex = windowIndex;
        this.selectedWindow = WINDOW_DATA[windowIndex];
        this.craftingInventory = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(@Nonnull EntityPlayer player) {
                return true;
            }

            @Override
            public void onCraftMatrixChanged(@Nonnull net.minecraft.inventory.IInventory inventory) {
                QIOCraftingWindow.this.onContentsChanged();
            }
        }, 3, 3);
    }

    public SelectedWindowData getWindowData() {
        return selectedWindow;
    }

    public byte getWindowIndex() {
        return windowIndex;
    }

    public InventoryCrafting getCraftingInventory() {
        return craftingInventory;
    }

    public InventoryCraftResult getResultInventory() {
        return resultInventory;
    }

    /** Reads only the nine input slots. The output is always recomputed. */
    public void read(@Nonnull NBTTagCompound tag) {
        updating = true;
        try {
            for (int slot = 0; slot < craftingInventory.getSizeInventory(); slot++) {
                craftingInventory.setInventorySlotContents(slot, ItemStack.EMPTY);
            }
            NBTTagList items = tag.getTagList("Items", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < items.tagCount(); i++) {
                NBTTagCompound slotTag = items.getCompoundTagAt(i);
                int slot = slotTag.getByte("Slot") & 0xFF;
                if (slot < craftingInventory.getSizeInventory()) {
                    try {
                        ItemStack stack = new ItemStack(slotTag);
                        if (!stack.isEmpty()) {
                            stack.setCount(Math.min(stack.getCount(), craftingInventory.getInventoryStackLimit()));
                            craftingInventory.setInventorySlotContents(slot, stack);
                        }
                    } catch (RuntimeException ignored) {
                        // A malformed individual slot must not invalidate the
                        // rest of a dashboard's persisted crafting windows.
                    }
                }
            }
        } finally {
            updating = false;
        }
        World world = holder.getHolderWorld();
        if (world != null && !world.isRemote) {
            updateOutputSlot(world);
        }
    }

    public void write(@Nonnull NBTTagCompound tag) {
        NBTTagList items = new NBTTagList();
        for (int slot = 0; slot < craftingInventory.getSizeInventory(); slot++) {
            ItemStack stack = craftingInventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                NBTTagCompound slotTag = new NBTTagCompound();
                slotTag.setByte("Slot", (byte) slot);
                stack.writeToNBT(slotTag);
                items.appendTag(slotTag);
            }
        }
        tag.setInteger("Version", 1);
        tag.setTag("Items", items);
    }

    public void onContentsChanged() {
        if (updating) {
            return;
        }
        holder.onCraftingWindowContentsChanged();
        World world = holder.getHolderWorld();
        if (world != null && !world.isRemote) {
            updateOutputSlot(world);
        }
    }

    /** Clears the cached recipe and recomputes the derived output. */
    public void invalidateRecipe() {
        lastRecipe = null;
        resultInventory.setInventorySlotContents(0, ItemStack.EMPTY);
        World world = holder.getHolderWorld();
        if (world != null && !world.isRemote) {
            updateOutputSlot(world);
        }
    }

    /** Recalculates the output using the server's recipe registry. */
    public void updateOutputSlot(@Nonnull World world) {
        if (craftingInventory.isEmpty()) {
            lastRecipe = null;
            resultInventory.setInventorySlotContents(0, ItemStack.EMPTY);
            return;
        }
        if (lastRecipe != null && lastRecipe.matches(craftingInventory, world)) {
            resultInventory.setInventorySlotContents(0, safeResult(lastRecipe, craftingInventory));
            return;
        }
        lastRecipe = CraftingManager.findMatchingRecipe(craftingInventory, world);
        resultInventory.setInventorySlotContents(0, lastRecipe == null ? ItemStack.EMPTY : safeResult(lastRecipe, craftingInventory));
    }

    @Nonnull
    private static ItemStack safeResult(@Nonnull IRecipe recipe, @Nonnull InventoryCrafting inventory) {
        ItemStack result = recipe.getCraftingResult(inventory);
        return result == null ? ItemStack.EMPTY : result;
    }

    public boolean isOutput(@Nonnull ItemStack stack) {
        ItemStack output = resultInventory.getStackInSlot(0);
        return !output.isEmpty() && ItemHandlerHelper.canItemStacksStack(output, stack);
    }

    /**
     * Mirrors the per-player output visibility check used by modern QIO
     * crafting windows. This is polled by the output slot's container tracker,
     * so an empty or newly valid recipe updates without reopening the window.
     */
    public boolean canViewRecipe(@Nonnull EntityPlayerMP player) {
        if (lastRecipe == null || resultInventory.getStackInSlot(0).isEmpty()) {
            return false;
        }
        return lastRecipe.isDynamic() || !player.world.getGameRules().getBoolean("doLimitedCrafting") ||
              player.getRecipeBook().isUnlocked(lastRecipe);
    }

    /**
     * Crafts repeatedly into the player's inventory. Every iteration checks
     * inventory space before mutating the QIO window, so a failed insertion
     * cannot consume ingredients.
     */
    @Nonnull
    public ItemStack performShiftCraft(@Nonnull EntityPlayer player, @Nonnull IQIOItemViewerContainer container) {
        World world = holder.getHolderWorld();
        if (world == null || world.isRemote || lastRecipe == null || !(player instanceof EntityPlayerMP) ||
              !canViewRecipe((EntityPlayerMP) player)) {
            return ItemStack.EMPTY;
        }
        ItemStack firstOutput = resultInventory.getStackInSlot(0);
        if (firstOutput.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int maxCrafts = calculateMaxCrafts(firstOutput);
        ItemStack craftedStack = ItemStack.EMPTY;
        for (int i = 0; i < maxCrafts; i++) {
            ItemStack output = resultInventory.getStackInSlot(0);
            if (output.isEmpty() || !ItemHandlerHelper.canItemStacksStack(firstOutput, output)) {
                break;
            }
            ItemStack simulated = output.copy();
            if (!container.insertIntoPlayerInventory(player, simulated, true).isEmpty()) {
                break;
            }
            ItemStack remainder = container.insertIntoPlayerInventory(player, output.copy(), false);
            int inserted = output.getCount() - (remainder.isEmpty() ? 0 : remainder.getCount());
            if (inserted <= 0) {
                break;
            }
            if (!remainder.isEmpty()) {
                returnRemainder(player, remainder);
            }
            ItemStack crafted = output.copy();
            crafted.setCount(inserted);
            onCrafted(player, crafted);
            craftedStack = crafted;
        }
        return craftedStack;
    }

    private int calculateMaxCrafts(@Nonnull ItemStack output) {
        int max = 64;
        for (int slot = 0; slot < craftingInventory.getSizeInventory(); slot++) {
            ItemStack input = craftingInventory.getStackInSlot(slot);
            if (!input.isEmpty()) {
                max = Math.min(max, Math.max(1, input.getCount()));
            }
        }
        // A one-item input may be replenished from QIO. Bound the loop to a
        // practical amount while still allowing a full output stack to craft.
        if (holder.getFrequency() != null) {
            max = Math.max(max, Math.min(1024, output.getMaxStackSize() * 16));
        }
        return Math.max(1, max);
    }

    /** Consumes one recipe set and handles container remainders/replacement. */
    public void onCrafted(@Nonnull EntityPlayer player, @Nonnull ItemStack crafted) {
        World world = holder.getHolderWorld();
        if (world == null || world.isRemote || crafted.isEmpty() || lastRecipe == null || !(player instanceof EntityPlayerMP) ||
              !canViewRecipe((EntityPlayerMP) player) || !lastRecipe.matches(craftingInventory, world)) {
            return;
        }
        if (!lastRecipe.isDynamic()) {
            ((EntityPlayerMP) player).unlockRecipes(Collections.singletonList(lastRecipe));
        }
        QIOFrequency frequency = holder.getFrequency();
        ForgeHooks.setCraftingPlayer(player);
        updating = true;
        try {
            crafted.onCrafting(world, player, crafted.getCount());
            NonNullList<ItemStack> remaining = lastRecipe.getRemainingItems(craftingInventory);
            if (remaining == null) {
                remaining = NonNullList.withSize(craftingInventory.getSizeInventory(), ItemStack.EMPTY);
            }
            for (int slot = 0; slot < craftingInventory.getSizeInventory(); slot++) {
                ItemStack input = craftingInventory.getStackInSlot(slot);
                if (!input.isEmpty()) {
                    craftingInventory.decrStackSize(slot, 1);
                }
                ItemStack remainder = slot < remaining.size() ? remaining.get(slot) : ItemStack.EMPTY;
                if (!remainder.isEmpty()) {
                    addRemainder(player, frequency, slot, remainder.copy());
                }
                if (craftingInventory.getStackInSlot(slot).isEmpty()) {
                    refillSlot(world, frequency, slot);
                }
            }
            StatBase stat = StatList.getCraftStats(crafted.getItem());
            if (stat != null) {
                player.addStat(stat);
            }
        } finally {
            ForgeHooks.setCraftingPlayer(null);
            updating = false;
            // Input consumption and QIO replacement are one logical change.
            // Persist them together and only then derive the next output.
            holder.onCraftingWindowContentsChanged();
            updateOutputSlot(world);
        }
    }

    private void refillSlot(@Nonnull World world, @Nullable QIOFrequency frequency, int slot) {
        if (frequency == null || lastRecipe == null || lastRecipe.isDynamic()) {
            return;
        }
        List<Ingredient> ingredients = getIngredientsForSlot(world, slot);
        if (ingredients.isEmpty()) {
            return;
        }
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient == Ingredient.EMPTY) {
                continue;
            }
            for (HashedItem type : new ArrayList<>(frequency.getItemDataMap().keySet())) {
                ItemStack candidate = type.createStack(1);
                if (!ingredient.apply(candidate)) {
                    continue;
                }
                craftingInventory.setInventorySlotContents(slot, candidate.copy());
                if (lastRecipe.matches(craftingInventory, world)) {
                    ItemStack removed = frequency.removeItem(candidate, 1);
                    if (!removed.isEmpty()) {
                        craftingInventory.setInventorySlotContents(slot, removed);
                        return;
                    }
                }
                craftingInventory.setInventorySlotContents(slot, ItemStack.EMPTY);
            }
        }
    }

    @Nonnull
    private List<Ingredient> getIngredientsForSlot(@Nonnull World world, int slot) {
        List<Ingredient> result = new ArrayList<>();
        if (lastRecipe == null || slot < 0 || slot >= craftingInventory.getSizeInventory()) {
            return result;
        }
        NonNullList<Ingredient> recipeIngredients = lastRecipe.getIngredients();
        if (recipeIngredients == null || recipeIngredients.isEmpty()) {
            return result;
        }
        if (lastRecipe instanceof IShapedRecipe) {
            IShapedRecipe shaped = (IShapedRecipe) lastRecipe;
            int recipeWidth = shaped.getRecipeWidth();
            int recipeHeight = shaped.getRecipeHeight();
            for (int columnStart = 0; columnStart <= 3 - recipeWidth; columnStart++) {
                for (int rowStart = 0; rowStart <= 3 - recipeHeight; rowStart++) {
                    for (boolean mirrored : new boolean[]{false, true}) {
                        if (matchesShapedLayout(shaped, recipeIngredients, columnStart, rowStart, mirrored, slot)) {
                            int column = (slot % 3) - columnStart;
                            int row = (slot / 3) - rowStart;
                            if (column >= 0 && row >= 0 && column < recipeWidth && row < recipeHeight) {
                                int index = mirrored ? recipeWidth - column - 1 + row * recipeWidth : column + row * recipeWidth;
                                Ingredient ingredient = recipeIngredients.get(index);
                                if (!result.contains(ingredient)) {
                                    result.add(ingredient);
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Shapeless recipes have no fixed coordinates. Try every recipe
            // ingredient; refillSlot performs the final full-recipe check.
            result.addAll(recipeIngredients);
        }
        return result;
    }

    private boolean matchesShapedLayout(@Nonnull IShapedRecipe shaped, @Nonnull NonNullList<Ingredient> ingredients,
          int columnStart, int rowStart, boolean mirrored, int replacementSlot) {
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                int recipeColumn = column - columnStart;
                int recipeRow = row - rowStart;
                Ingredient ingredient = Ingredient.EMPTY;
                if (recipeColumn >= 0 && recipeRow >= 0 && recipeColumn < shaped.getRecipeWidth() && recipeRow < shaped.getRecipeHeight()) {
                    int index = mirrored ? shaped.getRecipeWidth() - recipeColumn - 1 + recipeRow * shaped.getRecipeWidth() :
                          recipeColumn + recipeRow * shaped.getRecipeWidth();
                    ingredient = ingredients.get(index);
                }
                int actualSlot = column + row * 3;
                if (actualSlot != replacementSlot && !ingredient.apply(craftingInventory.getStackInSlot(actualSlot))) {
                    return false;
                }
            }
        }
        return true;
    }

    private void addRemainder(@Nonnull EntityPlayer player, @Nullable QIOFrequency frequency, int slot, @Nonnull ItemStack remainder) {
        ItemStack input = craftingInventory.getStackInSlot(slot);
        if (input.isEmpty()) {
            craftingInventory.setInventorySlotContents(slot, remainder);
            return;
        }
        if (ItemHandlerHelper.canItemStacksStack(input, remainder)) {
            int limit = Math.min(input.getMaxStackSize(), craftingInventory.getInventoryStackLimit());
            int add = Math.min(remainder.getCount(), Math.max(0, limit - input.getCount()));
            if (add > 0) {
                input.grow(add);
                remainder.shrink(add);
            }
        }
        returnRemainder(player, frequency, remainder);
    }

    private void returnRemainder(@Nonnull EntityPlayer player, @Nonnull ItemStack stack) {
        returnRemainder(player, holder.getFrequency(), stack);
    }

    private void returnRemainder(@Nonnull EntityPlayer player, @Nullable QIOFrequency frequency, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        // InventoryPlayer may return true after accepting only part of a
        // stack. Always inspect the mutated remainder before falling back to
        // QIO or dropping it.
        player.inventory.addItemStackToInventory(stack);
        if (!stack.isEmpty() && frequency != null) {
            stack = frequency.addItem(stack);
        }
        if (!stack.isEmpty()) {
            player.dropItem(stack, false);
        }
    }

    /** Empties this window to QIO or to the player, with a final drop fallback. */
    public void emptyTo(boolean toPlayerInv, @Nullable EntityPlayer player) {
        QIOFrequency frequency = holder.getFrequency();
        updating = true;
        try {
            for (int slot = 0; slot < craftingInventory.getSizeInventory(); slot++) {
                ItemStack stack = craftingInventory.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack remainder = stack.copy();
                craftingInventory.setInventorySlotContents(slot, ItemStack.EMPTY);
                if (toPlayerInv && player != null) {
                    player.inventory.addItemStackToInventory(remainder);
                    if (!remainder.isEmpty() && frequency != null) {
                        remainder = frequency.addItem(remainder);
                    }
                } else if (frequency != null) {
                    remainder = frequency.addItem(remainder);
                }
                if (!remainder.isEmpty()) {
                    if (player != null) {
                        player.dropItem(remainder, false);
                    } else {
                        // No player means a server-side network clear request. Do
                        // not destroy contents merely because the network vanished.
                        craftingInventory.setInventorySlotContents(slot, remainder);
                    }
                }
            }
        } finally {
            updating = false;
        }
        onContentsChanged();
    }
}
