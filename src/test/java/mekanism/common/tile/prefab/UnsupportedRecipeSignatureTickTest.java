package mekanism.common.tile.prefab;

import mekanism.api.IAsyncMachinePlanner;
import mekanism.api.IAsyncPlanCalculator;
import mekanism.common.Mekanism;
import mekanism.common.recipe.RecipeSnapshotCompiler;
import mekanism.common.recipe.UnsupportedRecipeSignatureException;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UnsupportedRecipeSignatureTickTest {

    @Test
    void unsupportedCaptureLeavesNoReservationOrCommitAndCanRecover() throws Exception {
        SignatureTile tile = new SignatureTile();
        AtomicInteger warnings = new AtomicInteger();
        Logger previous = Mekanism.logger;
        try {
            Mekanism.logger = logger(warnings);
            for (int tick = 0; tick < 5; tick++) invoke(tile, "scheduleAsyncPlan");
            invoke(tile, "runPlannerSynchronously");
            assertEquals(1, warnings.get());
            assertEquals(0, tile.calculations);
            assertEquals(0, tile.commits);
            assertEquals(0, tile.legacyCalls);
            assertFalse(tile.hasPendingAsyncPlan());

            tile.failure = Failure.NONE;
            invoke(tile, "runPlannerSynchronously");
            assertEquals(1, tile.commits);
            tile.failure = Failure.CAPTURE;
            invoke(tile, "scheduleAsyncPlan");
            assertEquals(2, warnings.get());
            assertEquals(1, tile.commits);
            assertFalse(tile.hasPendingAsyncPlan());
        } finally {
            Mekanism.logger = previous;
        }
    }

    @Test
    void unsupportedValidationDiscardsInsteadOfCommittingOrRepeatingWarnings() throws Exception {
        SignatureTile tile = new SignatureTile();
        tile.failure = Failure.VALIDATE;
        AtomicInteger warnings = new AtomicInteger();
        Logger previous = Mekanism.logger;
        try {
            Mekanism.logger = logger(warnings);
            for (int tick = 0; tick < 5; tick++) invoke(tile, "runPlannerSynchronously");
            assertEquals(1, warnings.get());
            assertEquals(5, tile.discards);
            assertEquals(0, tile.commits);
            assertEquals(0, tile.legacyCalls);
            assertFalse(tile.hasPendingAsyncPlan());
        } finally {
            Mekanism.logger = previous;
        }
    }

    private static void invoke(SignatureTile tile, String methodName) throws Exception {
        Method method = TileEntityBasicBlock.class.getDeclaredMethod(methodName, IAsyncMachinePlanner.class);
        method.setAccessible(true);
        method.invoke(tile, tile);
    }

    @Test
    void unsupportedWorkerResultIsDiscardedAndReleasesPendingReservation() throws Exception {
        SignatureTile tile = new SignatureTile();
        Class<?> pendingType = Class.forName(TileEntityBasicBlock.class.getName() + "$PendingAsyncPlan");
        Constructor<?> constructor = pendingType.getDeclaredConstructor(Object.class, long.class, long.class, long.class);
        constructor.setAccessible(true);
        Field failure = pendingType.getDeclaredField("failure");
        failure.setAccessible(true);
        Field pendingField = TileEntityBasicBlock.class.getDeclaredField("pendingAsyncPlan");
        pendingField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Object> pendingReference = (AtomicReference<Object>) pendingField.get(tile);
        Method finish = TileEntityBasicBlock.class.getDeclaredMethod("finishAsyncPlan", pendingType);
        finish.setAccessible(true);
        AtomicInteger warnings = new AtomicInteger();
        Logger previous = Mekanism.logger;
        try {
            Mekanism.logger = logger(warnings);
            for (int tick = 0; tick < 5; tick++) {
                Object pending = constructor.newInstance(1, 0L, 0L, 0L);
                failure.set(pending, new UnsupportedRecipeSignatureException(new Object(), "$", "Unsupported value"));
                pendingReference.set(pending);
                finish.invoke(tile, pending);
                assertFalse(tile.hasPendingAsyncPlan());
            }
            assertEquals(1, warnings.get());
            assertEquals(5, tile.discards);
            assertEquals(0, tile.commits);
            assertEquals(0, tile.legacyCalls);
        } finally {
            Mekanism.logger = previous;
        }
    }

    private static Logger logger(AtomicInteger warnings) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[]{Logger.class}, (proxy, method, args) -> {
            if (method.getName().equals("warn")) warnings.incrementAndGet();
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            return null;
        });
    }

    @Test
    void calculatorInitializationFailureFallsBackWithoutRetainingReservation() throws Exception {
        SignatureTile tile = new SignatureTile();
        tile.failure = Failure.CALCULATOR;
        assertFalse(tile.supportsAsync());
        for (int tick = 0; tick < 3; tick++) invoke(tile, "scheduleAsyncPlan");
        assertEquals(3, tile.calculations);
        assertEquals(3, tile.commits);
        assertEquals(0, tile.legacyCalls);
        assertFalse(tile.hasPendingAsyncPlan());
        tile.failure = Failure.NONE;
        assertTrue(tile.supportsAsync());
    }

    private enum Failure { CAPTURE, VALIDATE, CALCULATOR, NONE }

    private static final class SignatureTile extends TileEntityBasicBlock implements IAsyncMachinePlanner<Integer, Integer> {
        private static final IAsyncPlanCalculator<Integer, Integer> CALCULATOR = value -> value;
        private Failure failure = Failure.CAPTURE;
        private int calculations;
        private int commits;
        private int discards;
        private int legacyCalls;

        public Integer captureSnapshot() {
            if (failure == Failure.CAPTURE) RecipeSnapshotCompiler.semanticSignature(new Object());
            return 1;
        }

        public Integer calculatePlan(Integer snapshot) { calculations++; return snapshot; }
        public IAsyncPlanCalculator<Integer, Integer> getAsyncPlanCalculator() {
            if (failure == Failure.CALCULATOR) throw new NoClassDefFoundError("net/minecraft/client/renderer/texture/TextureAtlasSprite");
            return CALCULATOR;
        }
        public void commitPlan(Integer snapshot, Integer plan) { commits++; }

        public boolean isPlanStillValid(Integer snapshot, Integer plan) {
            if (failure == Failure.VALIDATE) RecipeSnapshotCompiler.semanticSignature(new Object());
            return true;
        }

        public void onPlanDiscarded(Integer snapshot, Integer plan, Throwable cause) { discards++; }

        @Override
        public void onAsyncUpdateServer() { legacyCalls++; }
    }
}
