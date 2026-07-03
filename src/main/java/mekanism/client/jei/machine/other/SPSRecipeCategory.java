package mekanism.client.jei.machine.other;

import mekanism.api.gas.GasStack;
import mekanism.api.text.TextComponentGroup;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.bar.GuiBar.IBarInfoHandler;
import mekanism.client.gui.element.bar.GuiDynamicHorizontalRateBar;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.MekanismJEI;
import mekanism.common.MekanismLang;
import mekanism.common.lib.Color;
import mekanism.common.lib.Color.ColorFunction;
import mekanism.common.util.text.TextUtils;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.List;

public class SPSRecipeCategory extends BaseRecipeCategory<SPSRecipeWrapper> {

    private GuiGauge<?> input;
    private GuiGauge<?> output;

    public SPSRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/Null.png", "mekanism.sps", "tile.MachineBlock4.sps.name", 3, 12, 168, 74);
    }

    @Override
    protected void addGuiElements() {
        guiElements.add(new GuiInnerScreen(this, 26, 13, 122, 60, () -> {
            List<ITextComponent> list = new ArrayList<>();
            list.add(new TextComponentGroup().translation(MekanismLang.STATUS.getTranslationKey())
                  .string(" ")
                  .translation(MekanismLang.ACTIVE.getTranslationKey()));
            list.add(new TextComponentGroup().translation(MekanismLang.PROCESS_RATE_MB.getTranslationKey()).string(" 1mB/t"));
            return list;
        }));
        input = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.RED, 6, 13));
        output = addElement(dummyGasGauge(GuiGasGauge.Type.STANDARD, GuiGasGauge.GaugeColor.RED, 150, 13));
        guiElements.add(new GuiDynamicHorizontalRateBar(this, new IBarInfoHandler() {
            @Override
            public ITextComponent getTooltip() {
                return new TextComponentGroup().translation(MekanismLang.PROGRESS.getTranslationKey()).string(" " + TextUtils.getPercent(timer.getValue() / 20F));
            }

            @Override
            public double getLevel() {
                return timer.getValue() / 20F;
            }
        }, 6, 75, 160, ColorFunction.scale(Color.rgbi(60, 45, 74), Color.rgbi(100, 30, 170))));
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, SPSRecipeWrapper spsRecipeWrapper, IIngredients iIngredients) {
        IGuiIngredientGroup<GasStack> gasStacks = recipeLayout.getIngredientsGroup(MekanismJEI.TYPE_GAS);
        initGas(gasStacks, 0, true, input, new GasStack(spsRecipeWrapper.getInputGas(), 1000));
        initGas(gasStacks, 1, false, output, new GasStack(spsRecipeWrapper.getOutputGas(), 1));
    }
}
