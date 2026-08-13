package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.util.SecurityUtils;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Server-authoritative exact frequency binding entry point for terminal blocks. */
public final class QIOProcessingTerminalBindingService {

    private QIOProcessingTerminalBindingService() {
    }

    public static boolean bind(@Nonnull QIOProcessingTerminal terminal,
          @Nonnull QIOFrequency frequency, @Nonnull EntityPlayer player) {
        Objects.requireNonNull(terminal, "terminal");
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(player, "player");
        if (player.world == null || player.world.isRemote || terminal.getWorld() == null ||
            terminal.getWorld().isRemote || terminal.getWorld() != player.world ||
            terminal.hasDataError() || terminal.hasIdentityConflict() ||
            !terminal.isUsableByPlayer(player) ||
            !SecurityUtils.canAccess(player, terminal) ||
            !SecurityUtils.canAccess(frequency.getSecurity(), player.getUniqueID(),
                  frequency.getOwner())) {
            return false;
        }
        QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(
              frequency, player.getUniqueID());
        return QIOFrequencyStorageAccess.INSTANCE.canAccess(reference,
              player.getUniqueID()) && terminal.applyAuthorizedBinding(frequency,
              player.getUniqueID());
    }

    public static boolean unbind(@Nonnull QIOProcessingTerminal terminal,
          @Nonnull EntityPlayer player) {
        Objects.requireNonNull(terminal, "terminal");
        Objects.requireNonNull(player, "player");
        return player.world != null && !player.world.isRemote && terminal.getWorld() == player.world &&
              !terminal.hasDataError() && !terminal.hasIdentityConflict() &&
              terminal.isUsableByPlayer(player) &&
              SecurityUtils.canAccess(player, terminal) &&
              terminal.applyAuthorizedBinding(null, player.getUniqueID());
    }
}
