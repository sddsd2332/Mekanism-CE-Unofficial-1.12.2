package mekanism.client.gui;

import mekanism.client.gui.element.tab.window.GuiSideConfigurationTab;
import mekanism.client.gui.element.tab.window.GuiTransporterConfigTab;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public abstract class GuiConfigurableTile<TILE extends TileEntityContainerBlock & ISideConfiguration, CONTAINER extends Container>
      extends GuiMekanismTile<TILE, CONTAINER> {

    private GuiSideConfigurationTab<TILE> sideConfigTab;
    private GuiTransporterConfigTab<TILE> transporterConfigTab;

    protected GuiConfigurableTile(TILE tile, CONTAINER container) {
        super(tile, container);
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        sideConfigTab = addButton(new GuiSideConfigurationTab<>(this, tileEntity, () -> sideConfigTab));
        transporterConfigTab = addButton(new GuiTransporterConfigTab<>(this, tileEntity, () -> transporterConfigTab));
    }
}
