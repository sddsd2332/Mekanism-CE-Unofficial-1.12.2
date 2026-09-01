package mekanism.api.qio.resource;

import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Built-in resource codecs used by the legacy item, fluid and gas APIs. */
public final class QIOResourceCodecs {

    public static final String ITEM_FAMILY = "item";
    public static final String FLUID_FAMILY = "fluid";
    public static final String GAS_FAMILY = "gas";

    public static final ResourceLocation ITEM_STACK_ID = new ResourceLocation("mekanism", "item_stack");
    public static final ResourceLocation FLUID_STACK_ID = new ResourceLocation("mekanism", "fluid_stack");
    public static final ResourceLocation GAS_STACK_ID = new ResourceLocation("mekanism", "gas_stack");

    public static final QIOResourceCodec<ItemStack> ITEM_STACK = new ItemStackCodec();
    public static final QIOResourceCodec<FluidStack> FLUID_STACK = new FluidStackCodec();
    public static final QIOResourceCodec<GasStack> GAS_STACK = new GasStackCodec();

    private QIOResourceCodecs() {
    }

    static void registerBuiltins(QIOResourceCodecRegistry registry) {
        registry.register(ITEM_STACK);
        registry.register(FLUID_STACK);
        registry.register(GAS_STACK);
    }

    @Nonnull
    public static QIOResourceDescriptor item(@Nonnull ItemStack stack) {
        return QIOResourceDescriptor.of(ITEM_STACK, stack);
    }

    @Nonnull
    public static QIOResourceDescriptor fluid(@Nonnull FluidStack stack) {
        return QIOResourceDescriptor.of(FLUID_STACK, stack);
    }

    @Nonnull
    public static QIOResourceDescriptor gas(@Nonnull GasStack stack) {
        return QIOResourceDescriptor.of(GAS_STACK, stack);
    }

    private abstract static class BuiltinCodec<T> implements QIOResourceCodec<T> {

        private final ResourceLocation codecId;
        private final String family;
        private final Class<T> valueClass;
        private final long storageUnits;

        private BuiltinCodec(ResourceLocation codecId, String family, Class<T> valueClass, long storageUnits) {
            this.codecId = codecId;
            this.family = family;
            this.valueClass = valueClass;
            this.storageUnits = storageUnits;
        }

        @Nonnull
        @Override
        public ResourceLocation getCodecId() {
            return codecId;
        }

        @Nonnull
        @Override
        public String getFamily() {
            return family;
        }

        @Nonnull
        @Override
        public Class<T> getValueClass() {
            return valueClass;
        }

        @Override
        public long getStorageUnitsPerUnit() {
            return storageUnits;
        }

        final void requireVersion(int version) {
            if (version != getCodecVersion()) {
                throw new IllegalArgumentException("Unsupported " + codecId + " payload version: " + version);
            }
        }
    }

    private static final class ItemStackCodec extends BuiltinCodec<ItemStack> {

        private ItemStackCodec() {
            super(ITEM_STACK_ID, ITEM_FAMILY, ItemStack.class, 1_000L);
        }

        @Nonnull
        @Override
        public ItemStack normalize(@Nonnull ItemStack value) {
            Objects.requireNonNull(value, "Item resource cannot be null");
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Item resource cannot be empty");
            }
            ItemStack copy = value.copy();
            copy.setCount(1);
            return copy;
        }

        @Override
        public boolean sameType(@Nonnull ItemStack first, @Nonnull ItemStack second) {
            return writeTemplate(normalize(first)).equals(writeTemplate(normalize(second)));
        }

        @Override
        public int typeHash(@Nonnull ItemStack value) {
            return writeTemplate(normalize(value)).hashCode();
        }

        @Nonnull
        @Override
        public NBTTagCompound writeTemplate(@Nonnull ItemStack value) {
            return normalize(value).writeToNBT(new NBTTagCompound());
        }

        @Nonnull
        @Override
        public ItemStack readTemplate(@Nonnull NBTTagCompound payload, int codecVersion) {
            requireVersion(codecVersion);
            return normalize(new ItemStack(Objects.requireNonNull(payload, "Item payload cannot be null")));
        }
    }

    private static final class FluidStackCodec extends BuiltinCodec<FluidStack> {

        private FluidStackCodec() {
            super(FLUID_STACK_ID, FLUID_FAMILY, FluidStack.class, 1L);
        }

        @Nonnull
        @Override
        public FluidStack normalize(@Nonnull FluidStack value) {
            Objects.requireNonNull(value, "Fluid resource cannot be null");
            if (value.getFluid() == null) {
                throw new IllegalArgumentException("Fluid resource cannot be empty");
            }
            return new FluidStack(value, 1);
        }

        @Override
        public boolean sameType(@Nonnull FluidStack first, @Nonnull FluidStack second) {
            return writeTemplate(normalize(first)).equals(writeTemplate(normalize(second)));
        }

        @Override
        public int typeHash(@Nonnull FluidStack value) {
            return writeTemplate(normalize(value)).hashCode();
        }

        @Nonnull
        @Override
        public NBTTagCompound writeTemplate(@Nonnull FluidStack value) {
            NBTTagCompound payload = new NBTTagCompound();
            normalize(value).writeToNBT(payload);
            return payload;
        }

        @Nonnull
        @Override
        public FluidStack readTemplate(@Nonnull NBTTagCompound payload, int codecVersion) {
            requireVersion(codecVersion);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(
                  Objects.requireNonNull(payload, "Fluid payload cannot be null"));
            if (stack == null) {
                throw new IllegalArgumentException("Fluid resource is unavailable");
            }
            return normalize(stack);
        }
    }

    private static final class GasStackCodec extends BuiltinCodec<GasStack> {

        private GasStackCodec() {
            super(GAS_STACK_ID, GAS_FAMILY, GasStack.class, 1L);
        }

        @Nonnull
        @Override
        public GasStack normalize(@Nonnull GasStack value) {
            Objects.requireNonNull(value, "Gas resource cannot be null");
            if (value.getGas() == null) {
                throw new IllegalArgumentException("Gas resource cannot be empty");
            }
            return new GasStack(value.getGas(), 1);
        }

        @Override
        public boolean sameType(@Nonnull GasStack first, @Nonnull GasStack second) {
            return writeTemplate(normalize(first)).equals(writeTemplate(normalize(second)));
        }

        @Override
        public int typeHash(@Nonnull GasStack value) {
            return writeTemplate(normalize(value)).hashCode();
        }

        @Nonnull
        @Override
        public NBTTagCompound writeTemplate(@Nonnull GasStack value) {
            NBTTagCompound payload = new NBTTagCompound();
            normalize(value).write(payload);
            return payload;
        }

        @Nonnull
        @Override
        public GasStack readTemplate(@Nonnull NBTTagCompound payload, int codecVersion) {
            requireVersion(codecVersion);
            GasStack stack = GasStack.readFromNBT(Objects.requireNonNull(payload, "Gas payload cannot be null"));
            if (stack == null) {
                throw new IllegalArgumentException("Gas resource is unavailable");
            }
            return normalize(stack);
        }
    }
}
