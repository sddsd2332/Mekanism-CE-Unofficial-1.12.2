package mekanism.qioprocessing.common.planning;

import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StackUtils;
import mekanism.common.recipe.processing.MachineRecipeItemInputs;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchClosureMode;
import mekanism.qioprocessing.common.content.plan.QIOCandidateInputGroup;
import mekanism.qioprocessing.common.content.plan.QIOCandidateOption;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.concurrent.TimeUnit;

/** Captures a compact logical workbench directory and materializes exact variants on demand. */
public final class QIOWorkbenchRecipeCatalog {

    public static final ResourceLocation PROVIDER_ID =
          new ResourceLocation(MekanismQIOProcessing.MODID, "workbench");
    public static final int DEFAULT_MAX_CANDIDATES_PER_INGREDIENT = 64;
    public static final int DEFAULT_MAX_VARIANTS_PER_RECIPE = 4_096;
    public static final int DEFAULT_MAX_MATERIALIZED_VARIANTS = 64;
    public static final int DEFAULT_PATTERN_CACHE_SIZE = 4_096;
    public static final int DEFAULT_BATCH_COMBINATION_BUDGET = 1_000_000;
    public static final int MAX_BATCH_TARGETS =
          QIOWorkbenchConfigurationMutation.MAX_BATCH_TARGETS;
    private static final int MAX_BATCH_MATCHED_RECIPES = 65_536;
    public static final int MAX_CLOSURE_DEPTH = 64;
    public static final int MAX_CLOSURE_OUTPUTS = 4_096;
    public static final int MAX_CLOSURE_PATTERNS = 16_384;
    public static final int MAX_CLOSURE_CYCLE_SAMPLES = 16;
    public static final long MAX_CLOSURE_WALL_NANOS = TimeUnit.SECONDS.toNanos(4);

    @FunctionalInterface
    public interface PriorityResolver {

        long priority(@Nonnull ResourceLocation recipeId);
    }

    private QIOWorkbenchRecipeCatalog() {
    }

    @Nonnull
    public static String productKey(@Nonnull PortableResourceDescriptor output) {
        return sha256(Objects.requireNonNull(output, "output").toString());
    }

    /** Builds a catalog from patterns explicitly encoded in one frequency. */
    @Nonnull
    public static Snapshot capturePatterns(@Nonnull World world,
          @Nonnull Collection<QIOWorkbenchConfiguration.EncodedPattern> patterns,
          @Nonnull PriorityResolver priorityResolver) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(priorityResolver, "priorityResolver");
        Builder builder = new Builder(world, priorityResolver,
              DEFAULT_MAX_CANDIDATES_PER_INGREDIENT, DEFAULT_MAX_VARIANTS_PER_RECIPE,
              DEFAULT_MAX_MATERIALIZED_VARIANTS);
        patterns.stream().sorted(Comparator.comparing(pattern ->
              pattern.getRecipeId().toString())).forEach(builder::addEncoded);
        return builder.build();
    }

    /** Resolves and validates a client ghost grid on the server before persistence. */
    @Nonnull
    public static QIOWorkbenchConfiguration.EncodedPattern resolveEncodedPattern(
          @Nonnull World world, @Nonnull List<ItemStack> grid) {
        Objects.requireNonNull(world, "world");
        List<ItemStack> checkedGrid = normalizeGrid(grid);
        InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
        for (int slot = 0; slot < 9; slot++) {
            inventory.setInventorySlotContents(slot, checkedGrid.get(slot).copy());
        }
        IRecipe recipe = CraftingManager.findMatchingRecipe(inventory, world);
        if (recipe == null || recipe.isDynamic() || recipe.getRegistryName() == null) {
            throw new IllegalArgumentException("No stable workbench recipe matches the encoded grid");
        }
        List<ItemStack> concreteGrid = new ArrayList<>(9);
        for (ItemStack stack : checkedGrid) {
            ItemStack concrete = concreteStack(stack);
            if (!stack.isEmpty() && concrete.isEmpty()) {
                throw new IllegalArgumentException(
                      "Workbench recipe grid contains an unresolved wildcard item");
            }
            concreteGrid.add(concrete);
        }
        for (int slot = 0; slot < 9; slot++) {
            inventory.setInventorySlotContents(slot, concreteGrid.get(slot).copy());
        }
        if (!recipe.matches(inventory, world)) {
            throw new IllegalArgumentException(
                  "Workbench recipe grid has no concrete item variant");
        }
        ItemStack result = concreteStack(recipe.getCraftingResult(inventory));
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("Workbench recipe produced no output");
        }
        Snapshot snapshot = capture(world, Collections.singletonList(recipe), ignored -> 0,
              DEFAULT_MAX_CANDIDATES_PER_INGREDIENT, DEFAULT_MAX_VARIANTS_PER_RECIPE,
              DEFAULT_MAX_MATERIALIZED_VARIANTS);
        RecipeDefinition definition = snapshot.getRecipeDefinition(recipe.getRegistryName());
        PortableResourceDescriptor output = PortableResourceDescriptor.item(result);
        if (definition == null || !definition.getOutput().equals(output) ||
            definition.getOutputAmount() != result.getCount()) {
            throw new IllegalArgumentException("Workbench recipe output is not stable");
        }
        return new QIOWorkbenchConfiguration.EncodedPattern(UUID.randomUUID(),
              recipe.getRegistryName(), definition.getSignature(), output, result.getCount(),
              concreteGrid);
    }

    /**
     * Discovers every representable stable recipe for the requested exact output identities.
     * The global registry is scanned once; candidate combinations then share one bounded budget.
     */
    @Nonnull
    public static List<QIOWorkbenchConfiguration.EncodedPattern> resolveEncodedTargets(
          @Nonnull World world, @Nonnull Collection<ItemStack> targets) {
        return resolveEncodedTargets(world, targets,
              QIORecipeCatalogService.INSTANCE.getRecipeOutputIndex(world),
              DEFAULT_BATCH_COMBINATION_BUDGET);
    }

    @Nonnull
    static List<QIOWorkbenchConfiguration.EncodedPattern> resolveEncodedTargets(
          @Nonnull World world, @Nonnull Collection<ItemStack> targets,
          @Nonnull Collection<? extends IRecipe> recipes, int combinationBudget) {
        Objects.requireNonNull(recipes, "recipes");
        return resolveEncodedTargets(world, targets, RecipeOutputIndex.build(world, recipes),
              combinationBudget);
    }

    @Nonnull
    private static List<QIOWorkbenchConfiguration.EncodedPattern> resolveEncodedTargets(
          @Nonnull World world, @Nonnull Collection<ItemStack> targets,
          @Nonnull RecipeOutputIndex recipeIndex, int combinationBudget) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(recipeIndex, "recipeIndex");
        if (targets.isEmpty() || targets.size() > MAX_BATCH_TARGETS ||
            combinationBudget <= 0) {
            throw new IllegalArgumentException("Invalid workbench batch discovery bounds");
        }
        Map<PortableResourceDescriptor, ItemStack> exactTargets = new LinkedHashMap<>();
        for (ItemStack target : targets) {
            if (target == null || target.isEmpty()) {
                throw new IllegalArgumentException("Workbench batch target is empty");
            }
            ItemStack copy = target.copy();
            copy.setCount(1);
            exactTargets.putIfAbsent(PortableResourceDescriptor.item(copy), copy);
        }
        if (exactTargets.isEmpty()) {
            throw new IllegalArgumentException("Workbench batch target list is empty");
        }

        int matchedRecipes = 0;
        for (PortableResourceDescriptor target : exactTargets.keySet()) {
            for (CachedRecipe ignored : recipeIndex.recipesFor(target)) {
                matchedRecipes++;
                if (matchedRecipes > MAX_BATCH_MATCHED_RECIPES) {
                    throw new IllegalStateException("Workbench batch matched too many recipes");
                }
            }
        }
        BatchCombinationBudget budget = new BatchCombinationBudget(combinationBudget);
        List<PortableResourceDescriptor> orderedTargets = new ArrayList<>(exactTargets.keySet());
        Collections.sort(orderedTargets);
        List<QIOWorkbenchConfiguration.EncodedPattern> result = new ArrayList<>();
        for (PortableResourceDescriptor target : orderedTargets) {
            for (CachedRecipe recipe : recipeIndex.recipesFor(target)) {
                if (!budget.tryCombinations(recipe.combinationCost)) {
                    throw new IllegalStateException(
                          "Workbench batch combination budget exhausted");
                }
                result.add(recipe.newPattern());
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Reads arbitrary Forge recipes on the server thread and converts them into immutable
     * representative patterns. The returned graph contains no IRecipe references and is safe
     * to traverse on a QIO planning worker.
     */
    @Nonnull
    public static ClosureInput prepareClosure(@Nonnull World world,
          @Nonnull Collection<QIOWorkbenchConfiguration.EncodedPattern> roots,
          @Nonnull QIOWorkbenchConfiguration configuration,
          @Nonnull QIOWorkbenchClosureMode mode) {
        return prepareClosure(world, roots, configuration, mode, false);
    }

    @Nonnull
    public static ClosureInput prepareClosure(@Nonnull World world,
          @Nonnull Collection<QIOWorkbenchConfiguration.EncodedPattern> roots,
          @Nonnull QIOWorkbenchConfiguration configuration,
          @Nonnull QIOWorkbenchClosureMode mode, boolean skipCyclicRecipes) {
        ClosureCapture capture = beginClosureCapture(world, roots, configuration, mode,
              skipCyclicRecipes);
        while (!capture.process(Integer.MAX_VALUE, Long.MAX_VALUE)) {
            // An unbounded synchronous utility capture always completes in one pass.
        }
        return capture.finish();
    }

    @Nonnull
    static ClosureInput prepareClosure(@Nonnull World world,
          @Nonnull Collection<QIOWorkbenchConfiguration.EncodedPattern> roots,
          @Nonnull QIOWorkbenchConfiguration configuration,
          @Nonnull QIOWorkbenchClosureMode mode,
          @Nonnull Collection<? extends IRecipe> recipes, int combinationBudget) {
        return prepareClosure(world, roots, configuration, mode, false, recipes,
              combinationBudget);
    }

    @Nonnull
    static ClosureInput prepareClosure(@Nonnull World world,
          @Nonnull Collection<QIOWorkbenchConfiguration.EncodedPattern> roots,
          @Nonnull QIOWorkbenchConfiguration configuration,
          @Nonnull QIOWorkbenchClosureMode mode, boolean skipCyclicRecipes,
          @Nonnull Collection<? extends IRecipe> recipes, int combinationBudget) {
        ClosureCapture capture = beginClosureCapture(world, roots, configuration, mode,
              skipCyclicRecipes, recipes, combinationBudget);
        while (!capture.process(Integer.MAX_VALUE, Long.MAX_VALUE)) {
            // An unbounded synchronous test/utility capture always completes in one pass.
        }
        return capture.finish();
    }

    /** Starts an incrementally processed server-thread recipe snapshot. */
    @Nonnull
    public static ClosureCapture beginClosureCapture(@Nonnull World world,
          @Nonnull Collection<QIOWorkbenchConfiguration.EncodedPattern> roots,
          @Nonnull QIOWorkbenchConfiguration configuration,
          @Nonnull QIOWorkbenchClosureMode mode) {
        return beginClosureCapture(world, roots, configuration, mode, false);
    }

    @Nonnull
    public static ClosureCapture beginClosureCapture(@Nonnull World world,
          @Nonnull Collection<QIOWorkbenchConfiguration.EncodedPattern> roots,
          @Nonnull QIOWorkbenchConfiguration configuration,
          @Nonnull QIOWorkbenchClosureMode mode, boolean skipCyclicRecipes) {
        return beginClosureCapture(world, roots, configuration, mode,
              skipCyclicRecipes,
              QIORecipeCatalogService.INSTANCE.getRecipeOutputIndex(world),
              DEFAULT_BATCH_COMBINATION_BUDGET);
    }

    @Nonnull
    static ClosureCapture beginClosureCapture(@Nonnull World world,
          @Nonnull Collection<QIOWorkbenchConfiguration.EncodedPattern> roots,
          @Nonnull QIOWorkbenchConfiguration configuration,
          @Nonnull QIOWorkbenchClosureMode mode,
          @Nonnull Collection<? extends IRecipe> recipes, int combinationBudget) {
        return beginClosureCapture(world, roots, configuration, mode, false, recipes,
              combinationBudget);
    }

    @Nonnull
    static ClosureCapture beginClosureCapture(@Nonnull World world,
          @Nonnull Collection<QIOWorkbenchConfiguration.EncodedPattern> roots,
          @Nonnull QIOWorkbenchConfiguration configuration,
          @Nonnull QIOWorkbenchClosureMode mode, boolean skipCyclicRecipes,
          @Nonnull Collection<? extends IRecipe> recipes, int combinationBudget) {
        Objects.requireNonNull(recipes, "recipes");
        return beginClosureCapture(world, roots, configuration, mode, skipCyclicRecipes,
              RecipeOutputIndex.build(world, recipes), combinationBudget);
    }

    @Nonnull
    static ClosureCapture beginClosureCapture(@Nonnull World world,
          @Nonnull Collection<QIOWorkbenchConfiguration.EncodedPattern> roots,
          @Nonnull QIOWorkbenchConfiguration configuration,
          @Nonnull QIOWorkbenchClosureMode mode, boolean skipCyclicRecipes,
          @Nonnull RecipeOutputIndex recipeIndex, int combinationBudget) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(recipeIndex, "recipeIndex");
        if (roots.isEmpty() || mode == QIOWorkbenchClosureMode.NONE ||
            combinationBudget <= 0) {
            throw new IllegalArgumentException("Invalid workbench closure input");
        }
        return new ClosureCapture(roots, configuration, mode, skipCyclicRecipes,
              recipeIndex, combinationBudget);
    }

    /** Traverses a pure-data closure graph and can therefore run on a background worker. */
    @Nonnull
    public static ClosureResult resolveClosure(@Nonnull ClosureInput input,
          @Nonnull BooleanSupplier cancelled) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(cancelled, "cancelled");
        ClosureWalker walker = new ClosureWalker(input, cancelled);
        return walker.resolve();
    }

    private static QIOWorkbenchConfiguration.EncodedPattern copyPattern(
          QIOWorkbenchConfiguration.EncodedPattern pattern) {
        return new QIOWorkbenchConfiguration.EncodedPattern(pattern.getPatternUUID(),
              pattern.getRecipeId(), pattern.getRecipeSignature(), pattern.getOutput(),
              pattern.getOutputAmount(), pattern.getGrid());
    }

    private static List<ItemStack> normalizeGrid(List<ItemStack> grid) {
        if (grid == null || grid.size() != 9) {
            throw new IllegalArgumentException("Workbench pattern grid must contain nine slots");
        }
        List<ItemStack> result = new ArrayList<>(9);
        boolean hasInput = false;
        for (ItemStack stack : grid) {
            ItemStack copy = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            if (!copy.isEmpty()) {
                copy.setCount(1);
                hasInput = true;
            }
            result.add(copy);
        }
        if (!hasInput) {
            throw new IllegalArgumentException("Workbench pattern grid has no inputs");
        }
        return Collections.unmodifiableList(result);
    }

    /** Converts a wildcard item stack into one concrete, registered variant. */
    @Nonnull
    private static ItemStack concreteStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        if (MachineRecipeItemInputs.isConcreteInput(stack)) return stack.copy();
        List<ItemStack> expanded = MachineRecipeItemInputs.expand(stack, null);
        return expanded.isEmpty() ? ItemStack.EMPTY : expanded.get(0).copy();
    }

    @Nonnull
    static Snapshot capture(@Nullable World world, @Nonnull Collection<IRecipe> recipes,
          @Nonnull PriorityResolver priorityResolver, int maxCandidatesPerIngredient,
          int maxVariantsPerRecipe, int maxMaterializedVariants) {
        Objects.requireNonNull(recipes, "recipes");
        Objects.requireNonNull(priorityResolver, "priorityResolver");
        if (maxCandidatesPerIngredient <= 0 || maxVariantsPerRecipe <= 0 ||
              maxMaterializedVariants <= 0) {
            throw new IllegalArgumentException("QIO workbench catalog limits must be positive");
        }
        Builder builder = new Builder(world, priorityResolver, maxCandidatesPerIngredient,
              maxVariantsPerRecipe, maxMaterializedVariants);
        List<IRecipe> ordered = new ArrayList<>(recipes);
        ordered.sort(Comparator.comparing(recipe -> recipe.getRegistryName() == null ? "" :
              recipe.getRegistryName().toString()));
        ordered.forEach(builder::add);
        return builder.build();
    }

    private static final class Builder {

        @Nullable
        private final World world;
        private final PriorityResolver priorityResolver;
        private final int maxCandidatesPerIngredient;
        private final int maxVariantsPerRecipe;
        private final int maxMaterializedVariants;
        private final Map<ResourceLocation, LogicalRecipe> recipes = new LinkedHashMap<>();
        private final List<String> diagnostics = new ArrayList<>();

        private Builder(@Nullable World world, PriorityResolver priorityResolver,
              int maxCandidatesPerIngredient, int maxVariantsPerRecipe,
              int maxMaterializedVariants) {
            this.world = world;
            this.priorityResolver = priorityResolver;
            this.maxCandidatesPerIngredient = maxCandidatesPerIngredient;
            this.maxVariantsPerRecipe = maxVariantsPerRecipe;
            this.maxMaterializedVariants = maxMaterializedVariants;
        }

        private void add(@Nullable IRecipe recipe) {
            if (recipe == null || recipe.isDynamic() || recipe.getRegistryName() == null) {
                return;
            }
            ResourceLocation recipeId = recipe.getRegistryName();
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            if (ingredients == null || ingredients.size() > 9) {
                diagnostics.add("Skipped workbench recipe with an invalid grid: " + recipeId);
                return;
            }
            ItemStack declaredOutput;
            List<List<IngredientChoice>> slots;
            try {
                declaredOutput = recipe.getRecipeOutput();
                if (declaredOutput == null || declaredOutput.isEmpty()) {
                    diagnostics.add("Skipped workbench recipe without a stable declared output: " +
                          recipeId);
                    return;
                }
                declaredOutput = declaredOutput.copy();
                slots = exactGridCandidates(recipe, ingredients);
                if (!MachineRecipeItemInputs.isConcreteInput(declaredOutput)) {
                    declaredOutput = firstConcreteRecipeOutput(recipe, declaredOutput, slots);
                    if (declaredOutput.isEmpty()) {
                        throw new IllegalArgumentException(
                              "Recipe output has no concrete item variant");
                    }
                }
            } catch (RuntimeException e) {
                diagnostics.add("Unable to snapshot workbench recipe " + recipeId + ": " +
                      diagnostic(e));
                return;
            }
            LogicalRecipe logical = new LogicalRecipe(world, recipe, recipeId, declaredOutput,
                  slots, priorityResolver.priority(recipeId), maxVariantsPerRecipe,
                  maxMaterializedVariants);
            if (recipes.putIfAbsent(recipeId, logical) != null) {
                diagnostics.add("Skipped duplicate workbench recipe identity: " + recipeId);
            }
        }

        private void addEncoded(@Nonnull QIOWorkbenchConfiguration.EncodedPattern pattern) {
            ResourceLocation recipeId = pattern.getRecipeId();
            IRecipe recipe = ForgeRegistries.RECIPES.getValue(recipeId);
            if (recipe == null || recipe.isDynamic() || recipe.getRegistryName() == null) {
                diagnostics.add("Encoded workbench recipe is no longer registered: " + recipeId);
                return;
            }
            try {
                InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
                List<ItemStack> grid = pattern.getGrid();
                for (int slot = 0; slot < 9; slot++) {
                    inventory.setInventorySlotContents(slot, grid.get(slot).copy());
                }
                if (!recipe.matches(inventory, world)) {
                    diagnostics.add("Encoded workbench grid no longer matches: " + recipeId);
                    return;
                }
                ItemStack result = concreteStack(recipe.getCraftingResult(inventory));
                if (result == null || result.isEmpty() ||
                    !PortableResourceDescriptor.item(result).equals(pattern.getOutput()) ||
                    result.getCount() != pattern.getOutputAmount()) {
                    diagnostics.add("Encoded workbench output changed: " + recipeId);
                    return;
                }
                add(recipe);
                LogicalRecipe logical = recipes.get(recipeId);
                if (logical == null || !logical.signature.equals(pattern.getRecipeSignature())) {
                    recipes.remove(recipeId);
                    diagnostics.add("Encoded workbench recipe signature changed: " + recipeId);
                }
            } catch (RuntimeException error) {
                diagnostics.add("Unable to validate encoded workbench recipe " + recipeId + ": " +
                      diagnostic(error));
            }
        }

        private List<List<IngredientChoice>> exactGridCandidates(IRecipe recipe,
              NonNullList<Ingredient> ingredients) {
            List<List<IngredientChoice>> grid = new ArrayList<>(9);
            for (int slot = 0; slot < 9; slot++) {
                grid.add(Collections.singletonList(IngredientChoice.empty()));
            }
            if (recipe instanceof IShapedRecipe shaped) {
                int width = shaped.getRecipeWidth();
                int height = shaped.getRecipeHeight();
                if (width <= 0 || height <= 0 || width > 3 || height > 3 ||
                      ingredients.size() != width * height) {
                    throw new IllegalArgumentException("invalid shaped dimensions");
                }
                for (int row = 0; row < height; row++) {
                    for (int column = 0; column < width; column++) {
                        grid.set(column + row * 3,
                              candidates(ingredients.get(column + row * width)));
                    }
                }
            } else {
                for (int slot = 0; slot < ingredients.size(); slot++) {
                    grid.set(slot, candidates(ingredients.get(slot)));
                }
            }
            return grid;
        }

        private List<IngredientChoice> candidates(Ingredient ingredient) {
            if (ingredient == null || ingredient == Ingredient.EMPTY) {
                return Collections.singletonList(IngredientChoice.empty());
            }
            ItemStack[] matching = ingredient.getMatchingStacks();
            if (matching == null || matching.length == 0) {
                throw new IllegalArgumentException("Ingredient has no exact item candidates");
            }
            Map<String, IngredientChoice> unique = new LinkedHashMap<>();
            for (ItemStack candidate : matching) {
                if (candidate == null || candidate.isEmpty()) {
                    continue;
                }
                for (ItemStack concrete : MachineRecipeItemInputs.expand(candidate,
                      ingredient::apply)) {
                    ItemStack copy = concrete.copy();
                    copy.setCount(1);
                    try {
                        IngredientChoice itemChoice = IngredientChoice.item(copy);
                        unique.putIfAbsent(itemChoice.sortKey(), itemChoice);
                    } catch (RuntimeException ignored) {
                    }
                    try {
                        IngredientChoice fluidChoice = directFluidChoice(copy);
                        if (fluidChoice != null) {
                            unique.putIfAbsent(fluidChoice.sortKey(), fluidChoice);
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            List<String> keys = new ArrayList<>(unique.keySet());
            Collections.sort(keys);
            List<IngredientChoice> ordered = new ArrayList<>();
            for (String key : keys) {
                if (ordered.size() >= maxCandidatesPerIngredient) {
                    break;
                }
                ordered.add(unique.get(key));
            }
            if (ordered.isEmpty()) {
                throw new IllegalArgumentException("Ingredient has no registered exact item candidates");
            }
            return Collections.unmodifiableList(ordered);
        }

        @Nonnull
        private ItemStack firstConcreteRecipeOutput(@Nonnull IRecipe recipe,
              @Nonnull ItemStack declaredOutput,
              @Nonnull List<List<IngredientChoice>> slots) {
            InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
            return firstConcreteRecipeOutput(recipe, declaredOutput, slots, inventory, 0,
                  new int[]{0});
        }

        @Nonnull
        private ItemStack firstConcreteRecipeOutput(@Nonnull IRecipe recipe,
              @Nonnull ItemStack declaredOutput,
              @Nonnull List<List<IngredientChoice>> slots,
              @Nonnull InventoryCrafting inventory, int slot, @Nonnull int[] attempts) {
            if (attempts[0] >= maxVariantsPerRecipe) return ItemStack.EMPTY;
            if (slot >= slots.size()) {
                attempts[0]++;
                try {
                    if (!recipe.matches(inventory, world)) return ItemStack.EMPTY;
                    ItemStack result = concreteStack(recipe.getCraftingResult(inventory));
                    return StackUtils.equalsWildcardWithNBT(declaredOutput, result) ? result :
                          ItemStack.EMPTY;
                } catch (RuntimeException ignored) {
                    return ItemStack.EMPTY;
                }
            }
            for (IngredientChoice choice : slots.get(slot)) {
                inventory.setInventorySlotContents(slot, choice.matchingStack());
                ItemStack result = firstConcreteRecipeOutput(recipe, declaredOutput, slots,
                      inventory, slot + 1, attempts);
                if (!result.isEmpty()) return result;
            }
            inventory.setInventorySlotContents(slot, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }

        private Snapshot build() {
            Map<ResourceLocation, CompiledRecipe> compiled = new LinkedHashMap<>();
            List<String> compiledDiagnostics = new ArrayList<>(diagnostics);
            for (LogicalRecipe recipe : recipes.values()) {
                try {
                    CompiledRecipe template = recipe.compile();
                    if (template != null) {
                        compiled.put(recipe.recipeId, template);
                    } else {
                        compiledDiagnostics.add("Unable to compile workbench recipe " +
                              recipe.recipeId);
                    }
                } catch (RuntimeException error) {
                    compiledDiagnostics.add("Unable to compile workbench recipe " +
                          recipe.recipeId + ": " + diagnostic(error));
                }
            }
            return new Snapshot(compiled, compiledDiagnostics, DEFAULT_PATTERN_CACHE_SIZE);
        }
    }

    public static final class Snapshot {

        private final Map<ResourceLocation, CompiledRecipe> recipes;
        private final Map<PortableResourceDescriptor, List<CompiledRecipe>> recipesByOutput;
        private final List<ResourceLocation> recipeIds;
        private final Set<ResourceLocation> recipeIdSet;
        private final Set<PortableResourceDescriptor> declaredOutputs;
        private final List<String> diagnostics;
        private final String structuralSignature;
        private final Map<String, QIOWorkbenchRecipePattern> patterns;

        private Snapshot(Map<ResourceLocation, CompiledRecipe> recipes, List<String> diagnostics,
              int maximumCachedPatterns) {
            this.recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
            this.recipeIds = Collections.unmodifiableList(new ArrayList<>(recipes.keySet()));
            this.recipeIdSet = Collections.unmodifiableSet(new LinkedHashSet<>(recipes.keySet()));
            this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
            Map<PortableResourceDescriptor, List<CompiledRecipe>> outputIndex = new LinkedHashMap<>();
            for (CompiledRecipe recipe : recipes.values()) {
                outputIndex.computeIfAbsent(recipe.definition.getOutput(),
                            ignored -> new ArrayList<>())
                      .add(recipe);
            }
            List<PortableResourceDescriptor> outputs = new ArrayList<>(outputIndex.keySet());
            Collections.sort(outputs);
            Map<PortableResourceDescriptor, List<CompiledRecipe>> orderedIndex =
                  new LinkedHashMap<>();
            for (PortableResourceDescriptor output : outputs) {
                orderedIndex.put(output, Collections.unmodifiableList(outputIndex.get(output)));
            }
            recipesByOutput = Collections.unmodifiableMap(orderedIndex);
            declaredOutputs = Collections.unmodifiableSet(new LinkedHashSet<>(outputs));
            structuralSignature = compiledCatalogSignature(recipes.values());
            patterns = new LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                      Map.Entry<String, QIOWorkbenchRecipePattern> eldest) {
                    return size() > maximumCachedPatterns;
                }
            };
        }

        @Nonnull
        public List<QIOPlanningRoute> materialize(
              @Nonnull PortableResourceDescriptor output,
              @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
              @Nonnull Set<PortableResourceDescriptor> producibleResources,
              @Nonnull PriorityResolver priorityResolver) {
            Objects.requireNonNull(priorityResolver, "priorityResolver");
            Map<ResourceLocation, Long> priorities = new LinkedHashMap<>();
            List<CompiledRecipe> matching = recipesByOutput.get(output);
            if (matching != null) {
                for (CompiledRecipe recipe : matching) {
                    priorities.put(recipe.definition.getRecipeId(),
                          priorityResolver.priority(recipe.definition.getRecipeId()));
                }
            }
            return materialize(output, availableResources, producibleResources, priorities);
        }

        @Nonnull
        public List<QIOPlanningRoute> materialize(
              @Nonnull PortableResourceDescriptor output,
              @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
              @Nonnull Set<PortableResourceDescriptor> producibleResources,
              @Nonnull Map<ResourceLocation, Long> effectivePriorities) {
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(availableResources, "availableResources");
            Objects.requireNonNull(producibleResources, "producibleResources");
            Objects.requireNonNull(effectivePriorities, "effectivePriorities");
            return materialize(output, availableResources, producibleResources,
                  effectivePriorities, Integer.MAX_VALUE, null);
        }

        @Nonnull
        public List<QIOPlanningRoute> materialize(
              @Nonnull PortableResourceDescriptor output,
              @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
              @Nonnull Set<PortableResourceDescriptor> producibleResources,
              @Nonnull Map<ResourceLocation, Long> effectivePriorities, int maximumRoutes) {
            return materialize(output, availableResources, producibleResources,
                  effectivePriorities, maximumRoutes, null);
        }

        @Nonnull
        public List<QIOPlanningRoute> materialize(
              @Nonnull PortableResourceDescriptor output,
              @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
              @Nonnull Set<PortableResourceDescriptor> producibleResources,
              @Nonnull Map<ResourceLocation, Long> effectivePriorities, int maximumRoutes,
              @Nullable QIOWorkbenchConfiguration configuration) {
            return materialize(output, availableResources, producibleResources,
                  effectivePriorities, maximumRoutes, configuration, () -> false);
        }

        @Nonnull
        List<QIOPlanningRoute> materialize(
              @Nonnull PortableResourceDescriptor output,
              @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
              @Nonnull Set<PortableResourceDescriptor> producibleResources,
              @Nonnull Map<ResourceLocation, Long> effectivePriorities, int maximumRoutes,
              @Nullable QIOWorkbenchConfiguration configuration,
              @Nonnull BooleanSupplier cancelled) {
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(availableResources, "availableResources");
            Objects.requireNonNull(producibleResources, "producibleResources");
            Objects.requireNonNull(effectivePriorities, "effectivePriorities");
            Objects.requireNonNull(cancelled, "cancelled");
            if (maximumRoutes <= 0) {
                throw new IllegalArgumentException("maximumRoutes must be positive");
            }
            List<CompiledRecipe> matching = recipesByOutput.get(output);
            if (matching == null || matching.isEmpty()) {
                return Collections.emptyList();
            }
            List<CompiledRecipe> orderedMatching = new ArrayList<>();
            for (CompiledRecipe recipe : matching) {
                if (effectivePriorities.containsKey(recipe.definition.getRecipeId())) {
                    orderedMatching.add(recipe);
                }
            }
            orderedMatching.sort(Comparator.comparingLong((CompiledRecipe recipe) ->
                  effectivePriorities.get(recipe.definition.getRecipeId())).reversed()
                  .thenComparing(recipe -> recipe.definition.getRecipeId().toString()));
            List<QIOPlanningRoute> result = new ArrayList<>();
            for (CompiledRecipe recipe : orderedMatching) {
                CompiledRecipe.checkpoint(cancelled);
                if (result.size() >= maximumRoutes) {
                    break;
                }
                Long priority = effectivePriorities.get(recipe.definition.getRecipeId());
                List<QIOWorkbenchRecipePattern> materialized = recipe.materialize(
                      availableResources, producibleResources, null, configuration,
                      cancelled);
                for (int variantIndex = 0; variantIndex < materialized.size();
                      variantIndex++) {
                    if (result.size() >= maximumRoutes) {
                        break;
                    }
                    QIOWorkbenchRecipePattern pattern = materialized.get(variantIndex);
                    QIOPlanningRoute route = pattern.getRoute().withPriority(priority)
                          .withVariantPriority(Long.MAX_VALUE - variantIndex);
                    cachePattern(route.getStableId(), pattern);
                    result.add(route);
                }
            }
            result.sort(Comparator.comparingLong(QIOPlanningRoute::getRoutePriority)
                  .reversed().thenComparing(QIOPlanningRoute::getLogicalId)
                  .thenComparing(Comparator.comparingLong(
                        QIOPlanningRoute::getVariantPriority).reversed())
                  .thenComparing(QIOPlanningRoute::getStableId));
            return Collections.unmodifiableList(result);
        }

        @Nullable
        public QIOWorkbenchRecipePattern getPattern(
              @Nonnull String stableRouteId) {
            String checked = Objects.requireNonNull(stableRouteId, "stableRouteId");
            QIOWorkbenchRecipePattern cached = cachedPattern(checked);
            if (cached != null) {
                return cached;
            }
            ParsedRouteId parsed = parseStableRouteId(checked);
            if (parsed == null) {
                return null;
            }
            CompiledRecipe recipe = recipes.get(parsed.recipeId);
            if (recipe == null) {
                return null;
            }
            List<QIOWorkbenchRecipePattern> recovered = recipe.materialize(
                  Collections.emptyMap(), Collections.emptySet(), parsed.variantId, null,
                  () -> false);
            if (recovered.isEmpty()) {
                return null;
            }
            QIOWorkbenchRecipePattern pattern = recovered.get(0);
            cachePattern(pattern.getRoute().getStableId(), pattern);
            return pattern;
        }

        /** Resolves an exact workbench variant from the resources owned by one job. */
        @Nullable
        public QIOWorkbenchRecipePattern resolveExecutablePattern(
              @Nonnull String recipeId,
              @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
              @Nonnull Map<PortableResourceDescriptor, Long> expectedOutputs,
              @Nonnull QIOWorkbenchConfiguration configuration) {
            ResourceLocation id;
            try {
                id = new ResourceLocation(Objects.requireNonNull(recipeId, "recipeId"));
            } catch (RuntimeException e) {
                return null;
            }
            CompiledRecipe recipe = recipes.get(id);
            if (recipe == null || !configuration.isRecipeEnabled(recipeId,
                  recipe.definition.getSignature())) {
                return null;
            }
            List<QIOWorkbenchRecipePattern> materialized = recipe.materialize(
                  availableResources, Collections.emptySet(), null, configuration,
                  () -> false);
            for (QIOWorkbenchRecipePattern pattern : materialized) {
                if (expectedOutputs.equals(pattern.getExpectedOutputs()) &&
                    canSupply(availableResources, pattern.getExactInputs())) {
                    cachePattern(pattern.getStableRouteId(), pattern);
                    return pattern;
                }
            }
            return null;
        }

        private static boolean canSupply(Map<PortableResourceDescriptor, Long> available,
              Map<PortableResourceDescriptor, Long> requested) {
            for (Map.Entry<PortableResourceDescriptor, Long> entry : requested.entrySet()) {
                if (available.getOrDefault(entry.getKey(), 0L) < entry.getValue()) return false;
            }
            return true;
        }

        /** Number of exact patterns currently retained by the bounded cache. */
        int getCachedPatternCount() {
            synchronized (patterns) {
                return patterns.size();
            }
        }

        @Nonnull
        public List<ResourceLocation> getRecipeIds() {
            return recipeIds;
        }

        @Nonnull
        public Set<PortableResourceDescriptor> getDeclaredOutputs() {
            return declaredOutputs;
        }

        @Nonnull
        public List<PortableResourceDescriptor> getOrderedOutputs() {
            return Collections.unmodifiableList(new ArrayList<>(recipesByOutput.keySet()));
        }

        @Nonnull
        public List<ResourceLocation> getRecipeIds(
              @Nonnull PortableResourceDescriptor output) {
            List<CompiledRecipe> matching = recipesByOutput.get(
                  Objects.requireNonNull(output, "output"));
            if (matching == null || matching.isEmpty()) {
                return Collections.emptyList();
            }
            List<ResourceLocation> result = new ArrayList<>(matching.size());
            matching.forEach(recipe -> result.add(recipe.definition.getRecipeId()));
            return Collections.unmodifiableList(result);
        }

        @Nullable
        public RecipeDefinition getRecipeDefinition(@Nonnull ResourceLocation recipeId) {
            CompiledRecipe recipe = recipes.get(Objects.requireNonNull(recipeId, "recipeId"));
            return recipe == null ? null : recipe.definition;
        }

        @Nonnull
        public Set<PortableResourceDescriptor> getDeclaredOutputs(
              @Nonnull Set<ResourceLocation> enabledRecipes) {
            Objects.requireNonNull(enabledRecipes, "enabledRecipes");
            Set<PortableResourceDescriptor> result = new LinkedHashSet<>();
            for (Map.Entry<PortableResourceDescriptor, List<CompiledRecipe>> entry :
                  recipesByOutput.entrySet()) {
                for (CompiledRecipe recipe : entry.getValue()) {
                    if (enabledRecipes.contains(recipe.definition.getRecipeId())) {
                        result.add(entry.getKey());
                        break;
                    }
                }
            }
            return Collections.unmodifiableSet(result);
        }

        public boolean containsRecipe(@Nonnull ResourceLocation recipeId) {
            return recipeIdSet.contains(Objects.requireNonNull(recipeId, "recipeId"));
        }

        public boolean containsRecipe(@Nonnull String recipeId) {
            try {
                return containsRecipe(new ResourceLocation(Objects.requireNonNull(recipeId,
                      "recipeId")));
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        @Nonnull
        public List<String> getDiagnostics() {
            return diagnostics;
        }

        @Nonnull
        public String getStructuralSignature() {
            return structuralSignature;
        }

        private QIOWorkbenchRecipePattern cachedPattern(String stableRouteId) {
            synchronized (patterns) {
                return patterns.get(stableRouteId);
            }
        }

        private void cachePattern(String stableRouteId, QIOWorkbenchRecipePattern pattern) {
            synchronized (patterns) {
                patterns.put(stableRouteId, pattern);
            }
        }
    }

    /** Immutable logical recipe data used by the management terminal. */
    public static final class RecipeDefinition {

        private final ResourceLocation recipeId;
        private final PortableResourceDescriptor output;
        private final int outputAmount;
        private final boolean shaped;
        private final int width;
        private final int height;
        private final String signature;
        private final List<IngredientDefinition> ingredients;

        private RecipeDefinition(ResourceLocation recipeId,
              PortableResourceDescriptor output, int outputAmount, boolean shaped,
              int width, int height, String signature,
              List<IngredientDefinition> ingredients) {
            this.recipeId = recipeId;
            this.output = output;
            this.outputAmount = outputAmount;
            this.shaped = shaped;
            this.width = width;
            this.height = height;
            this.signature = signature;
            this.ingredients = Collections.unmodifiableList(new ArrayList<>(ingredients));
        }

        @Nonnull public ResourceLocation getRecipeId() { return recipeId; }
        @Nonnull public PortableResourceDescriptor getOutput() { return output; }
        public int getOutputAmount() { return outputAmount; }
        public boolean isShaped() { return shaped; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        @Nonnull public String getSignature() { return signature; }
        @Nonnull public List<IngredientDefinition> getIngredients() { return ingredients; }

        @Nonnull
        public List<String> getCandidateIds(int slot) {
            if (slot < 0 || slot >= ingredients.size()) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            for (CandidateDefinition candidate : ingredients.get(slot).getCandidates()) {
                result.add(candidate.getCandidateId());
            }
            return Collections.unmodifiableList(result);
        }
    }

    public static final class IngredientDefinition {

        private final int slot;
        private final List<CandidateDefinition> candidates;

        private IngredientDefinition(int slot, List<CandidateDefinition> candidates) {
            this.slot = slot;
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        }

        public int getSlot() { return slot; }
        public boolean isEmpty() { return candidates.isEmpty(); }
        @Nonnull public List<CandidateDefinition> getCandidates() { return candidates; }
    }

    public static final class CandidateDefinition {

        private final String candidateId;
        private final ItemStack displayStack;
        private final PortableResourceDescriptor resource;
        private final long amount;
        private final boolean virtualFluid;

        private CandidateDefinition(String candidateId, ItemStack displayStack,
              PortableResourceDescriptor resource, long amount, boolean virtualFluid) {
            this.candidateId = candidateId;
            this.displayStack = displayStack.copy();
            this.resource = resource;
            this.amount = amount;
            this.virtualFluid = virtualFluid;
        }

        @Nonnull public String getCandidateId() { return candidateId; }
        @Nonnull public ItemStack getDisplayStack() { return displayStack.copy(); }
        @Nonnull public PortableResourceDescriptor getResource() { return resource; }
        public long getAmount() { return amount; }
        public boolean isVirtualFluid() { return virtualFluid; }
    }

    private static final class LogicalRecipe {

        @Nullable
        private final World world;
        private final IRecipe recipe;
        private final ResourceLocation recipeId;
        private final PortableResourceDescriptor declaredOutput;
        private final int declaredOutputAmount;
        private final List<List<IngredientChoice>> candidates;
        private final boolean shaped;
        private final int width;
        private final int height;
        private final String signature;
        private final long capturedPriority;
        private final int maxVariants;
        private final int maxRetained;
        @Nullable
        private List<Map<String, Map<PortableResourceDescriptor, Long>>> candidateOutputProfiles;

        private LogicalRecipe(@Nullable World world, IRecipe recipe, ResourceLocation recipeId,
              ItemStack declaredOutput, List<List<IngredientChoice>> candidates,
              long capturedPriority, int maxVariants, int maxRetained) {
            this.world = world;
            this.recipe = recipe;
            this.recipeId = recipeId;
            this.declaredOutput = PortableResourceDescriptor.item(declaredOutput);
            this.declaredOutputAmount = declaredOutput.getCount();
            List<List<IngredientChoice>> copied = new ArrayList<>(candidates.size());
            for (List<IngredientChoice> slot : candidates) {
                copied.add(Collections.unmodifiableList(new ArrayList<>(slot)));
            }
            this.candidates = Collections.unmodifiableList(copied);
            shaped = recipe instanceof IShapedRecipe;
            width = shaped ? ((IShapedRecipe) recipe).getRecipeWidth() : 0;
            height = shaped ? ((IShapedRecipe) recipe).getRecipeHeight() : 0;
            signature = sha256(structuralSignature());
            this.capturedPriority = capturedPriority;
            this.maxVariants = maxVariants;
            this.maxRetained = maxRetained;
        }

        @Nullable
        private CompiledRecipe compile() {
            BatchCombinationBudget budget = new BatchCombinationBudget(maxVariants);
            QIOWorkbenchRecipePattern representative = representativePattern(budget);
            if (representative == null) {
                return null;
            }
            List<IngredientChoice> baseline = decodeVariant(representative.getVariantId());
            if (baseline == null) {
                return null;
            }
            ensureCandidateOutputProfiles(baseline);
            Map<PortableResourceDescriptor, Long> outputs = outputsFor(baseline);
            if (outputs == null) {
                return null;
            }
            int combinationCost = Math.max(1, maxVariants - budget.remaining);
            return new CompiledRecipe(definition(), candidates, baseline, outputs,
                  Objects.requireNonNull(candidateOutputProfiles,
                        "candidate output profiles"), structuralSignature(), maxVariants,
                  maxRetained, combinationCost);
        }

        private List<QIOWorkbenchRecipePattern> materialize(
              Map<PortableResourceDescriptor, Long> available,
              Set<PortableResourceDescriptor> producible, @Nullable String requestedVariant,
              @Nullable QIOWorkbenchConfiguration configuration) {
            if (requestedVariant != null) {
                QIOWorkbenchRecipePattern recovered = recoverPattern(requestedVariant);
                return recovered == null ? Collections.emptyList() :
                      Collections.singletonList(recovered);
            }
            List<List<IngredientChoice>> effective = effectiveCandidates(configuration);
            if (effective == null) {
                return Collections.emptyList();
            }
            List<List<IngredientChoice>> prioritized = prioritizeCandidates(effective,
                  available, producible);
            List<ScoredPattern> retained = new ArrayList<>();
            int[] attempted = {0};
            enumerate(0, emptyGrid(), attempted, retained, available, producible,
                  prioritized, effective);
            retained.sort(Comparator.comparing(ScoredPattern::score));
            List<QIOWorkbenchRecipePattern> result = new ArrayList<>(retained.size());
            for (ScoredPattern scored : retained) {
                result.add(scored.pattern);
            }
            return result;
        }

        @Nullable
        private QIOWorkbenchRecipePattern representativePattern(
              BatchCombinationBudget budget) {
            return representativePattern(0, emptyGrid(), budget, new int[]{0});
        }

        @Nullable
        private QIOWorkbenchRecipePattern representativePattern(int slot,
              List<IngredientChoice> grid, BatchCombinationBudget budget, int[] attempted) {
            if (budget.exhausted) return null;
            if (slot == 9) {
                if (attempted[0] >= maxVariants) return null;
                attempted[0]++;
                if (!budget.tryCombination()) return null;
                return createPattern(grid, candidates);
            }
            for (IngredientChoice candidate : candidates.get(slot)) {
                grid.set(slot, candidate);
                QIOWorkbenchRecipePattern pattern = representativePattern(slot + 1, grid,
                      budget, attempted);
                if (pattern != null || budget.exhausted || attempted[0] >= maxVariants) {
                    return pattern;
                }
            }
            return null;
        }

        private boolean enumerate(int slot, List<IngredientChoice> grid, int[] attempted,
              List<ScoredPattern> retained, Map<PortableResourceDescriptor, Long> available,
              Set<PortableResourceDescriptor> producible,
              List<List<IngredientChoice>> enumerationCandidates,
              List<List<IngredientChoice>> configuredCandidates) {
            if (attempted[0] >= maxVariants) {
                return false;
            }
            if (slot == 9) {
                attempted[0]++;
                QIOWorkbenchRecipePattern pattern = createPattern(grid,
                      configuredCandidates);
                if (pattern == null) {
                    return false;
                }
                addBounded(retained, new ScoredPattern(pattern,
                      VariantScore.of(pattern.getRoute(), available, producible,
                            candidatePreferenceKey(grid, configuredCandidates))));
                return false;
            }
            for (IngredientChoice candidate : enumerationCandidates.get(slot)) {
                grid.set(slot, candidate);
                if (enumerate(slot + 1, grid, attempted, retained, available, producible,
                      enumerationCandidates, configuredCandidates)) {
                    return true;
                }
                if (attempted[0] >= maxVariants) {
                    break;
                }
            }
            return false;
        }

        @Nullable
        private QIOWorkbenchRecipePattern recoverPattern(String encodedVariant) {
            List<IngredientChoice> grid = decodeVariant(encodedVariant);
            if (grid == null) {
                return null;
            }
            QIOWorkbenchRecipePattern pattern = createPattern(grid, candidates);
            return pattern != null && encodedVariant.equals(pattern.getVariantId()) ? pattern :
                  null;
        }

        @Nullable
        private List<IngredientChoice> decodeVariant(String encodedVariant) {
            String[] parts = encodedVariant.split("\\.", -1);
            if (parts.length != 10 || !"i1".equals(parts[0])) {
                return null;
            }
            List<IngredientChoice> grid = emptyGrid();
            try {
                for (int slot = 0; slot < 9; slot++) {
                    int candidateIndex = Integer.parseInt(parts[slot + 1], 36);
                    List<IngredientChoice> slotCandidates = candidates.get(slot);
                    if (candidateIndex < 0 || candidateIndex >= slotCandidates.size()) {
                        return null;
                    }
                    grid.set(slot, slotCandidates.get(candidateIndex));
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
            return grid;
        }

        private List<List<IngredientChoice>> prioritizeCandidates(
              List<List<IngredientChoice>> configured,
              Map<PortableResourceDescriptor, Long> available,
              Set<PortableResourceDescriptor> producible) {
            List<List<IngredientChoice>> prioritized = new ArrayList<>(configured.size());
            for (List<IngredientChoice> slot : configured) {
                if (slot.size() <= 1) {
                    prioritized.add(slot);
                    continue;
                }
                List<IngredientChoice> ordered = new ArrayList<>(slot);
                // List.sort is stable, so the player's configured order remains the
                // tie-breaker inside each availability class.
                ordered.sort(Comparator.comparingInt(candidate ->
                      candidateAvailability(candidate, available, producible)));
                prioritized.add(Collections.unmodifiableList(ordered));
            }
            return Collections.unmodifiableList(prioritized);
        }

        private int candidateAvailability(IngredientChoice candidate,
              Map<PortableResourceDescriptor, Long> available,
              Set<PortableResourceDescriptor> producible) {
            if (candidate.isEmpty()) return 0;
            long stored = available.getOrDefault(candidate.resource(), 0L);
            if (stored >= candidate.amount()) return 0;
            if (producible.contains(candidate.resource())) return 1;
            return stored > 0 ? 2 : 3;
        }

        @Nullable
        private List<List<IngredientChoice>> effectiveCandidates(
              @Nullable QIOWorkbenchConfiguration configuration) {
            if (configuration == null) {
                return candidates;
            }
            List<List<IngredientChoice>> effective = new ArrayList<>(candidates.size());
            for (int slot = 0; slot < candidates.size(); slot++) {
                List<IngredientChoice> defaults = candidates.get(slot);
                if (defaults.size() == 1 && defaults.get(0).isEmpty()) {
                    effective.add(defaults);
                    continue;
                }
                List<String> defaultIds = new ArrayList<>(defaults.size());
                Map<String, IngredientChoice> byId = new LinkedHashMap<>();
                for (IngredientChoice candidate : defaults) {
                    defaultIds.add(candidate.candidateId());
                    byId.put(candidate.candidateId(), candidate);
                }
                List<String> enabled = configuration.orderedEnabledCandidates(
                      recipeId.toString(), signature, slot, defaultIds);
                if (enabled.isEmpty()) {
                    return null;
                }
                List<IngredientChoice> ordered = new ArrayList<>(enabled.size());
                for (String candidateId : enabled) {
                    IngredientChoice candidate = byId.get(candidateId);
                    if (candidate != null) {
                        ordered.add(candidate);
                    }
                }
                if (ordered.isEmpty()) {
                    return null;
                }
                effective.add(Collections.unmodifiableList(ordered));
            }
            return Collections.unmodifiableList(effective);
        }

        private RecipeDefinition definition() {
            List<IngredientDefinition> ingredients = new ArrayList<>(9);
            for (int slot = 0; slot < candidates.size(); slot++) {
                List<CandidateDefinition> definitions = new ArrayList<>();
                for (IngredientChoice candidate : candidates.get(slot)) {
                    if (!candidate.isEmpty()) {
                        definitions.add(candidate.definition());
                    }
                }
                ingredients.add(new IngredientDefinition(slot, definitions));
            }
            return new RecipeDefinition(recipeId, declaredOutput, declaredOutputAmount,
                  shaped, width, height, signature, ingredients);
        }

        private void addBounded(List<ScoredPattern> retained, ScoredPattern candidate) {
            retained.add(candidate);
            retained.sort(Comparator.comparing(ScoredPattern::score));
            if (retained.size() > maxRetained) {
                retained.remove(retained.size() - 1);
            }
        }

        @Nullable
        private QIOWorkbenchRecipePattern createPattern(List<IngredientChoice> grid,
              List<List<IngredientChoice>> enabledCandidates) {
            InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
            for (int slot = 0; slot < 9; slot++) {
                inventory.setInventorySlotContents(slot, grid.get(slot).matchingStack());
            }
            try {
                if (!recipe.matches(inventory, world)) {
                    return null;
                }
                ItemStack result = concreteStack(recipe.getCraftingResult(inventory));
                if (result == null || result.isEmpty()) {
                    return null;
                }
                if (!PortableResourceDescriptor.item(result).equals(declaredOutput) ||
                    result.getCount() != declaredOutputAmount) {
                    return null;
                }
                QIOPlanningRoute.Builder routeBuilder = QIOPlanningRoute.builder(
                      ProviderKind.WORKBENCH, PROVIDER_ID, recipeId.toString(),
                      recipeId.toString()).variantId(variantId(grid)).priority(capturedPriority);
                for (IngredientChoice input : grid) {
                    if (!input.isEmpty()) {
                        routeBuilder.input(input.resource(), input.amount());
                    }
                }
                Map<PortableResourceDescriptor, Long> expectedOutputs = new LinkedHashMap<>();
                PortableResourceDescriptor resultResource = PortableResourceDescriptor.item(result);
                routeBuilder.output(resultResource, result.getCount());
                expectedOutputs.put(resultResource, (long) result.getCount());
                NonNullList<ItemStack> remaining = recipe.getRemainingItems(inventory);
                for (int slot = 0; slot < remaining.size(); slot++) {
                    ItemStack remainder = concreteStack(remaining.get(slot));
                    if (slot < grid.size() && grid.get(slot).isVirtualFluid()) {
                        continue;
                    }
                    if (remainder != null && !remainder.isEmpty()) {
                        routeBuilder.output(PortableResourceDescriptor.item(remainder),
                              remainder.getCount());
                        expectedOutputs.merge(PortableResourceDescriptor.item(remainder),
                              (long) remainder.getCount(), Math::addExact);
                    }
                }
                addCandidateGroups(routeBuilder, grid, enabledCandidates);
                QIOPlanningRoute route = routeBuilder.build();
                return new QIOWorkbenchRecipePattern(route, recipeId, matchingGrid(grid),
                      virtualFluidSlots(grid));
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private void addCandidateGroups(QIOPlanningRoute.Builder routeBuilder,
              List<IngredientChoice> grid,
              List<List<IngredientChoice>> enabledCandidates) {
            ensureCandidateOutputProfiles(grid);
            for (int slot = 0; slot < enabledCandidates.size(); slot++) {
                List<IngredientChoice> choices = enabledCandidates.get(slot);
                if (choices.size() <= 1 || choices.get(0).isEmpty()) continue;
                List<Integer> slots = Collections.singletonList(slot);
                choices = compatibleChoices(grid.get(slot), choices, slot);
                if (choices.size() <= 1) continue;
                List<QIOCandidateOption> options = new ArrayList<>(choices.size());
                for (IngredientChoice choice : choices) {
                    options.add(new QIOCandidateOption(choice.candidateId(),
                          choice.resource(), choice.amount(), choice.isVirtualFluid()));
                }
                Map<PortableResourceDescriptor, Long> plannedInputs = new LinkedHashMap<>();
                for (int groupedSlot : slots) {
                    IngredientChoice selected = grid.get(groupedSlot);
                    plannedInputs.merge(selected.resource(), selected.amount(), Math::addExact);
                }
                routeBuilder.candidateInput(new QIOCandidateInputGroup(slots, options,
                      plannedInputs));
            }
        }

        /**
         * Profiles candidate output behavior once for the logical recipe. Exact variants can then
         * reuse the result instead of invoking the Forge recipe once per candidate and variant.
         */
        private void ensureCandidateOutputProfiles(List<IngredientChoice> baselineGrid) {
            if (candidateOutputProfiles != null) return;
            List<Map<String, Map<PortableResourceDescriptor, Long>>> profiles =
                  new ArrayList<>(candidates.size());
            for (int slot = 0; slot < candidates.size(); slot++) {
                List<IngredientChoice> choices = candidates.get(slot);
                Map<String, Map<PortableResourceDescriptor, Long>> slotProfiles =
                      new LinkedHashMap<>();
                if (choices.size() > 1 && !choices.get(0).isEmpty()) {
                    for (IngredientChoice choice : choices) {
                        List<IngredientChoice> test = new ArrayList<>(baselineGrid);
                        test.set(slot, choice);
                        try {
                            Map<PortableResourceDescriptor, Long> outputs = outputsFor(test);
                            if (outputs != null) {
                                slotProfiles.put(choice.candidateId(), Collections.unmodifiableMap(
                                      new LinkedHashMap<>(outputs)));
                            }
                        } catch (RuntimeException ignored) {
                            // A broken or context-sensitive candidate remains an exact-only input.
                        }
                    }
                }
                profiles.add(Collections.unmodifiableMap(slotProfiles));
            }
            candidateOutputProfiles = Collections.unmodifiableList(profiles);
        }

        private List<IngredientChoice> compatibleChoices(IngredientChoice selected,
              List<IngredientChoice> choices, int slot) {
            List<Map<String, Map<PortableResourceDescriptor, Long>>> profiles =
                  Objects.requireNonNull(candidateOutputProfiles,
                        "candidate output profiles");
            Map<String, Map<PortableResourceDescriptor, Long>> slotProfiles = profiles.get(slot);
            Map<PortableResourceDescriptor, Long> selectedOutputs =
                  slotProfiles.get(selected.candidateId());
            if (selectedOutputs == null) return Collections.emptyList();
            List<IngredientChoice> compatible = new ArrayList<>();
            for (IngredientChoice choice : choices) {
                if (selectedOutputs.equals(slotProfiles.get(choice.candidateId()))) {
                    compatible.add(choice);
                }
            }
            return compatible;
        }

        @Nullable
        private Map<PortableResourceDescriptor, Long> outputsFor(List<IngredientChoice> grid) {
            InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
            for (int slot = 0; slot < 9; slot++) {
                inventory.setInventorySlotContents(slot, grid.get(slot).matchingStack());
            }
            if (!recipe.matches(inventory, world)) return null;
            ItemStack result = concreteStack(recipe.getCraftingResult(inventory));
            if (result == null || result.isEmpty() ||
                !PortableResourceDescriptor.item(result).equals(declaredOutput) ||
                result.getCount() != declaredOutputAmount) return null;
            Map<PortableResourceDescriptor, Long> outputs = new LinkedHashMap<>();
            outputs.put(PortableResourceDescriptor.item(result), (long) result.getCount());
            NonNullList<ItemStack> remaining = recipe.getRemainingItems(inventory);
            for (int slot = 0; slot < remaining.size(); slot++) {
                ItemStack remainder = concreteStack(remaining.get(slot));
                if (slot < grid.size() && grid.get(slot).isVirtualFluid()) continue;
                if (remainder != null && !remainder.isEmpty()) {
                    outputs.merge(PortableResourceDescriptor.item(remainder),
                          (long) remainder.getCount(), Math::addExact);
                }
            }
            return outputs;
        }

        private String variantId(List<IngredientChoice> grid) {
            StringBuilder encoded = new StringBuilder("i1");
            for (int slot = 0; slot < grid.size(); slot++) {
                int candidateIndex = candidates.get(slot).indexOf(grid.get(slot));
                if (candidateIndex < 0) {
                    throw new IllegalArgumentException(
                          "Workbench grid contains an unknown ingredient candidate");
                }
                encoded.append('.').append(Integer.toString(candidateIndex, 36));
            }
            return encoded.toString();
        }

        private String structuralSignature() {
            StringBuilder canonical = new StringBuilder(recipeId.toString()).append('|')
                  .append(recipe.getClass().getName()).append('|').append(declaredOutput)
                  .append('@').append(declaredOutputAmount).append('|');
            for (int slot = 0; slot < candidates.size(); slot++) {
                canonical.append(slot).append('=');
                for (IngredientChoice choice : candidates.get(slot)) {
                    canonical.append(choice.sortKey()).append(',');
                }
                canonical.append(';');
            }
            return canonical.toString();
        }
    }

    /** Immutable recipe template produced while Forge recipes are still confined to the server thread. */
    private static final class CompiledRecipe {

        private final RecipeDefinition definition;
        private final List<List<IngredientChoice>> candidates;
        private final List<IngredientChoice> baselineGrid;
        private final Map<PortableResourceDescriptor, Long> baselineOutputs;
        private final List<Map<String, Map<PortableResourceDescriptor, Long>>>
              candidateOutputProfiles;
        private final String structuralSignature;
        private final int maxVariants;
        private final int maxRetained;
        private final int combinationCost;
        @Nullable
        private volatile CachedMaterialization recentMaterialization;
        private final Map<String, QIOCandidateInputGroup> candidateGroupCache =
              new LinkedHashMap<String, QIOCandidateInputGroup>(64, 0.75F, true) {
                  @Override
                  protected boolean removeEldestEntry(
                        Map.Entry<String, QIOCandidateInputGroup> eldest) {
                      return size() > 256;
                  }
              };

        private CompiledRecipe(RecipeDefinition definition,
              List<List<IngredientChoice>> candidates, List<IngredientChoice> baselineGrid,
              Map<PortableResourceDescriptor, Long> baselineOutputs,
              List<Map<String, Map<PortableResourceDescriptor, Long>>>
                    candidateOutputProfiles,
              String structuralSignature, int maxVariants, int maxRetained,
              int combinationCost) {
            this.definition = Objects.requireNonNull(definition, "definition");
            List<List<IngredientChoice>> copiedCandidates = new ArrayList<>(candidates.size());
            for (List<IngredientChoice> slot : candidates) {
                copiedCandidates.add(Collections.unmodifiableList(new ArrayList<>(slot)));
            }
            this.candidates = Collections.unmodifiableList(copiedCandidates);
            this.baselineGrid = Collections.unmodifiableList(new ArrayList<>(baselineGrid));
            this.baselineOutputs = Collections.unmodifiableMap(new LinkedHashMap<>(
                  baselineOutputs));
            List<Map<String, Map<PortableResourceDescriptor, Long>>> copiedProfiles =
                  new ArrayList<>(candidateOutputProfiles.size());
            for (Map<String, Map<PortableResourceDescriptor, Long>> slot :
                  candidateOutputProfiles) {
                Map<String, Map<PortableResourceDescriptor, Long>> copiedSlot =
                      new LinkedHashMap<>();
                slot.forEach((candidateId, outputs) -> copiedSlot.put(candidateId,
                      Collections.unmodifiableMap(new LinkedHashMap<>(outputs))));
                copiedProfiles.add(Collections.unmodifiableMap(copiedSlot));
            }
            this.candidateOutputProfiles = Collections.unmodifiableList(copiedProfiles);
            this.structuralSignature = Objects.requireNonNull(structuralSignature,
                  "structuralSignature");
            this.maxVariants = maxVariants;
            this.maxRetained = maxRetained;
            this.combinationCost = combinationCost;
        }

        private List<QIOWorkbenchRecipePattern> materialize(
              Map<PortableResourceDescriptor, Long> available,
              Set<PortableResourceDescriptor> producible, @Nullable String requestedVariant,
              @Nullable QIOWorkbenchConfiguration configuration,
              BooleanSupplier cancelled) {
            Objects.requireNonNull(available, "available");
            Objects.requireNonNull(producible, "producible");
            Objects.requireNonNull(cancelled, "cancelled");
            checkpoint(cancelled);
            if (requestedVariant != null) {
                List<IngredientChoice> recovered = decodeVariant(requestedVariant);
                QIOWorkbenchRecipePattern pattern = recovered == null ? null :
                      createPattern(recovered, candidates);
                return pattern == null || !requestedVariant.equals(pattern.getVariantId()) ?
                      Collections.emptyList() : Collections.singletonList(pattern);
            }
            List<List<IngredientChoice>> effective = effectiveCandidates(configuration);
            if (effective == null) {
                return Collections.emptyList();
            }
            MaterializationKey key = new MaterializationKey(effective, available, producible);
            CachedMaterialization cached = recentMaterialization;
            if (cached != null && cached.key.equals(key)) {
                checkpoint(cancelled);
                return cached.patterns;
            }
            List<List<IngredientChoice>> prioritized = prioritizeCandidates(effective,
                  available, producible);
            PriorityQueue<ScoredVariant> retained = new PriorityQueue<>(maxRetained,
                  Comparator.comparing(ScoredVariant::score).reversed());
            enumerateVariants(0, emptyGrid(), new int[]{0}, retained, available, producible,
                  prioritized, effective, cancelled);
            List<ScoredVariant> ordered = new ArrayList<>(retained);
            ordered.sort(Comparator.comparing(ScoredVariant::score));
            List<QIOWorkbenchRecipePattern> result = new ArrayList<>(ordered.size());
            for (ScoredVariant scored : ordered) {
                QIOWorkbenchRecipePattern pattern = createPattern(scored.variant, effective);
                if (pattern != null) {
                    result.add(pattern);
                }
            }
            List<QIOWorkbenchRecipePattern> immutable = Collections.unmodifiableList(result);
            recentMaterialization = new CachedMaterialization(key, immutable);
            return immutable;
        }

        private void enumerateVariants(int slot, List<IngredientChoice> grid, int[] attempted,
              PriorityQueue<ScoredVariant> retained,
              Map<PortableResourceDescriptor, Long> available,
              Set<PortableResourceDescriptor> producible,
              List<List<IngredientChoice>> enumerationCandidates,
              List<List<IngredientChoice>> configuredCandidates,
              BooleanSupplier cancelled) {
            if (slot == 0 || slot == 9 && (attempted[0] & 63) == 0) {
                checkpoint(cancelled);
            }
            if (attempted[0] >= maxVariants) {
                return;
            }
            if (slot == 9) {
                attempted[0]++;
                VariantTemplate variant = createVariantTemplate(grid, configuredCandidates);
                if (variant != null) {
                    addBoundedVariant(retained, new ScoredVariant(variant,
                          VariantScore.of(variant.exactInputs, available, producible,
                                variant.candidatePreference, variant.stableId)));
                }
                return;
            }
            for (IngredientChoice candidate : enumerationCandidates.get(slot)) {
                grid.set(slot, candidate);
                enumerateVariants(slot + 1, grid, attempted, retained, available, producible,
                      enumerationCandidates, configuredCandidates, cancelled);
                if (attempted[0] >= maxVariants) {
                    break;
                }
            }
        }

        private List<List<IngredientChoice>> prioritizeCandidates(
              List<List<IngredientChoice>> configured,
              Map<PortableResourceDescriptor, Long> available,
              Set<PortableResourceDescriptor> producible) {
            List<List<IngredientChoice>> prioritized = new ArrayList<>(configured.size());
            for (List<IngredientChoice> slot : configured) {
                if (slot.size() <= 1) {
                    prioritized.add(slot);
                    continue;
                }
                List<IngredientChoice> ordered = new ArrayList<>(slot);
                ordered.sort(Comparator.comparingInt(candidate ->
                      candidateAvailability(candidate, available, producible)));
                prioritized.add(Collections.unmodifiableList(ordered));
            }
            return Collections.unmodifiableList(prioritized);
        }

        private static int candidateAvailability(IngredientChoice candidate,
              Map<PortableResourceDescriptor, Long> available,
              Set<PortableResourceDescriptor> producible) {
            if (candidate.isEmpty()) return 0;
            long stored = available.getOrDefault(candidate.resource(), 0L);
            if (stored >= candidate.amount()) return 0;
            if (producible.contains(candidate.resource())) return 1;
            return stored > 0 ? 2 : 3;
        }

        @Nullable
        private List<List<IngredientChoice>> effectiveCandidates(
              @Nullable QIOWorkbenchConfiguration configuration) {
            if (configuration == null) {
                return candidates;
            }
            List<List<IngredientChoice>> effective = new ArrayList<>(candidates.size());
            for (int slot = 0; slot < candidates.size(); slot++) {
                List<IngredientChoice> defaults = candidates.get(slot);
                if (defaults.size() == 1 && defaults.get(0).isEmpty()) {
                    effective.add(defaults);
                    continue;
                }
                List<String> defaultIds = new ArrayList<>(defaults.size());
                Map<String, IngredientChoice> byId = new LinkedHashMap<>();
                for (IngredientChoice candidate : defaults) {
                    defaultIds.add(candidate.candidateId());
                    byId.put(candidate.candidateId(), candidate);
                }
                List<String> enabled = configuration.orderedEnabledCandidates(
                      definition.getRecipeId().toString(), definition.getSignature(), slot,
                      defaultIds);
                if (enabled.isEmpty()) {
                    return null;
                }
                List<IngredientChoice> ordered = new ArrayList<>(enabled.size());
                for (String candidateId : enabled) {
                    IngredientChoice candidate = byId.get(candidateId);
                    if (candidate != null) ordered.add(candidate);
                }
                if (ordered.isEmpty()) {
                    return null;
                }
                effective.add(Collections.unmodifiableList(ordered));
            }
            return Collections.unmodifiableList(effective);
        }

        @Nullable
        private QIOWorkbenchRecipePattern createPattern(List<IngredientChoice> grid,
              List<List<IngredientChoice>> enabledCandidates) {
            Map<PortableResourceDescriptor, Long> outputs = outputsFor(grid);
            if (outputs == null || outputs.getOrDefault(definition.getOutput(), 0L) !=
                  definition.getOutputAmount()) {
                return null;
            }
            return createPattern(grid, variantId(grid), exactInputs(grid), outputs,
                  enabledCandidates);
        }

        @Nullable
        private QIOWorkbenchRecipePattern createPattern(VariantTemplate variant,
              List<List<IngredientChoice>> enabledCandidates) {
            return createPattern(variant.grid, variant.variantId, variant.exactInputs,
                  variant.outputs, enabledCandidates);
        }

        @Nullable
        private QIOWorkbenchRecipePattern createPattern(List<IngredientChoice> grid,
              String variantId, Map<PortableResourceDescriptor, Long> exactInputs,
              Map<PortableResourceDescriptor, Long> outputs,
              List<List<IngredientChoice>> enabledCandidates) {
            try {
                QIOPlanningRoute.Builder routeBuilder = QIOPlanningRoute.builder(
                      ProviderKind.WORKBENCH, PROVIDER_ID,
                      definition.getRecipeId().toString(),
                      definition.getRecipeId().toString()).variantId(variantId);
                exactInputs.forEach(routeBuilder::input);
                outputs.forEach(routeBuilder::output);
                addCandidateGroups(routeBuilder, grid, enabledCandidates);
                return new QIOWorkbenchRecipePattern(routeBuilder.build(),
                      definition.getRecipeId(), matchingGrid(grid), virtualFluidSlots(grid));
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        @Nullable
        private VariantTemplate createVariantTemplate(List<IngredientChoice> grid,
              List<List<IngredientChoice>> configuredCandidates) {
            Map<PortableResourceDescriptor, Long> outputs = outputsFor(grid);
            if (outputs == null || outputs.getOrDefault(definition.getOutput(), 0L) !=
                  definition.getOutputAmount()) {
                return null;
            }
            String variantId = variantId(grid);
            String stableId = PROVIDER_ID + "/" + definition.getRecipeId() + "/" +
                  definition.getRecipeId() + "/" + variantId;
            return new VariantTemplate(grid, variantId, stableId, exactInputs(grid), outputs,
                  candidatePreferenceKey(grid, configuredCandidates));
        }

        private static Map<PortableResourceDescriptor, Long> exactInputs(
              List<IngredientChoice> grid) {
            Map<PortableResourceDescriptor, Long> inputs = new LinkedHashMap<>();
            for (IngredientChoice input : grid) {
                if (!input.isEmpty()) {
                    inputs.merge(input.resource(), input.amount(), Math::addExact);
                }
            }
            return Collections.unmodifiableMap(inputs);
        }

        @Nullable
        private Map<PortableResourceDescriptor, Long> outputsFor(
              List<IngredientChoice> grid) {
            Map<PortableResourceDescriptor, Long> outputs = new LinkedHashMap<>(
                  baselineOutputs);
            for (int slot = 0; slot < grid.size(); slot++) {
                IngredientChoice baseline = baselineGrid.get(slot);
                IngredientChoice selected = grid.get(slot);
                if (baseline.candidateId().equals(selected.candidateId())) {
                    continue;
                }
                Map<String, Map<PortableResourceDescriptor, Long>> profiles =
                      candidateOutputProfiles.get(slot);
                Map<PortableResourceDescriptor, Long> baselineProfile = profiles.get(
                      baseline.candidateId());
                Map<PortableResourceDescriptor, Long> selectedProfile = profiles.get(
                      selected.candidateId());
                if (baselineProfile == null || selectedProfile == null ||
                    !applyOutputDelta(outputs, baselineProfile, selectedProfile)) {
                    return null;
                }
            }
            return Collections.unmodifiableMap(outputs);
        }

        private static boolean applyOutputDelta(
              Map<PortableResourceDescriptor, Long> outputs,
              Map<PortableResourceDescriptor, Long> baseline,
              Map<PortableResourceDescriptor, Long> selected) {
            Set<PortableResourceDescriptor> resources = new LinkedHashSet<>();
            resources.addAll(baseline.keySet());
            resources.addAll(selected.keySet());
            for (PortableResourceDescriptor resource : resources) {
                long change;
                long next;
                try {
                    change = Math.subtractExact(selected.getOrDefault(resource, 0L),
                          baseline.getOrDefault(resource, 0L));
                    next = Math.addExact(outputs.getOrDefault(resource, 0L), change);
                } catch (ArithmeticException ignored) {
                    return false;
                }
                if (next < 0) return false;
                if (next == 0) outputs.remove(resource);
                else outputs.put(resource, next);
            }
            return true;
        }

        private void addCandidateGroups(QIOPlanningRoute.Builder routeBuilder,
              List<IngredientChoice> grid,
              List<List<IngredientChoice>> enabledCandidates) {
            for (int slot = 0; slot < enabledCandidates.size(); slot++) {
                List<IngredientChoice> choices = enabledCandidates.get(slot);
                if (choices.size() <= 1 || choices.get(0).isEmpty()) continue;
                List<IngredientChoice> compatible = compatibleChoices(grid.get(slot), choices,
                      slot);
                if (compatible.size() <= 1) continue;
                List<QIOCandidateOption> options = new ArrayList<>(compatible.size());
                for (IngredientChoice choice : compatible) {
                    options.add(new QIOCandidateOption(choice.candidateId(), choice.resource(),
                          choice.amount(), choice.isVirtualFluid()));
                }
                Map<PortableResourceDescriptor, Long> plannedInputs = new LinkedHashMap<>();
                IngredientChoice selected = grid.get(slot);
                plannedInputs.put(selected.resource(), selected.amount());
                routeBuilder.candidateInput(cachedCandidateGroup(slot, compatible, selected,
                      options, plannedInputs));
            }
        }

        @Nonnull
        private QIOCandidateInputGroup cachedCandidateGroup(int slot,
              List<IngredientChoice> compatible, IngredientChoice selected,
              List<QIOCandidateOption> options,
              Map<PortableResourceDescriptor, Long> plannedInputs) {
            StringBuilder key = new StringBuilder(128).append(slot).append('|')
                  .append(selected.candidateId()).append('|');
            for (IngredientChoice choice : compatible) {
                key.append(choice.candidateId()).append('@').append(choice.resource())
                      .append('@').append(choice.amount()).append('@')
                      .append(choice.isVirtualFluid()).append(';');
            }
            String cacheKey = key.toString();
            synchronized (candidateGroupCache) {
                QIOCandidateInputGroup cached = candidateGroupCache.get(cacheKey);
                if (cached != null) {
                    return cached;
                }
                QIOCandidateInputGroup created = new QIOCandidateInputGroup(
                      Collections.singletonList(slot), options, plannedInputs);
                candidateGroupCache.put(cacheKey, created);
                return created;
            }
        }

        private List<IngredientChoice> compatibleChoices(IngredientChoice selected,
              List<IngredientChoice> choices, int slot) {
            Map<String, Map<PortableResourceDescriptor, Long>> profiles =
                  candidateOutputProfiles.get(slot);
            Map<PortableResourceDescriptor, Long> selectedOutputs = profiles.get(
                  selected.candidateId());
            if (selectedOutputs == null) return Collections.emptyList();
            List<IngredientChoice> compatible = new ArrayList<>();
            for (IngredientChoice choice : choices) {
                if (selectedOutputs.equals(profiles.get(choice.candidateId()))) {
                    compatible.add(choice);
                }
            }
            return compatible;
        }

        @Nullable
        private List<IngredientChoice> decodeVariant(String encodedVariant) {
            String[] parts = encodedVariant.split("\\.", -1);
            if (parts.length != 10 || !"i1".equals(parts[0])) return null;
            List<IngredientChoice> grid = emptyGrid();
            try {
                for (int slot = 0; slot < 9; slot++) {
                    int candidateIndex = Integer.parseInt(parts[slot + 1], 36);
                    List<IngredientChoice> slotCandidates = candidates.get(slot);
                    if (candidateIndex < 0 || candidateIndex >= slotCandidates.size()) {
                        return null;
                    }
                    grid.set(slot, slotCandidates.get(candidateIndex));
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
            return grid;
        }

        private String variantId(List<IngredientChoice> grid) {
            StringBuilder encoded = new StringBuilder("i1");
            for (int slot = 0; slot < grid.size(); slot++) {
                int candidateIndex = candidates.get(slot).indexOf(grid.get(slot));
                if (candidateIndex < 0) {
                    throw new IllegalArgumentException(
                          "Workbench grid contains an unknown ingredient candidate");
                }
                encoded.append('.').append(Integer.toString(candidateIndex, 36));
            }
            return encoded.toString();
        }

        private void addBoundedVariant(PriorityQueue<ScoredVariant> retained,
              ScoredVariant candidate) {
            if (retained.size() < maxRetained) {
                retained.add(candidate);
            } else if (candidate.score.compareTo(retained.element().score) < 0) {
                retained.remove();
                retained.add(candidate);
            }
        }

        private boolean matches(QIOWorkbenchConfiguration.EncodedPattern pattern) {
            if (!definition.getRecipeId().equals(pattern.getRecipeId()) ||
                !definition.getSignature().equals(pattern.getRecipeSignature()) ||
                !definition.getOutput().equals(pattern.getOutput()) ||
                definition.getOutputAmount() != pattern.getOutputAmount()) {
                return false;
            }
            List<ItemStack> grid = pattern.getGrid();
            for (int slot = 0; slot < grid.size(); slot++) {
                ItemStack encoded = grid.get(slot);
                boolean matched = false;
                for (IngredientChoice candidate : candidates.get(slot)) {
                    if (ItemStack.areItemStacksEqual(encoded, candidate.matchingStack())) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return false;
            }
            return true;
        }

        private QIOWorkbenchConfiguration.EncodedPattern newPattern() {
            return new QIOWorkbenchConfiguration.EncodedPattern(UUID.randomUUID(),
                  definition.getRecipeId(), definition.getSignature(), definition.getOutput(),
                  definition.getOutputAmount(), matchingGrid(baselineGrid));
        }

        private static void checkpoint(BooleanSupplier cancelled) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException(
                      "QIO workbench materialization cancelled");
            }
        }

        private static final class MaterializationKey {

            private final List<List<IngredientChoice>> candidates;
            private final Map<PortableResourceDescriptor, Long> available;
            private final Set<PortableResourceDescriptor> producible;

            private MaterializationKey(List<List<IngredientChoice>> candidates,
                  Map<PortableResourceDescriptor, Long> available,
                  Set<PortableResourceDescriptor> producible) {
                this.candidates = candidates;
                Map<PortableResourceDescriptor, Long> relevantAvailable = new LinkedHashMap<>();
                Set<PortableResourceDescriptor> relevantProducible = new LinkedHashSet<>();
                for (List<IngredientChoice> slot : candidates) {
                    for (IngredientChoice candidate : slot) {
                        if (!candidate.isEmpty()) {
                            PortableResourceDescriptor resource = candidate.resource();
                            relevantAvailable.put(resource, available.getOrDefault(resource, 0L));
                            if (producible.contains(resource)) {
                                relevantProducible.add(resource);
                            }
                        }
                    }
                }
                this.available = Collections.unmodifiableMap(relevantAvailable);
                this.producible = Collections.unmodifiableSet(relevantProducible);
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof MaterializationKey key &&
                      candidates.equals(key.candidates) && available.equals(key.available) &&
                      producible.equals(key.producible);
            }

            @Override
            public int hashCode() {
                return Objects.hash(candidates, available, producible);
            }
        }

        private static final class CachedMaterialization {

            private final MaterializationKey key;
            private final List<QIOWorkbenchRecipePattern> patterns;

            private CachedMaterialization(MaterializationKey key,
                  List<QIOWorkbenchRecipePattern> patterns) {
                this.key = key;
                this.patterns = patterns;
            }
        }

        private static final class VariantTemplate {

            private final List<IngredientChoice> grid;
            private final String variantId;
            private final String stableId;
            private final Map<PortableResourceDescriptor, Long> exactInputs;
            private final Map<PortableResourceDescriptor, Long> outputs;
            private final String candidatePreference;

            private VariantTemplate(List<IngredientChoice> grid, String variantId,
                  String stableId, Map<PortableResourceDescriptor, Long> exactInputs,
                  Map<PortableResourceDescriptor, Long> outputs,
                  String candidatePreference) {
                this.grid = Collections.unmodifiableList(new ArrayList<>(grid));
                this.variantId = variantId;
                this.stableId = stableId;
                this.exactInputs = exactInputs;
                this.outputs = outputs;
                this.candidatePreference = candidatePreference;
            }
        }
    }

    private static final class ScoredPattern {

        private final QIOWorkbenchRecipePattern pattern;
        private final VariantScore score;

        private ScoredPattern(QIOWorkbenchRecipePattern pattern, VariantScore score) {
            this.pattern = pattern;
            this.score = score;
        }

        private VariantScore score() {
            return score;
        }
    }

    private static final class ScoredVariant {

        private final CompiledRecipe.VariantTemplate variant;
        private final VariantScore score;

        private ScoredVariant(CompiledRecipe.VariantTemplate variant, VariantScore score) {
            this.variant = variant;
            this.score = score;
        }

        private VariantScore score() {
            return score;
        }
    }

    private static final class VariantScore implements Comparable<VariantScore> {

        private final long unavailableKinds;
        private final long producibleKinds;
        private final long missingAmount;
        private final String candidatePreference;
        private final String stableId;

        private VariantScore(long unavailableKinds, long producibleKinds, long missingAmount,
              String candidatePreference, String stableId) {
            this.unavailableKinds = unavailableKinds;
            this.producibleKinds = producibleKinds;
            this.missingAmount = missingAmount;
            this.candidatePreference = candidatePreference;
            this.stableId = stableId;
        }

        private static VariantScore of(QIOPlanningRoute route,
              Map<PortableResourceDescriptor, Long> available,
              Set<PortableResourceDescriptor> producible, String candidatePreference) {
            return of(route.getExactInputs(), available, producible, candidatePreference,
                  route.getStableId());
        }

        private static VariantScore of(Map<PortableResourceDescriptor, Long> exactInputs,
              Map<PortableResourceDescriptor, Long> available,
              Set<PortableResourceDescriptor> producible, String candidatePreference,
              String stableId) {
            long unavailableKinds = 0;
            long producibleKinds = 0;
            long missingAmount = 0;
            for (Map.Entry<PortableResourceDescriptor, Long> input :
                  exactInputs.entrySet()) {
                long missing = Math.max(0, input.getValue() -
                      available.getOrDefault(input.getKey(), 0L));
                if (missing > 0) {
                    missingAmount = saturatedAdd(missingAmount, missing);
                    if (producible.contains(input.getKey())) {
                        producibleKinds++;
                    } else {
                        unavailableKinds++;
                    }
                }
            }
            return new VariantScore(unavailableKinds, producibleKinds, missingAmount,
                  candidatePreference, stableId);
        }

        @Override
        public int compareTo(VariantScore other) {
            int compared = Long.compare(unavailableKinds, other.unavailableKinds);
            if (compared == 0) compared = Long.compare(producibleKinds, other.producibleKinds);
            if (compared == 0) compared = Long.compare(missingAmount, other.missingAmount);
            if (compared == 0) compared = candidatePreference.compareTo(
                  other.candidatePreference);
            return compared == 0 ? stableId.compareTo(other.stableId) : compared;
        }
    }

    private static final class ParsedRouteId {

        private final ResourceLocation recipeId;
        private final String variantId;

        private ParsedRouteId(ResourceLocation recipeId, String variantId) {
            this.recipeId = recipeId;
            this.variantId = variantId;
        }
    }

    /** Fully parsed, immutable workbench directory shared by all frequencies for one server. */
    static final class RecipeOutputIndex {

        private static final BuildListener NOOP_LISTENER = new BuildListener() {
            @Override public void scanComplete(int recipeCount) {
            }
            @Override public void cacheStarted(int recipeCount) {
            }
        };

        private final Map<PortableResourceDescriptor, List<CachedRecipe>> recipesByOutput;
        private final Map<ResourceLocation, CachedRecipe> recipesById;
        private final int recipeCount;

        private RecipeOutputIndex(
              Map<PortableResourceDescriptor, List<CachedRecipe>> recipesByOutput,
              Map<ResourceLocation, CachedRecipe> recipesById, int recipeCount) {
            this.recipesByOutput = recipesByOutput;
            this.recipesById = recipesById;
            this.recipeCount = recipeCount;
        }

        @Nonnull
        static RecipeOutputIndex build(@Nullable World world,
              @Nonnull Collection<? extends IRecipe> recipes) {
            return build(world, recipes, NOOP_LISTENER);
        }

        @Nonnull
        static RecipeOutputIndex build(@Nullable World world,
              @Nonnull Collection<? extends IRecipe> recipes,
              @Nonnull BuildListener listener) {
            Objects.requireNonNull(recipes, "recipes");
            Objects.requireNonNull(listener, "listener");
            Builder builder = new Builder(world, ignored -> 0,
                  DEFAULT_MAX_CANDIDATES_PER_INGREDIENT,
                  DEFAULT_MAX_VARIANTS_PER_RECIPE,
                  DEFAULT_MAX_MATERIALIZED_VARIANTS);
            List<IRecipe> orderedRecipes = new ArrayList<>(recipes);
            orderedRecipes.sort(Comparator.comparing(recipe -> recipe == null ||
                  recipe.getRegistryName() == null ? "" :
                  recipe.getRegistryName().toString()));
            for (IRecipe recipe : orderedRecipes) {
                try {
                    builder.add(recipe);
                } catch (RuntimeException ignored) {
                    // A broken third-party recipe cannot prevent the global directory loading.
                }
            }
            listener.scanComplete(builder.recipes.size());
            listener.cacheStarted(builder.recipes.size());
            Map<PortableResourceDescriptor, List<CachedRecipe>> grouped =
                  new LinkedHashMap<>();
            Map<ResourceLocation, CachedRecipe> byId = new LinkedHashMap<>();
            int recipeCount = 0;
            for (LogicalRecipe logical : builder.recipes.values()) {
                CompiledRecipe compiled;
                try {
                    compiled = logical.compile();
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (compiled == null) continue;
                CachedRecipe cached = new CachedRecipe(compiled);
                grouped.computeIfAbsent(compiled.definition.getOutput(),
                      ignored -> new ArrayList<>()).add(cached);
                byId.put(compiled.definition.getRecipeId(), cached);
                recipeCount++;
            }
            List<PortableResourceDescriptor> outputs = new ArrayList<>(grouped.keySet());
            Collections.sort(outputs);
            Map<PortableResourceDescriptor, List<CachedRecipe>> ordered =
                  new LinkedHashMap<>();
            for (PortableResourceDescriptor output : outputs) {
                List<CachedRecipe> outputRecipes = grouped.get(output);
                ordered.put(output, Collections.unmodifiableList(
                      new ArrayList<>(outputRecipes)));
            }
            return new RecipeOutputIndex(Collections.unmodifiableMap(ordered),
                  Collections.unmodifiableMap(new LinkedHashMap<>(byId)), recipeCount);
        }

        @Nonnull
        private List<CachedRecipe> recipesFor(
              @Nonnull PortableResourceDescriptor output) {
            List<CachedRecipe> recipes = recipesByOutput.get(output);
            return recipes == null ? Collections.emptyList() : recipes;
        }

        @Nonnull
        Snapshot snapshotFor(@Nonnull QIOWorkbenchConfiguration configuration) {
            Objects.requireNonNull(configuration, "configuration");
            Map<ResourceLocation, CompiledRecipe> selected = new LinkedHashMap<>();
            List<String> diagnostics = new ArrayList<>();
            for (QIOWorkbenchConfiguration.EncodedPattern pattern :
                  configuration.getEncodedPatterns()) {
                CachedRecipe cached = recipesById.get(pattern.getRecipeId());
                if (cached == null) {
                    diagnostics.add("Encoded workbench recipe is no longer registered: " +
                          pattern.getRecipeId());
                } else if (!cached.compiled.matches(pattern)) {
                    diagnostics.add("Encoded workbench recipe changed: " +
                          pattern.getRecipeId());
                } else {
                    selected.put(pattern.getRecipeId(), cached.compiled);
                }
            }
            return new Snapshot(selected, diagnostics, DEFAULT_PATTERN_CACHE_SIZE);
        }

        int getRecipeCount() {
            return recipeCount;
        }

        interface BuildListener {
            void scanComplete(int recipeCount);
            void cacheStarted(int recipeCount);
        }
    }

    /** One prevalidated representative route with no Forge recipe or world reference. */
    private static final class CachedRecipe {

        private final CompiledRecipe compiled;
        private final RecipeDefinition definition;
        private final int combinationCost;

        private CachedRecipe(CompiledRecipe compiled) {
            this.compiled = Objects.requireNonNull(compiled, "compiled");
            definition = compiled.definition;
            combinationCost = compiled.combinationCost;
        }

        @Nonnull
        private QIOWorkbenchConfiguration.EncodedPattern newPattern() {
            return compiled.newPattern();
        }
    }

    /** Incremental, worker-safe lookup of root-reachable recipes in the global directory. */
    public static final class ClosureCapture {

        private final QIOWorkbenchConfiguration configuration;
        private final QIOWorkbenchClosureMode mode;
        private final boolean skipCyclicRecipes;
        private final List<QIOWorkbenchConfiguration.EncodedPattern> roots = new ArrayList<>();
        private final Set<String> existingRecipeIds = new LinkedHashSet<>();
        private final RecipeOutputIndex recipeIndex;
        private final Map<PortableResourceDescriptor, List<ClosureCandidate>> index =
              new LinkedHashMap<>();
        private final Deque<PortableResourceDescriptor> pendingOutputs = new ArrayDeque<>();
        private final Set<PortableResourceDescriptor> queuedOutputs = new LinkedHashSet<>();
        private final Set<ResourceLocation> processedRecipeIds = new LinkedHashSet<>();
        @Nullable private PortableResourceDescriptor currentOutput;
        private List<CachedRecipe> currentRecipes = Collections.emptyList();
        private final List<String> currentDefaultIds = new ArrayList<>();
        private final Map<String, ClosureCandidate> currentCandidates = new LinkedHashMap<>();
        private int currentRecipeIndex;
        private int processedRecipeCount;
        private int captured;
        private boolean truncated;
        private boolean complete;

        private ClosureCapture(
              Collection<QIOWorkbenchConfiguration.EncodedPattern> roots,
              QIOWorkbenchConfiguration configuration, QIOWorkbenchClosureMode mode,
              boolean skipCyclicRecipes, RecipeOutputIndex recipeIndex,
              int combinationBudget) {
            this.configuration = configuration.copy();
            this.mode = mode;
            this.skipCyclicRecipes = skipCyclicRecipes;
            for (QIOWorkbenchConfiguration.EncodedPattern root : roots) {
                QIOWorkbenchConfiguration.EncodedPattern copied = copyPattern(root);
                this.roots.add(copied);
                enqueueDependencies(copied);
            }
            configuration.getEncodedPatterns().forEach(pattern ->
                  existingRecipeIds.add(pattern.getRecipeId().toString()));
            this.recipeIndex = recipeIndex;
        }

        /**
         * Processes at most {@code maximumSteps} recipes/patterns and stops at the shared
         * wall-clock budget. Returns true once {@link #finish()} is available.
         */
        public boolean process(int maximumSteps, long maximumNanos) {
            if (maximumSteps <= 0 || maximumNanos <= 0) {
                throw new IllegalArgumentException("Closure capture slice must be positive");
            }
            if (complete) return true;
            long deadline = maximumNanos == Long.MAX_VALUE ? Long.MAX_VALUE :
                  saturatedAdd(System.nanoTime(), maximumNanos);
            int steps = 0;
            while (steps < maximumSteps && beforeDeadline(deadline)) {
                if (currentOutput == null) {
                    if (pendingOutputs.isEmpty() || truncated) {
                        complete = true;
                        break;
                    }
                    beginOutput(pendingOutputs.removeFirst());
                    if (currentRecipes.isEmpty()) {
                        finishOutput();
                        continue;
                    }
                }
                if (currentRecipeIndex < currentRecipes.size()) {
                    captureRecipe(currentRecipes.get(currentRecipeIndex++));
                    steps++;
                }
                if (currentRecipeIndex >= currentRecipes.size()) finishOutput();
            }
            return complete;
        }

        @Nonnull
        public ClosureInput finish() {
            if (!complete) {
                throw new IllegalStateException("Workbench closure capture is incomplete");
            }
            return new ClosureInput(mode, skipCyclicRecipes, roots, index,
                  existingRecipeIds, truncated);
        }

        private void beginOutput(PortableResourceDescriptor output) {
            currentOutput = output;
            List<CachedRecipe> defaults = recipeIndex.recipesFor(output);
            Map<String, CachedRecipe> byId = new LinkedHashMap<>();
            List<String> defaultIds = new ArrayList<>(defaults.size());
            for (CachedRecipe recipe : defaults) {
                String recipeId = recipe.definition.getRecipeId().toString();
                defaultIds.add(recipeId);
                byId.put(recipeId, recipe);
            }
            List<String> order = configuration.orderedRecipes(productKey(output), defaultIds);
            List<CachedRecipe> ordered = new ArrayList<>(defaults.size());
            for (String recipeId : order) {
                CachedRecipe recipe = byId.get(recipeId);
                if (recipe != null) ordered.add(recipe);
            }
            currentRecipes = ordered;
            currentDefaultIds.clear();
            currentCandidates.clear();
            currentRecipeIndex = 0;
        }

        private void captureRecipe(CachedRecipe recipe) {
            RecipeDefinition definition = recipe.definition;
            ResourceLocation registryName = definition.getRecipeId();
            if (!processedRecipeIds.add(registryName)) return;
            processedRecipeCount++;
            if (processedRecipeCount > MAX_BATCH_MATCHED_RECIPES ||
                captured >= MAX_CLOSURE_PATTERNS) {
                truncated = true;
                currentRecipeIndex = currentRecipes.size();
                return;
            }
            if (!definition.getOutput().equals(currentOutput)) return;
            String recipeId = registryName.toString();
            currentDefaultIds.add(recipeId);
            boolean enabled = configuration.isRecipeEnabled(recipeId,
                  definition.getSignature());
            if (mode == QIOWorkbenchClosureMode.PREFERRED && !enabled) return;
            QIOWorkbenchConfiguration.EncodedPattern existing =
                  configuration.getEncodedPattern(recipeId);
            QIOWorkbenchConfiguration.EncodedPattern pattern = null;
            if (existing != null && existing.getOutput().equals(currentOutput) &&
                existing.getRecipeSignature().equals(definition.getSignature())) {
                pattern = copyPattern(existing);
            }
            if (pattern == null) {
                pattern = recipe.newPattern();
            }
            if (pattern != null) {
                currentCandidates.put(recipeId, new ClosureCandidate(pattern, enabled));
                captured++;
                enqueueDependencies(pattern);
                if (mode == QIOWorkbenchClosureMode.PREFERRED && !skipCyclicRecipes) {
                    currentRecipeIndex = currentRecipes.size();
                }
            }
        }

        private void finishOutput() {
            if (currentOutput != null && !currentCandidates.isEmpty()) {
                List<String> order = configuration.orderedRecipes(productKey(currentOutput),
                      currentDefaultIds);
                List<ClosureCandidate> ordered = new ArrayList<>(currentCandidates.size());
                for (String recipeId : order) {
                    ClosureCandidate candidate = currentCandidates.get(recipeId);
                    if (candidate != null) ordered.add(candidate);
                }
                if (!ordered.isEmpty()) {
                    index.put(currentOutput, Collections.unmodifiableList(ordered));
                }
            }
            currentOutput = null;
            currentRecipes = Collections.emptyList();
            currentDefaultIds.clear();
            currentCandidates.clear();
            currentRecipeIndex = 0;
        }

        private void enqueueDependencies(
              QIOWorkbenchConfiguration.EncodedPattern pattern) {
            Set<PortableResourceDescriptor> dependencies = new LinkedHashSet<>();
            for (ItemStack stack : pattern.getGrid()) {
                if (!stack.isEmpty()) {
                    dependencies.add(PortableResourceDescriptor.item(stack));
                }
            }
            for (PortableResourceDescriptor dependency : dependencies) {
                if (queuedOutputs.contains(dependency)) continue;
                if (queuedOutputs.size() >= MAX_CLOSURE_OUTPUTS) {
                    truncated = true;
                    return;
                }
                queuedOutputs.add(dependency);
                pendingOutputs.addLast(dependency);
            }
        }

        int getProcessedRecipeCount() {
            return processedRecipeCount;
        }

        int getQueuedOutputCount() {
            return queuedOutputs.size();
        }

        private static boolean beforeDeadline(long deadline) {
            return deadline == Long.MAX_VALUE || System.nanoTime() - deadline < 0;
        }

        private static long saturatedAdd(long left, long right) {
            return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
        }
    }

    /** Immutable recipe graph prepared on the server thread. */
    public static final class ClosureInput {

        private final QIOWorkbenchClosureMode mode;
        private final boolean skipCyclicRecipes;
        private final List<QIOWorkbenchConfiguration.EncodedPattern> roots;
        private final Map<PortableResourceDescriptor, List<ClosureCandidate>> recipesByOutput;
        private final Set<String> existingRecipeIds;
        private final boolean initiallyTruncated;

        private ClosureInput(QIOWorkbenchClosureMode mode, boolean skipCyclicRecipes,
              List<QIOWorkbenchConfiguration.EncodedPattern> roots,
              Map<PortableResourceDescriptor, List<ClosureCandidate>> recipesByOutput,
              Set<String> existingRecipeIds, boolean initiallyTruncated) {
            this.mode = mode;
            this.skipCyclicRecipes = skipCyclicRecipes;
            this.roots = Collections.unmodifiableList(new ArrayList<>(roots));
            this.recipesByOutput = Collections.unmodifiableMap(
                  new LinkedHashMap<>(recipesByOutput));
            this.existingRecipeIds = Collections.unmodifiableSet(
                  new LinkedHashSet<>(existingRecipeIds));
            this.initiallyTruncated = initiallyTruncated;
        }
    }

    /** Pure-data result retained only on the server until confirmation. */
    public static final class ClosureResult {

        private final List<QIOWorkbenchConfiguration.EncodedPattern> roots;
        private final List<QIOWorkbenchConfiguration.EncodedPattern> patterns;
        private final int newProductCount;
        private final int newRecipeCount;
        private final int existingRecipeCount;
        private final int leafMaterialCount;
        private final int cycleCount;
        private final int skippedCyclicRecipeCount;
        private final List<String> cyclePaths;
        private final boolean truncated;

        private ClosureResult(List<QIOWorkbenchConfiguration.EncodedPattern> roots,
              Collection<QIOWorkbenchConfiguration.EncodedPattern> patterns,
              int newProductCount, int newRecipeCount, int existingRecipeCount,
              int leafMaterialCount, int cycleCount, int skippedCyclicRecipeCount,
              List<String> cyclePaths, boolean truncated) {
            this.roots = Collections.unmodifiableList(new ArrayList<>(roots));
            this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
            this.newProductCount = newProductCount;
            this.newRecipeCount = newRecipeCount;
            this.existingRecipeCount = existingRecipeCount;
            this.leafMaterialCount = leafMaterialCount;
            this.cycleCount = cycleCount;
            this.skippedCyclicRecipeCount = skippedCyclicRecipeCount;
            this.cyclePaths = Collections.unmodifiableList(new ArrayList<>(cyclePaths));
            this.truncated = truncated;
        }

        @Nonnull public List<QIOWorkbenchConfiguration.EncodedPattern> getRoots() {
            return roots;
        }
        @Nonnull public List<QIOWorkbenchConfiguration.EncodedPattern> getPatterns() {
            return patterns;
        }
        public int getNewProductCount() { return newProductCount; }
        public int getNewRecipeCount() { return newRecipeCount; }
        public int getExistingRecipeCount() { return existingRecipeCount; }
        public int getLeafMaterialCount() { return leafMaterialCount; }
        public int getCycleCount() { return cycleCount; }
        public int getSkippedCyclicRecipeCount() { return skippedCyclicRecipeCount; }
        @Nonnull public List<String> getCyclePaths() { return cyclePaths; }
        public boolean isTruncated() { return truncated; }
    }

    private static final class ClosureCandidate {

        private final QIOWorkbenchConfiguration.EncodedPattern pattern;
        private final boolean enabled;

        private ClosureCandidate(QIOWorkbenchConfiguration.EncodedPattern pattern,
              boolean enabled) {
            this.pattern = pattern;
            this.enabled = enabled;
        }
    }

    private static final class ClosureWalker {

        private final ClosureInput input;
        private final BooleanSupplier cancelled;
        private final Map<String, QIOWorkbenchConfiguration.EncodedPattern> discovered =
              new LinkedHashMap<>();
        private final Set<PortableResourceDescriptor> visitedOutputs = new LinkedHashSet<>();
        private final Set<PortableResourceDescriptor> newProducts = new LinkedHashSet<>();
        private final Set<PortableResourceDescriptor> leaves = new LinkedHashSet<>();
        private final Set<String> existingRecipes = new LinkedHashSet<>();
        private final List<String> cyclePaths = new ArrayList<>();
        private final long startedNanos = System.nanoTime();
        private int cycleCount;
        private int skippedCyclicRecipeCount;
        private boolean truncated;
        private boolean stopped;

        private ClosureWalker(ClosureInput input, BooleanSupplier cancelled) {
            this.input = input;
            this.cancelled = cancelled;
            truncated = input.initiallyTruncated;
        }

        private ClosureResult resolve() {
            for (QIOWorkbenchConfiguration.EncodedPattern root : input.roots) {
                if (checkpoint()) break;
                addPattern(root);
                visitedOutputs.add(root.getOutput());
                List<PortableResourceDescriptor> active = new ArrayList<>();
                active.add(root.getOutput());
                expandPattern(root, 0, active);
            }
            int newRecipes = 0;
            for (String recipeId : discovered.keySet()) {
                if (!input.existingRecipeIds.contains(recipeId)) newRecipes++;
            }
            return new ClosureResult(input.roots, discovered.values(), newProducts.size(),
                  newRecipes, existingRecipes.size(), leaves.size(), cycleCount,
                  skippedCyclicRecipeCount, cyclePaths, truncated);
        }

        private void expandPattern(QIOWorkbenchConfiguration.EncodedPattern pattern,
              int depth, List<PortableResourceDescriptor> active) {
            Set<PortableResourceDescriptor> inputs = new LinkedHashSet<>();
            for (ItemStack stack : pattern.getGrid()) {
                if (!stack.isEmpty()) inputs.add(PortableResourceDescriptor.item(stack));
            }
            for (PortableResourceDescriptor dependency : inputs) {
                if (checkpoint()) return;
                int cycleAt = active.indexOf(dependency);
                if (cycleAt >= 0) {
                    recordCycle(active, cycleAt, dependency);
                    continue;
                }
                expandOutput(dependency, depth + 1, active);
            }
        }

        private void expandOutput(PortableResourceDescriptor output, int depth,
              List<PortableResourceDescriptor> active) {
            if (checkpoint() || visitedOutputs.contains(output)) return;
            if (depth > MAX_CLOSURE_DEPTH || visitedOutputs.size() >= MAX_CLOSURE_OUTPUTS) {
                truncated = true;
                return;
            }
            visitedOutputs.add(output);
            List<ClosureCandidate> candidates = input.recipesByOutput.get(output);
            if (candidates == null || candidates.isEmpty()) {
                leaves.add(output);
                return;
            }
            active.add(output);
            boolean selected = false;
            for (ClosureCandidate candidate : candidates) {
                if (input.mode == QIOWorkbenchClosureMode.PREFERRED && !candidate.enabled) {
                    continue;
                }
                if (input.skipCyclicRecipes && rejectsCandidate(candidate.pattern, active)) {
                    skippedCyclicRecipeCount++;
                    continue;
                }
                selected = true;
                if (!addPattern(candidate.pattern)) break;
                expandPattern(candidate.pattern, depth, active);
                if (input.mode == QIOWorkbenchClosureMode.PREFERRED || checkpoint()) break;
            }
            active.remove(active.size() - 1);
            if (!selected) leaves.add(output);
        }

        private boolean rejectsCandidate(
              QIOWorkbenchConfiguration.EncodedPattern pattern,
              List<PortableResourceDescriptor> active) {
            Set<PortableResourceDescriptor> inputs = new LinkedHashSet<>();
            for (ItemStack stack : pattern.getGrid()) {
                if (!stack.isEmpty()) inputs.add(PortableResourceDescriptor.item(stack));
            }
            for (PortableResourceDescriptor dependency : inputs) {
                int cycleAt = active.indexOf(dependency);
                if (cycleAt >= 0) {
                    recordCycle(active, cycleAt, dependency);
                    return true;
                }
            }
            return false;
        }

        private boolean addPattern(QIOWorkbenchConfiguration.EncodedPattern pattern) {
            String recipeId = pattern.getRecipeId().toString();
            if (discovered.containsKey(recipeId)) return true;
            if (discovered.size() >= MAX_CLOSURE_PATTERNS) {
                truncated = true;
                return false;
            }
            discovered.put(recipeId, pattern);
            if (input.existingRecipeIds.contains(recipeId)) {
                existingRecipes.add(recipeId);
            } else {
                newProducts.add(pattern.getOutput());
            }
            return true;
        }

        private void recordCycle(List<PortableResourceDescriptor> active, int cycleAt,
              PortableResourceDescriptor dependency) {
            cycleCount++;
            if (cyclePaths.size() >= MAX_CLOSURE_CYCLE_SAMPLES) return;
            StringBuilder path = new StringBuilder();
            for (int index = cycleAt; index < active.size(); index++) {
                if (path.length() > 0) path.append(" -> ");
                path.append(active.get(index));
            }
            path.append(" -> ").append(dependency);
            cyclePaths.add(path.toString());
        }

        private boolean checkpoint() {
            if (stopped) return true;
            if (System.nanoTime() - startedNanos >= MAX_CLOSURE_WALL_NANOS) {
                stopped = true;
                truncated = true;
                return true;
            }
            if (cancelled.getAsBoolean()) {
                stopped = true;
                truncated = true;
            }
            return stopped;
        }
    }

    private static final class BatchCombinationBudget {

        private int remaining;
        private boolean exhausted;

        private BatchCombinationBudget(int remaining) {
            this.remaining = remaining;
        }

        private boolean tryCombination() {
            if (remaining <= 0) {
                exhausted = true;
                return false;
            }
            remaining--;
            return true;
        }

        private boolean tryCombinations(int amount) {
            if (amount <= 0 || remaining < amount) {
                exhausted = true;
                return false;
            }
            remaining -= amount;
            return true;
        }
    }

    @Nullable
    private static ParsedRouteId parseStableRouteId(String stableRouteId) {
        String prefix = PROVIDER_ID + "/";
        if (!stableRouteId.startsWith(prefix)) {
            return null;
        }
        String remainder = stableRouteId.substring(prefix.length());
        int variantSeparator = remainder.lastIndexOf('/');
        if (variantSeparator <= 0 || variantSeparator == remainder.length() - 1) {
            return null;
        }
        String logical = remainder.substring(0, variantSeparator);
        String variant = remainder.substring(variantSeparator + 1);
        for (int split = logical.indexOf('/'); split >= 0;
              split = logical.indexOf('/', split + 1)) {
            String left = logical.substring(0, split);
            String right = logical.substring(split + 1);
            if (!left.equals(right)) {
                continue;
            }
            try {
                return new ParsedRouteId(new ResourceLocation(left), variant);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<IngredientChoice> emptyGrid() {
        List<IngredientChoice> grid = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            grid.add(IngredientChoice.empty());
        }
        return grid;
    }

    private static List<ItemStack> matchingGrid(List<IngredientChoice> grid) {
        List<ItemStack> copy = new ArrayList<>(grid.size());
        grid.forEach(choice -> copy.add(choice.matchingStack()));
        return copy;
    }

    private static boolean[] virtualFluidSlots(List<IngredientChoice> grid) {
        boolean[] slots = new boolean[grid.size()];
        for (int index = 0; index < grid.size(); index++) {
            slots[index] = grid.get(index).isVirtualFluid();
        }
        return slots;
    }

    private static String candidatePreferenceKey(List<IngredientChoice> grid,
          List<List<IngredientChoice>> orderedCandidates) {
        StringBuilder key = new StringBuilder(grid.size() * 3);
        for (int slot = 0; slot < grid.size(); slot++) {
            int rank = orderedCandidates.get(slot).indexOf(grid.get(slot));
            int checked = Math.max(0, rank);
            if (checked < 10) {
                key.append('0');
            }
            key.append(checked).append('|');
        }
        return key.toString();
    }

    @Nullable
    private static IngredientChoice directFluidChoice(ItemStack candidate) {
        IFluidHandlerItem first = FluidContainerUtils.getUnstackedFluidHandlerCapability(candidate);
        if (first == null) return null;
        IFluidTankProperties[] properties = first.getTankProperties();
        if (properties == null || properties.length != 1 || properties[0] == null) return null;
        FluidStack stored = properties[0].getContents();
        if (stored == null || stored.amount <= 0 || stored.amount > properties[0].getCapacity() ||
              !properties[0].canDrainFluidType(stored)) return null;
        FluidStack simulated = first.drain(stored.copy(), false);
        if (!sameFluidAmount(stored, simulated)) return null;
        IFluidHandlerItem second = FluidContainerUtils.getUnstackedFluidHandlerCapability(candidate);
        if (second == null) return null;
        FluidStack drained = second.drain(stored.copy(), true);
        if (!sameFluidAmount(stored, drained)) return null;
        IFluidTankProperties[] after = second.getTankProperties();
        if (after == null || after.length != 1 || after[0] == null) return null;
        FluidStack left = after[0].getContents();
        if (left != null && left.amount > 0) return null;
        return IngredientChoice.fluid(candidate, stored);
    }

    private static boolean sameFluidAmount(@Nullable FluidStack expected,
          @Nullable FluidStack actual) {
        return expected != null && actual != null && expected.amount == actual.amount &&
              expected.isFluidEqual(actual);
    }

    private static final class IngredientChoice {

        private static final IngredientChoice EMPTY = new IngredientChoice(ItemStack.EMPTY, null,
              0, false, "");
        private final ItemStack matchingStack;
        @Nullable
        private final PortableResourceDescriptor resource;
        private final long amount;
        private final boolean virtualFluid;
        private final String sortKey;
        private final String candidateId;

        private IngredientChoice(ItemStack matchingStack,
              @Nullable PortableResourceDescriptor resource, long amount, boolean virtualFluid,
              String sortKey) {
            this.matchingStack = matchingStack.copy();
            this.resource = resource;
            this.amount = amount;
            this.virtualFluid = virtualFluid;
            this.sortKey = sortKey;
            candidateId = resource == null ? "" : sha256(sortKey);
        }

        private static IngredientChoice empty() { return EMPTY; }

        private static IngredientChoice item(ItemStack stack) {
            if (!MachineRecipeItemInputs.isConcreteInput(stack)) {
                throw new IllegalArgumentException(
                      "QIO workbench item candidate must have concrete metadata");
            }
            PortableResourceDescriptor item = PortableResourceDescriptor.item(stack);
            return new IngredientChoice(stack, item, 1, false, "I|" + item);
        }

        private static IngredientChoice fluid(ItemStack matchingStack, FluidStack fluid) {
            if (!MachineRecipeItemInputs.isConcreteInput(matchingStack)) {
                throw new IllegalArgumentException(
                      "QIO workbench fluid container must have concrete metadata");
            }
            PortableResourceDescriptor descriptor = PortableResourceDescriptor.fluid(fluid);
            String container;
            try {
                container = PortableResourceDescriptor.item(matchingStack).toString();
            } catch (RuntimeException ignored) {
                ResourceLocation registryName = matchingStack.getItem().getRegistryName();
                if (registryName == null) {
                    throw new IllegalArgumentException("Direct fluid container has no stable identity");
                }
                container = registryName + "|" + matchingStack.getMetadata() + "|" +
                      String.valueOf(matchingStack.getTagCompound());
            }
            return new IngredientChoice(matchingStack, descriptor, fluid.amount, true,
                  "F|" + descriptor + '|' + fluid.amount + '|' + container);
        }

        private boolean isEmpty() { return resource == null; }
        private ItemStack matchingStack() { return matchingStack.copy(); }
        private PortableResourceDescriptor resource() {
            return Objects.requireNonNull(resource, "resource");
        }
        private long amount() { return amount; }
        private boolean isVirtualFluid() { return virtualFluid; }
        private String sortKey() { return sortKey; }
        private String candidateId() { return candidateId; }
        private CandidateDefinition definition() {
            return new CandidateDefinition(candidateId, matchingStack,
                  Objects.requireNonNull(resource, "resource"), amount, virtualFluid);
        }
    }

    private static String catalogSignature(Collection<LogicalRecipe> recipes) {
        StringBuilder canonical = new StringBuilder();
        for (LogicalRecipe recipe : recipes) {
            canonical.append(recipe.structuralSignature()).append('\n');
        }
        return sha256(canonical.toString());
    }

    private static String compiledCatalogSignature(Collection<CompiledRecipe> recipes) {
        StringBuilder canonical = new StringBuilder();
        for (CompiledRecipe recipe : recipes) {
            canonical.append(recipe.structuralSignature).append('\n');
        }
        return sha256(canonical.toString());
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static String sha256(String value) {
        return QIOHashing.sha256(value);
    }

    private static String diagnostic(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() :
              message;
    }
}
