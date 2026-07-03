package mekanism.client.gui.machine;

import mekanism.client.gui.GuiConfigurableTile;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.GuiDownArrow;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.bar.GuiHorizontalPowerBar;
import mekanism.client.gui.element.button.ToggleButton;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.client.sound.SoundHandler;
import mekanism.common.Mekanism;
import mekanism.common.inventory.container.ContainerRotaryCondensentrator;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.tile.machine.TileEntityRotaryCondensentrator;
import mekanism.common.util.LangUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiRotaryCondensentrator extends GuiConfigurableTile<TileEntityRotaryCondensentrator, ContainerRotaryCondensentrator> {

    private GuiElement energyBar;

    public GuiRotaryCondensentrator(InventoryPlayer inventory, TileEntityRotaryCondensentrator tile) {
        super(tile, new ContainerRotaryCondensentrator(inventory, tile));
        dynamicSlots = true;
        titleLabelY = 4;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiDownArrow(this, 159, 44));
        energyBar = addButton(new GuiHorizontalPowerBar(this, tileEntity.getEnergyContainer(), 115, 75)
              .warning(WarningType.NOT_ENOUGH_ENERGY, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY))
              .warning(WarningType.NOT_ENOUGH_ENERGY_REDUCED_RATE, tileEntity.getWarningCheck(RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE)));
        addButton(new GuiEnergyTab(this, tileEntity.getEnergyContainer(), tileEntity::getEnergyUsed));
        addButton(new GuiFluidGauge(this, tileEntity.fluidTank, GuiFluidGauge.Type.STANDARD, 133, 13)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(TileEntityRotaryCondensentrator.NOT_ENOUGH_FLUID_INPUT_ERROR))
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(TileEntityRotaryCondensentrator.NOT_ENOUGH_SPACE_FLUID_OUTPUT_ERROR)));
        addButton(new GuiGasGauge(this, tileEntity.gasTank, 25, 13)
              .warning(WarningType.NO_MATCHING_RECIPE, tileEntity.getWarningCheck(TileEntityRotaryCondensentrator.NOT_ENOUGH_GAS_INPUT_ERROR))
              .warning(WarningType.NO_SPACE_IN_OUTPUT, tileEntity.getWarningCheck(TileEntityRotaryCondensentrator.NOT_ENOUGH_SPACE_GAS_OUTPUT_ERROR)));
        addButton(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler.IBooleanProgressInfoHandler() {
            @Override
            public boolean fillProgressBar() {
                return tileEntity.getActive();
            }

            @Override
            public boolean isActive() {
                return tileEntity.mode == 0;
            }
        }, ProgressType.LARGE_RIGHT, this, 64, 39))
              .recipeViewerCategories(RecipeViewerRecipeType.CONDENSENTRATING)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
        addButton(new GuiProgress(new mekanism.client.gui.element.progress.IProgressInfoHandler.IBooleanProgressInfoHandler() {
            @Override
            public boolean fillProgressBar() {
                return tileEntity.getActive();
            }

            @Override
            public boolean isActive() {
                return tileEntity.mode == 1;
            }
        }, ProgressType.LARGE_LEFT, this, 64, 39))
              .recipeViewerCategories(RecipeViewerRecipeType.DECONDENSENTRATING)
              .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, tileEntity.getWarningCheck(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
        addButton(new ToggleButton(this, 4, 4, () -> tileEntity.mode == 1, this::sendModePacket,
              new TextComponentString(LangUtils.localize("gui.rotaryCondensentrator.toggleOperation")),
              new TextComponentString(LangUtils.localize("gui.rotaryCondensentrator.toggleOperation"))));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), titleLabelY);
        drawScaledScrollingString(new TextComponentString(tileEntity.mode == 0 ? LangUtils.localize("gui.condensentrating") : LangUtils.localize("gui.decondensentrating")),
              4, ySize - 92, TextAlignment.LEFT, titleTextColor(), energyBar.getRelativeX() - 4, 2, false, 1, GuiElement.getMillis());
        super.drawForegroundText(mouseX, mouseY);
    }

    private void sendModePacket() {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(0)));
        SoundHandler.playSound(SoundEvents.UI_BUTTON_CLICK);
    }
}