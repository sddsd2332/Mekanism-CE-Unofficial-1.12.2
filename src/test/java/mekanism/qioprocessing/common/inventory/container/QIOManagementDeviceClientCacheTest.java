package mekanism.qioprocessing.common.inventory.container;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.terminal.QIOManagementDeviceGroupSnapshot;
import mekanism.qioprocessing.common.terminal.QIOPageCursor;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOManagementDeviceClientCacheTest {

    @Test
    void onlyContiguousPagesFromOneSessionAndRevisionAreMerged() {
        UUID nonce = UUID.randomUUID();
        QIOManagementDeviceClientCache cache = new QIOManagementDeviceClientCache();
        QIOAutomationDeviceSnapshot first = snapshot(1);
        QIOAutomationDeviceSnapshot second = snapshot(2);
        QIOAutomationDeviceSnapshot third = snapshot(3);
        QIOPageCursor next = new QIOPageCursor(nonce, 5, 2);

        assertEquals(0, cache.getPageGeneration());
        cache.expectView("machine:type");
        assertTrue(cache.apply(nonce, "machine:type", 5, 0, 3,
              Arrays.asList(first, second), next));
        assertEquals(1, cache.getPageGeneration());
        assertFalse(cache.apply(nonce, "machine:type", 6, 2, 3,
              Collections.singletonList(third), null));
        assertFalse(cache.apply(UUID.randomUUID(), "machine:type", 5, 2, 3,
              Collections.singletonList(third), null));
        assertTrue(cache.apply(nonce, "machine:type", 5, 2, 3,
              Collections.singletonList(third), null));
        assertEquals(2, cache.getPageGeneration());

        assertEquals(3, cache.getDevices().size());
        assertEquals(3, cache.getTotalSize());
        assertNull(cache.getNextCursor());
    }

    @Test
    void stalePageFromPreviouslySelectedMachineTypeIsRejected() {
        UUID nonce = UUID.randomUUID();
        QIOManagementDeviceClientCache cache = new QIOManagementDeviceClientCache();
        cache.expectView("machine:first");
        assertTrue(cache.apply(nonce, "machine:first", 1, 0, 1,
              Collections.singletonList(snapshot(1)), null));

        cache.expectView("machine:second");
        assertTrue(cache.getDevices().isEmpty());
        assertFalse(cache.apply(nonce, "machine:first", 1, 0, 1,
              Collections.singletonList(snapshot(1)), null));
        assertTrue(cache.apply(nonce, "machine:second", 1, 0, 1,
              Collections.singletonList(snapshot(2)), null));
        assertEquals("machine:second", cache.getViewKey());
    }

    @Test
    void machineTypePagesOnlyAppendForTheSameSessionAndRevision() {
        UUID nonce = UUID.randomUUID();
        QIOManagementDeviceGroupClientCache cache =
              new QIOManagementDeviceGroupClientCache();
        QIOManagementDeviceGroupSnapshot first = group("test:first", 1, 2);
        QIOManagementDeviceGroupSnapshot second = group("test:second", 0, 1);
        QIOPageCursor next = new QIOPageCursor(nonce, 4, 1);

        assertTrue(cache.apply(nonce, 4, 0, 2,
              Collections.singletonList(first), next));
        assertFalse(cache.apply(nonce, 5, 1, 2,
              Collections.singletonList(second), null));
        assertTrue(cache.apply(nonce, 4, 1, 2,
              Collections.singletonList(second), null));
        assertEquals(2, cache.getGroups().size());
        assertNull(cache.getNextCursor());
    }

    @Test
    void pageReadinessIsScopedToTheCurrentTerminalSession() {
        UUID firstSession = UUID.randomUUID();
        UUID reopenedSession = UUID.randomUUID();
        QIOManagementDeviceGroupClientCache groups =
              new QIOManagementDeviceGroupClientCache();
        QIOManagementDeviceClientCache devices = new QIOManagementDeviceClientCache();

        assertFalse(groups.hasPageFor(firstSession));
        assertTrue(groups.apply(firstSession, 3, 0, 0,
              Collections.emptyList(), null));
        assertTrue(groups.hasPageFor(firstSession));
        assertFalse(groups.hasPageFor(reopenedSession));

        devices.expectView("machine:type");
        assertFalse(devices.hasPageFor(firstSession, "machine:type"));
        assertTrue(devices.apply(firstSession, "machine:type", 3, 0, 0,
              Collections.emptyList(), null));
        assertTrue(devices.hasPageFor(firstSession, "machine:type"));
        assertFalse(devices.hasPageFor(reopenedSession, "machine:type"));
        assertFalse(devices.hasPageFor(firstSession, "machine:other"));

        groups.clear();
        devices.clear();
        assertFalse(groups.hasPageFor(firstSession));
        assertFalse(devices.hasPageFor(firstSession, "machine:type"));
    }

    @Test
    void delayedOlderFirstPagesCannotOverwriteNewerPagesInTheSameSession() {
        UUID nonce = UUID.randomUUID();
        QIOManagementDeviceGroupClientCache groups =
              new QIOManagementDeviceGroupClientCache();
        QIOManagementDeviceClientCache devices = new QIOManagementDeviceClientCache();

        QIOManagementDeviceGroupSnapshot newestGroup = group("test:newest", 1, 1);
        assertTrue(groups.apply(nonce, 8, 0, 1,
              Collections.singletonList(newestGroup), null));
        assertFalse(groups.apply(nonce, 7, 0, 0, Collections.emptyList(), null));
        assertEquals(Collections.singletonList(newestGroup), groups.getGroups());
        assertEquals(8, groups.getSourceRevision());

        devices.expectView("machine:type");
        QIOAutomationDeviceSnapshot newestDevice = snapshot(8);
        assertTrue(devices.apply(nonce, "machine:type", 8, 0, 1,
              Collections.singletonList(newestDevice), null));
        assertFalse(devices.apply(nonce, "machine:type", 7, 0, 0,
              Collections.emptyList(), null));
        assertEquals(Collections.singletonList(newestDevice), devices.getDevices());
        assertEquals(8, devices.getSourceRevision());
    }

    private static QIOAutomationDeviceSnapshot snapshot(int x) {
        return new QIOAutomationDeviceSnapshot(UUID.randomUUID(),
              new QIOAutomationDeviceLocation(0, new BlockPos(x, 64, 0)),
              "mekanism:machineblock", 0, "mekanism:test",
              QIOAutomationMode.SCHEDULED, QIOAutomationHost.State.ACTIVE,
              true, x, 1, 1, 1, 0, null);
    }

    private static QIOManagementDeviceGroupSnapshot group(String scope,
          int online, int total) {
        return new QIOManagementDeviceGroupSnapshot(
              "AUTOMATION_MACHINE|" + scope,
              QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE,
              "mekanism:machineblock", 0, scope, online, total);
    }
}
