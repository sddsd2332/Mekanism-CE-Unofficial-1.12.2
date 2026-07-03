package mekanism.common.tile.machine;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.machines.SawmillRecipe;
import mekanism.common.tile.prefab.TileEntityChanceMachine;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TileEntityPrecisionSawmill extends TileEntityChanceMachine<SawmillRecipe> {

    public static final RecipeError NOT_ENOUGH_SPACE_SECONDARY_OUTPUT_ERROR = RecipeError.create();
    private static final List<RecipeError> TRACKED_ERROR_TYPES = Arrays.asList(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          NOT_ENOUGH_SPACE_SECONDARY_OUTPUT_ERROR,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );

    public TileEntityPrecisionSawmill() {
        super("sawmill", MachineType.PRECISION_SAWMILL, 200, TRACKED_ERROR_TYPES, NOT_ENOUGH_SPACE_SECONDARY_OUTPUT_ERROR);
    }

    @Override
    public Map<ItemStackInput, SawmillRecipe> getRecipes() {
        return Recipe.PRECISION_SAWMILL.get();
    }
}
