package mekanism.common.recipe.machines;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.recipe.outputs.ItemStackOutput;
import net.minecraft.item.ItemStack;

public class CrystallizerRecipe extends MachineRecipe<GasInput, ItemStackOutput, CrystallizerRecipe> {

    public CrystallizerRecipe(GasInput input, ItemStackOutput output) {
        super(input, output);
    }

    public CrystallizerRecipe(GasStack input, ItemStack output) {
        this(new GasInput(input), new ItemStackOutput(output));
    }

    public boolean canOperate(IExtendedGasTank gasTank, IInventorySlot outputSlot) {
        return getInput().useGas(gasTank, false, 1) && getOutput().applyOutputs(outputSlot, false);
    }

    @Override
    public CrystallizerRecipe copy() {
        return new CrystallizerRecipe(getInput().copy(), getOutput().copy());
    }
}
