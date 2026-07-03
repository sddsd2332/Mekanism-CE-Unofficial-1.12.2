package mekanism.common.recipe.machines;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.MekanismFluids;
import mekanism.common.recipe.cache.IConstantGasRecipe;
import mekanism.common.recipe.inputs.ItemStackInput;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.outputs.GasOutput;
import net.minecraft.item.ItemStack;

public class DissolutionRecipe extends MachineRecipe<ItemStackInput, GasOutput, DissolutionRecipe> implements IConstantGasRecipe<GasStack> {

    public DissolutionRecipe(ItemStackInput input, GasOutput output) {
        super(input, output);
    }

    public DissolutionRecipe(ItemStack input, GasStack output) {
        this(new ItemStackInput(input), new GasOutput(output));
    }

    @Override
    public ItemStack getItemInput() {
        return getInput().ingredient;
    }

    @Override
    public GasStack getGasInput() {
        return new GasStack(MekanismFluids.SulfuricAcid, 1);
    }

    @Override
    public boolean test(ItemStack itemInput, GasStack gasInput) {
        return MachineInput.inputContains(itemInput, getInput().ingredient) && gasInput != null && gasInput.getGas() == MekanismFluids.SulfuricAcid;
    }

    @Override
    public GasStack getOutput(ItemStack itemInput, GasStack gasInput) {
        return getOutput().output.copy();
    }

    @Override
    public boolean isOutputEmpty(GasStack output) {
        return output == null || output.amount <= 0;
    }

    public boolean canOperate(IInventorySlot inputSlot, IExtendedGasTank outputTank) {
        return getInput().useItemStackFromSlot(inputSlot, false) && getOutput().applyOutputs(outputTank, false, 1);
    }

    @Override
    public DissolutionRecipe copy() {
        return new DissolutionRecipe(getInput().copy(), getOutput().copy());
    }
}
