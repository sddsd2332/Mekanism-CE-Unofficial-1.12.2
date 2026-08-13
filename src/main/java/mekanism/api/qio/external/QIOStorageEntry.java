package mekanism.api.qio.external;

import mekanism.api.gas.GasStack;
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
    private final QIOStorageResourceKind kind;
    private final BigInteger storedAmount;
    private final BigInteger committedAmount;
    private final BigInteger availableAmount;
    private final ItemStack item;
    private final FluidStack fluid;
    private final GasStack gas;

    private QIOStorageEntry(UUID resourceUUID, QIOStorageResourceKind kind, BigInteger storedAmount,
          BigInteger committedAmount,
          @Nullable ItemStack item, @Nullable FluidStack fluid, @Nullable GasStack gas) {
        this.resourceUUID = Objects.requireNonNull(resourceUUID, "resourceUUID");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.storedAmount = requireAmount(storedAmount, "storedAmount");
        this.committedAmount = requireAmount(committedAmount, "committedAmount");
        this.availableAmount = this.storedAmount.subtract(this.committedAmount).max(BigInteger.ZERO);
        this.item = item == null ? ItemStack.EMPTY : item.copy();
        this.fluid = fluid == null ? null : fluid.copy();
        this.gas = gas == null ? null : gas.copy();
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
        ItemStack copy = template.copy();
        copy.setCount(1);
        return new QIOStorageEntry(resourceUUID, QIOStorageResourceKind.ITEM, storedAmount,
              committedAmount, copy, null, null);
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
        FluidStack copy = template.copy();
        copy.amount = 1;
        return new QIOStorageEntry(resourceUUID, QIOStorageResourceKind.FLUID, storedAmount,
              committedAmount, null, copy, null);
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
        GasStack copy = template.copy();
        copy.amount = 1;
        return new QIOStorageEntry(resourceUUID, QIOStorageResourceKind.GAS, storedAmount,
              committedAmount, null, null, copy);
    }

    @Nonnull
    public UUID getResourceUUID() {
        return resourceUUID;
    }

    @Nonnull
    public QIOStorageResourceKind getKind() {
        return kind;
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
        return item.copy();
    }

    @Nullable
    public FluidStack getFluid() {
        return fluid == null ? null : fluid.copy();
    }

    @Nullable
    public GasStack getGas() {
        return gas == null ? null : gas.copy();
    }

    @Nonnull
    public QIOStorageEntry withAmount(BigInteger newAmount) {
        return new QIOStorageEntry(resourceUUID, kind, newAmount, BigInteger.ZERO, item, fluid, gas);
    }

    @Nonnull
    public QIOStorageEntry withAmounts(BigInteger newStoredAmount, BigInteger newCommittedAmount) {
        return new QIOStorageEntry(resourceUUID, kind, newStoredAmount, newCommittedAmount, item, fluid, gas);
    }

    private static BigInteger requireAmount(BigInteger amount, String name) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("QIO storage " + name + " cannot be negative");
        }
        return amount;
    }

    private static long clamp(BigInteger amount) {
        return amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0 ? Long.MAX_VALUE : amount.longValue();
    }
}
