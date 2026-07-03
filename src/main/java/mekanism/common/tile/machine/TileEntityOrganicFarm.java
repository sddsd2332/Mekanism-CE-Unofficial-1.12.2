package mekanism.common.tile.machine;

import mekanism.api.gas.Gas;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.machines.FarmRecipe;
import mekanism.common.tile.prefab.TileEntityFarmMachine;

import java.util.Map;

public class TileEntityOrganicFarm extends TileEntityFarmMachine<FarmRecipe> {
    public TileEntityOrganicFarm() {
        super("injection", MachineType.ORGANIC_FARM, BASE_TICKS_REQUIRED, BASE_GAS_PER_TICK);
    }

    @Override
    public Map<AdvancedMachineInput, FarmRecipe> getRecipes() {
        return Recipe.ORGANIC_FARM.get();
    }

    @Override
    public boolean isValidGas(Gas gas) {
        return Recipe.ORGANIC_FARM.containsRecipe(gas);
    }

    @Override
    public boolean upgradeableSecondaryEfficiency() {
        return true;
    }

    @Override
    public boolean useStatisticalMechanics() {
        return true;
    }

}
