package mekanism.client.gui.qio;

import mekanism.client.gui.element.GuiScreenSwitch;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.ContainerQIOExporter;
import mekanism.common.network.qio.PacketQIOComponentConfig;
import mekanism.common.tile.qio.TileEntityQIOExporter;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiQIOExporter extends GuiQIOFilterHandler<TileEntityQIOExporter, ContainerQIOExporter> {

    public GuiQIOExporter(InventoryPlayer inventory, TileEntityQIOExporter tile) {
        super(tile, new ContainerQIOExporter(inventory, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        int gap = 1;
        int toggleWidth = (xSize - 18) / 2 - gap;
        addButton(new GuiScreenSwitch(this, 9, 122, toggleWidth, MekanismLang.QIO_EXPORT_WITHOUT_FILTER.translate(),
              tileEntity::getExportWithoutFilter, (element, mouseX, mouseY) -> {
                  PacketQIOComponentConfig.toggleFilterless(tileEntity);
                  return true;
        }));
        addButton(new GuiScreenSwitch(this, 9 + toggleWidth + 2 * gap, 122, toggleWidth,
              MekanismLang.QIO_EXPORTER_ROUND_ROBIN.translate(), tileEntity::getRoundRobin, (element, mouseX, mouseY) -> {
                  PacketQIOComponentConfig.toggleRoundRobin(tileEntity);
                  return true;
              }).tooltip(() -> java.util.Collections.singletonList(MekanismLang.QIO_EXPORTER_ROUND_ROBIN_DESCRIPTION.translate())));
    }
}
