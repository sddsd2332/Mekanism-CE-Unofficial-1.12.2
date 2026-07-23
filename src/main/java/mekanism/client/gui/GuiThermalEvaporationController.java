package mekanism.client.gui;

import mekanism.client.gui.element.GuiDownArrow;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiHorizontalRateBar;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.tab.GuiHeatTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.warning.IWarningTracker;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.inventory.container.ContainerThermalEvaporationController;
import mekanism.common.tile.multiblock.TileEntityThermalEvaporationController;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiThermalEvaporationController extends GuiMekanismTile<TileEntityThermalEvaporationController, ContainerThermalEvaporationController> {

    private GuiFluidGauge inputGauge;
    private GuiFluidGauge outputGauge;

    public GuiThermalEvaporationController(InventoryPlayer inventory, TileEntityThermalEvaporationController tile) {
        super(tile, new ContainerThermalEvaporationController(inventory, tile));
        xSize += 20;
        inventoryLabelX += 10;
        inventoryLabelY += 2;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        inputGauge = addButton(new GuiFluidGauge(this, tileEntity.inputTank, GuiFluidGauge.Type.STANDARD, 6, 13))
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity::hasWarningNoMatchingRecipe);
        outputGauge = addButton(new GuiFluidGauge(this, tileEntity.outputTank, GuiFluidGauge.Type.STANDARD, 172, 13))
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity::hasWarningNoSpaceInOutput);
        addButton(new GuiHeatTab(this, () -> {
            String environment = MekanismUtils.getTemperatureDisplay(tileEntity.totalLoss, TemperatureUnit.KELVIN, false);
            return Collections.singletonList(new TextComponentString(LangUtils.localize("gui.dissipated") + ": " + environment + "/t"));
        }));
        addButton(new GuiHorizontalRateBar(this, new GuiHorizontalRateBar.IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentString(LangUtils.localize("gui.temp") + ": " + getTemp());
            }

            @Override
            public double getLevel() {
                return tileEntity.getTemperatureScale();
            }
        }, 58, 62)).warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity::hasWarningInputDoesntProduceOutput);
        addButton(new GuiDownArrow(this, 32, 39));
        addButton(new GuiDownArrow(this, 156, 39));
        addButton(new GuiInnerScreen(this, 48, 19, 100, 40, this::getScreenText).spacing(1).recipeViewerCategories(RecipeViewerRecipeType.EVAPORATING));
    }

    @Override
    protected void addWarningTab(IWarningTracker warningTracker) {
        addButton(new GuiWarningTab(this, warningTracker, false));
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> list = new ArrayList<>();
        list.add(new TextComponentString(getStruct()));
        list.add(new TextComponentString(LangUtils.localize("gui.height") + ": " + tileEntity.height));
        list.add(new TextComponentString(LangUtils.localize("gui.temp") + ": " + getTemp()));
        list.add(new TextComponentString(LangUtils.localize("gui.production") + ": " + Math.round(tileEntity.lastGain * 100D) / 100D + " mB/t"));
        return list;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleTextWithOffset(new TextComponentString(tileEntity.getName()), inputGauge.getRelativeRight(), 4, outputGauge.getRelativeX());
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    private String getStruct() {
        if (tileEntity.structured) {
            return LangUtils.localize("gui.formed");
        } else if (tileEntity.controllerConflict) {
            return LangUtils.localize("gui.conflict");
        }
        return LangUtils.localize("gui.incomplete");
    }

    private String getTemp() {
        return MekanismUtils.getTemperatureDisplay(tileEntity.getTemperature(), TemperatureUnit.KELVIN);
    }
}
