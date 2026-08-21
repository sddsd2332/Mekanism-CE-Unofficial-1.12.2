package mekanism.common.inventory.container;

import mekanism.api.inventory.IInventorySlot;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.inventory.container.slot.VirtualInventoryContainerSlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MekanismTileContainer<TILE extends TileEntityContainerBlock> extends MekanismContainer {

    @Nullable
    protected final TILE tile;
    @Nullable
    private VirtualInventoryContainerSlot upgradeSlot;
    @Nullable
    private VirtualInventoryContainerSlot upgradeOutputSlot;
    private final Map<ResourceLocation, Object> extensions = new LinkedHashMap<>();

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
        MekanismTileContainerExtensionRegistry.attach(this, tile);
    }

    void addExtension(@Nonnull ResourceLocation id, @Nonnull Object extension) {
        if (extensions.putIfAbsent(Objects.requireNonNull(id, "Extension id cannot be null"),
              Objects.requireNonNull(extension, "Extension cannot be null")) != null) {
            throw new IllegalArgumentException("Duplicate container extension " + id);
        }
    }

    @Nullable
    public <EXTENSION> EXTENSION getExtension(@Nonnull ResourceLocation id,
          @Nonnull Class<EXTENSION> type) {
        Object extension = extensions.get(Objects.requireNonNull(id, "Extension id cannot be null"));
        return type.isInstance(extension) ? type.cast(extension) : null;
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
        // GuiContainer calls this on the client immediately after the screen
        // is constructed. Security data for a tile can arrive one packet
        // later, so only enforce it on the authoritative server; all server
        // packet handlers still call this method and therefore retain the
        // permission check.
        return tile != null && tile.isUsableByPlayer(player) &&
              (player.world.isRemote || SecurityUtils.canAccess(player, tile));
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
                    if (containerSlot instanceof InventoryContainerSlot inventoryContainerSlot) {
                        inventoryContainerSlot.setExtractionGuard(() ->
                              tile.isContainerExtractionGuarded(inventorySlot));
                    }
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
