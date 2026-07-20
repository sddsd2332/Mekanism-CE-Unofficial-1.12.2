package mekanism.client.jei;

import mekanism.client.gui.GuiMekanism;
import mezz.jei.api.IRecipesGui;
import mezz.jei.api.recipe.IFocus;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.inventory.Container;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIORecipeViewerGuiHandlerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void recipeViewerDetectionRunsAfterOtherGuiOpenListeners() throws NoSuchMethodException {
        Method listener = QIORecipeViewerGuiHandler.class.getMethod("onGuiOpen", GuiOpenEvent.class);

        assertEquals(EventPriority.LOWEST, listener.getAnnotation(SubscribeEvent.class).priority());
    }

    @Test
    void directRecipeViewerOpenPreservesQIOContainer() {
        TestGuiMekanism current = new TestGuiMekanism();

        assertTrue(QIORecipeViewerGuiHandler.preserveQIOContainer(current, new TestRecipesGui()));
        assertTrue(current.switchingToJEI);
    }

    @Test
    void ordinaryGuiOpenDoesNotPreserveQIOContainer() {
        TestGuiMekanism current = new TestGuiMekanism();

        assertFalse(QIORecipeViewerGuiHandler.preserveQIOContainer(current, new OrdinaryGuiScreen()));
        assertFalse(current.switchingToJEI);
    }

    private static final class TestGuiMekanism extends GuiMekanism<Container> {

        private TestGuiMekanism() {
            super(new Container() {
                @Override
                public boolean canInteractWith(EntityPlayer playerIn) {
                    return true;
                }
            });
        }
    }

    private static final class TestRecipesGui extends GuiScreen implements IRecipesGui {

        @Override
        public void confirmClicked(boolean result, int id) {
        }

        @Override
        public <V> void show(IFocus<V> focus) {
        }

        @Override
        public void showCategories(List<String> recipeCategoryUids) {
        }

        @Override
        public Object getIngredientUnderMouse() {
            return null;
        }

        @Override
        public String getSearchFilter() {
            return "";
        }

        @Override
        public boolean setSearchFilter(String searchFilter) {
            return false;
        }

        @Override
        public RecipeSearchMode getSearchMode() {
            return RecipeSearchMode.NONE;
        }

        @Override
        public boolean setSearchMode(RecipeSearchMode searchMode) {
            return false;
        }
    }

    private static final class OrdinaryGuiScreen extends GuiScreen {

        @Override
        public void confirmClicked(boolean result, int id) {
        }
    }
}
