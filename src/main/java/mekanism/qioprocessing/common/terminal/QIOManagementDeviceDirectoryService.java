package mekanism.qioprocessing.common.terminal;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.config.MekanismConfig;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceCatalog;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceDirectoryCleanupService;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileCatalog.Selection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Session-bound paged read service for the management terminal's device directory. */
public final class QIOManagementDeviceDirectoryService {

    private QIOManagementDeviceDirectoryService() {
    }

    @Nonnull
    public static QIOPage<QIOAutomationDeviceSnapshot> getPage(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nullable QIOPageCursor cursor, int requestedPageSize) {
        return getPage(session, network, currentAccessRevision, cursor, requestedPageSize,
              MekanismConfig.current().qioProcessing.terminalPageSize.val());
    }

    @Nonnull
    static QIOPage<QIOAutomationDeviceSnapshot> getPage(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nullable QIOPageCursor cursor, int requestedPageSize, int maximumPageSize) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(network, "network");
        if (!session.isOpen()) {
            throw new IllegalStateException("QIO terminal session is closed");
        }
        if (session.getTerminalType() != QIOProcessingTerminalType.MANAGEMENT) {
            throw new SecurityException("This terminal session cannot read the device directory");
        }
        if (session.getFrequencyUUID() == null ||
            !session.getFrequencyUUID().equals(network.getFrequencyUUID())) {
            throw new SecurityException("QIO terminal session targets another frequency");
        }
        if (currentAccessRevision < 0 ||
            session.getAccessRevision() != currentAccessRevision) {
            throw new IllegalStateException("QIO frequency access changed during the session");
        }
        QIOAutomationDeviceDirectoryCleanupService.pruneLoadedStaleRecords(network);
        QIOAutomationDeviceCatalog catalog = network.getAutomationDevices();
        return withProfileSelections(network, QIOPagination.page(catalog.getDevices(),
              catalog.getRevision(), session.getSessionNonce(), cursor, requestedPageSize,
              maximumPageSize));
    }

    @Nonnull
    public static QIOPage<QIOAutomationDeviceSnapshot> getPageForType(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nonnull String typeKey, @Nullable QIOPageCursor cursor,
          int requestedPageSize) {
        Objects.requireNonNull(typeKey, "typeKey");
        QIOManagementDeviceGroupService.validate(session, network, currentAccessRevision);
        QIOAutomationDeviceDirectoryCleanupService.pruneLoadedStaleRecords(network);
        QIOAutomationDeviceCatalog catalog = network.getAutomationDevices();
        List<QIOAutomationDeviceSnapshot> matches = new ArrayList<>();
        for (QIOAutomationDeviceSnapshot device : catalog.getDevices()) {
            if (typeKey.equals(QIOManagementDeviceGroupSnapshot.typeKey(device))) {
                matches.add(device);
            }
        }
        return withProfileSelections(network, QIOPagination.page(matches,
              catalog.getRevision(), session.getSessionNonce(), cursor, requestedPageSize,
              MekanismConfig.current().qioProcessing.terminalPageSize.val()));
    }

    private static QIOPage<QIOAutomationDeviceSnapshot> withProfileSelections(
          QIOProcessingNetworkData network, QIOPage<QIOAutomationDeviceSnapshot> page) {
        List<QIOAutomationDeviceSnapshot> entries = new ArrayList<>(page.getEntries().size());
        for (QIOAutomationDeviceSnapshot device : page.getEntries()) {
            QIOAutomationMode mode = device.getKind() ==
                  QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE ? device.getMode() : null;
            if (mode == null || mode == QIOAutomationMode.OUTPUT_ONLY) {
                entries.add(device);
                continue;
            }
            Selection selection = network.getAutomationRecipeProfiles().getSelection(
                  device.getDeviceUUID(), mode, device.getProfileScopeId());
            entries.add(device.withRecipeProfileSelection(selection.isIndividual(),
                  selection.getGlobalSlot(), selection.getFilterMode()));
        }
        return new QIOPage<>(page.getSourceRevision(), page.getOffset(), page.getTotalSize(),
              entries, page.getNextCursor());
    }
}
