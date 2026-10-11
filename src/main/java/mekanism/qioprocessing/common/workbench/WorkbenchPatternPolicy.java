package mekanism.qioprocessing.common.workbench;

import mekanism.qioprocessing.common.recipe.RecipeDeclaration;
import mekanism.qioprocessing.common.recipe.RecipeIngredient;
import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

/**
 * Pure candidate-policy check. Admission never substitutes for server recipe validation.
 */
public final class WorkbenchPatternPolicy {
    private static final int WILDCARD_METADATA = 32767;

    private WorkbenchPatternPolicy() {
    }

    public static boolean admits(WorkbenchRoute route, RecipeDeclaration recipe, List<RecipeItemKey> grid) {
        if (route == null || !route.enabled() || !route.validChoices(recipe) || grid == null || grid.size() != 9)
            return false;
        // A verified custom recipe may accept more than its display declaration enumerates.
        if (route.choices().isEmpty()) return true;
        if (recipe.layout() == RecipeDeclaration.Layout.SHAPED) {
            for (int y = 0; y <= 3 - recipe.height(); y++) {
                for (int x = 0; x <= 3 - recipe.width(); x++) {
                    if (shaped(route, recipe, grid, x, y, false) || shaped(route, recipe, grid, x, y, true))
                        return true;
                }
            }
            return false;
        }
        if (recipe.layout() == RecipeDeclaration.Layout.SHAPELESS) {
            int occupied = 0, required = 0;
            for (RecipeItemKey key : grid) if (key != null) occupied++;
            for (RecipeIngredient input : recipe.inputs()) if (!input.isEmptySlot()) required++;
            return occupied == required && assign(route, recipe, grid, 0, 0);
        }
        for (int slot = 0; slot < 9; slot++) {
            if (slot >= recipe.inputs().size()) {
                if (grid.get(slot) != null) return false;
            } else if (!accepts(route, recipe, slot, grid.get(slot))) return false;
        }
        return true;
    }

    private static boolean shaped(WorkbenchRoute route, RecipeDeclaration recipe, List<RecipeItemKey> grid,
                                  int offsetX, int offsetY, boolean mirrored) {
        for (int physical = 0; physical < 9; physical++) {
            int x = physical % 3 - offsetX, y = physical / 3 - offsetY;
            if (x < 0 || y < 0 || x >= recipe.width() || y >= recipe.height()) {
                if (grid.get(physical) != null) return false;
            } else {
                int logical = y * recipe.width() + (mirrored ? recipe.width() - 1 - x : x);
                if (!accepts(route, recipe, logical, grid.get(physical))) return false;
            }
        }
        return true;
    }

    private static boolean assign(WorkbenchRoute route, RecipeDeclaration recipe, List<RecipeItemKey> grid,
                                  int logical, int used) {
        if (logical == recipe.inputs().size()) return true;
        if (recipe.inputs().get(logical).isEmptySlot()) return assign(route, recipe, grid, logical + 1, used);
        for (int physical = 0; physical < 9; physical++) {
            int bit = 1 << physical;
            if ((used & bit) == 0 && grid.get(physical) != null && accepts(route, recipe, logical, grid.get(physical)) &&
                    assign(route, recipe, grid, logical + 1, used | bit)) return true;
        }
        return false;
    }

    private static boolean accepts(WorkbenchRoute route, RecipeDeclaration recipe, int logical, RecipeItemKey actual) {
        RecipeIngredient input = recipe.inputs().get(logical);
        if (input.isEmptySlot()) return actual == null;
        if (actual == null) return false;
        List<RecipeItemKey> candidates = route.candidates(recipe, logical);
        if (candidates.isEmpty()) return !route.choices().containsKey(logical);
        for (RecipeItemKey candidate : candidates) {
            if (candidate.equals(actual)) return true;
            if (candidate.metadata() == WILDCARD_METADATA && candidate.itemId().equals(actual.itemId())) {
                NBTTagCompound expected = candidate.displayNBT(), concrete = actual.displayNBT();
                expected.removeTag("Damage");
                concrete.removeTag("Damage");
                if (expected.equals(concrete)) return true;
            }
        }
        return false;
    }
}
