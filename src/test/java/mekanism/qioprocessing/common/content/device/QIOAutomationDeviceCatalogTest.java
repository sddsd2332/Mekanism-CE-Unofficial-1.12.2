package mekanism.qioprocessing.common.content.device;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.processing.MachinePresentationDescriptor;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants.NBT;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOAutomationDeviceCatalogTest {

    @Test
    void onlineObservationTransitionsOfflineAndPersists() throws Exception {
        QIOAutomationDeviceCatalog catalog = new QIOAutomationDeviceCatalog();
        UUID deviceUUID = UUID.randomUUID();
        QIOAutomationDeviceLocation location = location(0, 1, 2, 3);

        assertTrue(catalog.observe(snapshot(deviceUUID, location, 10, 1), 8));
        assertEquals(1, catalog.getRevision());
        assertFalse(catalog.markOffline(deviceUUID, location(0, 9, 9, 9), 20));
        assertTrue(catalog.markOffline(deviceUUID, location, 30));
        assertEquals(2, catalog.getRevision());
        assertFalse(catalog.get(deviceUUID).isOnline());
        assertEquals(30, catalog.get(deviceUUID).getLastSeenTick());
        assertFalse(catalog.markOffline(deviceUUID, location, 40));

        QIOAutomationDeviceCatalog restored = QIOAutomationDeviceCatalog.read(catalog.write());
        assertEquals(2, restored.getRevision());
        assertFalse(restored.get(deviceUUID).isOnline());
        assertEquals(30, restored.get(deviceUUID).getLastSeenTick());
    }

    @Test
    void networkReloadForcesEveryPersistedDeviceOffline() throws Exception {
        UUID frequencyUUID = UUID.randomUUID();
        UUID deviceUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("devices", null, SecurityMode.PUBLIC));
        assertTrue(network.observeAutomationDevice(snapshot(deviceUUID,
              location(-1, 4, 5, 6), 77, 3), 8));

        QIOProcessingNetworkData restored = QIOProcessingNetworkData.read(network.write(),
              frequencyUUID);

        QIOAutomationDeviceSnapshot loaded = restored.getAutomationDevices().get(deviceUUID);
        assertNotNull(loaded);
        assertFalse(loaded.isOnline());
        assertEquals(77, loaded.getLastSeenTick());
        assertTrue(restored.wasRepairedOnLoad());
        assertEquals(2, restored.getAutomationDevices().getRevision());
    }

    @Test
    void aNewUuidAtTheSameCoordinateDoesNotInheritTheOldRecord() {
        QIOAutomationDeviceCatalog catalog = new QIOAutomationDeviceCatalog();
        QIOAutomationDeviceLocation location = location(7, 10, 20, 30);
        UUID oldUUID = UUID.randomUUID();
        UUID newUUID = UUID.randomUUID();

        assertTrue(catalog.observe(snapshot(oldUUID, location, 4, 2), 8));
        assertTrue(catalog.markOffline(oldUUID, location, 5));
        assertTrue(catalog.observe(snapshot(newUUID, location, 6, 19), 8));

        assertEquals(2, catalog.size());
        assertFalse(catalog.get(oldUUID).isOnline());
        assertEquals(2, catalog.get(oldUUID).getConfigurationRevision());
        assertTrue(catalog.get(newUUID).isOnline());
        assertEquals(19, catalog.get(newUUID).getConfigurationRevision());
    }

    @Test
    void forgettingRequiresAnOfflineRecordAndTheCurrentDirectoryRevision() {
        QIOAutomationDeviceCatalog catalog = new QIOAutomationDeviceCatalog();
        UUID firstUUID = UUID.randomUUID();
        UUID secondUUID = UUID.randomUUID();
        QIOAutomationDeviceLocation firstLocation = location(0, 1, 0, 0);
        QIOAutomationDeviceLocation secondLocation = location(0, 2, 0, 0);
        assertTrue(catalog.observe(snapshot(firstUUID, firstLocation, 1, 1), 8));

        assertThrows(IllegalStateException.class,
              () -> catalog.remove(firstUUID, catalog.getRevision()));
        assertTrue(catalog.markOffline(firstUUID, firstLocation, 2));
        long staleRevision = catalog.getRevision();
        assertTrue(catalog.observe(snapshot(secondUUID, secondLocation, 3, 1), 8));
        assertThrows(IllegalStateException.class,
              () -> catalog.remove(firstUUID, staleRevision));
        assertTrue(catalog.remove(firstUUID, catalog.getRevision()));
        assertEquals(1, catalog.size());
        assertThrows(IllegalStateException.class,
              () -> catalog.remove(secondUUID, catalog.getRevision()));
    }

    @Test
    void authoritativeMembershipRemovalIsLocationGuardedAndAllowsOnlineRecords() {
        UUID frequencyUUID = UUID.randomUUID();
        UUID deviceUUID = UUID.randomUUID();
        QIOAutomationDeviceLocation location = location(0, 8, 9, 10);
        QIOProcessingNetworkData network = new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("membership", null, SecurityMode.PUBLIC));
        assertTrue(network.observeAutomationDevice(snapshot(deviceUUID, location, 4, 1), 8));

        assertFalse(network.forgetAutomationDeviceMembership(deviceUUID,
              location(0, 80, 90, 100)));
        assertNotNull(network.getAutomationDevices().get(deviceUUID));
        assertTrue(network.getAutomationDevices().get(deviceUUID).isOnline());

        assertTrue(network.forgetAutomationDeviceMembership(deviceUUID, location));
        assertNull(network.getAutomationDevices().get(deviceUUID));
        assertFalse(network.forgetAutomationDeviceMembership(deviceUUID, location));
    }

    @Test
    void coordinateIndexFollowsObservationsAndRemovals() {
        QIOAutomationDeviceCatalog catalog = new QIOAutomationDeviceCatalog();
        UUID deviceUUID = UUID.randomUUID();
        QIOAutomationDeviceLocation first = location(2, 31, 70, -17);
        QIOAutomationDeviceLocation second = location(2, 48, 70, -17);
        assertTrue(catalog.observe(snapshot(deviceUUID, first, 1, 1), 8));
        assertEquals(1, catalog.getDevicesInChunk(2, 1, -2).size());
        assertTrue(catalog.observe(snapshot(deviceUUID, second, 2, 2), 8));
        assertTrue(catalog.getDevicesInChunk(2, 1, -2).isEmpty());
        assertEquals(1, catalog.getDevicesInChunk(2, 3, -2).size());
        assertTrue(catalog.removeAtLocation(deviceUUID, second));
        assertTrue(catalog.getDevicesInChunk(2, 3, -2).isEmpty());
    }

    @Test
    void configuredDirectoryLimitRejectsOnlyNewDeviceIdentities() {
        QIOAutomationDeviceCatalog catalog = new QIOAutomationDeviceCatalog();
        UUID acceptedUUID = UUID.randomUUID();
        QIOAutomationDeviceLocation acceptedLocation = location(0, 1, 1, 1);
        assertTrue(catalog.observe(snapshot(acceptedUUID, acceptedLocation, 1, 1), 1));

        assertThrows(IllegalStateException.class, () -> catalog.observe(snapshot(
              UUID.randomUUID(), location(0, 2, 2, 2), 2, 1), 1));
        assertTrue(catalog.observe(snapshot(acceptedUUID, acceptedLocation, 3, 2), 1));
        assertEquals(1, catalog.size());
        assertEquals(2, catalog.get(acceptedUUID).getConfigurationRevision());
    }

    @Test
    void damagedDirectoryNbtIsRejected() throws Exception {
        QIOAutomationDeviceCatalog catalog = new QIOAutomationDeviceCatalog();
        UUID deviceUUID = UUID.randomUUID();
        catalog.observe(snapshot(deviceUUID, location(0, 1, 2, 3), 1, 1), 8);

        NBTTagCompound duplicate = catalog.write();
        NBTTagList duplicateDevices = duplicate.getTagList("devices", NBT.TAG_COMPOUND);
        duplicateDevices.appendTag(duplicateDevices.getCompoundTagAt(0).copy());
        assertThrows(QIOProcessingDataException.class,
              () -> QIOAutomationDeviceCatalog.read(duplicate));

        NBTTagCompound negativeRevision = catalog.write();
        negativeRevision.setLong("revision", -1);
        assertThrows(QIOProcessingDataException.class,
              () -> QIOAutomationDeviceCatalog.read(negativeRevision));

        NBTTagCompound invalidMetadata = catalog.write();
        invalidMetadata.getTagList("devices", NBT.TAG_COMPOUND).getCompoundTagAt(0)
              .setInteger("blockMetadata", -1);
        assertThrows(QIOProcessingDataException.class,
              () -> QIOAutomationDeviceCatalog.read(invalidMetadata));

        NBTTagCompound missingProfileScope = catalog.write();
        missingProfileScope.getTagList("devices", NBT.TAG_COMPOUND).getCompoundTagAt(0)
              .removeTag("profileScopeId");
        assertThrows(QIOProcessingDataException.class,
              () -> QIOAutomationDeviceCatalog.read(missingProfileScope));
    }

    @Test
    void processorSnapshotsPreserveLongLaneCountsAndGenericState() throws Exception {
        UUID processorUUID = UUID.randomUUID();
        QIOAutomationDeviceSnapshot processor = new QIOAutomationDeviceSnapshot(
              processorUUID, location(0, 4, 5, 6),
              QIOAutomationDeviceSnapshot.Kind.CRAFTING_PROCESSOR,
              "mekanismqioprocessing:ultimate_qio_crafting_processor", 0,
              "mekanismqioprocessing:ultimate", "mekanismqioprocessing:builtin",
              "ACTIVE", true, 10, 0, 0, Long.MAX_VALUE, 2, true, null);

        QIOAutomationDeviceSnapshot restored =
              QIOAutomationDeviceSnapshot.read(processor.write());

        assertEquals(QIOAutomationDeviceSnapshot.Kind.CRAFTING_PROCESSOR,
              restored.getKind());
        assertEquals(Long.MAX_VALUE, restored.getRouteCount());
        assertEquals("ACTIVE", restored.getStateName());
        assertEquals("mekanismqioprocessing:builtin", restored.getModeName());
        assertNull(restored.getMode());
        assertNull(restored.getState());
        assertTrue(restored.isManagementPaused());
    }

    @Test
    void managementProfileSelectionRoundTripsWithTheDevicePageSnapshot() throws Exception {
        QIOAutomationDeviceSnapshot selected = snapshot(UUID.randomUUID(),
              location(0, 4, 5, 6), 10, 2).withRecipeProfileSelection(
                    false, 7, RouteFilterMode.BLACKLIST);

        QIOAutomationDeviceSnapshot restored =
              QIOAutomationDeviceSnapshot.read(selected.write());

        assertTrue(restored.hasRecipeProfile());
        assertFalse(restored.isIndividualRecipeProfile());
        assertEquals(7, restored.getGlobalRecipeProfileSlot());
        assertEquals(RouteFilterMode.BLACKLIST, restored.getRecipeRouteFilterMode());
    }

    @Test
    void presentationTierRoundTripsAndLegacySnapshotsFallBack() throws Exception {
        NBTTagCompound tier = new NBTTagCompound();
        tier.setInteger("tier", 3);
        QIOAutomationDeviceSnapshot current = snapshotWithPresentation(
              UUID.randomUUID(), location(0, 4, 5, 6), tier);

        QIOAutomationDeviceSnapshot restored =
              QIOAutomationDeviceSnapshot.read(current.write());
        assertEquals(3, restored.getPresentation().getItemNbt().getInteger("tier"));

        NBTTagCompound legacy = current.write();
        legacy.removeTag("presentation");
        QIOAutomationDeviceSnapshot legacyRestored =
              QIOAutomationDeviceSnapshot.read(legacy);
        assertEquals(current.getBlockId(), legacyRestored.getPresentation().getItemId());
        assertEquals(current.getBlockMetadata(),
              legacyRestored.getPresentation().getItemMetadata());
        assertNull(legacyRestored.getPresentation().getItemNbt());
    }

    @Test
    void onlineObservationRefreshesAnExistingLegacyPresentation() {
        QIOAutomationDeviceCatalog catalog = new QIOAutomationDeviceCatalog();
        UUID deviceUUID = UUID.randomUUID();
        QIOAutomationDeviceLocation location = location(0, 4, 5, 6);
        QIOAutomationDeviceSnapshot legacy = snapshot(deviceUUID, location, 10, 2)
              .offline(10);
        assertTrue(catalog.observe(legacy, 8));
        assertFalse(catalog.get(deviceUUID).isOnline());
        long oldRevision = catalog.getRevision();

        NBTTagCompound tier = new NBTTagCompound();
        tier.setInteger("tier", 3);
        QIOAutomationDeviceSnapshot refreshed = snapshotWithPresentation(deviceUUID,
              location, tier);
        assertTrue(catalog.observe(refreshed, 8));

        assertEquals(oldRevision + 1, catalog.getRevision());
        assertEquals(3, catalog.get(deviceUUID).getPresentation().getItemNbt()
              .getInteger("tier"));
    }

    private static QIOAutomationDeviceSnapshot snapshot(UUID deviceUUID,
          QIOAutomationDeviceLocation location, long lastSeenTick, long configurationRevision) {
        return new QIOAutomationDeviceSnapshot(deviceUUID, location,
              "mekanism:machineblock", 0, "mekanism:test_provider",
              QIOAutomationMode.SCHEDULED, QIOAutomationHost.State.ACTIVE, true,
              lastSeenTick, configurationRevision, 1, 2, 0, null);
    }

    private static QIOAutomationDeviceSnapshot snapshotWithPresentation(UUID deviceUUID,
          QIOAutomationDeviceLocation location, NBTTagCompound tag) {
        return new QIOAutomationDeviceSnapshot(deviceUUID, location,
              QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE,
              "mekanism:machineblock", 0,
              MachinePresentationDescriptor.of("minecraft:diamond", 0, tag, "ultimate"),
              "mekanism:test_provider", "mekanism:test_provider",
              QIOAutomationMode.SCHEDULED.name(), QIOAutomationHost.State.ACTIVE.name(),
              true, 10, 2, 1, 2, 0, false, null);
    }

    private static QIOAutomationDeviceLocation location(int dimension, int x, int y, int z) {
        return new QIOAutomationDeviceLocation(dimension, new BlockPos(x, y, z));
    }
}
