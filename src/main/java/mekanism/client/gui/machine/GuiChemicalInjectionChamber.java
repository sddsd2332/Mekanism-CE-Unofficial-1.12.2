package mekanism.client.gui.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.recipe.machines.InjectionRecipe;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiChemicalInjectionChamber extends GuiAdvancedElectricMachine<InjectionRecipe, TileEntityAdvancedElectricMachine<InjectionRecipe>> {

    public GuiChemicalInjectionChamber(InventoryPlayer inventory, TileEntityAdvancedElectricMachine<InjectionRecipe> tile) {
        super(inventory, tile);
    }

    @Override
    protected IRecipeViewerRecipeType<?>[] getRecipeViewerRecipeTypes() {
        return new IRecipeViewerRecipeType[]{RecipeViewerRecipeType.INJECTING};
    }
}