package mekanism.client.gui;

import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiDynamicHorizontalRateBar;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.MekanismLang;
import mekanism.common.capabilities.tank.ValidatingGasTank;
import mekanism.common.content.sps.SynchronizedSPSData;
import mekanism.common.inventory.container.ContainerSPSMultiblock;
import mekanism.common.lib.Color;
import mekanism.common.lib.Color.ColorFunction;
import mekanism.common.tile.multiblock.TileEntitySPSCasing;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiSPSMultiblock extends GuiMekanismTile<TileEntitySPSCasing, ContainerSPSMultiblock> {

    private final IExtendedGasTank emptyInput = new ValidatingGasTank(SynchronizedSPSData.INPUT_CAPACITY, gas -> true);
    private final IExtendedGasTank emptyOutput = new ValidatingGasTank(SynchronizedSPSData.OUTPUT_CAPACITY, gas -> true);

    public GuiSPSMultiblock(InventoryPlayer inventory, TileEntitySPSCasing tile) {
        super(tile, new ContainerSPSMultiblock(inventory, tile));
        dynamicSlots = true;
        ySize += 16;
        inventoryLabelY = ySize - 92;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiGasGauge(this, this::getInputTank, GuiGasGauge.Type.STANDARD, 7, 17)
              .withColor(GuiGasGauge.GaugeColor.RED));
        addButton(new GuiGasGauge(this, this::getOutputTank, GuiGasGauge.Type.STANDARD, 151, 17)
              .withColor(GuiGasGauge.GaugeColor.RED));
        addButton(new GuiInnerScreen(this, 27, 17, 122, 60, this::getScreenText).recipeViewerCategories(RecipeViewerRecipeType.SPS));
        addButton(new GuiDynamicHorizontalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentGroup().translation(MekanismLang.PROGRESS.getTranslationKey()).string(" " + TextUtils.getPercent(getScaledProgress()));
            }

            @Override
            public double getLevel() {
                return Math.min(1, getScaledProgress());
            }
        }, 7, 79, 160, ColorFunction.scale(Color.rgbi(60, 45, 74), Color.rgbi(100, 30, 170))));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 5);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> list = new ArrayList<>();
        SynchronizedSPSData structure = tileEntity.structure;
        boolean active = structure != null && structure.lastProcessed > 0;
        list.add(new TextComponentGroup().translation(MekanismLang.STATUS.getTranslationKey())
              .string(" ")
              .translation(active ? MekanismLang.ACTIVE.getTranslationKey() : MekanismLang.IDLE.getTranslationKey()));
        if (structure != null) {
            list.add(new TextComponentGroup().translation(MekanismLang.SPS_ENERGY_INPUT.getTranslationKey())
                  .string(" " + MekanismUtils.getEnergyDisplay(structure.lastReceivedEnergy)));
            list.add(new TextComponentGroup().translation(MekanismLang.PROCESS_RATE_MB.getTranslationKey())
                  .string(" " + structure.getProcessRate() + "mB/t"));
            list.add(new TextComponentGroup().string(LangUtils.localize("gui.formed") + ": true"));
        } else {
            list.add(new TextComponentGroup().string(LangUtils.localize("gui.formed") + ": false"));
        }
        return list;
    }

    private IExtendedGasTank getInputTank() {
        return tileEntity.structure == null ? emptyInput : tileEntity.structure.inputTank;
    }

    private IExtendedGasTank getOutputTank() {
        return tileEntity.structure == null ? emptyOutput : tileEntity.structure.outputTank;
    }

    private double getScaledProgress() {
        return tileEntity.structure == null ? 0 : tileEntity.structure.getScaledProgress();
    }
}
