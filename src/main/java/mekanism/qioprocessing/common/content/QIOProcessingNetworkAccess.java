package mekanism.qioprocessing.common.content;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalFrequencyContainer;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Resolves the live frequency behind an open terminal and materializes its processing network. */
/**
 * QIO 处理模块中的 QIOProcessingNetworkAccess 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingNetworkAccess {

    private QIOProcessingNetworkAccess() {
    }

    /**
     * Returns the network for the frequency currently visible to the authoritative container.
     * A portable terminal is not a processing-network device, so this also covers its first page
     * request by creating the otherwise absent network record.
     */
    @Nullable
    /** 通过频率引用取得或创建网络，并在访问失败时返回隔离结果。 */
    public static QIOProcessingNetworkData getOrCreate(
          @Nonnull QIOProcessingTerminalFrequencyContainer container,
          @Nonnull QIOProcessingTerminalSession session, @Nonnull EntityPlayer player) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(player, "player");
        UUID expectedUUID = session.getFrequencyUUID();
        if (expectedUUID == null || session.getAccessRevision() < 0) {
            return null;
        }
        QIOFrequency frequency = container.getTerminalFrequency();
        if (frequency == null || !expectedUUID.equals(frequency.getFrequencyUUID()) ||
            !frequency.isValid() || frequency.isRemoved() ||
            frequency.getAccessRevision() != session.getAccessRevision()) {
            return null;
        }
        QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(
              frequency, player.getUniqueID());
        if (!QIOFrequencyStorageAccess.INSTANCE.canAccess(reference,
              player.getUniqueID())) {
            return null;
        }
        try {
            return QIOProcessingNetworkManager.INSTANCE.getOrCreate(expectedUUID,
                  new QIOFrequencyIdentitySnapshot(reference.getFrequencyName(),
                        reference.getOwnerUUID(), reference.getSecurityMode()));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            return null;
        }
    }
}
