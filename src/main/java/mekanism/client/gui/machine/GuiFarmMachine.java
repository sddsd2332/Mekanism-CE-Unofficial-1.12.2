package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.bar.GuiGasBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.inventory.container.ContainerFarmMachine;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.tile.prefab.TileEntityFarmMachine;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiFarmMachine<RECIPE extends FarmMachineRecipe<RECIPE>, TILE extends TileEntityFarmMachine<RECIPE>>
      extends GuiConfigurableTile<TILE, ContainerFarmMachine<RECIPE>> {

    public GuiFarmMachine(InventoryPlayer inventory, TILE tile) {
        super(tile, new ContainerFarmMachine<>(inventory, tile));
        inventoryLabelY += 2;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiVerticalPowerBar(this, tileEntity.getEnergyContainer(), 164, 15))
              .warning(WarningType.NOT_ENOUGH_ENERGY, () -> tileEntity.getEnergy() < tileEntity.energyPerTick || tileEntity.getEnergy() == 0);
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getActive));
        addButton(new GuiGasBar(this, tileEntity.gasTank, 60, 36, 8, 14, true, this::getGasBarTooltip))
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity::hasWarningNoMatchingSecondaryInput);
        addButton(new GuiSlot(SlotType.OUTPUT_WIDE, this, 111, 30)
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity::hasWarningNoSpaceInOutput));
        addButton(new GuiProgress(tileEntity::getScaledProgress, ProgressType.BAR, this, 77, 37))
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

    private List<String> getGasBarTooltip() {
        List<String> tooltip = new ArrayList<>();
        if (tileEntity.gasTank.getGas() == null) {
            tooltip.add(LangUtils.localize("gui.none"));
        } else {
            tooltip.add(tileEntity.gasTank.getGas().getGas().getLocalizedName() + ": " +
                  (tileEntity.gasTank.getStored() == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : tileEntity.gasTank.getStored()));
        }
        return tooltip;
    }
}