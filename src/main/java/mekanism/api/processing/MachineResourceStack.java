package mekanism.api.processing;

import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Immutable typed resource and amount used by machine recipes and transfer plans.
 *
 * <p>The contained item, fluid, or gas payload is normalized to one unit. The amount is stored separately as a
 * {@code long}, so recipe planning is not limited by the mutable stack classes used by Minecraft 1.12.</p>
 */
public final class MachineResourceStack {

    private static final String KIND = "kind";
    private static final String PORT = "port";
    private static final String ORDER = "order";
    private static final String AMOUNT = "amount";
    private static final String RESOURCE = "resource";

    private final MachineResourceKind kind;
    private final String portId;
    private final int order;
    private final long amount;
    private final ItemStack item;
    @Nullable
    private final FluidStack fluid;
    @Nullable
    private final GasStack gas;

    private MachineResourceStack(MachineResourceKind kind, String portId, int order, long amount, ItemStack item,
          @Nullable FluidStack fluid, @Nullable GasStack gas) {
        this.kind = Objects.requireNonNull(kind, "Resource kind cannot be null");
        this.portId = portId == null ? "" : portId;
        this.order = Math.max(0, order);
        if (amount <= 0) {
            throw new IllegalArgumentException("Resource amount must be positive");
        }
        this.amount = amount;
        this.item = normalizeItem(item);
        this.fluid = normalizeFluid(fluid);
        this.gas = normalizeGas(gas);
        validatePayload();
    }

    public static MachineResourceStack item(String portId, @Nonnull ItemStack stack) {
        Objects.requireNonNull(stack, "Item stack cannot be null");
        if (stack.isEmpty() || stack.getCount() <= 0) {
            throw new IllegalArgumentException("Item stack cannot be empty");
        }
        return new MachineResourceStack(MachineResourceKind.ITEM, portId, 0, stack.getCount(), stack, null, null);
    }

    public static MachineResourceStack item(String portId, @Nonnull ItemStack stack, long amount) {
        return new MachineResourceStack(MachineResourceKind.ITEM, portId, 0, amount, stack, null, null);
    }

    public static MachineResourceStack fluid(String portId, @Nonnull FluidStack stack) {
        Objects.requireNonNull(stack, "Fluid stack cannot be null");
        return new MachineResourceStack(MachineResourceKind.FLUID, portId, 0, stack.amount, ItemStack.EMPTY, stack, null);
    }

    public static MachineResourceStack fluid(String portId, @Nonnull FluidStack stack, long amount) {
        return new MachineResourceStack(MachineResourceKind.FLUID, portId, 0, amount, ItemStack.EMPTY, stack, null);
    }

    public static MachineResourceStack gas(String portId, @Nonnull GasStack stack) {
        Objects.requireNonNull(stack, "Gas stack cannot be null");
        return new MachineResourceStack(MachineResourceKind.GAS, portId, 0, stack.amount, ItemStack.EMPTY, null, stack);
    }

    public static MachineResourceStack gas(String portId, @Nonnull GasStack stack, long amount) {
        return new MachineResourceStack(MachineResourceKind.GAS, portId, 0, amount, ItemStack.EMPTY, null, stack);
    }

    public MachineResourceStack withOrder(int order) {
        return new MachineResourceStack(kind, portId, order, amount, item, fluid, gas);
    }

    public MachineResourceStack withPort(String portId) {
        return new MachineResourceStack(kind, portId, order, amount, item, fluid, gas);
    }

    public MachineResourceStack withAmount(long amount) {
        return new MachineResourceStack(kind, portId, order, amount, item, fluid, gas);
    }

    public MachineResourceStack scale(long multiplier) {
        if (multiplier <= 0 || amount > Long.MAX_VALUE / multiplier) {
            throw new IllegalArgumentException("Invalid resource multiplier: " + multiplier);
        }
        return withAmount(amount * multiplier);
    }

    public MachineResourceKind kind() {
        return kind;
    }

    public String portId() {
        return portId;
    }

    public int order() {
        return order;
    }

    public long amount() {
        return amount;
    }

    @Nonnull
    public ItemStack itemStack() {
        if (kind != MachineResourceKind.ITEM || amount > Integer.MAX_VALUE) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = item.copy();
        copy.setCount((int) amount);
        return copy;
    }

    @Nullable
    public FluidStack fluidStack() {
        if (kind != MachineResourceKind.FLUID || fluid == null || amount > Integer.MAX_VALUE) {
            return null;
        }
        return new FluidStack(fluid, (int) amount);
    }

    @Nullable
    public GasStack gasStack() {
        if (kind != MachineResourceKind.GAS || gas == null || amount > Integer.MAX_VALUE) {
            return null;
        }
        return new GasStack(gas.getGas(), (int) amount);
    }

    public boolean sameResource(@Nullable MachineResourceStack other) {
        if (other == null || kind != other.kind) {
            return false;
        }
        return switch (kind) {
            case ITEM -> ItemStack.areItemsEqual(item, other.item) && ItemStack.areItemStackTagsEqual(item, other.item);
            case FLUID -> fluid != null && other.fluid != null && fluid.isFluidEqual(other.fluid);
            case GAS -> gas != null && other.gas != null && gas.isGasEqual(other.gas);
        };
    }

    public NBTTagCompound write(NBTTagCompound nbt) {
        nbt.setInteger(KIND, kind.ordinal());
        nbt.setString(PORT, portId);
        nbt.setInteger(ORDER, order);
        nbt.setLong(AMOUNT, amount);
        NBTTagCompound resource = new NBTTagCompound();
        switch (kind) {
            case ITEM -> item.writeToNBT(resource);
            case FLUID -> fluid.writeToNBT(resource);
            case GAS -> gas.write(resource);
        }
        nbt.setTag(RESOURCE, resource);
        return nbt;
    }

    @Nullable
    public static MachineResourceStack read(@Nullable NBTTagCompound nbt) {
        if (nbt == null || !nbt.hasKey(RESOURCE, NBT.TAG_COMPOUND)) {
            return null;
        }
        int kindIndex = nbt.getInteger(KIND);
        if (kindIndex < 0 || kindIndex >= MachineResourceKind.values().length) {
            return null;
        }
        long amount = nbt.getLong(AMOUNT);
        if (amount <= 0) {
            return null;
        }
        String portId = nbt.getString(PORT);
        int order = nbt.getInteger(ORDER);
        NBTTagCompound resource = nbt.getCompoundTag(RESOURCE);
        try {
            MachineResourceStack stack = switch (MachineResourceKind.values()[kindIndex]) {
                case ITEM -> item(portId, new ItemStack(resource), amount);
                case FLUID -> fluid(portId, FluidStack.loadFluidStackFromNBT(resource), amount);
                case GAS -> gas(portId, GasStack.readFromNBT(resource), amount);
            };
            return stack.withOrder(order);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void validatePayload() {
        boolean valid = switch (kind) {
            case ITEM -> !item.isEmpty();
            case FLUID -> fluid != null && fluid.getFluid() != null;
            case GAS -> gas != null && gas.getGas() != null;
        };
        if (!valid) {
            throw new IllegalArgumentException("Resource payload does not match kind " + kind);
        }
    }

    private static ItemStack normalizeItem(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    @Nullable
    private static FluidStack normalizeFluid(@Nullable FluidStack stack) {
        return stack == null || stack.getFluid() == null ? null : new FluidStack(stack, 1);
    }

    @Nullable
    private static GasStack normalizeGas(@Nullable GasStack stack) {
        return stack == null || stack.getGas() == null ? null : new GasStack(stack.getGas(), 1);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MachineResourceStack other)) {
            return false;
        }
        return order == other.order && amount == other.amount && portId.equals(other.portId) && sameResource(other);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(kind, portId, order, amount);
        return 31 * result + switch (kind) {
            case ITEM -> Objects.hash(item.getItem(), item.getMetadata(), item.getTagCompound());
            case FLUID -> Objects.hash(fluid == null ? null : fluid.getFluid(), fluid == null ? null : fluid.tag);
            case GAS -> Objects.hash(gas == null ? null : gas.getGas());
        };
    }

    @Override
    public String toString() {
        return kind + "[" + portId + ", amount=" + amount + ']';
    }
}
