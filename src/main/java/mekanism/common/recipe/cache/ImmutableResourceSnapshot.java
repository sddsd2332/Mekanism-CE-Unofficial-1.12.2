package mekanism.common.recipe.cache;

import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Defensive value copy of one machine resource. The contained Minecraft stack is
 * never returned directly; every accessor returns a new copy.
 */
public final class ImmutableResourceSnapshot {

    public enum Kind {
        EMPTY,
        ITEM,
        FLUID,
        GAS,
        OTHER
    }

    private final Kind kind;
    @Nullable
    private final ItemStack item;
    @Nullable
    private final FluidStack fluid;
    @Nullable
    private final GasStack gas;
    @Nullable
    private final String descriptor;
    private final long amount;

    private ImmutableResourceSnapshot(Kind kind, @Nullable ItemStack item, @Nullable FluidStack fluid,
          @Nullable GasStack gas, @Nullable String descriptor, long amount) {
        this.kind = kind;
        this.item = item == null ? null : item.copy();
        this.fluid = fluid == null ? null : fluid.copy();
        this.gas = gas == null ? null : gas.copy();
        this.descriptor = descriptor;
        this.amount = amount;
    }

    public static ImmutableResourceSnapshot empty() {
        return new ImmutableResourceSnapshot(Kind.EMPTY, ItemStack.EMPTY, null, null, null, 0);
    }

    public static ImmutableResourceSnapshot of(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? empty() :
              new ImmutableResourceSnapshot(Kind.ITEM, stack, null, null, null, stack.getCount());
    }

    public static ImmutableResourceSnapshot of(@Nullable FluidStack stack) {
        return stack == null || stack.amount <= 0 ? empty() :
              new ImmutableResourceSnapshot(Kind.FLUID, null, stack, null, null, stack.amount);
    }

    public static ImmutableResourceSnapshot of(@Nullable GasStack stack) {
        return stack == null || stack.amount <= 0 ? empty() :
              new ImmutableResourceSnapshot(Kind.GAS, null, null, stack, null, stack.amount);
    }

    /** Creates a scalar resource descriptor for non-Minecraft values. */
    public static ImmutableResourceSnapshot descriptor(String descriptor, long amount) {
        if (descriptor == null || descriptor.isEmpty()) {
            return empty();
        }
        return new ImmutableResourceSnapshot(Kind.OTHER, null, null, null, descriptor, amount);
    }

    /** Best-effort conversion for callers which receive a heterogeneous resource. */
    public static ImmutableResourceSnapshot of(@Nullable Object value) {
        if (value instanceof ItemStack) {
            return of((ItemStack) value);
        } else if (value instanceof FluidStack) {
            return of((FluidStack) value);
        } else if (value instanceof GasStack) {
            return of((GasStack) value);
        } else if (value == null) {
            return empty();
        }
        return descriptor(String.valueOf(value), 1);
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isEmpty() {
        return kind == Kind.EMPTY || amount <= 0;
    }

    public long getAmount() {
        return amount;
    }

    @Nullable
    public ItemStack getItemCopy() {
        return item == null ? null : item.copy();
    }

    @Nullable
    public FluidStack getFluidCopy() {
        return fluid == null ? null : fluid.copy();
    }

    @Nullable
    public GasStack getGasCopy() {
        return gas == null ? null : gas.copy();
    }

    @Nullable
    public String getDescriptor() {
        return descriptor;
    }

    /** Stable value used for plan/signature comparisons. */
    public String semanticKey() {
        if (kind == Kind.ITEM && item != null) {
            return "item:" + String.valueOf(item.getItem().getRegistryName()) + ':' + item.getMetadata() + ':' +
                  item.getCount() + ':' + (item.hasTagCompound() ? item.getTagCompound().toString() : "");
        } else if (kind == Kind.FLUID && fluid != null) {
            return "fluid:" + String.valueOf(fluid.getFluid().getName()) + ':' + fluid.amount + ':' +
                  (fluid.tag == null ? "" : fluid.tag.toString());
        } else if (kind == Kind.GAS && gas != null) {
            return "gas:" + (gas.getGas() == null ? "" : gas.getGas().getName()) + ':' + gas.amount;
        }
        return kind.name().toLowerCase() + ':' + String.valueOf(descriptor) + ':' + amount;
    }

    /** Resource identity without its quantity, suitable for recipe matching. */
    public String identityKey() {
        if (kind == Kind.ITEM && item != null) {
            return "item:" + String.valueOf(item.getItem().getRegistryName()) + ':' + item.getMetadata() + ':' +
                  (item.hasTagCompound() ? item.getTagCompound().toString() : "");
        } else if (kind == Kind.FLUID && fluid != null) {
            return "fluid:" + fluid.getFluid().getName() + ':' + (fluid.tag == null ? "" : fluid.tag.toString());
        } else if (kind == Kind.GAS && gas != null) {
            return "gas:" + (gas.getGas() == null ? "" : gas.getGas().getName());
        }
        return kind.name().toLowerCase() + ':' + String.valueOf(descriptor);
    }

    /** Pure type comparison used by the worker-side planner. */
    public boolean matchesType(ImmutableResourceSnapshot required) {
        if (required == null || kind != required.kind) return false;
        if (kind == Kind.ITEM && item != null && required.item != null) {
            if (item.getItem() != required.item.getItem()) return false;
            if (required.item.getMetadata() != OreDictionary.WILDCARD_VALUE &&
                item.getMetadata() != required.item.getMetadata()) return false;
            return Objects.equals(item.getTagCompound(), required.item.getTagCompound());
        } else if (kind == Kind.FLUID && fluid != null && required.fluid != null) {
            return fluid.isFluidEqual(required.fluid);
        } else if (kind == Kind.GAS && gas != null && required.gas != null) {
            return gas.isGasEqual(required.gas);
        }
        return identityKey().equals(required.identityKey());
    }

    public ImmutableResourceSnapshot withAmount(long newAmount) {
        long checked = Math.max(0, newAmount);
        if (checked == 0) return empty();
        int bounded = checked >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) checked;
        if (kind == Kind.ITEM && item != null) {
            ItemStack copy = item.copy();
            copy.setCount(bounded);
            return of(copy);
        } else if (kind == Kind.FLUID && fluid != null) {
            FluidStack copy = fluid.copy();
            copy.amount = bounded;
            return of(copy);
        } else if (kind == Kind.GAS && gas != null) {
            return of(gas.copy().withAmount(bounded));
        } else if (kind == Kind.OTHER) {
            return descriptor(descriptor, checked);
        }
        return empty();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ImmutableResourceSnapshot &&
              semanticKey().equals(((ImmutableResourceSnapshot) other).semanticKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(semanticKey());
    }

    @Override
    public String toString() {
        return semanticKey();
    }
}
