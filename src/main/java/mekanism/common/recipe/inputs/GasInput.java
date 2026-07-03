package mekanism.common.recipe.inputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.nbt.NBTTagCompound;

public class GasInput extends MachineInput<GasInput> {

    public GasStack ingredient;

    public GasInput(GasStack stack) {
        ingredient = stack;
    }

    public GasInput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        ingredient = GasStack.readFromNBT(nbtTags.getCompoundTag("input"));
    }

    @Override
    public GasInput copy() {
        return new GasInput(ingredient.copy());
    }

    @Override
    public boolean isValid() {
        return ingredient != null;
    }

    public boolean useGas(IExtendedGasTank gasTank, boolean deplete, int scale) {
        int amount = ingredient.amount * scale;
        GasStack gas = gasTank.getGas();
        if (gas == null || !gas.isGasEqual(ingredient) || gas.amount < amount) {
            return false;
        }
        GasStack extracted = gasTank.extract(amount, Action.get(deplete), AutomationType.INTERNAL);
        return extracted != null && extracted.amount == amount;
    }

    @Override
    public int hashIngredients() {
        return ingredient.hashCode();
    }

    @Override
    public boolean testEquality(GasInput other) {
        if (!isValid()) {
            return !other.isValid();
        }
        return other.ingredient.hashCode() == ingredient.hashCode();
    }

    @Override
    public boolean isInstance(Object other) {
        return other instanceof GasInput;
    }
}
