package mekanism.common.recipe.inputs;

import mekanism.api.Action;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

public class DoubleMachineInput extends MachineInput<DoubleMachineInput> implements IWildInput<DoubleMachineInput> {

    public ItemStack itemStack = ItemStack.EMPTY;
    public ItemStack extraStack = ItemStack.EMPTY;

    public DoubleMachineInput(ItemStack item, ItemStack extra) {
        itemStack = item;
        extraStack = extra;
    }

    public DoubleMachineInput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        itemStack = new ItemStack(nbtTags.getCompoundTag("input"));
        extraStack = new ItemStack(nbtTags.getCompoundTag("extra"));
    }

    @Override
    public DoubleMachineInput copy() {
        return new DoubleMachineInput(itemStack.copy(), extraStack.copy());
    }

    @Override
    public boolean isValid() {
        return !itemStack.isEmpty() && !extraStack.isEmpty();
    }

    protected boolean useItemInternal(ItemStack stack, IInventorySlot slot, boolean deplete) {
        if (inputContains(slot.getStack(), stack)) {
            if (deplete) {
                slot.shrinkStack(stack.getCount(), Action.EXECUTE);
            }
            return true;
        }
        return false;
    }

    public boolean useItem(IInventorySlot slot, boolean deplete) {
        return useItemInternal(itemStack, slot, deplete);
    }

    public boolean useExtra(IInventorySlot slot, boolean deplete) {
        return useItemInternal(extraStack, slot, deplete);
    }

    public boolean matches(DoubleMachineInput input) {
        return StackUtils.equalsWildcard(itemStack, input.itemStack) && input.itemStack.getCount() >= itemStack.getCount()
                && StackUtils.equalsWildcard(extraStack, input.extraStack) && input.extraStack.getCount() >= extraStack.getCount();
    }

    @Override
    public int hashIngredients() {
        return StackUtils.hashItemStack(itemStack) ^ Integer.reverse(StackUtils.hashItemStack(extraStack));
    }

    @Override
    public boolean testEquality(DoubleMachineInput other) {
        if (!isValid()) {
            return !other.isValid();
        }
        return MachineInput.inputItemMatches(itemStack, other.itemStack) && MachineInput.inputItemMatches(extraStack, other.extraStack);
    }

    @Override
    public boolean isInstance(Object other) {
        return other instanceof DoubleMachineInput;
    }

    @Override
    public DoubleMachineInput wildCopy() {
        return new DoubleMachineInput(new ItemStack(itemStack.getItem(), itemStack.getCount(), OreDictionary.WILDCARD_VALUE),
                new ItemStack(extraStack.getItem(), extraStack.getCount(), OreDictionary.WILDCARD_VALUE));
    }
}
