package mekanism.api.qio.external;

import mekanism.api.gas.GasStack;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** Immutable resource identity and exact amount in a QIO storage view. */
public final class QIOStorageEntry {

    private final UUID resourceUUID;
    private final QIOResourceDescriptor descriptor;
    private final BigInteger storedAmount;
    private final BigInteger committedAmount;
    private final BigInteger availableAmount;

    private QIOStorageEntry(UUID resourceUUID, BigInteger storedAmount, BigInteger committedAmount,
          QIOResourceDescriptor descriptor) {
        this.resourceUUID = Objects.requireNonNull(resourceUUID, "resourceUUID");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.storedAmount = requireAmount(storedAmount, "storedAmount");
        this.committedAmount = requireAmount(committedAmount, "committedAmount");
        this.availableAmount = this.storedAmount.subtract(this.committedAmount).max(BigInteger.ZERO);
    }

    @Nonnull
    public static QIOStorageEntry resource(UUID resourceUUID, BigInteger amount,
          QIOResourceDescriptor descriptor) {
        return resource(resourceUUID, amount, BigInteger.ZERO, descriptor);
    }

    @Nonnull
    public static QIOStorageEntry resource(UUID resourceUUID, BigInteger storedAmount,
          BigInteger committedAmount, QIOResourceDescriptor descriptor) {
        return new QIOStorageEntry(resourceUUID, storedAmount, committedAmount, descriptor);
    }

    @Nonnull
    public static QIOStorageEntry item(UUID resourceUUID, BigInteger amount, ItemStack template) {
        return item(resourceUUID, amount, BigInteger.ZERO, template);
    }

    @Nonnull
    public static QIOStorageEntry item(UUID resourceUUID, BigInteger storedAmount,
          BigInteger committedAmount, ItemStack template) {
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("QIO item template cannot be empty");
        }
        return resource(resourceUUID, storedAmount, committedAmount, QIOResourceCodecs.item(template));
    }

    @Nonnull
    public static QIOStorageEntry fluid(UUID resourceUUID, BigInteger amount, FluidStack template) {
        return fluid(resourceUUID, amount, BigInteger.ZERO, template);
    }

    @Nonnull
    public static QIOStorageEntry fluid(UUID resourceUUID, BigInteger storedAmount,
          BigInteger committedAmount, FluidStack template) {
        if (template == null || template.getFluid() == null) {
            throw new IllegalArgumentException("QIO fluid template cannot be empty");
        }
        return resource(resourceUUID, storedAmount, committedAmount, QIOResourceCodecs.fluid(template));
    }

    @Nonnull
    public static QIOStorageEntry gas(UUID resourceUUID, BigInteger amount, GasStack template) {
        return gas(resourceUUID, amount, BigInteger.ZERO, template);
    }

    @Nonnull
    public static QIOStorageEntry gas(UUID resourceUUID, BigInteger storedAmount,
          BigInteger committedAmount, GasStack template) {
        if (template == null || template.getGas() == null) {
            throw new IllegalArgumentException("QIO gas template cannot be empty");
        }
        return resource(resourceUUID, storedAmount, committedAmount, QIOResourceCodecs.gas(template));
    }

    @Nonnull
    public UUID getResourceUUID() {
        return resourceUUID;
    }

    @Nonnull
    public QIOResourceDescriptor getDescriptor() {
        return descriptor;
    }

    /** @deprecated Use {@link #getDescriptor()}; null means a custom codec. */
    @Deprecated
    @Nullable
    public QIOStorageResourceKind getKind() {
        return QIOStorageResourceKind.fromDescriptor(descriptor);
    }

    @Nonnull
    public BigInteger getExactAmount() {
        return storedAmount;
    }

    public long getAmountClamped() {
        return clamp(storedAmount);
    }

    @Nonnull
    public BigInteger getExactStoredAmount() {
        return storedAmount;
    }

    @Nonnull
    public BigInteger getExactCommittedAmount() {
        return committedAmount;
    }

    @Nonnull
    public BigInteger getExactAvailableAmount() {
        return availableAmount;
    }

    public long getStoredAmountClamped() {
        return clamp(storedAmount);
    }

    public long getCommittedAmountClamped() {
        return clamp(committedAmount);
    }

    public long getAvailableAmountClamped() {
        return clamp(availableAmount);
    }

    @Nonnull
    public ItemStack getItem() {
        ItemStack stack = descriptor.resolve(QIOResourceCodecs.ITEM_STACK);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Nullable
    public FluidStack getFluid() {
        FluidStack stack = descriptor.resolve(QIOResourceCodecs.FLUID_STACK);
        return stack == null ? null : stack;
    }

    @Nullable
    public GasStack getGas() {
        GasStack stack = descriptor.resolve(QIOResourceCodecs.GAS_STACK);
        return stack == null ? null : stack;
    }

    @Nonnull
    public QIOStorageEntry withAmount(BigInteger newAmount) {
        return new QIOStorageEntry(resourceUUID, newAmount, BigInteger.ZERO, descriptor);
    }

    @Nonnull
    public QIOStorageEntry withAmounts(BigInteger newStoredAmount, BigInteger newCommittedAmount) {
        return new QIOStorageEntry(resourceUUID, newStoredAmount, newCommittedAmount, descriptor);
    }

    private static BigInteger requireAmount(BigInteger amount, String name) {
        Objects.requireNonNull(amount, name);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("QIO storage " + name + " cannot be negative");
        }
        return amount;
    }

    private static long clamp(BigInteger amount) {
        return amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0 ? Long.MAX_VALUE : amount.longValue();
    }
}
