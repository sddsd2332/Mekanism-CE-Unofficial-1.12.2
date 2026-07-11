package mekanism.common.tile;

import mekanism.common.tile.prefab.TileEntityBasicBlock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerTransactionTest {

    @Test
    void transactionsAreReentrantAndSerializeTheSameOwner() throws Exception {
        TestTile tile = new TestTile();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> increment(tile, 10_000));
            Future<?> second = executor.submit(() -> increment(tile, 10_000));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            tile.runContainerTransaction(() -> tile.runContainerTransaction(tile.value::incrementAndGet));
            assertEquals(20_001, tile.value.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void externalTryOperationRejectsWithoutMutatingWhenOwnerIsBusy() throws Exception {
        TestTile tile = new TestTile();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = executor.submit(() -> tile.runContainerTransaction(() -> {
                entered.countDown();
                await(release);
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            assertFalse(tile.tryIncrement());
            assertEquals(0, tile.value.get());
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);

            assertTrue(tile.tryIncrement());
            assertEquals(1, tile.value.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void differentOwnersDoNotShareAGlobalLock() throws Exception {
        TestTile blocked = new TestTile();
        TestTile independent = new TestTile();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> blocked.runContainerTransaction(() -> {
                entered.countDown();
                await(release);
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            Future<?> otherOwner = executor.submit(() -> independent.runContainerTransaction(independent.value::incrementAndGet));
            otherOwner.get(2, TimeUnit.SECONDS);
            assertEquals(1, independent.value.get());

            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void increment(TestTile tile, int times) {
        for (int i = 0; i < times; i++) {
            tile.runContainerTransaction(() -> {
                int current = tile.value.get();
                Thread.yield();
                tile.value.set(current + 1);
            });
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for transaction test latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static class TestTile extends TileEntityBasicBlock {

        private final AtomicInteger value = new AtomicInteger();

        private boolean tryIncrement() {
            return tryRunContainerTransaction(value::incrementAndGet);
        }
    }
}
