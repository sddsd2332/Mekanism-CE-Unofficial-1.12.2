package mekanism.client.jei.machine.other;

import mekanism.client.jei.MekanismJEI;
import mekanism.client.jei.machine.MekanismRecipeWrapper;
import mekanism.common.recipe.inputs.IntegerInput;
import mekanism.common.recipe.machines.AmbientGasRecipe;
import mekanism.common.recipe.outputs.ChanceGasOutput;
import mekanism.common.util.LangUtils;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import javax.annotation.Nonnull;

public class AmbientGasRecipeWrapper<RECIPE extends AmbientGasRecipe> extends MekanismRecipeWrapper<RECIPE> {

    public AmbientGasRecipeWrapper(RECIPE recipe) {
        super(recipe);
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setOutput(MekanismJEI.TYPE_GAS, recipe.getOutput().output);
    }

    @Override
    public void drawInfo(@Nonnull Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        ChanceGasOutput output = recipe.getOutput();
        IntegerInput input = recipe.getInput();
        if (output != null) {
            FontRenderer fontRendererObj = minecraft.fontRenderer;
            fontRendererObj.drawString(LangUtils.localize("gui.dimension2") + ":" + input.ingredient, 3, 3, 0x33ff99);
            fontRendererObj.drawString(LangUtils.localize("gui.dimensionGas") + ":", 3, 12, 0x33ff99);
            fontRendererObj.drawString(output.output.getGas().getLocalizedName(), 3, 21, 0x33ff99);
            fontRendererObj.drawString(LangUtils.localize("gui.probability2") + ":" + Math.round(output.primaryChance * 100) + "%", 3, 30, 0x33ff99);
        }
    }

}
