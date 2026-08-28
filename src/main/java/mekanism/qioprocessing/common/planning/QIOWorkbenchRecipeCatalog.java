package mekanism.qioprocessing.common.planning;

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
import mekanism.qioprocessing.common.util.QIORecipeStackUtils;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** Captures a compact logical workbench directory and materializes exact variants on demand. */
/**
 * QIO 处理模块中的 QIOWorkbenchRecipeCatalog 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
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

    /** Semantic class of one frozen ingredient matcher. */
    public enum MatcherKind {
        EMPTY,
        EXACT,
        ORE,
        WILDCARD,
        ANY_OF
    }

    /**
     * Describes which positions are semantically part of a frozen recipe.  The physical
     * 3x3 inventory is still materialized at the Forge boundary, but the catalog and cache do
     * not assign meaning to empty positions in a shapeless recipe.
     */
    public enum RecipeLayout {
        SHAPED,
        SHAPELESS,
        ORDERED_UNKNOWN
    }

    private QIOWorkbenchRecipeCatalog() {
    }

    /** Signals a bounded server-side lookup which should be retried on a later tick. */
    public static final class TargetedRecipeLookupBusyException extends IllegalStateException {

        private TargetedRecipeLookupBusyException() {
            super("QIO targeted workbench recipe lookup budget is exhausted");
        }
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
        QIORecipeCatalogService service = QIORecipeCatalogService.INSTANCE;
        boolean targetedAllowed = service.allowsTargetedEncoding();
        int dimension = world.provider.getDimension();
        QIORecipeTargetedLookupGuard.Lookup targetedCache = targetedAllowed ?
              service.lookupTargetedRecipe(dimension, checkedGrid) : null;
        InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
        for (int slot = 0; slot < 9; slot++) {
            inventory.setInventorySlotContents(slot, checkedGrid.get(slot));
        }
        IRecipe preferred = null;
        // Once a capability-neutral result is cached, do not call CraftingManager again. The
        // submitted grid was normalized before reaching this point, so transient providers are
        // never inspected by the native matcher.
        if (targetedCache == null || !targetedCache.isCached()) {
            // Ordinary stacks retain one Forge-native match. Capability-bearing stacks in modes
            // with a targeted fallback skip this call because providers may throw or perform
            // expensive work while the normal matcher inspects them.
            try {
                preferred = CraftingManager.findMatchingRecipe(inventory, world);
            } catch (RuntimeException | LinkageError ignored) {
                // The capability-neutral catalog path below can still identify the recipe.
            }
        } else if (targetedCache.getRecipeId() != null) {
            preferred = ForgeRegistries.RECIPES.getValue(targetedCache.getRecipeId());
        }
        ResourceLocation preferredId = preferred == null ? null : preferred.getRegistryName();
        QIOWorkbenchConfiguration.EncodedPattern normalized =
              resolveEncodedPatternFromCatalog(world, checkedGrid, preferredId);
        if (normalized != null) return normalized;
        // A published global index can usually normalize capability-bearing stacks without a
        // second Forge traversal. Only modes which support targeted encoding need the bounded
        // capability-neutral fallback after that index has missed.
        boolean targetedPreferred = false;
        if (targetedAllowed) {
            preferred = findTargetedRecipe(world, checkedGrid);
            targetedPreferred = preferred != null;
            preferredId = preferred == null ? null : preferred.getRegistryName();
            if (preferredId != null) {
                normalized = resolveEncodedPatternFromCatalog(world, checkedGrid, preferredId);
                if (normalized != null) return normalized;
            }
        }
        // Keep Forge's normal shaped-recipe placement/mirroring semantics after matching against
        // capability-neutral copies. Any transient ForgeCaps are intentionally excluded from the
        // saved QIO recipe identity.
        if (preferred != null) {
            QIOWorkbenchConfiguration.EncodedPattern direct = resolveDirectEncodedPattern(
                  world, checkedGrid, preferred);
            if (direct != null) {
                if (targetedPreferred) {
                    service.rememberTargetedRecipe(dimension, checkedGrid,
                          preferred.getRegistryName());
                }
                return direct;
            }
        }
        if (targetedPreferred) {
            service.forgetTargetedRecipe(dimension, checkedGrid);
        }
        throw new IllegalArgumentException(
              "No stable workbench recipe matches the encoded grid or its frozen variants");
    }

    @Nullable
    private static QIOWorkbenchConfiguration.EncodedPattern resolveEncodedPatternFromCatalog(
          @Nonnull World world, @Nonnull List<ItemStack> selectedGrid,
          @Nullable ResourceLocation preferredRecipeId) {
        QIORecipeCatalogService service = QIORecipeCatalogService.INSTANCE;
        RecipeOutputIndex index = service.getRecipeOutputIndex(world);
        QIOWorkbenchConfiguration.EncodedPattern result = index.resolveEncodedPattern(world,
              selectedGrid, preferredRecipeId);
        if (result != null) return result;
        // A singleton immutable index is also the direct-encoding path for DISABLED mode.  It
        // does not perform a global scan and keeps nine-slot encoding available when the global
        // directory is intentionally empty.
        if (preferredRecipeId != null) {
            IRecipe preferred = ForgeRegistries.RECIPES.getValue(preferredRecipeId);
            if (preferred != null) {
                index = RecipeOutputIndex.build(world, Collections.singletonList(preferred));
                return index.resolveEncodedPattern(world, selectedGrid, preferredRecipeId);
            }
        }
        return null;
    }

    @Nullable
    private static QIOWorkbenchConfiguration.EncodedPattern resolveDirectEncodedPattern(
          @Nonnull World world, @Nonnull List<ItemStack> selectedGrid,
          @Nonnull IRecipe recipe) {
        if (recipe.isDynamic() || recipe.getRegistryName() == null) return null;
        List<ItemStack> concreteGrid = new ArrayList<>(9);
        for (ItemStack stack : selectedGrid) {
            ItemStack concrete = concreteSelectionStack(stack);
            if (!stack.isEmpty() && concrete.isEmpty()) return null;
            concreteGrid.add(concrete);
        }
        InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
        try {
            for (int slot = 0; slot < 9; slot++) {
                inventory.setInventorySlotContents(slot,
                      withoutSerializedCapabilities(concreteGrid.get(slot)));
            }
            if (!recipe.matches(inventory, world)) return null;
            ItemStack result = concreteStack(recipe.getCraftingResult(inventory));
            if (result.isEmpty()) return null;
            QIORecipeCatalogService service = QIORecipeCatalogService.INSTANCE;
            RecipeOutputIndex index = service.getRecipeOutputIndex(world);
            RecipeDefinition definition = index.getRecipeDefinition(recipe.getRegistryName());
            if (definition == null) {
                index = RecipeOutputIndex.build(world, Collections.singletonList(recipe));
                definition = index.getRecipeDefinition(recipe.getRegistryName());
            }
            PortableResourceDescriptor output = PortableResourceDescriptor.itemIgnoringCapabilities(result);
            if (definition == null || !definition.getOutput().equals(output) ||
                  definition.getOutputAmount() != result.getCount()) {
                return null;
            }
            return new QIOWorkbenchConfiguration.EncodedPattern(UUID.randomUUID(),
                  recipe.getRegistryName(), definition.getSignature(), output,
                  result.getCount(), patternLayout(definition.getLayout()),
                  encodedGrid(concreteGrid, definition.getLayout()));
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static IRecipe findTargetedRecipe(World world, List<ItemStack> selectedGrid) {
        QIORecipeCatalogService service = QIORecipeCatalogService.INSTANCE;
        int dimension = world.provider.getDimension();
        QIORecipeTargetedLookupGuard.Lookup cached =
              service.lookupTargetedRecipe(dimension, selectedGrid);
        if (cached.isCached()) {
            ResourceLocation recipeId = cached.getRecipeId();
            if (recipeId == null) return null;
            IRecipe recipe = ForgeRegistries.RECIPES.getValue(recipeId);
            if (recipe != null && !recipe.isDynamic() && recipe.getRegistryName() != null) {
                return recipe;
            }
            service.forgetTargetedRecipe(dimension, selectedGrid);
        }
        if (!service.tryAcquireTargetedRecipeSearch()) {
            throw new TargetedRecipeLookupBusyException();
        }
        InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
        for (int slot = 0; slot < selectedGrid.size(); slot++) {
            inventory.setInventorySlotContents(slot, selectedGrid.get(slot));
        }
        // CraftingManager was already consulted for ordinary stacks by the caller. Calling it
        // again here would repeat a full registry traversal on every uncached miss; the frozen
        // snapshot below is the single budgeted fallback for both ordinary and capability stacks.
        for (IRecipe recipe : QIORecipeCatalogService.INSTANCE.getStableRecipeSnapshot()) {
            if (recipe == null || recipe.isDynamic() || recipe.getRegistryName() == null) {
                continue;
            }
            try {
                if (recipe.matches(inventory, world)) {
                    service.rememberTargetedRecipe(dimension, selectedGrid,
                          recipe.getRegistryName());
                    return recipe;
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        service.rememberTargetedRecipe(dimension, selectedGrid, null);
        return null;
    }

    @Nonnull
    private static ItemStack withoutSerializedCapabilities(@Nullable ItemStack stack) {
        return QIORecipeStackUtils.copyForRecipeSelection(stack, 1);
    }

    /** Concrete variant expansion for the capability-neutral recipe-selection boundary. */
    @Nonnull
    private static ItemStack concreteSelectionStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        if (MachineRecipeItemInputs.isConcreteInput(stack)) {
            return withoutSerializedCapabilities(stack);
        }
        // The generic expander calls ItemStack#copy while walking wildcard variants.  Use a
        // recipe-selection copy first so a held stack with a large ForgeCaps payload is never
        // serialized on this boundary.
        ItemStack selection = QIORecipeStackUtils.copyForRecipeSelection(stack, 1);
        List<ItemStack> expanded = MachineRecipeItemInputs.expand(selection, null, 1);
        return expanded.isEmpty() ? ItemStack.EMPTY :
              withoutSerializedCapabilities(expanded.get(0));
    }

    /**
     * Discovers every representable stable recipe for the requested outputs. Exact capability
     * identities are preferred; a unique capability-neutral catalog variant is the fallback.
     */
    @Nonnull
    public static List<QIOWorkbenchConfiguration.EncodedPattern> resolveEncodedTargets(
          @Nonnull World world, @Nonnull Collection<ItemStack> targets) {
        if (!QIORecipeCatalogService.INSTANCE.allowsBatchEncoding()) {
            throw new IllegalStateException(
                  "Batch workbench encoding is disabled by the recipe catalog scan mode");
        }
        return resolveEncodedTargets(world, targets,
              QIORecipeCatalogService.INSTANCE.getRecipeOutputIndex(world),
              DEFAULT_BATCH_COMBINATION_BUDGET, true);
    }

    @Nonnull
    static List<QIOWorkbenchConfiguration.EncodedPattern> resolveEncodedTargets(
          @Nonnull World world, @Nonnull Collection<ItemStack> targets,
          @Nonnull Collection<? extends IRecipe> recipes, int combinationBudget) {
        Objects.requireNonNull(recipes, "recipes");
        return resolveEncodedTargets(world, targets, RecipeOutputIndex.build(world, recipes),
              combinationBudget, false);
    }

    @Nonnull
    private static List<QIOWorkbenchConfiguration.EncodedPattern> resolveEncodedTargets(
          @Nonnull World world, @Nonnull Collection<ItemStack> targets,
          @Nonnull RecipeOutputIndex recipeIndex, int combinationBudget,
          boolean validateCurrentRegistry) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(recipeIndex, "recipeIndex");
        if (targets.isEmpty() || targets.size() > MAX_BATCH_TARGETS ||
            combinationBudget <= 0) {
            throw new IllegalArgumentException("Invalid workbench batch discovery bounds");
        }
        Map<PortableResourceDescriptor, List<CachedRecipe>> selectedTargets =
              new LinkedHashMap<>();
        for (ItemStack target : targets) {
            if (target == null || target.isEmpty()) {
                throw new IllegalArgumentException("Workbench batch target is empty");
            }
            ItemStack copy = QIORecipeStackUtils.copyForRecipeSelection(target, 1);
            if (copy.isEmpty()) continue;
            OutputSelection selection = recipeIndex.resolveOutputSelection(copy);
            if (selection != null) {
                selectedTargets.putIfAbsent(selection.output, selection.recipes);
            }
        }
        if (selectedTargets.isEmpty()) {
            return Collections.emptyList();
        }

        int matchedRecipes = 0;
        for (List<CachedRecipe> recipes : selectedTargets.values()) {
            if (recipes.size() > MAX_BATCH_MATCHED_RECIPES - matchedRecipes) {
                throw new IllegalStateException("Workbench batch matched too many recipes");
            }
            matchedRecipes += recipes.size();
        }
        BatchCombinationBudget budget = new BatchCombinationBudget(combinationBudget);
        List<PortableResourceDescriptor> orderedTargets = new ArrayList<>(
              selectedTargets.keySet());
        Collections.sort(orderedTargets);
        List<QIOWorkbenchConfiguration.EncodedPattern> result = new ArrayList<>();
        for (PortableResourceDescriptor target : orderedTargets) {
            for (CachedRecipe recipe : selectedTargets.get(target)) {
                if (!budget.tryCombinations(recipe.combinationCost)) {
                    throw new IllegalStateException(
                          "Workbench batch combination budget exhausted");
                }
                QIOWorkbenchConfiguration.EncodedPattern pattern = recipe.newPattern();
                result.add(validateCurrentRegistry ? validateEncodedPattern(world, pattern) :
                      pattern);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Nonnull
    private static QIOWorkbenchConfiguration.EncodedPattern validateEncodedPattern(
          @Nonnull World world,
          @Nonnull QIOWorkbenchConfiguration.EncodedPattern expected) {
        IRecipe recipe = ForgeRegistries.RECIPES.getValue(expected.getRecipeId());
        if (recipe == null || recipe.isDynamic() || recipe.getRegistryName() == null) {
            throw new IllegalArgumentException("Selected workbench recipe is no longer registered");
        }
        InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
        List<ItemStack> grid = expected.getGrid();
        for (int slot = 0; slot < 9; slot++) {
            inventory.setInventorySlotContents(slot,
                  QIORecipeStackUtils.copyForRecipeSelection(grid.get(slot)));
        }
        if (!recipe.matches(inventory, world)) {
            throw new IllegalArgumentException("Selected workbench recipe no longer matches");
        }
        ItemStack result = concreteStack(recipe.getCraftingResult(inventory));
        if (result.isEmpty() || result.getCount() != expected.getOutputAmount() ||
            !PortableResourceDescriptor.itemIgnoringCapabilities(result).equals(expected.getOutput())) {
            throw new IllegalArgumentException("Selected workbench recipe output changed");
        }
        Snapshot current = capture(world, Collections.singletonList(recipe), ignored -> 0,
              DEFAULT_MAX_CANDIDATES_PER_INGREDIENT, DEFAULT_MAX_VARIANTS_PER_RECIPE,
              DEFAULT_MAX_MATERIALIZED_VARIANTS);
        RecipeDefinition definition = current.getRecipeDefinition(expected.getRecipeId());
        if (definition == null ||
            !definition.getSignature().equals(expected.getRecipeSignature())) {
            throw new IllegalArgumentException("Selected workbench recipe structure changed");
        }
        return new QIOWorkbenchConfiguration.EncodedPattern(UUID.randomUUID(),
              expected.getRecipeId(), definition.getSignature(), definition.getOutput(),
              definition.getOutputAmount(), patternLayout(definition.getLayout()),
              encodedGrid(grid, definition.getLayout()));
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
              pattern.getOutputAmount(), pattern.getLayout(), pattern.getGrid());
    }

    private static List<ItemStack> normalizeGrid(List<ItemStack> grid) {
        if (grid == null || grid.size() != 9) {
            throw new IllegalArgumentException("Workbench pattern grid must contain nine slots");
        }
        List<ItemStack> result = new ArrayList<>(9);
        boolean hasInput = false;
        for (ItemStack stack : grid) {
            // ItemStack#copy serializes the Forge capability dispatcher in 1.12.  Recipe
            // selection deliberately ignores transient capabilities, so make a lightweight copy
            // containing only the registry item, metadata, and ordinary item NBT.
            ItemStack copy = stack == null || stack.isEmpty() ? ItemStack.EMPTY :
                  withoutSerializedCapabilities(stack);
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
        if (MachineRecipeItemInputs.isConcreteInput(stack)) {
            return QIORecipeStackUtils.copyForRecipeSelection(stack);
        }
        // Keep wildcard expansion capability-neutral; ItemStack#copy serializes ForgeCaps.
        ItemStack selection = QIORecipeStackUtils.copyForRecipeSelection(stack, 1);
        List<ItemStack> expanded = MachineRecipeItemInputs.expand(selection, null, 1);
        return expanded.isEmpty() ? ItemStack.EMPTY :
              QIORecipeStackUtils.copyForRecipeSelection(expanded.get(0));
    }

    /** Recipe selection ignores context-only capabilities but preserves item metadata and NBT. */
    private static boolean sameRecipeSelectionItem(ItemStack first, ItemStack second) {
        return QIORecipeStackUtils.sameRecipeSelection(first, second);
    }

    private static boolean hasDescriptorCapabilities(
          PortableResourceDescriptor descriptor) {
        return descriptor.hasCapabilities();
    }

    private static boolean validateNormalizedRecipe(@Nonnull World world,
          @Nonnull IRecipe recipe, @Nonnull RecipeDefinition definition,
          @Nonnull List<ItemStack> normalizedGrid) {
        if (normalizedGrid.size() != 9) return false;
        try {
            InventoryCrafting inventory = MekanismUtils.getDummyCraftingInv();
            for (int slot = 0; slot < 9; slot++) {
                inventory.setInventorySlotContents(slot,
                      QIORecipeStackUtils.copyForRecipeSelection(normalizedGrid.get(slot)));
            }
            if (!recipe.matches(inventory, world)) return false;
            ItemStack result = concreteStack(recipe.getCraftingResult(inventory));
            return !result.isEmpty() && definition.getOutputAmount() == result.getCount() &&
                  definition.getOutput().equals(PortableResourceDescriptor.itemIgnoringCapabilities(result));
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
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
        private final int candidateScanLimit;
        private final int maxVariantsPerRecipe;
        private final int maxMaterializedVariants;
        @Nullable
        private final QIOForgeRecipeData forgeData;
        private final Map<ResourceLocation, LogicalRecipe> recipes = new TreeMap<>();
        private final Map<CandidateExpansionKey, List<ItemStack>> wildcardExpansionCache =
              new LinkedHashMap<>();
        private final List<String> diagnostics = new ArrayList<>();

        private Builder(@Nullable World world, PriorityResolver priorityResolver,
              int maxCandidatesPerIngredient, int maxVariantsPerRecipe,
              int maxMaterializedVariants) {
            this(world, priorityResolver, maxCandidatesPerIngredient, maxVariantsPerRecipe,
                  maxMaterializedVariants, null);
        }

        private Builder(@Nullable World world, PriorityResolver priorityResolver,
              int maxCandidatesPerIngredient, int maxVariantsPerRecipe,
              int maxMaterializedVariants, @Nullable QIOForgeRecipeData forgeData) {
            this.world = world;
            this.priorityResolver = priorityResolver;
            this.maxCandidatesPerIngredient = maxCandidatesPerIngredient;
            candidateScanLimit = maxCandidatesPerIngredient > Integer.MAX_VALUE / 8 ?
                  Integer.MAX_VALUE : maxCandidatesPerIngredient * 8;
            this.maxVariantsPerRecipe = maxVariantsPerRecipe;
            this.maxMaterializedVariants = maxMaterializedVariants;
            this.forgeData = forgeData;
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
            GridCandidates slots;
            try {
                declaredOutput = recipe.getRecipeOutput();
                if (declaredOutput == null || declaredOutput.isEmpty()) {
                    diagnostics.add("Skipped workbench recipe without a stable declared output: " +
                          recipeId);
                    return;
                }
                declaredOutput = QIORecipeStackUtils.copyForRecipeSelection(declaredOutput);
                if (declaredOutput.isEmpty()) {
                    throw new IllegalArgumentException("Recipe output could not be frozen");
                }
                slots = exactGridCandidates(recipe, ingredients);
                if (!MachineRecipeItemInputs.isConcreteInput(declaredOutput)) {
                    declaredOutput = firstConcreteRecipeOutput(recipe, declaredOutput,
                          slots.candidates);
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
                    inventory.setInventorySlotContents(slot,
                          QIORecipeStackUtils.copyForRecipeSelection(grid.get(slot)));
                }
                if (!recipe.matches(inventory, world)) {
                    diagnostics.add("Encoded workbench grid no longer matches: " + recipeId);
                    return;
                }
                ItemStack result = concreteStack(recipe.getCraftingResult(inventory));
                if (result == null || result.isEmpty() ||
                    !PortableResourceDescriptor.itemIgnoringCapabilities(result).equals(pattern.getOutput()) ||
                    result.getCount() != pattern.getOutputAmount()) {
                    diagnostics.add("Encoded workbench output changed: " + recipeId);
                    return;
                }
                add(recipe);
                LogicalRecipe logical = recipes.get(recipeId);
                if (logical == null || !logical.signature.equals(pattern.getRecipeSignature()) ||
                      !patternLayout(logical.layout).equals(pattern.getLayout())) {
                    recipes.remove(recipeId);
                    diagnostics.add("Encoded workbench recipe signature changed: " + recipeId);
                }
            } catch (RuntimeException error) {
                diagnostics.add("Unable to validate encoded workbench recipe " + recipeId + ": " +
                      diagnostic(error));
            }
        }

        private GridCandidates exactGridCandidates(IRecipe recipe,
              NonNullList<Ingredient> ingredients) {
            List<MatcherSeed> grid = new ArrayList<>(9);
            for (int slot = 0; slot < 9; slot++) {
                grid.add(MatcherSeed.empty());
            }
            RecipeLayout layout;
            if (recipe instanceof IShapedRecipe shaped) {
                layout = RecipeLayout.SHAPED;
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
                layout = isKnownShapelessRecipe(recipe) ? RecipeLayout.SHAPELESS :
                      RecipeLayout.ORDERED_UNKNOWN;
                for (int slot = 0; slot < ingredients.size(); slot++) {
                    grid.set(slot, candidates(ingredients.get(slot)));
                }
            }
            return new GridCandidates(layout, grid);
        }

        /** Forge 1.12 has no public IShapelessRecipe marker; keep unknown custom recipes ordered. */
        private static boolean isKnownShapelessRecipe(@Nonnull IRecipe recipe) {
            Class<?> type = recipe.getClass();
            while (type != null) {
                String name = type.getName();
                if (name.equals("net.minecraft.item.crafting.ShapelessRecipes") ||
                      name.equals("net.minecraftforge.oredict.ShapelessOreRecipe") ||
                      name.endsWith(".ShapelessRecipes") || name.endsWith(".ShapelessOreRecipe")) {
                    return true;
                }
                type = type.getSuperclass();
            }
            return false;
        }

        private MatcherSeed candidates(Ingredient ingredient) {
            if (ingredient == null || ingredient == Ingredient.EMPTY) {
                return MatcherSeed.empty();
            }
            ItemStack[] matching = ingredient.getMatchingStacks();
            if (matching == null || matching.length == 0) {
                throw new IllegalArgumentException("Ingredient has no exact item candidates");
            }
            Map<String, IngredientChoice> unique = new LinkedHashMap<>();
            int scanned = 0;
            candidateLoop:
            for (ItemStack candidate : matching) {
                if (candidate == null || candidate.isEmpty()) {
                    continue;
                }
                boolean expandedWildcard = !MachineRecipeItemInputs.isConcreteInput(candidate);
                for (ItemStack concrete : expandedCandidate(candidate)) {
                    if (scanned++ >= candidateScanLimit) {
                        break candidateLoop;
                    }
                    if (!ingredient.apply(concrete)) {
                        continue;
                    }
                    ItemStack copy = QIORecipeStackUtils.copyForRecipeSelection(concrete, 1);
                    if (copy.isEmpty()) continue;
                    try {
                        IngredientChoice itemChoice = IngredientChoice.item(copy);
                        unique.putIfAbsent(itemChoice.sortKey(), itemChoice);
                    } catch (RuntimeException ignored) {
                    }
                    try {
                        // For a concrete matching stack, inspect the original capability in place.
                        // The capability-neutral item copy remains the persistent identity.
                        IngredientChoice fluidChoice = directFluidChoice(concrete);
                        if (fluidChoice != null) {
                            unique.putIfAbsent(fluidChoice.sortKey(), fluidChoice);
                        }
                    } catch (RuntimeException ignored) {
                    }
                    if (expandedWildcard && forgeData != null) {
                        // SEARCH and ore variants cross the asynchronous boundary without
                        // ForgeCaps. Their small, immutable fluid snapshots are indexed by the
                        // stable item identity and restored here.
                        for (FluidStack fluid : forgeData.containedFluids(concrete)) {
                            try {
                                IngredientChoice fluidChoice = IngredientChoice.fluid(copy, fluid);
                                unique.putIfAbsent(fluidChoice.sortKey(), fluidChoice);
                            } catch (RuntimeException ignored) {
                            }
                            if (unique.size() >= maxCandidatesPerIngredient) {
                                break;
                            }
                        }
                    }
                    if (unique.size() >= maxCandidatesPerIngredient) {
                        break candidateLoop;
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
            MatcherKind kind = matcherKind(ingredient, matching);
            List<String> source = new ArrayList<>(matching.length);
            for (ItemStack stack : matching) {
                source.add(stackIdentity(stack));
            }
            Collections.sort(source);
            StringBuilder semantic = new StringBuilder(ingredient.getClass().getName())
                  .append('|').append(kind.name()).append('|');
            source.forEach(key -> semantic.append(key).append(';'));
            return new MatcherSeed(new MatcherKey(kind, sha256(semantic.toString()), ordered),
                  ordered);
        }

        private static MatcherKind matcherKind(Ingredient ingredient, ItemStack[] matching) {
            String className = ingredient.getClass().getName();
            if (className.equals("net.minecraftforge.oredict.OreIngredient") ||
                className.endsWith(".OreIngredient")) {
                return MatcherKind.ORE;
            }
            for (ItemStack stack : matching) {
                if (stack != null && !stack.isEmpty() &&
                    stack.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                    return MatcherKind.WILDCARD;
                }
            }
            return matching.length == 1 ? MatcherKind.EXACT : MatcherKind.ANY_OF;
        }

    private static String stackIdentity(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        try {
            return PortableResourceDescriptor.itemIgnoringCapabilities(stack) + "@" +
                  stack.getCount();
            } catch (RuntimeException ignored) {
                ResourceLocation name = stack.getItem().getRegistryName();
                return String.valueOf(name) + '|' + stack.getMetadata() + '|' +
                      stack.getCount() + '|' + String.valueOf(stack.getTagCompound());
            }
        }

        private List<ItemStack> expandedCandidate(ItemStack candidate) {
            if (MachineRecipeItemInputs.isConcreteInput(candidate)) {
                // The caller reads capabilities directly and never retains or mutates this
                // recipe-owned stack. IngredientChoice performs the capability-neutral copy.
                return Collections.singletonList(candidate);
            }
            CandidateExpansionKey key = new CandidateExpansionKey(candidate);
            List<ItemStack> expanded = wildcardExpansionCache.get(key);
            if (expanded == null) {
                List<ItemStack> captured = forgeData == null ? Collections.emptyList() :
                      forgeData.expand(candidate, maxCandidatesPerIngredient);
                if (captured.isEmpty()) {
                    // This fallback is used only when the immutable Forge snapshot is not yet
                    // available.  Normalize before entering the generic expander because it
                    // copies wildcard inputs and would otherwise serialize ForgeCaps.
                    ItemStack selection = QIORecipeStackUtils.copyForRecipeSelection(candidate, 1);
                    expanded = Collections.unmodifiableList(MachineRecipeItemInputs.expand(
                          selection, null, maxCandidatesPerIngredient));
                } else {
                    expanded = captured;
                }
                wildcardExpansionCache.put(key, expanded);
            }
            return expanded;
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
            CatalogSymbols symbols = CatalogSymbols.build(new SymbolCatalogInput(
                  recipes.values()));
            for (LogicalRecipe recipe : recipes.values()) {
                try {
                    CompiledRecipe template = recipe.compile(symbols);
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
            return new Snapshot(compiled, compiledDiagnostics, DEFAULT_PATTERN_CACHE_SIZE,
                  symbols.generation(compiled.values()));
        }
    }

    private static final class CandidateExpansionKey {

        private final Item item;
        private final int metadata;
        private final int count;
        @Nullable
        private final NBTTagCompound tag;
        private final int hashCode;

        private CandidateExpansionKey(ItemStack stack) {
            item = stack.getItem();
            metadata = stack.getMetadata();
            count = stack.getCount();
            tag = stack.getTagCompound();
            int hash = System.identityHashCode(item);
            hash = 31 * hash + metadata;
            hash = 31 * hash + count;
            hashCode = 31 * hash + (tag == null ? 0 : tag.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CandidateExpansionKey other)) {
                return false;
            }
            return item == other.item && metadata == other.metadata && count == other.count &&
                  Objects.equals(tag, other.tag);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class GridCandidates {

        private final RecipeLayout layout;
        private final List<MatcherSeed> matchers;
        private final List<List<IngredientChoice>> candidates;

        private GridCandidates(RecipeLayout layout, List<MatcherSeed> matchers) {
            this.layout = Objects.requireNonNull(layout, "layout");
            this.matchers = Collections.unmodifiableList(new ArrayList<>(matchers));
            List<List<IngredientChoice>> choices = new ArrayList<>(matchers.size());
            for (MatcherSeed matcher : matchers) {
                choices.add(matcher.choices);
            }
            candidates = Collections.unmodifiableList(choices);
        }
    }

    private static final class MatcherSeed {

        private static final MatcherSeed EMPTY = new MatcherSeed(MatcherKey.EMPTY,
              Collections.singletonList(IngredientChoice.empty()));
        private final MatcherKey key;
        private final List<IngredientChoice> choices;

        private MatcherSeed(MatcherKey key, List<IngredientChoice> choices) {
            this.key = Objects.requireNonNull(key, "key");
            this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
        }

        private static MatcherSeed empty() {
            return EMPTY;
        }
    }

    private static final class MatcherKey implements Comparable<MatcherKey> {

        private static final MatcherKey EMPTY = new MatcherKey();
        private final MatcherKind kind;
        private final String canonical;
        private final int hashCode;

        private MatcherKey() {
            kind = MatcherKind.EMPTY;
            canonical = "EMPTY";
            hashCode = canonical.hashCode();
        }

        private MatcherKey(MatcherKind kind, String sourceSignature,
              List<IngredientChoice> choices) {
            this.kind = Objects.requireNonNull(kind, "kind");
            StringBuilder value = new StringBuilder(kind.name()).append('|')
                  .append(Objects.requireNonNull(sourceSignature, "sourceSignature"))
                  .append('|');
            for (IngredientChoice choice : choices) {
                value.append(choice.sortKey()).append(';');
            }
            canonical = value.toString();
            hashCode = canonical.hashCode();
        }

        @Override
        public int compareTo(MatcherKey other) {
            return canonical.compareTo(Objects.requireNonNull(other, "other").canonical);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MatcherKey key && canonical.equals(key.canonical);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class FrozenItemType {

        private final PortableResourceDescriptor descriptor;
        private final int runtimeItemId;
        private final ItemStack prototype;
        private final NBTTagCompound serializedPrototype;

        private FrozenItemType(PortableResourceDescriptor descriptor, int runtimeItemId,
              ItemStack prototype) {
            this.descriptor = descriptor;
            this.runtimeItemId = runtimeItemId;
            this.prototype = QIORecipeStackUtils.copyForRecipeSelection(prototype, 1);
            if (this.prototype.isEmpty()) {
                throw new IllegalArgumentException("Cannot freeze an empty item type");
            }
            serializedPrototype = QIORecipeStackUtils.writeForRecipeSelection(this.prototype);
        }

        private static FrozenItemType capture(ItemStack stack) {
            ItemStack prototype = QIORecipeStackUtils.copyForRecipeSelection(stack, 1);
            if (prototype.isEmpty()) throw new IllegalArgumentException("Invalid item type");
            PortableResourceDescriptor descriptor;
            try {
                descriptor = PortableResourceDescriptor.itemIgnoringCapabilities(prototype);
            } catch (RuntimeException error) {
                ResourceLocation registryName = prototype.getItem().getRegistryName();
                if (registryName == null) throw error;
                // Test and compatibility containers may carry a stable name without being in
                // Item.REGISTRY. Preserve their full serialized identity for this generation.
                descriptor = PortableResourceDescriptor.named(
                      PortableResourceDescriptor.Kind.ITEM, registryName.toString(),
                      prototype.getMetadata(), QIORecipeStackUtils.writeForRecipeSelection(prototype));
            }
            return new FrozenItemType(descriptor, Item.getIdFromItem(prototype.getItem()),
                  prototype);
        }
    }

    /** One concrete item/metadata/ordinary-NBT identity in a catalog generation. */
    public static final class ItemTypeDefinition {

        private final int itemTypeId;
        private final int runtimeItemId;
        private final PortableResourceDescriptor descriptor;
        private final ItemStack prototype;
        private final NBTTagCompound serializedPrototype;

        private ItemTypeDefinition(int itemTypeId, FrozenItemType frozen) {
            this.itemTypeId = itemTypeId;
            runtimeItemId = frozen.runtimeItemId;
            descriptor = frozen.descriptor;
            prototype = frozen.prototype;
            serializedPrototype = frozen.serializedPrototype.copy();
        }

        public int getItemTypeId() { return itemTypeId; }
        public int getRuntimeItemId() { return runtimeItemId; }
        @Nonnull public PortableResourceDescriptor getDescriptor() { return descriptor; }
        @Nonnull public ItemStack getDisplayStack() {
            return QIORecipeStackUtils.copyForRecipeSelection(prototype);
        }
    }

    /** Immutable numeric lookup for every concrete item identity used by this generation. */
    public static final class ItemTypeTable {

        private final List<ItemTypeDefinition> entries;
        private final Map<PortableResourceDescriptor, Integer> ids;

        private ItemTypeTable(List<ItemTypeDefinition> entries,
              Map<PortableResourceDescriptor, Integer> ids) {
            this(entries, ids, false);
        }

        private ItemTypeTable(List<ItemTypeDefinition> entries,
              Map<PortableResourceDescriptor, Integer> ids, boolean owned) {
            this.entries = Collections.unmodifiableList(owned ? entries :
                  new ArrayList<>(entries));
            this.ids = Collections.unmodifiableMap(owned ? ids :
                  new LinkedHashMap<>(ids));
        }

        public int size() { return entries.size(); }

        @Nonnull
        public ItemTypeDefinition get(int itemTypeId) {
            if (itemTypeId < 0 || itemTypeId >= entries.size()) {
                throw new IllegalArgumentException("Unknown QIO item type ID: " + itemTypeId);
            }
            return entries.get(itemTypeId);
        }

        public int getId(@Nonnull PortableResourceDescriptor descriptor) {
            Integer id = ids.get(Objects.requireNonNull(descriptor, "descriptor"));
            if (id == null) {
                throw new IllegalArgumentException("Item type is not part of this catalog generation");
            }
            return id;
        }
    }

    /** One shared ingredient matcher and its selectable concrete variants. */
    public static final class MatcherDefinition {

        private final int matcherId;
        private final MatcherKind kind;
        private final List<IngredientChoice> choices;
        private final List<CandidateDefinition> candidates;
        private final List<String> candidateIds;

        private MatcherDefinition(int matcherId, MatcherKey key,
              List<IngredientChoice> choices, ItemTypeTable itemTypes) {
            this.matcherId = matcherId;
            kind = key.kind;
            this.choices = choices;
            List<CandidateDefinition> definitions = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (IngredientChoice choice : choices) {
                if (!choice.isEmpty()) {
                    CandidateDefinition definition = choice.definition(itemTypes);
                    definitions.add(definition);
                    ids.add(definition.getCandidateId());
                }
            }
            candidates = Collections.unmodifiableList(definitions);
            candidateIds = Collections.unmodifiableList(ids);
        }

        public int getMatcherId() { return matcherId; }
        @Nonnull public MatcherKind getKind() { return kind; }
        public boolean isEmpty() { return matcherId == IngredientVariantTable.EMPTY_MATCHER_ID; }
        @Nonnull public List<CandidateDefinition> getCandidates() { return candidates; }
        @Nonnull public List<String> getCandidateIds() { return candidateIds; }
    }

    /** Immutable MatcherId to variant-set table shared by every recipe in one generation. */
    public static final class IngredientVariantTable {

        public static final int EMPTY_MATCHER_ID = 0;
        private final List<MatcherDefinition> matchers;

        private IngredientVariantTable(List<MatcherDefinition> matchers) {
            this(matchers, false);
        }

        private IngredientVariantTable(List<MatcherDefinition> matchers, boolean owned) {
            this.matchers = Collections.unmodifiableList(owned ? matchers :
                  new ArrayList<>(matchers));
        }

        public int size() { return matchers.size(); }

        @Nonnull
        public MatcherDefinition get(int matcherId) {
            if (matcherId < 0 || matcherId >= matchers.size()) {
                throw new IllegalArgumentException("Unknown QIO ingredient matcher ID: " + matcherId);
            }
            return matchers.get(matcherId);
        }

        @Nonnull
        private List<IngredientChoice> choices(int matcherId) {
            return get(matcherId).choices;
        }
    }

    /** Immutable numeric recipe-skeleton directory for one catalog generation. */
    public static final class RecipeSkeletonTable {

        private final List<RecipeDefinition> recipes;
        private final Map<ResourceLocation, RecipeDefinition> recipesById;

        private RecipeSkeletonTable(Collection<CompiledRecipe> compiled) {
            List<RecipeDefinition> ordered = new ArrayList<>(compiled.size());
            Map<ResourceLocation, RecipeDefinition> byId = new LinkedHashMap<>();
            for (CompiledRecipe recipe : compiled) {
                ordered.add(recipe.definition);
                byId.put(recipe.definition.getRecipeId(), recipe.definition);
            }
            recipes = Collections.unmodifiableList(ordered);
            recipesById = Collections.unmodifiableMap(byId);
        }

        public int size() { return recipes.size(); }
        @Nonnull public List<RecipeDefinition> getRecipes() { return recipes; }
        @Nullable public RecipeDefinition get(@Nonnull ResourceLocation recipeId) {
            return recipesById.get(Objects.requireNonNull(recipeId, "recipeId"));
        }
    }

    /** Atomically published set of all three numeric tables. */
    public static final class CatalogGeneration {

        private final ItemTypeTable itemTypes;
        private final IngredientVariantTable ingredientVariants;
        private final RecipeSkeletonTable recipeSkeletons;
        private final String generationId;
        private final Object generationToken;

        private CatalogGeneration(ItemTypeTable itemTypes,
              IngredientVariantTable ingredientVariants,
              RecipeSkeletonTable recipeSkeletons, String generationId,
              Object generationToken) {
            this.itemTypes = itemTypes;
            this.ingredientVariants = ingredientVariants;
            this.recipeSkeletons = recipeSkeletons;
            this.generationId = generationId;
            this.generationToken = generationToken;
        }

        @Nonnull public ItemTypeTable getItemTypes() { return itemTypes; }
        @Nonnull public IngredientVariantTable getIngredientVariants() {
            return ingredientVariants;
        }
        @Nonnull public RecipeSkeletonTable getRecipeSkeletons() { return recipeSkeletons; }
        @Nonnull public String getGenerationId() { return generationId; }
    }

    /** Pure-data projection used by the background symbol-table builder. */
    private static final class SymbolCatalogInput {

        private final List<SymbolRecipe> recipes;

        private SymbolCatalogInput(Collection<LogicalRecipe> recipes) {
            List<SymbolRecipe> frozen = new ArrayList<>(recipes.size());
            for (LogicalRecipe recipe : recipes) {
                frozen.add(new SymbolRecipe(recipe));
            }
            this.recipes = Collections.unmodifiableList(frozen);
        }

        private SymbolCatalogInput(List<SymbolRecipe> recipes, boolean owned) {
            this.recipes = Collections.unmodifiableList(owned ? recipes :
                  new ArrayList<>(recipes));
        }
    }

    private static final class SymbolRecipe {

        private final FrozenItemType output;
        private final List<MatcherSeed> matchers;

        private SymbolRecipe(LogicalRecipe recipe) {
            output = recipe.declaredOutputType;
            List<MatcherSeed> frozen = new ArrayList<>(recipe.candidates.size());
            for (int slot = 0; slot < recipe.candidates.size(); slot++) {
                frozen.add(new MatcherSeed(recipe.matcherKeys.get(slot),
                      recipe.candidates.get(slot)));
            }
            matchers = Collections.unmodifiableList(frozen);
        }
    }

    private static final class SymbolShard {

        private final Map<PortableResourceDescriptor, FrozenItemType> itemTypes =
              new LinkedHashMap<>();
        private final Map<MatcherKey, MatcherSeed> matchers = new LinkedHashMap<>();

        private static SymbolShard scan(SymbolCatalogInput input, int from, int to) {
            SymbolShard shard = new SymbolShard();
            shard.matchers.put(MatcherKey.EMPTY, MatcherSeed.empty());
            for (int index = from; index < to; index++) {
                SymbolRecipe recipe = input.recipes.get(index);
                shard.itemTypes.putIfAbsent(recipe.output.descriptor, recipe.output);
                for (MatcherSeed matcher : recipe.matchers) {
                    shard.matchers.putIfAbsent(matcher.key, matcher);
                    for (IngredientChoice choice : matcher.choices) {
                        if (!choice.isEmpty()) {
                            shard.itemTypes.putIfAbsent(choice.itemType.descriptor,
                                  choice.itemType);
                        }
                    }
                }
            }
            return shard;
        }
    }

    private static final class CatalogSymbols {

        private final ItemTypeTable itemTypes;
        private final IngredientVariantTable ingredientVariants;
        private final Map<MatcherKey, Integer> matcherIds;
        private final Object generationToken = new Object();

        private CatalogSymbols(ItemTypeTable itemTypes,
              IngredientVariantTable ingredientVariants,
              Map<MatcherKey, Integer> matcherIds) {
            this.itemTypes = itemTypes;
            this.ingredientVariants = ingredientVariants;
            this.matcherIds = matcherIds;
        }

        private static CatalogSymbols build(SymbolCatalogInput input) {
            return merge(Collections.singletonList(SymbolShard.scan(input, 0,
                  input.recipes.size())));
        }

        private static CompletableFuture<CatalogSymbols> buildAsync(SymbolCatalogInput input,
              Executor executor) {
            return buildAsync(input, executor, 2);
        }

        private static CompletableFuture<CatalogSymbols> buildAsync(SymbolCatalogInput input,
              Executor executor, int workerCount) {
            Objects.requireNonNull(executor, "executor");
            if (workerCount <= 0) {
                throw new IllegalArgumentException("Catalog worker count must be positive");
            }
            int shards = Math.min(workerCount, Math.max(1, input.recipes.size()));
            int baseSize = input.recipes.size() / shards;
            List<CompletableFuture<SymbolShard>> futures = new ArrayList<>(shards);
            for (int shard = 0; shard < shards; shard++) {
                int from = shard * baseSize;
                int to = shard == shards - 1 ? input.recipes.size() : from + baseSize;
                futures.add(CompletableFuture.supplyAsync(() ->
                      SymbolShard.scan(input, from, to), executor));
            }
            CompletableFuture<?>[] pending = futures.toArray(new CompletableFuture<?>[0]);
            return CompletableFuture.allOf(pending).thenApplyAsync(ignored -> {
                List<SymbolShard> completed = new ArrayList<>(futures.size());
                futures.forEach(future -> completed.add(future.join()));
                return merge(completed);
            }, executor);
        }

        private static CatalogSymbols merge(Collection<SymbolShard> shards) {
            Map<PortableResourceDescriptor, FrozenItemType> frozenItems = new TreeMap<>();
            Map<MatcherKey, MatcherSeed> frozenMatchers = new TreeMap<>();
            frozenMatchers.put(MatcherKey.EMPTY, MatcherSeed.empty());
            for (SymbolShard shard : shards) {
                shard.itemTypes.forEach(frozenItems::putIfAbsent);
                shard.matchers.forEach(frozenMatchers::putIfAbsent);
            }
            List<ItemTypeDefinition> itemDefinitions = new ArrayList<>(frozenItems.size());
            Map<PortableResourceDescriptor, Integer> itemIds = new LinkedHashMap<>();
            for (FrozenItemType item : frozenItems.values()) {
                int id = itemDefinitions.size();
                itemDefinitions.add(new ItemTypeDefinition(id, item));
                itemIds.put(item.descriptor, id);
            }
            ItemTypeTable itemTypes = new ItemTypeTable(itemDefinitions, itemIds);

            List<MatcherDefinition> matcherDefinitions = new ArrayList<>(frozenMatchers.size());
            Map<MatcherKey, Integer> matcherIds = new HashMap<>();
            MatcherSeed empty = frozenMatchers.remove(MatcherKey.EMPTY);
            matcherDefinitions.add(new MatcherDefinition(
                  IngredientVariantTable.EMPTY_MATCHER_ID, MatcherKey.EMPTY,
                  empty == null ? MatcherSeed.empty().choices : empty.choices, itemTypes));
            matcherIds.put(MatcherKey.EMPTY, IngredientVariantTable.EMPTY_MATCHER_ID);
            for (Map.Entry<MatcherKey, MatcherSeed> entry : frozenMatchers.entrySet()) {
                int id = matcherDefinitions.size();
                matcherDefinitions.add(new MatcherDefinition(id, entry.getKey(),
                      entry.getValue().choices, itemTypes));
                matcherIds.put(entry.getKey(), id);
            }
            return new CatalogSymbols(itemTypes,
                  new IngredientVariantTable(matcherDefinitions),
                  Collections.unmodifiableMap(matcherIds));
        }

        private int matcherId(MatcherKey key) {
            Integer matcherId = matcherIds.get(key);
            if (matcherId == null) {
                throw new IllegalStateException("Recipe matcher belongs to another catalog generation");
            }
            return matcherId;
        }

        private CatalogGeneration generation(Collection<CompiledRecipe> recipes) {
            RecipeSkeletonTable skeletons = new RecipeSkeletonTable(recipes);
            return new CatalogGeneration(itemTypes, ingredientVariants, skeletons,
                  generationSignature(recipes), generationToken);
        }

        private String generationSignature(Collection<CompiledRecipe> recipes) {
            StringBuilder canonical = new StringBuilder();
            for (ItemTypeDefinition itemType : itemTypes.entries) {
                canonical.append("I|").append(itemType.itemTypeId).append('|')
                      .append(itemType.descriptor).append('\n');
            }
            for (MatcherDefinition matcher : ingredientVariants.matchers) {
                canonical.append("M|").append(matcher.matcherId).append('|')
                      .append(matcher.kind).append('|');
                for (CandidateDefinition candidate : matcher.candidates) {
                    canonical.append(candidate.candidateId).append('@')
                          .append(candidate.itemTypeId).append(';');
                }
                canonical.append('\n');
            }
            canonical.append("R|").append(compiledCatalogSignature(recipes));
            return sha256(canonical.toString());
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
        private final CatalogGeneration generation;
        private final Map<String, QIOWorkbenchRecipePattern> patterns;

        private Snapshot(Map<ResourceLocation, CompiledRecipe> recipes, List<String> diagnostics,
              int maximumCachedPatterns, CatalogGeneration generation) {
            this.recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
            this.recipeIds = Collections.unmodifiableList(new ArrayList<>(recipes.keySet()));
            this.recipeIdSet = Collections.unmodifiableSet(new LinkedHashSet<>(recipes.keySet()));
            this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
            this.generation = Objects.requireNonNull(generation, "generation");
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
            structuralSignature = sha256(compiledCatalogSignature(recipes.values()) + '|' +
                  generation.getGenerationId());
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

        @Nullable
        private QIOWorkbenchConfiguration.EncodedPattern normalizeEncodedPattern(
              @Nonnull ResourceLocation recipeId, @Nonnull List<ItemStack> selectedGrid,
              @Nonnull Predicate<List<ItemStack>> validator) {
            CompiledRecipe recipe = recipes.get(Objects.requireNonNull(recipeId,
                  "recipeId"));
            return recipe == null ? null : recipe.normalizeEncodedPattern(selectedGrid,
                  validator);
        }

        @Nonnull
        public CatalogGeneration getGeneration() {
            return generation;
        }

        @Nonnull
        public MatcherDefinition getMatcher(@Nonnull IngredientDefinition ingredient) {
            IngredientDefinition checked = Objects.requireNonNull(ingredient, "ingredient");
            if (checked.generationToken != generation.generationToken) {
                throw new IllegalArgumentException(
                      "Ingredient matcher belongs to another catalog generation");
            }
            return generation.ingredientVariants.get(checked.getMatcherId());
        }

        @Nonnull
        public List<CandidateDefinition> getCandidates(
              @Nonnull IngredientDefinition ingredient) {
            return getMatcher(ingredient).getCandidates();
        }

        @Nonnull
        public List<String> getCandidateIds(@Nonnull RecipeDefinition recipe, int slot) {
            Objects.requireNonNull(recipe, "recipe");
            if (recipe.generationToken != generation.generationToken) {
                throw new IllegalArgumentException(
                      "Recipe skeleton belongs to another catalog generation");
            }
            if (slot < 0 || slot >= recipe.getIngredients().size()) {
                return Collections.emptyList();
            }
            return getMatcher(recipe.getIngredients().get(slot)).getCandidateIds();
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
        private final int outputItemTypeId;
        private final int outputAmount;
        private final RecipeLayout layout;
        private final boolean shaped;
        private final int width;
        private final int height;
        private final String signature;
        private final List<IngredientDefinition> ingredients;
        private final Map<Integer, Integer> condensedInputs;
        private final Object generationToken;

        private RecipeDefinition(ResourceLocation recipeId,
              PortableResourceDescriptor output, int outputItemTypeId, int outputAmount,
              RecipeLayout layout,
              int width, int height, String signature,
              List<IngredientDefinition> ingredients, Object generationToken) {
            this.recipeId = recipeId;
            this.output = output;
            this.outputItemTypeId = outputItemTypeId;
            this.outputAmount = outputAmount;
            this.layout = Objects.requireNonNull(layout, "layout");
            this.shaped = layout == RecipeLayout.SHAPED;
            this.width = width;
            this.height = height;
            this.signature = signature;
            this.ingredients = Collections.unmodifiableList(new ArrayList<>(ingredients));
            this.generationToken = generationToken;
            Map<Integer, Integer> condensed = new LinkedHashMap<>();
            for (IngredientDefinition ingredient : ingredients) {
                if (!ingredient.isEmpty()) {
                    condensed.merge(ingredient.getMatcherId(), 1, Math::addExact);
                }
            }
            condensedInputs = Collections.unmodifiableMap(condensed);
        }

        @Nonnull public ResourceLocation getRecipeId() { return recipeId; }
        @Nonnull public PortableResourceDescriptor getOutput() { return output; }
        public int getOutputItemTypeId() { return outputItemTypeId; }
        public int getOutputAmount() { return outputAmount; }
        @Nonnull public RecipeLayout getLayout() { return layout; }
        public boolean isShaped() { return shaped; }
        public boolean isShapeless() { return layout == RecipeLayout.SHAPELESS; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        @Nonnull public String getSignature() { return signature; }
        @Nonnull public List<IngredientDefinition> getIngredients() { return ingredients; }
        /** Sparse logical entries; shapeless entries are occurrence-ordered and have no slot identity. */
        @Nonnull
        public List<IngredientDefinition> getLogicalIngredients() {
            List<IngredientDefinition> result = new ArrayList<>();
            for (IngredientDefinition ingredient : ingredients) {
                if (!ingredient.isEmpty()) result.add(ingredient);
            }
            return Collections.unmodifiableList(result);
        }
        @Nonnull public Map<Integer, Integer> getCondensedInputs() { return condensedInputs; }
    }

    public static final class IngredientDefinition {

        private final int slot;
        private final int matcherId;
        private final Object generationToken;

        private IngredientDefinition(int slot, int matcherId, Object generationToken) {
            this.slot = slot;
            this.matcherId = matcherId;
            this.generationToken = generationToken;
        }

        public int getSlot() { return slot; }
        public int getMatcherId() { return matcherId; }
        public boolean isEmpty() {
            return matcherId == IngredientVariantTable.EMPTY_MATCHER_ID;
        }
    }

    public static final class CandidateDefinition {

        private final String candidateId;
        private final int itemTypeId;
        private final ItemTypeDefinition itemType;
        private final PortableResourceDescriptor resource;
        private final long amount;
        private final boolean virtualFluid;

        private CandidateDefinition(String candidateId, ItemTypeDefinition itemType,
              PortableResourceDescriptor resource, long amount, boolean virtualFluid) {
            this.candidateId = candidateId;
            this.itemType = itemType;
            itemTypeId = itemType.getItemTypeId();
            this.resource = resource;
            this.amount = amount;
            this.virtualFluid = virtualFluid;
        }

        @Nonnull public String getCandidateId() { return candidateId; }
        public int getItemTypeId() { return itemTypeId; }
        @Nonnull public ItemStack getDisplayStack() { return itemType.getDisplayStack(); }
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
        private final FrozenItemType declaredOutputType;
        private final int declaredOutputAmount;
        private final List<List<IngredientChoice>> candidates;
        private final List<MatcherKey> matcherKeys;
        private final RecipeLayout layout;
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
              ItemStack declaredOutput, GridCandidates grid,
              long capturedPriority, int maxVariants, int maxRetained) {
            this.world = world;
            this.recipe = recipe;
            this.recipeId = recipeId;
            this.declaredOutput = PortableResourceDescriptor.itemIgnoringCapabilities(declaredOutput);
            declaredOutputType = FrozenItemType.capture(declaredOutput);
            this.declaredOutputAmount = declaredOutput.getCount();
            List<List<IngredientChoice>> copied = new ArrayList<>(grid.candidates.size());
            for (List<IngredientChoice> slot : grid.candidates) {
                copied.add(Collections.unmodifiableList(new ArrayList<>(slot)));
            }
            this.candidates = Collections.unmodifiableList(copied);
            List<MatcherKey> copiedKeys = new ArrayList<>(grid.matchers.size());
            for (MatcherSeed matcher : grid.matchers) {
                copiedKeys.add(matcher.key);
            }
            matcherKeys = Collections.unmodifiableList(copiedKeys);
            layout = grid.layout;
            shaped = layout == RecipeLayout.SHAPED;
            width = shaped ? ((IShapedRecipe) recipe).getRecipeWidth() : 0;
            height = shaped ? ((IShapedRecipe) recipe).getRecipeHeight() : 0;
            signature = sha256("qio-workbench-recipe-v2|" + structuralSignature());
            this.capturedPriority = capturedPriority;
            this.maxVariants = maxVariants;
            this.maxRetained = maxRetained;
        }

        @Nullable
        private CompiledRecipe compile(CatalogSymbols symbols) {
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
            return new CompiledRecipe(definition(symbols), symbols.ingredientVariants,
                  baseline, outputs,
                  Objects.requireNonNull(candidateOutputProfiles,
                        "candidate output profiles"), signature, maxVariants,
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
            String prefix = "v2";
            String layoutCode = layout.name().toLowerCase(java.util.Locale.ROOT);
            if (parts.length < 2 || !prefix.equals(parts[0]) ||
                  !layoutCode.equals(parts[1])) {
                return null;
            }
            List<IngredientChoice> grid = emptyGrid();
            try {
                List<Integer> active = activeCandidateSlots(candidates);
                if (parts.length != active.size() + 2) return null;
                for (int index = 0; index < active.size(); index++) {
                    int slot = active.get(index);
                    int candidateIndex = Integer.parseInt(parts[index + 2], 36);
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

        private RecipeDefinition definition(CatalogSymbols symbols) {
            List<IngredientDefinition> ingredients = new ArrayList<>(9);
            for (int slot = 0; slot < matcherKeys.size(); slot++) {
                ingredients.add(new IngredientDefinition(slot,
                      symbols.matcherId(matcherKeys.get(slot)), symbols.generationToken));
            }
            return new RecipeDefinition(recipeId, declaredOutput,
                  symbols.itemTypes.getId(declaredOutput), declaredOutputAmount,
                  layout, width, height, signature, ingredients, symbols.generationToken);
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
                if (!PortableResourceDescriptor.itemIgnoringCapabilities(result).equals(declaredOutput) ||
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
                PortableResourceDescriptor resultResource =
                      PortableResourceDescriptor.itemIgnoringCapabilities(result);
                routeBuilder.output(resultResource, result.getCount());
                expectedOutputs.put(resultResource, (long) result.getCount());
                NonNullList<ItemStack> remaining = recipe.getRemainingItems(inventory);
                for (int slot = 0; slot < remaining.size(); slot++) {
                    ItemStack remainder = concreteStack(remaining.get(slot));
                    if (slot < grid.size() && grid.get(slot).isVirtualFluid()) {
                        continue;
                    }
                    if (remainder != null && !remainder.isEmpty()) {
                        routeBuilder.output(PortableResourceDescriptor.itemIgnoringCapabilities(remainder),
                              remainder.getCount());
                        expectedOutputs.merge(PortableResourceDescriptor.itemIgnoringCapabilities(remainder),
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
                !PortableResourceDescriptor.itemIgnoringCapabilities(result).equals(declaredOutput) ||
                result.getCount() != declaredOutputAmount) return null;
            Map<PortableResourceDescriptor, Long> outputs = new LinkedHashMap<>();
                outputs.put(PortableResourceDescriptor.itemIgnoringCapabilities(result),
                      (long) result.getCount());
            NonNullList<ItemStack> remaining = recipe.getRemainingItems(inventory);
            for (int slot = 0; slot < remaining.size(); slot++) {
                ItemStack remainder = concreteStack(remaining.get(slot));
                if (slot < grid.size() && grid.get(slot).isVirtualFluid()) continue;
                if (remainder != null && !remainder.isEmpty()) {
                    outputs.merge(PortableResourceDescriptor.itemIgnoringCapabilities(remainder),
                          (long) remainder.getCount(), Math::addExact);
                }
            }
            return outputs;
        }

        private String variantId(List<IngredientChoice> grid) {
            StringBuilder encoded = new StringBuilder("v2.")
                  .append(layout.name().toLowerCase(java.util.Locale.ROOT));
            for (int slot : activeCandidateSlots(candidates)) {
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
                  .append(recipe.getClass().getName()).append('|').append(layout).append('|')
                  .append(declaredOutput)
                  .append('@').append(declaredOutputAmount).append('|');
            for (int slot = 0; slot < candidates.size(); slot++) {
                if (candidates.get(slot).size() == 1 && candidates.get(slot).get(0).isEmpty()) {
                    continue;
                }
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
        private final RecipeLayout layout;
        private final int[] matcherIds;
        private final IngredientVariantTable ingredientVariants;
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
              IngredientVariantTable ingredientVariants,
              List<IngredientChoice> baselineGrid,
              Map<PortableResourceDescriptor, Long> baselineOutputs,
              List<Map<String, Map<PortableResourceDescriptor, Long>>>
                    candidateOutputProfiles,
              String structuralSignature, int maxVariants, int maxRetained,
              int combinationCost) {
            this.definition = Objects.requireNonNull(definition, "definition");
            layout = definition.getLayout();
            this.ingredientVariants = Objects.requireNonNull(ingredientVariants,
                  "ingredientVariants");
            matcherIds = new int[definition.getIngredients().size()];
            for (int slot = 0; slot < matcherIds.length; slot++) {
                matcherIds[slot] = definition.getIngredients().get(slot).getMatcherId();
            }
            candidates = Collections.unmodifiableList(new MatcherGrid(ingredientVariants,
                  matcherIds));
            List<IngredientChoice> sharedBaseline = new ArrayList<>(baselineGrid.size());
            for (int slot = 0; slot < baselineGrid.size(); slot++) {
                IngredientChoice original = baselineGrid.get(slot);
                IngredientChoice shared = null;
                for (IngredientChoice choice : candidates.get(slot)) {
                    if (choice.candidateId().equals(original.candidateId())) {
                        shared = choice;
                        break;
                    }
                }
                if (shared == null) {
                    throw new IllegalStateException(
                          "Baseline candidate is absent from its shared matcher");
                }
                sharedBaseline.add(shared);
            }
            this.baselineGrid = Collections.unmodifiableList(sharedBaseline);
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

        private static final class MatcherGrid extends AbstractList<List<IngredientChoice>> {

            private final IngredientVariantTable variants;
            private final int[] matcherIds;

            private MatcherGrid(IngredientVariantTable variants, int[] matcherIds) {
                this.variants = variants;
                this.matcherIds = matcherIds;
            }

            @Override
            public List<IngredientChoice> get(int index) {
                return variants.choices(matcherIds[index]);
            }

            @Override
            public int size() {
                return matcherIds.length;
            }
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
            String layoutCode = layout.name().toLowerCase(java.util.Locale.ROOT);
            if (parts.length < 2 || !"v2".equals(parts[0]) ||
                  !layoutCode.equals(parts[1])) return null;
            List<IngredientChoice> grid = emptyGrid();
            try {
                List<Integer> active = activeCandidateSlots(candidates);
                if (parts.length != active.size() + 2) return null;
                for (int index = 0; index < active.size(); index++) {
                    int slot = active.get(index);
                    int candidateIndex = Integer.parseInt(parts[index + 2], 36);
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
            StringBuilder encoded = new StringBuilder("v2.")
                  .append(layout.name().toLowerCase(java.util.Locale.ROOT));
            for (int slot : activeCandidateSlots(candidates)) {
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

        @Nullable
        private QIOWorkbenchConfiguration.EncodedPattern normalizeEncodedPattern(
              @Nonnull List<ItemStack> selectedGrid,
              @Nonnull Predicate<List<ItemStack>> validator) {
            Objects.requireNonNull(selectedGrid, "selectedGrid");
            Objects.requireNonNull(validator, "validator");
            if (selectedGrid.size() != 9) return null;

            List<ItemStack> selectedInputs = new ArrayList<>();
            List<Integer> selectedPositions = new ArrayList<>();
            for (int selectedSlot = 0; selectedSlot < selectedGrid.size(); selectedSlot++) {
                ItemStack selected = selectedGrid.get(selectedSlot);
                if (selected == null || selected.isEmpty()) continue;
                ItemStack normalized = withoutSerializedCapabilities(selected);
                selectedInputs.add(normalized);
                selectedPositions.add(selectedSlot);
            }

            List<NormalizationSlot> slots = new ArrayList<>();
            for (int slot = 0; slot < candidates.size(); slot++) {
                List<NormalizationOption> options = new ArrayList<>();
                List<IngredientChoice> choices = candidates.get(slot);
                boolean hasIngredient = false;
                for (int choiceIndex = 0; choiceIndex < choices.size(); choiceIndex++) {
                    IngredientChoice choice = choices.get(choiceIndex);
                    if (choice.isEmpty()) continue;
                    hasIngredient = true;
                    ItemStack candidate = choice.matchingStack();
                    for (int inputIndex = 0; inputIndex < selectedInputs.size(); inputIndex++) {
                        ItemStack selected = selectedInputs.get(inputIndex);
                        if (definition.isShaped() && selectedPositions.get(inputIndex) != slot) {
                            continue;
                        }
                        boolean exact;
                        exact = sameRecipeSelectionItem(selected, candidate);
                        if (exact || sameRecipeSelectionItem(selected, candidate)) {
                            options.add(new NormalizationOption(inputIndex, choice,
                                  exact ? 0 : 1, choiceIndex));
                        }
                    }
                }
                if (!hasIngredient) continue;
                if (options.isEmpty()) return null;
                options.sort(Comparator.comparingInt(
                      (NormalizationOption option) -> option.penalty)
                      .thenComparingInt(option -> option.inputIndex)
                      .thenComparingInt(option -> option.choiceIndex));
                slots.add(new NormalizationSlot(slot, options));
            }
            if (slots.size() != selectedInputs.size()) return null;
            slots.sort(Comparator.comparingInt(
                  (NormalizationSlot slot) -> slot.options.size())
                  .thenComparingInt(slot -> slot.logicalSlot));

            int maximumSteps = maxVariants > Integer.MAX_VALUE / 9 ? Integer.MAX_VALUE :
                  maxVariants * 9;
            NormalizationSearch search = new NormalizationSearch(maximumSteps);
            normalizeEncodedPattern(0, slots, emptyGrid(),
                  new boolean[selectedInputs.size()], 0, search, validator);
            if (search.bestGrid == null) return null;
            return new QIOWorkbenchConfiguration.EncodedPattern(UUID.randomUUID(),
                  definition.getRecipeId(), definition.getSignature(), definition.getOutput(),
                  definition.getOutputAmount(), patternLayout(definition.getLayout()),
                  encodedGrid(matchingGrid(search.bestGrid), definition.getLayout()));
        }

        private void normalizeEncodedPattern(int depth, List<NormalizationSlot> slots,
              List<IngredientChoice> grid, boolean[] usedInputs, int penalty,
              NormalizationSearch search, Predicate<List<ItemStack>> validator) {
            if (search.attempts >= maxVariants || search.bestPenalty == 0 ||
                search.steps >= search.maximumSteps ||
                search.bestGrid != null && penalty >= search.bestPenalty) {
                return;
            }
            if (depth >= slots.size()) {
                search.attempts++;
                if (createPattern(grid, candidates) != null) {
                    List<ItemStack> normalized = matchingGrid(grid);
                    if (validator.test(normalized)) {
                        search.bestPenalty = penalty;
                        search.bestGrid = new ArrayList<>(grid);
                    }
                }
                return;
            }
            NormalizationSlot slot = slots.get(depth);
            for (NormalizationOption option : slot.options) {
                if (search.steps++ >= search.maximumSteps) return;
                if (usedInputs[option.inputIndex]) continue;
                usedInputs[option.inputIndex] = true;
                grid.set(slot.logicalSlot, option.choice);
                normalizeEncodedPattern(depth + 1, slots, grid, usedInputs,
                      penalty + option.penalty, search, validator);
                grid.set(slot.logicalSlot, IngredientChoice.empty());
                usedInputs[option.inputIndex] = false;
                if (search.attempts >= maxVariants || search.bestPenalty == 0) return;
            }
        }

        private static final class NormalizationOption {

            private final int inputIndex;
            private final IngredientChoice choice;
            private final int penalty;
            private final int choiceIndex;

            private NormalizationOption(int inputIndex, IngredientChoice choice,
                  int penalty, int choiceIndex) {
                this.inputIndex = inputIndex;
                this.choice = choice;
                this.penalty = penalty;
                this.choiceIndex = choiceIndex;
            }
        }

        private static final class NormalizationSlot {

            private final int logicalSlot;
            private final List<NormalizationOption> options;

            private NormalizationSlot(int logicalSlot,
                  List<NormalizationOption> options) {
                this.logicalSlot = logicalSlot;
                this.options = options;
            }
        }

        private static final class NormalizationSearch {

            private final int maximumSteps;
            private int attempts;
            private int steps;
            private int bestPenalty = Integer.MAX_VALUE;
            @Nullable private List<IngredientChoice> bestGrid;

            private NormalizationSearch(int maximumSteps) {
                this.maximumSteps = maximumSteps;
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
                    if (sameRecipeSelectionItem(encoded, candidate.matchingStack())) {
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
                  definition.getOutputAmount(), patternLayout(definition.getLayout()),
                  encodedGrid(matchingGrid(baselineGrid), definition.getLayout()));
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

    /** Capability-neutral key used only while selecting a workbench recipe. */
    private static final class RecipeItemKey {

        private final String registryName;
        private final int metadata;
        @Nullable private final NBTTagCompound tag;
        private final int hashCode;

        private RecipeItemKey(PortableResourceDescriptor descriptor) {
            if (descriptor.getKind() != PortableResourceDescriptor.Kind.ITEM) {
                throw new IllegalArgumentException(
                      "Only item outputs can select workbench recipes");
            }
            registryName = descriptor.getRegistryName();
            metadata = descriptor.getMetadata();
            tag = descriptor.getTag();
            hashCode = Objects.hash(registryName, metadata, tag);
        }

        /** Builds the same key without serializing ForgeCaps from the stack. */
        private RecipeItemKey(ItemStack stack) {
            this(PortableResourceDescriptor.itemIgnoringCapabilities(stack));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof RecipeItemKey key && metadata == key.metadata &&
                  registryName.equals(key.registryName) && Objects.equals(tag, key.tag);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class OutputSelection {

        private final PortableResourceDescriptor output;
        private final List<CachedRecipe> recipes;

        private OutputSelection(PortableResourceDescriptor output,
              List<CachedRecipe> recipes) {
            this.output = Objects.requireNonNull(output, "output");
            this.recipes = Objects.requireNonNull(recipes, "recipes");
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
        private final Map<RecipeItemKey, List<PortableResourceDescriptor>> outputsBySelectionKey;
        private final Map<RecipeItemKey, List<CachedRecipe>> recipesByInputSelectionKey;
        private final Map<ResourceLocation, CachedRecipe> recipesById;
        /** Immutable fallback order; avoid copying the entire index for every lookup miss. */
        private final List<CachedRecipe> allRecipes;
        private final int recipeCount;
        private final CatalogGeneration generation;
        private final QIOForgeRecipeData forgeData;

        private RecipeOutputIndex(
              Map<PortableResourceDescriptor, List<CachedRecipe>> recipesByOutput,
              Map<ResourceLocation, CachedRecipe> recipesById, int recipeCount,
              CatalogGeneration generation, QIOForgeRecipeData forgeData) {
            this.recipesByOutput = recipesByOutput;
            outputsBySelectionKey = buildOutputSelectionIndex(recipesByOutput.keySet());
            this.recipesById = recipesById;
            recipesByInputSelectionKey = buildInputSelectionIndex(recipesById.values());
            allRecipes = Collections.unmodifiableList(new ArrayList<>(recipesById.values()));
            this.recipeCount = recipeCount;
            this.generation = generation;
            this.forgeData = Objects.requireNonNull(forgeData, "forgeData");
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
            CatalogSymbols symbols = CatalogSymbols.build(new SymbolCatalogInput(
                  builder.recipes.values()));
            Map<ResourceLocation, CompiledRecipe> compiledRecipes = new LinkedHashMap<>();
            for (LogicalRecipe logical : builder.recipes.values()) {
                CompiledRecipe compiled;
                try {
                    compiled = logical.compile(symbols);
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (compiled == null) continue;
                compiledRecipes.put(compiled.definition.getRecipeId(), compiled);
            }
            return fromCompiled(symbols, compiledRecipes);
        }

        @Nonnull
        static RecipeOutputIndex empty() {
            CatalogSymbols symbols = CatalogSymbols.build(new SymbolCatalogInput(
                  Collections.emptyList()));
            return fromCompiled(symbols, Collections.emptyMap());
        }

        @Nonnull
        static Capture beginCapture(@Nullable World world,
              @Nonnull Collection<? extends IRecipe> recipes,
              @Nonnull BuildListener listener) {
            return new Capture(world, recipes, listener, false, false);
        }

        @Nonnull
        static Capture beginGlobalCapture(@Nullable World world,
              @Nonnull Collection<? extends IRecipe> recipes,
              @Nonnull BuildListener listener) {
            return new Capture(world, recipes, listener, true, false);
        }

        @Nonnull
        static Capture beginGlobalCaptureStable(@Nullable World world,
              @Nonnull List<? extends IRecipe> recipes,
              @Nonnull BuildListener listener) {
            return new Capture(world, recipes, listener, true, true);
        }

        @Nonnull
        private static RecipeOutputIndex fromCompiled(CatalogSymbols symbols,
              Map<ResourceLocation, CompiledRecipe> compiledRecipes) {
            return fromCompiled(symbols, compiledRecipes, QIOForgeRecipeData.empty());
        }

        private static RecipeOutputIndex fromCompiled(CatalogSymbols symbols,
              Map<ResourceLocation, CompiledRecipe> compiledRecipes,
              QIOForgeRecipeData forgeData) {
            return mergeIndexShards(symbols, Collections.singletonList(
                  IndexShard.scan(new ArrayList<>(compiledRecipes.values()), 0,
                        compiledRecipes.size())), forgeData);
        }

        private static CompletableFuture<RecipeOutputIndex> fromCompiledAsync(
              CatalogSymbols symbols, Collection<CompiledRecipe> compiledRecipes,
              Executor executor, int workerCount, QIOForgeRecipeData forgeData) {
            Objects.requireNonNull(executor, "executor");
            if (workerCount <= 0) {
                throw new IllegalArgumentException("Catalog worker count must be positive");
            }
            return CompletableFuture.supplyAsync(() -> Collections.unmodifiableList(
                  new ArrayList<>(compiledRecipes)), executor).thenCompose(ordered -> {
                int shards = Math.min(workerCount, Math.max(1, ordered.size()));
                int baseSize = ordered.size() / shards;
                List<CompletableFuture<IndexShard>> futures = new ArrayList<>(shards);
                for (int shard = 0; shard < shards; shard++) {
                    int from = shard * baseSize;
                    int to = shard == shards - 1 ? ordered.size() : from + baseSize;
                    futures.add(CompletableFuture.supplyAsync(() ->
                          IndexShard.scan(ordered, from, to), executor));
                }
                CompletableFuture<?>[] pending = futures.toArray(
                      new CompletableFuture<?>[0]);
                return CompletableFuture.allOf(pending).thenApplyAsync(ignored -> {
                    List<IndexShard> completed = new ArrayList<>(futures.size());
                    futures.forEach(future -> completed.add(future.join()));
                    return mergeIndexShards(symbols, completed, forgeData);
                }, executor);
            });
        }

        private static RecipeOutputIndex mergeIndexShards(CatalogSymbols symbols,
              List<IndexShard> shards, QIOForgeRecipeData forgeData) {
            Map<PortableResourceDescriptor, List<CachedRecipe>> grouped =
                  new LinkedHashMap<>();
            Map<ResourceLocation, CachedRecipe> byId = new LinkedHashMap<>();
            List<CompiledRecipe> compiledRecipes = new ArrayList<>();
            for (IndexShard shard : shards) {
                for (Map.Entry<PortableResourceDescriptor, List<CachedRecipe>> entry :
                      shard.recipesByOutput.entrySet()) {
                    grouped.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                          .addAll(entry.getValue());
                }
                for (Map.Entry<ResourceLocation, CachedRecipe> entry :
                      shard.recipesById.entrySet()) {
                    byId.put(entry.getKey(), entry.getValue());
                    compiledRecipes.add(entry.getValue().compiled);
                }
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
                  Collections.unmodifiableMap(new LinkedHashMap<>(byId)), byId.size(),
                  symbols.generation(compiledRecipes), forgeData);
        }

        private static final class IndexShard {

            private final Map<PortableResourceDescriptor, List<CachedRecipe>> recipesByOutput =
                  new LinkedHashMap<>();
            private final Map<ResourceLocation, CachedRecipe> recipesById =
                  new LinkedHashMap<>();

            private static IndexShard scan(List<CompiledRecipe> recipes, int from, int to) {
                IndexShard shard = new IndexShard();
                for (int index = from; index < to; index++) {
                    CompiledRecipe compiled = recipes.get(index);
                    CachedRecipe cached = new CachedRecipe(compiled);
                    shard.recipesByOutput.computeIfAbsent(compiled.definition.getOutput(),
                          ignored -> new ArrayList<>()).add(cached);
                    shard.recipesById.put(compiled.definition.getRecipeId(), cached);
                }
                return shard;
            }
        }

        @Nonnull
        private List<CachedRecipe> recipesFor(
              @Nonnull PortableResourceDescriptor output) {
            List<CachedRecipe> recipes = recipesByOutput.get(output);
            return recipes == null ? Collections.emptyList() : recipes;
        }

        /** Resolves a player grid against frozen matcher variants, with exact registry order. */
        @Nullable
        private QIOWorkbenchConfiguration.EncodedPattern resolveEncodedPattern(
              @Nonnull World world, @Nonnull List<ItemStack> selectedGrid,
              @Nullable ResourceLocation preferredRecipeId) {
            Set<ResourceLocation> visited = new LinkedHashSet<>();
            if (preferredRecipeId != null) {
                CachedRecipe preferred = recipesById.get(preferredRecipeId);
                QIOWorkbenchConfiguration.EncodedPattern result =
                      resolveEncodedPattern(world, selectedGrid, preferred);
                if (result != null) return result;
                visited.add(preferredRecipeId);
            }
            List<CachedRecipe> candidates = indexedInputCandidates(selectedGrid);
            for (CachedRecipe candidate : candidates) {
                ResourceLocation recipeId = candidate.compiled.definition.getRecipeId();
                if (!visited.add(recipeId)) continue;
                QIOWorkbenchConfiguration.EncodedPattern result =
                      resolveEncodedPattern(world, selectedGrid, candidate);
                if (result != null) return result;
            }
            return null;
        }

        @Nonnull
        private List<CachedRecipe> indexedInputCandidates(List<ItemStack> selectedGrid) {
            List<CachedRecipe> best = null;
            Set<RecipeItemKey> keys = new LinkedHashSet<>();
            for (ItemStack selected : selectedGrid) {
                if (selected == null || selected.isEmpty()) continue;
                try {
                    // This index intentionally excludes ForgeCaps. Do not serialize the stack
                    // here: attached capability providers are allowed to fail during NBT export.
                    keys.add(new RecipeItemKey(selected));
                } catch (RuntimeException | LinkageError ignored) {
                    return allRecipes;
                }
            }
            for (RecipeItemKey key : keys) {
                List<CachedRecipe> indexed = recipesByInputSelectionKey.get(key);
                if (indexed == null || indexed.isEmpty()) return Collections.emptyList();
                if (best == null || indexed.size() < best.size()) best = indexed;
            }
            return best == null ? allRecipes : best;
        }

        @Nullable
        private QIOWorkbenchConfiguration.EncodedPattern resolveEncodedPattern(
              @Nonnull World world, @Nonnull List<ItemStack> selectedGrid,
              @Nullable CachedRecipe cached) {
            if (cached == null) return null;
            CompiledRecipe compiled = cached.compiled;
            ResourceLocation recipeId = compiled.definition.getRecipeId();
            IRecipe recipe = ForgeRegistries.RECIPES.getValue(recipeId);
            if (recipe == null || recipe.isDynamic() || recipe.getRegistryName() == null) {
                return null;
            }
            return compiled.normalizeEncodedPattern(selectedGrid, normalized ->
                  validateNormalizedRecipe(world, recipe, compiled.definition, normalized));
        }

        @Nullable
        private OutputSelection resolveOutputSelection(@Nonnull ItemStack requested) {
            ItemStack checked = Objects.requireNonNull(requested, "requested");
            RecipeItemKey selectionKey = new RecipeItemKey(checked);
            try {
                PortableResourceDescriptor exact =
                      PortableResourceDescriptor.itemIgnoringCapabilities(checked);
                List<CachedRecipe> exactRecipes = recipesByOutput.get(exact);
                if (exactRecipes != null && !exactRecipes.isEmpty()) {
                    return new OutputSelection(exact, exactRecipes);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // Fall through to the capability-neutral output index.
            }
            List<PortableResourceDescriptor> variants = outputsBySelectionKey.get(
                  selectionKey);
            if (variants == null || variants.isEmpty()) return null;
            List<PortableResourceDescriptor> neutral = new ArrayList<>(1);
            for (PortableResourceDescriptor variant : variants) {
                if (!hasDescriptorCapabilities(variant)) neutral.add(variant);
            }
            if (neutral.size() != 1) {
                throw new IllegalArgumentException(
                      "Workbench target matches multiple capability variants");
            }
            PortableResourceDescriptor normalized = neutral.get(0);
            return new OutputSelection(normalized, recipesByOutput.get(normalized));
        }

        private static Map<RecipeItemKey, List<PortableResourceDescriptor>>
              buildOutputSelectionIndex(Collection<PortableResourceDescriptor> outputs) {
            Map<RecipeItemKey, List<PortableResourceDescriptor>> mutable =
                  new LinkedHashMap<>();
            for (PortableResourceDescriptor output : outputs) {
                if (output.getKind() != PortableResourceDescriptor.Kind.ITEM) continue;
                mutable.computeIfAbsent(new RecipeItemKey(output), ignored ->
                      new ArrayList<>()).add(output);
            }
            Map<RecipeItemKey, List<PortableResourceDescriptor>> immutable =
                  new LinkedHashMap<>();
            mutable.forEach((key, variants) -> immutable.put(key,
                  Collections.unmodifiableList(new ArrayList<>(variants))));
            return Collections.unmodifiableMap(immutable);
        }

        private static Map<RecipeItemKey, List<CachedRecipe>> buildInputSelectionIndex(
              Collection<CachedRecipe> recipes) {
            Map<RecipeItemKey, List<CachedRecipe>> mutable = new LinkedHashMap<>();
            for (CachedRecipe cached : recipes) {
                Set<RecipeItemKey> seen = new LinkedHashSet<>();
                for (List<IngredientChoice> slot : cached.compiled.candidates) {
                    for (IngredientChoice choice : slot) {
                        if (choice.isEmpty()) continue;
                        RecipeItemKey key;
                        try {
                            key = new RecipeItemKey(choice.matchingStack());
                        } catch (RuntimeException | LinkageError ignored) {
                            continue;
                        }
                        if (seen.add(key)) {
                            mutable.computeIfAbsent(key, ignored -> new ArrayList<>())
                                  .add(cached);
                        }
                    }
                }
            }
            Map<RecipeItemKey, List<CachedRecipe>> immutable = new LinkedHashMap<>();
            mutable.forEach((key, values) -> immutable.put(key,
                  Collections.unmodifiableList(new ArrayList<>(values))));
            return Collections.unmodifiableMap(immutable);
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
            return new Snapshot(selected, diagnostics, DEFAULT_PATTERN_CACHE_SIZE,
                  generation);
        }

        /** Returns active pattern IDs that no longer match this immutable catalog generation. */
        @Nonnull
        List<String> invalidPatternIds(@Nonnull QIOWorkbenchConfiguration configuration) {
            Objects.requireNonNull(configuration, "configuration");
            List<String> invalid = new ArrayList<>();
            for (QIOWorkbenchConfiguration.EncodedPattern pattern :
                  configuration.getEncodedPatterns()) {
                if (!isPatternValid(pattern)) {
                    invalid.add(pattern.getRecipeId().toString());
                }
            }
            return Collections.unmodifiableList(invalid);
        }

        boolean arePatternsValid(@Nonnull QIOWorkbenchConfiguration configuration) {
            Objects.requireNonNull(configuration, "configuration");
            for (QIOWorkbenchConfiguration.EncodedPattern pattern :
                  configuration.getEncodedPatterns()) {
                if (!isPatternValid(pattern)) return false;
            }
            return true;
        }

        /** Validates one encoded pattern without exposing mutable compiled recipe state. */
        boolean isPatternValid(@Nonnull QIOWorkbenchConfiguration.EncodedPattern pattern) {
            Objects.requireNonNull(pattern, "pattern");
            CachedRecipe cached = recipesById.get(pattern.getRecipeId());
            return cached != null && cached.compiled.matches(pattern);
        }

        int getRecipeCount() {
            return recipeCount;
        }

        @Nullable
        private RecipeDefinition getRecipeDefinition(@Nonnull ResourceLocation recipeId) {
            CachedRecipe recipe = recipesById.get(Objects.requireNonNull(recipeId, "recipeId"));
            return recipe == null ? null : recipe.compiled.definition;
        }

        @Nonnull
        String getGenerationId() {
            return generation.getGenerationId();
        }

        @Nonnull
        String getForgeSignature() {
            return forgeData.getStructuralSignature();
        }

        @Nonnull
        CacheData writeCache() {
            List<NBTTagCompound> itemRecords = new ArrayList<>(
                  generation.itemTypes.entries.size());
            for (ItemTypeDefinition itemType : generation.itemTypes.entries) {
                NBTTagCompound record = new NBTTagCompound();
                record.setInteger("itemTypeId", itemType.itemTypeId);
                record.setTag("descriptor", itemType.descriptor.write());
                record.setTag("stack", itemType.serializedPrototype.copy());
                itemRecords.add(record);
            }
            List<NBTTagCompound> matcherRecords = new ArrayList<>(
                  generation.ingredientVariants.matchers.size());
            Map<String, IngredientChoice> candidateById = new TreeMap<>();
            for (MatcherDefinition matcher : generation.ingredientVariants.matchers) {
                NBTTagCompound record = new NBTTagCompound();
                record.setInteger("matcherId", matcher.matcherId);
                record.setString("kind", matcher.kind.name());
                NBTTagList candidateIds = new NBTTagList();
                for (IngredientChoice choice : matcher.choices) {
                    if (choice.isEmpty()) continue;
                    candidateById.putIfAbsent(choice.candidateId(), choice);
                    candidateIds.appendTag(stringTag(choice.candidateId()));
                }
                record.setTag("candidateIds", candidateIds);
                matcherRecords.add(record);
            }
            List<NBTTagCompound> candidateRecords = new ArrayList<>(candidateById.size());
            for (Map.Entry<String, IngredientChoice> entry : candidateById.entrySet()) {
                IngredientChoice choice = entry.getValue();
                NBTTagCompound record = new NBTTagCompound();
                record.setString("candidateId", entry.getKey());
                record.setInteger("itemTypeId", generation.itemTypes.getId(
                      choice.itemType.descriptor));
                record.setTag("resource", choice.resource().write());
                record.setLong("amount", choice.amount);
                record.setBoolean("virtualFluid", choice.virtualFluid);
                candidateRecords.add(record);
            }
            Map<String, Integer> profileIds = new LinkedHashMap<>();
            List<NBTTagCompound> candidateProfileRecords = new ArrayList<>();
            for (CachedRecipe cached : recipesById.values()) {
                for (Map<String, Map<PortableResourceDescriptor, Long>> slotProfiles :
                      cached.compiled.candidateOutputProfiles) {
                    // Profile contents can contain large capability/NBT identities.  Keep the
                    // canonical form in memory for equality, but use a fixed-size key for the
                    // cache's profile table so no unbounded string reaches NBT UTF encoding.
                    String canonical = profileCanonical(slotProfiles);
                    String profileKey = sha256(canonical);
                    if (!profileIds.containsKey(profileKey)) {
                        int profileId = profileIds.size();
                        profileIds.put(profileKey, profileId);
                        NBTTagCompound profileRecord = new NBTTagCompound();
                        profileRecord.setInteger("profileId", profileId);
                        NBTTagList profileCandidates = new NBTTagList();
                        for (Map.Entry<String, Map<PortableResourceDescriptor, Long>> entry :
                              slotProfiles.entrySet()) {
                            NBTTagCompound candidate = new NBTTagCompound();
                            candidate.setString("candidateId", entry.getKey());
                            candidate.setTag("outputs", writeAmounts(entry.getValue()));
                            profileCandidates.appendTag(candidate);
                        }
                        profileRecord.setTag("candidates", profileCandidates);
                        candidateProfileRecords.add(profileRecord);
                    }
                }
            }
            List<NBTTagCompound> recipeRecords = new ArrayList<>(recipesById.size());
            List<NBTTagCompound> outputProfiles = new ArrayList<>(recipesById.size());
            for (CachedRecipe cached : recipesById.values()) {
                CompiledRecipe recipe = cached.compiled;
                RecipeDefinition definition = recipe.definition;
                NBTTagCompound record = new NBTTagCompound();
                record.setString("recipeId", definition.recipeId.toString());
                record.setTag("output", definition.output.write());
                record.setInteger("outputItemTypeId", definition.outputItemTypeId);
                record.setInteger("outputAmount", definition.outputAmount);
                record.setString("layout", definition.layout.name());
                record.setInteger("width", definition.width);
                record.setInteger("height", definition.height);
                record.setString("signature", definition.signature);
                NBTTagList ingredients = new NBTTagList();
                for (int slot : activeCandidateSlots(recipe.candidates)) {
                    NBTTagCompound ingredient = new NBTTagCompound();
                    if (definition.isShapeless()) ingredient.setInteger("occurrence", slot);
                    else ingredient.setInteger("slot", slot);
                    ingredient.setInteger("matcherId", recipe.matcherIds[slot]);
                    ingredients.appendTag(ingredient);
                }
                record.setTag("ingredients", ingredients);
                recipeRecords.add(record);

                NBTTagCompound profiles = new NBTTagCompound();
                profiles.setString("recipeId", definition.recipeId.toString());
                profiles.setString("structuralSignature", recipe.structuralSignature);
                profiles.setInteger("maxVariants", recipe.maxVariants);
                profiles.setInteger("maxRetained", recipe.maxRetained);
                profiles.setInteger("combinationCost", recipe.combinationCost);
                NBTTagList baseline = new NBTTagList();
                for (int slot : activeCandidateSlots(recipe.candidates)) {
                    NBTTagCompound value = stringTag(recipe.baselineGrid.get(slot).candidateId());
                    if (definition.isShapeless()) value.setInteger("occurrence", slot);
                    else value.setInteger("slot", slot);
                    baseline.appendTag(value);
                }
                profiles.setTag("baseline", baseline);
                profiles.setTag("baselineOutputs", writeAmounts(recipe.baselineOutputs));
                NBTTagList slots = new NBTTagList();
                for (int slot : activeCandidateSlots(recipe.candidates)) {
                    NBTTagCompound slotRecord = new NBTTagCompound();
                    if (definition.isShapeless()) slotRecord.setInteger("occurrence", slot);
                    else slotRecord.setInteger("slot", slot);
                    int profileId = profileIds.get(sha256(profileCanonical(
                          recipe.candidateOutputProfiles.get(slot))));
                    slotRecord.setInteger("profileId", profileId);
                    slots.appendTag(slotRecord);
                }
                profiles.setTag("slots", slots);
                outputProfiles.add(profiles);
            }
            List<NBTTagCompound> reverseIndexes = new ArrayList<>(recipesByOutput.size());
            for (Map.Entry<PortableResourceDescriptor, List<CachedRecipe>> entry :
                  recipesByOutput.entrySet()) {
                NBTTagCompound record = new NBTTagCompound();
                record.setTag("output", entry.getKey().write());
                NBTTagList recipes = new NBTTagList();
                entry.getValue().forEach(recipe -> recipes.appendTag(
                      stringTag(recipe.definition.recipeId.toString())));
                record.setTag("recipes", recipes);
                reverseIndexes.add(record);
            }
            return new CacheData(generation.getGenerationId(), recipeCount,
                  forgeData.writeCache(), itemRecords, candidateRecords, matcherRecords,
                  recipeRecords, outputProfiles, candidateProfileRecords, reverseIndexes);
        }

        @Nonnull
        static RecipeOutputIndex readCache(@Nonnull CacheData data) {
            CacheRestore restore = beginCacheRestore(data);
            while (!restore.process(Integer.MAX_VALUE, Long.MAX_VALUE, null, 1)) {
                Thread.yield();
            }
            return restore.finish();
        }

        @Nonnull
        static CacheRestore beginCacheRestore(@Nonnull CacheData data) {
            return new CacheRestore(data);
        }

        /** Bounded server-thread decoding followed by worker-only immutable index assembly. */
        static final class CacheRestore {

            private enum Stage {
                ITEM_TYPES,
                CANDIDATES,
                MATCHERS,
                CANDIDATE_PROFILES,
                OUTPUT_PROFILES,
                RECIPES,
                FORGE_DATA,
                INDEX,
                COMPLETE
            }

            private final CacheData data;
            private final List<ItemTypeDefinition> itemDefinitions = new ArrayList<>();
            private final Map<PortableResourceDescriptor, Integer> itemIds =
                  new LinkedHashMap<>();
            private final List<MatcherDefinition> matchers = new ArrayList<>();
            private final Map<String, IngredientChoice> candidatesById = new LinkedHashMap<>();
            private final Map<Integer, Map<String, Map<PortableResourceDescriptor, Long>>>
                  candidateProfilesById = new LinkedHashMap<>();
            private final Map<String, NBTTagCompound> profilesByRecipe = new LinkedHashMap<>();
            private final Map<ResourceLocation, CompiledRecipe> compiled =
                  new LinkedHashMap<>();
            private final QIOForgeRecipeData.CacheRestore forgeRestore;
            private Stage stage = Stage.ITEM_TYPES;
            private int itemIndex;
            private int matcherIndex;
            private int candidateIndex;
            private int candidateProfileIndex;
            private int profileIndex;
            private int recipeIndex;
            @Nullable private ItemTypeTable itemTypes;
            @Nullable private IngredientVariantTable variants;
            @Nullable private CatalogSymbols symbols;
            @Nullable private QIOForgeRecipeData forgeData;
            @Nullable private CompletableFuture<RecipeOutputIndex> indexFuture;
            @Nullable private RecipeOutputIndex result;

            private CacheRestore(CacheData data) {
                this.data = Objects.requireNonNull(data, "data");
                if (data.itemTypes.size() > 4_000_000 || data.candidates.size() > 4_000_000 ||
                    data.matchers.size() > 4_000_000 || data.recipes.size() > 4_000_000 ||
                    data.outputProfiles.size() > 4_000_000 ||
                    data.candidateProfiles.size() > 4_000_000) {
                    throw new IllegalArgumentException(
                          "QIO recipe cache exceeds structural limits");
                }
                forgeRestore = QIOForgeRecipeData.beginCacheRestore(data.forgeData);
            }

            boolean process(int maximumRecords, long maximumNanos,
                  @Nullable Executor worker, int workerCount) {
                if (maximumRecords <= 0 || maximumNanos <= 0 || workerCount <= 0) {
                    throw new IllegalArgumentException(
                          "QIO recipe cache restore slice must be positive");
                }
                long deadline = maximumNanos == Long.MAX_VALUE ? Long.MAX_VALUE :
                      saturatedAdd(System.nanoTime(), maximumNanos);
                int processed = 0;
                while (stage != Stage.COMPLETE && processed < maximumRecords &&
                      (deadline == Long.MAX_VALUE || System.nanoTime() < deadline)) {
                    switch (stage) {
                        case ITEM_TYPES -> {
                            if (itemIndex >= data.itemTypes.size()) {
                                itemTypes = new ItemTypeTable(itemDefinitions, itemIds, true);
                                stage = Stage.CANDIDATES;
                                continue;
                            }
                            restoreItemType(data.itemTypes.get(itemIndex), itemIndex);
                            itemIndex++;
                        }
                        case CANDIDATES -> {
                            if (candidateIndex >= data.candidates.size()) {
                                stage = Stage.MATCHERS;
                                continue;
                            }
                            restoreCandidate(data.candidates.get(candidateIndex++));
                        }
                        case MATCHERS -> {
                            if (matcherIndex >= data.matchers.size()) {
                                variants = new IngredientVariantTable(matchers, true);
                                symbols = new CatalogSymbols(
                                      Objects.requireNonNull(itemTypes, "item types"),
                                      variants, Collections.emptyMap());
                                stage = Stage.CANDIDATE_PROFILES;
                                continue;
                            }
                            restoreMatcher(data.matchers.get(matcherIndex), matcherIndex);
                            matcherIndex++;
                        }
                        case CANDIDATE_PROFILES -> {
                            if (candidateProfileIndex >= data.candidateProfiles.size()) {
                                stage = Stage.OUTPUT_PROFILES;
                                continue;
                            }
                            restoreCandidateProfile(
                                  data.candidateProfiles.get(candidateProfileIndex++));
                        }
                        case OUTPUT_PROFILES -> {
                            if (profileIndex >= data.outputProfiles.size()) {
                                stage = Stage.RECIPES;
                                continue;
                            }
                            NBTTagCompound profile = data.outputProfiles.get(profileIndex++);
                            String recipeId = profile.getString("recipeId");
                            if (profilesByRecipe.put(recipeId, profile) != null) {
                                throw new IllegalArgumentException(
                                      "Cached QIO output profile is duplicated");
                            }
                        }
                        case RECIPES -> {
                            if (recipeIndex >= data.recipes.size()) {
                                if (!profilesByRecipe.isEmpty() ||
                                    compiled.size() != data.recipeCount) {
                                    throw new IllegalArgumentException(
                                          "Cached QIO recipe/profile count is invalid");
                                }
                                stage = Stage.FORGE_DATA;
                                continue;
                            }
                            restoreRecipe(data.recipes.get(recipeIndex++));
                        }
                        case FORGE_DATA -> {
                            if (!forgeRestore.process(maximumRecords - processed, deadline,
                                  worker, workerCount)) {
                                return false;
                            }
                            forgeData = forgeRestore.finish();
                            stage = Stage.INDEX;
                            return false;
                        }
                        case INDEX -> {
                            CatalogSymbols activeSymbols = Objects.requireNonNull(symbols,
                                  "catalog symbols");
                            QIOForgeRecipeData activeForgeData = Objects.requireNonNull(
                                  forgeData, "Forge recipe data");
                            if (worker == null) {
                                result = validate(fromCompiled(activeSymbols, compiled,
                                      activeForgeData));
                                stage = Stage.COMPLETE;
                                continue;
                            }
                            if (indexFuture == null) {
                                indexFuture = fromCompiledAsync(activeSymbols,
                                      compiled.values(), worker,
                                      workerCount, activeForgeData)
                                      .thenApplyAsync(this::validate, worker);
                                return false;
                            }
                            if (!indexFuture.isDone()) return false;
                            result = indexFuture.join();
                            indexFuture = null;
                            stage = Stage.COMPLETE;
                            continue;
                        }
                        default -> throw new IllegalStateException(
                              "Unexpected QIO cache restore stage");
                    }
                    processed++;
                }
                return stage == Stage.COMPLETE;
            }

            @Nonnull
            RecipeOutputIndex finish() {
                if (stage != Stage.COMPLETE || result == null) {
                    throw new IllegalStateException("QIO recipe cache restore is incomplete");
                }
                return result;
            }

            void cancel() {
                forgeRestore.cancel();
                if (indexFuture != null) indexFuture.cancel(true);
            }

            private void restoreItemType(NBTTagCompound record, int index) {
                if (record.getInteger("itemTypeId") != index) {
                    throw new IllegalArgumentException(
                          "Cached QIO item IDs are not contiguous");
                }
                // Cache records contain recipe-selection data only.  Do not use the
                // regular ItemStack NBT constructor here: StellarCore and other
                // integrations may materialize ForgeCaps while restoring a stack,
                // which can both change identity and inflate the cache.
                ItemStack stack = QIORecipeStackUtils.readForRecipeSelection(
                      record.getCompoundTag("stack"));
                if (stack.isEmpty()) {
                    throw new IllegalArgumentException("Cached QIO item is unresolved");
                }
                FrozenItemType frozen = FrozenItemType.capture(stack);
                PortableResourceDescriptor expected = PortableResourceDescriptor.read(
                      record.getCompoundTag("descriptor"));
                if (!expected.equals(frozen.descriptor)) {
                    throw new IllegalArgumentException("Cached QIO item identity changed");
                }
                itemDefinitions.add(new ItemTypeDefinition(index, frozen));
                if (itemIds.put(expected, index) != null) {
                    throw new IllegalArgumentException(
                          "Cached QIO item identity is duplicated");
                }
            }

            private void restoreMatcher(NBTTagCompound record, int index) {
                if (record.getInteger("matcherId") != index) {
                    throw new IllegalArgumentException(
                          "Cached QIO matcher IDs are not contiguous");
                }
                MatcherKind kind;
                try {
                    kind = MatcherKind.valueOf(record.getString("kind"));
                } catch (RuntimeException error) {
                    throw new IllegalArgumentException(
                          "Cached QIO matcher kind is invalid", error);
                }
                NBTTagList storedCandidateIds = record.getTagList("candidateIds", 8);
                if (storedCandidateIds.tagCount() > DEFAULT_MAX_CANDIDATES_PER_INGREDIENT) {
                    throw new IllegalArgumentException(
                          "Cached QIO matcher has too many choices");
                }
                List<IngredientChoice> choices = new ArrayList<>(storedCandidateIds.tagCount());
                Set<String> seen = new LinkedHashSet<>();
                for (int choice = 0; choice < storedCandidateIds.tagCount(); choice++) {
                    String candidateId = storedCandidateIds.getStringTagAt(choice);
                    IngredientChoice selected = candidatesById.get(candidateId);
                    if (selected == null || !seen.add(candidateId)) {
                        throw new IllegalArgumentException("Cached QIO matcher candidate is invalid");
                    }
                    choices.add(selected);
                }
                if (kind == MatcherKind.EMPTY) {
                    if (!choices.isEmpty()) {
                        throw new IllegalArgumentException("Cached empty matcher has candidates");
                    }
                    choices = Collections.singletonList(IngredientChoice.empty());
                } else if (choices.isEmpty()) {
                    throw new IllegalArgumentException("Cached matcher has no candidates");
                }
                if (index == IngredientVariantTable.EMPTY_MATCHER_ID &&
                    (kind != MatcherKind.EMPTY || choices.size() != 1 ||
                     !choices.get(0).isEmpty())) {
                    throw new IllegalArgumentException("Cached empty QIO matcher is invalid");
                }
                MatcherKey key = kind == MatcherKind.EMPTY ? MatcherKey.EMPTY :
                      new MatcherKey(kind, "cache", choices);
                matchers.add(new MatcherDefinition(index, key, choices,
                      Objects.requireNonNull(itemTypes, "item types")));
            }

            private void restoreCandidate(NBTTagCompound record) {
                String candidateId = record.getString("candidateId");
                if (candidateId.isEmpty() || candidatesById.containsKey(candidateId)) {
                    throw new IllegalArgumentException("Cached QIO candidate identity is duplicated");
                }
                int itemTypeId = record.getInteger("itemTypeId");
                ItemTypeTable activeItemTypes = Objects.requireNonNull(itemTypes, "item types");
                if (itemTypeId < 0 || itemTypeId >= activeItemTypes.size()) {
                    throw new IllegalArgumentException("Cached QIO candidate item type is invalid");
                }
                long amount = record.getLong("amount");
                if (amount <= 0 || !record.hasKey("resource", NBT.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("Cached QIO candidate definition is invalid");
                }
                PortableResourceDescriptor resource = PortableResourceDescriptor.read(
                      record.getCompoundTag("resource"));
                IngredientChoice choice = IngredientChoice.fromCache(
                      activeItemTypes.get(itemTypeId),
                      resource, amount, record.getBoolean("virtualFluid"));
                if (!choice.candidateId().equals(candidateId)) {
                    throw new IllegalArgumentException("Cached QIO candidate signature changed");
                }
                candidatesById.put(candidateId, choice);
            }

            private void restoreCandidateProfile(NBTTagCompound record) {
                int profileId = record.getInteger("profileId");
                if (profileId != candidateProfilesById.size() ||
                      candidateProfilesById.containsKey(profileId)) {
                    throw new IllegalArgumentException("Cached QIO output profile ID is invalid");
                }
                NBTTagList stored = record.getTagList("candidates", 10);
                if (stored.tagCount() > DEFAULT_MAX_CANDIDATES_PER_INGREDIENT) {
                    throw new IllegalArgumentException("Cached QIO output profile is too large");
                }
                Map<String, Map<PortableResourceDescriptor, Long>> profile = new LinkedHashMap<>();
                for (int index = 0; index < stored.tagCount(); index++) {
                    NBTTagCompound candidate = stored.getCompoundTagAt(index);
                    String candidateId = candidate.getString("candidateId");
                    if (!candidatesById.containsKey(candidateId) || profile.containsKey(candidateId)) {
                        throw new IllegalArgumentException("Cached QIO output profile candidate is invalid");
                    }
                    profile.put(candidateId, readAmounts(candidate.getTagList("outputs", 10)));
                }
                candidateProfilesById.put(profileId, Collections.unmodifiableMap(profile));
            }

            private void restoreRecipe(NBTTagCompound record) {
                ItemTypeTable activeItemTypes = Objects.requireNonNull(itemTypes,
                      "item types");
                IngredientVariantTable activeVariants = Objects.requireNonNull(variants,
                      "ingredient variants");
                CatalogSymbols activeSymbols = Objects.requireNonNull(symbols,
                      "catalog symbols");
                ResourceLocation recipeId = new ResourceLocation(
                      record.getString("recipeId"));
                if (compiled.containsKey(recipeId)) {
                    throw new IllegalArgumentException("Cached QIO recipe is duplicated");
                }
                PortableResourceDescriptor output = PortableResourceDescriptor.read(
                      record.getCompoundTag("output"));
                int outputItemTypeId = record.getInteger("outputItemTypeId");
                if (outputItemTypeId < 0 || outputItemTypeId >= activeItemTypes.size() ||
                    !activeItemTypes.get(outputItemTypeId).descriptor.equals(output)) {
                    throw new IllegalArgumentException(
                          "Cached QIO recipe output ID is invalid");
                }
                RecipeLayout layout;
                try {
                    layout = RecipeLayout.valueOf(record.getString("layout"));
                } catch (RuntimeException error) {
                    throw new IllegalArgumentException("Cached QIO recipe layout is invalid", error);
                }
                int[] matcherIds = new int[9];
                NBTTagList storedIngredients = record.getTagList("ingredients", 10);
                if (storedIngredients.tagCount() > 9) {
                    throw new IllegalArgumentException("Cached QIO recipe has too many ingredients");
                }
                boolean[] seenSlots = new boolean[9];
                for (int index = 0; index < storedIngredients.tagCount(); index++) {
                    NBTTagCompound stored = storedIngredients.getCompoundTagAt(index);
                    int slot = layout == RecipeLayout.SHAPELESS ?
                          stored.getInteger("occurrence") : stored.getInteger("slot");
                    if (slot < 0 || slot >= 9 || seenSlots[slot] ||
                          !stored.hasKey("matcherId")) {
                        throw new IllegalArgumentException("Cached QIO recipe ingredient position is invalid");
                    }
                    int matcherId = stored.getInteger("matcherId");
                    if (matcherId == IngredientVariantTable.EMPTY_MATCHER_ID) {
                        throw new IllegalArgumentException("Cached QIO recipe stores an empty ingredient");
                    }
                    activeVariants.get(matcherId);
                    matcherIds[slot] = matcherId;
                    seenSlots[slot] = true;
                }
                if (layout == RecipeLayout.SHAPELESS) {
                    for (int index = 0; index < storedIngredients.tagCount(); index++) {
                        if (!seenSlots[index]) {
                            throw new IllegalArgumentException("Cached shapeless occurrences are not contiguous");
                        }
                    }
                }
                List<IngredientDefinition> ingredients = new ArrayList<>(9);
                for (int slot = 0; slot < matcherIds.length; slot++) {
                    activeVariants.get(matcherIds[slot]);
                    ingredients.add(new IngredientDefinition(slot, matcherIds[slot],
                          activeSymbols.generationToken));
                }
                RecipeDefinition definition = new RecipeDefinition(recipeId, output,
                      outputItemTypeId, positiveInt(record, "outputAmount"),
                      layout, record.getInteger("width"),
                      record.getInteger("height"), hash(record.getString("signature"),
                      "recipe signature"), ingredients, activeSymbols.generationToken);
                NBTTagCompound profile = profilesByRecipe.remove(recipeId.toString());
                if (profile == null) {
                    throw new IllegalArgumentException(
                          "Cached QIO recipe has no output profile");
                }
                List<IngredientChoice> baseline = readBaseline(profile, definition,
                      activeVariants);
                Map<PortableResourceDescriptor, Long> baselineOutputs = readAmounts(
                      profile.getTagList("baselineOutputs", 10));
                List<Map<String, Map<PortableResourceDescriptor, Long>>> candidateProfiles =
                      readOutputProfiles(profile, definition, activeVariants,
                            candidateProfilesById);
                compiled.put(recipeId, new CompiledRecipe(definition, activeVariants,
                      baseline, baselineOutputs, candidateProfiles,
                      structuralSignature(profile.getString("structuralSignature")),
                      positiveInt(profile, "maxVariants"),
                      positiveInt(profile, "maxRetained"),
                      positiveInt(profile, "combinationCost")));
            }

            private RecipeOutputIndex validate(RecipeOutputIndex restored) {
                if (!restored.getGenerationId().equals(data.generationId) ||
                    !restored.matchesReverseIndex(data.reverseIndexes)) {
                    throw new IllegalArgumentException(
                          "Cached QIO catalog signature is invalid");
                }
                return restored;
            }
        }

        private boolean matchesReverseIndex(List<NBTTagCompound> expected) {
            if (expected.size() != recipesByOutput.size()) return false;
            int outputIndex = 0;
            for (Map.Entry<PortableResourceDescriptor, List<CachedRecipe>> entry :
                  recipesByOutput.entrySet()) {
                NBTTagCompound record = expected.get(outputIndex++);
                if (!entry.getKey().equals(PortableResourceDescriptor.read(
                      record.getCompoundTag("output")))) return false;
                NBTTagList recipes = record.getTagList("recipes", 10);
                if (recipes.tagCount() != entry.getValue().size()) return false;
                for (int index = 0; index < recipes.tagCount(); index++) {
                    if (!entry.getValue().get(index).definition.recipeId.toString().equals(
                          recipes.getCompoundTagAt(index).getString("value"))) return false;
                }
            }
            return true;
        }

        private static List<IngredientChoice> readBaseline(NBTTagCompound profile,
              RecipeDefinition definition, IngredientVariantTable variants) {
            NBTTagList baseline = profile.getTagList("baseline", 10);
            if (baseline.tagCount() > 9) {
                throw new IllegalArgumentException("Cached QIO baseline is too large");
            }
            List<IngredientChoice> result = new ArrayList<>(9);
            for (int slot = 0; slot < 9; slot++) result.add(IngredientChoice.empty());
            boolean[] seen = new boolean[9];
            for (int index = 0; index < baseline.tagCount(); index++) {
                NBTTagCompound stored = baseline.getCompoundTagAt(index);
                int slot = definition.isShapeless() ? stored.getInteger("occurrence") :
                      stored.getInteger("slot");
                if (slot < 0 || slot >= 9 || seen[slot] ||
                      definition.ingredients.get(slot).isEmpty()) {
                    throw new IllegalArgumentException("Cached QIO baseline position is invalid");
                }
                String candidateId = stored.getString("value");
                List<IngredientChoice> choices = variants.choices(
                      definition.ingredients.get(slot).matcherId);
                IngredientChoice selected = null;
                for (IngredientChoice choice : choices) {
                    if (choice.candidateId().equals(candidateId)) {
                        selected = choice;
                        break;
                    }
                }
                if (selected == null) {
                    throw new IllegalArgumentException("Cached QIO baseline choice is missing");
                }
                result.set(slot, selected);
                seen[slot] = true;
            }
            if (definition.isShapeless()) {
                for (int index = 0; index < baseline.tagCount(); index++) {
                    if (!seen[index]) throw new IllegalArgumentException(
                          "Cached shapeless baseline occurrences are not contiguous");
                }
            }
            return result;
        }

        private static List<Map<String, Map<PortableResourceDescriptor, Long>>>
              readOutputProfiles(NBTTagCompound profile, RecipeDefinition definition,
              IngredientVariantTable variants,
              Map<Integer, Map<String, Map<PortableResourceDescriptor, Long>>> profileTable) {
            if (!profile.hasKey("slots", NBT.TAG_LIST)) {
                throw new IllegalArgumentException("Cached QIO output profile has no slots list");
            }
            NBTTagList slots = profile.getTagList("slots", NBT.TAG_COMPOUND);
            if (slots.tagCount() > 9) {
                throw new IllegalArgumentException("Cached QIO output profiles are too large");
            }
            List<Map<String, Map<PortableResourceDescriptor, Long>>> result =
                  new ArrayList<>(9);
            for (int slot = 0; slot < 9; slot++) result.add(Collections.emptyMap());
            boolean[] seen = new boolean[9];
            String positionKey = definition.isShapeless() ? "occurrence" : "slot";
            for (int index = 0; index < slots.tagCount(); index++) {
                NBTTagCompound slotRecord = slots.getCompoundTagAt(index);
                if (!slotRecord.hasKey(positionKey, NBT.TAG_INT) ||
                      !slotRecord.hasKey("profileId", NBT.TAG_INT)) {
                    throw new IllegalArgumentException(
                          "Cached QIO output profile slot record is incomplete");
                }
                int slot = slotRecord.getInteger(positionKey);
                if (slot < 0 || slot >= definition.ingredients.size() || slot >= 9 ||
                      seen[slot]) {
                    throw new IllegalArgumentException("Cached QIO output profile position is invalid");
                }
                if (definition.ingredients.get(slot).isEmpty()) {
                    throw new IllegalArgumentException("Cached QIO output profile targets an empty ingredient");
                }
                Set<String> allowed = new LinkedHashSet<>();
                variants.choices(definition.ingredients.get(slot).matcherId)
                      .forEach(choice -> allowed.add(choice.candidateId()));
                int profileId = slotRecord.getInteger("profileId");
                if (profileId < 0) {
                    throw new IllegalArgumentException("Cached QIO output profile ID is invalid");
                }
                Map<String, Map<PortableResourceDescriptor, Long>> candidates =
                      profileTable.get(profileId);
                if (candidates == null || candidates.isEmpty()) {
                    throw new IllegalArgumentException("Cached QIO output profile reference is invalid");
                }
                for (String candidateId : candidates.keySet()) {
                    if (candidateId == null || candidateId.isEmpty() ||
                          !allowed.contains(candidateId) || candidates.get(candidateId) == null) {
                        throw new IllegalArgumentException(
                              "Cached QIO candidate output profile is invalid");
                    }
                }
                result.set(slot, Collections.unmodifiableMap(candidates));
                seen[slot] = true;
            }
            int expected = 0;
            for (IngredientDefinition ingredient : definition.ingredients) {
                if (!ingredient.isEmpty()) expected++;
            }
            if (slots.tagCount() != expected) {
                throw new IllegalArgumentException("Cached QIO output profile slot count is invalid");
            }
            if (definition.isShapeless()) {
                for (int index = 0; index < expected; index++) {
                    if (!seen[index]) throw new IllegalArgumentException(
                          "Cached shapeless output profile occurrences are not contiguous");
                }
            } else {
                for (int index = 0; index < definition.ingredients.size(); index++) {
                    if (!definition.ingredients.get(index).isEmpty() && !seen[index]) {
                        throw new IllegalArgumentException(
                              "Cached shaped output profile is missing an ingredient slot");
                    }
                }
            }
            return Collections.unmodifiableList(result);
        }

        private static NBTTagList writeAmounts(Map<PortableResourceDescriptor, Long> values) {
            NBTTagList result = new NBTTagList();
            List<PortableResourceDescriptor> ordered = new ArrayList<>(values.keySet());
            Collections.sort(ordered);
            for (PortableResourceDescriptor resource : ordered) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setTag("resource", resource.write());
                entry.setLong("amount", values.get(resource));
                result.appendTag(entry);
            }
            return result;
        }

        private static String profileCanonical(
              Map<String, Map<PortableResourceDescriptor, Long>> profile) {
            StringBuilder canonical = new StringBuilder();
            List<String> candidateIds = new ArrayList<>(profile.keySet());
            Collections.sort(candidateIds);
            for (String candidateId : candidateIds) {
                canonical.append(candidateId).append('=');
                Map<PortableResourceDescriptor, Long> outputs = profile.get(candidateId);
                List<PortableResourceDescriptor> resources = new ArrayList<>(outputs.keySet());
                Collections.sort(resources);
                for (PortableResourceDescriptor resource : resources) {
                    canonical.append(resource).append('@').append(outputs.get(resource)).append(';');
                }
                canonical.append('|');
            }
            return canonical.toString();
        }

        private static Map<PortableResourceDescriptor, Long> readAmounts(NBTTagList values) {
            if (values.tagCount() > 1_000_000) {
                throw new IllegalArgumentException("Cached QIO amount map is too large");
            }
            Map<PortableResourceDescriptor, Long> result = new LinkedHashMap<>();
            for (int index = 0; index < values.tagCount(); index++) {
                NBTTagCompound entry = values.getCompoundTagAt(index);
                PortableResourceDescriptor resource = PortableResourceDescriptor.read(
                      entry.getCompoundTag("resource"));
                long amount = entry.getLong("amount");
                if (amount <= 0 || result.put(resource, amount) != null) {
                    throw new IllegalArgumentException("Cached QIO amount entry is invalid");
                }
            }
            return Collections.unmodifiableMap(result);
        }

        private static NBTTagCompound stringTag(String value) {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("value", value);
            return data;
        }

        private static int positiveInt(NBTTagCompound data, String key) {
            int value = data.getInteger(key);
            if (value <= 0) {
                throw new IllegalArgumentException("Cached QIO " + key + " must be positive");
            }
            return value;
        }

        private static String hash(String value, String description) {
            if (value == null || value.length() != 64) {
                throw new IllegalArgumentException("Cached QIO " + description + " is invalid");
            }
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (!((character >= '0' && character <= '9') ||
                      (character >= 'a' && character <= 'f'))) {
                    throw new IllegalArgumentException(
                          "Cached QIO " + description + " is invalid");
                }
            }
            return value;
        }

        private static String structuralSignature(String value) {
            return hash(value, "recipe structural signature");
        }

        static final class CacheData {

            final String generationId;
            final int recipeCount;
            final QIOForgeRecipeData.CacheData forgeData;
            final List<NBTTagCompound> itemTypes;
            final List<NBTTagCompound> candidates;
            final List<NBTTagCompound> matchers;
            final List<NBTTagCompound> recipes;
            final List<NBTTagCompound> outputProfiles;
            final List<NBTTagCompound> candidateProfiles;
            final List<NBTTagCompound> reverseIndexes;

            CacheData(String generationId, int recipeCount,
                  QIOForgeRecipeData.CacheData forgeData,
                  List<NBTTagCompound> itemTypes, List<NBTTagCompound> candidates,
                  List<NBTTagCompound> matchers, List<NBTTagCompound> recipes,
                  List<NBTTagCompound> outputProfiles,
                  List<NBTTagCompound> candidateProfiles,
                  List<NBTTagCompound> reverseIndexes) {
                this.generationId = hash(generationId, "generation signature");
                if (recipeCount < 0) {
                    throw new IllegalArgumentException("Cached QIO recipe count is negative");
                }
                this.recipeCount = recipeCount;
                this.forgeData = Objects.requireNonNull(forgeData, "forgeData");
                this.itemTypes = immutableTags(itemTypes);
                this.candidates = immutableTags(candidates);
                this.matchers = immutableTags(matchers);
                this.recipes = immutableTags(recipes);
                this.outputProfiles = immutableTags(outputProfiles);
                this.candidateProfiles = immutableTags(candidateProfiles);
                this.reverseIndexes = immutableTags(reverseIndexes);
            }

            private static List<NBTTagCompound> immutableTags(List<NBTTagCompound> values) {
                List<NBTTagCompound> copied = new ArrayList<>(values.size());
                values.forEach(value -> copied.add(value.copy()));
                return Collections.unmodifiableList(copied);
            }
        }

        interface BuildListener {
            void scanComplete(int recipeCount);
            void cacheStarted(int recipeCount);
        }

        /**
         * Server-thread Forge capture with worker-only symbol/index phases. A worker never sees
         * IRecipe or World; it receives only SymbolCatalogInput or compiled immutable templates.
         */
        static final class Capture {

            private enum Stage {
                FORGE_CAPTURE,
                FORGE_INDEX,
                RECIPE_SNAPSHOT,
                SCAN,
                SYMBOL_INPUT,
                SYMBOLS,
                COMPILE,
                INDEX,
                COMPLETE
            }

            @Nullable private Builder builder;
            @Nullable private final World world;
            private final List<IRecipe> recipes = new ArrayList<>();
            @Nullable private Iterator<? extends IRecipe> recipeSource;
            private final BuildListener listener;
            private final Map<ResourceLocation, CompiledRecipe> compiled = new LinkedHashMap<>();
            private Stage stage = Stage.SCAN;
            private int recipeIndex;
            private int compileIndex;
            private List<LogicalRecipe> logicalRecipes = new ArrayList<>();
            private List<SymbolRecipe> symbolRecipes = new ArrayList<>();
            @Nullable private Iterator<LogicalRecipe> logicalSource;
            @Nullable private CatalogSymbols symbols;
            @Nullable private CompletableFuture<CatalogSymbols> symbolFuture;
            @Nullable private CompletableFuture<RecipeOutputIndex> indexFuture;
            @Nullable private QIOForgeRecipeData.Capture forgeCapture;
            @Nullable private CompletableFuture<QIOForgeRecipeData> forgeFuture;
            private QIOForgeRecipeData forgeData = QIOForgeRecipeData.empty();
            @Nullable private RecipeOutputIndex result;

            private Capture(@Nullable World world,
                  Collection<? extends IRecipe> recipes, BuildListener listener,
                  boolean captureGlobalForgeData, boolean registrySnapshotIsStable) {
                Objects.requireNonNull(recipes, "recipes");
                this.world = world;
                this.listener = Objects.requireNonNull(listener, "listener");
                if (registrySnapshotIsStable) {
                    recipeSource = recipes.iterator();
                } else {
                    // Forge's registry collection is live. Keep a stable sorted view unless the
                    // caller already owns the immutable lifecycle snapshot.
                    List<IRecipe> stableRecipes = new ArrayList<>(recipes.size());
                    for (IRecipe recipe : recipes) {
                        if (recipe != null) stableRecipes.add(recipe);
                    }
                    stableRecipes.sort(Comparator.comparing(recipe -> {
                        ResourceLocation id = recipe.getRegistryName();
                        return id == null ? "" : id.toString();
                    }));
                    recipeSource = Collections.unmodifiableList(stableRecipes).iterator();
                }
                if (captureGlobalForgeData) {
                    forgeCapture = QIOForgeRecipeData.beginCapture();
                    stage = Stage.FORGE_CAPTURE;
                } else {
                    builder = newBuilder(world, QIOForgeRecipeData.empty());
                    stage = Stage.RECIPE_SNAPSHOT;
                }
            }

            private static Builder newBuilder(@Nullable World world,
                  QIOForgeRecipeData forgeData) {
                return new Builder(world, ignored -> 0,
                      DEFAULT_MAX_CANDIDATES_PER_INGREDIENT,
                      DEFAULT_MAX_VARIANTS_PER_RECIPE,
                      DEFAULT_MAX_MATERIALIZED_VARIANTS, forgeData);
            }

            boolean process(int maximumRecipes, long maximumNanos,
                  @Nullable Executor worker) {
                return process(maximumRecipes, maximumNanos, worker, 2);
            }

            boolean process(int maximumRecipes, long maximumNanos,
                  @Nullable Executor worker, int workerCount) {
                if (maximumRecipes <= 0 || maximumNanos <= 0) {
                    throw new IllegalArgumentException(
                          "Recipe catalog capture slice must be positive");
                }
                long deadline = maximumNanos == Long.MAX_VALUE ? Long.MAX_VALUE :
                      saturatedAdd(System.nanoTime(), maximumNanos);
                int processed = 0;
                while (stage != Stage.COMPLETE) {
                    if (stage == Stage.FORGE_CAPTURE) {
                        QIOForgeRecipeData.Capture active = Objects.requireNonNull(forgeCapture,
                              "forgeCapture");
                        processed += active.process(maximumRecipes - processed, deadline);
                        if (!active.isComplete()) return false;
                        if (worker == null) {
                            forgeData = active.finish();
                            builder = newBuilder(world, forgeData);
                            forgeCapture = null;
                            stage = Stage.RECIPE_SNAPSHOT;
                        } else {
                            forgeFuture = active.finishAsync(worker, workerCount);
                            stage = Stage.FORGE_INDEX;
                            return false;
                        }
                    }
                    if (stage == Stage.FORGE_INDEX) {
                        CompletableFuture<QIOForgeRecipeData> pending = Objects.requireNonNull(
                              forgeFuture, "forgeFuture");
                        if (!pending.isDone()) return false;
                        forgeData = pending.join();
                        forgeFuture = null;
                        forgeCapture = null;
                        builder = newBuilder(world, forgeData);
                        stage = Stage.RECIPE_SNAPSHOT;
                    }
                    if (stage == Stage.RECIPE_SNAPSHOT) {
                        Iterator<? extends IRecipe> source = Objects.requireNonNull(
                              recipeSource, "recipe source");
                        while (source.hasNext() && processed < maximumRecipes &&
                              (deadline == Long.MAX_VALUE || System.nanoTime() < deadline)) {
                            recipes.add(source.next());
                            processed++;
                        }
                        if (source.hasNext()) return false;
                        recipeSource = null;
                        stage = Stage.SCAN;
                    }
                    if (stage == Stage.SCAN) {
                        Builder activeBuilder = Objects.requireNonNull(builder, "builder");
                        while (recipeIndex < recipes.size() && processed < maximumRecipes &&
                              (deadline == Long.MAX_VALUE || System.nanoTime() < deadline)) {
                            try {
                                activeBuilder.add(recipes.get(recipeIndex));
                            } catch (RuntimeException ignored) {
                                // A broken third-party recipe cannot abort the directory.
                            }
                            recipeIndex++;
                            processed++;
                        }
                        if (recipeIndex < recipes.size()) return false;
                        listener.scanComplete(activeBuilder.recipes.size());
                        listener.cacheStarted(activeBuilder.recipes.size());
                        logicalSource = activeBuilder.recipes.values().iterator();
                        stage = Stage.SYMBOL_INPUT;
                    }
                    if (stage == Stage.SYMBOL_INPUT) {
                        Iterator<LogicalRecipe> source = Objects.requireNonNull(logicalSource,
                              "logical recipe source");
                        while (source.hasNext() && processed < maximumRecipes &&
                              (deadline == Long.MAX_VALUE || System.nanoTime() < deadline)) {
                            LogicalRecipe logical = source.next();
                            logicalRecipes.add(logical);
                            symbolRecipes.add(new SymbolRecipe(logical));
                            processed++;
                        }
                        if (source.hasNext()) return false;
                        logicalSource = null;
                        builder = null;
                        SymbolCatalogInput input = new SymbolCatalogInput(symbolRecipes, true);
                        if (worker == null) {
                            symbols = CatalogSymbols.build(input);
                            stage = Stage.COMPILE;
                        } else {
                            symbolFuture = CatalogSymbols.buildAsync(input, worker, workerCount);
                            stage = Stage.SYMBOLS;
                            return false;
                        }
                    }
                    if (stage == Stage.SYMBOLS) {
                        CompletableFuture<CatalogSymbols> pending = Objects.requireNonNull(
                              symbolFuture, "symbolFuture");
                        if (!pending.isDone()) return false;
                        symbols = pending.join();
                        symbolFuture = null;
                        stage = Stage.COMPILE;
                    }
                    if (stage == Stage.COMPILE) {
                        CatalogSymbols activeSymbols = Objects.requireNonNull(symbols,
                              "symbols");
                        while (compileIndex < logicalRecipes.size() &&
                              processed < maximumRecipes &&
                              (deadline == Long.MAX_VALUE || System.nanoTime() < deadline)) {
                            LogicalRecipe logical = logicalRecipes.get(compileIndex++);
                            try {
                                CompiledRecipe recipe = logical.compile(activeSymbols);
                                if (recipe != null) {
                                    compiled.put(recipe.definition.getRecipeId(), recipe);
                                }
                            } catch (RuntimeException ignored) {
                                // Keep the remaining catalog usable when one recipe is broken.
                            }
                            processed++;
                        }
                        if (compileIndex < logicalRecipes.size()) return false;
                        logicalRecipes = Collections.emptyList();
                        symbolRecipes = Collections.emptyList();
                        if (worker == null) {
                            result = fromCompiled(activeSymbols, compiled, forgeData);
                            stage = Stage.COMPLETE;
                        } else {
                            indexFuture = fromCompiledAsync(activeSymbols,
                                  compiled.values(), worker,
                                  workerCount, forgeData);
                            stage = Stage.INDEX;
                            return false;
                        }
                    }
                    if (stage == Stage.INDEX) {
                        CompletableFuture<RecipeOutputIndex> pending = Objects.requireNonNull(
                              indexFuture, "indexFuture");
                        if (!pending.isDone()) return false;
                        result = pending.join();
                        indexFuture = null;
                        stage = Stage.COMPLETE;
                    }
                }
                return true;
            }

            @Nonnull
            RecipeOutputIndex finish() {
                if (stage != Stage.COMPLETE || result == null) {
                    throw new IllegalStateException("Recipe catalog capture is incomplete");
                }
                return result;
            }

            void cancel() {
                if (forgeFuture != null) forgeFuture.cancel(true);
                if (symbolFuture != null) symbolFuture.cancel(true);
                if (indexFuture != null) indexFuture.cancel(true);
            }
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
                    dependencies.add(PortableResourceDescriptor.itemIgnoringCapabilities(stack));
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
                if (!stack.isEmpty()) {
                    inputs.add(PortableResourceDescriptor.itemIgnoringCapabilities(stack));
                }
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
                if (!stack.isEmpty()) {
                    inputs.add(PortableResourceDescriptor.itemIgnoringCapabilities(stack));
                }
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

    private static QIOWorkbenchConfiguration.PatternLayout patternLayout(RecipeLayout layout) {
        return switch (Objects.requireNonNull(layout, "layout")) {
            case SHAPED -> QIOWorkbenchConfiguration.PatternLayout.SHAPED;
            case SHAPELESS -> QIOWorkbenchConfiguration.PatternLayout.SHAPELESS;
            case ORDERED_UNKNOWN -> QIOWorkbenchConfiguration.PatternLayout.ORDERED_UNKNOWN;
        };
    }

    /** Returns only semantically occupied recipe positions; empty shaped cells are omitted. */
    private static List<Integer> activeCandidateSlots(
          List<List<IngredientChoice>> candidates) {
        List<Integer> result = new ArrayList<>();
        for (int slot = 0; slot < candidates.size(); slot++) {
            List<IngredientChoice> choices = candidates.get(slot);
            if (!(choices.size() == 1 && choices.get(0).isEmpty())) {
                result.add(slot);
            }
        }
        return result;
    }

    private static List<ItemStack> matchingGrid(List<IngredientChoice> grid) {
        List<ItemStack> copy = new ArrayList<>(grid.size());
        grid.forEach(choice -> copy.add(choice.matchingStack()));
        return copy;
    }

    /** Materializes the frequency-facing representation; shapeless inputs are packed by occurrence. */
    private static List<ItemStack> encodedGrid(List<ItemStack> grid, RecipeLayout layout) {
        if (layout != RecipeLayout.SHAPELESS) return grid;
        List<ItemStack> packed = new ArrayList<>(9);
        for (ItemStack stack : grid) {
            if (stack != null && !stack.isEmpty()) {
                packed.add(QIORecipeStackUtils.copyForRecipeSelection(stack));
            }
        }
        while (packed.size() < 9) packed.add(ItemStack.EMPTY);
        return packed;
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
        FluidStack stored = QIOFluidContainerSnapshot.capture(candidate);
        return stored == null ? null : IngredientChoice.fluid(candidate, stored);
    }

    private static final class IngredientChoice {

        private static final IngredientChoice EMPTY = new IngredientChoice(ItemStack.EMPTY, null,
              0, false, "");
        private final ItemStack matchingStack;
        @Nullable
        private final FrozenItemType itemType;
        @Nullable
        private final PortableResourceDescriptor resource;
        private final long amount;
        private final boolean virtualFluid;
        private final String sortKey;
        private final String candidateId;

        private IngredientChoice(ItemStack matchingStack,
              @Nullable PortableResourceDescriptor resource, long amount, boolean virtualFluid,
              String sortKey) {
            this.matchingStack = QIORecipeStackUtils.copyForRecipeSelection(matchingStack, 1);
            itemType = matchingStack.isEmpty() ? null : FrozenItemType.capture(matchingStack);
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
            PortableResourceDescriptor item = PortableResourceDescriptor.itemIgnoringCapabilities(stack);
            return new IngredientChoice(stack, item, 1, false, "I|" + item);
        }

        private static IngredientChoice fromCache(ItemTypeDefinition itemType,
              PortableResourceDescriptor resource, long amount, boolean virtualFluid) {
            Objects.requireNonNull(itemType, "itemType");
            Objects.requireNonNull(resource, "resource");
            String sortKey;
            if (virtualFluid) {
                String container = itemType.descriptor.toString();
                sortKey = "F|" + resource + '|' + amount + '|' + container;
            } else {
                sortKey = "I|" + resource;
            }
            return new IngredientChoice(itemType.prototype, resource, amount, virtualFluid,
                  sortKey);
        }

        private static IngredientChoice fluid(ItemStack matchingStack, FluidStack fluid) {
            if (!MachineRecipeItemInputs.isConcreteInput(matchingStack)) {
                throw new IllegalArgumentException(
                      "QIO workbench fluid container must have concrete metadata");
            }
            PortableResourceDescriptor descriptor = PortableResourceDescriptor.fluid(fluid);
            String container;
            try {
                container = PortableResourceDescriptor.itemIgnoringCapabilities(matchingStack).toString();
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
        private ItemStack matchingStack() {
            return QIORecipeStackUtils.copyForRecipeSelection(matchingStack);
        }
        private PortableResourceDescriptor resource() {
            return Objects.requireNonNull(resource, "resource");
        }
        private long amount() { return amount; }
        private boolean isVirtualFluid() { return virtualFluid; }
        private String sortKey() { return sortKey; }
        private String candidateId() { return candidateId; }
        private CandidateDefinition definition(ItemTypeTable itemTypes) {
            FrozenItemType frozen = Objects.requireNonNull(itemType, "itemType");
            ItemTypeDefinition itemType = itemTypes.get(itemTypes.getId(frozen.descriptor));
            return new CandidateDefinition(candidateId, itemType,
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
