package mekanism.qioprocessing.common.planning;

import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIORecipeCatalogPersistenceTest {

    private static final String HASH =
          "0000000000000000000000000000000000000000000000000000000000000000";

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @TempDir
    Path worldDirectory;

    @Test
    void rescanMarkerSurvivesUntilExplicitlyCleared() throws Exception {
        assertFalse(QIORecipeCatalogPersistence.isRescanRequired(
              worldDirectory.toFile()));

        QIORecipeCatalogPersistence.ScanState requested =
              QIORecipeCatalogPersistence.markRescanRequired(worldDirectory.toFile(),
                    "example:recipe");
        assertTrue(QIORecipeCatalogPersistence.isRescanRequired(
              worldDirectory.toFile()));

        QIORecipeCatalogPersistence.clearRescanRequired(worldDirectory.toFile(),
              requested.requestedEpoch);
        assertFalse(QIORecipeCatalogPersistence.isRescanRequired(
              worldDirectory.toFile()));
    }

    @Test
    void olderSaveCannotClearANewerRescanRequest() throws Exception {
        QIORecipeCatalogPersistence.ScanState first =
              QIORecipeCatalogPersistence.markRescanRequired(worldDirectory.toFile(),
                    "example:first");
        QIORecipeCatalogPersistence.ScanState second =
              QIORecipeCatalogPersistence.markRescanRequired(worldDirectory.toFile(),
                    "example:second");

        QIORecipeCatalogPersistence.ScanState afterOldSave =
              QIORecipeCatalogPersistence.clearRescanRequired(worldDirectory.toFile(),
                    first.requestedEpoch);
        assertTrue(afterOldSave.isRequired());
        assertEquals(second.requestedEpoch, afterOldSave.requestedEpoch);
        assertEquals(first.requestedEpoch, afterOldSave.completedEpoch);

        QIORecipeCatalogPersistence.ScanState complete =
              QIORecipeCatalogPersistence.clearRescanRequired(worldDirectory.toFile(),
                    second.requestedEpoch);
        assertFalse(complete.isRequired());
        assertEquals(second.requestedEpoch, complete.completedEpoch);
    }

    @Test
    void damagedMarkerRequestsAConservativeRebuild() throws Exception {
        Path marker = worldDirectory.resolve("mekanism/qio_processing/recipe_catalog")
              .resolve("scan-state.dat");
        Files.createDirectories(marker.getParent());
        Files.write(marker, new byte[]{1, 2, 3, 4});

        QIORecipeCatalogPersistence.ScanState damaged =
              QIORecipeCatalogPersistence.readScanState(worldDirectory.toFile());
        assertTrue(damaged.isRequired());

        QIORecipeCatalogPersistence.clearRescanRequired(worldDirectory.toFile(),
              damaged.requestedEpoch);
        assertFalse(QIORecipeCatalogPersistence.isRescanRequired(
              worldDirectory.toFile()));
    }

    @Test
    void rejectsUnknownShardCategoriesBeforeBuildingAParsedDirectory() throws Exception {
        // The category guard is intentionally exercised through the same metadata path used by
        // disk loading; unknown categories must never be accumulated into an unbounded map.
        assertThrows(java.io.IOException.class,
              () -> QIORecipeCatalogPersistence.categoryRecordLimit("untrusted"));
    }

    @Test
    void cancellationDuringShardCopyRemovesUnpublishedGeneration() throws Exception {
        BlockingCopyCompound blocking = new BlockingCopyCompound();
        QIOForgeRecipeData.CacheData forgeData = new QIOForgeRecipeData.CacheData(
              Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), HASH);
        QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData cache =
              new QIOWorkbenchRecipeCatalog.RecipeOutputIndex.CacheData(HASH, 0, forgeData,
                    Collections.emptyList(),
                    Collections.<NBTTagCompound>singletonList(blocking),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());
        QIORecipeCatalogPersistence.SaveToken token =
              new QIORecipeCatalogPersistence.SaveToken();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Future<UUID> save = worker.submit(() -> QIORecipeCatalogPersistence.save(
              worldDirectory.toFile(), cache, HASH, null, token));
        try {
            assertTrue(blocking.awaitSaveCopy(), "save did not reach the shard-copy checkpoint");
            token.invalidate();
            blocking.releaseSaveCopy();
            assertNull(save.get(10, TimeUnit.SECONDS));
        } finally {
            token.invalidate();
            blocking.releaseSaveCopy();
            worker.shutdownNow();
        }

        Path root = worldDirectory.resolve("mekanism/qio_processing/recipe_catalog");
        assertFalse(Files.isRegularFile(root.resolve("manifest.dat")));
        assertFalse(hasEntries(root.resolve("staging")));
        assertFalse(hasEntries(root.resolve("generations")));
    }

    @Test
    void shardEstimatorMatchesForgeNbtSizeTracking() throws Exception {
        NBTTagCompound root = new NBTTagCompound();
        root.setString("unicode", "QIO-\u4e2d\u6587-\u0000");
        root.setByteArray("bytes", new byte[]{1, 2, 3, 4});
        root.setIntArray("integers", new int[]{5, 6, 7});
        root.setTag("longs", new NBTTagLongArray(new long[]{8, 9}));
        NBTTagList records = new NBTTagList();
        NBTTagCompound record = new NBTTagCompound();
        record.setInteger("id", 12);
        record.setLong("amount", 34);
        records.appendTag(record);
        root.setTag("records", records);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(root, new DataOutputStream(bytes));
        CountingSizeTracker tracker = new CountingSizeTracker();
        CompressedStreamTools.read(new DataInputStream(
              new ByteArrayInputStream(bytes.toByteArray())), tracker);

        assertTrue(QIORecipeCatalogPersistence.estimatedNbtReadBytes(root) >=
              tracker.readBytes);
    }

    @Test
    void shardEstimatorDoesNotRenderLongArrays() {
        NBTTagLongArray longs = new NBTTagLongArray(new long[]{1, 2, 3}) {
            @Override
            public String toString() {
                throw new AssertionError("long-array estimation must not render the array");
            }
        };

        assertEquals(55, QIORecipeCatalogPersistence.estimatedNbtReadBytes(longs));
    }

    private static final class CountingSizeTracker extends NBTSizeTracker {

        private long readBytes;

        private CountingSizeTracker() {
            super(Long.MAX_VALUE);
        }

        @Override
        public void read(long bits) {
            readBytes += bits / 8;
        }
    }

    private static boolean hasEntries(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) return false;
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isPresent();
        }
    }

    private static final class BlockingCopyCompound extends NBTTagCompound {

        private final AtomicInteger copies = new AtomicInteger();
        private final CountDownLatch saveCopyStarted = new CountDownLatch(1);
        private final CountDownLatch saveCopyReleased = new CountDownLatch(1);

        @Override
        public NBTTagCompound copy() {
            // CacheData takes one defensive copy. The next copy occurs while a shard is written.
            if (copies.incrementAndGet() == 1) return this;
            saveCopyStarted.countDown();
            try {
                if (!saveCopyReleased.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release shard copy");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Shard copy was interrupted", error);
            }
            return super.copy();
        }

        private boolean awaitSaveCopy() throws InterruptedException {
            return saveCopyStarted.await(10, TimeUnit.SECONDS);
        }

        private void releaseSaveCopy() {
            saveCopyReleased.countDown();
        }
    }
}
