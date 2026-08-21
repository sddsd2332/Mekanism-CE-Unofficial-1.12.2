package mekanism.client.gui;

import mekanism.client.gui.element.tab.GuiRedstoneControlTab;
import mekanism.client.gui.element.tab.GuiSecurityTab;
import mekanism.client.gui.element.tab.window.GuiUpgradeWindowTab;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.IRedstoneControl.RedstoneControl;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.IUpgradeTile;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.machine.QIOAutomationCapabilities;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

@SideOnly(Side.CLIENT)
public abstract class GuiMekanismTile<TILE extends TileEntityContainerBlock, CONTAINER extends Container> extends GuiMekanism<CONTAINER> {

    protected final TILE tileEntity;
    @Nullable
    private GuiUpgradeWindowTab upgradeWindowTab;

    protected GuiMekanismTile(TILE tile, CONTAINER container) {
        super(container);
        this.tileEntity = tile;
    }

    public TILE getTileEntity() {
        return tileEntity;
    }

    public Container getContainer() {
        return inventorySlots;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addGenericTabs();
    }

    protected void addGenericTabs() {
        addQIOAutomationWarning();
        if (tileEntity instanceof IUpgradeTile upgradeTile && upgradeTile.supportsUpgrades()) {
            upgradeWindowTab = addButton(new GuiUpgradeWindowTab(this, tileEntity, () -> upgradeWindowTab));
        }
        if (tileEntity instanceof IRedstoneControl) {
            addRedstoneControlTab((TileEntity & IRedstoneControl) tileEntity);
        }
        if (tileEntity instanceof ISecurityTile) {
            addSecurityTab();
        }
        for (mekanism.client.gui.element.Widget extension :
              MekanismTileGuiExtensionRegistry.createElements(this, tileEntity)) {
            addButton(extension);
        }
    }

    private void addQIOAutomationWarning() {
        if (!tileEntity.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) {
            return;
        }
        trackWarning(mekanism.client.gui.warning.WarningTracker.WarningType.QIO_AUTOMATION_ERROR, () -> {
            QIOAutomationHost host = tileEntity.getCapability(
                  QIOAutomationCapabilities.AUTOMATION_HOST, null);
            return host != null && (host.getState() == QIOAutomationHost.State.IDENTITY_CONFLICT ||
                  host.hasRecoveryPending() || host.getRecoveryDiagnostic() != null);
        });
    }

    protected <REDSTONE_TILE extends TileEntity & IRedstoneControl> void addRedstoneControlTab(REDSTONE_TILE tile) {
        addButton(new GuiRedstoneControlTab<>(this, tile)
              .warning(WarningType.REDSTONE_SIGNAL_ABSENT,
                    () -> tile.getControlType() == RedstoneControl.HIGH && !MekanismUtils.canFunction(tile))
              .warning(WarningType.REDSTONE_SIGNAL_PRESENT,
                    () -> tile.getControlType() == RedstoneControl.LOW && !MekanismUtils.canFunction(tile))
              .warning(WarningType.REDSTONE_PULSE_REQUIRED,
                    () -> tile.getControlType() == RedstoneControl.PULSE && !MekanismUtils.canFunction(tile)));
    }

    protected void addSecurityTab() {
        if (tileEntity instanceof ISecurityTile) {
            addSecurityTab((TileEntity & ISecurityTile) tileEntity);
        }
    }

    protected <SECURITY_TILE extends TileEntity & ISecurityTile> void addSecurityTab(SECURITY_TILE tile) {
        addButton(new GuiSecurityTab<>(this, tile));
    }

    protected <SECURITY_TILE extends TileEntity & ISecurityTile> void addSecurityTab(SECURITY_TILE tile, int y) {
        addButton(new GuiSecurityTab<>(this, tile, y));
    }

    @Nullable
    @Override
    protected DataType findDataType(InventoryContainerSlot slot) {
        return getFromSlot(slot);
    }

    @Nullable
    private DataType getFromSlot(InventoryContainerSlot slot) {
        if (!(tileEntity instanceof ISideConfiguration configuration) || configuration.getConfig() == null) {
            return null;
        }
        return configuration.getActiveDataType(slot.getInventorySlot());
    }
}
