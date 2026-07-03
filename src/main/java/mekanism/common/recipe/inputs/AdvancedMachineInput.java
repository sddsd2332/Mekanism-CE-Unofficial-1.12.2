package mekanism.common.recipe.inputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

public class AdvancedMachineInput extends MachineInput<AdvancedMachineInput> implements IWildInput<AdvancedMachineInput> {

    public ItemStack itemStack = ItemStack.EMPTY;

    public Gas gasType;

    public AdvancedMachineInput(ItemStack item, Gas gas) {
        itemStack = item;
        gasType = gas;
    }

    public AdvancedMachineInput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        itemStack = new ItemStack(nbtTags.getCompoundTag("input"));
        gasType = Gas.readFromNBT(nbtTags.getCompoundTag("gasType"));
    }

    @Override
    public AdvancedMachineInput copy() {
        return new AdvancedMachineInput(itemStack.copy(), gasType);
    }

    @Override
    public boolean isValid() {
        return !itemStack.isEmpty() && gasType != null;
    }

    public boolean useItem(IInventorySlot slot, boolean deplete) {
        if (inputContains(slot.getStack(), itemStack)) {
            if (deplete) {
                slot.shrinkStack(itemStack.getCount(), Action.EXECUTE);
            }
            return true;
        }
        return false;
    }

    public boolean useSecondary(IExtendedGasTank gasTank, int amountToUse, boolean deplete) {
        GasStack gas = gasTank.getGas();
        if (gas == null || gas.getGas() != gasType || gas.amount < amountToUse) {
            return false;
        }
        GasStack extracted = gasTank.extract(amountToUse, Action.get(deplete), AutomationType.INTERNAL);
        return extracted != null && extracted.amount == amountToUse;
    }

    public boolean matches(AdvancedMachineInput input) {
        return StackUtils.equalsWildcard(itemStack, input.itemStack) && input.itemStack.getCount() >= itemStack.getCount();
    }

    @Override
    public int hashIngredients() {
        return StackUtils.hashItemStack(itemStack) << 8 | gasType.getID();
    }

    @Override
    public boolean testEquality(AdvancedMachineInput other) {
        if (!isValid()) {
            return !other.isValid();
        }
        return MachineInput.inputItemMatches(itemStack, other.itemStack) && gasType.getID() == other.gasType.getID();
    }

    @Override
    public boolean isInstance(Object other) {
        return other instanceof AdvancedMachineInput;
    }

    @Override
    public AdvancedMachineInput wildCopy() {
        return new AdvancedMachineInput(new ItemStack(itemStack.getItem(), itemStack.getCount(), OreDictionary.WILDCARD_VALUE), gasType);
    }
}
