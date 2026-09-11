package mekanism.common.tile.prefab;

import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.cache.RecipeExecutionPlan;
import mekanism.common.recipe.cache.RecipeExecutionPlanner;
import mekanism.common.recipe.cache.RecipeRunSnapshot;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.EnrichmentRecipe;
import mekanism.common.tile.machine.TileEntityEnrichmentChamber;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class RecipeMachineCommitTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        Field namedMods = Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        if (namedMods.get(Loader.instance()) == null) namedMods.set(Loader.instance(), Collections.emptyMap());
        Bootstrap.register();
    }

    @Test
    void ordinaryMachineUsesTheSamePlanEntryPointForProgressAndCompletion() throws Exception {
        RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND));
        TileEntityEnrichmentChamber tile = machine();
        tile.ticksRequired = 2;
        RecipeRunSnapshot first = tile.captureSnapshot();
        RecipeExecutionPlan plan = CompletableFuture.supplyAsync(() -> RecipeExecutionPlanner.calculate(first)).get();
        assertEquals(1, plan.getLane(0).getOperations());
        assertEquals(1, tile.inputSlot.getCount());
        assertEquals(0, tile.operatingTicks);
        tile.commitPlan(first, plan);
        assertEquals(1, tile.operatingTicks);
        assertEquals(1, tile.inputSlot.getCount());
        assertTrue(tile.outputSlot.isEmpty());

        RecipeRunSnapshot second = tile.captureSnapshot();
        tile.commitPlan(second, RecipeExecutionPlanner.calculate(second));
        assertEquals(0, tile.operatingTicks);
        assertTrue(tile.inputSlot.isEmpty());
        assertEquals(Items.DIAMOND, tile.outputSlot.getStack().getItem());
        assertEquals(1, tile.outputSlot.getCount());
        assertEquals(first.getStoredEnergy() - 2 * first.getLane(0).getEnergyPerTick(), tile.getEnergy());
    }

    @Test
    void replacementRecipeRejectsInFlightPlanAndNextCaptureUsesNewOutput() {
        RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND));
        TileEntityEnrichmentChamber tile = machine();
        tile.ticksRequired = 1;
        RecipeRunSnapshot old = tile.captureSnapshot();
        RecipeExecutionPlan oldPlan = RecipeExecutionPlanner.calculate(old);
        RecipeHandler.Recipe.ENRICHMENT_CHAMBER.get().put(new ItemStackInput(new ItemStack(Items.IRON_INGOT)),
              new EnrichmentRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.EMERALD)));
        tile.commitPlan(old, oldPlan);
        assertEquals(1, tile.inputSlot.getCount());
        assertTrue(tile.outputSlot.isEmpty());
        assertEquals(old.getStoredEnergy(), tile.getEnergy());
        RecipeRunSnapshot fresh = tile.captureSnapshot();
        tile.commitPlan(fresh, RecipeExecutionPlanner.calculate(fresh));
        assertTrue(tile.inputSlot.isEmpty());
        assertEquals(Items.EMERALD, tile.outputSlot.getStack().getItem());
    }

    @Test
    void inPlaceRecipeMutationRejectsOldPlanAndNextCaptureCommitsNewOutput() {
        EnrichmentRecipe recipe = new EnrichmentRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND));
        RecipeHandler.Recipe.ENRICHMENT_CHAMBER.get().put(new ItemStackInput(new ItemStack(Items.IRON_INGOT)), recipe);
        TileEntityEnrichmentChamber tile = machine();
        tile.ticksRequired = 1;
        RecipeRunSnapshot old = tile.captureSnapshot();
        RecipeExecutionPlan oldPlan = tile.calculatePlan(old);
        // Lookup owns a copy; exercise mutation of the actual source used by capture/commit.
        assertNotSame(recipe, tile.getRecipe());
        tile.getRecipe().getOutput().output.setCount(2);
        assertEquals(old.getGlobalRecipeGeneration(), RecipeHandler.getGlobalRecipeGeneration());
        tile.commitPlan(old, oldPlan);
        assertEquals(1, tile.inputSlot.getCount());
        assertTrue(tile.outputSlot.isEmpty());
        assertEquals(old.getStoredEnergy(), tile.getEnergy());
        RecipeRunSnapshot fresh = tile.captureSnapshot();
        assertNotEquals(old.getRecipeSignature(), fresh.getRecipeSignature());
        tile.commitPlan(fresh, tile.calculatePlan(fresh));
        assertTrue(tile.inputSlot.isEmpty());
        assertEquals(Items.DIAMOND, tile.outputSlot.getStack().getItem());
        assertEquals(2, tile.outputSlot.getCount());
    }

    @Test
    void ordinaryMachineAcceptsIndependentConfigurationAndRejectsEitherCounterChange() {
        RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND));
        ConfigMachine tile = new ConfigMachine();
        tile.inputSlot.setStackUnchecked(new ItemStack(Items.IRON_INGOT));
        tile.setEnergy(tile.getMaxEnergy());
        tile.ticksRequired = 1;
        RecipeRunSnapshot old = tile.captureSnapshot();
        assertNotEquals(old.getMachineStateVersion(), old.getConfigurationVersion());
        assertTrue(tile.isPlanStillValid(old, tile.calculatePlan(old)));
        tile.configuration++;
        assertFalse(tile.isPlanStillValid(old, tile.calculatePlan(old)));
        RecipeRunSnapshot configured = tile.captureSnapshot();
        assertTrue(tile.isPlanStillValid(configured, tile.calculatePlan(configured)));
        tile.invalidateProcessingState();
        assertFalse(tile.isPlanStillValid(configured, tile.calculatePlan(configured)));
        RecipeRunSnapshot fresh = tile.captureSnapshot();
        tile.commitPlan(fresh, tile.calculatePlan(fresh));
        assertTrue(tile.inputSlot.isEmpty());
        assertEquals(Items.DIAMOND, tile.outputSlot.getStack().getItem());
    }

    private static final class ConfigMachine extends TileEntityEnrichmentChamber {
        private long configuration = 777;
        public long getAsyncConfigurationVersion() { return configuration; }
        public void setActive(boolean active) {
            if (isActive != active) {
                isActive = active;
                markProcessingStateChanged();
            }
        }
    }

    @Test
    void newlyRegisteredRecipeWakesAnEmptyLookupCache() {
        ItemStackInput input = new ItemStackInput(new ItemStack(Items.BLAZE_ROD));
        RecipeHandler.Recipe.ENRICHMENT_CHAMBER.get().remove(input);
        TileEntityEnrichmentChamber tile = machine();
        tile.inputSlot.setStackUnchecked(new ItemStack(Items.BLAZE_ROD));
        tile.ticksRequired = 1;
        RecipeRunSnapshot missing = tile.captureSnapshot();
        assertFalse(missing.getLane(0).isRecipePresent());
        tile.commitPlan(missing, RecipeExecutionPlanner.calculate(missing));
        RecipeHandler.addEnrichmentChamberRecipe(new ItemStack(Items.BLAZE_ROD), new ItemStack(Items.GOLD_INGOT));
        RecipeRunSnapshot fresh = tile.captureSnapshot();
        assertTrue(fresh.getLane(0).isRecipePresent());
        tile.commitPlan(fresh, RecipeExecutionPlanner.calculate(fresh));
        assertTrue(tile.inputSlot.isEmpty());
        assertEquals(Items.GOLD_INGOT, tile.outputSlot.getStack().getItem());
    }

    private static TileEntityEnrichmentChamber machine() {
        TileEntityEnrichmentChamber tile = new TileEntityEnrichmentChamber() {
            @Override
            public void setActive(boolean active) {
                if (isActive != active) {
                    isActive = active;
                    markProcessingStateChanged();
                }
            }
        };
        tile.inputSlot.setStackUnchecked(new ItemStack(Items.IRON_INGOT));
        tile.setEnergy(tile.getMaxEnergy());
        return tile;
    }
}
