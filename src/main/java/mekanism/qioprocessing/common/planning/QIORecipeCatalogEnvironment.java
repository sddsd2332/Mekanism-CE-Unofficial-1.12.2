package mekanism.qioprocessing.common.planning;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Server-start fingerprint which avoids SEARCH expansion but validates recipe and ore content. */
final class QIORecipeCatalogEnvironment {

    private static final String FORMAT = "qio-recipe-environment-v3";
    private static final int MAX_NBT_DEPTH = 128;

    private QIORecipeCatalogEnvironment() {
    }

    /** The supplied recipe list must be an immutable registry-name-sorted server-thread view. */
    @Nonnull
    static String fingerprint(@Nonnull List<? extends IRecipe> recipes) {
        Objects.requireNonNull(recipes, "recipes");
        MessageDigest digest = sha256();
        appendString(digest, FORMAT);

        List<ModContainer> mods = new ArrayList<>(Loader.instance().getActiveModList());
        mods.sort(Comparator.comparing(ModContainer::getModId));
        appendInt(digest, mods.size());
        for (ModContainer mod : mods) {
            appendString(digest, mod.getModId());
            appendString(digest, mod.getVersion());
        }

        List<Item> items = new ArrayList<>(ForgeRegistries.ITEMS.getValuesCollection());
        items.removeIf(item -> item == null || item.getRegistryName() == null);
        items.sort(Comparator.comparing(item -> item.getRegistryName().toString()));
        appendInt(digest, items.size());
        for (Item item : items) {
            appendString(digest, item.getRegistryName().toString());
            appendInt(digest, Item.getIdFromItem(item));
        }

        List<Block> blocks = new ArrayList<>(ForgeRegistries.BLOCKS.getValuesCollection());
        blocks.removeIf(block -> block == null || block.getRegistryName() == null);
        blocks.sort(Comparator.comparing(block -> block.getRegistryName().toString()));
        appendInt(digest, blocks.size());
        for (Block block : blocks) {
            appendString(digest, block.getRegistryName().toString());
            appendInt(digest, Block.getIdFromBlock(block));
        }

        FingerprintContext context = new FingerprintContext();
        appendInt(digest, recipes.size());
        for (IRecipe recipe : recipes) {
            appendRecipe(digest, recipe, context);
        }

        String[] capturedOreNames = OreDictionary.getOreNames();
        List<String> oreNames = new ArrayList<>();
        if (capturedOreNames != null) {
            for (String oreName : capturedOreNames) {
                if (oreName != null && !oreName.isEmpty()) oreNames.add(oreName);
            }
        }
        oreNames.sort(String::compareTo);
        appendInt(digest, oreNames.size());
        for (String oreName : oreNames) {
            // Forge exposes a mutable ore list. Snapshot it before hashing so a late registry
            // mutation cannot invalidate the iterator or mix two logical generations.
            List<ItemStack> values = new ArrayList<>(OreDictionary.getOres(oreName, false));
            appendOre(digest, oreName, OreDictionary.getOreID(oreName), values, context);
        }
        return lowerHex(digest.digest());
    }

    /** Package-visible deterministic record used by focused compatibility tests. */
    @Nonnull
    static String recipeFingerprint(@Nonnull IRecipe recipe) {
        MessageDigest digest = sha256();
        appendString(digest, FORMAT);
        appendRecipe(digest, Objects.requireNonNull(recipe, "recipe"),
              new FingerprintContext());
        return lowerHex(digest.digest());
    }

    /** Capability-neutral, fixed-size identity for one selected nine-slot workbench grid. */
    @Nonnull
    static String targetedGridFingerprint(@Nonnull List<ItemStack> grid) {
        Objects.requireNonNull(grid, "grid");
        if (grid.size() != 9) {
            throw new IllegalArgumentException("Workbench pattern grid must contain nine slots");
        }
        MessageDigest digest = sha256();
        appendString(digest, FORMAT);
        appendString(digest, "targeted-grid-v1");
        for (ItemStack stack : grid) {
            if (stack == null || stack.isEmpty()) {
                appendBoolean(digest, false);
                continue;
            }
            if (stack.getItem().getRegistryName() == null) {
                throw new IllegalArgumentException("Targeted workbench item is not registered");
            }
            appendBoolean(digest, true);
            appendString(digest, stack.getItem().getRegistryName().toString());
            appendInt(digest, stack.getMetadata());
            // ForgeCaps is not part of ItemStack#getTagCompound and is intentionally omitted.
            appendNbt(digest, stack.getTagCompound(), 0);
        }
        return lowerHex(digest.digest());
    }

    /** Deterministic ore record used by the environment fingerprint and focused tests. */
    @Nonnull
    static String oreFingerprint(@Nonnull String oreName, int oreId,
          @Nonnull List<ItemStack> values) {
        MessageDigest digest = sha256();
        appendString(digest, FORMAT);
        appendOre(digest, oreName, oreId, values, new FingerprintContext());
        return lowerHex(digest.digest());
    }

    private static void appendOre(MessageDigest digest, String oreName, int oreId,
          List<ItemStack> values, FingerprintContext context) {
        appendString(digest, Objects.requireNonNull(oreName, "oreName"));
        appendInt(digest, oreId);
        List<ItemStack> checked = Objects.requireNonNull(values, "ore values");
        appendStacks(digest, checked.toArray(new ItemStack[0]), context);
    }

    private static void appendRecipe(MessageDigest digest, IRecipe recipe,
          FingerprintContext context) {
        if (recipe == null || recipe.getRegistryName() == null) {
            throw new IllegalArgumentException("Recipe fingerprint input is not registered");
        }
        appendString(digest, recipe.getRegistryName().toString());
        appendString(digest, recipe.getClass().getName());
        appendBoolean(digest, recipe.isDynamic());
        appendString(digest, recipe.getGroup());
        for (int height = 1; height <= 3; height++) {
            for (int width = 1; width <= 3; width++) {
                appendBoolean(digest, recipe.canFit(width, height));
            }
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        appendInt(digest, ingredients == null ? -1 : ingredients.size());
        if (ingredients != null) {
            for (Ingredient ingredient : ingredients) {
                if (ingredient == null) {
                    appendInt(digest, -1);
                    continue;
                }
                byte[] signature = context.ingredientSignatures.get(ingredient);
                if (signature == null) {
                    context.ingredientDigest.reset();
                    ItemStack[] matching = ingredient.getMatchingStacks();
                    appendStacks(context.ingredientDigest,
                          matching == null ? new ItemStack[0] : matching, context);
                    signature = context.ingredientDigest.digest();
                    context.ingredientSignatures.put(ingredient, signature);
                }
                appendBytes(digest, signature);
            }
        }
        appendBytes(digest, stackSignature(recipe.getRecipeOutput(), context));
    }

    private static void appendStacks(MessageDigest digest, ItemStack[] stacks,
          FingerprintContext context) {
        List<byte[]> signatures = new ArrayList<>(stacks.length);
        for (ItemStack stack : stacks) {
            signatures.add(stackSignature(stack, context));
        }
        signatures.sort(QIORecipeCatalogEnvironment::compareUnsigned);
        appendInt(digest, signatures.size());
        signatures.forEach(signature -> appendBytes(digest, signature));
    }

    private static byte[] stackSignature(ItemStack stack, FingerprintContext context) {
        MessageDigest digest = context.stackDigest;
        digest.reset();
        if (stack == null || stack.isEmpty()) {
            appendBoolean(digest, false);
            return digest.digest();
        }
        appendBoolean(digest, true);
        appendString(digest, String.valueOf(stack.getItem().getRegistryName()));
        appendInt(digest, stack.getMetadata());
        appendInt(digest, stack.getCount());
        // These fields already describe the stack shell. Hash the ordinary tag directly to
        // avoid allocating a temporary compound per candidate; ForgeCaps remains excluded.
        appendNbt(digest, stack.getTagCompound(), 0);
        return digest.digest();
    }

    private static void appendNbt(MessageDigest digest, NBTBase value, int depth) {
        if (depth > MAX_NBT_DEPTH) {
            throw new IllegalArgumentException("Recipe environment NBT is too deeply nested");
        }
        if (value == null) {
            appendInt(digest, -1);
            return;
        }
        appendInt(digest, value.getId());
        if (value instanceof NBTTagCompound compound) {
            List<String> keys = new ArrayList<>(compound.getKeySet());
            keys.sort(String::compareTo);
            appendInt(digest, keys.size());
            for (String key : keys) {
                appendString(digest, key);
                appendNbt(digest, compound.getTag(key), depth + 1);
            }
        } else if (value instanceof NBTTagList list) {
            appendInt(digest, list.tagCount());
            for (NBTBase element : list) {
                appendNbt(digest, element, depth + 1);
            }
        } else {
            appendString(digest, value.toString());
        }
    }

    private static int compareUnsigned(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int comparison = Integer.compare(left[index] & 0xFF, right[index] & 0xFF);
            if (comparison != 0) return comparison;
        }
        return Integer.compare(left.length, right.length);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void appendBoolean(MessageDigest digest, boolean value) {
        digest.update((byte) (value ? 1 : 0));
    }

    private static void appendInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void appendBytes(MessageDigest digest, byte[] value) {
        appendInt(digest, value.length);
        digest.update(value);
    }

    private static void appendString(MessageDigest digest, String value) {
        byte[] encoded = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        appendBytes(digest, encoded);
    }

    private static String lowerHex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] encoded = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xFF;
            encoded[index * 2] = alphabet[value >>> 4];
            encoded[index * 2 + 1] = alphabet[value & 0x0F];
        }
        return new String(encoded);
    }

    private static final class FingerprintContext {

        private final java.util.IdentityHashMap<Ingredient, byte[]> ingredientSignatures =
              new java.util.IdentityHashMap<>();
        private final MessageDigest ingredientDigest = sha256();
        private final MessageDigest stackDigest = sha256();
    }
}
