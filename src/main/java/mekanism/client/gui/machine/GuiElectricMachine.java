package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.inventory.container.ContainerElectricMachine;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.machines.BasicMachineRecipe;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiElectricMachine<RECIPE extends BasicMachineRecipe<RECIPE>, TILE extends TileEntityElectricMachine<RECIPE>>
      extends GuiConfigurableTile<TILE, ContainerElectricMachine<RECIPE>> {

    public GuiElectricMachine(InventoryPlayer inventory, TILE tile) {
        super(tile, new ContainerElectricMachine<>(inventory, tile));
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiUpArrow(this, 68, 38));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 16))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY));
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getActive));
        addButton(new GuiProgress(tileEntity::getScaledProgress, ProgressType.BAR, this, 86, 38))
              .recipeViewerCategories(getRecipeViewerRecipeTypes())
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
    }

    protected IRecipeViewerRecipeType<?>[] getRecipeViewerRecipeTypes() {
        return new IRecipeViewerRecipeType[0];
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }
}