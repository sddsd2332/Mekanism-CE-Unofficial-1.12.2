package mekanism.common.tile.prefab;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.CachedRecipe;
import mekanism.common.recipe.cache.RecipeExecutionPlan;
import mekanism.common.recipe.cache.RecipeExecutionPlanner;
import mekanism.common.recipe.cache.RecipeRunSnapshot;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mekanism.common.tile.machine.TileEntityAntiprotonicNucleosynthesizer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class NucleosynthesizerPlanCommitTest {

    private static final Gas GAS = new Gas("nucleosynthesizer_plan_test", 0xFFFFFF);

    @BeforeAll
    static void bootstrap() throws Exception {
        Field namedMods = Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        if (namedMods.get(Loader.instance()) == null) namedMods.set(Loader.instance(), Collections.emptyMap());
        Bootstrap.register();
    }

    @Test
    void multipleCompletionsAndFinalProgressMatchLegacyTick() throws Exception {
        register(1, 2, 4, 0);
        Machine planned = compareTick(8, 100, 0, 0, 16200);
        assertEquals(6, planned.getRecipeInputSlot().getCount());
        assertEquals(96, planned.inputGasTank.getStored());
        assertEquals(2, planned.getRecipeOutputSlot().getCount());
        assertEquals(1, planned.operatingTicks);
        assertEquals(14400, planned.getEnergy());
        assertEquals(2, planned.finishes);
        assertTrue(planned.getActive());
    }

    @Test
    void initialProgressAndExactCompletionMatchLegacyTick() throws Exception {
        register(1, 2, 4, 0);
        compareTick(8, 100, 0, 3, 16200);
        compareTick(8, 100, 0, 3, 200);
    }

    @Test
    void depletedOrInsufficientInputsStopLaterPasses() throws Exception {
        register(1, 2, 4, 0);
        compareTick(1, 100, 0, 0, 16200);
        compareTick(8, 2, 0, 0, 16200);
        compareTick(8, 3, 0, 0, 16200);
        register(2, 2, 4, 0);
        compareTick(3, 100, 0, 0, 16200);
    }

    @Test
    void fullOutputsAndInsufficientEnergyPreserveLegacyState() throws Exception {
        register(1, 2, 4, 0);
        compareTick(8, 100, 63, 0, 16200);
        compareTick(8, 100, 64, 2, 16200);
        compareTick(8, 100, 0, 2, 199);
        register(1, 2, 4, 4800);
        compareTick(8, 100, 0, 2, 16200);
    }

    @Test
    void noRecipeAndRecipeRemovalCannotConsumeAnything() throws Exception {
        register(1, 2, 4, 0);
        compareTick(0, 100, 0, 0, 16200);
        compareTick(8, 0, 0, 0, 16200);
        compareTick(0, 100, 0, 2, 16200);
        compareTick(8, 0, 0, 2, 16200);
        Machine tile = machine(8, 100, 0, 2, 16200);
        RecipeRunSnapshot captured = tile.captureSnapshot();
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(captured);
        RecipeHandler.Recipe.ANTIPROTONIC_NUCLEOSYNTHESIZER.get().remove(tile.getRecipe().getInput());
        tile.commitPlan(captured, plan);
        assertEquals(8, tile.getRecipeInputSlot().getCount());
        assertEquals(100, tile.inputGasTank.getStored());
        assertEquals(16200, tile.getEnergy());
        assertEquals(2, tile.operatingTicks);
        assertEquals(0, tile.finishes);
        assertTrue(tile.getRecipeOutputSlot().isEmpty());
    }

    @Test
    void consecutiveTicksRecheckTheCacheAfterExhaustionAndRefill() throws Exception {
        register(1, 2, 4, 0);
        Machine legacy = machine(1, 3, 0, 0, 16200);
        Machine planned = machine(1, 3, 0, 0, 16200);
        for (int tick = 0; tick < 4; tick++) {
            if (tick == 2) {
                legacy.getRecipeInputSlot().setStackUnchecked(new ItemStack(Items.IRON_INGOT, 4));
                planned.getRecipeInputSlot().setStackUnchecked(new ItemStack(Items.IRON_INGOT, 4));
                legacy.inputGasTank.setStackUnchecked(new GasStack(GAS, 100));
                planned.inputGasTank.setStackUnchecked(new GasStack(GAS, 100));
            }
            compareTick(legacy, planned);
        }
    }

    @Test
    void longRecipeBatchesOnlyUnchangedIncompletePasses() throws Exception {
        register(1, 2, 1000000, 0);
        Machine planned = compareTick(8, 100, 0, 4, 80000);
        assertEquals(24, planned.operatingTicks);
        assertEquals(76000, planned.getEnergy());
        assertEquals(0, planned.finishes);
    }

    private static void register(int items, int gas, int ticks, double extraEnergy) {
        RecipeHandler.addNucleosynthesizerRecipe(new ItemStack(Items.IRON_INGOT, items),
              new GasStack(GAS, gas), new ItemStack(Items.EMERALD), extraEnergy, ticks);
    }

    private static Machine compareTick(int items, int gas, int outputs, int progress, double energy) throws Exception {
        Machine legacy = machine(items, gas, outputs, progress, energy);
        Machine planned = machine(items, gas, outputs, progress, energy);
        compareTick(legacy, planned);
        return planned;
    }

    private static void compareTick(Machine legacy, Machine planned) throws Exception {
        RecipeRunSnapshot captured = planned.captureSnapshot();
        RecipeExecutionPlan plan = CompletableFuture.supplyAsync(() -> RecipeExecutionPlanner.calculate(captured)).get();
        legacy.onAsyncUpdateServer();
        planned.commitPlan(captured, plan);
        assertAll(
              () -> assertTrue(ItemStack.areItemStacksEqual(legacy.getRecipeInputSlot().getStack(), planned.getRecipeInputSlot().getStack()), "input"),
              () -> assertTrue(ItemStack.areItemStacksEqual(legacy.getRecipeOutputSlot().getStack(), planned.getRecipeOutputSlot().getStack()), "output"),
              () -> assertEquals(legacy.inputGasTank.getStored(), planned.inputGasTank.getStored(), "gas"),
              () -> assertEquals(legacy.getEnergy(), planned.getEnergy(), "energy"),
              () -> assertEquals(legacy.operatingTicks, planned.operatingTicks, "progress"),
              () -> assertEquals(legacy.getActive(), planned.getActive(), "active"),
              () -> assertEquals(legacy.getEnergyUsed(), planned.getEnergyUsed(), "usage"),
              () -> assertEquals(legacy.finishes, planned.finishes, "finishes"),
              () -> assertEquals(legacy.errors(), planned.errors(), "errors items=" + legacy.getRecipeInputSlot().getCount()
                    + " gas=" + legacy.inputGasTank.getStored() + " progress=" + legacy.operatingTicks));
    }

    private static Machine machine(int items, int gas, int outputs, int progress, double energy) {
        Machine tile = new Machine();
        tile.getRecipeInputSlot().setStackUnchecked(items == 0 ? ItemStack.EMPTY : new ItemStack(Items.IRON_INGOT, items));
        tile.inputGasTank.setStackUnchecked(gas == 0 ? null : new GasStack(GAS, gas));
        tile.getRecipeOutputSlot().setStackUnchecked(outputs == 0 ? ItemStack.EMPTY : new ItemStack(Items.EMERALD, outputs));
        tile.operatingTicks = progress;
        tile.setEnergy(energy);
        return tile;
    }

    private static class Machine extends TileEntityAntiprotonicNucleosynthesizer {
        private int finishes;

        @Override
        public void setActive(boolean active) {
            if (isActive != active) {
                isActive = active;
                markProcessingStateChanged();
            }
        }

        @Override
        protected void onCachedRecipeFinish() {
            finishes++;
            super.onCachedRecipeFinish();
        }

        private Set<CachedRecipe.OperationTracker.RecipeError> errors() {
            CachedRecipe<NucleosynthesizerRecipe> cache = recipeCacheLookupMonitor.getCachedRecipe(0);
            return cache == null ? Collections.emptySet() : cache.getErrors();
        }
    }
}
