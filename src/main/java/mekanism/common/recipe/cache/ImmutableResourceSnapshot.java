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

    private static final ImmutableResourceSnapshot EMPTY = new ImmutableResourceSnapshot(
          Kind.EMPTY, null, null, null, null, 0);

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
    private volatile String cachedSemanticKey;
    private volatile String cachedIdentityKey;

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
        return EMPTY;
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
        if (kind == Kind.EMPTY) return ItemStack.EMPTY;
        if (item == null) return null;
        ItemStack copy = item.copy();
        copy.setCount((int) amount);
        return copy;
    }

    @Nullable
    public FluidStack getFluidCopy() {
        if (fluid == null) return null;
        FluidStack copy = fluid.copy();
        copy.amount = (int) amount;
        return copy;
    }

    @Nullable
    public GasStack getGasCopy() {
        return gas == null ? null : gas.copy().withAmount((int) amount);
    }

    @Nullable
    public String getDescriptor() {
        return descriptor;
    }

    /** Stable value used for plan/signature comparisons. */
    public String semanticKey() {
        String cached = cachedSemanticKey;
        if (cached == null) cachedSemanticKey = cached = createSemanticKey();
        return cached;
    }

    private String createSemanticKey() {
        if (kind == Kind.ITEM && item != null) {
            return "item:" + String.valueOf(item.getItem().getRegistryName()) + ':' + item.getMetadata() + ':' +
                  amount + ':' + (item.hasTagCompound() ? item.getTagCompound().toString() : "");
        } else if (kind == Kind.FLUID && fluid != null) {
            return "fluid:" + String.valueOf(fluid.getFluid().getName()) + ':' + amount + ':' +
                  (fluid.tag == null ? "" : fluid.tag.toString());
        } else if (kind == Kind.GAS && gas != null) {
            return "gas:" + (gas.getGas() == null ? "" : gas.getGas().getName()) + ':' + amount;
        }
        return kind.name().toLowerCase() + ':' + String.valueOf(descriptor) + ':' + amount;
    }

    /** Resource identity without its quantity, suitable for recipe matching. */
    public String identityKey() {
        String cached = cachedIdentityKey;
        if (cached == null) cachedIdentityKey = cached = createIdentityKey();
        return cached;
    }

    private String createIdentityKey() {
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
        if (kind == Kind.EMPTY) return empty();
        long bounded = kind == Kind.OTHER ? checked : Math.min(Integer.MAX_VALUE, checked);
        return bounded == amount ? this : new ImmutableResourceSnapshot(this, bounded);
    }

    /** Quantity changes are pure: ItemStack.copy may invoke Forge capability listeners. */
    private ImmutableResourceSnapshot(ImmutableResourceSnapshot source, long amount) {
        kind = source.kind;
        item = source.item;
        fluid = source.fluid;
        gas = source.gas;
        descriptor = source.descriptor;
        this.amount = amount;
        cachedIdentityKey = source.cachedIdentityKey;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ImmutableResourceSnapshot &&
              semanticKey().equals(((ImmutableResourceSnapshot) other).semanticKey());
    }

    @Override
    public int hashCode() {
        return 31 + semanticKey().hashCode();
    }

    @Override
    public String toString() {
        return semanticKey();
    }
}
