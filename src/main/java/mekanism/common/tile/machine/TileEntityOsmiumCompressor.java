package mekanism.common.tile.machine;

import mekanism.api.gas.Gas;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.machines.OsmiumCompressorRecipe;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;

import java.util.Map;

public class TileEntityOsmiumCompressor extends TileEntityAdvancedElectricMachine<OsmiumCompressorRecipe> {

    public TileEntityOsmiumCompressor() {
        super("compressor", MachineType.OSMIUM_COMPRESSOR, BASE_TICKS_REQUIRED, BASE_GAS_PER_TICK);
    }

    @Override
    public Map<AdvancedMachineInput, OsmiumCompressorRecipe> getRecipes() {
        return Recipe.OSMIUM_COMPRESSOR.get();
    }

    @Override
    public boolean isValidGas(Gas gas) {
        return Recipe.OSMIUM_COMPRESSOR.containsRecipe(gas);
    }

}
