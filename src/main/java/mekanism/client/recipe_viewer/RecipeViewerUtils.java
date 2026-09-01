package mekanism.client.recipe_viewer;

import mekanism.client.recipe_viewer.interfaces.IRecipeViewerRecipeArea;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerRuntime;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class RecipeViewerUtils {

    private static final IRecipeViewerRuntime EMPTY_RUNTIME = new IRecipeViewerRuntime() {
    };
    private static volatile IRecipeViewerRuntime runtime = EMPTY_RUNTIME;

    private RecipeViewerUtils() {
    }

    public static void setRuntime(IRecipeViewerRuntime runtime) {
        RecipeViewerUtils.runtime = runtime == null ? EMPTY_RUNTIME : runtime;
    }

    public static boolean openRecipeViewerRecipes(IRecipeViewerRecipeArea<?> recipeArea, double mouseX, double mouseY, int button) {
        if (button != 0 || !recipeArea.isRecipeViewerAreaActive() || !recipeArea.isMouseOverRecipeViewerArea(mouseX, mouseY)) {
            return false;
        }
        IRecipeViewerRecipeType<?>[] recipeCategories = recipeArea.getRecipeCategories();
        if (recipeCategories == null || recipeCategories.length == 0) {
            return false;
        }
        List<ResourceLocation> categoryIds = new ArrayList<>();
        for (IRecipeViewerRecipeType<?> recipeCategory : recipeCategories) {
            if (recipeCategory != null) {
                ResourceLocation id = recipeCategory.id();
                if (id != null) {
                    categoryIds.add(id);
                }
            }
        }
        if (categoryIds.isEmpty()) {
            return false;
        }
        return runtime.showCategories(categoryIds);
    }

    public static void refreshRecipeTransferButtons() {
        runtime.refreshRecipeTransferButtons();
    }
}
