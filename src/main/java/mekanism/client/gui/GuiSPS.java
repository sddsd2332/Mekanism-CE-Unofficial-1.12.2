package mekanism.client.gui;

import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiDynamicHorizontalRateBar;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.ContainerSPS;
import mekanism.common.lib.Color;
import mekanism.common.lib.Color.ColorFunction;
import mekanism.common.tile.TileEntitySPS;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiSPS extends GuiConfigurableTile<TileEntitySPS, ContainerSPS> {

    public GuiSPS(InventoryPlayer inventory, TileEntitySPS tile) {
        super(tile, new ContainerSPS(inventory, tile));
        ySize += 5;
        inventoryLabelY = ySize - 92;
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new GuiEnergyTab(this, () -> Arrays.asList(
              new TextComponentString(LangUtils.localize("gui.using") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.lastReceivedEnergy) + "/t"),
              new TextComponentString(LangUtils.localize("gui.needed") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getNeedEnergy()))
        )));
        addButton(new GuiGasGauge(this, tileEntity.inputTank, GuiGasGauge.Type.STANDARD, 7, 17).withColor(GuiGasGauge.GaugeColor.RED));
        addButton(new GuiGasGauge(this, tileEntity.outputTank, GuiGasGauge.Type.STANDARD, 151, 17).withColor(GuiGasGauge.GaugeColor.RED));
        addButton(new GuiInnerScreen(this, 27, 17, 122, 60, this::getScreenText).recipeViewerCategories(RecipeViewerRecipeType.SPS));
        addButton(new GuiDynamicHorizontalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentGroup().translation(MekanismLang.PROGRESS.getTranslationKey()).string(" " + TextUtils.getPercent(tileEntity.getScaledProgress()));
            }

            @Override
            public double getLevel() {
                return Math.min(1, tileEntity.getScaledProgress());
            }
        }, 7, 79, 160, ColorFunction.scale(Color.rgbi(60, 45, 74), Color.rgbi(100, 30, 170))));
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        drawTitleText(new TextComponentString(tileEntity.getName()), 6);
        renderInventoryText();
        super.drawForegroundText(mouseX, mouseY);
    }

    private List<ITextComponent> getScreenText() {
        List<ITextComponent> list = new ArrayList<>();
        boolean active = tileEntity.lastProcessed > 0;
        list.add(new TextComponentGroup().translation(MekanismLang.STATUS.getTranslationKey())
              .string(" ")
              .translation(active ? MekanismLang.ACTIVE.getTranslationKey() : MekanismLang.IDLE.getTranslationKey()));
        if (active) {
            list.add(new TextComponentGroup().translation(MekanismLang.SPS_ENERGY_INPUT.getTranslationKey())
                  .string(" " + MekanismUtils.getEnergyDisplay(tileEntity.lastReceivedEnergy)));
            list.add(new TextComponentGroup().translation(MekanismLang.PROCESS_RATE_MB.getTranslationKey())
                  .string(" " + tileEntity.getProcessRate() + "mB/t"));
        }
        return list;
    }
}
