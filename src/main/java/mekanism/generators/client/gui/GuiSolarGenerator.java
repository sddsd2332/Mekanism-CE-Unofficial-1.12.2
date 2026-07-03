package mekanism.generators.client.gui;

import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.client.gui.element.GuiStateTexture;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.inventory.container.ContainerSolarGenerator;
import mekanism.generators.common.tile.TileEntitySolarGenerator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import java.util.Arrays;
import java.util.List;

public class GuiSolarGenerator extends GuiGenerator<TileEntitySolarGenerator, ContainerSolarGenerator> {

    public GuiSolarGenerator(InventoryPlayer inventory, TileEntitySolarGenerator tile) {
        super(tile, new ContainerSolarGenerator(inventory, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiInnerScreen(this, 40, 23, 96, 40, this::getScreenText));
        addButton(new GuiEnergyTab(this, () -> getEnergyTabText(tileEntity.getProduction())));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15));
        addButton(new GuiStateTexture(this, 18, 35, tileEntity::canSeeSun,
              new ResourceLocation(MekanismGenerators.MODID, "gui/sees_sun.png"),
              new ResourceLocation(MekanismGenerators.MODID, "gui/no_sun.png")));
    }

    private List<ITextComponent> getScreenText() {
        return Arrays.asList(
              energy(tileEntity.getEnergy(), tileEntity.getMaxEnergy()),
              text(LangUtils.localize("gui.producing") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getProduction()) + "/t"),
              text(LangUtils.localize("gui.out") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getMaxOutput()) + "/t")
        );
    }
}
