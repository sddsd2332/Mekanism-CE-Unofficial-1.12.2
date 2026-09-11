package mekanism.common.tile.prefab;

import net.minecraft.init.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for the local transaction boundary used by atomic machine ejection. */
class TileEntityContainerTransactionTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void keepsCompetingContainerWorkOutsideTheEjectionWindow() throws Exception {
        BlockingTile tile = new BlockingTile();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch competingTransactionEntered = new CountDownLatch(1);
        try {
            Future<?> ejection = workers.submit(tile::runAtomicEjectionWindow);
            assertTrue(tile.ejectionWindowEntered.await(5, TimeUnit.SECONDS));

            Future<?> competitor = workers.submit(() -> tile.runContainerTransaction(
                  competingTransactionEntered::countDown));
            assertFalse(competingTransactionEntered.await(200, TimeUnit.MILLISECONDS));

            tile.releaseEjectionWindow.countDown();
            ejection.get(5, TimeUnit.SECONDS);
            competitor.get(5, TimeUnit.SECONDS);
            assertTrue(competingTransactionEntered.await(1, TimeUnit.SECONDS));
        } finally {
            tile.releaseEjectionWindow.countDown();
            workers.shutdownNow();
        }
    }

    private static final class BlockingTile extends TileEntityBasicBlock {

        private final CountDownLatch ejectionWindowEntered = new CountDownLatch(1);
        private final CountDownLatch releaseEjectionWindow = new CountDownLatch(1);

        private void runAtomicEjectionWindow() {
            runContainerTransaction(() -> {
                ejectionWindowEntered.countDown();
                try {
                    if (!releaseEjectionWindow.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release ejection window");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Ejection window was interrupted", error);
                }
            });
        }

        @Override
        public void doRestrictedTick() {
        }
    }
}
