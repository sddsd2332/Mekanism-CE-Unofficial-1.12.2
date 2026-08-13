package mekanism.qioprocessing.common.terminal;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.content.policy.QIOPolicyCatalog;
import mekanism.qioprocessing.common.content.policy.QIOPolicyEntrySnapshot;
import mekanism.qioprocessing.common.content.policy.QIOPolicyMutation;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.MutationResult;
import mekanism.qioprocessing.common.terminal.QIOManagementPolicyService.MutationStatus;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOManagementPolicyServiceTest {

    @Test
    void expectedRevisionPreventsLostUpdatesAndReturnsTheAuthoritativeTarget() {
        UUID frequencyUUID = UUID.randomUUID();
        UUID deviceUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequencyUUID);
        network.observeAutomationDevice(machine(deviceUUID), 8);
        QIOProcessingTerminalSession session = session(frequencyUUID,
              QIOProcessingTerminalType.MANAGEMENT, 5);

        MutationResult applied = QIOManagementPolicyService.mutate(session, network, 5,
              0, QIOPolicyMutation.deviceRoute(deviceUUID, "test:provider", "route",
                    "recipe", QIOPolicyCatalog.Toggle.DISABLED, 7L));
        assertEquals(MutationStatus.APPLIED, applied.getStatus());
        assertEquals(1, applied.getPolicyRevision());
        assertFalse(applied.getAuthoritativeEntry().isEffectiveEnabled());

        MutationResult conflict = QIOManagementPolicyService.mutate(session, network, 5,
              0, QIOPolicyMutation.deviceRoute(deviceUUID, "test:provider", "route",
                    "recipe", QIOPolicyCatalog.Toggle.ENABLED, 9L));
        assertEquals(MutationStatus.REVISION_CONFLICT, conflict.getStatus());
        assertEquals(1, conflict.getPolicyRevision());
        assertEquals(QIOPolicyCatalog.Toggle.DISABLED,
              conflict.getAuthoritativeEntry().getConfiguredToggle());
        assertEquals(7, conflict.getAuthoritativeEntry().getConfiguredRoutePriority());
    }

    @Test
    void forgedAndTerminalDeviceTargetsCannotAllocateMachinePolicies() {
        UUID frequencyUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequencyUUID);
        QIOProcessingTerminalSession session = session(frequencyUUID,
              QIOProcessingTerminalType.MANAGEMENT, 2);

        MutationResult unknown = QIOManagementPolicyService.mutate(session, network, 2,
              0, QIOPolicyMutation.deviceDefault(UUID.randomUUID(),
                    QIOPolicyCatalog.Toggle.DISABLED, 0, 0));
        assertEquals(MutationStatus.INVALID_TARGET, unknown.getStatus());
        assertEquals(0, network.getPolicies().getRevision());

        UUID terminalUUID = UUID.randomUUID();
        network.observeAutomationDevice(terminal(terminalUUID), 8);
        MutationResult terminal = QIOManagementPolicyService.mutate(session, network, 2,
              0, QIOPolicyMutation.deviceDefault(terminalUUID,
                    QIOPolicyCatalog.Toggle.DISABLED, 0, 0));
        assertEquals(MutationStatus.INVALID_TARGET, terminal.getStatus());
        assertEquals(0, network.getPolicies().getRevision());
    }

    @Test
    void policyPagesAreSessionBoundAndRejectStaleCursors() {
        UUID frequencyUUID = UUID.randomUUID();
        UUID deviceUUID = UUID.randomUUID();
        QIOProcessingNetworkData network = network(frequencyUUID);
        network.observeAutomationDevice(machine(deviceUUID), 8);
        network.setDeviceDefaultPolicy(deviceUUID, QIOPolicyCatalog.Toggle.ENABLED, 3, 4);
        QIOProcessingTerminalSession session = session(frequencyUUID,
              QIOProcessingTerminalType.MANAGEMENT, 7);

        QIOPage<QIOPolicyEntrySnapshot> first = QIOManagementPolicyService.getPage(
              session, network, 7, null, 1, 1);
        assertEquals(1, first.getEntries().size());
        assertEquals(2, first.getTotalSize());
        assertEquals(QIOPolicyEntrySnapshot.Kind.GLOBAL_DEFAULT,
              first.getEntries().get(0).getKind());

        network.setGlobalDefaultPolicy(QIOPolicyCatalog.Toggle.DISABLED, 1);
        assertThrows(IllegalStateException.class, () ->
              QIOManagementPolicyService.getPage(session, network, 7,
                    first.getNextCursor(), 1, 1));
        assertThrows(SecurityException.class, () ->
              QIOManagementPolicyService.getPage(session(UUID.randomUUID(),
                    QIOProcessingTerminalType.MANAGEMENT, 7), network, 7,
                    null, 1, 1));
        assertThrows(SecurityException.class, () ->
              QIOManagementPolicyService.getPage(session(frequencyUUID,
                    QIOProcessingTerminalType.MAINTENANCE, 7), network, 7,
                    null, 1, 1));
    }

    private static QIOProcessingNetworkData network(UUID frequencyUUID) {
        return new QIOProcessingNetworkData(frequencyUUID,
              new QIOFrequencyIdentitySnapshot("policy", null, SecurityMode.PUBLIC));
    }

    private static QIOProcessingTerminalSession session(UUID frequencyUUID,
          QIOProcessingTerminalType type, long accessRevision) {
        return new QIOProcessingTerminalSession(UUID.randomUUID(),
              QIOProcessingTerminalSession.TargetKind.BLOCK, type,
              UUID.randomUUID(), 0, frequencyUUID, accessRevision);
    }

    private static QIOAutomationDeviceSnapshot machine(UUID deviceUUID) {
        return new QIOAutomationDeviceSnapshot(deviceUUID,
              new QIOAutomationDeviceLocation(0, new BlockPos(1, 64, 0)),
              "mekanism:machineblock", 0, "test:provider",
              QIOAutomationMode.SCHEDULED, QIOAutomationHost.State.ACTIVE,
              true, 1, 1, 1, 1, 0, null);
    }

    private static QIOAutomationDeviceSnapshot terminal(UUID deviceUUID) {
        return new QIOAutomationDeviceSnapshot(deviceUUID,
              new QIOAutomationDeviceLocation(0, new BlockPos(2, 64, 0)),
              QIOAutomationDeviceSnapshot.Kind.TERMINAL,
              "mekanismqioprocessing:qio_management_terminal", 0,
              "mekanismqioprocessing:management", "MANAGEMENT", "ONLINE",
              true, 1, 1, 0, 0, 0, null);
    }
}
