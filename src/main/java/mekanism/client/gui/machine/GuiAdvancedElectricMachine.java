package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiGasBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.inventory.container.ContainerAdvancedElectricMachine;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiAdvancedElectricMachine<RECIPE extends AdvancedMachineRecipe<RECIPE>, TILE extends TileEntityAdvancedElectricMachine<RECIPE>>
      extends GuiConfigurableTile<TILE, ContainerAdvancedElectricMachine<RECIPE>> {

    public GuiAdvancedElectricMachine(InventoryPlayer inventory, TILE tile) {
        super(tile, new ContainerAdvancedElectricMachine<>(inventory, tile));
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 16))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY));
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getActive));
        addButton(new GuiProgress(tileEntity::getScaledProgress, ProgressType.BAR, this, 86, 38))
              .recipeViewerCategories(getRecipeViewerRecipeTypes())
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
        addButton(new GuiGasBar(this, tileEntity.gasTank, 68, 36, 6, 12, true, this::getGasBarTooltip))
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_SECONDARY_INPUT));
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

    protected List<String> getGasBarTooltip() {
        List<String> tooltip = new ArrayList<>();
        GasStackWrapper stack = new GasStackWrapper(tileEntity.gasTank.getGas(), tileEntity.gasTank.getStored());
        if (stack.gasStack == null) {
            tooltip.add(LangUtils.localize("gui.none"));
        } else {
            tooltip.add(stack.gasStack.getGas().getLocalizedName() + ": " +
                  (stack.stored == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : stack.stored));
        }
        return tooltip;
    }

    protected static class GasStackWrapper {
        private final mekanism.api.gas.GasStack gasStack;
        private final int stored;

        private GasStackWrapper(mekanism.api.gas.GasStack gasStack, int stored) {
            this.gasStack = gasStack;
            this.stored = stored;
        }
    }
}