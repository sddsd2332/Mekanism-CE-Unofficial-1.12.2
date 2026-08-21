package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiEnergyGauge;
import mekanism.client.gui.element.gauge.GuiHybridGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.inventory.container.ContainerFarmMachine;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.tile.prefab.TileEntityFarmMachine;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;

@SideOnly(Side.CLIENT)
public class GuiFarmMachine<RECIPE extends FarmMachineRecipe<RECIPE>, TILE extends TileEntityFarmMachine<RECIPE>>
      extends GuiConfigurableTile<TILE, ContainerFarmMachine<RECIPE>> {

    public GuiFarmMachine(InventoryPlayer inventory, TILE tile) {
        super(tile, new ContainerFarmMachine<>(inventory, tile));
        xSize = 220;
        ySize = 254;
        inventoryLabelX = 30;
        inventoryLabelY = 163;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiEnergyGauge(this, tileEntity.getEnergyContainer(), GuiEnergyGauge.Type.STANDARD, 37, 15))
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> tileEntity.getEnergy() < tileEntity.energyPerTick || tileEntity.getEnergy() == 0);
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getActive));
        addButton(new GuiHybridGauge(() -> tileEntity.mergedTank.getGasTank(),
              () -> Collections.singletonList(tileEntity.mergedTank.getGasTank()),
              () -> tileEntity.mergedTank.getFluidTank(),
              () -> Collections.singletonList(tileEntity.mergedTank.getFluidTank()),
              GaugeType.STANDARD, this, 37, 99))
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity::hasWarningNoMatchingSecondaryInput);
        trackWarning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity::hasWarningNoSpaceInOutput);
        addButton(new GuiProgress(tileEntity::getScaledProgress, ProgressType.RIGHT, this, 32, 83))
              .recipeViewerCategories(getRecipeViewerRecipeTypes())
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity::hasWarningInputDoesntProduceOutput);
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
