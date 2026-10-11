package mekanism.qioprocessing.common.workbench;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * Captures a real open container, never a frequency UUID supplied by the client.
 */
public final class WorkbenchViewerAccess implements WorkbenchSessions.Access {
    private final EntityPlayerMP player;
    private final QIOItemViewerContainer container;
    private final QIOFrequency frequency;
    private final QIOFrequencyReference reference;
    private final int window, dimension;

    public WorkbenchViewerAccess(EntityPlayerMP player, int window) {
        if (!(player.openContainer instanceof QIOItemViewerContainer) || player.openContainer.windowId != window) {
            throw new WorkbenchSessions.Rejected(WorkbenchSessions.Failure.ACCESS_DENIED);
        }
        this.player = player;
        this.window = window;
        this.dimension = player.dimension;
        container = (QIOItemViewerContainer) player.openContainer;
        frequency = container.getFrequency();
        if (frequency == null) throw new WorkbenchSessions.Rejected(WorkbenchSessions.Failure.ACCESS_DENIED);
        reference = QIOFrequencyStorageAccess.INSTANCE.createReference(frequency, player.getUniqueID());
    }

    @Override
    public UUID player() {
        return player.getUniqueID();
    }

    @Override
    public UUID frequency() {
        return reference.getFrequencyUUID();
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public QIOFrequencyReference frequencyReference() {
        return reference;
    }

    @Override
    public boolean valid() {
        return !player.isDead && player.connection != null && player.connection.getNetworkManager().isChannelOpen()
                && player.dimension == dimension && player.openContainer == container && container.windowId == window
                && container.canInteractWith(player) && container.getFrequency() == frequency
                && QIOFrequencyStorageAccess.INSTANCE.canAccess(reference, player());
    }
}
