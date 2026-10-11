package mekanism.qioprocessing.common.recipe;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.OreIngredient;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import java.util.*;

import static mekanism.qioprocessing.common.recipe.RecipeDiagnostic.Reason.*;

/**
 * One owner-thread capture. No world, matches/result/remainder calls, global item scan, or grid product.
 */
public final class RecipeScanner {
    private RecipeScanner() {
    }

    public static RecipeCatalog capture(Iterable<? extends IRecipe> recipes) {
        return new Capture().scan(recipes);
    }

    private static final class Capture {
        private final long started = System.nanoTime();
        private final Map<String, RecipeDeclaration> recipes = new TreeMap<>();
        private final List<RecipeDiagnostic> diagnostics = new ArrayList<>();
        private final IdentityHashMap<Ingredient, RecipeIngredient> ingredientInstances = new IdentityHashMap<>();
        private final Map<RecipeIngredient, RecipeIngredient> domains = new HashMap<>();
        private final Map<RecipeItemKey, RecipeItemKey> items = new HashMap<>();
        private int scanned;

        RecipeCatalog scan(Iterable<? extends IRecipe> source) {
            for (IRecipe recipe : source) captureRecipe(recipe);
            // Discard all live Ingredient/recipe references and temporary pools after publication.
            long estimated = 128;
            Set<RecipeIngredient> retainedDomains = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<RecipeItemKey> retainedItems = new HashSet<>();
            for (RecipeDeclaration recipe : recipes.values()) {
                estimated += 384L + recipe.inputs().size() * 8L + 2L * (recipe.id().length() + recipe.sourceType().length());
                retainedItems.add(recipe.output());
                for (RecipeIngredient domain : recipe.inputs()) if (!domain.isEmptySlot()) retainedDomains.add(domain);
            }
            for (RecipeIngredient domain : retainedDomains) {
                estimated += 256L + domain.candidates().size() * 8L;
                retainedItems.addAll(domain.candidates());
            }
            for (RecipeItemKey key : retainedItems) estimated += key.estimatedBytes();
            return new RecipeCatalog(new LinkedHashMap<>(recipes), diagnostics, scanned,
                    retainedDomains.size(), retainedItems.size(), estimated, started);
        }

        private void captureRecipe(IRecipe recipe) {
            scanned++;
            String id = "<unnamed>";
            try {
                if (recipe == null) {
                    diagnostic(id, NULL_RECIPE, -1, "Null registry entry");
                    return;
                }
                if (recipe.getRegistryName() == null) {
                    diagnostic(id, UNNAMED, -1, recipe.getClass().getName());
                    return;
                }
                id = recipe.getRegistryName().toString();
                if (recipes.containsKey(id)) {
                    diagnostic(id, DUPLICATE_ID, -1, "Duplicate registry name");
                    return;
                }
                if (recipe.isDynamic()) {
                    diagnostic(id, DYNAMIC, -1, "Dynamic output requires explicit encoding");
                    return;
                }
                ItemStack output = recipe.getRecipeOutput();
                if (output == null || output.isEmpty()) {
                    diagnostic(id, EMPTY_OUTPUT, -1, "No declared output");
                    return;
                }
                RecipeItemKey outputKey = item(output);
                int outputCount = output.getCount();
                List<Ingredient> source = recipe.getIngredients();
                if (source == null || source.isEmpty()) {
                    diagnostic(id, EMPTY_INPUTS, -1, "No declared inputs");
                    return;
                }
                if (source.size() > 9 || !recipe.canFit(3, 3)) {
                    diagnostic(id, INVALID_LAYOUT, -1, "Does not fit a 3x3 workbench");
                    return;
                }
                RecipeDeclaration.Layout layout = RecipeDeclaration.Layout.UNSPECIFIED;
                int width = 0, height = 0;
                if (recipe instanceof IShapedRecipe) {
                    width = ((IShapedRecipe) recipe).getRecipeWidth();
                    height = ((IShapedRecipe) recipe).getRecipeHeight();
                    if (width < 1 || height < 1 || width > 3 || height > 3 || width * height != source.size()) {
                        diagnostic(id, INVALID_LAYOUT, -1, "Inconsistent shaped dimensions");
                        return;
                    }
                    layout = RecipeDeclaration.Layout.SHAPED;
                } else if (recipe instanceof ShapelessRecipes || recipe instanceof ShapelessOreRecipe) {
                    layout = RecipeDeclaration.Layout.SHAPELESS;
                }
                List<RecipeIngredient> inputs = new ArrayList<>(source.size());
                boolean anyInput = false;
                for (int slot = 0; slot < source.size(); slot++) {
                    Ingredient ingredient = source.get(slot);
                    if (ingredient == null) throw new IllegalArgumentException("Null ingredient in slot " + slot);
                    RecipeIngredient domain = domain(ingredient);
                    inputs.add(domain);
                    anyInput |= !domain.isEmptySlot();
                    if (!domain.hasDeclaredCandidates())
                        diagnostic(id, UNENUMERATED_INGREDIENT, slot, domain.sourceType());
                }
                if (!anyInput) {
                    diagnostic(id, EMPTY_INPUTS, -1, "All declared slots are empty");
                    return;
                }
                recipes.put(id, new RecipeDeclaration(id, recipe.getClass().getName(), outputKey, outputCount,
                        layout, width, height, inputs));
            } catch (RuntimeException | LinkageError error) {
                diagnostic(id, CAPTURE_FAILED, -1, error.getClass().getName() + ": " + error.getMessage());
            }
        }

        private RecipeIngredient domain(Ingredient ingredient) {
            if (ingredient == Ingredient.EMPTY) return RecipeIngredient.EMPTY;
            RecipeIngredient existing = ingredientInstances.get(ingredient);
            if (existing != null) return existing;
            LinkedHashSet<RecipeItemKey> candidates = new LinkedHashSet<>();
            ItemStack[] stacks = ingredient.getMatchingStacks();
            if (stacks != null) for (ItemStack stack : stacks) {
                addIngredientStack(ingredient, stack, candidates);
            }
            if (ingredient instanceof OreIngredient && stacks != null) {
                java.util.Set<Integer> oreIds = new java.util.TreeSet<>();
                for (ItemStack stack : stacks)
                    if (stack != null && !stack.isEmpty()) {
                        for (int id : OreDictionary.getOreIDs(stack)) oreIds.add(id);
                    }
                for (int id : oreIds)
                    for (ItemStack raw : OreDictionary.getOres(
                            OreDictionary.getOreName(id), false)) {
                        if (raw != null && !raw.isEmpty() &&
                                raw.getMetadata() == OreDictionary.WILDCARD_VALUE && ingredient.apply(raw)) {
                            candidates.add(item(raw));
                        }
                    }
            }
            // Preserve raw wildcard declarations, including items with no creative-tab variants.
            // Reading this base field does not invoke third-party callbacks or enumerate all items.
            for (ItemStack stack : ingredient.matchingStacks) {
                addIngredientStack(ingredient, stack, candidates);
            }
            RecipeIngredient next = new RecipeIngredient(ingredient.getClass().getName(), new ArrayList<>(candidates), false);
            RecipeIngredient shared = domains.putIfAbsent(next, next);
            if (shared != null) next = shared;
            ingredientInstances.put(ingredient, next);
            return next;
        }

        private void addIngredientStack(Ingredient ingredient, ItemStack stack,
                                        LinkedHashSet<RecipeItemKey> candidates) {
            if (stack == null || stack.isEmpty()) return;
            if (stack.getMetadata() != OreDictionary.WILDCARD_VALUE) {
                candidates.add(item(stack));
                return;
            }
            int before = candidates.size();
            java.util.Set<Integer> oreIds = new java.util.TreeSet<>();
            for (int id : OreDictionary.getOreIDs(stack)) oreIds.add(id);
            for (int id : oreIds) {
                for (ItemStack raw : OreDictionary.getOres(OreDictionary.getOreName(id), false)) {
                    addConcreteIngredientStack(ingredient, raw, candidates);
                }
            }
            if (candidates.size() == before) {
                addCreativeVariants(ingredient, stack, candidates);
            }
            // Keep the original wildcard as a bounded fallback for metadata values which are
            // accepted by Forge but are not exposed through the item's creative variants or the
            // current ore dictionary entries.
            candidates.add(item(stack));
        }

        private void addConcreteIngredientStack(Ingredient ingredient, ItemStack stack,
                                                LinkedHashSet<RecipeItemKey> candidates) {
            if (stack == null || stack.isEmpty()) return;
            if (stack.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                addCreativeVariants(ingredient, stack, candidates);
            } else if (ingredient.apply(stack)) {
                candidates.add(item(stack));
            }
        }

        private void addCreativeVariants(Ingredient ingredient, ItemStack wildcard,
                                         LinkedHashSet<RecipeItemKey> candidates) {
            NonNullList<ItemStack> variants = NonNullList.create();
            wildcard.getItem().getSubItems(CreativeTabs.SEARCH, variants);
            for (ItemStack variant : variants) {
                if (variant != null && !variant.isEmpty() && variant.getItem() == wildcard.getItem()
                        && variant.getMetadata() != OreDictionary.WILDCARD_VALUE
                        && ingredient.apply(variant)) {
                    candidates.add(item(variant));
                }
            }
        }

        private RecipeItemKey item(ItemStack stack) {
            RecipeItemKey key = RecipeItemKey.capture(stack);
            RecipeItemKey shared = items.putIfAbsent(key, key);
            return shared == null ? key : shared;
        }

        private void diagnostic(String id, RecipeDiagnostic.Reason reason, int slot, String detail) {
            diagnostics.add(new RecipeDiagnostic(id, reason, slot, detail));
        }
    }
}
