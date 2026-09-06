package mekanism.common.recipe;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.infuse.InfuseType;
import mekanism.api.recipes.IRecipeSignatureSource;
import mekanism.common.InfuseStorage;
import mekanism.common.recipe.machines.MachineRecipe;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Main-thread semantic projection. Never reflects fields or stringifies unknown objects. */
public final class RecipeSnapshotCompiler {

    private static final String FORMAT = "recipe-signature-v2:";
    private static final int MAX_DEPTH = 128;

    private RecipeSnapshotCompiler() {
    }

    public static <R extends MachineRecipe<?, ?, ?>> RecipeDefinitionSnapshot compile(String recipeId,
          RecipeGeneration generation, @Nullable R recipe) {
        if (recipe == null) {
            return new RecipeDefinitionSnapshot(recipeId, generation, "missing", Collections.emptyMap());
        }
        // Copying first could erase a custom subclass's semantics. Projection already produces detached strings.
        String signature = semanticSignature(recipe);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("class", recipe.getClass().getName());
        values.put("signature", signature);
        return new RecipeDefinitionSnapshot(recipeId, generation, signature, values);
    }

    /**
     * Accepts built-in recipe types, supported semantic values or an explicit
     * {@link IRecipeSignatureSource}. Unknown types (including unadapted recipe
     * subclasses), unsupported nested values and cycles throw
     * {@link UnsupportedRecipeSignatureException}; no partial signature is returned.
     */
    public static String semanticSignature(@Nullable Object value) {
        return FORMAT + canonical(value, new IdentityHashMap<>(), "$", 0);
    }

    /**
     * Adapter entry point for third-party recipes that cannot implement an interface.
     * Resolve their complete semantic data on the main thread and supply a stable
     * namespaced schema ID. No third-party recipe object is inspected here.
     */
    public static String semanticSignature(String schemaId, Map<String, ?> data) {
        return FORMAT + adapted(schemaId, data, new IdentityHashMap<>(), "$", 0);
    }

    private static String adapted(String schemaId, Map<String, ?> data,
          IdentityHashMap<Object, Boolean> active, String path, int depth) {
        if (schemaId == null || schemaId.trim().isEmpty() || data == null) {
            throw new UnsupportedRecipeSignatureException(data, path, "An explicit schema ID and semantic data are required");
        }
        return node("adapted", schemaId, canonical(data, active, path + ".data", depth + 1));
    }

    private static String canonical(@Nullable Object value, IdentityHashMap<Object, Boolean> active,
          String path, int depth) {
        if (depth > MAX_DEPTH) throw new UnsupportedRecipeSignatureException(value, path, "Signature nesting exceeds " + MAX_DEPTH);
        if (value == null) return node("null");
        Class<?> type = value.getClass();
        if (type == String.class || type == Character.class || type == Boolean.class ||
              type == Byte.class || type == Short.class || type == Integer.class || type == Long.class ||
              type == Float.class || type == Double.class || type == BigInteger.class || type == BigDecimal.class) {
            return node(type.getName(), value.toString());
        }
        if (value instanceof Enum<?>) {
            Enum<?> enumeration = (Enum<?>) value;
            return node(enumeration.getDeclaringClass().getName(), enumeration.name());
        }
        // Registry objects are leaves; their rendering fields are neither inspected nor resolved.
        if (value instanceof InfuseType) return node("infuse_type", ((InfuseType) value).name);
        if (value instanceof Gas) return node("gas_type", ((Gas) value).getName());
        if (value instanceof Fluid) return node("fluid_type", ((Fluid) value).getName());
        if (value instanceof Item) return node("item_type", String.valueOf(((Item) value).getRegistryName()));
        if (value instanceof Block) return node("block_type", String.valueOf(((Block) value).getRegistryName()));
        if (type == ResourceLocation.class) return node("resource", value.toString());
        if (active.put(value, Boolean.TRUE) != null) {
            throw new UnsupportedRecipeSignatureException(value, path, "Cyclic signature data");
        }
        try {
            if (type == ItemStack.class) {
                ItemStack stack = (ItemStack) value;
                return node("item", canonical(stack.getItem(), active, path + ".item", depth + 1),
                      Integer.toString(stack.getMetadata()), Integer.toString(stack.getCount()),
                      canonical(stack.getTagCompound(), active, path + ".tag", depth + 1));
            }
            if (type == FluidStack.class) {
                FluidStack stack = (FluidStack) value;
                return node("fluid", canonical(stack.getFluid(), active, path + ".fluid", depth + 1),
                      Integer.toString(stack.amount), canonical(stack.tag, active, path + ".tag", depth + 1));
            }
            if (type == GasStack.class) {
                GasStack stack = (GasStack) value;
                return node("gas", canonical(stack.getGas(), active, path + ".gas", depth + 1), Integer.toString(stack.amount));
            }
            if (type == InfuseStorage.class) {
                InfuseStorage storage = (InfuseStorage) value;
                return node("infuse", canonical(storage.getType(), active, path + ".type", depth + 1), Integer.toString(storage.getAmount()));
            }
            if (value instanceof IRecipeSignatureSource) {
                IRecipeSignatureSource source = (IRecipeSignatureSource) value;
                return adapted(source.getRecipeSignatureType(), source.getRecipeSignatureData(), active, path, depth);
            }
            if (value instanceof Map<?, ?>) {
                List<String> entries = new ArrayList<>();
                int index = 0;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    String entryPath = path + '[' + index++ + ']';
                    entries.add(node("entry", canonical(entry.getKey(), active, entryPath + ".key", depth + 1),
                          canonical(entry.getValue(), active, entryPath + ".value", depth + 1)));
                }
                Collections.sort(entries);
                return node("map", entries.toArray(new String[0]));
            }
            if (value instanceof Iterable<?>) {
                List<String> elements = new ArrayList<>();
                int index = 0;
                for (Object element : (Iterable<?>) value) elements.add(canonical(element, active, path + '[' + index++ + ']', depth + 1));
                if (value instanceof Set<?>) Collections.sort(elements);
                return node(value instanceof Set<?> ? "set" : "list", elements.toArray(new String[0]));
            }
            if (type.isArray()) {
                List<String> elements = new ArrayList<>();
                for (int index = 0; index < Array.getLength(value); index++) {
                    elements.add(canonical(Array.get(value, index), active, path + '[' + index + ']', depth + 1));
                }
                return node(type.getName(), elements.toArray(new String[0]));
            }
            if (type == NBTTagCompound.class) {
                NBTTagCompound tag = (NBTTagCompound) value;
                Map<String, NBTBase> entries = new LinkedHashMap<>();
                for (String key : tag.getKeySet()) entries.put(key, tag.getTag(key));
                return node("nbt_compound", canonical(entries, active, path, depth + 1));
            }
            if (type == NBTTagList.class) {
                NBTTagList tag = (NBTTagList) value;
                List<NBTBase> entries = new ArrayList<>();
                for (int index = 0; index < tag.tagCount(); index++) entries.add(tag.get(index));
                return node("nbt_list", Integer.toString(tag.getTagType()), canonical(entries, active, path, depth + 1));
            }
            if (type == NBTTagEnd.class || type == NBTTagByte.class || type == NBTTagShort.class ||
                  type == NBTTagInt.class || type == NBTTagLong.class || type == NBTTagFloat.class ||
                  type == NBTTagDouble.class || type == NBTTagString.class || type == NBTTagByteArray.class ||
                  type == NBTTagIntArray.class || type == NBTTagLongArray.class) {
                return node(type.getName(), value.toString());
            }
            Map<String, Object> fields = BuiltinRecipeSignatureData.capture(value);
            if (fields != null) return node(type.getName(), canonical(fields, active, path, depth + 1));
            throw new UnsupportedRecipeSignatureException(value, path, "Unsupported recipe signature value");
        } finally {
            active.remove(value);
        }
    }

    // Length framing prevents delimiter-containing values from producing the same signature.
    private static String node(String type, String... values) {
        StringBuilder result = new StringBuilder(type).append('(');
        for (String value : values) {
            if (value == null) result.append("-1:");
            else result.append(value.length()).append(':').append(value);
        }
        return result.append(')').toString();
    }
}
