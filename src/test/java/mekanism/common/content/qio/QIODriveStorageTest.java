package mekanism.common.content.qio;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIODriveStorageTest {

    private File worldDirectory;

    @AfterEach
    void cleanUp() throws Exception {
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
        if (worldDirectory != null) {
            delete(worldDirectory);
        }
    }

    @Test
    void repeatedLoadOfCurrentWorldDoesNotCanonicalizeAgain() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-drive-load-test").toFile();
        CountingCanonicalFile countingDirectory = new CountingCanonicalFile(worldDirectory);

        QIODriveStorage.INSTANCE.createOrLoad(countingDirectory);
        QIODriveStorage.INSTANCE.createOrLoad(countingDirectory);

        assertEquals(1, countingDirectory.getCanonicalizationCount());
    }

    @Test
    void duplicateUuidIsLockedAcrossHoldersAndReleasedOnUnmount() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-mount-test").toFile();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        UUID drive = UUID.randomUUID();
        assertNotNull(QIODriveStorage.INSTANCE.getOrCreate(drive, mekanism.common.tier.QIODriveTier.BASE));

        TestHolder first = new TestHolder(0, new BlockPos(1, 2, 3));
        TestHolder second = new TestHolder(0, new BlockPos(4, 5, 6));
        QIODriveMount firstMount = new QIODriveMount(first, 0);
        QIODriveMount secondMount = new QIODriveMount(second, 0);
        assertEquals(QIODriveStorage.MountResult.MOUNTED, QIODriveStorage.INSTANCE.tryMount(drive, firstMount));
        assertEquals(QIODriveStorage.MountResult.DUPLICATE_UUID, QIODriveStorage.INSTANCE.tryMount(drive, secondMount));
        QIODriveStorage.INSTANCE.unmountAllForHolder(first);
        assertEquals(QIODriveStorage.MountResult.MOUNTED, QIODriveStorage.INSTANCE.tryMount(drive, secondMount));
    }

    @Test
    void existingDriveRecordUpgradesButNeverDowngrades() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-tier-upgrade-test").toFile();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        UUID drive = UUID.randomUUID();
        QIODriveRecord record = QIODriveStorage.INSTANCE.getOrCreate(drive, mekanism.common.tier.QIODriveTier.BASE);
        assertNotNull(record);
        assertEquals(mekanism.common.tier.QIODriveTier.BASE, record.getTier());
        long revision = QIODriveStorage.INSTANCE.getMountRevision();

        assertEquals(record, QIODriveStorage.INSTANCE.getOrCreate(drive, mekanism.common.tier.QIODriveTier.SUPERMASSIVE));
        assertEquals(mekanism.common.tier.QIODriveTier.SUPERMASSIVE, record.getTier());
        assertTrue(QIODriveStorage.INSTANCE.getMountRevision() > revision);
        assertEquals(record, QIODriveStorage.INSTANCE.getOrCreate(drive, mekanism.common.tier.QIODriveTier.BASE));
        assertEquals(mekanism.common.tier.QIODriveTier.SUPERMASSIVE, record.getTier());
    }

    @Test
    void legacyRecordUpgradeMarksManagedStorageDirtyAndAdvancesRevision() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-legacy-record-upgrade-test").toFile();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        UUID drive = UUID.randomUUID();
        QIODriveRecord record = QIODriveStorage.INSTANCE.getOrCreate(drive, mekanism.common.tier.QIODriveTier.BASE);
        long revision = QIODriveStorage.INSTANCE.getMountRevision();

        assertTrue(record.upgradeTier(mekanism.common.tier.QIODriveTier.HYPER_DENSE));
        assertTrue(QIODriveStorage.INSTANCE.getMountRevision() > revision);
        QIODriveStorage.INSTANCE.flush();
        QIODriveStorage.INSTANCE.reset();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);

        assertEquals(mekanism.common.tier.QIODriveTier.HYPER_DENSE,
              QIODriveStorage.INSTANCE.get(drive).getTier());
    }

    @Test
    void replacementHolderAtSamePositionWaitsForExplicitUnmount() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-mount-handoff-test").toFile();
        QIODriveStorage.INSTANCE.createOrLoad(worldDirectory);
        UUID drive = UUID.randomUUID();
        assertNotNull(QIODriveStorage.INSTANCE.getOrCreate(drive, mekanism.common.tier.QIODriveTier.BASE));

        TestHolder oldHolder = new TestHolder(0, new BlockPos(3, 4, 5));
        TestHolder replacement = new TestHolder(0, new BlockPos(3, 4, 5));
        QIODriveMount oldMount = new QIODriveMount(oldHolder, 0);
        QIODriveMount replacementMount = new QIODriveMount(replacement, 0);
        assertEquals(QIODriveStorage.MountResult.MOUNTED, QIODriveStorage.INSTANCE.tryMount(drive, oldMount));
        assertEquals(QIODriveStorage.MountResult.DUPLICATE_UUID,
              QIODriveStorage.INSTANCE.tryMount(drive, replacementMount));
        assertEquals(oldHolder, QIODriveStorage.INSTANCE.getActiveMount(drive).getHolder());

        QIODriveStorage.INSTANCE.unmount(replacementMount);
        assertEquals(oldHolder, QIODriveStorage.INSTANCE.getActiveMount(drive).getHolder());
        QIODriveStorage.INSTANCE.unmount(oldMount);
        assertEquals(QIODriveStorage.MountResult.MOUNTED, QIODriveStorage.INSTANCE.tryMount(drive, replacementMount));
        assertEquals(replacement, QIODriveStorage.INSTANCE.getActiveMount(drive).getHolder());
    }

    private static void delete(File file) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    private static final class CountingCanonicalFile extends File {

        private int canonicalizationCount;

        private CountingCanonicalFile(File file) {
            super(file.getPath());
        }

        @Override
        public File getCanonicalFile() throws java.io.IOException {
            canonicalizationCount++;
            return super.getCanonicalFile();
        }

        private int getCanonicalizationCount() {
            return canonicalizationCount;
        }
    }

    private static final class TestHolder implements IQIODriveHolder {

        private final int dimension;
        private final BlockPos position;

        private TestHolder(int dimension, BlockPos position) {
            this.dimension = dimension;
            this.position = position;
        }

        @Override
        public int getQIODimension() {
            return dimension;
        }

        @Override
        public BlockPos getQIOPosition() {
            return position;
        }
    }
}
