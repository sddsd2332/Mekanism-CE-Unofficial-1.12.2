package mekanism.common.tile.machine;

import mekanism.api.gas.Gas;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.machines.InjectionRecipe;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;

import java.util.Map;

public class TileEntityChemicalInjectionChamber extends TileEntityAdvancedElectricMachine<InjectionRecipe> {

    public TileEntityChemicalInjectionChamber() {
        super("injection", MachineType.CHEMICAL_INJECTION_CHAMBER, BASE_TICKS_REQUIRED, BASE_GAS_PER_TICK);
    }

    @Override
    public Map<AdvancedMachineInput, InjectionRecipe> getRecipes() {
        return Recipe.CHEMICAL_INJECTION_CHAMBER.get();
    }

    @Override
    public boolean isValidGas(Gas gas) {
        return Recipe.CHEMICAL_INJECTION_CHAMBER.containsRecipe(gas);
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
