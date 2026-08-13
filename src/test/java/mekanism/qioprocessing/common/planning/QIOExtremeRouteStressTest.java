package mekanism.qioprocessing.common.planning;

import mekanism.common.TestBootstrap;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static guardrails for large modpacks and routes with conserved byproducts. */
class QIOExtremeRouteStressTest {

    private static final QIOPlanSourceRevisions REVISIONS =
          new QIOPlanSourceRevisions(1, 2, 3, 4, 5, 6, 7, 8);
    private static PortableResourceDescriptor target;
    private static PortableResourceDescriptor a;
    private static PortableResourceDescriptor b;
    private static PortableResourceDescriptor c;
    private static PortableResourceDescriptor d;

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.bootstrapMinecraft();
        target = PortableResourceDescriptor.named(PortableResourceDescriptor.Kind.ITEM,
              "test:stress_target", 0, null);
        a = PortableResourceDescriptor.named(PortableResourceDescriptor.Kind.ITEM,
              "test:stress_a", 0, null);
        b = PortableResourceDescriptor.named(PortableResourceDescriptor.Kind.ITEM,
              "test:stress_b", 0, null);
        c = PortableResourceDescriptor.named(PortableResourceDescriptor.Kind.ITEM,
              "test:stress_c", 0, null);
        d = PortableResourceDescriptor.named(PortableResourceDescriptor.Kind.ITEM,
              "test:stress_d", 0, null);
    }

    @Test
    void tenThousandWorkbenchRecipesStayLogicalAndCaptureUnderFiveSeconds() {
        List<net.minecraft.item.crafting.IRecipe> recipes = new ArrayList<>(10_000);
        for (int index = 0; index < 10_000; index++) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.add(Ingredient.fromStacks(new ItemStack(Items.IRON_INGOT)));
            ShapedRecipes recipe = new ShapedRecipes("test", 1, 1, ingredients,
                  new ItemStack(Items.DIAMOND));
            recipe.setRegistryName(new ResourceLocation("stress", "workbench_" + index));
            recipes.add(recipe);
        }
        QIOWorkbenchRecipeCatalog.Snapshot snapshot = assertTimeout(Duration.ofSeconds(5),
              () -> QIOWorkbenchRecipeCatalog.capture(null, recipes, ignored -> 0,
                    64, 4_096, 64));
        assertEquals(10_000, snapshot.getRecipeIds().size());
        assertEquals(0, snapshot.getCachedPatternCount());
        PortableResourceDescriptor diamond = PortableResourceDescriptor.item(
              new ItemStack(Items.DIAMOND));
        assertTrue(snapshot.getDeclaredOutputs().contains(diamond));
        Map<ResourceLocation, Long> priorities = new LinkedHashMap<>();
        for (ResourceLocation recipeId : snapshot.getRecipeIds()) {
            priorities.put(recipeId, 0L);
        }
        List<QIOPlanningRoute> bounded = assertTimeout(Duration.ofSeconds(5), () ->
              snapshot.materialize(diamond, Collections.emptyMap(),
                    snapshot.getDeclaredOutputs(), priorities, 128));
        assertEquals(128, bounded.size());
        assertEquals(128, snapshot.getCachedPatternCount());
    }

    @Test
    void multiCandidateRecipeDoesNotCartesianExpandUntilRequested() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromStacks(candidateStacks(64)));
        ingredients.add(Ingredient.fromStacks(candidateStacks(64)));
        ShapedRecipes recipe = new ShapedRecipes("test", 2, 1, ingredients,
              new ItemStack(Items.DIAMOND));
        ResourceLocation recipeId = new ResourceLocation("stress", "candidate_bomb");
        recipe.setRegistryName(recipeId);
        QIOWorkbenchRecipeCatalog.Snapshot snapshot = QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 7, 64, 4_096, 64);
        assertEquals(0, snapshot.getCachedPatternCount());
        PortableResourceDescriptor chosen = PortableResourceDescriptor.item(candidateStacks(64)[63]);
        Map<PortableResourceDescriptor, Long> available = Collections.singletonMap(chosen, 1L);
        Map<ResourceLocation, Long> priorities = Collections.singletonMap(recipeId, 7L);
        List<QIOPlanningRoute> routes = assertTimeout(Duration.ofSeconds(5), () -> snapshot
              .materialize(PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)), available,
                    Collections.emptySet(), priorities));
        assertTrue(routes.size() <= 64);
        assertFalse(routes.isEmpty());
        assertTrue(routes.get(0).getExactInputs().containsKey(chosen));
        assertTrue(snapshot.getCachedPatternCount() <= 64);
    }

    @Test
    void availableCandidateBeyondCartesianPrefixIsStillSelected() {
        ItemStack[] candidates = candidateStacks(10);
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (int slot = 0; slot < 4; slot++) {
            ingredients.add(Ingredient.fromStacks(candidates));
        }
        ShapedRecipes recipe = new ShapedRecipes("test", 2, 2, ingredients,
              new ItemStack(Items.DIAMOND));
        ResourceLocation recipeId = new ResourceLocation("stress", "repeated_candidates");
        recipe.setRegistryName(recipeId);
        QIOWorkbenchRecipeCatalog.Snapshot snapshot = QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 7, 10, 4_096, 64);

        PortableResourceDescriptor chosen = PortableResourceDescriptor.item(candidates[9]);
        Map<PortableResourceDescriptor, Long> available = Collections.singletonMap(chosen, 4L);
        Map<ResourceLocation, Long> priorities = Collections.singletonMap(recipeId, 7L);
        List<QIOPlanningRoute> routes = assertTimeout(Duration.ofSeconds(5), () -> snapshot
              .materialize(PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)),
                    available, Collections.emptySet(), priorities));

        assertFalse(routes.isEmpty());
        QIOPlanningRoute selected = routes.get(0);
        assertEquals(Collections.singletonMap(chosen, 4L), selected.getExactInputs());

        QIOWorkbenchRecipeCatalog.Snapshot cold = QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 7, 10, 1, 64);
        assertEquals(0, cold.getCachedPatternCount());
        QIOWorkbenchRecipePattern recovered = cold.getPattern(selected.getStableId());
        assertNotNull(recovered);
        assertEquals(selected.getVariantId(), recovered.getVariantId());
        assertEquals(Collections.singletonMap(chosen, 4L), recovered.getExactInputs());
    }

    @Test
    void candidateCompatibilityIsProfiledOncePerLogicalRecipe() {
        ItemStack[] candidates = candidateStacks(32);
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.fromStacks(candidates));
        ingredients.add(Ingredient.fromStacks(candidates));
        CountingShapedRecipe recipe = new CountingShapedRecipe(ingredients,
              new ItemStack(Items.DIAMOND));
        ResourceLocation recipeId = new ResourceLocation("stress", "profiled_candidates");
        recipe.setRegistryName(recipeId);
        QIOWorkbenchRecipeCatalog.Snapshot snapshot = QIOWorkbenchRecipeCatalog.capture(null,
              Collections.singletonList(recipe), ignored -> 0, 32, 1_024, 64);

        List<QIOPlanningRoute> routes = assertTimeout(Duration.ofSeconds(5), () -> snapshot
              .materialize(PortableResourceDescriptor.item(new ItemStack(Items.DIAMOND)),
                    Collections.emptyMap(), Collections.emptySet(),
                    Collections.singletonMap(recipeId, 0L)));

        assertFalse(routes.isEmpty());
        // 1,024 exact variants plus one 32-candidate profile for each occupied slot.
        assertTrue(recipe.matchesCalls <= 1_100,
              "candidate compatibility was revalidated for every exact variant: " +
                    recipe.matchesCalls);
    }

    @Test
    void tenThousandMachineRoutesAndConservedByproductCyclesUseBoundedClosure() {
        List<QIOPlanningRoute> routes = new ArrayList<>(10_002);
        for (int index = 0; index < 10_000; index++) {
            PortableResourceDescriptor noise = PortableResourceDescriptor.named(
                  PortableResourceDescriptor.Kind.ITEM, "test:noise_" + index, 0, null);
            routes.add(route("noise_" + index, noise, a, 0));
        }
        // A+B -> A is a neutral self-cycle and must terminate as an unresolved cycle.
        routes.add(QIOPlanningRoute.builder(ProviderKind.MEKANISM,
                    new ResourceLocation("stress", "machine"), "neutral", "neutral")
              .input(a, 1).input(b, 1).output(a, 1).build());
        // B+D -> C+B conserves B while producing the requested C.
        routes.add(QIOPlanningRoute.builder(ProviderKind.MEKANISM,
                    new ResourceLocation("stress", "machine"), "catalytic", "catalytic")
              .input(b, 1).input(d, 1).output(c, 1).output(b, 1).build());

        QIOPlanningRouteClosure.ProviderIndex index = assertTimeout(Duration.ofSeconds(5),
              () -> QIOPlanningRouteClosure.indexProviders(routes));
        Set<PortableResourceDescriptor> outputs = new LinkedHashSet<>(index.getOutputs());
        List<QIOPlanningRoute> closure = assertTimeout(Duration.ofSeconds(5), () ->
              QIOPlanningRouteClosure.collect(c, Collections.emptyMap(), index,
                    emptyWorkbench(), Collections.emptyMap(), outputs, 1_024));
        assertEquals(1, closure.stream().filter(route -> route.getRouteId().equals("catalytic"))
              .count());
        assertEquals(0, closure.stream().filter(route -> route.getRouteId().startsWith("noise_"))
              .count());

        QIOPlanningResult catalytic = assertTimeout(Duration.ofSeconds(5), () ->
              plan(c, 1, Collections.emptyMap(), closure, 128, 1_024, 10_000));
        assertEquals(QIOPlanningResult.Status.SUCCESS, catalytic.getStatus());
        Map<PortableResourceDescriptor, Long> expectedExternal = new LinkedHashMap<>();
        expectedExternal.put(b, 1L);
        expectedExternal.put(d, 1L);
        assertEquals(expectedExternal, catalytic.getPlan().getExternalRequirements());
        assertEquals(1, catalytic.getPlan().getSteps().size());

        List<QIOPlanningRoute> neutralClosure = QIOPlanningRouteClosure.collect(a,
              Collections.emptyMap(), index, emptyWorkbench(), Collections.emptyMap(), outputs,
              1_024);
        QIOPlanningResult neutral = assertTimeout(Duration.ofSeconds(5), () ->
              plan(a, 1, Collections.emptyMap(), neutralClosure, 128, 1_024, 10_000));
        assertTrue(neutral.getStatus() == QIOPlanningResult.Status.UNRESOLVABLE_CYCLE ||
              neutral.getStatus() == QIOPlanningResult.Status.CYCLE_REQUIRES_SCC);
    }

    @Test
    void tenThousandSharedBranchesCollapseIntoOneDownstreamStep() {
        PortableResourceDescriptor shared = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:shared_intermediate", 0, null);
        PortableResourceDescriptor raw = PortableResourceDescriptor.named(
              PortableResourceDescriptor.Kind.ITEM, "test:shared_raw", 0, null);
        ResourceLocation provider = new ResourceLocation("stress", "shared_machine");
        QIOPlanningRoute.Builder root = QIOPlanningRoute.builder(ProviderKind.MEKANISM,
              provider, "shared_root", "shared_root").output(target, 1);
        List<QIOPlanningRoute> routes = new ArrayList<>(10_002);
        for (int index = 0; index < 10_000; index++) {
            PortableResourceDescriptor branch = PortableResourceDescriptor.named(
                  PortableResourceDescriptor.Kind.ITEM, "test:shared_branch_" + index,
                  0, null);
            root.input(branch, 1);
            routes.add(QIOPlanningRoute.builder(ProviderKind.MEKANISM, provider,
                        "shared_branch_" + index, "shared_branch_" + index)
                  .input(shared, 1).output(branch, 1).build());
        }
        routes.add(root.build());
        routes.add(QIOPlanningRoute.builder(ProviderKind.MEKANISM, provider,
                    "shared_source", "shared_source")
              .input(raw, 1).output(shared, 1).build());

        QIOPlanningResult result = assertTimeout(Duration.ofSeconds(5), () ->
              plan(target, 1, Collections.emptyMap(), routes, 8, 20_000, 30_000));

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(20_001, result.getPlannedOperations());
        assertEquals(Collections.singletonMap(raw, 10_000L),
              result.getPlan().getExternalRequirements());
        assertEquals(10_002, result.getPlan().getSteps().size());
        QIOPlanStep sharedStep = result.getPlan().getSteps().stream()
              .filter(step -> step.getRouteId().equals("shared_source"))
              .findFirst().orElseThrow(AssertionError::new);
        assertEquals(10_000, sharedStep.getOperations());
    }

    @Test
    void tenThousandDeepRouteChainUsesIterativePlanningAndValidation() {
        ResourceLocation provider = new ResourceLocation("stress", "deep_machine");
        List<QIOPlanningRoute> routes = new ArrayList<>(10_000);
        PortableResourceDescriptor output = target;
        PortableResourceDescriptor raw = null;
        for (int index = 0; index < 10_000; index++) {
            PortableResourceDescriptor input = PortableResourceDescriptor.named(
                  PortableResourceDescriptor.Kind.ITEM, "test:deep_input_" + index,
                  0, null);
            routes.add(QIOPlanningRoute.builder(ProviderKind.MEKANISM, provider,
                        "deep_" + index, "deep_" + index)
                  .input(input, 1).output(output, 1).build());
            output = input;
            raw = input;
        }

        PortableResourceDescriptor finalRaw = raw;
        QIOPlanningResult result = assertTimeout(Duration.ofSeconds(5), () ->
              plan(target, 1, Collections.emptyMap(), routes, 20_000, 20_000,
                    20_000));

        assertEquals(QIOPlanningResult.Status.SUCCESS, result.getStatus());
        assertEquals(10_000, result.getPlannedOperations());
        assertEquals(10_000, result.getPlan().getSteps().size());
        assertEquals(Collections.singletonMap(finalRaw, 1L),
              result.getPlan().getExternalRequirements());
    }

    private static ItemStack[] candidateStacks(int count) {
        ItemStack[] stacks = new ItemStack[count];
        for (int index = 0; index < count; index++) {
            ItemStack stack = new ItemStack(Items.DYE, 1, index & 15);
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            tag.setInteger("variant", index);
            stack.setTagCompound(tag);
            stacks[index] = stack;
        }
        return stacks;
    }

    private static final class CountingShapedRecipe extends ShapedRecipes {

        private int matchesCalls;

        private CountingShapedRecipe(NonNullList<Ingredient> ingredients, ItemStack output) {
            super("test", 2, 1, ingredients, output);
        }

        @Override
        public boolean matches(InventoryCrafting inventory, World world) {
            matchesCalls++;
            return super.matches(inventory, world);
        }
    }

    private static QIOPlanningRoute route(String id, PortableResourceDescriptor output,
          PortableResourceDescriptor input, long priority) {
        return QIOPlanningRoute.builder(ProviderKind.MEKANISM,
                    new ResourceLocation("stress", "machine"), id, id)
              .priority(priority).input(input, 1).output(output, 1).build();
    }

    private static QIOWorkbenchRecipeCatalog.Snapshot emptyWorkbench() {
        return QIOWorkbenchRecipeCatalog.capture(null, Collections.emptyList(), ignored -> 0,
              64, 4_096, 64);
    }

    private static QIOPlanningResult plan(PortableResourceDescriptor root, long amount,
          Map<PortableResourceDescriptor, Long> available, List<QIOPlanningRoute> routes,
          int maximumDepth, int maximumNodes, long maximumOperations) {
        QIOPlanningSnapshot snapshot = new QIOPlanningSnapshot(REVISIONS, available, routes,
              maximumDepth, maximumNodes, maximumOperations);
        try {
            return QIOPlanner.INSTANCE.plan(new QIOPlanningRequest(
                  java.util.UUID.randomUUID(), 1, snapshot, root, amount), () -> false);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
