package mekanism.qioprocessing.api.resource;

import mekanism.api.gas.GasStack;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Portable processing resource identity backed by the QIO codec descriptor.
 *
 * <p>The nested kind remains as a source-compatibility view for existing GUI and planner code;
 * custom codecs return {@code CUSTOM} and are handled by codec id/family.</p>
 */
public final class PortableResourceDescriptor implements Comparable<PortableResourceDescriptor> {

    public enum Kind {
        ITEM,
        FLUID,
        GAS,
        CUSTOM
    }

    private final QIOResourceDescriptor descriptor;
    private final String sortKey;
    private final int hashCode;

    private PortableResourceDescriptor(QIOResourceDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        sortKey = descriptor.toString();
        hashCode = descriptor.hashCode();
    }

    @Nonnull
    public static PortableResourceDescriptor fromDescriptor(@Nonnull QIOResourceDescriptor descriptor) {
        return new PortableResourceDescriptor(descriptor);
    }

    @Nonnull
    public static PortableResourceDescriptor named(@Nonnull Kind kind, @Nonnull String registryName,
          int metadata, @Nullable NBTTagCompound tag) {
        return new PortableResourceDescriptor(legacyDescriptor(kind, registryName, metadata, tag, null));
    }

    @Nonnull
    public static PortableResourceDescriptor item(@Nonnull ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot describe an empty item stack");
        }
        return new PortableResourceDescriptor(QIOResourceCodecs.item(stack));
    }

    @Nonnull
    public static PortableResourceDescriptor itemIgnoringCapabilities(@Nonnull ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot describe an empty item stack");
        }
        QIOResourceDescriptor descriptor = QIOResourceCodecs.item(stack);
        NBTTagCompound payload = descriptor.getPayload();
        payload.removeTag("ForgeCaps");
        return new PortableResourceDescriptor(QIOResourceDescriptor.persisted(descriptor.getCodecId(),
              descriptor.getFamily(), descriptor.getCodecVersion(), descriptor.getStorageUnitsPerUnit(), payload));
    }

    @Nonnull
    public static PortableResourceDescriptor fluid(@Nonnull FluidStack stack) {
        return fromDescriptor(QIOResourceCodecs.fluid(Objects.requireNonNull(stack, "stack")));
    }

    @Nonnull
    public static PortableResourceDescriptor gas(@Nonnull GasStack stack) {
        return fromDescriptor(QIOResourceCodecs.gas(Objects.requireNonNull(stack, "stack")));
    }

    @Nonnull
    public static PortableResourceDescriptor fromStorageEntry(@Nonnull QIOStorageEntry entry) {
        return fromDescriptor(Objects.requireNonNull(entry, "entry").getDescriptor());
    }

    @Nonnull
    public QIOResourceDescriptor getDescriptor() {
        return descriptor;
    }

    @Nonnull
    public Kind getKind() {
        if (QIOResourceCodecs.ITEM_STACK_ID.equals(descriptor.getCodecId())) {
            return Kind.ITEM;
        }
        if (QIOResourceCodecs.FLUID_STACK_ID.equals(descriptor.getCodecId())) {
            return Kind.FLUID;
        }
        if (QIOResourceCodecs.GAS_STACK_ID.equals(descriptor.getCodecId())) {
            return Kind.GAS;
        }
        return Kind.CUSTOM;
    }

    @Nonnull
    public String getFamily() {
        return descriptor.getFamily();
    }

    @Nonnull
    public ResourceLocation getCodecId() {
        return descriptor.getCodecId();
    }

    @Nonnull
    public String getRegistryName() {
        if (getKind() == Kind.ITEM) {
            ItemStack stack = resolveItem();
            if (!stack.isEmpty() && stack.getItem().getRegistryName() != null) {
                return stack.getItem().getRegistryName().toString();
            }
            String name = descriptor.getPayload().getString("id");
            return name.isEmpty() ? descriptor.getCodecId().toString() : name;
        }
        if (getKind() == Kind.FLUID) {
            FluidStack stack = resolveFluid();
            String name = stack == null ? null : FluidRegistry.getFluidName(stack);
            if (name != null) {
                return name;
            }
            name = descriptor.getPayload().getString("FluidName");
            return name.isEmpty() ? descriptor.getCodecId().toString() : name;
        }
        if (getKind() == Kind.GAS) {
            GasStack stack = resolveGas();
            if (stack != null && stack.getGas() != null) {
                return stack.getGas().getName();
            }
            String name = descriptor.getPayload().getString("gasName");
            return name.isEmpty() ? descriptor.getCodecId().toString() : name;
        }
        return descriptor.getCodecId().toString();
    }

    public int getMetadata() {
        return getKind() == Kind.ITEM ? descriptor.getPayload().getShort("Damage") : 0;
    }

    @Nullable
    public NBTTagCompound getTag() {
        NBTTagCompound payload = descriptor.getPayload();
        if (getKind() == Kind.ITEM && payload.hasKey("tag", 10)) {
            return payload.getCompoundTag("tag");
        }
        if (getKind() == Kind.FLUID && payload.hasKey("Tag", 10)) {
            return payload.getCompoundTag("Tag");
        }
        return null;
    }

    @Nonnull
    public PortableResourceDescriptor withoutCapabilities() {
        if (!hasCapabilities()) {
            return this;
        }
        NBTTagCompound payload = descriptor.getPayload();
        payload.removeTag("ForgeCaps");
        return new PortableResourceDescriptor(QIOResourceDescriptor.persisted(descriptor.getCodecId(),
              descriptor.getFamily(), descriptor.getCodecVersion(), descriptor.getStorageUnitsPerUnit(), payload));
    }

    public boolean hasCapabilities() {
        return getKind() == Kind.ITEM && descriptor.getPayload().hasKey("ForgeCaps", 10);
    }

    public boolean isResolved() {
        return descriptor.isResolved();
    }

    @Nonnull
    public ItemStack resolveItem() {
        ItemStack stack = descriptor.resolve(QIOResourceCodecs.ITEM_STACK);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Nullable
    public FluidStack resolveFluid() {
        return descriptor.resolve(QIOResourceCodecs.FLUID_STACK);
    }

    @Nullable
    public GasStack resolveGas() {
        return descriptor.resolve(QIOResourceCodecs.GAS_STACK);
    }

    @Nonnull
    public NBTTagCompound write() {
        return descriptor.write();
    }

    @Nonnull
    public static PortableResourceDescriptor read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "data");
        if (data.hasKey("codec", 8)) {
            return new PortableResourceDescriptor(QIOResourceDescriptor.read(data));
        }
        // Development-era processing records used the old three-value shape.
        Kind kind;
        try {
            kind = Kind.valueOf(data.getString("kind"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown portable QIO resource kind: " + data.getString("kind"), e);
        }
        NBTTagCompound tag = data.hasKey("tag", 10) ? data.getCompoundTag("tag") : null;
        NBTTagCompound capabilities = data.hasKey("capabilities", 10) ? data.getCompoundTag("capabilities") : null;
        return new PortableResourceDescriptor(legacyDescriptor(kind, data.getString("registryName"),
              kind == Kind.ITEM ? data.getInteger("metadata") : 0, tag, capabilities));
    }

    @Override
    public int compareTo(@Nonnull PortableResourceDescriptor other) {
        return sortKey.compareTo(Objects.requireNonNull(other, "other").sortKey);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof PortableResourceDescriptor other &&
              descriptor.equals(other.descriptor);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return sortKey;
    }

    private static QIOResourceDescriptor legacyDescriptor(Kind kind, String registryName, int metadata,
          @Nullable NBTTagCompound tag, @Nullable NBTTagCompound capabilities) {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.CUSTOM) {
            throw new IllegalArgumentException("Custom resources must be created from a QIO descriptor");
        }
        String name = Objects.requireNonNull(registryName, "registryName").trim();
        if (name.isEmpty() || name.length() > 256) {
            throw new IllegalArgumentException("Invalid legacy processing resource name");
        }
        NBTTagCompound payload = new NBTTagCompound();
        if (kind == Kind.ITEM) {
            payload.setString("id", name);
            payload.setByte("Count", (byte) 1);
            payload.setShort("Damage", (short) metadata);
            if (tag != null && !tag.isEmpty()) payload.setTag("tag", tag.copy());
            if (capabilities != null && !capabilities.isEmpty()) payload.setTag("ForgeCaps", capabilities.copy());
            return QIOResourceDescriptor.persisted(QIOResourceCodecs.ITEM_STACK_ID,
                  QIOResourceCodecs.ITEM_FAMILY, 1, QIOResourceCodecs.ITEM_STACK.getStorageUnitsPerUnit(), payload);
        }
        if (kind == Kind.FLUID) {
            payload.setString("FluidName", name);
            payload.setInteger("Amount", 1);
            if (tag != null && !tag.isEmpty()) payload.setTag("Tag", tag.copy());
            return QIOResourceDescriptor.persisted(QIOResourceCodecs.FLUID_STACK_ID,
                  QIOResourceCodecs.FLUID_FAMILY, 1, QIOResourceCodecs.FLUID_STACK.getStorageUnitsPerUnit(), payload);
        }
        payload.setString("gasName", name);
        payload.setInteger("amount", 1);
        return QIOResourceDescriptor.persisted(QIOResourceCodecs.GAS_STACK_ID,
              QIOResourceCodecs.GAS_FAMILY, 1, QIOResourceCodecs.GAS_STACK.getStorageUnitsPerUnit(), payload);
    }
}
