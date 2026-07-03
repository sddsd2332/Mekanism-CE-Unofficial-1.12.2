package mekanism.common.recipe.inputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class RotaryInput extends MachineInput<RotaryInput> {

    @Nullable
    public FluidStack fluidInput;
    @Nullable
    public GasStack gasInput;

    public RotaryInput(@Nullable FluidStack fluidInput, @Nullable GasStack gasInput) {
        this.fluidInput = fluidInput;
        this.gasInput = gasInput;
    }

    public RotaryInput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        fluidInput = FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("fluidInput"));
        gasInput = GasStack.readFromNBT(nbtTags.getCompoundTag("gasInput"));
    }

    @Override
    public RotaryInput copy() {
        return new RotaryInput(fluidInput == null ? null : fluidInput.copy(), gasInput == null ? null : gasInput.copy());
    }

    @Override
    public boolean isValid() {
        return fluidInput != null || gasInput != null;
    }

    public boolean containsType(@Nullable FluidStack stack) {
        return stack != null && stack.amount > 0 && fluidInput != null && stack.isFluidEqual(fluidInput);
    }

    public boolean containsType(@Nullable GasStack stack) {
        return stack != null && stack.amount > 0 && gasInput != null && stack.isGasEqual(gasInput);
    }

    public boolean useFluid(IExtendedFluidTank fluidTank, boolean deplete, int scale) {
        if (fluidInput == null || scale <= 0) {
            return false;
        }
        int amount = fluidInput.amount * scale;
        FluidStack stored = fluidTank.getFluid();
        if (stored != null && stored.containsFluid(new FluidStack(fluidInput, amount))) {
            FluidStack extracted = fluidTank.extract(amount, Action.get(deplete), AutomationType.INTERNAL);
            return !ExtendedFluidHandlerUtils.isEmpty(extracted) && extracted.amount == amount;
        }
        return false;
    }

    public boolean useGas(IExtendedGasTank gasTank, boolean deplete, int scale) {
        if (gasInput == null || scale <= 0) {
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

    @Override
    public int hashIngredients() {
        int fluidHash = fluidInput == null || fluidInput.getFluid() == null ? 0 : fluidInput.getFluid().hashCode();
        int gasHash = gasInput == null ? 0 : gasInput.hashCode();
        return 31 * fluidHash + gasHash;
    }

    @Override
    public boolean testEquality(RotaryInput other) {
        if (!isValid()) {
            return !other.isValid();
        }
        boolean fluidMatches = fluidInput == null ? other.fluidInput == null : other.fluidInput != null && fluidInput.isFluidEqual(other.fluidInput);
        boolean gasMatches = gasInput == null ? other.gasInput == null : other.gasInput != null && gasInput.isGasEqual(other.gasInput);
        return fluidMatches && gasMatches;
    }

    @Override
    public boolean isInstance(Object other) {
        return other instanceof RotaryInput;
    }
}
