package mekanism.client.gui.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.recipe.machines.FarmRecipe;
import mekanism.common.tile.machine.TileEntityOrganicFarm;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiOrganicFarm extends GuiFarmMachine<FarmRecipe, TileEntityOrganicFarm> {

    public GuiOrganicFarm(InventoryPlayer inventory, TileEntityOrganicFarm tile) {
        super(inventory, tile);
    }

    @Override
    protected IRecipeViewerRecipeType<?>[] getRecipeViewerRecipeTypes() {
        return new IRecipeViewerRecipeType[]{RecipeViewerRecipeType.ORGANIC_FARM};
    }
}