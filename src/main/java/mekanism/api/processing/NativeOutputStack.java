package mekanism.api.processing;

import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

/**
 * Immutable, operation-local native resource. No codec normalization/round trip on the transfer hot path.
 */
public final class NativeOutputStack {
    private final MachineResourceKind kind;
    private final ItemStack item;
    private final FluidStack fluid;
    private final GasStack gas;
    private final int amount;
    private NBTTagCompound itemIdentity;

    private NativeOutputStack(MachineResourceKind kind, ItemStack item, FluidStack fluid, GasStack gas, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Native output amount must be positive");
        this.kind = kind;
        this.item = item;
        this.fluid = fluid;
        this.gas = gas;
        this.amount = amount;
    }

    public static NativeOutputStack item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Empty item");
        ItemStack template = stack.copy();
        template.setCount(1);
        return new NativeOutputStack(MachineResourceKind.ITEM, template, null, null, stack.getCount());
    }

    public static NativeOutputStack fluid(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) throw new IllegalArgumentException("Empty fluid");
        return new NativeOutputStack(MachineResourceKind.FLUID, null, new FluidStack(stack, 1), null, stack.amount);
    }

    public static NativeOutputStack gas(GasStack stack) {
        if (stack == null || stack.getGas() == null) throw new IllegalArgumentException("Empty gas");
        return new NativeOutputStack(MachineResourceKind.GAS, null, null, new GasStack(stack.getGas(), 1), stack.amount);
    }

    public static NativeOutputStack from(MachineResourceStack stack) {
        switch (stack.kind()) {
            case ITEM:
                return item(stack.itemStack());
            case FLUID:
                return fluid(stack.fluidStack());
            case GAS:
                return gas(stack.gasStack());
            default:
                throw new IllegalArgumentException("No native representation");
        }
    }

    public MachineResourceKind kind() {
        return kind;
    }

    public int amount() {
        return amount;
    }

    public NativeOutputStack withAmount(int count) {
        NativeOutputStack result = new NativeOutputStack(kind, item, fluid, gas, count);
        result.itemIdentity = itemIdentity;
        return result;
    }

    public ItemStack itemStack() {
        ItemStack copy = item.copy();
        copy.setCount(amount);
        return copy;
    }

    public FluidStack fluidStack() {
        return new FluidStack(fluid, amount);
    }

    public GasStack gasStack() {
        return new GasStack(gas.getGas(), amount);
    }

    private NBTTagCompound itemIdentity() {
        // The built-in item codec compares normalized serialized stacks, including ForgeCaps.
        // Retain precisely that identity, but serialize once per local candidate, not per comparison.
        if (itemIdentity == null) itemIdentity = item.writeToNBT(new NBTTagCompound());
        return itemIdentity;
    }

    public boolean sameType(NativeOutputStack other) {
        if (other == null || kind != other.kind) return false;
        switch (kind) {
            case ITEM:
                return item.getItem() == other.item.getItem() && item.getMetadata() == other.item.getMetadata()
                        && itemIdentity().equals(other.itemIdentity());
            case FLUID:
                return fluid.isFluidEqual(other.fluid);
            case GAS:
                return gas.isGasEqual(other.gas);
            default:
                return false;
        }
    }

    public MachineResourceStack describe(String portId) {
        switch (kind) {
            case ITEM:
                return MachineResourceStack.item(portId, itemStack());
            case FLUID:
                return MachineResourceStack.fluid(portId, fluidStack());
            case GAS:
                return MachineResourceStack.gas(portId, gasStack());
            default:
                throw new IllegalStateException("Unsupported native kind");
        }
    }
}
