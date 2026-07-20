package mekanism.client.gui.qio;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.custom.GuiQIODriveStatusSlot;
import mekanism.client.gui.element.custom.GuiQIOFrequencyDataScreen;
import mekanism.client.gui.element.tab.GuiQIOFrequencyTab;
import mekanism.common.inventory.container.ContainerQIODriveArray;
import mekanism.common.tile.qio.TileEntityQIODriveArray;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiQIODriveArray extends GuiMekanismTile<TileEntityQIODriveArray, ContainerQIODriveArray> {

    private GuiQIOFrequencyTab<?> frequencyTab;

    public GuiQIODriveArray(InventoryPlayer inventory, TileEntityQIODriveArray tile) {
        super(tile, new ContainerQIODriveArray(inventory, tile));
        dynamicSlots = true;
        ySize += 40;
        inventoryLabelY = ySize - 94;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        frequencyTab = addButton(new GuiQIOFrequencyTab.Tile(this, tileEntity, () -> frequencyTab));
        addButton(new GuiQIOFrequencyDataScreen(this, 15, 19, xSize - 32, 46, tileEntity::getQIOFrequency));
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 6; x++) {
                int slot = y * 6 + x;
                addButton(new GuiQIODriveStatusSlot(this, xSize / 2 - (6 * 18 / 2) + x * 18 - 1,
                      69 + y * 18, slot, tileEntity::getDriveStatusData));
            }
        }
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}
