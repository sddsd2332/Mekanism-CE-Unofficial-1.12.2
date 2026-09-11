package mekanism.common.recipe.cache;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RecipeLanePlannerTest {

    private static ImmutableResourceSnapshot resource(String type, long amount) {
        return ImmutableResourceSnapshot.descriptor(type, amount);
    }

    private static RecipeSemanticsSnapshot semantics(String input, String output) {
        return new RecipeSemanticsSnapshot(Collections.singletonList(new RecipeResourceFlow("input",
              resource(input, 1), RecipeResourceFlow.Phase.COMPLETION)),
              Collections.singletonList(new RecipeResourceFlow("output", resource(output, 1), RecipeResourceFlow.Phase.OUTPUT)),
              0, 0, true);
    }

    private static RecipeLaneSnapshot.Builder lane(int index, long input) {
        return RecipeLaneSnapshot.builder(index).requiredTicks(1).baselineMaxOperations(4)
              .input("input", resource("ore", input))
              .output("output", ImmutableResourceSnapshot.empty(), 64)
              .recipeSemantics(semantics("ore", "ingot"));
    }

    private static RecipeRunSnapshot snapshot(RecipeLaneSnapshot... lanes) {
        RecipeRunSnapshot.Builder builder = RecipeRunSnapshot.builder("test:lanes")
              .recipeGeneration(7).machineStateVersion(3).randomSeed(498123).energy(100, 2);
        for (RecipeLaneSnapshot lane : lanes) builder.lane(lane);
        return builder.build();
    }

    @Test
    void inputInAnotherLaneOrTopLevelCannotBeBorrowed() {
        RecipeRunSnapshot snapshot = RecipeRunSnapshot.builder("test:isolated")
              .input("input", resource("ore", 100))
              .lane(lane(0, 0).build()).lane(lane(1, 4).build()).build();
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot);
        assertEquals(0, plan.getLane(0).getOperations());
        assertTrue(plan.getLane(0).getInputConsumption().isEmpty());
        assertEquals(4, plan.getLane(1).getOperations());
        assertEquals(4L, plan.getInputConsumption().get("lane.1.input"));
        assertFalse(plan.getOutputs().containsKey("lane.0.output"));
    }

    @Test
    void everyLaneUsesItsOwnProgressDurationAndOperationLimit() {
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot(
              lane(0, 8).operatingTicks(2).requiredTicks(3).baselineMaxOperations(2).build(),
              lane(1, 8).operatingTicks(1).requiredTicks(5).baselineMaxOperations(3).build()));
        assertEquals(2, plan.getLane(0).getOperations());
        assertEquals(0, plan.getLane(0).getNewOperatingTicks());
        assertEquals(2L, plan.getLane(0).getCompletionConsumption().get("input"));
        assertEquals(3, plan.getLane(1).getOperations());
        assertEquals(2, plan.getLane(1).getNewOperatingTicks());
        assertTrue(plan.getLane(1).getCompletionConsumption().isEmpty());
        assertTrue(plan.getLane(1).getOutputs().isEmpty());
        assertEquals(5, plan.getOperations());
        assertEquals(10, plan.getEnergyAsDouble());
    }

    @Test
    void sharedPerTickInputIsAllocatedInLaneIndexOrder() {
        RecipeSemanticsSnapshot semantics = new RecipeSemanticsSnapshot(Arrays.asList(
              new RecipeResourceFlow("input", resource("ore", 1), RecipeResourceFlow.Phase.COMPLETION),
              new RecipeResourceFlow("gas", resource("gas", 1), RecipeResourceFlow.Phase.PER_TICK)),
              Collections.singletonList(new RecipeResourceFlow("output", resource("ingot", 1), RecipeResourceFlow.Phase.OUTPUT)),
              0, 0, true);
        RecipeLaneSnapshot first = lane(0, 8).requiredTicks(4).baselineMaxOperations(2)
              .input("gas", resource("gas", 5), true).recipeSemantics(semantics)
              .perTickInputMultipliers(Collections.singletonMap("gas", 2L)).build();
        RecipeLaneSnapshot second = lane(1, 8).requiredTicks(4).baselineMaxOperations(2)
              .input("gas", resource("gas", 5), true).recipeSemantics(semantics).build();
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot(second, first));
        assertEquals(2, plan.getLane(0).getOperations());
        assertEquals(1, plan.getLane(1).getOperations());
        assertEquals(5L, plan.getPerTickConsumption().get("gas"));
        assertTrue(plan.getCompletionConsumption().isEmpty());
        assertEquals(Arrays.asList(0, 1), Arrays.asList(plan.getLanes().keySet().toArray()));
    }

    @Test
    void perTickInputMultiplierShortageReportsTheInputError() {
        RecipeSemanticsSnapshot semantics = new RecipeSemanticsSnapshot(Arrays.asList(
              new RecipeResourceFlow("input", resource("ore", 1), RecipeResourceFlow.Phase.PER_TICK)),
              Collections.singletonList(new RecipeResourceFlow("output", resource("ingot", 1), RecipeResourceFlow.Phase.OUTPUT)),
              0, 0, true);
        RecipeLaneSnapshot lane = lane(0, 1).requiredTicks(1).baselineMaxOperations(2)
              .input("input", resource("ore", 1)).recipeSemantics(semantics)
              .perTickInputMultipliers(Collections.singletonMap("input", 2L)).build();

        RecipeLanePlan plan = RecipeExecutionPlanner.calculate(snapshot(lane)).getLane(0);

        assertEquals(0, plan.getOperations());
        assertTrue(plan.getErrors().contains("NOT_ENOUGH_INPUT"));
    }

    @Test
    void incompleteLaneDoesNotReserveCompletionInputsFromLaterLane() {
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot(
              lane(0, 1).input("input", resource("ore", 1), true).requiredTicks(4).build(),
              lane(1, 1).input("input", resource("ore", 1), true).build()));
        assertEquals(1, plan.getLane(0).getNewOperatingTicks());
        assertEquals(1L, plan.getInputConsumption().get("input"));
        assertEquals(1, plan.getLane(1).getOperations());
    }

    @Test
    void sharedEnergyUsesPerLaneCostsAndPreservesPausedProgress() {
        RecipeRunSnapshot captured = RecipeRunSnapshot.builder("test:energy").energy(7, 1)
              .lane(lane(0, 1).energyPerTick(5).build())
              .lane(lane(1, 1).energyPerTick(3).operatingTicks(2).requiredTicks(4).build()).build();
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(captured);
        assertEquals(5, plan.getEnergyAsDouble());
        assertEquals(0, plan.getLane(1).getOperations());
        assertEquals(2, plan.getLane(1).getNewOperatingTicks());
        assertTrue(plan.getLane(1).getErrors().contains("NOT_ENOUGH_ENERGY"));
    }

    @Test
    void fullFixedOutputCannotUseSecondarySlotOrAnotherLane() {
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot(
              lane(0, 4).output("output", resource("ingot", 64), 64)
                    .output("secondary", ImmutableResourceSnapshot.empty(), 64).build(),
              lane(1, 4).build()));
        assertEquals(0, plan.getLane(0).getOperations());
        assertEquals(0, plan.getLane(0).getEnergyAsDouble());
        assertTrue(plan.getLane(0).getErrors().contains("NOT_ENOUGH_OUTPUT_SPACE"));
        assertEquals(4, plan.getLane(1).getOperations());
    }

    @Test
    void pooledOutputsReserveSpaceAndTypesAcrossAllFlowsAndLanes() {
        RecipeSemanticsSnapshot twoOutputs = new RecipeSemanticsSnapshot(
              semantics("ore", "ingot").getInputs(), Arrays.asList(
              new RecipeResourceFlow("first", resource("ingot", 1), RecipeResourceFlow.Phase.OUTPUT),
              new RecipeResourceFlow("second", resource("dust", 1), RecipeResourceFlow.Phase.OUTPUT, 0.5)), 0, 0, true);
        RecipeExecutionPlan blocked = RecipeExecutionPlanner.calculate(snapshot(
              lane(0, 1).pooledOutputs(true).recipeSemantics(twoOutputs).build()));
        assertEquals(0, blocked.getOperations());

        RecipeExecutionPlan shared = RecipeExecutionPlanner.calculate(snapshot(
              lane(0, 1).output("output", ImmutableResourceSnapshot.empty(), 64, true).build(),
              lane(1, 1).output("output", ImmutableResourceSnapshot.empty(), 64, true)
                    .recipeSemantics(semantics("ore", "dust")).build()));
        assertEquals(1, shared.getLane(0).getOperations());
        assertEquals(0, shared.getLane(1).getOperations());
    }

    @Test
    void outputLimitIsCapturedForTheProducedItemIncludingNonStackableItems() {
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot(lane(0, 4)
              .pooledOutputs(true).outputLimit("output", resource("ingot", 1), 1).build()));
        assertEquals(1, plan.getOperations());
    }

    @Test
    void chanceSequenceMatchesRunLaneForEveryOperationAndOutput() {
        RecipeSemanticsSnapshot chance = new RecipeSemanticsSnapshot(semantics("ore", "ingot").getInputs(),
              Arrays.asList(new RecipeResourceFlow("a", resource("a", 1), RecipeResourceFlow.Phase.OUTPUT, 0.3, true),
                    new RecipeResourceFlow("b", resource("b", 1), RecipeResourceFlow.Phase.OUTPUT, 0.7, true)), 0, 0, true);
        RecipeLaneSnapshot lane = lane(3, 16).baselineMaxOperations(16).recipeSemantics(chance)
              .output("a", ImmutableResourceSnapshot.empty(), 64)
              .output("b", ImmutableResourceSnapshot.empty(), 64).build();
        RecipeRunSnapshot captured = snapshot(lane(0, 0).build(), lane);
        RecipeLanePlan plan = RecipeExecutionPlanner.calculate(captured).getLane(3);
        Map<String, Long> expected = new LinkedHashMap<>();
        RecipeRandomContext.run(captured.getRandomSeed(), () -> RecipeRandomContext.runLane(3, () -> {
            Random fallback = new Random(0);
            for (int operation = 0; operation < 16; operation++) {
                if (RecipeRandomContext.nextDouble(fallback) < 0.3) expected.merge("a", 1L, Long::sum);
                if (RecipeRandomContext.nextDouble(fallback) < 0.7) expected.merge("b", 1L, Long::sum);
            }
        }));
        expected.forEach((key, amount) -> assertEquals(amount.longValue(), plan.getOutputs().get(key).getAmount()));
        assertEquals(plan.getOutputs(), RecipeExecutionPlanner.calculate(captured).getLane(3).getOutputs());
        assertEquals(plan.getOutputs(), RecipeExecutionPlanner.calculate(snapshot(lane)).getLane(3).getOutputs());
        assertFalse(RecipeRandomContext.isActive());
    }

    @Test
    void repeatedMultiLaneCalculationsRemainDeterministicAndStateless() {
        RecipeSemanticsSnapshot chance = new RecipeSemanticsSnapshot(
              Collections.singletonList(new RecipeResourceFlow("input", resource("ore", 1),
                    RecipeResourceFlow.Phase.COMPLETION)),
              Arrays.asList(new RecipeResourceFlow("common", resource("common", 1),
                          RecipeResourceFlow.Phase.OUTPUT, 0.25, true),
                    new RecipeResourceFlow("rare", resource("rare", 1),
                          RecipeResourceFlow.Phase.OUTPUT, 0.125, true)), 0, 0, true);
        RecipeRunSnapshot captured = RecipeRunSnapshot.builder("test:stress")
              .recipeGeneration(11).machineStateVersion(4).randomSeed(987654321L).energy(1000, 1)
              .lane(RecipeLaneSnapshot.builder(0).requiredTicks(1).baselineMaxOperations(8)
                    .input("input", resource("ore", 8)).output("common", ImmutableResourceSnapshot.empty(), 128)
                    .output("rare", ImmutableResourceSnapshot.empty(), 128).recipeSemantics(chance).build())
              .lane(RecipeLaneSnapshot.builder(1).requiredTicks(2).baselineMaxOperations(6)
                    .input("input", resource("ore", 6)).output("common", ImmutableResourceSnapshot.empty(), 128)
                    .output("rare", ImmutableResourceSnapshot.empty(), 128).recipeSemantics(chance).build())
              .build();
        RecipeExecutionPlan expected = RecipeExecutionPlanner.calculate(captured);
        for (int iteration = 0; iteration < 10_000; iteration++) {
            RecipeExecutionPlan actual = RecipeExecutionPlanner.calculate(captured);
            assertEquals(expected.getOperations(), actual.getOperations());
            assertEquals(expected.getEnergyAsDouble(), actual.getEnergyAsDouble());
            assertEquals(expected.getOutputs(), actual.getOutputs());
            assertEquals(expected.getLanes().toString(), actual.getLanes().toString());
        }
        assertFalse(RecipeRandomContext.isActive());
    }

    @Test
    void templateIsRequiredButDoesNotLimitOrConsumeOperations() {
        RecipeSemanticsSnapshot semantics = new RecipeSemanticsSnapshot(Arrays.asList(
              new RecipeResourceFlow("template", resource("pattern", 100), RecipeResourceFlow.Phase.COMPLETION),
              new RecipeResourceFlow("input", resource("ore", 1), RecipeResourceFlow.Phase.COMPLETION)),
              RecipeLanePlannerTest.semantics("ore", "ingot").getOutputs(), 0, 0, true);
        RecipeLaneSnapshot lane = lane(0, 4).input("template", resource("pattern", 1))
              .templateInputKeys(Collections.singleton("template")).recipeSemantics(semantics).build();
        RecipeLanePlan plan = RecipeExecutionPlanner.calculate(snapshot(lane)).getLane(0);
        assertEquals(4, plan.getOperations());
        assertFalse(plan.getInputConsumption().containsKey("template"));
        assertEquals(4L, plan.getCompletionConsumption().get("input"));
    }

    @Test
    void missingCompletionInputResetsProgressButPausedErrorRetainsIt() {
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot(
              lane(0, 0).operatingTicks(3).requiredTicks(5).build(),
              lane(1, 0).operatingTicks(3).requiredTicks(5).pausedForErrors(true).error("NOT_ENOUGH_INPUT").build()));
        assertEquals(0, plan.getLane(0).getNewOperatingTicks());
        assertEquals(3, plan.getLane(1).getNewOperatingTicks());
        assertTrue(plan.getLane(1).getErrors().contains("NOT_ENOUGH_INPUT"));
        assertEquals(0, plan.getEnergyAsDouble());
    }

    @Test
    void missingRecipePreservesProgressOnlyForExplicitlyCapturedMachineRule() {
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot(
              RecipeLaneSnapshot.builder(0).operatingTicks(3).requiredTicks(5).recipePresent(false)
                    .keepProgressWithoutRecipe(true).build(),
              RecipeLaneSnapshot.builder(1).operatingTicks(3).requiredTicks(5).recipePresent(false).build()));
        assertEquals(3, plan.getLane(0).getNewOperatingTicks());
        assertEquals(0, plan.getLane(1).getNewOperatingTicks());
        assertEquals(0, plan.getOperations());
        assertTrue(plan.getInputConsumption().isEmpty());
    }

    @Test
    void interchangeableOutputsUseLargerOrientationOnlyWhenOriginalDoesNotFit() {
        RecipeSemanticsSnapshot pair = new RecipeSemanticsSnapshot(semantics("ore", "ingot").getInputs(), Arrays.asList(
              new RecipeResourceFlow("a", resource("first", 2), RecipeResourceFlow.Phase.OUTPUT),
              new RecipeResourceFlow("b", resource("second", 1), RecipeResourceFlow.Phase.OUTPUT)), 0, 0, true);
        RecipeLaneSnapshot lane = RecipeLaneSnapshot.builder(0).baselineMaxOperations(4).requiredTicks(1)
              .input("input", resource("ore", 4)).output("a", ImmutableResourceSnapshot.empty(), 2)
              .output("b", ImmutableResourceSnapshot.empty(), 4).interchangeableOutputs(true).recipeSemantics(pair).build();
        RecipeLanePlan full = RecipeExecutionPlanner.calculate(snapshot(lane)).getLane(0);
        assertEquals(2, full.getOperations());
        Map<String, ImmutableResourceSnapshot> fullContents = RecipeExecutionPlanner.outputContentsAfter(lane, lane.getOutputContents(), full);
        assertEquals(resource("second", 2), fullContents.get("a"));
        assertEquals(resource("first", 4), fullContents.get("b"));
        RecipeRunSnapshot limitedEnergy = RecipeRunSnapshot.builder("test:pair").energy(1, 1).lane(lane).build();
        RecipeLanePlan limited = RecipeExecutionPlanner.calculate(limitedEnergy).getLane(0);
        assertEquals(1, limited.getOperations());
        Map<String, ImmutableResourceSnapshot> limitedContents = RecipeExecutionPlanner.outputContentsAfter(lane, lane.getOutputContents(), limited);
        assertEquals(resource("first", 2), limitedContents.get("a"));
        assertEquals(resource("second", 1), limitedContents.get("b"));
    }
}
