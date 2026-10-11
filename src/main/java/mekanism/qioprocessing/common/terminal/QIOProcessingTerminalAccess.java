package mekanism.qioprocessing.common.terminal;

import mekanism.api.qio.external.QIOFrequencyReference;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Common server-side terminal contract implemented by both legacy TileEntities and core hosts.
 */
public interface QIOProcessingTerminalAccess {
    QIOProcessingTerminalType terminalType();

    QIOFrequencyReference endpointReference();

    long configurationRevision();

    boolean bindCurrentFrequency(UUID playerId);

    World getWorld();
}
