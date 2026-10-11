package mekanism.qioprocessing.common.planning;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.crafting.CompoundIngredient;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.oredict.OreDictionary;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Admission for declaration access, not a replacement for server-thread recipe validation.
 * In particular, an IC2 IngredientRecipeInput is not trusted without inspecting its nested
 * IRecipeInput: the fluid implementation's getInputs performs a global item-registry scan.
 */
final class QIOWorkbenchDeclarationAdapters {
    private static final int MAX_DEPTH = 32;
    private static final int MAX_REFERENCES = 4096;

    private QIOWorkbenchDeclarationAdapters() {
    }

    static void requireSupported(Ingredient ingredient) {
        inspectIngredient(ingredient, Collections.newSetFromMap(new IdentityHashMap<>()), 0, new int[]{0});
    }

    private static void enter(Object value, Set<Object> active, int depth, int[] references) {
        if (value == null || depth > MAX_DEPTH || ++references[0] > MAX_REFERENCES || !active.add(value)) {
            throw unsupported("CYCLIC_OR_OVERSIZED_INGREDIENT_DECLARATION");
        }
    }

    private static void inspectIngredient(Ingredient ingredient, Set<Object> active, int depth, int[] references) {
        if (ingredient == Ingredient.EMPTY) return;
        // IC2 RecipeInputBase is itself an Ingredient; most ordinary IC2 recipes never use
        // IngredientRecipeInput. Audit the direct input by the same exact-class rules.
        String directType = ingredient.getClass().getName();
        if (directType.equals("ic2.core.recipe.RecipeInputItemStack") ||
                directType.equals("ic2.core.recipe.RecipeInputItemStackExact") ||
                directType.equals("ic2.core.recipe.RecipeInputOreDict") ||
                directType.equals("ic2.core.recipe.RecipeInputIngredient") ||
                directType.equals("ic2.core.recipe.RecipeInputMultiple") ||
                directType.equals("ic2.core.recipe.RecipeInputFluidContainer")) {
            inspectIC2(ingredient, active, depth, references);
            return;
        }
        enter(ingredient, active, depth, references);
        try {
            String type = ingredient.getClass().getName();
            switch (type) {
                case "net.minecraft.item.crafting.Ingredient":
                case "net.minecraftforge.oredict.OreIngredient":
                case "net.minecraftforge.common.crafting.IngredientNBT":
                case "mekanism.common.recipe.MekanismIngredient":
                case "blusunrize.immersiveengineering.common.crafting.IngredientMultiOre":
                case "blusunrize.immersiveengineering.common.crafting.IngredientFactoryStackableNBT$IngredientStackableNBT":
                    return;
                case "blusunrize.immersiveengineering.common.crafting.IngredientFluidStack":
                    requireBucketFluid(field(ingredient, "fluid"));
                    return;
                case "blusunrize.immersiveengineering.common.crafting.IngredientIngrStack":
                    Object stack = field(ingredient, "ingredientStack");
                    if (stack == null || !stack.getClass().getName().equals("blusunrize.immersiveengineering.api.crafting.IngredientStack")) {
                        throw unsupported("UNSUPPORTED_IE_INGREDIENT_STACK");
                    }
                    Object fluid = field(stack, "fluid");
                    if (fluid != null) requireBucketFluid(fluid);
                    else if (!Integer.valueOf(1).equals(field(stack, "inputSize"))) {
                        throw unsupported("UNSUPPORTED_IE_WORKBENCH_SLOT_AMOUNT");
                    }
                    return;
                case "net.minecraftforge.common.crafting.CompoundIngredient":
                case "mekanism.common.recipe.MultiOredictIngredient":
                    for (Ingredient child : ((CompoundIngredient) ingredient).getChildren()) {
                        inspectIngredient(child, active, depth + 1, references);
                    }
                    return;
                case "ic2.core.recipe.IngredientRecipeInput":
                    inspectIC2(field(ingredient, "part"), active, depth + 1, references);
                    return;
                default:
                    throw unsupported("UNSUPPORTED_INGREDIENT_IMPLEMENTATION: " + type);
            }
        } finally {
            active.remove(ingredient);
        }
    }

    private static void inspectIC2(Object input, Set<Object> active, int depth, int[] references) {
        enter(input, active, depth, references);
        try {
            String type = input.getClass().getName();
            switch (type) {
                case "ic2.core.recipe.RecipeInputItemStack":
                case "ic2.core.recipe.RecipeInputItemStackExact":
                case "ic2.core.recipe.RecipeInputOreDict":
                    return;
                case "ic2.core.recipe.RecipeInputIngredient":
                    Object child = field(input, "ingredient");
                    if (!(child instanceof Ingredient)) throw unsupported("INVALID_IC2_INGREDIENT_WRAPPER");
                    inspectIngredient((Ingredient) child, active, depth + 1, references);
                    return;
                case "ic2.core.recipe.RecipeInputMultiple":
                    Object children = field(input, "inputs");
                    if (!(children instanceof Object[])) throw unsupported("INVALID_IC2_MULTIPLE_INPUT");
                    for (Object nested : (Object[]) children) inspectIC2(nested, active, depth + 1, references);
                    return;
                case "ic2.core.recipe.RecipeInputFluidContainer":
                    Object fluid = field(input, "fluid");
                    Object amount = field(input, "amount");
                    if (!(fluid instanceof Fluid) || !(amount instanceof Integer) || (Integer) amount <= 0 || (Integer) amount > 1000) {
                        throw unsupported("UNSUPPORTED_IC2_FLUID_REQUIREMENT: canonical bucket requires 1..1000 mB of a named fluid");
                    }
                    return;
                default:
                    throw unsupported("UNSUPPORTED_IC2_INPUT_IMPLEMENTATION: " + type);
            }
        } finally {
            active.remove(input);
        }
    }

    private static Object field(Object instance, String name) {
        try {
            // Only exact audited optional-mod class names reach this method. No optional-mod class is
            // linked from QIO's constant pool, and no reflected live object leaves capture.
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException | RuntimeException error) {
            throw unsupported("OPTIONAL_MOD_DECLARATION_LAYOUT_CHANGED: " + instance.getClass().getName() + "." + name);
        }
    }

    /**
     * Expand only item identities named by this ingredient, never ForgeRegistries.ITEMS.
     */
    static List<ItemStack> matchingStacks(Ingredient ingredient) {
        requireSupported(ingredient);
        ItemStack[] declared = declaredStacks(ingredient);
        List<ItemStack> result = new ArrayList<>();
        if (declared == null) return result;
        for (ItemStack stack : declared) {
            if (stack == null || stack.isEmpty()) continue;
            if (stack.getMetadata() != OreDictionary.WILDCARD_VALUE) {
                result.add(stack);
                continue;
            }
            NonNullList<ItemStack> variants = NonNullList.create();
            stack.getItem().getSubItems(CreativeTabs.SEARCH, variants);
            int before = result.size();
            for (ItemStack variant : variants) {
                if (!variant.isEmpty() && variant.getItem() == stack.getItem() &&
                        variant.getMetadata() != OreDictionary.WILDCARD_VALUE) result.add(variant);
            }
            if (before == result.size())
                throw unsupported("UNRESOLVED_DECLARED_ITEM_WILDCARD: " + stack.getItem().getRegistryName());
        }
        return result;
    }

    /**
     * Flatten audited wrappers ourselves so no parent calls IC2's global fluid-container scan.
     */
    private static ItemStack[] declaredStacks(Ingredient ingredient) {
        String type = ingredient.getClass().getName();
        if (type.equals("blusunrize.immersiveengineering.common.crafting.IngredientFluidStack")) {
            return bucketStacks((FluidStack) field(ingredient, "fluid"));
        }
        if (type.equals("blusunrize.immersiveengineering.common.crafting.IngredientIngrStack")) {
            Object stack = field(ingredient, "ingredientStack");
            FluidStack fluid = (FluidStack) field(stack, "fluid");
            if (fluid != null) return bucketStacks(fluid);
            // The audited item branch enumerates only the explicit stack/list/named ore.
        }
        if (type.equals("ic2.core.recipe.IngredientRecipeInput")) return ic2Stacks(field(ingredient, "part"));
        if (type.startsWith("ic2.core.recipe.RecipeInput")) return ic2Stacks(ingredient);
        if (type.equals("net.minecraftforge.common.crafting.CompoundIngredient") ||
                type.equals("mekanism.common.recipe.MultiOredictIngredient")) {
            List<ItemStack> result = new ArrayList<>();
            for (Ingredient child : ((CompoundIngredient) ingredient).getChildren())
                Collections.addAll(result, declaredStacks(child));
            return result.toArray(new ItemStack[0]);
        }
        return ingredient.getMatchingStacks();
    }

    private static void requireBucketFluid(Object value) {
        if (!(value instanceof FluidStack) || ((FluidStack) value).getFluid() == null ||
                ((FluidStack) value).amount <= 0 || ((FluidStack) value).amount > 1000) {
            throw unsupported("UNSUPPORTED_IE_FLUID_REQUIREMENT: canonical bucket requires 1..1000 mB");
        }
    }

    private static ItemStack[] bucketStacks(FluidStack required) {
        FluidStack full = required.copy();
        full.amount = 1000;
        ItemStack bucket = FluidUtil.getFilledBucket(full);
        FluidStack contained = bucket.isEmpty() ? null : FluidUtil.getFluidContained(bucket);
        if (contained == null || !contained.containsFluid(required)) {
            throw unsupported("IE_FLUID_HAS_NO_CANONICAL_BUCKET: " + required.getFluid().getName());
        }
        return new ItemStack[]{bucket};
    }

    private static ItemStack[] ic2Stacks(Object input) {
        String type = input.getClass().getName();
        if (type.equals("ic2.core.recipe.RecipeInputFluidContainer")) {
            Fluid fluid = (Fluid) field(input, "fluid");
            int amount = (Integer) field(input, "amount");
            // Explicit canonical representatives; a virtual candidate consumes this complete
            // container's fluid amount. Never pretend a partly consumed bucket is amount mB.
            ItemStack bucket = FluidUtil.getFilledBucket(new FluidStack(fluid, 1000));
            FluidStack contained = bucket.isEmpty() ? null : FluidUtil.getFluidContained(bucket);
            if (contained == null || contained.getFluid() != fluid || contained.amount < amount) {
                throw unsupported("IC2_FLUID_HAS_NO_CANONICAL_BUCKET: " + fluid.getName());
            }
            return new ItemStack[]{bucket};
        }
        if (type.equals("ic2.core.recipe.RecipeInputMultiple")) {
            List<ItemStack> result = new ArrayList<>();
            for (Object child : (Object[]) field(input, "inputs")) Collections.addAll(result, ic2Stacks(child));
            return result.toArray(new ItemStack[0]);
        }
        if (type.equals("ic2.core.recipe.RecipeInputIngredient"))
            return declaredStacks((Ingredient) field(input, "ingredient"));
        if (input instanceof Ingredient) return ((Ingredient) input).getMatchingStacks();
        throw unsupported("UNSUPPORTED_IC2_DECLARATION_INPUT: " + type);
    }

    static IllegalArgumentException unsupported(String diagnostic) {
        return new UnsupportedDeclarationException(diagnostic);
    }

    static final class UnsupportedDeclarationException extends IllegalArgumentException {
        private UnsupportedDeclarationException(String diagnostic) {
            super(diagnostic);
        }
    }
}
