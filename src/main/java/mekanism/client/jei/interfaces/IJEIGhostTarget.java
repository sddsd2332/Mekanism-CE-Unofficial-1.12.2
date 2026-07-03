package mekanism.client.jei.interfaces;

import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;

public interface IJEIGhostTarget extends IRecipeViewerGhostTarget {

    interface IGhostIngredientConsumer extends IRecipeViewerGhostTarget.IGhostIngredientConsumer {
    }

    interface IGhostItemConsumer extends IGhostIngredientConsumer, IRecipeViewerGhostTarget.IGhostItemConsumer {
    }

    interface IGhostBlockItemConsumer extends IGhostItemConsumer, IRecipeViewerGhostTarget.IGhostBlockItemConsumer {
    }
}
