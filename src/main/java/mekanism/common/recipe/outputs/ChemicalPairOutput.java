package mekanism.common.recipe.outputs;

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
public class ChemicalPairOutput extends MachineOutput<ChemicalPairOutput> {

    /**
     * The left gas of this chemical input
     */
    public GasStack leftGas;

    /**
     * The right gas of this chemical input
     */
    public GasStack rightGas;

    /**
     * Creates a chemical input with two defined gasses.
     *
     * @param left  - left gas
     * @param right - right gas
     */
    public ChemicalPairOutput(GasStack left, GasStack right) {
        leftGas = left;
        rightGas = right;
    }

    public ChemicalPairOutput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        leftGas = GasStack.readFromNBT(nbtTags.getCompoundTag("leftOutput"));
        rightGas = GasStack.readFromNBT(nbtTags.getCompoundTag("rightOutput"));
    }

    /**
     * @return True if this is a valid ChemicalPair
     */
    public boolean isValid() {
        return leftGas != null && rightGas != null;
    }

    /**
     * Whether or not the defined input contains the same gasses and at least the required amount of the defined gasses as this input.
     *
     * @param input - input to check
     * @return if the input meets this input's requirements
     */
    public boolean meetsInput(ChemicalPairOutput input) {
        return meets(input) || meets(input.swap());
    }

    /**
     * Swaps the right gas and left gas of this input.
     *
     * @return a swapped ChemicalInput
     */
    public ChemicalPairOutput swap() {
        return new ChemicalPairOutput(rightGas, leftGas);
    }

    public boolean applyOutputs(IExtendedGasTank leftTank, IExtendedGasTank rightTank, boolean doEmit, int scale) {
        if (insertPair(leftTank, rightTank, leftGas, rightGas, doEmit, scale)) {
            return true;
        } else if (insertPair(leftTank, rightTank, rightGas, leftGas, doEmit, scale)) {
            return true;
        }
        return false;
    }

    private boolean insertPair(IExtendedGasTank leftTank, IExtendedGasTank rightTank, GasStack leftOutput, GasStack rightOutput, boolean doEmit, int scale) {
        GasStack leftInsert = leftOutput.copy().withAmount(leftOutput.amount * scale);
        GasStack rightInsert = rightOutput.copy().withAmount(rightOutput.amount * scale);
        if (!canInsertAll(leftTank, leftInsert) || !canInsertAll(rightTank, rightInsert)) {
            return false;
        }
        if (doEmit) {
            leftTank.insert(leftInsert, Action.EXECUTE, AutomationType.INTERNAL);
            rightTank.insert(rightInsert, Action.EXECUTE, AutomationType.INTERNAL);
        }
        return true;
    }

    private boolean canInsertAll(IExtendedGasTank tank, GasStack stack) {
        GasStack remainder = tank.insert(stack, Action.SIMULATE, AutomationType.INTERNAL);
        return remainder == null || remainder.amount <= 0;
    }

    /**
     * Draws the needed amount of gas from each tank.
     *
     * @param leftTank  - left tank to draw from
     * @param rightTank - right tank to draw from
     */
    public void draw(IExtendedGasTank leftTank, IExtendedGasTank rightTank) {
        if (meets(new ChemicalPairOutput(leftTank.getGas(), rightTank.getGas()))) {
            leftTank.extract(leftGas.amount, Action.EXECUTE, AutomationType.INTERNAL);
            rightTank.extract(rightGas.amount, Action.EXECUTE, AutomationType.INTERNAL);
        } else if (meets(new ChemicalPairOutput(rightTank.getGas(), leftTank.getGas()))) {
            leftTank.extract(rightGas.amount, Action.EXECUTE, AutomationType.INTERNAL);
            rightTank.extract(leftGas.amount, Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    /**
     * Whether or not one of this ChemicalInput's GasStack entry's gas type is equal to the gas type of the given gas.
     *
     * @param stack - stack to check
     * @return if the stack's gas type is contained in this ChemicalInput
     */
    public boolean containsType(GasStack stack) {
        if (stack == null || stack.amount == 0) {
            return false;
        }
        return stack.isGasEqual(leftGas) || stack.isGasEqual(rightGas);
    }

    /**
     * Actual implementation of meetsInput(), performs the checks.
     *
     * @param input - input to check
     * @return if the input meets this input's requirements
     */
    private boolean meets(ChemicalPairOutput input) {
        if (input == null || !input.isValid()) {
            return false;
        }
        if (input.leftGas.getGas() != leftGas.getGas() || input.rightGas.getGas() != rightGas.getGas()) {
            return false;
        }
        return input.leftGas.amount >= leftGas.amount && input.rightGas.amount >= rightGas.amount;
    }

    @Override
    public ChemicalPairOutput copy() {
        return new ChemicalPairOutput(leftGas.copy(), rightGas.copy());
    }
}
