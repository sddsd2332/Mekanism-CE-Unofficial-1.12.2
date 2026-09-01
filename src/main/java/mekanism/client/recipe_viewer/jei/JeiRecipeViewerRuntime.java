package mekanism.client.recipe_viewer.jei;

import mekanism.client.recipe_viewer.interfaces.IRecipeViewerRuntime;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IRecipesGui;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class JeiRecipeViewerRuntime implements IRecipeViewerRuntime {

    private final IJeiRuntime runtime;

    public JeiRecipeViewerRuntime(IJeiRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean showCategories(List<ResourceLocation> categoryIds) {
        IRecipesGui recipesGui = runtime.getRecipesGui();
        if (recipesGui == null) {
            return false;
        }
        List<String> categoryUids = new ArrayList<>(categoryIds.size());
        for (ResourceLocation categoryId : categoryIds) {
            categoryUids.add(categoryId.getNamespace() + "." + categoryId.getPath());
        }
        recipesGui.showCategories(categoryUids);
        return true;
    }

    @Override
    public void refreshRecipeTransferButtons() {
        mekanism.client.jei.MekanismJEI.refreshRecipeTransferButtons();
    }
}
