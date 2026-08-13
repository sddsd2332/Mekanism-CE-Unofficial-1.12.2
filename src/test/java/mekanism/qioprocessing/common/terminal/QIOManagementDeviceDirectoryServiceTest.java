package mekanism.qioprocessing.common.terminal;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile.RouteFilterMode;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOManagementDeviceDirectoryServiceTest {

    @Test
    void devicePagesUseTheSessionAndCatalogRevisions() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequencyUUID);
        network.observeAutomationDevice(snapshot(UUID.randomUUID(), 1), 8);
        network.observeAutomationDevice(snapshot(UUID.randomUUID(), 2), 8);
        network.observeAutomationDevice(snapshot(UUID.randomUUID(), 3), 8);
        QIOProcessingTerminalSession session = session(frequencyUUID,
              QIOProcessingTerminalType.MANAGEMENT, 5);

        QIOPage<QIOAutomationDeviceSnapshot> first =
              QIOManagementDeviceDirectoryService.getPage(session, network, 5,
                    null, 2, 2);
        assertEquals(2, first.getEntries().size());
        assertEquals(3, first.getTotalSize());
        QIOPage<QIOAutomationDeviceSnapshot> second =
              QIOManagementDeviceDirectoryService.getPage(session, network, 5,
                    first.getNextCursor(), 2, 2);
        assertEquals(1, second.getEntries().size());

        QIOPageCursor stale = first.getNextCursor();
        network.observeAutomationDevice(snapshot(UUID.randomUUID(), 4), 8);
        assertThrows(IllegalStateException.class, () ->
              QIOManagementDeviceDirectoryService.getPage(session, network, 5,
                    stale, 2, 2));
    }

    @Test
    void wrongTerminalFrequencyOrAccessRevisionCannotReadDeviceLocations() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequencyUUID);
        network.observeAutomationDevice(snapshot(UUID.randomUUID(), 1), 8);

        assertThrows(SecurityException.class, () ->
              QIOManagementDeviceDirectoryService.getPage(session(frequencyUUID,
                    QIOProcessingTerminalType.MAINTENANCE, 3), network, 3,
                    null, 1, 2));
        assertThrows(SecurityException.class, () ->
              QIOManagementDeviceDirectoryService.getPage(session(UUID.randomUUID(),
                    QIOProcessingTerminalType.MANAGEMENT, 3), network, 3,
                    null, 1, 2));
        assertThrows(IllegalStateException.class, () ->
              QIOManagementDeviceDirectoryService.getPage(session(frequencyUUID,
                    QIOProcessingTerminalType.MANAGEMENT, 3), network, 4,
                    null, 1, 2));
    }

    @Test
    void devicePagesExposeCurrentFrequencyProfileSelection() {
        UUID frequencyUUID = UUID.randomUUID();
        UUID deviceUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequencyUUID);
        network.observeAutomationDevice(snapshot(deviceUUID, 1), 8);
        network.getAutomationRecipeProfiles().cycleGlobalSlot(deviceUUID,
              QIOAutomationMode.SCHEDULED, "mekanism:test_provider", 1);
        network.getAutomationRecipeProfiles().toggleRouteFilterMode(deviceUUID,
              QIOAutomationMode.SCHEDULED, "mekanism:test_provider");
        QIOProcessingTerminalSession session = session(frequencyUUID,
              QIOProcessingTerminalType.MANAGEMENT, 5);

        QIOAutomationDeviceSnapshot global = QIOManagementDeviceDirectoryService
              .getPage(session, network, 5, null, 1, 2).getEntries().get(0);
        assertTrue(global.hasRecipeProfile());
        assertFalse(global.isIndividualRecipeProfile());
        assertEquals(2, global.getGlobalRecipeProfileSlot());
        assertEquals(RouteFilterMode.WHITELIST, global.getRecipeRouteFilterMode());

        network.getAutomationRecipeProfiles().toggleProfileMode(deviceUUID,
              QIOAutomationMode.SCHEDULED, "mekanism:test_provider");
        QIOAutomationDeviceSnapshot individual = QIOManagementDeviceDirectoryService
              .getPage(session, network, 5, null, 1, 2).getEntries().get(0);
        assertTrue(individual.isIndividualRecipeProfile());
        assertEquals(RouteFilterMode.WHITELIST, individual.getRecipeRouteFilterMode());
    }

    private static QIOProcessingNetworkData network(UUID frequencyUUID) {
        return new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("management", null,
                    SecurityMode.PUBLIC));
    }

    private static QIOProcessingTerminalSession session(UUID frequencyUUID,
          QIOProcessingTerminalType type, long accessRevision) {
        return new QIOProcessingTerminalSession(UUID.randomUUID(),
              QIOProcessingTerminalSession.TargetKind.BLOCK, type,
              UUID.randomUUID(), 0, frequencyUUID, accessRevision);
    }

    private static QIOAutomationDeviceSnapshot snapshot(UUID deviceUUID, int x) {
        return new QIOAutomationDeviceSnapshot(deviceUUID,
              new QIOAutomationDeviceLocation(0, new BlockPos(x, 64, 0)),
              "mekanism:machineblock", 0, "mekanism:test_provider",
              QIOAutomationMode.SCHEDULED, QIOAutomationHost.State.ACTIVE,
              true, x, 1, 1, 1, 0, null);
    }
}
