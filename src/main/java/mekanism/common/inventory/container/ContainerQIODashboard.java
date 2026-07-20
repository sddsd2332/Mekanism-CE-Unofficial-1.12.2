package mekanism.common.inventory.container;

import mekanism.common.tile.qio.TileEntityQIODashboard;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.util.SecurityUtils;
import mekanism.common.network.qio.PacketQIOComponentConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

/** Dashboard viewer container with a live frequency reference on the server. */
public class ContainerQIODashboard extends QIOItemViewerContainer {

    private final TileEntityQIODashboard tile;

    public ContainerQIODashboard(InventoryPlayer inventory, TileEntityQIODashboard tile) {
        super(inventory, tile == null ? null : tile.getQIOFrequency(), false);
        this.tile = tile;
        if (tile != null) {
            setCraftingWindowHolder(tile);
            tile.addContainerTrackers(this);
        }
        addSlotsAndOpen();
        openViewer();
    }

    public TileEntityQIODashboard getTileEntity() {
        return tile;
    }

    @Override
    public boolean shiftClickIntoFrequency() {
        return tile == null || tile.shiftClickIntoFrequency();
    }

    @Override
    public void toggleTargetDirection() {
        if (tile != null) {
            if (isRemote()) {
                tile.toggleShiftClickIntoFrequency();
            }
            PacketQIOComponentConfig.toggleTargetDirection(tile);
        }
    }

    @Override
    public QIOFrequency getFrequency() {
        if (tile != null && isRemote()) {
            // The tile's frequency tracker is authoritative on the client;
            // keep the viewer snapshot pointed at the same frequency after an
            // in-place selector update.
            frequency = tile.getQIOFrequency();
        } else if (!isRemote() && tile != null) {
            QIOFrequency resolved = tile.getQIOFrequency();
            if (resolved != frequency && inv != null && inv.player instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) inv.player;
                if (frequency != null) {
                    frequency.closeViewer(player);
                }
                frequency = resolved;
                if (frequency != null) {
                    frequency.openViewer(player);
                }
                sendViewerSync(player);
            } else {
                frequency = resolved;
            }
        }
        return frequency;
    }

    @Override
    public void detectAndSendChanges() {
        if (!isRemote()) {
            // Frequency selection now happens inside this container. Rebind
            // the viewer before syncing trackers so the item snapshot and
            // selected frequency change arrive in the same update cycle.
            getFrequency();
        }
        super.detectAndSendChanges();
    }

    @Override
    protected void openInventory(InventoryPlayer inventory) {
        super.openInventory(inventory);
        if (tile != null) {
            tile.open(inventory.player);
        }
    }

    @Override
    protected void closeInventory(EntityPlayer player) {
        if (tile != null) {
            tile.close(player);
        }
        super.closeInventory(player);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile != null && tile.isUsableByPlayer(player) &&
              (player.world.isRemote || SecurityUtils.canAccess(player, tile)) && super.canInteractWith(player);
    }
}
