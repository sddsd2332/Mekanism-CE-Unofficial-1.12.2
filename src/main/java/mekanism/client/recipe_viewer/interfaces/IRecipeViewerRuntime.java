package mekanism.client.recipe_viewer.interfaces;

import net.minecraft.util.ResourceLocation;

import java.util.List;

public interface IRecipeViewerRuntime {

    default boolean showCategories(List<ResourceLocation> categoryIds) {
        return false;
    }

    default void refreshRecipeTransferButtons() {
    }
}
