package mekanism.common.content.qio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.common.PacketHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Immutable network/view representation of one QIO resource and its amount. */
public final class QIOResourceEntry {

    private final UUID uuid;
    private final QIOResourceDescriptor descriptor;
    private final QIOAmount amount;
    private final boolean displayPayloadAvailable;

    private QIOResourceEntry(UUID uuid, QIOResourceDescriptor descriptor, QIOAmount amount) {
        this(uuid, descriptor, amount, true);
    }

    private QIOResourceEntry(UUID uuid, QIOResourceDescriptor descriptor, QIOAmount amount,
          boolean displayPayloadAvailable) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.amount = amount == null ? QIOAmount.ZERO : amount;
        this.displayPayloadAvailable = displayPayloadAvailable;
    }

    @Nullable
    public static QIOResourceEntry create(UUID uuid, long amount) {
        return create(uuid, QIOAmount.of(amount));
    }

    @Nullable
    public static QIOResourceEntry create(UUID uuid, QIOAmount amount) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(uuid);
        return type == null ? null : new QIOResourceEntry(uuid, type.getDescriptor(), amount);
    }

    @Nonnull
    public static QIOResourceEntry of(UUID uuid, QIOResourceDescriptor descriptor, QIOAmount amount) {
        return new QIOResourceEntry(uuid, descriptor, amount);
    }

    public UUID getUUID() {
        return uuid;
    }

    @Nonnull
    public QIOResourceDescriptor getDescriptor() {
        return descriptor;
    }

    /** @deprecated Use {@link #getDescriptor()}; null means a custom codec. */
    @Deprecated
    @Nullable
    public QIOResourceKind getKind() {
        return QIOResourceKind.fromDescriptor(descriptor);
    }

    public long getAmount() {
        return amount.longValueClamped();
    }

    @Nonnull
    public QIOAmount getExactAmount() {
        return amount;
    }

    public long getStorageUnitsPerUnit() {
        return descriptor.getStorageUnitsPerUnit();
    }

    /** False when an oversized codec payload was intentionally replaced by the generic fallback. */
    public boolean hasDisplayPayload() {
        return displayPayloadAvailable;
    }

    public QIOResourceEntry withAmount(long amount) {
        return withAmount(QIOAmount.of(amount));
    }

    public QIOResourceEntry withAmount(QIOAmount amount) {
        return new QIOResourceEntry(uuid, descriptor, amount, displayPayloadAvailable);
    }

    @Nonnull
    public ItemStack getItem() {
        ItemStack stack = descriptor.resolve(QIOResourceCodecs.ITEM_STACK);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Nullable
    public FluidStack getFluid() {
        return descriptor.resolve(QIOResourceCodecs.FLUID_STACK);
    }

    @Nullable
    public GasStack getGas() {
        return descriptor.resolve(QIOResourceCodecs.GAS_STACK);
    }

    @Nonnull
    public ItemStack createItemStack(int count) {
        ItemStack stack = getItem();
        if (stack.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        stack.setCount(count);
        return stack;
    }

    @Nullable
    public FluidStack createFluidStack(int amount) {
        FluidStack stack = getFluid();
        return stack == null || amount <= 0 ? null : new FluidStack(stack, amount);
    }

    @Nullable
    public GasStack createGasStack(int amount) {
        GasStack stack = getGas();
        return stack == null || amount <= 0 ? null : new GasStack(stack.getGas(), amount);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof QIOResourceEntry other && uuid.equals(other.uuid) &&
              descriptor.equals(other.descriptor) && amount.equals(other.amount) &&
              displayPayloadAvailable == other.displayPayloadAvailable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, descriptor, amount, displayPayloadAvailable);
    }

    public void write(ByteBuf buffer) {
        buffer.writeLong(uuid.getMostSignificantBits());
        buffer.writeLong(uuid.getLeastSignificantBits());
        amount.write(buffer);
        boolean includePayload = displayPayloadAvailable &&
              QIONetworkResourceLimits.isSafeDescriptorPayload(descriptor.getPayload());
        buffer.writeBoolean(includePayload);
        PacketHandler.writeNBT(buffer, includePayload ? descriptor.write() : networkFallbackDescriptor().write());
    }

    /** Exact encoded size after applying the per-descriptor fallback. */
    public int getNetworkEncodedSize() {
        ByteBuf temporary = Unpooled.buffer();
        try {
            write(temporary);
            return temporary.readableBytes();
        } finally {
            temporary.release();
        }
    }

    @Nullable
    public static QIOResourceEntry read(ByteBuf buffer) {
        int entryStart = buffer.readerIndex();
        try {
            UUID uuid = new UUID(buffer.readLong(), buffer.readLong());
            QIOAmount amount = QIOAmount.read(buffer);
            boolean displayPayloadAvailable = buffer.readBoolean();
            NBTTagCompound descriptor = PacketHandler.readNBT(buffer);
            if (descriptor == null) {
                return null;
            }
            QIOResourceDescriptor decoded = QIOResourceDescriptor.read(descriptor);
            if (!QIONetworkResourceLimits.isSafeDescriptorPayload(decoded.getPayload())) {
                return null;
            }
            if (buffer.readerIndex() - entryStart > QIONetworkResourceLimits.MAX_ENTRY_BYTES) {
                return null;
            }
            return new QIOResourceEntry(uuid, decoded, amount, displayPayloadAvailable);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private QIOResourceDescriptor networkFallbackDescriptor() {
        return QIOResourceDescriptor.persisted(descriptor.getCodecId(), descriptor.getFamily(),
              descriptor.getCodecVersion(), descriptor.getStorageUnitsPerUnit(), new NBTTagCompound());
    }
}
