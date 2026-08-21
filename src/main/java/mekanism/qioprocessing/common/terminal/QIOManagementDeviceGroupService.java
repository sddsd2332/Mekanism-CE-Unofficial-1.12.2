package mekanism.qioprocessing.common.terminal;

import mekanism.common.config.MekanismConfig;
import mekanism.api.processing.MachinePresentationDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceDirectoryCleanupService;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Session-bound machine-type aggregation for the management terminal. */
/**
 * QIO 处理模块中的 QIOManagementDeviceGroupService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOManagementDeviceGroupService {

    private QIOManagementDeviceGroupService() {
    }

    @Nonnull
    public static QIOPage<QIOManagementDeviceGroupSnapshot> getPage(
          @Nonnull QIOProcessingTerminalSession session,
          @Nonnull QIOProcessingNetworkData network, long currentAccessRevision,
          @Nullable QIOPageCursor cursor, int requestedPageSize) {
        validate(session, network, currentAccessRevision);
        QIOAutomationDeviceDirectoryCleanupService.pruneLoadedStaleRecords(network);
        Map<String, Builder> grouped = new LinkedHashMap<>();
        for (QIOAutomationDeviceSnapshot device : network.getAutomationDevices().getDevices()) {
            String typeKey = QIOManagementDeviceGroupSnapshot.typeKey(device);
            grouped.computeIfAbsent(typeKey, ignored -> new Builder(typeKey, device))
                  .add(device);
        }
        List<QIOManagementDeviceGroupSnapshot> groups = new ArrayList<>(grouped.size());
        grouped.values().forEach(builder -> groups.add(builder.build()));
        groups.sort(Comparator.comparing(QIOManagementDeviceGroupSnapshot::getTypeKey));
        return QIOPagination.page(groups, network.getAutomationDevices().getRevision(),
              session.getSessionNonce(), cursor, requestedPageSize,
              MekanismConfig.current().qioProcessing.terminalPageSize.val());
    }

    static void validate(QIOProcessingTerminalSession session,
          QIOProcessingNetworkData network, long currentAccessRevision) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(network, "network");
        if (!session.isOpen() ||
            session.getTerminalType() != QIOProcessingTerminalType.MANAGEMENT ||
            session.getFrequencyUUID() == null ||
            !session.getFrequencyUUID().equals(network.getFrequencyUUID())) {
            throw new SecurityException("Invalid QIO management device group session");
        }
        if (currentAccessRevision < 0 ||
            session.getAccessRevision() != currentAccessRevision) {
            throw new IllegalStateException("QIO frequency access changed during the session");
        }
    }

    private static final class Builder {

        private final String typeKey;
        private final QIOAutomationDeviceSnapshot.Kind kind;
        private final String blockId;
        private final int blockMetadata;
        private final MachinePresentationDescriptor presentation;
        private final String profileScopeId;
        private int onlineCount;
        private int totalCount;

        private Builder(String typeKey, QIOAutomationDeviceSnapshot first) {
            this.typeKey = typeKey;
            kind = first.getKind();
            blockId = first.getBlockId();
            blockMetadata = first.getBlockMetadata();
            presentation = first.getPresentation();
            profileScopeId = kind == QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE ?
                  first.getProfileScopeId() : "";
        }

        private void add(QIOAutomationDeviceSnapshot device) {
            totalCount++;
            if (device.isOnline()) onlineCount++;
        }

        private QIOManagementDeviceGroupSnapshot build() {
            return new QIOManagementDeviceGroupSnapshot(typeKey, kind, blockId,
                  blockMetadata, presentation, profileScopeId, onlineCount, totalCount);
        }
    }
}
