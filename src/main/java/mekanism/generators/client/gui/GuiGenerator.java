package mekanism.generators.client.gui;

import mekanism.client.SpecialColors;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.GuiSideHolder;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.common.tile.TileEntityGenerator;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;
import java.util.List;

public abstract class GuiGenerator<TILE extends TileEntityGenerator, CONTAINER extends MekanismTileContainer<TILE>> extends GuiMekanismTile<TILE, CONTAINER> {

    protected GuiGenerator(TILE tile, CONTAINER container) {
        super(tile, container);
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        addButton(GuiSideHolder.create(this, -26, 6, 98, true, true, SpecialColors.TAB_ARMOR_SLOTS));
        super.addGuiElements();
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), titleY());
        renderGeneratorInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    protected void renderGeneratorInventoryText() {
        drawString(new TextComponentString(LangUtils.localize("container.inventory")), inventoryTextX(), ySize - 96 + 2, 0x404040);
    }

    protected int titleY() {
        return 6;
    }

    protected int inventoryTextX() {
        return 8;
    }

    protected List<ITextComponent> getEnergyTabText(double production) {
        return Arrays.asList(
              new TextComponentString(LangUtils.localize("gui.producing") + ": " + MekanismUtils.getEnergyDisplay(production) + "/t"),
              new TextComponentString(LangUtils.localize("gui.maxOutput") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getMaxOutput()) + "/t")
        );
    }

    protected static ITextComponent text(String text) {
        return new TextComponentString(text);
    }

    protected static ITextComponent energy(double energy) {
        return text(MekanismUtils.getEnergyDisplay(energy));
    }

    protected static ITextComponent energy(double energy, double maxEnergy) {
        return text(MekanismUtils.getEnergyDisplay(energy, maxEnergy));
    }
}
