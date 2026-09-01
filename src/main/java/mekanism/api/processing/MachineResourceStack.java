package mekanism.api.processing;

import mekanism.api.gas.GasStack;
import mekanism.api.qio.resource.QIOResourceCodec;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Immutable codec-backed resource and amount used by machine recipes and transfer plans.
 *
 * <p>The descriptor is the authoritative resource identity. {@link MachineResourceKind} remains a compatibility
 * view for integrations that only understand the built-in item, fluid and gas codecs.</p>
 */
public final class MachineResourceStack {

    private static final int DATA_VERSION = 2;
    private static final String VERSION = "version";
    private static final String LEGACY_KIND = "kind";
    private static final String PORT = "port";
    private static final String ORDER = "order";
    private static final String AMOUNT = "amount";
    private static final String DESCRIPTOR = "descriptor";
    private static final String LEGACY_RESOURCE = "resource";

    private final QIOResourceDescriptor descriptor;
    private final String portId;
    private final int order;
    private final long amount;

    private MachineResourceStack(QIOResourceDescriptor descriptor, String portId, int order, long amount) {
        this.descriptor = Objects.requireNonNull(descriptor, "Resource descriptor cannot be null");
        this.portId = portId == null ? "" : portId;
        this.order = Math.max(0, order);
        if (amount <= 0) {
            throw new IllegalArgumentException("Resource amount must be positive");
        }
        this.amount = amount;
    }

    @Nonnull
    public static MachineResourceStack resource(String portId, @Nonnull QIOResourceDescriptor descriptor,
          long amount) {
        return new MachineResourceStack(descriptor, portId, 0, amount);
    }

    @Nonnull
    public static <T> MachineResourceStack resource(String portId, @Nonnull QIOResourceCodec<T> codec,
          @Nonnull T value, long amount) {
        return resource(portId, QIOResourceDescriptor.of(codec, value), amount);
    }

    public static MachineResourceStack item(String portId, @Nonnull ItemStack stack) {
        Objects.requireNonNull(stack, "Item stack cannot be null");
        if (stack.isEmpty() || stack.getCount() <= 0) {
            throw new IllegalArgumentException("Item stack cannot be empty");
        }
        return resource(portId, QIOResourceCodecs.item(stack), stack.getCount());
    }

    public static MachineResourceStack item(String portId, @Nonnull ItemStack stack, long amount) {
        return resource(portId, QIOResourceCodecs.item(Objects.requireNonNull(stack, "Item stack cannot be null")), amount);
    }

    public static MachineResourceStack fluid(String portId, @Nonnull FluidStack stack) {
        Objects.requireNonNull(stack, "Fluid stack cannot be null");
        return resource(portId, QIOResourceCodecs.fluid(stack), stack.amount);
    }

    public static MachineResourceStack fluid(String portId, @Nonnull FluidStack stack, long amount) {
        return resource(portId, QIOResourceCodecs.fluid(Objects.requireNonNull(stack, "Fluid stack cannot be null")), amount);
    }

    public static MachineResourceStack gas(String portId, @Nonnull GasStack stack) {
        Objects.requireNonNull(stack, "Gas stack cannot be null");
        return resource(portId, QIOResourceCodecs.gas(stack), stack.amount);
    }

    public static MachineResourceStack gas(String portId, @Nonnull GasStack stack, long amount) {
        return resource(portId, QIOResourceCodecs.gas(Objects.requireNonNull(stack, "Gas stack cannot be null")), amount);
    }

    public MachineResourceStack withOrder(int order) {
        return new MachineResourceStack(descriptor, portId, order, amount);
    }

    public MachineResourceStack withPort(String portId) {
        return new MachineResourceStack(descriptor, portId, order, amount);
    }

    public MachineResourceStack withAmount(long amount) {
        return new MachineResourceStack(descriptor, portId, order, amount);
    }

    public MachineResourceStack scale(long multiplier) {
        if (multiplier <= 0 || amount > Long.MAX_VALUE / multiplier) {
            throw new IllegalArgumentException("Invalid resource multiplier: " + multiplier);
        }
        return withAmount(amount * multiplier);
    }

    /** @deprecated Use {@link #descriptor()} or {@link #codecId()} for compatibility decisions. */
    @Deprecated
    public MachineResourceKind kind() {
        return MachineResourceKind.fromDescriptor(descriptor);
    }

    @Nonnull
    public QIOResourceDescriptor descriptor() {
        return descriptor;
    }

    @Nonnull
    public String family() {
        return descriptor.getFamily();
    }

    @Nonnull
    public ResourceLocation codecId() {
        return descriptor.getCodecId();
    }

    public boolean isResolved() {
        return descriptor.isResolved();
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
        if (amount > Integer.MAX_VALUE) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = descriptor.resolve(QIOResourceCodecs.ITEM_STACK);
        if (copy == null) {
            return ItemStack.EMPTY;
        }
        copy.setCount((int) amount);
        return copy;
    }

    @Nullable
    public FluidStack fluidStack() {
        if (amount > Integer.MAX_VALUE) {
            return null;
        }
        FluidStack template = descriptor.resolve(QIOResourceCodecs.FLUID_STACK);
        return template == null ? null : new FluidStack(template, (int) amount);
    }

    @Nullable
    public GasStack gasStack() {
        if (amount > Integer.MAX_VALUE) {
            return null;
        }
        GasStack template = descriptor.resolve(QIOResourceCodecs.GAS_STACK);
        return template == null ? null : new GasStack(template.getGas(), (int) amount);
    }

    @Nullable
    public <T> T resolve(@Nonnull QIOResourceCodec<T> codec) {
        return descriptor.resolve(codec);
    }

    public boolean sameResource(@Nullable MachineResourceStack other) {
        return other != null && descriptor.equals(other.descriptor);
    }

    public NBTTagCompound write(NBTTagCompound nbt) {
        Objects.requireNonNull(nbt, "Resource NBT cannot be null");
        nbt.setInteger(VERSION, DATA_VERSION);
        nbt.setString(PORT, portId);
        nbt.setInteger(ORDER, order);
        nbt.setLong(AMOUNT, amount);
        nbt.setTag(DESCRIPTOR, descriptor.write());
        return nbt;
    }

    @Nullable
    public static MachineResourceStack read(@Nullable NBTTagCompound nbt) {
        if (nbt == null) {
            return null;
        }
        long amount = nbt.getLong(AMOUNT);
        if (amount <= 0) {
            return null;
        }
        String portId = nbt.getString(PORT);
        int order = nbt.getInteger(ORDER);
        try {
            if (nbt.hasKey(DESCRIPTOR, NBT.TAG_COMPOUND)) {
                return resource(portId, QIOResourceDescriptor.read(nbt.getCompoundTag(DESCRIPTOR)), amount)
                      .withOrder(order);
            }
            return readLegacy(nbt, portId, order, amount);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static MachineResourceStack readLegacy(NBTTagCompound nbt, String portId, int order, long amount) {
        if (!nbt.hasKey(LEGACY_RESOURCE, NBT.TAG_COMPOUND)) {
            return null;
        }
        int kindIndex = nbt.getInteger(LEGACY_KIND);
        NBTTagCompound resource = nbt.getCompoundTag(LEGACY_RESOURCE);
        MachineResourceStack stack;
        if (kindIndex == MachineResourceKind.ITEM.ordinal()) {
            stack = item(portId, new ItemStack(resource), amount);
        } else if (kindIndex == MachineResourceKind.FLUID.ordinal()) {
            stack = fluid(portId, FluidStack.loadFluidStackFromNBT(resource), amount);
        } else if (kindIndex == MachineResourceKind.GAS.ordinal()) {
            stack = gas(portId, GasStack.readFromNBT(resource), amount);
        } else {
            return null;
        }
        return stack.withOrder(order);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof MachineResourceStack other && order == other.order &&
              amount == other.amount && portId.equals(other.portId) && descriptor.equals(other.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor, portId, order, amount);
    }

    @Override
    public String toString() {
        return descriptor.getCodecId() + "[" + portId + ", amount=" + amount + ']';
    }
}
