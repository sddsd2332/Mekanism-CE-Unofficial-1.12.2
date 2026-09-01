package mekanism.api.qio.resource;

import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Immutable generic QIO resource descriptor and positive integer amount. */
public final class QIOResourceStack {

    private final QIOResourceDescriptor descriptor;
    private final long amount;

    public QIOResourceStack(@Nonnull QIOResourceDescriptor descriptor, long amount) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (amount <= 0) {
            throw new IllegalArgumentException("QIO resource amount must be positive");
        }
        this.amount = amount;
    }

    @Nonnull
    public static QIOResourceStack item(@Nonnull ItemStack stack) {
        return new QIOResourceStack(QIOResourceCodecs.item(stack), stack.getCount());
    }

    @Nonnull
    public static QIOResourceStack fluid(@Nonnull FluidStack stack) {
        return new QIOResourceStack(QIOResourceCodecs.fluid(stack), stack.amount);
    }

    @Nonnull
    public static QIOResourceStack gas(@Nonnull GasStack stack) {
        return new QIOResourceStack(QIOResourceCodecs.gas(stack), stack.amount);
    }

    @Nonnull
    public QIOResourceDescriptor getDescriptor() {
        return descriptor;
    }

    public long getAmount() {
        return amount;
    }

    @Nonnull
    public QIOResourceStack withAmount(long amount) {
        return new QIOResourceStack(descriptor, amount);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setTag("descriptor", descriptor.write());
        data.setLong("amount", amount);
        return data;
    }

    @Nonnull
    public static QIOResourceStack read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "resource stack");
        return new QIOResourceStack(QIOResourceDescriptor.read(data.getCompoundTag("descriptor")),
              data.getLong("amount"));
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof QIOResourceStack other && amount == other.amount &&
              descriptor.equals(other.descriptor);
    }

    @Override
    public int hashCode() {
        return 31 * descriptor.hashCode() + Long.hashCode(amount);
    }
}
