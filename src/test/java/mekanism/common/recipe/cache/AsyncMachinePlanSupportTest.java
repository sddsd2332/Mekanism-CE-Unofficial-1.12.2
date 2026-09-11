package mekanism.common.recipe.cache;

import mekanism.api.recipes.IRecipeSignatureSource;
import mekanism.common.recipe.RecipeSnapshotCompiler;
import mekanism.common.recipe.UnsupportedRecipeSignatureException;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AsyncMachinePlanSupportTest {

    @Test
    void independentConfigurationAndStateCountersAreValidatedSeparately() {
        ConfigTile tile = new ConfigTile();
        RecipeRunSnapshot initial = tile.captureSnapshot();
        assertNotEquals(initial.getMachineStateVersion(), initial.getConfigurationVersion());
        assertValid(tile, initial);
        RecipeRunSnapshot helper = AsyncMachinePlanSupport.capture(tile, tile.source, tile.getAsyncRecipeCategoryGeneration());
        assertEquals(tile.configuration, helper.getConfigurationVersion());
        assertValid(tile, helper);
        tile.configuration++;
        assertFalse(tile.isPlanStillValid(initial, tile.calculatePlan(initial)));
        RecipeRunSnapshot reconfigured = tile.captureSnapshot();
        assertValid(tile, reconfigured);
        tile.invalidateProcessingState();
        assertFalse(tile.isPlanStillValid(reconfigured, tile.calculatePlan(reconfigured)));
        assertValid(tile, tile.captureSnapshot());
    }

    @Test
    void standalonePlannerCanSupplyItsConfigurationCounterExplicitly() {
        TileEntityBasicBlock tile = new TileEntityBasicBlock() { };
        RecipeRunSnapshot snapshot = AsyncMachinePlanSupport.capture(tile, null, 0, 42, 0, 0, "");
        RecipeExecutionPlan plan = RecipeExecutionPlanner.calculate(snapshot);
        assertTrue(AsyncMachinePlanSupport.isStillValid(tile, snapshot, plan, null, 0, 42));
        assertFalse(AsyncMachinePlanSupport.isStillValid(tile, snapshot, plan, null, 0, 43));
        tile.invalidateProcessingState();
        assertFalse(AsyncMachinePlanSupport.isStillValid(tile, snapshot, plan, null, 0, 42));
    }

    @Test
    void failedSignatureExtractionEvictsCacheAndCanRecoverWithoutGenerationChange() {
        ConfigTile tile = new ConfigTile();
        RecipeRunSnapshot original = tile.captureSnapshot();
        tile.source.value = new Object();
        assertThrows(UnsupportedRecipeSignatureException.class,
              () -> tile.isPlanStillValid(original, tile.calculatePlan(original)));
        assertThrows(UnsupportedRecipeSignatureException.class, tile::captureSnapshot);
        tile.source.value = 2;
        RecipeRunSnapshot recovered = tile.captureSnapshot();
        assertEquals(original.getGlobalRecipeGeneration(), recovered.getGlobalRecipeGeneration());
        assertNotEquals(original.getRecipeSignature(), recovered.getRecipeSignature());
        assertEquals(RecipeSnapshotCompiler.semanticSignature(tile.source), recovered.getRecipeSignature());
        assertValid(tile, recovered);
    }

    @Test
    void rejectingAnOlderPlanDoesNotEvictNewlyCompiledSemantics() {
        ConfigTile tile = new ConfigTile();
        RecipeRunSnapshot original = tile.captureSnapshot();
        tile.source.value = 2;
        assertFalse(tile.isPlanStillValid(original, tile.calculatePlan(original)));
        RecipeRunSnapshot fresh = tile.captureSnapshot();
        assertValid(tile, fresh);
        assertFalse(tile.isPlanStillValid(original, tile.calculatePlan(original)));
        int signatureCalls = tile.source.calls;
        RecipeRunSnapshot next = tile.captureSnapshot();
        assertEquals(signatureCalls, tile.source.calls, "An old rejection must not recompile the current cache");
        assertEquals(fresh.getRecipeSignature(), next.getRecipeSignature());
    }

    private static void assertValid(ConfigTile tile, RecipeRunSnapshot snapshot) {
        assertTrue(tile.isPlanStillValid(snapshot, tile.calculatePlan(snapshot)));
    }

    private static final class ConfigTile extends TileEntityBasicBlock implements IAsyncRecipeMachine {
        private long configuration = 777;
        private final SignatureSource source = new SignatureSource();
        public long getAsyncConfigurationVersion() { return configuration; }
        public Object getAsyncRecipeSnapshotSource() { return source; }
    }

    private static final class SignatureSource implements IRecipeSignatureSource {
        private Object value = 1;
        private int calls;
        public String getRecipeSignatureType() { return "test:mutable_recipe"; }
        public Map<String, ?> getRecipeSignatureData() {
            calls++;
            return Collections.singletonMap("output", value);
        }
    }
}
