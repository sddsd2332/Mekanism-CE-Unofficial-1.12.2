package mekanism.common.recipe.cache;

import mekanism.api.IContainerTransaction;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.cache.inputs.InputHelper;
import mekanism.common.recipe.cache.outputs.OutputHelper;
import mekanism.common.recipe.machines.EnrichmentRecipe;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class RecipePlanCommitterTest {

    @BeforeAll
    static void bootstrap() { Bootstrap.register(); }

    @Test
    void workerPlanCommitsAllLanesBeforeAnyContentListener() {
        DirectTransaction owner = new DirectTransaction();
        BasicEnergyContainer energy = BasicEnergyContainer.create(100, null);
        energy.setEnergy(100);
        Lane first = new Lane(0, BasicInventorySlot.at(null, 0, 0), energy);
        Lane second = new Lane(1, BasicInventorySlot.at(null, 0, 0), energy);
        AtomicInteger notifications = new AtomicInteger();
        first.input.addContentsListener(() -> {
            assertFalse(owner.inTransaction);
            assertEquals(1, first.output.getCount());
            assertEquals(1, second.output.getCount());
            assertEquals(90, energy.getEnergy());
            assertEquals(1, first.finished.get());
            assertEquals(1, second.finished.get());
            notifications.incrementAndGet();
        });
        RecipeRunSnapshot captured = snapshot(energy, first, second);
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(captured);
        assertTrue(RecipePlanCommitter.commit(owner, captured, plan, targets(first, second), energy, () -> true));
        assertEquals(1, notifications.get());
        assertTrue(first.input.isEmpty());
        assertTrue(second.input.isEmpty());
        assertEquals(0, first.progress.get());
    }

    @Test
    void lastLaneHandlerSimulationFailureLeavesWholeMachineUntouched() {
        BasicEnergyContainer energy = BasicEnergyContainer.create(100, null);
        energy.setEnergy(100);
        Lane first = new Lane(0, BasicInventorySlot.at(null, 0, 0), energy);
        Lane second = new Lane(1, BasicInventorySlot.at(stack -> true, stack -> false, null, 0, 0), energy);
        RecipeRunSnapshot captured = snapshot(energy, first, second);
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(captured);
        assertEquals(2, plan.getOperations());
        assertFalse(RecipePlanCommitter.commit(new DirectTransaction(), captured, plan, targets(first, second), energy, () -> true));
        assertEquals(1, first.input.getCount());
        assertEquals(1, second.input.getCount());
        assertTrue(first.output.isEmpty());
        assertTrue(second.output.isEmpty());
        assertEquals(100, energy.getEnergy());
        assertEquals(0, first.finished.get());
        assertEquals(0, second.finished.get());
    }

    @Test
    void mutationWithoutListenerStillRejectsCapturedValues() {
        BasicEnergyContainer energy = BasicEnergyContainer.create(100, null);
        energy.setEnergy(100);
        Lane first = new Lane(0, BasicInventorySlot.at(null, 0, 0), energy);
        Lane second = new Lane(1, BasicInventorySlot.at(null, 0, 0), energy);
        RecipeRunSnapshot captured = snapshot(energy, first, second);
        second.input.setStackUncheckedNoUpdate(new ItemStack(Items.GOLD_INGOT));
        assertFalse(RecipePlanCommitter.commit(new DirectTransaction(), captured, RecipeExecutionPlanner.calculate(captured),
              targets(first, second), energy, () -> true));
        assertEquals(1, first.input.getCount());
        assertEquals(Items.GOLD_INGOT, second.input.getStack().getItem());
        assertTrue(first.output.isEmpty());
        assertEquals(100, energy.getEnergy());
    }

    @Test
    void partialWriteFailureRollsBackFailedContainerAndEarlierLanesWithoutNotifications() {
        BasicEnergyContainer energy = BasicEnergyContainer.create(100, null);
        energy.setEnergy(100);
        FailingSlot failingOutput = new FailingSlot();
        Lane first = new Lane(0, BasicInventorySlot.at(null, 0, 0), energy);
        Lane second = new Lane(1, failingOutput, energy);
        AtomicInteger notifications = new AtomicInteger();
        first.input.addContentsListener(notifications::incrementAndGet);
        RecipeRunSnapshot captured = snapshot(energy, first, second);
        assertFalse(RecipePlanCommitter.commit(new DirectTransaction(), captured, RecipeExecutionPlanner.calculate(captured),
              targets(first, second), energy, () -> true));
        assertEquals(1, first.input.getCount());
        assertEquals(1, second.input.getCount());
        assertTrue(first.output.isEmpty());
        assertTrue(second.output.isEmpty());
        assertEquals(100, energy.getEnergy());
        assertEquals(0, notifications.get());
        assertEquals(0, first.finished.get());
        assertEquals(0, second.finished.get());
    }

    @Test
    void incorrectWorkerResourceAmountsCannotReachHandlers() {
        BasicEnergyContainer energy = BasicEnergyContainer.create(100, null);
        energy.setEnergy(100);
        Lane lane = new Lane(0, BasicInventorySlot.at(null, 0, 0), energy);
        RecipeRunSnapshot captured = snapshot(energy, lane);
        RecipeExecutionPlan correct = RecipeExecutionPlanner.calculate(captured);
        RecipeLanePlan wrong = new RecipeLanePlan(0, 1, 5, 0, true, Collections.emptyMap(),
              Collections.emptyMap(), Collections.emptyMap(), correct.getLane(0).getOutputs(), Collections.emptySet());
        RecipeExecutionPlan corrupt = RecipeExecutionPlan.builder(captured.getRecipeId()).operations(1).energy(5)
              .randomSeed(captured.getRandomSeed()).lane(wrong).build();
        assertFalse(RecipePlanCommitter.commit(new DirectTransaction(), captured, corrupt, targets(lane), energy, () -> true));
        assertEquals(1, lane.input.getCount());
        assertTrue(lane.output.isEmpty());
        assertEquals(100, energy.getEnergy());
    }

    private static RecipeRunSnapshot snapshot(BasicEnergyContainer energy, Lane... lanes) {
        RecipeRunSnapshot.Builder builder = RecipeRunSnapshot.builder("test:atomic-lanes").energy(energy.getEnergy(), 5).randomSeed(33);
        for (Lane lane : lanes) builder.lane(lane.snapshot());
        return builder.build();
    }

    private static Map<Integer, RecipeLaneCommitTarget> targets(Lane... lanes) {
        Map<Integer, RecipeLaneCommitTarget> targets = new LinkedHashMap<>();
        for (Lane lane : lanes) targets.put(lane.index, new RecipeLaneCommitTarget(lane.cache)
              .input("item.0", lane.input).output("item.0", lane.output));
        return targets;
    }

    private static final class Lane {
        private final int index;
        private final BasicInventorySlot input = BasicInventorySlot.at(null, 0, 0);
        private final BasicInventorySlot output;
        private final EnrichmentRecipe recipe = new EnrichmentRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND));
        private final CachedRecipe<EnrichmentRecipe> cache;
        private final AtomicInteger finished = new AtomicInteger();
        private final AtomicInteger progress = new AtomicInteger();

        private Lane(int index, BasicInventorySlot output, BasicEnergyContainer energy) {
            this.index = index;
            this.output = output;
            input.setStack(new ItemStack(Items.IRON_INGOT));
            cache = new OneInputCachedRecipe<ItemStack, ItemStack, ItemStack, EnrichmentRecipe>(recipe, () -> true,
                  InputHelper.getInputHandler(input, RecipeError.NOT_ENOUGH_INPUT),
                  OutputHelper.getOutputHandler(output, RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
                  () -> recipe.getInput().ingredient, stack -> stack.getItem() == Items.IRON_INGOT,
                  stack -> recipe.getOutput().output.copy(), ItemStack::isEmpty, ItemStack::isEmpty) {
                @Override
                protected void finishProcessing(int operations) {
                    fail("Plan commits must consume the worker's resource changes, not run the legacy process method");
                }
            }.setEnergyRequirements(() -> 5, energy).setRequiredTicks(() -> 1)
                  .setOperatingTicksChanged(progress::set).setOnFinish(finished::incrementAndGet);
        }

        private RecipeLaneSnapshot snapshot() {
            return RecipeLaneSnapshot.builder(index).requiredTicks(1).energyPerTick(5)
                  .input("item.0", ImmutableResourceSnapshot.of(input.getStack()))
                  .output("item.0", ImmutableResourceSnapshot.of(output.getStack()), 64)
                  .recipeSemantics(RecipeSemanticsCompiler.compile(recipe, "")).build();
        }
    }

    private static final class FailingSlot extends BasicInventorySlot {
        private boolean firstWrite = true;

        private FailingSlot() { super(alwaysTrue, alwaysTrue, alwaysTrue, null, 0, 0); }

        @Override
        public void setStackUncheckedNoUpdate(ItemStack stack) {
            super.setStackUncheckedNoUpdate(stack);
            if (firstWrite && !isEmpty()) {
                firstWrite = false;
                throw new IllegalStateException("Injected write failure after the container changed");
            }
        }
    }

    private static final class DirectTransaction implements IContainerTransaction {
        private boolean inTransaction;
        @Override public void runContainerTransaction(Runnable action) { callContainerTransaction(() -> { action.run(); return null; }); }
        @Override public <T> T callContainerTransaction(Supplier<T> action) {
            inTransaction = true;
            try { return action.get(); }
            finally { inTransaction = false; }
        }
    }
}
