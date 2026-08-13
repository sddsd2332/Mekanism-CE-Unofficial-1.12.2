package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.config.MekanismConfig;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.machine.QIOAutomationDeviceRegistry;
import mekanism.qioprocessing.common.processor.QIOCraftingProcessorDeviceRegistry;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/** Loaded-only machine command service. It never resolves worlds or loads chunks. */
public final class QIODeviceCommandService {

    public enum Command {
        PAUSE,
        RESUME
    }

    private QIODeviceCommandService() {
    }

    @Nonnull
    public static QIODeviceCommandResult execute(@Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nonnull UUID requestId, @Nonnull UUID deviceUUID,
          long expectedDirectoryRevision, long expectedConfigurationRevision,
          @Nonnull Command command, long currentTick) {
        validateSession(session, network, currentAccessRevision);
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(deviceUUID, "deviceUUID");
        Objects.requireNonNull(command, "command");
        if (expectedDirectoryRevision < 0 || expectedConfigurationRevision < 0 ||
            currentTick < 0) throw new IllegalArgumentException("Invalid device command revision");
        QIOAutomationDeviceSnapshot snapshot = network.getAutomationDevices().getDevices().stream()
              .filter(device -> deviceUUID.equals(device.getDeviceUUID())).findFirst().orElse(null);
        if (snapshot == null) return result(requestId, deviceUUID,
              QIODeviceCommandResult.Status.NOT_FOUND, network, 0, false);
        if (snapshot.getKind() == QIOAutomationDeviceSnapshot.Kind.TERMINAL) {
            return result(requestId, deviceUUID, QIODeviceCommandResult.Status.INVALID_STATE,
                  network, snapshot.getConfigurationRevision(), snapshot.isManagementPaused());
        }
        if (network.getAutomationDevices().getRevision() != expectedDirectoryRevision ||
            snapshot.getConfigurationRevision() != expectedConfigurationRevision) {
            return result(requestId, deviceUUID,
                  QIODeviceCommandResult.Status.REVISION_CONFLICT, network,
                  snapshot.getConfigurationRevision(), snapshot.isManagementPaused());
        }
        if (!snapshot.isOnline()) return result(requestId, deviceUUID,
              QIODeviceCommandResult.Status.OFFLINE, network,
              snapshot.getConfigurationRevision(), snapshot.isManagementPaused());
        boolean paused = command == Command.PAUSE;
        long updatedRevision;
        if (snapshot.getKind() == QIOAutomationDeviceSnapshot.Kind.CRAFTING_PROCESSOR) {
            QIOCraftingProcessorDeviceRegistry.LoadedProcessor loaded =
                  QIOCraftingProcessorDeviceRegistry.INSTANCE.findLoaded(deviceUUID,
                        network.getFrequencyUUID());
            if (loaded == null || loaded.dimension() != snapshot.getLocation().dimension() ||
                !loaded.position().equals(snapshot.getLocation().position())) {
                return result(requestId, deviceUUID,
                      QIODeviceCommandResult.Status.IDENTITY_MISMATCH, network,
                      snapshot.getConfigurationRevision(), snapshot.isManagementPaused());
            }
            if (loaded.processor().getManagementConfigurationRevision() !=
                expectedConfigurationRevision) {
                return result(requestId, deviceUUID,
                      QIODeviceCommandResult.Status.REVISION_CONFLICT, network,
                      loaded.processor().getManagementConfigurationRevision(),
                      loaded.processor().isManagementPaused());
            }
            if (loaded.processor().isManagementPaused() == paused) {
                return result(requestId, deviceUUID, QIODeviceCommandResult.Status.UNCHANGED,
                      network, expectedConfigurationRevision, paused);
            }
            if (!loaded.processor().setManagementPaused(paused)) {
                return result(requestId, deviceUUID, QIODeviceCommandResult.Status.INVALID_STATE,
                      network, expectedConfigurationRevision,
                      loaded.processor().isManagementPaused());
            }
            updatedRevision = loaded.processor().getManagementConfigurationRevision();
        } else {
            QIOAutomationDeviceRegistry.LoadedDevice loaded =
                  QIOAutomationDeviceRegistry.INSTANCE.findLoadedDevice(deviceUUID);
            if (loaded == null || !loaded.location().equals(snapshot.getLocation())) {
                return result(requestId, deviceUUID,
                      QIODeviceCommandResult.Status.IDENTITY_MISMATCH, network,
                      snapshot.getConfigurationRevision(), snapshot.isManagementPaused());
            }
            QIOFrequencyReference reference = loaded.host().getFrequencyReference();
            if (reference == null || !network.getFrequencyUUID().equals(
                  reference.getFrequencyUUID())) {
                return result(requestId, deviceUUID,
                      QIODeviceCommandResult.Status.IDENTITY_MISMATCH, network,
                      snapshot.getConfigurationRevision(), snapshot.isManagementPaused());
            }
            if (loaded.host().getConfigurationRevision() != expectedConfigurationRevision) {
                return result(requestId, deviceUUID,
                      QIODeviceCommandResult.Status.REVISION_CONFLICT, network,
                      loaded.host().getConfigurationRevision(), loaded.host().isManagementPaused());
            }
            if (loaded.host().isManagementPaused() == paused) {
                return result(requestId, deviceUUID, QIODeviceCommandResult.Status.UNCHANGED,
                      network, expectedConfigurationRevision, paused);
            }
            if (!loaded.host().setManagementPaused(paused)) {
                return result(requestId, deviceUUID, QIODeviceCommandResult.Status.INVALID_STATE,
                      network, expectedConfigurationRevision, loaded.host().isManagementPaused());
            }
            updatedRevision = loaded.host().getConfigurationRevision();
        }
        network.observeAutomationDevice(snapshot.withManagementState(paused, updatedRevision,
                    currentTick),
              MekanismConfig.current().qioProcessing.deviceRecordsPerFrequency.val());
        return result(requestId, deviceUUID, QIODeviceCommandResult.Status.ACCEPTED,
              network, updatedRevision, paused);
    }

    private static QIODeviceCommandResult result(UUID requestId, UUID deviceUUID,
          QIODeviceCommandResult.Status status, QIOProcessingNetworkData network,
          long configurationRevision, boolean paused) {
        return new QIODeviceCommandResult(requestId, deviceUUID, status,
              network.getAutomationDevices().getRevision(), configurationRevision, paused);
    }

    private static void validateSession(QIOProcessingTerminalSession session,
          QIOProcessingNetworkData network, long currentAccessRevision) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(network, "network");
        if (!session.isOpen()) throw new IllegalStateException("Terminal session is closed");
        if (session.getTerminalType() != QIOProcessingTerminalType.MANAGEMENT) {
            throw new SecurityException("This terminal cannot command devices");
        }
        if (session.getFrequencyUUID() == null ||
            !session.getFrequencyUUID().equals(network.getFrequencyUUID())) {
            throw new SecurityException("Terminal targets another frequency");
        }
        if (session.getAccessRevision() != currentAccessRevision || currentAccessRevision < 0) {
            throw new IllegalStateException("Frequency access changed");
        }
    }
}
