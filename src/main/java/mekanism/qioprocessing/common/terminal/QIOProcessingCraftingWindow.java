package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Server-owned nine-slot pattern state; it never mutates QIO storage.
 */
public final class QIOProcessingCraftingWindow {
    private final Supplier<World> world;
    private final Runnable changed;
    private final ItemStack[] slots = new ItemStack[9];
    private ItemStack output = ItemStack.EMPTY;
    private String diagnostic = "";

    public QIOProcessingCraftingWindow(Supplier<World> world, Runnable changed) {
        this.world = Objects.requireNonNull(world, "world");
        this.changed = Objects.requireNonNull(changed, "changed");
        for (int index = 0; index < slots.length; index++) slots[index] = ItemStack.EMPTY;
    }

    public ItemStack slot(int index) {
        checkIndex(index);
        return slots[index].copy();
    }

    public void setSlot(int index, ItemStack stack) {
        checkIndex(index);
        ItemStack next = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!next.isEmpty()) next.setCount(1);
        slots[index] = next;
        recompute();
        changed.run();
    }

    public ItemStack output() {
        return output.copy();
    }

    public String diagnostic() {
        return diagnostic;
    }

    public List<ItemStack> grid() {
        List<ItemStack> result = new ArrayList<>(slots.length);
        for (ItemStack slot : slots) result.add(slot.copy());
        return Collections.unmodifiableList(result);
    }

    public void recompute() {
        World current = world.get();
        if (current == null || current.isRemote) return;
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
            }
        }, 3, 3);
        for (int index = 0; index < slots.length; index++)
            inventory.setInventorySlotContents(index, slots[index].copy());
        try {
            IRecipe recipe = CraftingManager.findMatchingRecipe(inventory, current);
            output = recipe == null || recipe.getCraftingResult(inventory) == null
                    ? ItemStack.EMPTY : recipe.getCraftingResult(inventory).copy();
            diagnostic = "";
        } catch (RuntimeException error) {
            output = ItemStack.EMPTY;
            diagnostic = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            if (diagnostic.length() > 512) diagnostic = diagnostic.substring(0, 512);
        }
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList stored = new NBTTagList();
        for (int index = 0; index < slots.length; index++) {
            if (slots[index].isEmpty()) continue;
            NBTTagCompound row = new NBTTagCompound();
            row.setByte("slot", (byte) index);
            row.setTag("item", slots[index].writeToNBT(new NBTTagCompound()));
            stored.appendTag(row);
        }
        tag.setTag("slots", stored);
        if (!diagnostic.isEmpty()) tag.setString("diagnostic", diagnostic);
        return tag;
    }

    public void read(NBTTagCompound tag) {
        for (int index = 0; index < slots.length; index++) slots[index] = ItemStack.EMPTY;
        NBTTagList stored = tag == null ? new NBTTagList() : tag.getTagList("slots", NBT.TAG_COMPOUND);
        for (int index = 0; index < stored.tagCount(); index++) {
            NBTTagCompound row = stored.getCompoundTagAt(index);
            int slot = row.getByte("slot") & 0xFF;
            if (slot < slots.length && row.hasKey("item", 10)) {
                try {
                    ItemStack value = new ItemStack(row.getCompoundTag("item"));
                    if (!value.isEmpty()) {
                        value.setCount(1);
                        slots[slot] = value;
                    }
                } catch (RuntimeException ignored) {
                    diagnostic = "Invalid crafting window slot";
                }
            }
        }
        diagnostic = tag == null ? diagnostic : tag.getString("diagnostic");
        recompute();
        changed.run();
    }

    public RecipeItemKey outputKey() {
        return output.isEmpty() ? null : RecipeItemKey.capture(output);
    }

    private static void checkIndex(int index) {
        if (index < 0 || index >= 9) throw new IllegalArgumentException("Invalid crafting window slot");
    }
}

