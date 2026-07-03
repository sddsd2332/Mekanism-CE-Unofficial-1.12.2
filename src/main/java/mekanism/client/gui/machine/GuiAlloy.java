package mekanism.client.gui.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.recipe.machines.AlloyRecipe;
import mekanism.common.tile.prefab.TileEntityDoubleElectricMachine;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiAlloy extends GuiDoubleElectricMachine<AlloyRecipe, TileEntityDoubleElectricMachine<AlloyRecipe>> {

    public GuiAlloy(InventoryPlayer inventory, TileEntityDoubleElectricMachine<AlloyRecipe> tile) {
        super(inventory, tile);
    }

    @Override
    protected IRecipeViewerRecipeType<?>[] getRecipeViewerRecipeTypes() {
        return new IRecipeViewerRecipeType[]{RecipeViewerRecipeType.ALLOYING};
    }
}