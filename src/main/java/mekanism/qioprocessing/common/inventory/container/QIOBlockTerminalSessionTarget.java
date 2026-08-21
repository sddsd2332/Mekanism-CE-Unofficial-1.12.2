package mekanism.qioprocessing.common.inventory.container;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.qioprocessing.common.terminal.QIOProcessingFrequencyAccess;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** Shared exact target checks for every block-terminal container shape. */
/**
 * QIO 处理模块中的 QIOBlockTerminalSessionTarget 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
final class QIOBlockTerminalSessionTarget {

    private final QIOProcessingTerminal terminal;
    @Nullable
    private final QIOProcessingTerminalSession session;

    QIOBlockTerminalSessionTarget(@Nonnull InventoryPlayer inventory,
          @Nonnull QIOProcessingTerminal terminal, boolean remote) {
        this.terminal = terminal;
        session = remote ? null : createSession(inventory, terminal);
    }

    @Nullable
    QIOProcessingTerminalSession getSession() {
        return session;
    }

    boolean validate(@Nonnull EntityPlayer player) {
        if (session == null || terminal.hasDataError() ||
            terminal.hasIdentityConflict()) {
            return false;
        }
        QIOFrequencyReference reference = terminal.getFrequencyReference();
        long accessRevision = QIOProcessingFrequencyAccess.getAccessibleRevision(reference,
              player.getUniqueID());
        UUID frequencyUUID = accessRevision < 0 || reference == null ? null :
              reference.getFrequencyUUID();
        return session.validate(session.getSessionNonce(), player.getUniqueID(),
              QIOProcessingTerminalSession.TargetKind.BLOCK,
              terminal.getTerminalType(), terminal.getPersistentTerminalUUID(),
              terminal.getConfigurationRevision(), frequencyUUID,
              accessRevision < 0 ? -1 : accessRevision) ==
              QIOProcessingTerminalSession.Validation.ACCEPTED;
    }

    boolean acceptMutation(long expectedRevision, long updatedRevision) {
        if (session == null || session.getTargetRevision() != expectedRevision ||
            terminal.getConfigurationRevision() != updatedRevision) {
            return false;
        }
        QIOFrequencyReference reference = terminal.getFrequencyReference();
        long accessRevision = QIOProcessingFrequencyAccess.getAccessibleRevision(reference,
              session.getPlayerUUID());
        UUID frequencyUUID = accessRevision < 0 || reference == null ? null :
              reference.getFrequencyUUID();
        session.rebind(expectedRevision, updatedRevision, frequencyUUID,
              accessRevision < 0 ? -1 : accessRevision);
        return true;
    }

    void close() {
        if (session != null) {
            session.close();
        }
    }

    private static QIOProcessingTerminalSession createSession(InventoryPlayer inventory,
          QIOProcessingTerminal terminal) {
        QIOFrequencyReference reference = terminal.getFrequencyReference();
        UUID playerUUID = inventory.player.getUniqueID();
        long accessRevision = QIOProcessingFrequencyAccess.getAccessibleRevision(reference,
              playerUUID);
        UUID frequencyUUID = accessRevision < 0 || reference == null ? null :
              reference.getFrequencyUUID();
        return new QIOProcessingTerminalSession(playerUUID,
              QIOProcessingTerminalSession.TargetKind.BLOCK,
              terminal.getTerminalType(), terminal.getPersistentTerminalUUID(),
              terminal.getConfigurationRevision(), frequencyUUID,
              accessRevision < 0 ? -1 : accessRevision);
    }
}
