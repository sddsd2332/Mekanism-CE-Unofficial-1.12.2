package mekanism.client.recipe_viewer;

import mekanism.client.gui.GuiMekanism;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.Widget;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.client.jei.GuiElementHandler;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostIngredientConsumer;
import mekanism.common.lib.collection.LRU;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

public class GhostIngredientHandler {

    public static <INGREDIENT, TARGET> List<TARGET> getTargetsTyped(GuiMekanism<?> gui, INGREDIENT ingredient,
          BiFunction<IGhostIngredientConsumer, INGREDIENT, Object> supportedIngredient, TargetCreator<TARGET> targetCreator) {
        boolean hasTargets = false;
        int depth = 0;
        Map<Integer, List<TargetInfo<TARGET>>> depthBasedTargets = new TreeMap<>();
        Map<Integer, List<Rectangle>> layerIntersections = new TreeMap<>();
        List<TargetInfo<TARGET>> ghostTargets = getTargets(gui.children(), ingredient, supportedIngredient, targetCreator);
        if (!ghostTargets.isEmpty()) {
            depthBasedTargets.put(depth, ghostTargets);
            hasTargets = true;
        }
        for (LRU<GuiWindow>.LRUIterator iter = gui.getWindowsDescendingIterator(); iter.hasNext(); ) {
            GuiWindow window = iter.next();
            depth++;
            if (hasTargets) {
                List<Rectangle> areas = new ArrayList<>();
                areas.add(new Rectangle(window.getX(), window.getY(), window.getWidth(), window.getHeight()));
                areas.addAll(GuiElementHandler.getAreasFor(window.getX(), window.getY(), window.getWidth(), window.getHeight(), window.children()));
                layerIntersections.put(depth, areas);
            }
            ghostTargets = getTargets(window.children(), ingredient, supportedIngredient, targetCreator);
            if (!ghostTargets.isEmpty()) {
                depthBasedTargets.put(depth, ghostTargets);
                hasTargets = true;
            }
        }
        if (!hasTargets) {
            return Collections.emptyList();
        }
        List<TARGET> targets = new ArrayList<>();
        List<Rectangle> coveredArea = new ArrayList<>();
        for (Integer targetDepth : ((TreeMap<Integer, List<TargetInfo<TARGET>>>) depthBasedTargets).descendingKeySet()) {
            for (; depth > targetDepth; depth--) {
                List<Rectangle> covered = layerIntersections.get(depth);
                if (covered != null) {
                    coveredArea.addAll(covered);
                }
            }
            for (TargetInfo<TARGET> ghostTarget : depthBasedTargets.get(targetDepth)) {
                targets.addAll(ghostTarget.convertToTargets(coveredArea));
            }
        }
        return targets;
    }

    private static <INGREDIENT, TARGET> List<TargetInfo<TARGET>> getTargets(List<? extends Widget> children, INGREDIENT ingredient,
          BiFunction<IGhostIngredientConsumer, INGREDIENT, Object> supportedIngredient, TargetCreator<TARGET> targetCreator) {
        List<TargetInfo<TARGET>> ghostTargets = new ArrayList<>();
        for (Widget child : children) {
            if (!child.visible) {
                continue;
            }
            if (child instanceof GuiElement element) {
                ghostTargets.addAll(getTargets(element.children(), ingredient, supportedIngredient, targetCreator));
            }
            if (child instanceof IRecipeViewerGhostTarget ghostTarget) {
                IGhostIngredientConsumer ghostHandler = ghostTarget.getGhostHandler();
                if (ghostHandler != null) {
                    Object supported = supportedIngredient.apply(ghostHandler, ingredient);
                    if (supported != null) {
                        ghostTargets.add(new TargetInfo<>(ghostTarget, ghostHandler, child.getX(), child.getY(), child.getWidth(), child.getHeight(), supported,
                              targetCreator));
                    }
                }
            }
        }
        return ghostTargets;
    }

    private static void addVisibleAreas(List<Rectangle> visible, Rectangle area, List<Rectangle> coveredArea) {
        boolean intersected = false;
        int x = area.x;
        int x2 = x + area.width;
        int y = area.y;
        int y2 = y + area.height;
        int size = coveredArea.size();
        for (int i = 0; i < size; i++) {
            Rectangle covered = coveredArea.get(i);
            int cx = covered.x;
            int cx2 = cx + covered.width;
            int cy = covered.y;
            int cy2 = cy + covered.height;
            if (x < cx2 && x2 > cx && y < cy2 && y2 > cy) {
                intersected = true;
                if (x < cx || y < cy || x2 > cx2 || y2 > cy2) {
                    List<Rectangle> uncoveredArea = getVisibleArea(area, covered);
                    if (i + 1 == size) {
                        addAllNonEmpty(visible, uncoveredArea);
                    } else {
                        List<Rectangle> coveredAreas = coveredArea.subList(i + 1, size);
                        for (Rectangle visibleArea : uncoveredArea) {
                            addVisibleAreas(visible, visibleArea, coveredAreas);
                        }
                    }
                }
                break;
            }
        }
        if (!intersected && area.width > 0 && area.height > 0) {
            visible.add(area);
        }
    }

    private static void addAllNonEmpty(List<Rectangle> visible, List<Rectangle> areas) {
        for (Rectangle area : areas) {
            if (area.width > 0 && area.height > 0) {
                visible.add(area);
            }
        }
    }

    private static List<Rectangle> getVisibleArea(Rectangle area, Rectangle coveredArea) {
        int x = area.x;
        int x2 = x + area.width;
        int y = area.y;
        int y2 = y + area.height;
        int cx = coveredArea.x;
        int cx2 = cx + coveredArea.width;
        int cy = coveredArea.y;
        int cy2 = cy + coveredArea.height;
        boolean intersectsTop = y >= cy && y <= cy2;
        boolean intersectsLeft = x >= cx && x <= cx2;
        boolean intersectsBottom = y2 >= cy && y2 <= cy2;
        boolean intersectsRight = x2 >= cx && x2 <= cx2;
        List<Rectangle> areas = new ArrayList<>();
        if (intersectsTop && intersectsBottom) {
            if (intersectsLeft) {
                areas.add(new Rectangle(cx2, y, x2 - cx2, area.height));
            } else if (intersectsRight) {
                areas.add(new Rectangle(x, y, cx - x, area.height));
            } else {
                areas.add(new Rectangle(x, y, cx - x, area.height));
                areas.add(new Rectangle(cx2, y, x2 - cx2, area.height));
            }
        } else if (intersectsLeft && intersectsRight) {
            if (intersectsTop) {
                areas.add(new Rectangle(x, cy2, area.width, y2 - cy2));
            } else if (intersectsBottom) {
                areas.add(new Rectangle(x, y, area.width, cy - y));
            } else {
                areas.add(new Rectangle(x, y, area.width, cy - y));
                areas.add(new Rectangle(x, cy2, area.width, y2 - cy2));
            }
        } else if (intersectsTop && intersectsLeft) {
            areas.add(new Rectangle(x, cy2, area.width, y2 - cy2));
            areas.add(new Rectangle(cx2, y, x2 - cx2, cy2 - y));
        } else if (intersectsTop && intersectsRight) {
            areas.add(new Rectangle(x, y, cx - x, cy2 - y));
            areas.add(new Rectangle(x, cy2, area.width, y2 - cy2));
        } else if (intersectsBottom && intersectsLeft) {
            areas.add(new Rectangle(x, y, area.width, cy - y));
            areas.add(new Rectangle(cx2, cy, x2 - cx2, y2 - cy));
        } else if (intersectsBottom && intersectsRight) {
            areas.add(new Rectangle(x, y, area.width, cy - y));
            areas.add(new Rectangle(x, cy, cx - x, y2 - cy));
        } else if (intersectsTop) {
            areas.add(new Rectangle(x, y, cx - x, cy2 - y));
            areas.add(new Rectangle(x, cy2, area.width, y2 - cy2));
            areas.add(new Rectangle(cx2, y, x2 - cx2, cy2 - y));
        } else if (intersectsLeft) {
            areas.add(new Rectangle(x, y, area.width, cy - y));
            areas.add(new Rectangle(x, cy2, area.width, y2 - cy2));
            areas.add(new Rectangle(cx2, cy, x2 - cx2, coveredArea.height));
        } else if (intersectsBottom) {
            areas.add(new Rectangle(x, y, area.width, cy - y));
            areas.add(new Rectangle(x, cy, cx - x, y2 - cy));
            areas.add(new Rectangle(cx2, cy, x2 - cx2, y2 - cy));
        } else if (intersectsRight) {
            areas.add(new Rectangle(x, y, area.width, cy - y));
            areas.add(new Rectangle(x, cy, cx - x, coveredArea.height));
            areas.add(new Rectangle(x, cy2, area.width, y2 - cy2));
        } else {
            areas.add(new Rectangle(x, y, area.width, cy - y));
            areas.add(new Rectangle(x, cy, cx - x, coveredArea.height));
            areas.add(new Rectangle(x, cy2, area.width, y2 - cy2));
            areas.add(new Rectangle(cx2, cy, x2 - cx2, coveredArea.height));
        }
        return areas;
    }

    @FunctionalInterface
    public interface TargetCreator<TARGET> {

        TARGET create(IGhostIngredientConsumer handler, Object ingredient, Rectangle area);
    }

    private static class TargetInfo<TARGET> {

        private final TargetCreator<TARGET> targetCreator;
        private final IGhostIngredientConsumer ghostHandler;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final Object supported;

        private TargetInfo(IRecipeViewerGhostTarget ghostTarget, IGhostIngredientConsumer ghostHandler, int x, int y, int width, int height, Object supported,
              TargetCreator<TARGET> targetCreator) {
            this.ghostHandler = ghostHandler;
            this.targetCreator = targetCreator;
            this.supported = supported;
            int borderSize = ghostTarget.borderSize();
            this.x = x + borderSize;
            this.y = y + borderSize;
            this.width = width - 2 * borderSize;
            this.height = height - 2 * borderSize;
        }

        private List<TARGET> convertToTargets(List<Rectangle> coveredArea) {
            if (width <= 0 || height <= 0) {
                return Collections.emptyList();
            }
            List<Rectangle> visibleAreas = new ArrayList<>();
            addVisibleAreas(visibleAreas, new Rectangle(x, y, width, height), coveredArea);
            List<TARGET> list = new ArrayList<>(visibleAreas.size());
            for (Rectangle visibleArea : visibleAreas) {
                list.add(targetCreator.create(ghostHandler, supported, visibleArea));
            }
            return list;
        }
    }
}
