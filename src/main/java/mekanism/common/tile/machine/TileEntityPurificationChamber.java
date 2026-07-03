package mekanism.common.tile.machine;

import mekanism.api.gas.Gas;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.machines.PurificationRecipe;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;

import java.util.Map;

public class TileEntityPurificationChamber extends TileEntityAdvancedElectricMachine<PurificationRecipe> {

    public TileEntityPurificationChamber() {
        super("purifier", MachineType.PURIFICATION_CHAMBER, BASE_TICKS_REQUIRED, BASE_GAS_PER_TICK);
    }

    @Override
    public Map<AdvancedMachineInput, PurificationRecipe> getRecipes() {
        return Recipe.PURIFICATION_CHAMBER.get();
    }

    @Override
    public boolean isValidGas(Gas gas) {
        return Recipe.PURIFICATION_CHAMBER.containsRecipe(gas);
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
