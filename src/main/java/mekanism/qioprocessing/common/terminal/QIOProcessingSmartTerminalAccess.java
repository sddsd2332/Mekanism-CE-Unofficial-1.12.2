package mekanism.qioprocessing.common.terminal;

import mekanism.qioprocessing.common.recipe.RecipeItemKey;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Smart-terminal portion of the legacy/new terminal bridge.
 */
public interface QIOProcessingSmartTerminalAccess extends QIOProcessingTerminalAccess {
    List<ItemStack> craftingGrid(int window);

    void setCraftingSlot(int window, int slot, ItemStack value);

    RecipeItemKey craftingOutputKey(int window);

    void markPatternConfigurationChanged();
}
