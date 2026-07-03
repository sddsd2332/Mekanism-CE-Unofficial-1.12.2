package mekanism.common.recipe.inputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

/**
 * An input of a gas, a fluid and an item for the pressurized reaction chamber
 */
public class NucleosynthesizerInput extends MachineInput<NucleosynthesizerInput> implements IWildInput<NucleosynthesizerInput> {

    private ItemStack theSolid = ItemStack.EMPTY;
    private GasStack theGas;

    public NucleosynthesizerInput(ItemStack solid, GasStack gas) {
        theSolid = solid;
        theGas = gas;
    }

    public NucleosynthesizerInput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        theSolid = new ItemStack(nbtTags.getCompoundTag("itemInput"));
        theGas = GasStack.readFromNBT(nbtTags.getCompoundTag("gasInput"));
    }

    /**
     * If this is a valid PressurizedReactants
     */
    @Override
    public boolean isValid() {
        return !theSolid.isEmpty() && theGas != null;
    }

    public boolean use(IInventorySlot slot, IExtendedGasTank gasTank, boolean deplete) {
        return use(slot, gasTank, deplete, deplete);
    }

    public boolean use(IInventorySlot slot, IExtendedGasTank gasTank, boolean deplete, boolean useGas) {
        if (meets(new NucleosynthesizerInput(slot.getStack(), gasTank.getGas()))) {
            if (deplete) {
                slot.shrinkStack(theSolid.getCount(), Action.EXECUTE);
            }
            if (useGas) {
                gasTank.extract(theGas.amount, Action.EXECUTE, AutomationType.INTERNAL);
            }
            return true;
        }
        return false;
    }

    /**
     * Whether or not this PressurizedReactants's ItemStack entry's item type is equal to the item type of the given item.
     *
     * @param stack - stack to check
     * @return if the stack's item type is contained in this PressurizedReactants
     */
    public boolean containsType(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() == 0) {
            return false;
        }
        return MachineInput.inputItemMatches(stack, theSolid);
    }


    /**
     * Whether or not this PressurizedReactants's GasStack entry's gas type is equal to the gas type of the given gas.
     *
     * @param stack - stack to check
     * @return if the stack's gas type is contained in this PressurizedReactants
     */
    public boolean containsType(GasStack stack) {
        if (stack == null || stack.amount == 0) {
            return false;
        }
        return stack.isGasEqual(theGas);
    }

    /**
     * Actual implementation of meetsInput(), performs the checks.
     *
     * @param input - input to check
     * @return if the input meets this input's requirements
     */
    public boolean meets(NucleosynthesizerInput input) {
        if (input == null || !input.isValid()) {
            return false;
        }
        if (!(StackUtils.equalsWildcard(input.theSolid, theSolid) && input.theGas.isGasEqual(theGas))) {
            return false;
        }
        return input.theSolid.getCount() >= theSolid.getCount() && input.theGas.amount >= theGas.amount;
    }

    @Override
    public NucleosynthesizerInput copy() {
        return new NucleosynthesizerInput(theSolid.copy(), theGas.copy());
    }

    public ItemStack getSolid() {
        return theSolid;
    }


    public GasStack getGas() {
        return theGas;
    }

    @Override
    public int hashIngredients() {
        return StackUtils.hashItemStack(theSolid) << 16 | theGas.hashCode();
    }

    @Override
    public boolean testEquality(NucleosynthesizerInput other) {
        return other.containsType(theSolid) && other.containsType(theGas);
    }

    @Override
    public boolean isInstance(Object other) {
        return other instanceof NucleosynthesizerInput;
    }

    @Override
    public NucleosynthesizerInput wildCopy() {
        return new NucleosynthesizerInput(new ItemStack(theSolid.getItem(), theSolid.getCount(), OreDictionary.WILDCARD_VALUE), theGas);
    }
}
