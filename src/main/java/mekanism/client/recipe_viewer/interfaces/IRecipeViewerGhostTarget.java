package mekanism.client.recipe_viewer.interfaces;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public interface IRecipeViewerGhostTarget {

    /**
     * @return {@code null} if it doesn't actually currently support ghost handling
     */
    @Nullable
    IGhostIngredientConsumer getGhostHandler();

    /**
     * Number of pixels on each side that make up the border, and should be ignored when creating the target area.
     */
    default int borderSize() {
        return 0;
    }

    interface IGhostIngredientConsumer extends Consumer<Object> {

        @Nullable
        Object supportedTarget(Object ingredient);
    }

    interface IGhostItemConsumer extends IGhostIngredientConsumer {

        @Nullable
        @Override
        default ItemStack supportedTarget(Object ingredient) {
            return ingredient instanceof ItemStack stack && !stack.isEmpty() ? stack : null;
        }
    }

    interface IGhostBlockItemConsumer extends IGhostItemConsumer {

        @Nullable
        @Override
        default ItemStack supportedTarget(Object ingredient) {
            ItemStack supported = IGhostItemConsumer.super.supportedTarget(ingredient);
            return supported != null && supported.getItem() instanceof ItemBlock ? supported : null;
        }
    }
}
