package mekanism.generators.client.gui;

import mekanism.client.gui.element.GuiRecipeViewerArea;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.util.LangUtils;
import mekanism.generators.common.inventory.container.ContainerGasGenerator;
import mekanism.generators.common.tile.TileEntityGasGenerator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;

public class GuiGasGenerator extends GuiGenerator<TileEntityGasGenerator, ContainerGasGenerator> {

    public GuiGasGenerator(InventoryPlayer inventory, TileEntityGasGenerator tile) {
        super(tile, new ContainerGasGenerator(inventory, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiEnergyTab(this, () -> getEnergyTabText(tileEntity.generationRate * tileEntity.getUsed() * tileEntity.getMaxBurnTicks())));
        addButton(new GuiGasGauge(this, tileEntity.fuelTank, GuiGasGauge.Type.WIDE, 55, 18));
        addButton(new GuiRecipeViewerArea(this, 55, 18, 66, 50, RecipeViewerRecipeType.GAS_FUEL_TO_ENERGY));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15));
    }

    @Override
    protected void renderGeneratorInventoryText() {
        renderInventoryTextAndOther(new TextComponentString(LangUtils.localize("gui.burnRate") + ": " + tileEntity.getUsed()));
    }
}
