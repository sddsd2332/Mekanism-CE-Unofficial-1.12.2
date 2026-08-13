package mekanism.qioprocessing.api.resource;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.qio.external.QIOStorageEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * World-independent, amount-free identity used by QIO processing plans and persistence.
 */
public final class PortableResourceDescriptor implements Comparable<PortableResourceDescriptor> {

    public enum Kind {
        ITEM,
        FLUID,
        GAS
    }

    private static final int MAX_REGISTRY_NAME_LENGTH = 256;

    private final Kind kind;
    private final String registryName;
    private final int metadata;
    @Nullable
    private final NBTTagCompound tag;
    @Nullable
    private final NBTTagCompound capabilities;
    private final String sortKey;
    private final int hashCode;

    private PortableResourceDescriptor(Kind kind, String registryName, int metadata,
          @Nullable NBTTagCompound tag) {
        this(kind, registryName, metadata, tag, null);
    }

    private PortableResourceDescriptor(Kind kind, String registryName, int metadata,
          @Nullable NBTTagCompound tag, @Nullable NBTTagCompound capabilities) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.registryName = requireName(registryName);
        if (kind != Kind.ITEM && metadata != 0) {
            throw new IllegalArgumentException("Only item resources may have metadata");
        }
        if (kind != Kind.ITEM && capabilities != null && !capabilities.isEmpty()) {
            throw new IllegalArgumentException("Only item resources may have capabilities");
        }
        this.metadata = metadata;
        this.tag = tag == null || tag.isEmpty() ? null : tag.copy();
        this.capabilities = capabilities == null || capabilities.isEmpty() ? null :
              capabilities.copy();
        String legacySortKey = kind.name() + '|' + this.registryName + '|' + metadata + '|' +
              canonicalTag(this.tag);
        sortKey = this.capabilities == null ? legacySortKey : legacySortKey + "|CAP|" +
              canonicalTag(this.capabilities);
        hashCode = this.capabilities == null ?
              Objects.hash(kind, this.registryName, metadata, this.tag) :
              Objects.hash(kind, this.registryName, metadata, this.tag, this.capabilities);
    }

    @Nonnull
    public static PortableResourceDescriptor named(@Nonnull Kind kind,
          @Nonnull String registryName, int metadata, @Nullable NBTTagCompound tag) {
        return new PortableResourceDescriptor(kind, registryName, metadata, tag);
    }

    @Nonnull
    public static PortableResourceDescriptor item(@Nonnull ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot describe an empty item stack");
        }
        ResourceLocation registryName = Item.REGISTRY.getNameForObject(stack.getItem());
        if (registryName == null) {
            throw new IllegalArgumentException("Item is not registered: " + stack.getItem());
        }
        NBTTagCompound serialized = stack.writeToNBT(new NBTTagCompound());
        NBTTagCompound capabilities = serialized.hasKey("ForgeCaps", 10) ?
              serialized.getCompoundTag("ForgeCaps") : null;
        return new PortableResourceDescriptor(Kind.ITEM, registryName.toString(),
              stack.getMetadata(), stack.getTagCompound(), capabilities);
    }

    @Nonnull
    public static PortableResourceDescriptor fluid(@Nonnull FluidStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.getFluid() == null) {
            throw new IllegalArgumentException("Cannot describe an empty fluid stack");
        }
        String registryName = FluidRegistry.getFluidName(stack);
        if (registryName == null || registryName.isEmpty()) {
            throw new IllegalArgumentException("Fluid is not registered: " + stack.getFluid());
        }
        return new PortableResourceDescriptor(Kind.FLUID, registryName, 0, stack.tag);
    }

    @Nonnull
    public static PortableResourceDescriptor gas(@Nonnull GasStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.getGas() == null) {
            throw new IllegalArgumentException("Cannot describe an empty gas stack");
        }
        return new PortableResourceDescriptor(Kind.GAS, stack.getGas().getName(), 0, null);
    }

    @Nonnull
    public static PortableResourceDescriptor fromStorageEntry(@Nonnull QIOStorageEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return switch (entry.getKind()) {
            case ITEM -> item(entry.getItem());
            case FLUID -> fluid(Objects.requireNonNull(entry.getFluid(), "fluid"));
            case GAS -> gas(Objects.requireNonNull(entry.getGas(), "gas"));
        };
    }

    @Nonnull
    public Kind getKind() {
        return kind;
    }

    @Nonnull
    public String getRegistryName() {
        return registryName;
    }

    public int getMetadata() {
        return metadata;
    }

    @Nullable
    public NBTTagCompound getTag() {
        return tag == null ? null : tag.copy();
    }

    @Nonnull
    public ItemStack resolveItem() {
        if (kind != Kind.ITEM) {
            return ItemStack.EMPTY;
        }
        Item item;
        try {
            item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(registryName));
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, 1, metadata,
              capabilities == null ? null : capabilities.copy());
        if (tag != null) {
            stack.setTagCompound(tag.copy());
        }
        return stack;
    }

    @Nullable
    public FluidStack resolveFluid() {
        if (kind != Kind.FLUID) {
            return null;
        }
        Fluid fluid = FluidRegistry.getFluid(registryName);
        return fluid == null ? null : new FluidStack(fluid, 1, tag == null ? null : tag.copy());
    }

    @Nullable
    public GasStack resolveGas() {
        if (kind != Kind.GAS) {
            return null;
        }
        Gas gas = GasRegistry.getGas(registryName);
        return gas == null ? null : new GasStack(gas, 1);
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("kind", kind.name());
        data.setString("registryName", registryName);
        if (kind == Kind.ITEM) {
            data.setInteger("metadata", metadata);
        }
        if (tag != null) {
            data.setTag("tag", tag.copy());
        }
        if (capabilities != null) {
            data.setTag("capabilities", capabilities.copy());
        }
        return data;
    }

    @Nonnull
    public static PortableResourceDescriptor read(@Nonnull NBTTagCompound data) {
        Objects.requireNonNull(data, "data");
        Kind kind;
        try {
            kind = Kind.valueOf(data.getString("kind"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown portable QIO resource kind: " + data.getString("kind"), e);
        }
        NBTTagCompound tag = data.hasKey("tag", 10) ? data.getCompoundTag("tag") : null;
        NBTTagCompound capabilities = data.hasKey("capabilities", 10) ?
              data.getCompoundTag("capabilities") : null;
        return new PortableResourceDescriptor(kind, data.getString("registryName"),
              kind == Kind.ITEM ? data.getInteger("metadata") : 0, tag, capabilities);
    }

    @Override
    public int compareTo(@Nonnull PortableResourceDescriptor other) {
        return sortKey.compareTo(Objects.requireNonNull(other, "other").sortKey);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PortableResourceDescriptor other)) {
            return false;
        }
        return metadata == other.metadata && kind == other.kind &&
              registryName.equals(other.registryName) && Objects.equals(tag, other.tag) &&
              Objects.equals(capabilities, other.capabilities);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return sortKey;
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "registryName");
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_REGISTRY_NAME_LENGTH) {
            throw new IllegalArgumentException("Resource registry name must contain 1.." +
                  MAX_REGISTRY_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String canonicalTag(@Nullable NBTTagCompound tag) {
        return tag == null ? "" : canonicalCopy(tag).toString();
    }

    private static NBTBase canonicalCopy(NBTBase value) {
        if (value instanceof NBTTagCompound compound) {
            NBTTagCompound copy = new NBTTagCompound();
            List<String> keys = new ArrayList<>(compound.getKeySet());
            Collections.sort(keys);
            for (String key : keys) {
                copy.setTag(key, canonicalCopy(compound.getTag(key)));
            }
            return copy;
        }
        if (value instanceof NBTTagList list) {
            NBTTagList copy = new NBTTagList();
            for (NBTBase element : list) {
                copy.appendTag(canonicalCopy(element));
            }
            return copy;
        }
        return value.copy();
    }
}
