package mekanism.common.tile.machine;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.Upgrade;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.DoubleMachineInput;
import mekanism.common.recipe.machines.CombinerRecipe;
import mekanism.common.tile.prefab.TileEntityDoubleElectricMachine;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Map;

public class TileEntityCombiner extends TileEntityDoubleElectricMachine<CombinerRecipe> {

    private static final int STONE_GENERATOR_ENERGY_PER_ITEM = 10;

    public TileEntityCombiner() {
        super("combiner", MachineType.COMBINER, 200);
        setSupportedUpgrade(Upgrade.STONE_GENERATOR);
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        if (isUpgradeInstalled(Upgrade.STONE_GENERATOR) && extraSlot.isEmpty()) {
            generateSecondaryInput();
        }
    }

    private void generateSecondaryInput() {
        for (DoubleMachineInput input : getRecipes().keySet()) {
            if (canGenerateSecondaryInput(input)) {
                ItemStack stackToInsert = input.extraStack.copy();
                int energyCost = stackToInsert.getCount() * STONE_GENERATOR_ENERGY_PER_ITEM;
                if (extraSlot.insertItem(stackToInsert, Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
                    extraSlot.insertItem(stackToInsert, Action.EXECUTE, AutomationType.INTERNAL);
                    getMainEnergyContainer().extract(energyCost, Action.EXECUTE, AutomationType.INTERNAL);
                }
                return;
            }
        }
    }

    private boolean canGenerateSecondaryInput(DoubleMachineInput input) {
        return ItemHandlerHelper.canItemStacksStack(input.extraStack, new ItemStack(Blocks.COBBLESTONE)) && input.useItem(inputSlot, false);
    }

    @Override
    public Map<DoubleMachineInput, CombinerRecipe> getRecipes() {
        return Recipe.COMBINER.get();
    }
}
