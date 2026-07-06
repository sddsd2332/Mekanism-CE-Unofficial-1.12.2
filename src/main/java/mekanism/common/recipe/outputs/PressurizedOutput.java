package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

public class PressurizedOutput extends MachineOutput<PressurizedOutput> {

    private ItemStack itemOutput = ItemStack.EMPTY;
    @Nullable
    private GasStack gasOutput;

    public PressurizedOutput(@Nullable ItemStack item, @Nullable GasStack gas) {
        itemOutput = item == null ? ItemStack.EMPTY : item;
        gasOutput = gas == null || gas.getGas() == null || gas.amount <= 0 ? null : gas;
    }

    public PressurizedOutput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        itemOutput = new ItemStack(nbtTags.getCompoundTag("itemOutput"));
        gasOutput = GasStack.readFromNBT(nbtTags.getCompoundTag("gasOutput"));
        if (gasOutput != null && (gasOutput.getGas() == null || gasOutput.amount <= 0)) {
            gasOutput = null;
        }
    }

    public boolean canFillTank(IExtendedGasTank tank) {
        if (gasOutput == null) {
            return true;
        }
        GasStack remainder = tank.insert(gasOutput, Action.SIMULATE, AutomationType.INTERNAL);
        return remainder == null || remainder.amount <= 0;
    }

    public boolean canAddProducts(IInventorySlot slot) {
        if (itemOutput.isEmpty()) {
            return true;
        }
        return slot.insertItem(itemOutput, Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    public void fillTank(IExtendedGasTank tank) {
        if (gasOutput != null) {
            tank.insert(gasOutput, Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    public void addProducts(IInventorySlot slot) {
        if (!itemOutput.isEmpty()) {
            slot.insertItem(itemOutput, Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    public boolean applyOutputs(IInventorySlot slot, IExtendedGasTank tank, boolean doEmit) {
        if (canFillTank(tank) && canAddProducts(slot)) {
            if (doEmit) {
                fillTank(tank);
                addProducts(slot);
            }
            return true;
        }
        return false;
    }

    public ItemStack getItemOutput() {
        return itemOutput;
    }

    @Nullable
    public GasStack getGasOutput() {
        return gasOutput;
    }

    @Override
    public PressurizedOutput copy() {
        return new PressurizedOutput(itemOutput.copy(), gasOutput == null ? null : gasOutput.copy());
    }
}
