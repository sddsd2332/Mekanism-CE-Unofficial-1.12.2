package mekanism.common.content.qio;

import io.netty.buffer.ByteBuf;
import mekanism.api.gas.GasStack;
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
    private final QIOResourceKind kind;
    private final long amount;
    private final ItemStack item;
    @Nullable
    private final FluidStack fluid;
    @Nullable
    private final GasStack gas;

    private QIOResourceEntry(UUID uuid, QIOResourceKind kind, long amount, ItemStack item,
          @Nullable FluidStack fluid, @Nullable GasStack gas) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.amount = Math.max(0, amount);
        this.item = item == null ? ItemStack.EMPTY : item.copy();
        if (!this.item.isEmpty()) {
            this.item.setCount(1);
        }
        this.fluid = fluid == null ? null : new FluidStack(fluid, 1);
        this.gas = gas == null || gas.getGas() == null ? null : new GasStack(gas.getGas(), 1);
    }

    @Nullable
    public static QIOResourceEntry create(UUID uuid, long amount) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(uuid);
        if (type == null) {
            return null;
        }
        return switch (type.getKind()) {
            case ITEM -> new QIOResourceEntry(uuid, type.getKind(), amount, type.createItemStack(1), null, null);
            case FLUID -> new QIOResourceEntry(uuid, type.getKind(), amount, ItemStack.EMPTY, type.createFluidStack(1), null);
            case GAS -> new QIOResourceEntry(uuid, type.getKind(), amount, ItemStack.EMPTY, null, type.createGasStack(1));
        };
    }

    public UUID getUUID() {
        return uuid;
    }

    public QIOResourceKind getKind() {
        return kind;
    }

    public long getAmount() {
        return amount;
    }

    public QIOResourceEntry withAmount(long amount) {
        return new QIOResourceEntry(uuid, kind, amount, item, fluid, gas);
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
    public ItemStack createItemStack(int count) {
        if (kind != QIOResourceKind.ITEM || item.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = item.copy();
        stack.setCount(count);
        return stack;
    }

    @Nullable
    public FluidStack createFluidStack(int amount) {
        if (kind != QIOResourceKind.FLUID || fluid == null || amount <= 0) {
            return null;
        }
        return new FluidStack(fluid, amount);
    }

    @Nullable
    public GasStack createGasStack(int amount) {
        if (kind != QIOResourceKind.GAS || gas == null || gas.getGas() == null || amount <= 0) {
            return null;
        }
        return new GasStack(gas.getGas(), amount);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof QIOResourceEntry)) {
            return false;
        }
        QIOResourceEntry other = (QIOResourceEntry) obj;
        if (!uuid.equals(other.uuid) || kind != other.kind || amount != other.amount) {
            return false;
        }
        switch (kind) {
            case ITEM:
                return ItemStack.areItemStacksEqual(item, other.item);
            case FLUID:
                return fluid == null ? other.fluid == null : fluid.isFluidEqual(other.fluid);
            case GAS:
                return gas == null ? other.gas == null : gas.isGasEqual(other.gas);
            default:
                return false;
        }
    }

    @Override
    public int hashCode() {
        int result = 31 * uuid.hashCode() + kind.hashCode();
        result = 31 * result + Long.hashCode(amount);
        switch (kind) {
            case ITEM:
                return 31 * result + item.hashCode();
            case FLUID:
                return 31 * result + (fluid == null ? 0 : fluid.hashCode());
            case GAS:
                return 31 * result + (gas == null || gas.getGas() == null ? 0 : gas.getGas().getName().hashCode());
            default:
                return result;
        }
    }

    public void write(ByteBuf buffer) {
        buffer.writeLong(uuid.getMostSignificantBits());
        buffer.writeLong(uuid.getLeastSignificantBits());
        buffer.writeByte(kind.ordinal());
        buffer.writeLong(amount);
        NBTTagCompound payload = new NBTTagCompound();
        switch (kind) {
            case ITEM:
                item.writeToNBT(payload);
                break;
            case FLUID:
                if (fluid != null) {
                    fluid.writeToNBT(payload);
                }
                break;
            case GAS:
                if (gas != null) {
                    gas.write(payload);
                }
                break;
        }
        PacketHandler.writeNBT(buffer, payload);
    }

    @Nullable
    public static QIOResourceEntry read(ByteBuf buffer) {
        try {
            UUID uuid = new UUID(buffer.readLong(), buffer.readLong());
            QIOResourceKind kind = QIOResourceKind.byOrdinal(buffer.readUnsignedByte());
            long amount = buffer.readLong();
            NBTTagCompound payload = PacketHandler.readNBT(buffer);
            if (kind == null || payload == null || amount < 0) {
                return null;
            }
            return switch (kind) {
                case ITEM -> {
                    ItemStack stack = new ItemStack(payload);
                    yield stack.isEmpty() ? null : new QIOResourceEntry(uuid, kind, amount, stack, null, null);
                }
                case FLUID -> {
                    FluidStack stack = FluidStack.loadFluidStackFromNBT(payload);
                    yield stack == null || stack.amount <= 0 ? null : new QIOResourceEntry(uuid, kind, amount, ItemStack.EMPTY, stack, null);
                }
                case GAS -> {
                    GasStack stack = GasStack.readFromNBT(payload);
                    yield stack == null ? null : new QIOResourceEntry(uuid, kind, amount, ItemStack.EMPTY, null, stack);
                }
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
