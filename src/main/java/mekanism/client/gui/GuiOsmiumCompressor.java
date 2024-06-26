package mekanism.client.gui;

import mekanism.common.recipe.machines.OsmiumCompressorRecipe;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiOsmiumCompressor extends GuiAdvancedElectricMachine<OsmiumCompressorRecipe> {

    public GuiOsmiumCompressor(InventoryPlayer inventory, TileEntityAdvancedElectricMachine<OsmiumCompressorRecipe> tile) {
        super(inventory, tile);
    }

}
