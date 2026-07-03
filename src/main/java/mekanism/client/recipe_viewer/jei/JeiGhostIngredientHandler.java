package mekanism.client.recipe_viewer.jei;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.recipe_viewer.GhostIngredientHandler;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mezz.jei.api.gui.IGhostIngredientHandler;

import java.awt.Rectangle;
import java.util.List;

public class JeiGhostIngredientHandler<GUI extends GuiMekanism<?>> implements IGhostIngredientHandler<GUI> {

    @Override
    public <INGREDIENT> List<Target<INGREDIENT>> getTargets(GUI gui, INGREDIENT ingredient, boolean doStart) {
        return GhostIngredientHandler.getTargetsTyped(gui, ingredient, (handler, value) -> handler.supportedTarget(value), JeiTarget::new);
    }

    @Override
    public void onComplete() {
    }

    private static class JeiTarget<INGREDIENT> implements Target<INGREDIENT> {

        private final IGhostIngredientConsumer handler;
        private final Object ingredient;
        private final Rectangle area;

        private JeiTarget(IGhostIngredientConsumer handler, Object ingredient, Rectangle area) {
            this.handler = handler;
            this.ingredient = ingredient;
            this.area = area;
        }

        @Override
        public Rectangle getArea() {
            return area;
        }

        @Override
        public void accept(INGREDIENT ignored) {
            handler.accept(ingredient);
        }
    }
}
