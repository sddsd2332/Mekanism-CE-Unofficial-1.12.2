package mekanism.client.recipe_viewer.type;

import mekanism.api.text.IHasTextComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public interface IRecipeViewerRecipeType<RECIPE> extends IHasTextComponent {

    ResourceLocation id();

    Class<? extends RECIPE> recipeClass();

    default boolean requiresHolder() {
        return true;
    }

    default ItemStack iconStack() {
        return ItemStack.EMPTY;
    }

    @Nullable
    default ResourceLocation icon() {
        return null;
    }

    int xOffset();

    int yOffset();

    int width();

    int height();

    default List<ItemStack> workstations() {
        return Collections.emptyList();
    }

    @Override
    default ITextComponent getTextComponent() {
        return new TextComponentString(id().toString());
    }
}
