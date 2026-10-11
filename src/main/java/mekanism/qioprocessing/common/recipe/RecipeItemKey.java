package mekanism.qioprocessing.common.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

/**
 * Frozen identity, including tags and Forge capabilities, without stack size. No live stack is retained.
 */
public final class RecipeItemKey {
    private final String itemId;
    private final int metadata;
    private final String familyKey;
    private final NBTTagCompound serialized;
    private final int hash;
    private final String fingerprint;
    private final long estimatedBytes;

    private RecipeItemKey(ItemStack stack) {
        itemId = Objects.requireNonNull(stack.getItem().getRegistryName(), "Unregistered recipe item").toString();
        metadata = stack.getMetadata();
        familyKey = familyKey(itemId, metadata);
        serialized = stack.serializeNBT().copy();
        serialized.setByte("Count", (byte) 1);
        // ItemStack writes registry names, not remappable numeric item IDs.
        hash = Objects.hash(itemId, metadata, serialized);
        fingerprint = new RecipeFingerprint().add(itemId).add(Integer.toString(metadata)).add(serialized).finish();
        estimatedBytes = 160L + 2L * (itemId.length() + serialized.toString().length() + fingerprint.length());
    }

    /**
     * Capture on the registry/server thread: capability serialization can invoke mod code.
     */
    public static RecipeItemKey capture(ItemStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Empty recipe item");
        return new RecipeItemKey(stack);
    }

    public String itemId() {
        return itemId;
    }

    public int metadata() {
        return metadata;
    }

    public String familyKey() {
        return familyKey;
    }

    static String familyKey(String item, int meta) {
        return item + "@" + meta;
    }

    public String fingerprint() {
        return fingerprint;
    }

    /**
     * Frozen display data; copying it never invokes ItemStack/capability code.
     */
    public NBTTagCompound displayNBT() {
        return serialized.copy();
    }

    long estimatedBytes() {
        return estimatedBytes;
    }

    /**
     * Materialization can initialize capabilities. Keep it on the owning game thread.
     */
    public ItemStack toStack(int count) {
        if (count <= 0) throw new IllegalArgumentException("Non-positive stack size");
        ItemStack stack = new ItemStack(serialized.copy());
        stack.setCount(count);
        return stack;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeItemKey)) return false;
        RecipeItemKey that = (RecipeItemKey) other;
        return metadata == that.metadata && itemId.equals(that.itemId) && serialized.equals(that.serialized);
    }
}

