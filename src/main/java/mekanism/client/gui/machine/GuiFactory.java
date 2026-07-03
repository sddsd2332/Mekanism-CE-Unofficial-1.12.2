package mekanism.client.gui.machine;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiDumpButton;
import mekanism.client.gui.element.bar.GuiFluidBar;
import mekanism.client.gui.element.bar.GuiGasBar;
import mekanism.client.gui.element.bar.GuiInfuseBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.tab.GuiSortingTab;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.inventory.container.ContainerFactory;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.interfaces.IHasDumpButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiFactory extends GuiConfigurableTile<TileEntityFactory, ContainerFactory> {

    private GuiDumpButton<?> dumpButton;
    private final InventoryPlayer inventory;
    private RecipeType displayedRecipeType;

    public GuiFactory(InventoryPlayer inventory, TileEntityFactory tile) {
        super(tile, new ContainerFactory(inventory, tile));
        this.inventory = inventory;
        displayedRecipeType = tile.getRecipeType();
        dynamicSlots = true;
        xSize += tile.getFactoryGuiWidthExtra();
        ySize += tile.getFactoryGuiHeightExtra();
        updateFactoryLabelPositions();
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiSortingTab(this, tileEntity));
        addButton(new GuiVerticalPowerBar(this, tileEntity.getMainEnergyContainer(), getXSize() - 12, 16,
              tileEntity.showsLongPowerBar() ? 73 : 52))
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY, 0));
        addButton(new GuiEnergyTab(this, tileEntity.getMainEnergyContainer(), tileEntity::getLastUsage));

        addSecondaryResourceElements();
        addTankElements();
        addSecondaryResourceDumpButton();
        addProcessProgressBars();
    }

    private void addProcessProgressBars() {
        for (int process = 0; process < tileEntity.getProcessCount(); process++) {
            int cacheIndex = process;
            addButton(new GuiProgress(() -> tileEntity.getScaledProgress(cacheIndex), ProgressType.DOWN, this, tileEntity.getProcessProgressX(process), 33))
                  .recipeViewerCategories(getRecipeViewerRecipeTypes())
                  .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT, cacheIndex));
        }
    }

    private void addSecondaryResourceElements() {
        if (!tileEntity.hasSecondaryResourceBar()) {
            return;
        }
        int width = tileEntity.getSecondaryResourceBarWidth();
        if (tileEntity.usesPressurizedTankBars()) {
            addButton(new GuiGasBar(this, tileEntity.getOutputGasTank(), tileEntity.getSecondaryResourceBarX(), tileEntity.getSecondaryResourceBarY(), width, 4, false,
                  Collections::emptyList))
                  .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE, 0));
            addButton(new GuiFluidBar(this, tileEntity.getInputFluidTank(), tileEntity.getSecondaryResourceBarX(), tileEntity.getSecondaryResourceBarY() + 8, width, 4, false))
                  .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_SECONDARY_INPUT, 0));
        } else if (tileEntity.usesGasSecondaryResourceBar()) {
            addButton(new GuiGasBar(this, tileEntity.getInputGasTank(), tileEntity.getSecondaryResourceBarX(), tileEntity.getSecondaryResourceBarY(), width, 4, false,
                  tileEntity::getSecondaryGasTooltip))
                  .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_SECONDARY_INPUT, 0));
        } else {
            addButton(new GuiInfuseBar(this, tileEntity.getInfuseStorage(), () -> tileEntity.getScaledInfuseLevel(width - 2) / (double) (width - 2),
                  tileEntity::getInfuseTooltip, tileEntity.getSecondaryResourceBarX(), tileEntity.getSecondaryResourceBarY(), width, 4, false))
                  .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_SECONDARY_INPUT, 0));
        }
    }

    private void addTankElements() {
        if (tileEntity.showsInputGasGauge()) {
            addButton(createGasGauge(tileEntity.getInputGasTank(), tileEntity.usesSlotInputGasGauge(), false, getInputGasGaugeType(), tileEntity.getInputGasGaugeX(),
                  tileEntity.getInputGasGaugeY())
                  .withColor(getInputGasGaugeColor())
                  .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_SECONDARY_INPUT, 0)));
        }
        if (tileEntity.showsOutputGasGauge()) {
            addButton(createGasGauge(tileEntity.getOutputGasTank(), tileEntity.usesSlotOutputGasGauge(), tileEntity.usesHorizontalOutputGasGauge(),
                  getOutputGasGaugeType(), tileEntity.getOutputGasGaugeX(), tileEntity.getOutputGasGaugeY()).withColor(GuiGasGauge.GaugeColor.BLUE)
                  .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE, 0)));
        }
        if (tileEntity.showsInputFluidGauge()) {
            addButton(createFluidGauge(tileEntity.getInputFluidTank(), tileEntity.usesSlotInputFluidGauge(), tileEntity.usesHorizontalInputFluidGauge(),
                  GuiFluidGauge.Type.STANDARD, tileEntity.getInputFluidGaugeX(), tileEntity.getInputFluidGaugeY())
                  .withColor(getInputFluidGaugeColor())
                  .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_SECONDARY_INPUT, 0)));
        }
    }

    private GuiGasGauge createGasGauge(IExtendedGasTank gasTank, boolean slot, boolean horizontal, GuiGasGauge.Type type, int x, int y) {
        if (!slot) {
            return new GuiGasGauge(this, gasTank, type, x, y);
        } else if (horizontal) {
            return new GuiGasGauge(() -> gasTank, () -> Collections.singletonList(gasTank), GaugeType.SlOT, this, x, y, tileEntity.getFactoryTankGaugeWidth(), 18) {
                @Override
                protected boolean isVertical() {
                    return false;
                }
            };
        }
        return new GuiGasGauge(() -> gasTank, () -> Collections.singletonList(gasTank), GaugeType.SlOT, this, x, y);
    }

    private GuiFluidGauge createFluidGauge(IExtendedFluidTank fluidTank, boolean slot, boolean horizontal, GuiFluidGauge.Type type, int x, int y) {
        if (!slot) {
            return new GuiFluidGauge(this, fluidTank, type, x, y);
        } else if (horizontal) {
            return new GuiFluidGauge(() -> fluidTank, () -> Collections.singletonList(fluidTank), GaugeType.SlOT, this, x, y, tileEntity.getFactoryTankGaugeWidth(), 18) {
                @Override
                protected boolean isVertical() {
                    return false;
                }
            };
        }
        return new GuiFluidGauge(this, fluidTank, GaugeType.SlOT, x, y);
    }

    private GuiGasGauge.Type getInputGasGaugeType() {
        return tileEntity.usesStandardInputGasGauge() ? GuiGasGauge.Type.STANDARD : GuiGasGauge.Type.SMALL;
    }

    private GuiGasGauge.Type getOutputGasGaugeType() {
        return tileEntity.usesStandardOutputGasGauge() ? GuiGasGauge.Type.STANDARD : GuiGasGauge.Type.SMALL;
    }

    private GuiGasGauge.GaugeColor getInputGasGaugeColor() {
        return tileEntity.usesRedInputGasGauge() ? GuiGasGauge.GaugeColor.RED : GuiGasGauge.GaugeColor.YELLOW;
    }

    private GuiFluidGauge.GaugeColor getInputFluidGaugeColor() {
        return tileEntity.usesRedInputFluidGauge() ? GuiFluidGauge.GaugeColor.RED : GuiFluidGauge.GaugeColor.YELLOW;
    }

    private void addSecondaryResourceDumpButton() {
        dumpButton = null;
        if (!tileEntity.hasSecondaryResourceDump()) {
            return;
        }
        dumpButton = addButton(new GuiDumpButton<>(this, (TileEntityFactory & IHasDumpButton) tileEntity, tileEntity.getSecondaryResourceDumpButtonX(),
              tileEntity.getSecondaryResourceDumpButtonY()));
    }

    private void rebuildFactoryGui() {
        int windowId = inventorySlots.windowId;
        xSize = 176 + tileEntity.getFactoryGuiWidthExtra();
        ySize = 166 + tileEntity.getFactoryGuiHeightExtra();
        updateFactoryLabelPositions();
        buttons.clear();
        focusListeners.clear();
        while (!windows.isEmpty()) {
            GuiWindow window = windows.iterator().next();
            window.close();
        }
        inventorySlots = new ContainerFactory(inventory, tileEntity);
        inventorySlots.windowId = windowId;
        dumpButton = null;
        initGui();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (displayedRecipeType != tileEntity.getRecipeType()) {
            displayedRecipeType = tileEntity.getRecipeType();
            rebuildFactoryGui();
        }
    }

    private IRecipeViewerRecipeType<?>[] getRecipeViewerRecipeTypes() {
        List<String> categories = tileEntity.getJeiRecipeCategories();
        IRecipeViewerRecipeType<?>[] recipeTypes = new IRecipeViewerRecipeType[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            recipeTypes[i] = RecipeViewerRecipeType.simple(categories.get(i));
        }
        return recipeTypes;
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 4);
        renderInventoryText(dumpButton == null ? getXSize() : dumpButton.getRelativeX());
        super.drawForegroundText(mouseX, mouseY);
    }

    private void updateFactoryLabelPositions() {
        inventoryLabelX = tileEntity.getPlayerInventoryXOffset();
        inventoryLabelY = tileEntity.getPlayerInventoryGuiY();
        titleLabelY = 4;
    }

}
