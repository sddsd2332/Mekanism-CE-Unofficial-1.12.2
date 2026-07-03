package mekanism.client.gui.machine;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.ContainerSeismicVibrator;
import mekanism.common.tile.machine.TileEntitySeismicVibrator;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiSeismicVibrator extends GuiMekanismTile<TileEntitySeismicVibrator, ContainerSeismicVibrator> {

    public GuiSeismicVibrator(InventoryPlayer inventory, TileEntitySeismicVibrator tile) {
        super(tile, new ContainerSeismicVibrator(inventory, tile));
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 16, 23, 112, 40, this::getScreenText));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15))
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> tileEntity.getEnergy() < MekanismConfig.current().usage.seismicVibrator.val() || tileEntity.getEnergy() == 0);
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getActive));
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> list = new ArrayList<>();
        list.add(new TextComponentString(tileEntity.isActive ? LangUtils.localize("gui.vibrating") : LangUtils.localize("gui.idle")));
        list.add(new TextComponentString(LangUtils.localize("gui.chunk") + ": " + (tileEntity.getPos().getX() >> 4) + ", " + (tileEntity.getPos().getZ() >> 4)));
        return list;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}