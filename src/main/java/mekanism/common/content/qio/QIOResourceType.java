package mekanism.common.content.qio;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.common.lib.inventory.HashedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** An immutable, amount-independent QIO resource template. */
public final class QIOResourceType {

    static final int DATA_VERSION = 3;
    private static final int LEGACY_DATA_VERSION = 2;
    private static final UUID LOOKUP_UUID = new UUID(0, 0);

    private final UUID uuid;
    private final QIOResourceDescriptor descriptor;

    private QIOResourceType(UUID uuid, QIOResourceDescriptor descriptor) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    static QIOResourceType descriptorLookup(QIOResourceDescriptor descriptor) {
        return forDescriptor(LOOKUP_UUID, descriptor);
    }

    static QIOResourceType itemLookup(HashedItem item) {
        Objects.requireNonNull(item, "item");
        return descriptorLookup(QIOResourceCodecs.item(item.createStack(1)));
    }

    static QIOResourceType fluidLookup(FluidStack fluid) {
        return descriptorLookup(QIOResourceCodecs.fluid(fluid));
    }

    static QIOResourceType gasLookup(GasStack gas) {
        return descriptorLookup(QIOResourceCodecs.gas(gas));
    }

    public static QIOResourceType forDescriptor(UUID uuid, QIOResourceDescriptor descriptor) {
        return new QIOResourceType(uuid, descriptor);
    }

    public static QIOResourceType forItem(UUID uuid, HashedItem item) {
        Objects.requireNonNull(item, "item");
        return forDescriptor(uuid, QIOResourceCodecs.item(item.createStack(1)));
    }

    public static QIOResourceType forFluid(UUID uuid, FluidStack fluid) {
        return forDescriptor(uuid, QIOResourceCodecs.fluid(fluid));
    }

    public static QIOResourceType forGas(UUID uuid, GasStack gas) {
        return forDescriptor(uuid, QIOResourceCodecs.gas(gas));
    }

    static QIOResourceType read(UUID fileUUID, NBTTagCompound data) {
        int version = data.getInteger("version");
        if (version != LEGACY_DATA_VERSION && version != DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported QIO resource type version: " + version);
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
        NBTTagCompound payload = data.getCompoundTag("payload");
        QIOResourceDescriptor descriptor;
        if (version == LEGACY_DATA_VERSION) {
            QIOResourceKind kind = QIOResourceKind.byName(data.getString("kind"));
            if (kind == null || !kind.isBuiltin()) {
                throw new IllegalArgumentException("Unknown legacy QIO resource kind: " + data.getString("kind"));
            }
            descriptor = QIOResourceDescriptor.persisted(kind.getCodecId(), kind.getFamily(), 1,
                  QIOStorageUnits.getUnitsPerResource(kind), payload);
        } else {
            if (!data.hasKey("codec", NBT.TAG_STRING) || !data.hasKey("family", NBT.TAG_STRING) ||
                  !data.hasKey("codecVersion", NBT.TAG_INT) ||
                  !data.hasKey("storageUnitsPerUnit", NBT.TAG_LONG) ||
                  !data.hasKey("payload", NBT.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Incomplete QIO resource type metadata");
            }
            ResourceLocation codecId;
            try {
                codecId = new ResourceLocation(data.getString("codec"));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid QIO resource codec id: " + data.getString("codec"), e);
            }
            descriptor = QIOResourceDescriptor.persisted(codecId, data.getString("family"),
                  data.getInteger("codecVersion"), data.getLong("storageUnitsPerUnit"), payload);
        }
        return forDescriptor(fileUUID, descriptor);
    }

    NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("version", DATA_VERSION);
        data.setString("uuid", uuid.toString());
        data.setString("family", descriptor.getFamily());
        data.setString("codec", descriptor.getCodecId().toString());
        data.setInteger("codecVersion", descriptor.getCodecVersion());
        data.setLong("storageUnitsPerUnit", descriptor.getStorageUnitsPerUnit());
        data.setTag("payload", descriptor.getPayload());
        return data;
    }

    public UUID getUUID() {
        return uuid;
    }

    @Nonnull
    public QIOResourceDescriptor getDescriptor() {
        return descriptor;
    }

    @Nonnull
    public ResourceLocation getCodecId() {
        return descriptor.getCodecId();
    }

    @Nonnull
    public String getFamily() {
        return descriptor.getFamily();
    }

    public int getCodecVersion() {
        return descriptor.getCodecVersion();
    }

    public long getStorageUnitsPerUnit() {
        return descriptor.getStorageUnitsPerUnit();
    }

    public boolean isResolved() {
        return descriptor.isResolved();
    }

    /** @deprecated Use {@link #getDescriptor()} or {@link #getCodecId()}. */
    @Deprecated
    @Nullable
    public QIOResourceKind getKind() {
        return QIOResourceKind.fromDescriptor(descriptor);
    }

    @Nonnull
    public ItemStack createItemStack(int amount) {
        ItemStack template = descriptor.resolve(QIOResourceCodecs.ITEM_STACK);
        if (template == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        template.setCount(amount);
        return template;
    }

    @Nullable
    public FluidStack createFluidStack(int amount) {
        FluidStack template = descriptor.resolve(QIOResourceCodecs.FLUID_STACK);
        return template == null || amount <= 0 ? null : new FluidStack(template, amount);
    }

    @Nullable
    public GasStack createGasStack(int amount) {
        GasStack template = descriptor.resolve(QIOResourceCodecs.GAS_STACK);
        return template == null || amount <= 0 ? null : new GasStack(template.getGas(), amount);
    }

    @Nullable
    public HashedItem getItemType() {
        ItemStack stack = createItemStack(1);
        return stack.isEmpty() ? null : HashedItem.create(stack);
    }

    @Nullable
    public FluidStack getFluidType() {
        return createFluidStack(1);
    }

    @Nullable
    public Gas getGasType() {
        GasStack stack = createGasStack(1);
        return stack == null ? null : stack.getGas();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof QIOResourceType other && descriptor.equals(other.descriptor);
    }

    @Override
    public int hashCode() {
        return descriptor.hashCode();
    }
}
