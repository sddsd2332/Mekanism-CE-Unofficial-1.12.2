package mekanism.common.content.qio;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.lib.inventory.HashedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable, amount-independent QIO resource template.
 */
public final class QIOResourceType {

    static final int DATA_VERSION = 2;
    private static final UUID LOOKUP_UUID = new UUID(0, 0);

    private final UUID uuid;
    private final QIOResourceKind kind;
    @Nullable
    private final HashedItem itemType;
    @Nullable
    private final FluidStack fluidType;
    @Nullable
    private final Gas gasType;
    private final int hashCode;

    private QIOResourceType(UUID uuid, QIOResourceKind kind, @Nullable HashedItem itemType,
          @Nullable FluidStack fluidType, @Nullable Gas gasType) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.itemType = itemType;
        this.fluidType = fluidType;
        this.gasType = gasType;
        this.hashCode = calculateHashCode();
    }

    static QIOResourceType itemLookup(HashedItem item) {
        return forItem(LOOKUP_UUID, item);
    }

    static QIOResourceType fluidLookup(FluidStack fluid) {
        return forFluid(LOOKUP_UUID, fluid);
    }

    static QIOResourceType gasLookup(GasStack gas) {
        return forGas(LOOKUP_UUID, gas);
    }

    public static QIOResourceType forItem(UUID uuid, HashedItem item) {
        Objects.requireNonNull(item, "item");
        ItemStack stack = item.createStack(1);
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a QIO resource type for an empty item stack");
        }
        return new QIOResourceType(uuid, QIOResourceKind.ITEM, HashedItem.create(stack), null, null);
    }

    public static QIOResourceType forFluid(UUID uuid, FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) {
            throw new IllegalArgumentException("Cannot create a QIO resource type for an empty fluid stack");
        }
        return new QIOResourceType(uuid, QIOResourceKind.FLUID, null, new FluidStack(fluid, 1), null);
    }

    public static QIOResourceType forGas(UUID uuid, GasStack gas) {
        if (gas == null || gas.getGas() == null) {
            throw new IllegalArgumentException("Cannot create a QIO resource type for an empty gas stack");
        }
        return new QIOResourceType(uuid, QIOResourceKind.GAS, null, null, gas.getGas());
    }

    static QIOResourceType read(UUID fileUUID, NBTTagCompound data) {
        if (data.getInteger("version") != DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported QIO resource type version: " + data.getInteger("version"));
        }
        UUID storedUUID;
        try {
            storedUUID = UUID.fromString(data.getString("uuid"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid QIO resource type UUID", e);
        }
        if (!fileUUID.equals(storedUUID)) {
            throw new IllegalArgumentException("QIO resource type UUID does not match its file name");
        }
        QIOResourceKind kind = QIOResourceKind.byName(data.getString("kind"));
        if (kind == null) {
            throw new IllegalArgumentException("Unknown QIO resource kind: " + data.getString("kind"));
        }
        NBTTagCompound payload = data.getCompoundTag("payload");
        switch (kind) {
            case ITEM:
                ItemStack item = new ItemStack(payload);
                if (item.isEmpty()) {
                    throw new IllegalArgumentException("QIO item resource is no longer available");
                }
                return forItem(fileUUID, HashedItem.create(item));
            case FLUID:
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(payload);
                if (fluid == null) {
                    throw new IllegalArgumentException("QIO fluid resource is no longer available");
                }
                return forFluid(fileUUID, fluid);
            case GAS:
                GasStack gas = GasStack.readFromNBT(payload);
                if (gas == null) {
                    throw new IllegalArgumentException("QIO gas resource is no longer available");
                }
                return forGas(fileUUID, gas);
            default:
                throw new IllegalStateException("Unhandled QIO resource kind: " + kind);
        }
    }

    NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("version", DATA_VERSION);
        data.setString("uuid", uuid.toString());
        data.setString("kind", kind.getSerializedName());
        NBTTagCompound payload = new NBTTagCompound();
        switch (kind) {
            case ITEM:
                itemType.createStack(1).writeToNBT(payload);
                break;
            case FLUID:
                fluidType.writeToNBT(payload);
                break;
            case GAS:
                new GasStack(gasType, 1).write(payload);
                break;
            default:
                throw new IllegalStateException("Unhandled QIO resource kind: " + kind);
        }
        data.setTag("payload", payload);
        return data;
    }

    public UUID getUUID() {
        return uuid;
    }

    public QIOResourceKind getKind() {
        return kind;
    }

    @Nonnull
    public ItemStack createItemStack(int amount) {
        return kind == QIOResourceKind.ITEM && amount > 0 ? itemType.createStack(amount) : ItemStack.EMPTY;
    }

    @Nullable
    public FluidStack createFluidStack(int amount) {
        return kind == QIOResourceKind.FLUID && amount > 0 ? new FluidStack(fluidType, amount) : null;
    }

    @Nullable
    public GasStack createGasStack(int amount) {
        return kind == QIOResourceKind.GAS && amount > 0 ? new GasStack(gasType, amount) : null;
    }

    @Nullable
    public HashedItem getItemType() {
        return itemType == null ? null : HashedItem.create(itemType.createStack(1));
    }

    @Nullable
    public FluidStack getFluidType() {
        return fluidType == null ? null : new FluidStack(fluidType, 1);
    }

    @Nullable
    public Gas getGasType() {
        return gasType;
    }

    private int calculateHashCode() {
        int result = kind.hashCode();
        switch (kind) {
            case ITEM:
                return 31 * result + itemType.hashCode();
            case FLUID:
                return 31 * result + fluidType.hashCode();
            case GAS:
                return 31 * result + gasType.getName().hashCode();
            default:
                return result;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof QIOResourceType)) {
            return false;
        }
        QIOResourceType other = (QIOResourceType) obj;
        if (kind != other.kind) {
            return false;
        }
        switch (kind) {
            case ITEM:
                return itemType.equals(other.itemType);
            case FLUID:
                return fluidType.equals(other.fluidType);
            case GAS:
                return gasType.getName().equals(other.gasType.getName());
            default:
                return false;
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
