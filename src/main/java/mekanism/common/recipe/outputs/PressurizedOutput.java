package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class PressurizedOutput extends MachineOutput<PressurizedOutput> {

    private ItemStack itemOutput = ItemStack.EMPTY;
    private GasStack gasOutput;

    public PressurizedOutput(ItemStack item, GasStack gas) {
        itemOutput = item;
        gasOutput = gas;
    }

    public PressurizedOutput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        itemOutput = new ItemStack(nbtTags.getCompoundTag("itemOutput"));
        gasOutput = GasStack.readFromNBT(nbtTags.getCompoundTag("gasOutput"));
    }

    public boolean canFillTank(IExtendedGasTank tank) {
        GasStack remainder = tank.insert(gasOutput, Action.SIMULATE, AutomationType.INTERNAL);
        return remainder == null || remainder.amount <= 0;
    }

    public boolean canAddProducts(IInventorySlot slot) {
        return slot.insertItem(itemOutput, Action.SIMULATE, AutomationType.INTERNAL).isEmpty();
    }

    public void fillTank(IExtendedGasTank tank) {
        tank.insert(gasOutput, Action.EXECUTE, AutomationType.INTERNAL);
    }

    public void addProducts(IInventorySlot slot) {
        slot.insertItem(itemOutput, Action.EXECUTE, AutomationType.INTERNAL);
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

    public GasStack getGasOutput() {
        return gasOutput;
    }

    @Override
    public PressurizedOutput copy() {
        return new PressurizedOutput(itemOutput.copy(), gasOutput.copy());
    }
}
