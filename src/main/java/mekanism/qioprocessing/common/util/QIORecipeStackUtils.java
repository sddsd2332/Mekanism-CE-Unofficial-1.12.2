package mekanism.qioprocessing.common.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Stack operations used at the workbench recipe-selection boundary.
 *
 * <p>Forge 1.12 {@link ItemStack#copy()} and {@link ItemStack#writeToNBT(NBTTagCompound)}
 * include the capability dispatcher.  A number of integrations attach transient, very large
 * capabilities while a stack is held by a player.  Recipe selection only needs the registered
 * item, metadata, ordinary item tag, and (where requested) the count, so it must not use those
 * capability-aware operations.  Real QIO resource identities intentionally stay outside this
 * helper and continue to use {@code PortableResourceDescriptor.item}.</p>
 */
public final class QIORecipeStackUtils {

    private QIORecipeStackUtils() {
    }

    /** Returns a mutable recipe-selection copy without serializing ForgeCaps. */
    @Nonnull
    public static ItemStack copyForRecipeSelection(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return copyForRecipeSelection(stack, stack.getCount());
    }

    /** Returns a recipe-selection copy with the requested count. */
    @Nonnull
    public static ItemStack copyForRecipeSelection(@Nullable ItemStack stack, int count) {
        if (stack == null || stack.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack copy = new ItemStack(stack.getItem(), count, stack.getMetadata(), null);
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && !tag.isEmpty()) {
                copy.setTagCompound(tag.copy());
            }
            return copy.isEmpty() ? ItemStack.EMPTY : copy;
        } catch (RuntimeException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Serializes only the stable recipe-selection fields.  In particular this never invokes
     * ItemStack.writeToNBT and therefore never emits a ForgeCaps compound.
     */
    @Nonnull
    public static NBTTagCompound writeForRecipeSelection(@Nullable ItemStack stack) {
        NBTTagCompound data = new NBTTagCompound();
        if (stack == null || stack.isEmpty()) {
            return data;
        }
        ResourceLocation name = Item.REGISTRY.getNameForObject(stack.getItem());
        data.setString("id", name == null ? "minecraft:air" : name.toString());
        data.setByte("Count", (byte) stack.getCount());
        data.setShort("Damage", (short) stack.getMetadata());
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && !tag.isEmpty()) {
            data.setTag("tag", tag.copy());
        }
        return data;
    }

    /** Reads a recipe-selection stack while explicitly dropping any persisted ForgeCaps. */
    @Nonnull
    public static ItemStack readForRecipeSelection(@Nullable NBTTagCompound data) {
        if (data == null || data.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            // Copy only the fields that belong to the recipe-selection contract.  Copying the
            // complete input compound before removing ForgeCaps would still allocate the full
            // (possibly megabyte-sized) capability tree from an old or foreign cache record.
            String itemName;
            if (data.hasKey("id", 8)) {
                itemName = data.getString("id");
            } else if (data.hasKey("id", 3)) {
                // Accept the legacy numeric item-id representation as well.
                itemName = Integer.toString(data.getInteger("id"));
            } else {
                return ItemStack.EMPTY;
            }

            int count = data.hasKey("Count", 1) ? data.getByte("Count") : 1;
            int metadata = data.hasKey("Damage", 2) ? data.getShort("Damage") : 0;
            ResourceLocation registryName;
            try {
                registryName = new ResourceLocation(itemName);
            } catch (RuntimeException ignored) {
                registryName = null;
            }
            if (registryName != null) {
                // ItemStack(NBTTagCompound) resolves through the legacy item registry.  Some
                // 1.12 integrations leave an alias there, so it can produce a different Item
                // instance than ForgeRegistries.ITEMS.getValue(owner). Resolve by the Forge
                // name first to keep restored cache objects tied to the active registry entry.
                Item item = ForgeRegistries.ITEMS.getValue(registryName);
                if (item != null) {
                    ItemStack stack = new ItemStack(item, count, metadata);
                    if (data.hasKey("tag", 10)) {
                        stack.setTagCompound(data.getCompoundTag("tag").copy());
                    }
                    return copyForRecipeSelection(stack);
                }
            }

            // Keep the legacy fallback for numeric and old aliases. Cache restore diagnostics
            // are emitted by the caller when this fallback produces a different registry item.

            NBTTagCompound stable = new NBTTagCompound();
            stable.setString("id", itemName);
            stable.setByte("Count", (byte) count);
            stable.setShort("Damage", (short) metadata);
            if (data.hasKey("tag", 10)) {
                stable.setTag("tag", data.getCompoundTag("tag").copy());
            }
            return copyForRecipeSelection(new ItemStack(stable));
        } catch (RuntimeException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    /** Compares item, metadata, and ordinary NBT while ignoring stack count and capabilities. */
    public static boolean sameRecipeSelection(@Nullable ItemStack first,
          @Nullable ItemStack second) {
        if (first == null || second == null) {
            return first == second;
        }
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getItem() == second.getItem() &&
              first.getMetadata() == second.getMetadata() &&
              Objects.equals(first.getTagCompound(), second.getTagCompound());
    }

    /** Compares stable recipe fields and stack count while ignoring capabilities. */
    public static boolean sameRecipeSelectionWithCount(@Nullable ItemStack first,
          @Nullable ItemStack second) {
        return sameRecipeSelection(first, second) &&
              (first == null || second == null || first.isEmpty() ||
                    first.getCount() == second.getCount());
    }
}
