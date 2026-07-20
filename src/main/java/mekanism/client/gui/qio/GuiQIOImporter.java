package mekanism.client.gui.qio;

import mekanism.client.gui.element.GuiScreenSwitch;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.ContainerQIOImporter;
import mekanism.common.network.qio.PacketQIOComponentConfig;
import mekanism.common.tile.qio.TileEntityQIOImporter;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiQIOImporter extends GuiQIOFilterHandler<TileEntityQIOImporter, ContainerQIOImporter> {

    public GuiQIOImporter(InventoryPlayer inventory, TileEntityQIOImporter tile) {
        super(tile, new ContainerQIOImporter(inventory, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiScreenSwitch(this, 9, 122, xSize - 18, MekanismLang.QIO_IMPORT_WITHOUT_FILTER.translate(),
              tileEntity::getImportWithoutFilter, (element, mouseX, mouseY) -> {
                  PacketQIOComponentConfig.toggleFilterless(tileEntity);
                  return true;
              }));
    }
}
