package mekanism.qioprocessing.common.content;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOProcessingNetworkManagerTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    private File worldDirectory;
    private QIOProcessingNetworkManager manager;

    @BeforeEach
    void setup() throws Exception {
        worldDirectory = Files.createTempDirectory("qio-processing-manager-test").toFile();
        manager = QIOProcessingNetworkManager.INSTANCE;
        manager.resetForTests();
    }

    @AfterEach
    void cleanup() throws Exception {
        manager.resetForTests();
        delete(worldDirectory);
    }

    @Test
    void repeatedLoadOfCurrentWorldDoesNotCanonicalizeAgain() throws Exception {
        CountingCanonicalFile countingDirectory = new CountingCanonicalFile(worldDirectory);

        manager.createOrLoad(countingDirectory);
        manager.createOrLoad(countingDirectory);

        assertEquals(1, countingDirectory.getCanonicalizationCount());
    }

    @Test
    void independentlySavedFrequencyRoundTripsThroughTheWorldIndex() {
        manager.createOrLoad(worldDirectory);
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = manager.getOrCreate(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("persisted", UUID.randomUUID(), SecurityMode.PRIVATE));
        network.createJob(QIOCraftingJobSource.MANUAL, UUID.randomUUID(), 5, 42,
              plan(2), 100);

        manager.flush();
        assertTrue(new File(worldDirectory, "mekanism/qio_processing/index.dat").isFile());
        assertTrue(new File(worldDirectory, "mekanism/qio_processing/networks/" +
              frequencyUUID + ".dat").isFile());

        manager.resetForTests();
        manager.createOrLoad(worldDirectory);
        QIOProcessingNetworkData restored = manager.get(frequencyUUID);

        assertNotNull(restored);
        assertEquals("persisted", restored.getLastKnownFrequencyIdentity().getName());
        assertEquals(1, restored.getJobs().size());
        assertFalse(restored.wasRepairedOnLoad());
    }

    @Test
    void executionCheckpointKeepsNetworkDirtyForBackupRotation() throws Exception {
        manager.createOrLoad(worldDirectory);
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = manager.getOrCreate(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("checkpoint", null, SecurityMode.PUBLIC));
        manager.flush();
        network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0, plan(1), 10);

        manager.checkpointNetwork(frequencyUUID);

        File active = QIOProcessingFileIO.dataFile(networkDirectory(), frequencyUUID);
        File backup = QIOProcessingFileIO.backupFile(active);
        assertEquals(1, QIOProcessingNetworkData.read(
              QIOProcessingFileIO.read(active), frequencyUUID).getJobs().size());
        assertFalse(backup.isFile());

        manager.flush();

        assertTrue(backup.isFile());
        assertEquals(1, QIOProcessingNetworkData.read(
              QIOProcessingFileIO.read(backup), frequencyUUID).getJobs().size());
    }

    @Test
    void legacyActiveNetworkIsArchivedResetAndReadyForDeviceRepublish()
          throws Exception {
        UUID frequencyUUID = UUID.randomUUID();
        UUID ownerUUID = UUID.randomUUID();
        UUID deviceUUID = UUID.randomUUID();
        QIOProcessingNetworkData legacyNetwork = new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("legacy-active", ownerUUID,
                    SecurityMode.PRIVATE));
        legacyNetwork.createJob(QIOCraftingJobSource.MANUAL, ownerUUID, 4, 12,
              plan(2), 100);
        legacyNetwork.observeAutomationDevice(snapshot(deviceUUID), 100);
        UUID legacyConfigUUID = legacyNetwork.getWorkbenchConfiguration().getConfigUUID();

        File networkDirectory = networkDirectory();
        assertTrue(networkDirectory.mkdirs());
        File active = QIOProcessingFileIO.dataFile(networkDirectory, frequencyUUID);
        NBTTagCompound legacyData = legacyData(legacyNetwork);
        QIOProcessingFileIO.writeAtomic(active, legacyData);
        QIOProcessingFileIO.writeAtomic(active, legacyData);

        manager.createOrLoad(worldDirectory);

        QIOProcessingNetworkData replacement = manager.get(frequencyUUID);
        assertNotNull(replacement);
        assertEquals(frequencyUUID, replacement.getFrequencyUUID());
        assertEquals("legacy-active", replacement.getLastKnownFrequencyIdentity().getName());
        assertEquals(ownerUUID, replacement.getLastKnownFrequencyIdentity().getOwnerUUID());
        assertEquals(SecurityMode.PRIVATE,
              replacement.getLastKnownFrequencyIdentity().getSecurityMode());
        assertTrue(replacement.getJobs().isEmpty());
        assertEquals(0, replacement.getAutomationDevices().size());
        assertFalse(legacyConfigUUID.equals(
              replacement.getWorkbenchConfiguration().getConfigUUID()));
        assertFalse(manager.getDamagedNetworks().contains(frequencyUUID));
        assertFalse(manager.getFutureNetworks().contains(frequencyUUID));
        assertTrue(hasFilePrefix(networkDirectory, frequencyUUID + ".dat.legacy"));
        assertTrue(hasFilePrefix(networkDirectory, frequencyUUID + ".dat.bak.legacy"));
        assertEquals(QIOProcessingNetworkData.SCHEMA_VERSION,
              QIOProcessingFileIO.read(active).getInteger("networkDataSchemaVersion"));
        assertEquals(QIOProcessingNetworkData.SCHEMA_VERSION,
              QIOProcessingFileIO.read(QIOProcessingFileIO.backupFile(active))
                    .getInteger("networkDataSchemaVersion"));

        assertTrue(replacement.observeAutomationDevice(snapshot(deviceUUID), 100));
        assertNotNull(replacement.getAutomationDevices().get(deviceUUID));
    }

    @Test
    void legacyBackupRebuildsAPreviouslyIsolatedMissingNetwork() throws Exception {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData legacyNetwork = new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("legacy-backup", null,
                    SecurityMode.PUBLIC));
        legacyNetwork.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0, plan(1), 10);

        File networkDirectory = networkDirectory();
        assertTrue(networkDirectory.mkdirs());
        File active = QIOProcessingFileIO.dataFile(networkDirectory, frequencyUUID);
        NBTTagCompound legacyData = legacyData(legacyNetwork);
        QIOProcessingFileIO.writeAtomic(active, legacyData);
        QIOProcessingFileIO.writeAtomic(active, legacyData);
        assertNotNull(QIOProcessingFileIO.quarantine(active, ".damaged"));

        manager.createOrLoad(worldDirectory);

        QIOProcessingNetworkData replacement = manager.get(frequencyUUID);
        assertNotNull(replacement);
        assertEquals("legacy-backup", replacement.getLastKnownFrequencyIdentity().getName());
        assertTrue(replacement.getJobs().isEmpty());
        assertNull(manager.getIsolationStatus(frequencyUUID));
        assertTrue(active.isFile());
        assertTrue(hasFilePrefix(networkDirectory, frequencyUUID + ".dat.damaged"));
        assertTrue(hasFilePrefix(networkDirectory, frequencyUUID + ".dat.bak.legacy"));
        assertEquals(QIOProcessingNetworkData.SCHEMA_VERSION,
              QIOProcessingFileIO.read(active).getInteger("networkDataSchemaVersion"));
    }

    @Test
    void malformedCurrentWorkbenchStateIsResetOnAuthorizedFrequencyAccess()
          throws Exception {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("current-damaged", null,
                    SecurityMode.PUBLIC));
        NBTTagCompound malformed = network.write();
        malformed.getCompoundTag("workbenchConfiguration").removeTag("patternRevision");

        File networkDirectory = networkDirectory();
        assertTrue(networkDirectory.mkdirs());
        File active = QIOProcessingFileIO.dataFile(networkDirectory, frequencyUUID);
        QIOProcessingFileIO.writeAtomic(active, malformed);

        manager.createOrLoad(worldDirectory);

        assertNull(manager.get(frequencyUUID));
        assertEquals(QIOProcessingNetworkManager.IsolationStatus.DAMAGED,
              manager.getIsolationStatus(frequencyUUID));
        assertTrue(hasFilePrefix(networkDirectory, frequencyUUID + ".dat.damaged"));
        manager.flush();
        QIOProcessingNetworkData replacement = manager.getOrCreate(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("recovered", null, SecurityMode.PUBLIC));
        assertNotNull(replacement);
        assertEquals("recovered", replacement.getLastKnownFrequencyIdentity().getName());
        assertTrue(replacement.getJobs().isEmpty());
        assertNull(manager.getIsolationStatus(frequencyUUID));
        assertTrue(active.isFile());
        assertEquals(QIOProcessingNetworkData.SCHEMA_VERSION,
              QIOProcessingFileIO.read(active).getInteger("networkDataSchemaVersion"));

        manager.resetForTests();
        manager.createOrLoad(worldDirectory);
        assertNotNull(manager.get(frequencyUUID));
        assertNull(manager.getIsolationStatus(frequencyUUID));
    }

    @Test
    void legacyNetworkWithMismatchedStoredUUIDRemainsDamaged() throws Exception {
        UUID fileUUID = UUID.randomUUID();
        QIOProcessingNetworkData differentNetwork = new QIOProcessingNetworkData(
              UUID.randomUUID(), new QIOFrequencyIdentitySnapshot("mismatched", null,
                    SecurityMode.PUBLIC));

        File networkDirectory = networkDirectory();
        assertTrue(networkDirectory.mkdirs());
        File active = QIOProcessingFileIO.dataFile(networkDirectory, fileUUID);
        QIOProcessingFileIO.writeAtomic(active, legacyData(differentNetwork));

        manager.createOrLoad(worldDirectory);

        assertNull(manager.get(fileUUID));
        assertEquals(QIOProcessingNetworkManager.IsolationStatus.DAMAGED,
              manager.getIsolationStatus(fileUUID));
        assertTrue(hasFilePrefix(networkDirectory, fileUUID + ".dat.damaged"));
        assertFalse(hasFilePrefix(networkDirectory, fileUUID + ".dat.legacy"));
    }

    @Test
    void damagedCurrentNetworkRestoresTheLastVerifiedCurrentSchemaBackup()
          throws Exception {
        manager.createOrLoad(worldDirectory);
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = manager.getOrCreate(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("backup", null, SecurityMode.PUBLIC));
        manager.flush();
        network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0, plan(1), 10);
        manager.flush();

        File active = new File(worldDirectory, "mekanism/qio_processing/networks/" +
              frequencyUUID + ".dat");
        File backup = QIOProcessingFileIO.backupFile(active);
        assertTrue(backup.isFile());
        assertEquals(QIOProcessingNetworkData.SCHEMA_VERSION,
              QIOProcessingFileIO.read(backup).getInteger("networkDataSchemaVersion"));

        manager.resetForTests();
        Files.write(active.toPath(), new byte[]{1, 2, 3, 4});
        manager.createOrLoad(worldDirectory);

        QIOProcessingNetworkData restored = manager.get(frequencyUUID);
        assertNotNull(restored);
        assertTrue(restored.getJobs().isEmpty(),
              "Recovery must use the previous verified generation");
        assertFalse(manager.getDamagedNetworks().contains(frequencyUUID));
        assertTrue(hasFilePrefix(active.getParentFile(),
              frequencyUUID + ".dat.damaged"));
        assertEquals(QIOProcessingNetworkData.SCHEMA_VERSION,
              QIOProcessingFileIO.read(active).getInteger("networkDataSchemaVersion"));
    }

    @Test
    void missingCurrentNetworkRestoresTheLastVerifiedCurrentSchemaBackup()
          throws Exception {
        manager.createOrLoad(worldDirectory);
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = manager.getOrCreate(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("missing-active", null,
                    SecurityMode.PUBLIC));
        manager.flush();
        network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0, plan(1), 10);
        manager.flush();
        manager.resetForTests();

        File active = new File(worldDirectory, "mekanism/qio_processing/networks/" +
              frequencyUUID + ".dat");
        assertTrue(QIOProcessingFileIO.backupFile(active).isFile());
        assertTrue(Files.deleteIfExists(active.toPath()));

        manager.createOrLoad(worldDirectory);

        QIOProcessingNetworkData restored = manager.get(frequencyUUID);
        assertNotNull(restored);
        assertTrue(restored.getJobs().isEmpty());
        assertTrue(active.isFile());
    }

    @Test
    void futureNetworkNeverDowngradesToItsCurrentSchemaBackup() throws Exception {
        manager.createOrLoad(worldDirectory);
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = manager.getOrCreate(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("future-with-backup", null,
                    SecurityMode.PUBLIC));
        manager.flush();
        network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0, plan(1), 10);
        manager.flush();
        manager.resetForTests();

        File active = new File(worldDirectory, "mekanism/qio_processing/networks/" +
              frequencyUUID + ".dat");
        NBTTagCompound futureData = new NBTTagCompound();
        futureData.setInteger("networkDataSchemaVersion",
              QIOProcessingNetworkData.SCHEMA_VERSION + 1);
        QIOProcessingNbt.writeUUID(futureData, "frequencyUUID", frequencyUUID);
        QIOProcessingFileIO.writeAtomic(active, futureData);
        assertTrue(QIOProcessingFileIO.backupFile(active).isFile());

        manager.createOrLoad(worldDirectory);

        assertNull(manager.get(frequencyUUID));
        assertTrue(manager.getFutureNetworks().contains(frequencyUUID));
        assertTrue(hasFilePrefix(active.getParentFile(),
              frequencyUUID + ".dat.future"));
    }

    @Test
    void damagedWorldIndexRestoresItsVerifiedCurrentSchemaBackup() throws Exception {
        manager.createOrLoad(worldDirectory);
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = manager.getOrCreate(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("index-backup", null,
                    SecurityMode.PUBLIC));
        manager.flush();
        network.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0, plan(1), 10);
        manager.flush();
        manager.resetForTests();

        File index = new File(worldDirectory, "mekanism/qio_processing/index.dat");
        assertTrue(QIOProcessingFileIO.backupFile(index).isFile());
        Files.write(index.toPath(), new byte[]{1, 2, 3, 4});

        manager.createOrLoad(worldDirectory);

        assertFalse(manager.isReadOnlyFutureIndex());
        assertNotNull(manager.get(frequencyUUID));
        assertEquals(QIOProcessingNetworkManager.WORLD_INDEX_SCHEMA_VERSION,
              QIOProcessingFileIO.read(index).getInteger("worldIndexSchemaVersion"));
        assertTrue(hasFilePrefix(index.getParentFile(), "index.dat.damaged"));
    }

    @Test
    void damagedAndFutureNetworkFilesAreIsolatedWithoutBeingOverwritten() throws Exception {
        manager.createOrLoad(worldDirectory);
        UUID damaged = UUID.randomUUID();
        manager.getOrCreate(damaged,
              new QIOFrequencyIdentitySnapshot("damaged", null, SecurityMode.PUBLIC));
        manager.flush();
        manager.resetForTests();
        File damagedFile = new File(worldDirectory, "mekanism/qio_processing/networks/" +
              damaged + ".dat");
        Files.write(damagedFile.toPath(), new byte[]{1, 2, 3, 4});

        UUID future = UUID.randomUUID();
        File networkDirectory = damagedFile.getParentFile();
        NBTTagCompound futureData = new NBTTagCompound();
        futureData.setInteger("networkDataSchemaVersion", QIOProcessingNetworkData.SCHEMA_VERSION + 1);
        QIOProcessingNbt.writeUUID(futureData, "frequencyUUID", future);
        QIOProcessingFileIO.writeAtomic(QIOProcessingFileIO.dataFile(networkDirectory, future), futureData);

        manager.createOrLoad(worldDirectory);

        assertNull(manager.get(damaged));
        assertNull(manager.get(future));
        assertTrue(manager.getDamagedNetworks().contains(damaged));
        assertTrue(manager.getFutureNetworks().contains(future));
        assertEquals(QIOProcessingNetworkManager.IsolationStatus.DAMAGED,
              manager.getIsolationStatus(damaged));
        assertEquals(QIOProcessingNetworkManager.IsolationStatus.FUTURE,
              manager.getIsolationStatus(future));
        QIOProcessingNetworkManager.IsolatedNetworkException futureError = assertThrows(
              QIOProcessingNetworkManager.IsolatedNetworkException.class,
              () -> manager.getOrCreate(future,
                    new QIOFrequencyIdentitySnapshot("overwrite", null,
                          SecurityMode.PUBLIC)));
        assertEquals(future, futureError.getFrequencyUUID());
        assertEquals(QIOProcessingNetworkManager.IsolationStatus.FUTURE,
              futureError.getStatus());
        assertTrue(hasFilePrefix(networkDirectory, damaged + ".dat.damaged"));
        assertTrue(hasFilePrefix(networkDirectory, future + ".dat.future"));
    }

    @Test
    void newerWorldIndexMakesTheStoreExplicitlyReadOnly() throws Exception {
        File root = new File(worldDirectory, "mekanism/qio_processing");
        assertTrue(root.mkdirs());
        NBTTagCompound index = new NBTTagCompound();
        index.setInteger("worldIndexSchemaVersion",
              QIOProcessingNetworkManager.WORLD_INDEX_SCHEMA_VERSION + 1);
        QIOProcessingFileIO.writeAtomic(new File(root, "index.dat"), index);

        manager.createOrLoad(worldDirectory);

        assertTrue(manager.isLoaded());
        assertTrue(manager.isReadOnlyFutureIndex());
        assertThrows(IllegalStateException.class, () -> manager.getOrCreate(UUID.randomUUID(),
              new QIOFrequencyIdentitySnapshot("future", null, SecurityMode.PUBLIC)));
    }

    @Test
    void frequencyLifecycleBlocksAssetsAndPersistsCleanDeletionTombstone() throws Exception {
        manager.createOrLoad(worldDirectory);
        UUID activeUUID = UUID.randomUUID();
        QIOProcessingNetworkData active = manager.getOrCreate(activeUUID,
              new QIOFrequencyIdentitySnapshot("active", null, SecurityMode.PUBLIC));
        active.createJob(QIOCraftingJobSource.MANUAL, null, 0, 0, plan(1), 10);
        mekanism.api.qio.external.QIOFrequencyReference activeReference =
              new mekanism.api.qio.external.QIOFrequencyReference(activeUUID, "active", null,
                    SecurityMode.PUBLIC, null);

        assertFalse(QIOProcessingFrequencyLifecycle.INSTANCE.beforeDelete(activeReference, null)
              .isAllowed());
        assertTrue(active.getFrequencyDeletionBlockers().contains("non_terminal_jobs"));
        assertTrue(active.getFrequencyDeletionBlockers().contains("material_claims"));

        UUID cleanUUID = UUID.randomUUID();
        QIOProcessingNetworkData clean = manager.getOrCreate(cleanUUID,
              new QIOFrequencyIdentitySnapshot("clean", null, SecurityMode.PUBLIC));
        mekanism.api.qio.external.QIOFrequencyReference cleanReference =
              new mekanism.api.qio.external.QIOFrequencyReference(cleanUUID, "clean", null,
                    SecurityMode.PUBLIC, null);
        assertTrue(QIOProcessingFrequencyLifecycle.INSTANCE.beforeDelete(cleanReference, null)
              .isAllowed());

        QIOProcessingFrequencyLifecycle.INSTANCE.afterDelete(cleanReference, null);

        assertEquals(QIOProcessingNetworkLifecycle.ORPHANED_FREQUENCY, clean.getLifecycle());
        File saved = new File(worldDirectory, "mekanism/qio_processing/networks/" +
              cleanUUID + ".dat");
        assertTrue(saved.isFile());
        assertEquals(QIOProcessingNetworkLifecycle.ORPHANED_FREQUENCY,
              QIOProcessingNetworkData.read(QIOProcessingFileIO.read(saved), cleanUUID)
                    .getLifecycle());

        QIOProcessingNetworkData sameNameReplacement = manager.getOrCreate(UUID.randomUUID(),
              new QIOFrequencyIdentitySnapshot("clean", null, SecurityMode.PUBLIC));
        assertEquals(QIOProcessingNetworkLifecycle.ACTIVE, sameNameReplacement.getLifecycle());
        assertEquals(QIOProcessingNetworkLifecycle.ORPHANED_FREQUENCY, clean.getLifecycle());

        QIOProcessingNetworkData rolledBackCoreFrequency = manager.getOrCreate(cleanUUID,
              new QIOFrequencyIdentitySnapshot("clean", null, SecurityMode.PUBLIC));
        assertEquals(QIOProcessingNetworkLifecycle.ACTIVE,
              rolledBackCoreFrequency.getLifecycle());
    }

    private static QIOCraftPlan plan(long required) {
        PortableResourceDescriptor input = PortableResourceDescriptor.item(new ItemStack(Blocks.STONE));
        return new QIOCraftPlan(UUID.randomUUID(), 1,
              new QIOPlanSourceRevisions(0, 0, 0, 0, 0, 0, 0, 0),
              PortableResourceDescriptor.item(new ItemStack(Blocks.DIRT)), 1,
              required == 0 ? Collections.emptyMap() : Collections.singletonMap(input, required));
    }

    private File networkDirectory() {
        return new File(worldDirectory, "mekanism/qio_processing/networks");
    }

    private static NBTTagCompound legacyData(QIOProcessingNetworkData network) {
        NBTTagCompound data = network.write();
        data.setInteger("networkDataSchemaVersion",
              QIOProcessingNetworkData.SCHEMA_VERSION - 1);
        NBTTagCompound workbench = data.getCompoundTag("workbenchConfiguration");
        workbench.setInteger("schema", 2);
        workbench.removeTag("patternRevision");
        workbench.removeTag("encodedPatterns");
        return data;
    }

    private static QIOAutomationDeviceSnapshot snapshot(UUID deviceUUID) {
        return new QIOAutomationDeviceSnapshot(deviceUUID,
              new QIOAutomationDeviceLocation(0, new BlockPos(1, 2, 3)),
              "mekanism:machineblock", 0, "mekanism:test_provider",
              QIOAutomationMode.SCHEDULED, QIOAutomationHost.State.ACTIVE, true,
              10, 1, 1, 1, 0, null);
    }

    private static boolean hasFilePrefix(File directory, String prefix) {
        File[] files = directory.listFiles(file -> file.getName().startsWith(prefix));
        return files != null && files.length > 0;
    }

    private static void delete(File file) throws Exception {
        if (file == null || !file.exists()) {
            return;
        }
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
}
