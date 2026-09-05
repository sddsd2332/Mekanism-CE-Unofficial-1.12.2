package mekanism.common.recipe;

import mekanism.common.recipe.machines.MachineRecipe;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import mekanism.api.gas.GasStack;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiles mutable recipe objects into registry-independent semantic values. */
public final class RecipeSnapshotCompiler {

    private RecipeSnapshotCompiler() {
    }

    public static <R extends MachineRecipe<?, ?, ?>> RecipeDefinitionSnapshot compile(String recipeId,
          RecipeGeneration generation, @Nullable R recipe) {
        if (recipe == null) {
            return new RecipeDefinitionSnapshot(recipeId, generation, "missing", Collections.emptyMap());
        }
        // copy() is intentionally confined to this main-thread conversion boundary.
        MachineRecipe<?, ?, ?> copied = recipe.copy();
        String signature = canonical(copied, new IdentityHashMap<>());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("class", copied.getClass().getName());
        values.put("signature", signature);
        return new RecipeDefinitionSnapshot(recipeId, generation, signature, values);
    }

    public static String semanticSignature(@Nullable Object value) {
        return canonical(value, new IdentityHashMap<>());
    }

    private static String canonical(@Nullable Object value, IdentityHashMap<Object, Boolean> seen) {
        if (value == null) return "null";
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return value.getClass().getName() + ':' + value;
        }
        if (value instanceof ItemStack) {
            ItemStack stack = (ItemStack) value;
            return "item(" + String.valueOf(stack.getItem().getRegistryName()) + ',' + stack.getMetadata() + ',' +
                  stack.getCount() + ',' + (stack.hasTagCompound() ? stack.getTagCompound() : "") + ')';
        }
        if (value instanceof FluidStack) {
            FluidStack stack = (FluidStack) value;
            return "fluid(" + stack.getFluid().getName() + ',' + stack.amount + ',' + (stack.tag == null ? "" : stack.tag) + ')';
        }
        if (value instanceof GasStack) {
            GasStack stack = (GasStack) value;
            return "gas(" + (stack.getGas() == null ? "" : stack.getGas().getName()) + ',' + stack.amount + ')';
        }
        if (value.getClass().isArray()) {
            if (seen.put(value, Boolean.TRUE) != null) return "<cycle>";
            StringBuilder builder = new StringBuilder(value.getClass().getName()).append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) builder.append(canonical(java.lang.reflect.Array.get(value, i), seen)).append(';');
            return builder.append(']').toString();
        }
        if (value instanceof Map<?, ?>) {
            if (seen.put(value, Boolean.TRUE) != null) return "<cycle>";
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                entries.add(canonical(entry.getKey(), seen) + '=' + canonical(entry.getValue(), seen));
            }
            Collections.sort(entries);
            return "map{" + String.join(";", entries) + '}';
        }
        if (value instanceof Iterable<?>) {
            if (seen.put(value, Boolean.TRUE) != null) return "<cycle>";
            StringBuilder builder = new StringBuilder(value.getClass().getName()).append('[');
            for (Object element : (Iterable<?>) value) builder.append(canonical(element, seen)).append(';');
            return builder.append(']').toString();
        }
        if (seen.put(value, Boolean.TRUE) != null) return "<cycle>";
        List<Field> fields = new ArrayList<>();
        Class<?> current = value.getClass();
        while (current != null && current != Object.class) {
            Collections.addAll(fields, current.getDeclaredFields());
            current = current.getSuperclass();
        }
        fields.removeIf(field -> Modifier.isStatic(field.getModifiers()) || field.isSynthetic());
        fields.sort(Comparator.comparing(Field::getName));
        StringBuilder builder = new StringBuilder(value.getClass().getName()).append('{');
        for (Field field : fields) {
            try {
                if (!field.isAccessible()) field.setAccessible(true);
                builder.append(field.getName()).append('=').append(canonical(field.get(value), seen)).append(';');
            } catch (RuntimeException | IllegalAccessException ignored) {
                builder.append(field.getName()).append("=<inaccessible>;");
            }
        }
        return builder.append('}').toString();
    }
}
