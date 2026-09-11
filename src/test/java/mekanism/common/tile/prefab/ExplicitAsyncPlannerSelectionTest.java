package mekanism.common.tile.prefab;

import mekanism.api.IAsyncMachinePlanner;
import mekanism.api.IAsyncPlanCalculator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplicitAsyncPlannerSelectionTest {

    @Test
    void overridingLegacyHookDoesNotOptIn() {
        LegacyTile tile = new LegacyTile();
        assertFalse(tile.supportsAsync());
    }

    @Test
    void implementingPlannerExplicitlyOptsIn() {
        PlannerTile tile = new PlannerTile();
        assertTrue(tile.supportsAsync());
        long captured = tile.getProcessingStateVersion();
        assertEquals(captured + 1, tile.invalidateProcessingState());
        assertFalse(tile.isProcessingStateCurrent(captured));
    }

    @Test
    void chunkUnloadCancelsPendingPlanReservation() throws Exception {
        PlannerTile tile = new PlannerTile();
        Field pendingField = TileEntityBasicBlock.class.getDeclaredField("pendingAsyncPlan");
        pendingField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Object> pending = (AtomicReference<Object>) pendingField.get(tile);
        Class<?> pendingType = Class.forName(
              "mekanism.common.tile.prefab.TileEntityBasicBlock$PendingAsyncPlan");
        Constructor<?> constructor = pendingType.getDeclaredConstructor(Object.class, long.class, long.class, long.class);
        constructor.setAccessible(true);
        pending.set(constructor.newInstance(new Object(), 0L, 0L, 0L));

        tile.onChunkUnload();

        assertFalse(tile.hasPendingAsyncPlan());
    }

    @Test
    void invalidateCancelsPendingPlanAndAdvancesStateVersion() throws Exception {
        PlannerTile tile = new PlannerTile();
        Field pendingField = TileEntityBasicBlock.class.getDeclaredField("pendingAsyncPlan");
        pendingField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Object> pending = (AtomicReference<Object>) pendingField.get(tile);
        Class<?> pendingType = Class.forName(
              "mekanism.common.tile.prefab.TileEntityBasicBlock$PendingAsyncPlan");
        Constructor<?> constructor = pendingType.getDeclaredConstructor(Object.class, long.class, long.class, long.class);
        constructor.setAccessible(true);
        long before = tile.getProcessingStateVersion();
        pending.set(constructor.newInstance(new Object(), before, 0L, 0L));

        // The public state invalidation bridge is the lifecycle-safe operation
        // available before a TileEntity has been attached to a world.
        tile.cancelPendingAsyncPlan(null);
        tile.invalidateProcessingState();

        assertFalse(tile.hasPendingAsyncPlan());
        assertTrue(tile.getProcessingStateVersion() > before);
        assertFalse(tile.isProcessingStateCurrent(before));
    }

    private static class LegacyTile extends TileEntityBasicBlock {
        @Override
        protected void onAsyncUpdateServer() {
        }
    }

    private static final class PlannerTile extends TileEntityBasicBlock implements IAsyncMachinePlanner<Integer, Integer> {
        private static final IAsyncPlanCalculator<Integer, Integer> CALCULATOR = value -> value;
        @Override
        public Integer captureSnapshot() { return 1; }
        @Override
        public Integer calculatePlan(Integer snapshot) { return snapshot; }
        @Override
        public IAsyncPlanCalculator<Integer, Integer> getAsyncPlanCalculator() { return CALCULATOR; }
        @Override
        public void commitPlan(Integer snapshot, Integer plan) { }
    }
}
