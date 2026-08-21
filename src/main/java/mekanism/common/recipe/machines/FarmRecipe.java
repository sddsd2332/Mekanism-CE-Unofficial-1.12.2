package mekanism.common.recipe.machines;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.recipes.FarmChanceOutput;
import mekanism.common.recipe.inputs.FarmInput;
import mekanism.common.recipe.outputs.FarmOutput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class FarmRecipe extends FarmMachineRecipe<FarmRecipe> {

    public FarmRecipe(FarmInput input, FarmOutput output) {
        super(input, output);
    }

    public FarmRecipe(ItemStack itemStack, Gas gas, ItemStack primaryOutput, ItemStack secondaryOutput, double chance) {
        this(new FarmInput(itemStack, gas), new FarmOutput(primaryOutput, secondaryOutput, chance));
    }

    public FarmRecipe(ItemStack itemStack, Gas gas, ItemStack primaryOutput) {
        this(new FarmInput(itemStack, gas), new FarmOutput(primaryOutput));
    }

    public FarmRecipe(ItemStack itemStack, GasStack gas, ItemStack primaryOutput, List<FarmChanceOutput> chanceOutputs) {
        this(new FarmInput(itemStack, gas), new FarmOutput(primaryOutput, chanceOutputs));
    }

    public FarmRecipe(ItemStack itemStack, Fluid fluid, ItemStack primaryOutput, ItemStack secondaryOutput, double chance) {
        this(new FarmInput(itemStack, fluid), new FarmOutput(primaryOutput, secondaryOutput, chance));
    }

    public FarmRecipe(ItemStack itemStack, Fluid fluid, ItemStack primaryOutput) {
        this(new FarmInput(itemStack, fluid), new FarmOutput(primaryOutput));
    }

    public FarmRecipe(ItemStack itemStack, FluidStack fluid, ItemStack primaryOutput, List<FarmChanceOutput> chanceOutputs) {
        this(new FarmInput(itemStack, fluid), new FarmOutput(primaryOutput, chanceOutputs));
    }

    @Override
    public FarmRecipe copy() {
        return new FarmRecipe(getInput().copy(), getOutput().copy());
    }
}
