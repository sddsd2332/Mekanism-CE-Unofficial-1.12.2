package mekanism.qioprocessing.common.planning;

import mekanism.common.TestBootstrap;
import mekanism.common.world.DummyWorld;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchClosureMode;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOWorkbenchRecipeCatalogTest {

    private static Item directFluidContainer;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        directFluidContainer = new TestFluidContainerItem();
        ResourceLocation id = new ResourceLocation("test", "qio_direct_fluid_container");
        directFluidContainer.setRegistryName(id);
    }

    @Test
    void ingredientCandidatesBecomeDistinctExactExecutableVariants() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromStacks(new ItemStack(Items.IRON_INGOT),
              new ItemStack(Items.GOLD_INGOT)));
        ShapedRecipes recipe = new ShapedRecipes("test", 1, 1, ingredients,
              new ItemStack(Items.DIAMOND));
        recipe.setRegistryName(new ResourceLocation("test", "candidate_recipe"));

        QIOWorkbenchRecipeCatalog.Snapshot snapshot = QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 3, 8, 8, 8);

        List<QIOPlanningRoute> routes = snapshot.materialize(
              PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)),
              Collections.emptyMap(), snapshot.getDeclaredOutputs(), ignored -> 3);
        assertEquals(2, routes.size());
        QIOPlanningRoute first = routes.get(0);
        QIOPlanningRoute second = routes.get(1);
        assertNotEquals(first.getVariantId(), second.getVariantId());
        assertEquals(3, first.getRoutePriority());
        assertEquals(1, first.getExactInputs().size());
        assertEquals(1, second.getExactInputs().size());
        assertTrue(first.getGuaranteedOutputs().containsKey(
              PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND))));
        assertNotNull(snapshot.getPattern(first.getStableId()));
        assertNotNull(snapshot.getPattern(second.getStableId()));
        assertEquals(1, snapshot.getRecipeIds().size());
        assertEquals(new ResourceLocation("test", "candidate_recipe"),
              snapshot.getRecipeIds().get(0));
        assertTrue(snapshot.containsRecipe("test:candidate_recipe"));
        assertEquals(64, snapshot.getStructuralSignature().length());
    }

    @Test
    void rawWildcardIngredientExpandsToConcreteQioCandidates() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(new RawWildcardIngredient(new ItemStack(Items.REDSTONE, 1,
              OreDictionary.WILDCARD_VALUE)));
        ShapedRecipes recipe = new ShapedRecipes("test", 1, 1, ingredients,
              new ItemStack(Items.DIAMOND));
        ResourceLocation recipeId = new ResourceLocation("test", "wildcard_input");
        recipe.setRegistryName(recipeId);

        QIOWorkbenchRecipeCatalog.Snapshot snapshot = QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 0, 64, 64, 64);
        QIOWorkbenchRecipeCatalog.RecipeDefinition definition =
              snapshot.getRecipeDefinition(recipeId);
        assertNotNull(definition);
        assertFalse(definition.getIngredients().get(0).getCandidates().isEmpty());
        definition.getIngredients().get(0).getCandidates().forEach(candidate ->
              assertNotEquals(OreDictionary.WILDCARD_VALUE,
                    candidate.getDisplayStack().getMetadata()));

        List<QIOPlanningRoute> routes = snapshot.materialize(
              PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)),
              Collections.singletonMap(PortableResourceDescriptor.item(
                    new ItemStack(Items.REDSTONE)), 1L), Collections.emptySet(),
              ignored -> 0);
        assertFalse(routes.isEmpty());
        assertTrue(routes.get(0).getExactInputs().containsKey(
              PortableResourceDescriptor.item(new ItemStack(Items.REDSTONE))));
    }

    @Test
    void wildcardDeclaredOutputIndexesTheConcreteCraftingResult() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromStacks(new ItemStack(Items.IRON_INGOT)));
        ShapedRecipes recipe = new ShapedRecipes("test", 1, 1, ingredients,
              new ItemStack(Items.COAL, 1, OreDictionary.WILDCARD_VALUE)) {
            @Override
            public ItemStack getCraftingResult(InventoryCrafting inventory) {
                return new ItemStack(Items.COAL, 1, 1);
            }
        };
        ResourceLocation recipeId = new ResourceLocation("test", "wildcard_output");
        recipe.setRegistryName(recipeId);
        PortableResourceDescriptor charcoal = PortableResourceDescriptor.item(
              new ItemStack(Items.COAL, 1, 1));

        QIOWorkbenchRecipeCatalog.Snapshot snapshot = QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 0, 64, 64, 64);
        QIOWorkbenchRecipeCatalog.RecipeDefinition definition =
              snapshot.getRecipeDefinition(recipeId);
        assertNotNull(definition);
        assertEquals(charcoal, definition.getOutput());
        assertEquals(Collections.singletonList(charcoal), snapshot.getOrderedOutputs());
        assertFalse(snapshot.materialize(charcoal, Collections.emptyMap(),
              Collections.emptySet(), ignored -> 0).isEmpty());
    }

    @Test
    void productionCatalogDoesNotExposeRecipesUntilTheFrequencyEncodesThem() {
        QIORecipeCatalogService.INSTANCE.clear();
        try {
            QIORecipeCatalogService.INSTANCE.refresh(new DummyWorld());
            QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
            QIOWorkbenchRecipeCatalog.Snapshot snapshot = QIORecipeCatalogService.INSTANCE
                  .getState(configuration).getSnapshot();
            assertTrue(snapshot.getRecipeIds().isEmpty());
            assertTrue(snapshot.getOrderedOutputs().isEmpty());
        } finally {
            QIORecipeCatalogService.INSTANCE.clear();
        }
    }

    @Test
    void serverResolvesARealGhostGridBeforeCreatingAnEncodedPattern() {
        List<ItemStack> grid = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) grid.add(ItemStack.EMPTY);
        grid.set(0, new ItemStack(Blocks.PLANKS));
        grid.set(3, new ItemStack(Blocks.PLANKS));

        DummyWorld world = new DummyWorld();
        QIOWorkbenchConfiguration.EncodedPattern pattern =
              QIOWorkbenchRecipeCatalog.resolveEncodedPattern(world, grid);

        assertEquals(Items.STICK, pattern.getOutput().resolveItem().getItem());
        assertEquals(4, pattern.getOutputAmount());
        assertEquals(9, pattern.getGrid().size());

        QIORecipeCatalogService.INSTANCE.clear();
        try {
            QIORecipeCatalogService.INSTANCE.refresh(world);
            QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
            assertTrue(configuration.putEncodedPattern(pattern));
            QIOWorkbenchRecipeCatalog.Snapshot snapshot = QIORecipeCatalogService.INSTANCE
                  .getState(configuration).getSnapshot();
            assertEquals(Collections.singletonList(pattern.getRecipeId()),
                  snapshot.getRecipeIds());
            assertEquals(Collections.singletonList(pattern.getOutput()),
                  snapshot.getOrderedOutputs());
            configuration.setRecipeEnabled(pattern.getRecipeId().toString(),
                  pattern.getRecipeSignature(), false);
            assertSame(snapshot, QIORecipeCatalogService.INSTANCE.getState(configuration)
                  .getSnapshot());
        } finally {
            QIORecipeCatalogService.INSTANCE.clear();
        }
    }

    @Test
    void safeFilledContainerAlsoCreatesDirectFluidVariantWithoutEmptyContainerOutput() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ItemStack filledContainer = new ItemStack(directFluidContainer);
        IFluidHandlerItem handler = mekanism.common.util.FluidContainerUtils
              .getUnstackedFluidHandlerCapability(filledContainer);
        assertNotNull(handler);
        assertEquals(1_000, handler.fill(new FluidStack(FluidRegistry.WATER, 1_000), true));
        filledContainer = handler.getContainer();
        ingredients.add(Ingredient.fromStacks(filledContainer));
        ShapedRecipes recipe = new ShapedRecipes("test", 1, 1, ingredients,
              new ItemStack(Items.DIAMOND));
        recipe.setRegistryName(new ResourceLocation("test", "direct_fluid_recipe"));

        QIOWorkbenchRecipeCatalog.Snapshot snapshot = QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 0, 8, 8, 8);

        QIOPlanningRoute direct = snapshot.materialize(
                    PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)),
                    Collections.emptyMap(), snapshot.getDeclaredOutputs(), ignored -> 0).stream()
              .filter(route -> route.getExactInputs().keySet().stream().anyMatch(resource ->
                    resource.getKind() == PortableResourceDescriptor.Kind.FLUID))
              .findFirst().orElseThrow(() -> new AssertionError("Missing direct fluid route"));
        PortableResourceDescriptor water = PortableResourceDescriptor.fluid(
              new FluidStack(FluidRegistry.WATER, 1));
        assertEquals(1_000L, direct.getExactInputs().get(water));
        assertTrue(direct.getGuaranteedOutputs().containsKey(
              PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND))));
        assertEquals(1, direct.getGuaranteedOutputs().size());
        assertTrue(snapshot.getPattern(direct.getStableId()).isVirtualFluidSlot(0));
    }

    @Test
    void stableRouteIdRebuildsPatternAfterColdCatalogLoad() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromStacks(new ItemStack(Items.IRON_INGOT)));
        ShapedRecipes recipe = new ShapedRecipes("test", 1, 1, ingredients,
              new ItemStack(Items.DIAMOND));
        ResourceLocation recipeId = new ResourceLocation("test", "cold_restore_recipe");
        recipe.setRegistryName(recipeId);

        QIOWorkbenchRecipeCatalog.Snapshot first = QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 0, 8, 8, 8);
        QIOPlanningRoute route = first.materialize(
              PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)),
              Collections.emptyMap(), Collections.emptySet(),
              Collections.singletonMap(recipeId, 0L)).get(0);
        QIOWorkbenchRecipeCatalog.Snapshot restored = QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 0, 8, 8, 8);
        assertEquals(0, restored.getCachedPatternCount());
        assertNotNull(restored.getPattern(route.getStableId()));
        assertEquals(route.getVariantId(), restored.getPattern(route.getStableId()).getVariantId());
    }

    @Test
    void batchTargetsDiscoverEveryStableMatchingRecipeInOneBoundedPass() {
        List<IRecipe> recipes = new ArrayList<>();
        recipes.add(recipe("batch_a", new ItemStack(Items.DIAMOND),
              new ItemStack(Items.IRON_INGOT)));
        recipes.add(recipe("batch_b", new ItemStack(Items.DIAMOND),
              new ItemStack(Items.GOLD_INGOT)));
        recipes.add(recipe("batch_other", new ItemStack(Items.EMERALD),
              new ItemStack(Items.COAL)));
        List<QIOWorkbenchConfiguration.EncodedPattern> patterns =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(new DummyWorld(),
                    Collections.singletonList(new ItemStack(Items.DIAMOND)), recipes, 32);
        assertEquals(2, patterns.size());
        assertEquals(new ResourceLocation("test", "batch_a"),
              patterns.get(0).getRecipeId());
        assertEquals(new ResourceLocation("test", "batch_b"),
              patterns.get(1).getRecipeId());
    }

    @Test
    void tenThousandMatchingBatchRoutesResolveUnderFiveSeconds() {
        List<IRecipe> recipes = new ArrayList<>(10_000);
        for (int index = 0; index < 10_000; index++) {
            recipes.add(recipe("batch_scale_" + index, new ItemStack(Items.DIAMOND),
                  new ItemStack(Items.IRON_INGOT)));
        }
        List<QIOWorkbenchConfiguration.EncodedPattern> patterns = assertTimeout(
              Duration.ofSeconds(5), () -> QIOWorkbenchRecipeCatalog.resolveEncodedTargets(
                    new DummyWorld(), Collections.singletonList(new ItemStack(Items.DIAMOND)),
                    recipes, 20_000));
        assertEquals(10_000, patterns.size());
    }

    @Test
    void preferredClosureFollowsOneStableDependencyRoute() {
        DummyWorld world = new DummyWorld();
        List<IRecipe> recipes = new ArrayList<>();
        recipes.add(recipe("closure_root", new ItemStack(Items.EMERALD),
              new ItemStack(Items.DIAMOND)));
        recipes.add(recipe("closure_dep_a", new ItemStack(Items.DIAMOND),
              new ItemStack(Items.IRON_INGOT)));
        recipes.add(recipe("closure_dep_b", new ItemStack(Items.DIAMOND),
              new ItemStack(Items.GOLD_INGOT)));
        List<QIOWorkbenchConfiguration.EncodedPattern> roots =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world,
                    Collections.singletonList(new ItemStack(Items.EMERALD)), recipes, 32);

        QIOWorkbenchRecipeCatalog.ClosureResult result =
              QIOWorkbenchRecipeCatalog.resolveClosure(
                    QIOWorkbenchRecipeCatalog.prepareClosure(world, roots,
                          new QIOWorkbenchConfiguration(),
                          QIOWorkbenchClosureMode.PREFERRED, recipes, 32), () -> false);

        assertEquals(2, result.getPatterns().size());
        assertEquals(new ResourceLocation("test", "closure_dep_a"),
              result.getPatterns().get(1).getRecipeId());
        assertEquals(2, result.getNewProductCount());
        assertEquals(0, result.getCycleCount());
        assertFalse(result.isTruncated());
    }

    @Test
    void allClosureFollowsEveryStableDependencyRoute() {
        DummyWorld world = new DummyWorld();
        List<IRecipe> recipes = new ArrayList<>();
        recipes.add(recipe("closure_all_root", new ItemStack(Items.EMERALD),
              new ItemStack(Items.DIAMOND)));
        recipes.add(recipe("closure_all_a", new ItemStack(Items.DIAMOND),
              new ItemStack(Items.IRON_INGOT)));
        recipes.add(recipe("closure_all_b", new ItemStack(Items.DIAMOND),
              new ItemStack(Items.GOLD_INGOT)));
        List<QIOWorkbenchConfiguration.EncodedPattern> roots =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world,
                    Collections.singletonList(new ItemStack(Items.EMERALD)), recipes, 32);

        QIOWorkbenchRecipeCatalog.ClosureResult result =
              QIOWorkbenchRecipeCatalog.resolveClosure(
                    QIOWorkbenchRecipeCatalog.prepareClosure(world, roots,
                          new QIOWorkbenchConfiguration(), QIOWorkbenchClosureMode.ALL,
                          recipes, 32), () -> false);

        assertEquals(3, result.getPatterns().size());
        assertEquals(3, result.getNewRecipeCount());
        assertEquals(2, result.getLeafMaterialCount());
    }

    @Test
    void existingDependencyIsSkippedForInsertionButStillTraversed() {
        DummyWorld world = new DummyWorld();
        List<IRecipe> recipes = new ArrayList<>();
        recipes.add(recipe("closure_existing_root", new ItemStack(Items.EMERALD),
              new ItemStack(Items.DIAMOND)));
        recipes.add(recipe("closure_existing_dep", new ItemStack(Items.DIAMOND),
              new ItemStack(Items.IRON_INGOT)));
        List<QIOWorkbenchConfiguration.EncodedPattern> roots =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world,
                    Collections.singletonList(new ItemStack(Items.EMERALD)), recipes, 32);
        QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
        QIOWorkbenchConfiguration.EncodedPattern dependency =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world,
                    Collections.singletonList(new ItemStack(Items.DIAMOND)), recipes, 32).get(0);
        assertTrue(configuration.putEncodedPattern(dependency));

        QIOWorkbenchRecipeCatalog.ClosureResult result =
              QIOWorkbenchRecipeCatalog.resolveClosure(
                    QIOWorkbenchRecipeCatalog.prepareClosure(world, roots, configuration,
                          QIOWorkbenchClosureMode.PREFERRED, recipes, 32), () -> false);

        assertEquals(2, result.getPatterns().size());
        assertEquals(1, result.getExistingRecipeCount());
        assertEquals(1, result.getNewRecipeCount());
        assertEquals(1, result.getLeafMaterialCount());
    }

    @Test
    void closureReportsAndTerminatesTwoRecipeAndSelfCycles() {
        DummyWorld world = new DummyWorld();
        List<IRecipe> cycle = new ArrayList<>();
        cycle.add(recipe("cycle_diamond", new ItemStack(Items.DIAMOND),
              new ItemStack(Items.IRON_INGOT)));
        cycle.add(recipe("cycle_iron", new ItemStack(Items.IRON_INGOT),
              new ItemStack(Items.DIAMOND)));
        List<QIOWorkbenchConfiguration.EncodedPattern> roots =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world,
                    Collections.singletonList(new ItemStack(Items.DIAMOND)), cycle, 32);
        QIOWorkbenchRecipeCatalog.ClosureResult twoRecipe =
              QIOWorkbenchRecipeCatalog.resolveClosure(
                    QIOWorkbenchRecipeCatalog.prepareClosure(world, roots,
                          new QIOWorkbenchConfiguration(), QIOWorkbenchClosureMode.ALL,
                          cycle, 32), () -> false);
        assertEquals(2, twoRecipe.getPatterns().size());
        assertEquals(1, twoRecipe.getCycleCount());
        assertEquals(1, twoRecipe.getCyclePaths().size());

        List<IRecipe> self = Collections.singletonList(recipe("cycle_double",
              new ItemStack(Items.EMERALD, 2), new ItemStack(Items.EMERALD)));
        List<QIOWorkbenchConfiguration.EncodedPattern> selfRoots =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world,
                    Collections.singletonList(new ItemStack(Items.EMERALD)), self, 8);
        QIOWorkbenchRecipeCatalog.ClosureResult selfResult =
              QIOWorkbenchRecipeCatalog.resolveClosure(
                    QIOWorkbenchRecipeCatalog.prepareClosure(world, selfRoots,
                          new QIOWorkbenchConfiguration(), QIOWorkbenchClosureMode.ALL,
                          self, 8), () -> false);
        assertEquals(1, selfResult.getPatterns().size());
        assertEquals(1, selfResult.getCycleCount());
    }

    @Test
    void closureCycleFilterSkipsDownstreamCycleAndKeepsExplicitRoot() {
        DummyWorld world = new DummyWorld();
        List<IRecipe> cycle = new ArrayList<>();
        cycle.add(recipe("cycle_filter_diamond", new ItemStack(Items.DIAMOND),
              new ItemStack(Items.IRON_INGOT)));
        cycle.add(recipe("cycle_filter_iron", new ItemStack(Items.IRON_INGOT),
              new ItemStack(Items.DIAMOND)));
        List<QIOWorkbenchConfiguration.EncodedPattern> roots =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world,
                    Collections.singletonList(new ItemStack(Items.DIAMOND)), cycle, 32);
        QIOWorkbenchRecipeCatalog.ClosureResult filtered =
              QIOWorkbenchRecipeCatalog.resolveClosure(
                    QIOWorkbenchRecipeCatalog.prepareClosure(world, roots,
                          new QIOWorkbenchConfiguration(), QIOWorkbenchClosureMode.ALL,
                          true, cycle, 32), () -> false);

        assertEquals(1, filtered.getPatterns().size());
        assertEquals(new ResourceLocation("test", "cycle_filter_diamond"),
              filtered.getPatterns().get(0).getRecipeId());
        assertEquals(1, filtered.getCycleCount());
        assertEquals(1, filtered.getSkippedCyclicRecipeCount());

        List<IRecipe> self = Collections.singletonList(recipe("cycle_filter_double",
              new ItemStack(Items.EMERALD, 2), new ItemStack(Items.EMERALD)));
        List<QIOWorkbenchConfiguration.EncodedPattern> selfRoots =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world,
                    Collections.singletonList(new ItemStack(Items.EMERALD)), self, 8);
        QIOWorkbenchRecipeCatalog.ClosureResult selfFiltered =
              QIOWorkbenchRecipeCatalog.resolveClosure(
                    QIOWorkbenchRecipeCatalog.prepareClosure(world, selfRoots,
                          new QIOWorkbenchConfiguration(), QIOWorkbenchClosureMode.ALL,
                          true, self, 8), () -> false);
        assertEquals(1, selfFiltered.getPatterns().size());
        assertEquals(1, selfFiltered.getCycleCount());
        assertEquals(0, selfFiltered.getSkippedCyclicRecipeCount());
    }

    @Test
    void preferredClosureCycleFilterFallsBackToNextSafeRoute() {
        DummyWorld world = new DummyWorld();
        List<IRecipe> recipes = new ArrayList<>();
        recipes.add(recipe("cycle_fallback_root", new ItemStack(Items.EMERALD),
              new ItemStack(Items.DIAMOND)));
        recipes.add(recipe("cycle_fallback_a_cycle", new ItemStack(Items.DIAMOND),
              new ItemStack(Items.EMERALD)));
        recipes.add(recipe("cycle_fallback_b_safe", new ItemStack(Items.DIAMOND),
              new ItemStack(Items.IRON_INGOT)));
        List<QIOWorkbenchConfiguration.EncodedPattern> roots =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world,
                    Collections.singletonList(new ItemStack(Items.EMERALD)), recipes, 32);
        QIOWorkbenchRecipeCatalog.ClosureResult filtered =
              QIOWorkbenchRecipeCatalog.resolveClosure(
                    QIOWorkbenchRecipeCatalog.prepareClosure(world, roots,
                          new QIOWorkbenchConfiguration(),
                          QIOWorkbenchClosureMode.PREFERRED, true, recipes, 32),
                    () -> false);

        assertEquals(2, filtered.getPatterns().size());
        assertEquals(new ResourceLocation("test", "cycle_fallback_b_safe"),
              filtered.getPatterns().get(1).getRecipeId());
        assertEquals(1, filtered.getCycleCount());
        assertEquals(1, filtered.getSkippedCyclicRecipeCount());
    }

    @Test
    void largeGlobalDirectoryCachesAllRecipesButClosureVisitsOnlyReachableRecipes() {
        DummyWorld world = new DummyWorld();
        IRecipe rootRecipe = recipe("reachable_root", new ItemStack(Items.EMERALD),
              new ItemStack(Items.DIAMOND));
        IRecipe dependencyRecipe = recipe("reachable_dependency",
              new ItemStack(Items.DIAMOND), new ItemStack(Items.IRON_INGOT));
        List<QIOWorkbenchConfiguration.EncodedPattern> roots =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world,
                    Collections.singletonList(new ItemStack(Items.EMERALD)),
                    java.util.Arrays.asList(rootRecipe, dependencyRecipe), 32);

        List<IRecipe> recipes = new ArrayList<>(10_002);
        recipes.add(rootRecipe);
        recipes.add(dependencyRecipe);
        for (int index = 0; index < 10_000; index++) {
            ItemStack output = new ItemStack(Items.COAL);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("noise", index);
            output.setTagCompound(tag);
            recipes.add(recipe("unreachable_" + index, output,
                  new ItemStack(Items.GOLD_INGOT)));
        }

        QIOWorkbenchRecipeCatalog.RecipeOutputIndex recipeIndex = assertTimeout(
              Duration.ofSeconds(5), () ->
                    QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(world, recipes));
        assertEquals(10_002, recipeIndex.getRecipeCount());
        QIOWorkbenchRecipeCatalog.ClosureCapture capture =
              QIOWorkbenchRecipeCatalog.beginClosureCapture(world, roots,
                    new QIOWorkbenchConfiguration(), QIOWorkbenchClosureMode.ALL,
                    false, recipeIndex, 32);
        QIOWorkbenchRecipeCatalog.ClosureInput input = assertTimeout(Duration.ofSeconds(5), () -> {
            while (!capture.process(Integer.MAX_VALUE, Long.MAX_VALUE)) {
                // The unbounded static capture should finish in one invocation.
            }
            return capture.finish();
        });
        QIOWorkbenchRecipeCatalog.ClosureResult result =
              QIOWorkbenchRecipeCatalog.resolveClosure(input, () -> false);

        assertEquals(1, capture.getProcessedRecipeCount());
        assertEquals(2, capture.getQueuedOutputCount());
        assertEquals(2, result.getPatterns().size());
        assertFalse(result.isTruncated());
    }

    @Test
    void downstreamClosureNeverCallsForgeRecipesAfterGlobalDirectoryBuild() {
        DummyWorld world = new DummyWorld();
        IRecipe rootRecipe = recipe("pure_directory_root", new ItemStack(Items.EMERALD),
              new ItemStack(Items.DIAMOND));
        GuardedRecipe dependencyRecipe = guardedRecipe("pure_directory_dependency",
              new ItemStack(Items.DIAMOND), new ItemStack(Items.IRON_INGOT));
        List<IRecipe> recipes = java.util.Arrays.asList(rootRecipe, dependencyRecipe);
        List<QIOWorkbenchConfiguration.EncodedPattern> roots =
              QIOWorkbenchRecipeCatalog.resolveEncodedTargets(world,
                    Collections.singletonList(new ItemStack(Items.EMERALD)), recipes, 32);
        QIOWorkbenchRecipeCatalog.RecipeOutputIndex recipeIndex =
              QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(world, recipes);
        QIOWorkbenchRecipeCatalog.ClosureCapture capture =
              QIOWorkbenchRecipeCatalog.beginClosureCapture(world, roots,
                    new QIOWorkbenchConfiguration(), QIOWorkbenchClosureMode.ALL,
                    false, recipeIndex, 32);
        dependencyRecipe.rejectFurtherCalls();

        QIOWorkbenchRecipeCatalog.ClosureResult result = assertTimeout(
              Duration.ofSeconds(5), () -> CompletableFuture.supplyAsync(() -> {
                  while (!capture.process(128, Long.MAX_VALUE)) {
                      // Exercise the same incremental lookup used by the planning worker.
                  }
                  return QIOWorkbenchRecipeCatalog.resolveClosure(capture.finish(),
                        () -> false);
              }).join());

        assertEquals(2, result.getPatterns().size());
        assertFalse(result.isTruncated());
    }

    @Test
    void globalRecipeOutputIndexIsReusedAndInvalidatedWithCatalogLifecycle() {
        QIORecipeCatalogService.INSTANCE.clear();
        DummyWorld world = new DummyWorld();
        try {
            QIORecipeCatalogService.INSTANCE.refresh(world);
            QIOWorkbenchRecipeCatalog.RecipeOutputIndex first =
                  QIORecipeCatalogService.INSTANCE.getRecipeOutputIndex(world);
            assertSame(first, QIORecipeCatalogService.INSTANCE.getRecipeOutputIndex(world));

            QIORecipeCatalogService.INSTANCE.refresh(world);
            QIOWorkbenchRecipeCatalog.RecipeOutputIndex refreshed =
                  QIORecipeCatalogService.INSTANCE.getRecipeOutputIndex(world);
            assertNotSame(first, refreshed);

            QIORecipeCatalogService.INSTANCE.clear();
            QIORecipeCatalogService.INSTANCE.refresh(world);
            assertNotSame(refreshed,
                  QIORecipeCatalogService.INSTANCE.getRecipeOutputIndex(world));
        } finally {
            QIORecipeCatalogService.INSTANCE.clear();
        }
    }

    @Test
    void frequencyPlanningViewNeverCallsForgeRecipeAfterGlobalCompilation() {
        DummyWorld world = new DummyWorld();
        GuardedRecipe recipe = guardedRecipe("compiled_frequency_view",
              new ItemStack(Items.DIAMOND), new ItemStack(Items.IRON_INGOT));
        ResourceLocation recipeId = recipe.getRegistryName();
        QIOWorkbenchRecipeCatalog.Snapshot definitionSource =
              QIOWorkbenchRecipeCatalog.capture(world, Collections.singletonList(recipe),
                    ignored -> 0, 8, 8, 8);
        QIOWorkbenchRecipeCatalog.RecipeDefinition definition =
              definitionSource.getRecipeDefinition(recipeId);
        assertNotNull(definition);
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(Items.IRON_INGOT));
        QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
        assertTrue(configuration.putEncodedPattern(
              new QIOWorkbenchConfiguration.EncodedPattern(UUID.randomUUID(), recipeId,
                    definition.getSignature(), definition.getOutput(),
                    definition.getOutputAmount(), grid)));
        QIOWorkbenchRecipeCatalog.RecipeOutputIndex index =
              QIOWorkbenchRecipeCatalog.RecipeOutputIndex.build(world,
                    Collections.singletonList(recipe));

        recipe.rejectFurtherCalls();
        QIOWorkbenchRecipeCatalog.Snapshot workerView = index.snapshotFor(configuration);
        List<QIOPlanningRoute> routes = workerView.materialize(definition.getOutput(),
              Collections.emptyMap(), Collections.emptySet(), ignored -> 0);

        assertEquals(1, routes.size());
        assertNotNull(workerView.getPattern(routes.get(0).getStableId()));
    }

    @Test
    void configuredCandidateOrderSurvivesPlanningAndAvailabilityStillWins() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromStacks(new ItemStack(Items.IRON_INGOT),
              new ItemStack(Items.GOLD_INGOT)));
        ShapedRecipes recipe = new ShapedRecipes("test", 1, 1, ingredients,
              new ItemStack(Items.DIAMOND));
        ResourceLocation recipeId = new ResourceLocation("test", "configured_candidates");
        recipe.setRegistryName(recipeId);
        QIOWorkbenchRecipeCatalog.Snapshot catalog = QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 0, 8, 8, 8);
        QIOWorkbenchRecipeCatalog.RecipeDefinition definition =
              catalog.getRecipeDefinition(recipeId);
        assertNotNull(definition);
        List<QIOWorkbenchRecipeCatalog.CandidateDefinition> candidates = definition
              .getIngredients().get(0).getCandidates();
        assertEquals(2, candidates.size());
        QIOWorkbenchRecipeCatalog.CandidateDefinition preferred = candidates.get(1);
        QIOWorkbenchRecipeCatalog.CandidateDefinition alternative = candidates.get(0);
        List<String> defaultIds = definition.getCandidateIds(0);

        QIOWorkbenchConfiguration configuration = new QIOWorkbenchConfiguration();
        assertTrue(configuration.moveCandidate(recipeId.toString(),
              definition.getSignature(), 0, defaultIds, preferred.getCandidateId(),
              -1, true));
        PortableResourceDescriptor output = PortableResourceDescriptor.item(
              new ItemStack(Items.DIAMOND));
        Map<ResourceLocation, Long> priorities = Collections.singletonMap(recipeId, 7L);
        List<QIOPlanningRoute> ordered = catalog.materialize(output,
              Collections.emptyMap(), Collections.emptySet(), priorities, 8,
              configuration);
        assertEquals(preferred.getResource(), onlyInput(ordered.get(0)));
        assertEquals(1, ordered.get(0).getCandidateInputs().size());
        assertEquals(preferred.getCandidateId(), ordered.get(0).getCandidateInputs().get(0)
              .getOptions().get(0).getCandidateId());
        assertTrue(ordered.get(0).getVariantPriority() >
              ordered.get(1).getVariantPriority());

        QIOPlanningSnapshot planning = new QIOPlanningSnapshot(
              new QIOPlanSourceRevisions(1, 2, 3, 4, 5, 6, 7, 8),
              Collections.emptyMap(), ordered, 16, 64, 1_000);
        assertEquals(preferred.getResource(), onlyInput(
              planning.getRoutesProducing(output).get(0)));
        QIOPlanningResult planned = QIOPlanner.INSTANCE.plan(new QIOPlanningRequest(
              UUID.randomUUID(), 1, planning, output, 1), () -> false);
        assertEquals(QIOPlanningResult.Status.SUCCESS, planned.getStatus());
        assertEquals(1L, planned.getPlan().getExternalRequirements().get(
              preferred.getResource()));
        assertTrue(planned.getPlan().getMaterialRequirements().getExactAmounts().isEmpty());
        assertEquals(1, planned.getPlan().getMaterialRequirements()
              .getCandidateRequirements().size());

        Set<PortableResourceDescriptor> producible = new LinkedHashSet<>();
        producible.add(alternative.getResource());
        List<QIOPlanningRoute> craftableFirst = catalog.materialize(output,
              Collections.emptyMap(), producible, priorities, 8, configuration);
        assertEquals(alternative.getResource(), onlyInput(craftableFirst.get(0)));

        Map<PortableResourceDescriptor, Long> available = new LinkedHashMap<>();
        available.put(alternative.getResource(), 1L);
        List<QIOPlanningRoute> availableFirst = catalog.materialize(output, available,
              Collections.emptySet(), priorities, 8, configuration);
        assertEquals(alternative.getResource(), onlyInput(availableFirst.get(0)));
        QIOWorkbenchRecipePattern executable = catalog.resolveExecutablePattern(
              recipeId.toString(), available, ordered.get(0).getGuaranteedOutputs(),
              configuration);
        assertNotNull(executable);
        assertEquals(alternative.getResource(), executable.getExactInputs().keySet()
              .iterator().next());

        assertTrue(configuration.setCandidateEnabled(recipeId.toString(),
              definition.getSignature(), 0, defaultIds, alternative.getCandidateId(),
              false));
        List<QIOPlanningRoute> filtered = catalog.materialize(output,
              Collections.emptyMap(), Collections.emptySet(), priorities, 8,
              configuration);
        assertEquals(1, filtered.size());
        assertEquals(preferred.getResource(), onlyInput(filtered.get(0)));
        assertTrue(filtered.get(0).getCandidateInputs().isEmpty());
    }

    private static PortableResourceDescriptor onlyInput(QIOPlanningRoute route) {
        assertEquals(1, route.getExactInputs().size());
        return route.getExactInputs().keySet().iterator().next();
    }

    private static ShapedRecipes recipe(String path, ItemStack output, ItemStack input) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromStacks(input));
        ShapedRecipes recipe = new ShapedRecipes("test", 1, 1, ingredients, output);
        recipe.setRegistryName(new ResourceLocation("test", path));
        return recipe;
    }

    private static GuardedRecipe guardedRecipe(String path, ItemStack output,
          ItemStack input) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromStacks(input));
        GuardedRecipe recipe = new GuardedRecipe(ingredients, output);
        recipe.setRegistryName(new ResourceLocation("test", path));
        return recipe;
    }

    private static final class GuardedRecipe extends ShapedRecipes {

        private boolean rejectFurtherCalls;

        private GuardedRecipe(NonNullList<Ingredient> ingredients, ItemStack output) {
            super("test", 1, 1, ingredients, output);
        }

        private void rejectFurtherCalls() {
            rejectFurtherCalls = true;
        }

        private void checkThreadSafePhase() {
            if (rejectFurtherCalls) {
                throw new AssertionError("Closure lookup called a Forge recipe after caching");
            }
        }

        @Override
        public boolean matches(InventoryCrafting inventory, World world) {
            checkThreadSafePhase();
            return super.matches(inventory, world);
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inventory) {
            checkThreadSafePhase();
            return super.getCraftingResult(inventory);
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inventory) {
            checkThreadSafePhase();
            return super.getRemainingItems(inventory);
        }
    }

    private static final class RawWildcardIngredient extends Ingredient {

        private final ItemStack wildcard;

        private RawWildcardIngredient(ItemStack wildcard) {
            super(wildcard);
            this.wildcard = wildcard.copy();
        }

        @Override
        public ItemStack[] getMatchingStacks() {
            return new ItemStack[]{wildcard.copy()};
        }
    }

    private static final class TestFluidContainerItem extends Item {

        private TestFluidContainerItem() {
            setMaxStackSize(1);
        }

        @Override
        public ICapabilityProvider initCapabilities(ItemStack stack,
              @Nullable NBTTagCompound nbt) {
            return new FluidHandlerItemStack(stack, 1_000);
        }

        @Override
        public boolean hasContainerItem(ItemStack stack) {
            return true;
        }

        @Override
        public ItemStack getContainerItem(ItemStack stack) {
            return new ItemStack(this);
        }
    }
}
