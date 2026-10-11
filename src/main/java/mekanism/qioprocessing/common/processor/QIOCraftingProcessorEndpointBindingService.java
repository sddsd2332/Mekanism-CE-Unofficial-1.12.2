package mekanism.qioprocessing.common.processor;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-thread authorization boundary for binding a Processor to its real QIO frequency.
 */
public final class QIOCraftingProcessorEndpointBindingService {
    private QIOCraftingProcessorEndpointBindingService() {
    }

    public static boolean bind(QIOCraftingProcessorTile tile, EntityPlayer player) {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(player, "player");
        if (player.world == null || player.world.isRemote || tile.getWorld() != player.world
                || !tile.isUsableByPlayer(player) || !SecurityUtils.canAccess(player, tile)) {
            return false;
        }
        QIOFrequency frequency = tile.qioFrequency();
        if (frequency == null || !frequency.isValid() || frequency.isRemoved()
                || !SecurityUtils.canAccess(frequency.getSecurity(), player.getUniqueID(), frequency.getOwner())) {
            return false;
        }
        QIOFrequencyReference reference = QIOFrequencyStorageAccess.INSTANCE.createReference(frequency,
                player.getUniqueID());
        if (!QIOFrequencyStorageAccess.INSTANCE.canAccess(reference, player.getUniqueID())) return false;
        tile.bindEndpoint(reference, player.getUniqueID());
        return true;
    }

    public static boolean unbind(QIOCraftingProcessorTile tile, EntityPlayer player) {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(player, "player");
        QIOFrequencyReference reference = tile.endpointReference();
        UUID playerId = player.getUniqueID();
        if (player.world == null || player.world.isRemote || tile.getWorld() != player.world
                || !tile.isUsableByPlayer(player) || !SecurityUtils.canAccess(player, tile)
                || reference == null || !playerId.equals(reference.getBindingPlayerUUID())) {
            return false;
        }
        tile.unbindEndpoint(reference, playerId);
        return true;
    }
}

