package mekanism.common.recipe.cache;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import mekanism.common.recipe.machines.EnrichmentRecipe;
import mekanism.common.recipe.machines.SawmillRecipe;

class RecipeExecutionPlannerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void resourceSnapshotDefensivelyCopiesItems() {
        ItemStack source = new ItemStack(Items.DIAMOND, 4);
        ImmutableResourceSnapshot snapshot = ImmutableResourceSnapshot.of(source);
        source.setCount(1);

        ItemStack first = snapshot.getItemCopy();
        ItemStack second = snapshot.getItemCopy();
        assertEquals(4, first.getCount());
        assertEquals(4, second.getCount());
        assertNotSame(first, second);
        first.setCount(2);
        assertEquals(4, snapshot.getItemCopy().getCount());
    }

    @Test
    void identicalSnapshotProducesIdenticalPlan() {
        Map<String, ImmutableResourceSnapshot> inputs = new LinkedHashMap<>();
        inputs.put("item", ImmutableResourceSnapshot.of(new ItemStack(Items.IRON_INGOT, 12)));
        RecipeRunSnapshot snapshot = RecipeRunSnapshot.builder("mekanism:test")
              .recipeSignature("input=iron;output=diamond")
              .globalRecipeGeneration(9)
              .categoryRecipeGeneration(3)
              .machineStateVersion(17)
              .randomSeed(42)
              .operatingTicks(2)
              .energy(100, 5)
              .inputs(inputs)
              .build();
        Map<String, Long> costs = Collections.singletonMap("item", 2L);

        RecipeExecutionPlan first = RecipeExecutionPlanner.calculate(snapshot, 8, 5, 10,
              costs, Collections.emptyMap());
        RecipeExecutionPlan second = RecipeExecutionPlanner.calculate(snapshot, 8, 5, 10,
              costs, Collections.emptyMap());

        assertEquals(first.getOperations(), second.getOperations());
        assertEquals(first.getEnergyAsDouble(), second.getEnergyAsDouble());
        assertEquals(first.getNewOperatingTicks(), second.getNewOperatingTicks());
        assertEquals(first.getRandomSeed(), second.getRandomSeed());
        assertTrue(first.isValidFor(snapshot));
    }

    @Test
    void staleVersionOrRecipeSignatureRejectsWholePlan() {
        RecipeRunSnapshot captured = RecipeRunSnapshot.builder("mekanism:test")
              .recipeSignature("old-output")
              .recipeGeneration(4)
              .machineStateVersion(7)
              .build();
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(captured, 1, 0, 1);

        RecipeRunSnapshot stateChanged = RecipeRunSnapshot.builder("mekanism:test")
              .recipeSignature("old-output")
              .recipeGeneration(4)
              .machineStateVersion(8)
              .build();
        RecipeRunSnapshot recipeChanged = RecipeRunSnapshot.builder("mekanism:test")
              .recipeSignature("new-output")
              .recipeGeneration(5)
              .machineStateVersion(7)
              .build();

        assertFalse(plan.isValidFor(stateChanged));
        assertFalse(plan.isValidFor(recipeChanged));
    }

    @Test
    void differentRandomSeedRejectsPlan() {
        RecipeRunSnapshot captured = RecipeRunSnapshot.builder("mekanism:test")
              .recipeSignature("stable")
              .recipeGeneration(4)
              .machineStateVersion(7)
              .randomSeed(11)
              .build();
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(captured, 1, 0, 1);

        RecipeRunSnapshot reseeded = RecipeRunSnapshot.builder("mekanism:test")
              .recipeSignature("stable")
              .recipeGeneration(4)
              .machineStateVersion(7)
              .randomSeed(12)
              .build();

        assertFalse(plan.isValidFor(reseeded));
    }

    @Test
    void compiledRecipeCalculatesInputOutputAndCompletionCosts() {
        EnrichmentRecipe recipe = new EnrichmentRecipe(new ItemStack(Items.IRON_INGOT, 2),
              new ItemStack(Items.DIAMOND, 1));
        Map<String, ImmutableResourceSnapshot> stored = new LinkedHashMap<>();
        stored.put("item.0", ImmutableResourceSnapshot.of(new ItemStack(Items.IRON_INGOT, 10)));
        stored.put("item.1", ImmutableResourceSnapshot.empty());
        Map<String, ImmutableResourceSnapshot> capacities = new LinkedHashMap<>();
        capacities.put("item.capacity.0", ImmutableResourceSnapshot.descriptor("items", 64));
        capacities.put("item.capacity.1", ImmutableResourceSnapshot.descriptor("items", 64));
        RecipeRunSnapshot snapshot = RecipeRunSnapshot.builder("mekanism:enrichment")
              .recipeSignature(recipe.semanticSignature())
              .recipeGeneration(4)
              .machineStateVersion(8)
              .operatingTicks(9)
              .requiredTicks(10)
              .energy(100, 5)
              .inputs(stored)
              .outputs(capacities)
              .recipeSemantics(RecipeSemanticsCompiler.compile(recipe, ""))
              .build();

        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot, 8, 5, 10);

        assertEquals(5, plan.getOperations());
        assertEquals(0, plan.getNewOperatingTicks());
        assertEquals(1, plan.getCompletionConsumption().size());
        assertEquals(10L, plan.getCompletionConsumption().values().iterator().next());
        assertEquals(1, plan.getOutputs().size());
        assertEquals(5, plan.getOutputs().values().iterator().next().getAmount());
    }

    @Test
    void chanceOutputsAreStableForCapturedSeed() {
        SawmillRecipe recipe = new SawmillRecipe(new ItemStack(Blocks.LOG),
              new ItemStack(Blocks.PLANKS), new ItemStack(Items.STICK), 0.5);
        RecipeSemanticsSnapshot semantics = RecipeSemanticsCompiler.compile(recipe, "");
        Map<String, ImmutableResourceSnapshot> stored = new LinkedHashMap<>();
        stored.put("item.0", ImmutableResourceSnapshot.of(new ItemStack(Blocks.LOG, 16)));
        stored.put("item.1", ImmutableResourceSnapshot.empty());
        stored.put("item.2", ImmutableResourceSnapshot.empty());
        Map<String, ImmutableResourceSnapshot> capacities = new LinkedHashMap<>();
        capacities.put("item.capacity.0", ImmutableResourceSnapshot.descriptor("items", 64));
        capacities.put("item.capacity.1", ImmutableResourceSnapshot.descriptor("items", 64));
        capacities.put("item.capacity.2", ImmutableResourceSnapshot.descriptor("items", 64));
        RecipeRunSnapshot snapshot = RecipeRunSnapshot.builder("mekanism:sawmill")
              .recipeSignature(recipe.semanticSignature()).recipeGeneration(2).machineStateVersion(3)
              .randomSeed(9918273).requiredTicks(1).inputs(stored).outputs(capacities)
              .recipeSemantics(semantics).build();

        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot, 8, 0, 1);
        assertEquals(plan.getOutputs(), RecipeExecutionPlanner.calculate(snapshot, 8, 0, 1).getOutputs());
        boolean plannedSecondary = plan.getOutputs().values().stream()
              .anyMatch(value -> value.getItemCopy() != null && value.getItemCopy().getItem() == Items.STICK);
        boolean[] committedSecondary = new boolean[1];
        RecipeRandomContext.run(snapshot.getRandomSeed(), () ->
              committedSecondary[0] = !recipe.getOutput().getSecondaryOutput().isEmpty());
        assertEquals(plannedSecondary, committedSecondary[0]);
    }
}
