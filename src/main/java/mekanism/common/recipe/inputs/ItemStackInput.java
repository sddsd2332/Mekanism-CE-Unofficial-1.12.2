package mekanism.common.recipe.inputs;

import mekanism.api.Action;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

public class ItemStackInput extends MachineInput<ItemStackInput> implements IWildInput<ItemStackInput> {

    public ItemStack ingredient = ItemStack.EMPTY;
    private ItemStackInput wildVersion = null;
    private int ingredientHash;

    public ItemStackInput(ItemStack stack) {
        ingredient = stack;
        ingredientHash = hashIngredients();
    }

    public ItemStackInput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        ingredient = new ItemStack(nbtTags.getCompoundTag("input"));
        ingredientHash = hashIngredients();
        wildVersion = null;
    }

    @Override
    public ItemStackInput copy() {
        return new ItemStackInput(ingredient.copy());
    }

    @Override
    public boolean isValid() {
        return !ingredient.isEmpty();
    }

    @Override
    public ItemStackInput wildCopy() {
        if (wildVersion == null) {
            if (ingredient.getMetadata() != OreDictionary.WILDCARD_VALUE) {
                this.wildVersion = new ItemStackInput(new ItemStack(ingredient.getItem(), ingredient.getCount(), OreDictionary.WILDCARD_VALUE));
            } else {
                this.wildVersion = this;
            }
        }
        return this.wildVersion;
    }

    public boolean useItemStackFromSlot(IInventorySlot slot, boolean deplete) {
        if (inputContains(slot.getStack(), ingredient)) {
            if (deplete) {
                slot.shrinkStack(ingredient.getCount(), Action.EXECUTE);
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashIngredients() {
        return StackUtils.hashItemStack(ingredient);
    }

    @Override
    public boolean testEquality(ItemStackInput other) {
        return MachineInput.inputItemMatches(ingredient, other.ingredient);
    }

    @Override
    public boolean isInstance(Object other) {
        return other instanceof ItemStackInput;
    }

    @Override
    public int hashCode() {
        return ingredientHash;
    }
}
