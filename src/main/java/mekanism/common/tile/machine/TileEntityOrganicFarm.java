package mekanism.common.tile.machine;

import mekanism.api.Coord4D;
import mekanism.api.gas.Gas;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.machines.FarmRecipe;
import mekanism.common.tile.prefab.TileEntityFarmMachine;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.Fluid;

import java.util.Map;
import java.util.function.BiConsumer;

public class TileEntityOrganicFarm extends TileEntityFarmMachine<FarmRecipe> implements IBoundingBlock {
    public TileEntityOrganicFarm() {
        super("injection", MachineType.ORGANIC_FARM, BASE_TICKS_REQUIRED, BASE_GAS_PER_TICK);
    }

    @Override
    public Map<FarmInput, FarmRecipe> getRecipes() {
        return Recipe.ORGANIC_FARM.get();
    }

    @Override
    public boolean isValidGas(Gas gas) {
        return Recipe.ORGANIC_FARM.containsRecipe(gas);
    }

    @Override
    public boolean isValidFluid(Fluid fluid) {
        return Recipe.ORGANIC_FARM.containsRecipe(fluid);
    }

    @Override
    public boolean upgradeableSecondaryEfficiency() {
        return true;
    }

    @Override
    public boolean useStatisticalMechanics() {
        return true;
    }

    @Override
    public void collectBoundingBlocks(BiConsumer<BlockPos, Boolean> consumer) {
        consumer.accept(getPos().up(), false);
    }

    @Override
    public void onPlace() {
        tryPlaceBoundingBlocks(world, Coord4D.get(this));
    }

    @Override
    public void onBreak() {
        removeBoundingBlocks(world, getPos());
    }

}
