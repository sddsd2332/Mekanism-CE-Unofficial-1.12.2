package mekanism.client.gui.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.recipe.machines.CellSeparatorRecipe;
import mekanism.common.tile.machine.TileEntityCellSeparator;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiCellSeparator extends GuiChanceMachine<CellSeparatorRecipe, TileEntityCellSeparator> {

    public GuiCellSeparator(InventoryPlayer inventory, TileEntityCellSeparator tile) {
        super(inventory, tile);
    }

    @Override
    protected IRecipeViewerRecipeType<?>[] getRecipeViewerRecipeTypes() {
        return new IRecipeViewerRecipeType[]{RecipeViewerRecipeType.CELL_SEPARATING};
    }
}