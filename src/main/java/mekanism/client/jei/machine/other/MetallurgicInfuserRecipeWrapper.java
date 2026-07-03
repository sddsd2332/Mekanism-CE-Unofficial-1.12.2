package mekanism.client.jei.machine.other;

import mekanism.client.gui.GuiUtils;
import mekanism.client.jei.machine.MekanismRecipeWrapper;
import mekanism.common.InfuseStorage;
import mekanism.common.config.MekanismConfig;
import mekanism.common.recipe.machines.MetallurgicInfuserRecipe;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MetallurgicInfuserRecipeWrapper<RECIPE extends MetallurgicInfuserRecipe> extends MekanismRecipeWrapper<RECIPE> {

    public MetallurgicInfuserRecipeWrapper(RECIPE recipe) {
        super(recipe);
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<ItemStack> inputStacks = Collections.singletonList(recipe.recipeInput.inputStack);
        List<ItemStack> infuseStacks = MetallurgicInfuserRecipeCategory.getInfuseStacks(recipe.getInput().infuse.getType());
        ingredients.setInput(VanillaTypes.ITEM, recipe.recipeInput.inputStack);
        ingredients.setInputLists(VanillaTypes.ITEM, Arrays.asList(inputStacks, infuseStacks));
        ingredients.setOutput(VanillaTypes.ITEM, recipe.recipeOutput.output);
    }

    @Override
    public void drawInfo(Minecraft mc, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        if (mc.currentScreen != null) {
            mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GuiUtils.drawTiledSprite(3, 0, 52, 4, 52, recipe.getInput().infuse.getType().sprite, GuiUtils.TilingDirection.DOWN_RIGHT);
        }
    }

    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (mouseX >= 3 && mouseX < 7 && mouseY >= 0 && mouseY < 52) {
            InfuseStorage infuse = recipe.getInput().infuse;
            return Collections.singletonList(infuse.getType().getLocalizedName() + ": " + infuse.getAmount());
        } else if (mouseX >= 158 && mouseX < 162 && mouseY >= 0 && mouseY < 52) {
            return Collections.singletonList(LangUtils.localize("gui.using") + ":" + MekanismUtils.getEnergyDisplay(MekanismConfig.current().usage.metallurgicInfuser.val()) + "/t");
        }
        return Collections.emptyList();
    }
}
