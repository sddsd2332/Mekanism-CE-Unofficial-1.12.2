package mekanism.common.inventory.container;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.inventory.container.slot.VirtualInventoryContainerSlot;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class MekanismTileContainer<TILE extends TileEntityContainerBlock> extends MekanismContainer {

    @Nullable
    protected final TILE tile;
    @Nullable
    private VirtualInventoryContainerSlot upgradeSlot;
    @Nullable
    private VirtualInventoryContainerSlot upgradeOutputSlot;

    public MekanismTileContainer(@Nullable TILE tile, InventoryPlayer inv) {
        super(inv);
        this.tile = tile;
        if (tile != null) {
            addContainerTrackers();
            addSlotsAndOpen();
        }
    }

    protected void addContainerTrackers() {
        tile.addContainerTrackers(this);
    }

    @Nullable
    public TILE getTileEntity() {
        return tile;
    }

    @Override
    protected void openInventory(@Nonnull InventoryPlayer inv) {
        super.openInventory(inv);
        if (tile != null) {
            tile.open(inv.player);
            tile.openInventory(inv.player);
        }
    }

    @Override
    protected void closeInventory(@Nonnull EntityPlayer player) {
        super.closeInventory(player);
        if (tile != null) {
            tile.close(player);
            tile.closeInventory(player);
        }
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        return tile != null && tile.isUsableByPlayer(player) && SecurityUtils.canAccess(player, tile);
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        if (this instanceof IEmptyContainer) {
            return;
        }
        addUpgradeSlots();
        if (tile != null && tile.hasInventory()) {
            List<IInventorySlot> inventorySlots = tile.getInventorySlots(null);
            for (IInventorySlot inventorySlot : inventorySlots) {
                Slot containerSlot = inventorySlot.createContainerSlot();
                if (containerSlot != null) {
                    addSlot(containerSlot);
                }
            }
        }
    }

    protected void addUpgradeSlots() {
        if (tile instanceof IUpgradeTile upgradeTile && upgradeTile.supportsUpgrades()) {
            addSlot(upgradeSlot = upgradeTile.getUpgradeSlot().createContainerSlot());
            addSlot(upgradeOutputSlot = upgradeTile.getUpgradeOutputSlot().createContainerSlot());
        }
    }

    @Nullable
    public VirtualInventoryContainerSlot getUpgradeSlot() {
        return upgradeSlot;
    }

    @Nullable
    public VirtualInventoryContainerSlot getUpgradeOutputSlot() {
        return upgradeOutputSlot;
    }
}
