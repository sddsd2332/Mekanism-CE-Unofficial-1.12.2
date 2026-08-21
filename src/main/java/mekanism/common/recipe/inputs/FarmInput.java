package mekanism.common.recipe.inputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;

/**
 * Organic Farm input: one item ingredient and exactly one gas or fluid ingredient.
 */
public class FarmInput extends MachineInput<FarmInput> implements IWildInput<FarmInput> {

    public ItemStack itemStack = ItemStack.EMPTY;
    @Nullable
    public GasStack gasInput;
    @Nullable
    public FluidStack fluidInput;

    public FarmInput(ItemStack itemStack, Gas gas) {
        this(itemStack, gas == null ? null : new GasStack(gas, 1), null);
    }

    public FarmInput(ItemStack itemStack, Fluid fluid) {
        this(itemStack, null, fluid == null ? null : new FluidStack(fluid, 1));
    }

    public FarmInput(ItemStack itemStack, GasStack gasInput) {
        this(itemStack, gasInput, null);
    }

    public FarmInput(ItemStack itemStack, FluidStack fluidInput) {
        this(itemStack, null, fluidInput);
    }

    private FarmInput(ItemStack itemStack, @Nullable GasStack gasInput, @Nullable FluidStack fluidInput) {
        this.itemStack = itemStack == null ? ItemStack.EMPTY : itemStack.copy();
        this.gasInput = gasInput == null ? null : gasInput.copy();
        this.fluidInput = fluidInput == null ? null : fluidInput.copy();
    }

    public FarmInput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        String itemKey = nbtTags.hasKey("itemInput") ? "itemInput" : "input";
        itemStack = new ItemStack(nbtTags.getCompoundTag(itemKey));
        gasInput = GasStack.readFromNBT(nbtTags.getCompoundTag("gasInput"));
        if (gasInput == null && nbtTags.hasKey("gasType")) {
            Gas gas = Gas.readFromNBT(nbtTags.getCompoundTag("gasType"));
            gasInput = gas == null ? null : new GasStack(gas, 1);
        }
        fluidInput = FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("fluidInput"));
    }

    @Override
    public FarmInput copy() {
        return new FarmInput(itemStack, gasInput, fluidInput);
    }

    @Override
    public boolean isValid() {
        return !itemStack.isEmpty() && (isGasInput() ^ isFluidInput());
    }

    public boolean isGasInput() {
        return gasInput != null && gasInput.getGas() != null && gasInput.amount > 0;
    }

    public boolean isFluidInput() {
        return fluidInput != null && fluidInput.getFluid() != null && fluidInput.amount > 0;
    }

    public boolean containsType(ItemStack stack) {
        return stack != null && MachineInput.inputContains(stack, itemStack);
    }

    public boolean containsType(@Nullable GasStack stack) {
        return isGasInput() && stack != null && stack.amount > 0 && stack.isGasEqual(gasInput);
    }

    public boolean containsType(@Nullable FluidStack stack) {
        return isFluidInput() && stack != null && stack.amount > 0 && stack.isFluidEqual(fluidInput);
    }

    public boolean matches(ItemStack item, @Nullable GasStack gas) {
        return MachineInput.inputContains(item, itemStack) && containsType(gas) && gas.amount >= gasInput.amount;
    }

    public boolean matches(ItemStack item, @Nullable FluidStack fluid) {
        return MachineInput.inputContains(item, itemStack) && containsType(fluid) && fluid.amount >= fluidInput.amount;
    }

    public boolean useItem(IInventorySlot slot, boolean deplete) {
        if (!MachineInput.inputContains(slot.getStack(), itemStack)) {
            return false;
        }
        if (deplete) {
            slot.shrinkStack(itemStack.getCount(), Action.EXECUTE);
        }
        return true;
    }

    public boolean useGas(IExtendedGasTank gasTank, int scale, boolean deplete) {
        if (!isGasInput() || scale <= 0) {
            return false;
        }
        int amount = gasInput.amount * scale;
        GasStack stored = gasTank.getGas();
        if (stored == null || !stored.isGasEqual(gasInput) || stored.amount < amount) {
            return false;
        }
        GasStack extracted = gasTank.extract(amount, Action.get(deplete), AutomationType.INTERNAL);
        return extracted != null && extracted.amount == amount;
    }

    public boolean useFluid(IExtendedFluidTank fluidTank, int scale, boolean deplete) {
        if (!isFluidInput() || scale <= 0) {
            return false;
        }
        int amount = fluidInput.amount * scale;
        FluidStack stored = fluidTank.getFluid();
        if (stored == null || !stored.isFluidEqual(fluidInput) || stored.amount < amount) {
            return false;
        }
        FluidStack extracted = fluidTank.extract(amount, Action.get(deplete), AutomationType.INTERNAL);
        return !ExtendedFluidHandlerUtils.isEmpty(extracted) && extracted.amount == amount;
    }

    @Override
    public int hashIngredients() {
        int mediumHash;
        if (isGasInput()) {
            mediumHash = 31 * gasInput.getGas().getID() + 1;
        } else if (isFluidInput()) {
            mediumHash = 31 * fluidInput.getFluid().getName().hashCode() + 2;
        } else {
            mediumHash = 0;
        }
        return 31 * StackUtils.hashItemStack(itemStack) + mediumHash;
    }

    @Override
    public boolean testEquality(FarmInput other) {
        if (!isValid()) {
            return !other.isValid();
        }
        if (!MachineInput.inputItemMatches(itemStack, other.itemStack)) {
            return false;
        }
        if (isGasInput()) {
            return other.isGasInput() && gasInput.isGasEqual(other.gasInput);
        }
        return other.isFluidInput() && fluidInput.isFluidEqual(other.fluidInput);
    }

    @Override
    public boolean isInstance(Object other) {
        return other instanceof FarmInput;
    }

    @Override
    public FarmInput wildCopy() {
        ItemStack wildcard = new ItemStack(itemStack.getItem(), itemStack.getCount(), OreDictionary.WILDCARD_VALUE);
        return new FarmInput(wildcard, gasInput, fluidInput);
    }
}
