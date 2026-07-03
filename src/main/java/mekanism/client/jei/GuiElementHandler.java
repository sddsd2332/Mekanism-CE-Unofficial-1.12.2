package mekanism.client.jei;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.Widget;
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
        return null;
    }
}
