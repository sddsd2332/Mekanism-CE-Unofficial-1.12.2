package mekanism.common.recipe.inputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.nbt.NBTTagCompound;

/**
 * An input of gasses for recipe use.
 *
 * @author aidancbrady
 */
public class ChemicalGasInput extends MachineInput<ChemicalGasInput> {

    /**
     * The left gas of this chemical input
     */
    public GasStack input;

    /**
     * The right gas of this chemical input
     */
    public GasStack uu;

    /**
     * Creates a chemical input with two defined gasses.
     *
     * @param left  - left gas
     * @param right - right gas
     */
    public ChemicalGasInput(GasStack left, GasStack right) {
        input = left;
        uu = right;
    }

    public ChemicalGasInput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        input = GasStack.readFromNBT(nbtTags.getCompoundTag("input"));
        uu = GasStack.readFromNBT(nbtTags.getCompoundTag("uu"));
    }

    public boolean useGas(IExtendedGasTank inputTank, IExtendedGasTank UUTank, boolean deplete, int scale) {
        int inputAmount = input.amount * scale;
        int uuAmount = uu.amount * scale;
        if (hasGas(inputTank, input, inputAmount) && hasGas(UUTank, uu, uuAmount)) {
            GasStack extracted = UUTank.extract(uuAmount, Action.get(deplete), AutomationType.INTERNAL);
            return extracted != null && extracted.amount == uuAmount;
        }
        return false;
    }

    private boolean hasGas(IExtendedGasTank tank, GasStack stack, int amount) {
        GasStack stored = tank.getGas();
        return stored != null && stored.isGasEqual(stack) && stored.amount >= amount;
    }

    /**
     * @return True if this is a valid ChemicalPair
     */
    @Override
    public boolean isValid() {
        return input != null && uu != null;
    }


    @Override
    public ChemicalGasInput copy() {
        return new ChemicalGasInput(input.copy(), uu.copy());
    }

    @Override
    public int hashIngredients() {
        return (input.hashCode() << 8 | uu.hashCode()) + (uu.hashCode() << 8 | input.hashCode());
    }

    @Override
    public boolean testEquality(ChemicalGasInput other) {
        if (!isValid()) {
            return !other.isValid();
        }
        return (other.input.hashCode() == input.hashCode() && other.uu.hashCode() == uu.hashCode());
    }


    @Override
    public boolean isInstance(Object other) {
        return other instanceof ChemicalGasInput;
    }
}
