package mekanism.client.recipe_viewer;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.jei.MekanismJEI;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerRecipeArea;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mezz.jei.api.IJeiRuntime;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class RecipeViewerUtils {

    private RecipeViewerUtils() {
    }

    public static boolean openRecipeViewerRecipes(IRecipeViewerRecipeArea<?> recipeArea, double mouseX, double mouseY, int button) {
        if (button != 0 || !recipeArea.isRecipeViewerAreaActive() || !recipeArea.isMouseOverRecipeViewerArea(mouseX, mouseY)) {
            return false;
        }
        IRecipeViewerRecipeType<?>[] recipeCategories = recipeArea.getRecipeCategories();
        if (recipeCategories == null || recipeCategories.length == 0) {
            return false;
        }
        List<String> uids = new ArrayList<>();
        for (IRecipeViewerRecipeType<?> recipeCategory : recipeCategories) {
            if (recipeCategory != null) {
                String uid = RecipeViewerRecipeType.categoryUid(recipeCategory);
                if (uid != null && !uid.isEmpty()) {
                    uids.add(uid);
                }
            }
        }
        if (uids.isEmpty()) {
            return false;
        }
        IJeiRuntime runtime = MekanismJEI.jeiRuntime;
        if (runtime == null) {
            return false;
        }
        if (Minecraft.getMinecraft().currentScreen instanceof GuiMekanism) {
            ((GuiMekanism<?>) Minecraft.getMinecraft().currentScreen).switchingToJEI = true;
        }
        runtime.getRecipesGui().showCategories(uids);
        return true;
    }
}
