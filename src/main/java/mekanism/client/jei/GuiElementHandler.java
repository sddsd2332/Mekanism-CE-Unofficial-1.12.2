package mekanism.client.jei;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.Widget;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.jei.interfaces.IJEIIngredientHelper;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import net.minecraft.client.gui.inventory.GuiContainer;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GuiElementHandler implements IAdvancedGuiHandler {

    private static boolean areaSticksOut(int x, int y, int width, int height, int parentX, int parentY, int parentWidth, int parentHeight) {
        return x < parentX || y < parentY || x + width > parentX + parentWidth || y + height > parentY + parentHeight;
    }

    public static List<Rectangle> getAreasFor(int parentX, int parentY, int parentWidth, int parentHeight, Collection<? extends Widget> children) {
        List<Rectangle> areas = new ArrayList<>();
        for (Widget child : children) {
            if (!child.visible) {
                continue;
            }
            int x = child.x;
            int y = child.y;
            int width = child.getWidth();
            int height = child.getHeight();
            if (areaSticksOut(x, y, width, height, parentX, parentY, parentWidth, parentHeight)) {
                areas.add(new Rectangle(x, y, width, height));
            }
            if (child instanceof GuiElement element) {
                for (Rectangle grandChildArea : getAreasFor(x, y, width, height, element.children())) {
                    if (areaSticksOut(grandChildArea.x, grandChildArea.y, grandChildArea.width, grandChildArea.height,
                          parentX, parentY, parentWidth, parentHeight)) {
                        areas.add(grandChildArea);
                    }
                }
            }
        }
        return areas;
    }

    @Override
    public Class getGuiContainerClass() {
        return GuiMekanism.class;
    }

    @Override
    public List<Rectangle> getGuiExtraAreas(GuiContainer gui) {
        if (gui instanceof GuiMekanism<?> guiMek) {
            int parentX = guiMek.getLeft();
            int parentY = guiMek.getTop();
            int parentWidth = guiMek.getWidth();
            int parentHeight = guiMek.getHeight();
            List<Rectangle> extraAreas = getAreasFor(parentX, parentY, parentWidth, parentHeight, guiMek.children());
            extraAreas.addAll(getAreasFor(parentX, parentY, parentWidth, parentHeight, guiMek.getWindows()));
            return extraAreas;
        }
        return null;
    }

    @Override
    public Object getIngredientUnderMouse(GuiContainer guiContainer, int mouseX, int mouseY) {
        if (!(guiContainer instanceof GuiMekanism<?> guiMek) || hasFocusedTextField(guiMek.getFocused())) {
            return null;
        }
        GuiWindow window = guiMek.getWindowHovering(mouseX, mouseY);
        if (window != null) {
            // A hovered window owns this mouse position. Never expose an
            // ingredient from the screen behind it.
            return findIngredient(window.children(), mouseX, mouseY).ingredient;
        }
        return findIngredient(guiMek.children(), mouseX, mouseY).ingredient;
    }

    private static boolean hasFocusedTextField(Object focused) {
        if (focused instanceof GuiTextField) {
            return true;
        }
        if (focused instanceof GuiElement element) {
            if (element instanceof GuiTextField && element.isFocused()) {
                return true;
            }
            for (GuiElement child : element.children()) {
                if (child.isFocused() && hasFocusedTextField(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static IngredientLookup findIngredient(List<? extends Widget> children, double mouseX, double mouseY) {
        // GuiElement click and tooltip dispatch both visit later-added
        // children first, so use the same order for JEI hit testing.
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget child = children.get(i);
            if (!child.visible) {
                continue;
            }
            if (child instanceof GuiElement element) {
                IngredientLookup nested = findIngredient(element.children(), mouseX, mouseY);
                if (nested.handled) {
                    return nested;
                }
            }
            if (child instanceof IJEIIngredientHelper helper && child.isMouseOver(mouseX, mouseY)) {
                return new IngredientLookup(true, helper.getIngredient(mouseX, mouseY));
            }
            // A visible interactive element above a resource slot owns this
            // position even when it is not itself an ingredient helper (for
            // example an expanded sort dropdown). Do not fall through to a
            // helper rendered behind it.
            if (child.isMouseOver(mouseX, mouseY)) {
                return IngredientLookup.BLOCKED;
            }
        }
        return IngredientLookup.NOT_FOUND;
    }

    private static final class IngredientLookup {

        private static final IngredientLookup NOT_FOUND = new IngredientLookup(false, null);
        private static final IngredientLookup BLOCKED = new IngredientLookup(true, null);

        private final boolean handled;
        private final Object ingredient;

        private IngredientLookup(boolean handled, Object ingredient) {
            this.handled = handled;
            this.ingredient = ingredient;
        }
    }
}
