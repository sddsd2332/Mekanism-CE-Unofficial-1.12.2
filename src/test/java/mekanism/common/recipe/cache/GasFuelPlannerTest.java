package mekanism.common.recipe.cache;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class GasFuelPlannerTest {

    private static RecipeRunSnapshot base() {
        return RecipeRunSnapshot.builder("test:fuel").recipeGeneration(3).machineStateVersion(4)
              .energy(0, 0).build();
    }

    private static GasFuelSnapshot snapshot(int fuel, int capacity, int burn, int maxBurn, double rate,
          double stored, double maxEnergy, int recipeBurn, double recipeEnergy, long processes,
          boolean canOperate, boolean canInsert, boolean dynamic, double reset, long outputProcesses) {
        GasFuelState state = new GasFuelState(ImmutableResourceSnapshot.descriptor("gas:test", fuel), capacity,
              burn, maxBurn, rate, reset, 0, stored, maxEnergy, recipeBurn, recipeEnergy, processes,
              canOperate, canInsert, dynamic, reset, outputProcesses);
        return new GasFuelSnapshot(base(), state);
    }

    @Test
    void coreGeneratorUsesFuelRemainderAndEnergyCapacity() {
        GasFuelPlan plan = GasFuelPlanner.calculate(snapshot(100, 18000, 2, 100, 10, 0, 1000,
              2, 10, 1, true, true, true, 20, 0));
        assertEquals(2, plan.getOperations());
        assertEquals(100, plan.fuelAfter.getAmount());
        assertEquals(0, plan.burnTicks);
        assertEquals(20, plan.generatedEnergy);
        assertEquals(40, plan.output);
        assertEquals(1.0, plan.clientUsed);
    }

    @Test
    void tierGeneratorOutputUsesProcessCountWhileBurnUsesAvailableTicks() {
        GasFuelPlan plan = GasFuelPlanner.calculate(snapshot(1000, 18000, 0, 10, 50, 0, 100000,
              10, 50, 8, true, true, true, 160, 8));
        assertEquals(15 * 8, plan.getOperations());
        assertEquals(800, plan.output);
        assertEquals(6000, plan.generatedEnergy);
    }

    @Test
    void fullEnergyLeavesFuelAndBurnStateUntouched() {
        GasFuelPlan plan = GasFuelPlanner.calculate(snapshot(100, 18000, 7, 10, 50, 1000, 1000,
              10, 50, 1, true, false, false, 0, 0));
        assertEquals(0, plan.getOperations());
        assertEquals(100, plan.fuelAfter.getAmount());
        assertEquals(7, plan.burnTicks);
        assertEquals(10, plan.maxBurnTicks);
        assertEquals(50, plan.generationRate);
        assertFalse(plan.isActive());
    }

    @Test
    void disabledOrMissingRecipeResetsBurnStateWithoutEnergy() {
        GasFuelPlan plan = GasFuelPlanner.calculate(snapshot(100, 18000, 7, 10, 50, 0, 1000,
              10, 50, 1, false, true, true, 20, 0));
        assertEquals(0, plan.getOperations());
        assertTrue(plan.fuelAfter.equals(ImmutableResourceSnapshot.descriptor("gas:test", 100)));
        assertEquals(0, plan.burnTicks);
        assertEquals(0, plan.maxBurnTicks);
        assertEquals(0, plan.generationRate);
        assertEquals(20, plan.output);
    }
}
