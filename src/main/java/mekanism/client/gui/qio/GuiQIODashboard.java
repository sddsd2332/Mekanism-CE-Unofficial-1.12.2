package mekanism.client.gui.qio;

import mekanism.common.inventory.container.ContainerQIODashboard;
import mekanism.client.gui.element.tab.GuiQIOFrequencyTab;
import mekanism.client.gui.element.tab.GuiSecurityTab;
import mekanism.common.tile.qio.TileEntityQIODashboard;
import mekanism.common.content.qio.QIOFrequency;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Main QIO Dashboard screen. */
@SideOnly(Side.CLIENT)
public class GuiQIODashboard extends GuiQIOViewerScreen<ContainerQIODashboard> {

    private final TileEntityQIODashboard tile;

    public GuiQIODashboard(InventoryPlayer inventory, TileEntityQIODashboard tile) {
        super(new ContainerQIODashboard(inventory, tile));
        this.tile = tile;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        frequencyTab = addButton(new GuiQIOFrequencyTab.Tile(this, tile, () -> frequencyTab));
        addButton(new GuiSecurityTab<>(this, tile));
    }

    @Override
    protected String getViewerTitle() {
        return tile.getName();
    }

    @Override
    protected QIOFrequency getQIOFrequency() {
        return tile.getQIOFrequency();
    }

    @Override
    protected GuiQIOViewerScreen<ContainerQIODashboard> recreate(ContainerQIODashboard container) {
        return new GuiQIODashboard(container);
    }

    private GuiQIODashboard(ContainerQIODashboard container) {
        super(container);
        tile = container.getTileEntity();
    }
}
