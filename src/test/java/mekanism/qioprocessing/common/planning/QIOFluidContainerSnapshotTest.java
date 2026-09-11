package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.common.util.QIORecipeStackUtils;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QIOFluidContainerSnapshotTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void capturesCapabilityOnlyFluidWithoutSerializingForgeCaps() {
        CapabilityOnlyFluidItem item = new CapabilityOnlyFluidItem();
        NBTTagCompound capabilityData = new NBTTagCompound();
        capabilityData.setBoolean("filled", true);
        ItemStack source = new ItemStack(item, 1, 0, capabilityData);

        FluidStack captured = QIOFluidContainerSnapshot.capture(source);

        assertNotNull(captured);
        assertEquals(FluidRegistry.WATER, captured.getFluid());
        assertEquals(1_000, captured.amount);
        assertEquals(0, item.serializeCalls);

        ItemStack capabilityNeutral = QIORecipeStackUtils.copyForRecipeSelection(source);
        assertNull(QIOFluidContainerSnapshot.capture(capabilityNeutral));
        assertEquals(0, item.serializeCalls);
    }

    @Test
    void roundTripsTheMinimalFrozenFluidCacheWithoutSerializingForgeCaps() {
        CapabilityOnlyFluidItem item = new CapabilityOnlyFluidItem();
        NBTTagCompound capabilityData = new NBTTagCompound();
        capabilityData.setBoolean("filled", true);
        ItemStack source = new ItemStack(item, 1, 0, capabilityData);

        QIOForgeRecipeData.FrozenFluid captured =
              QIOForgeRecipeData.FrozenFluid.capture(source);
        assertNotNull(captured);
        QIOForgeRecipeData.FrozenFluid restored =
              QIOForgeRecipeData.FrozenFluid.read(captured.write());
        FluidStack fluid = restored.resolve();

        assertNotNull(fluid);
        assertEquals(FluidRegistry.WATER, fluid.getFluid());
        assertEquals(1_000, fluid.amount);
        assertEquals(0, item.serializeCalls);
    }

    private static final class CapabilityOnlyFluidItem extends Item {

        private int serializeCalls;

        @Override
        public ICapabilityProvider initCapabilities(ItemStack stack,
              @Nullable NBTTagCompound nbt) {
            return new CapabilityOnlyFluidHandler(this, stack,
                  nbt != null && nbt.getBoolean("filled"));
        }
    }

    private static final class CapabilityOnlyFluidHandler
          implements ICapabilitySerializable<NBTTagCompound>, IFluidHandlerItem {

        private final CapabilityOnlyFluidItem owner;
        private final ItemStack container;
        @Nullable
        private FluidStack stored;

        private CapabilityOnlyFluidHandler(CapabilityOnlyFluidItem owner,
              ItemStack container, boolean filled) {
            this.owner = owner;
            this.container = container;
            stored = filled ? new FluidStack(FluidRegistry.WATER, 1_000) : null;
        }

        @Override
        public boolean hasCapability(@Nonnull Capability<?> capability,
              @Nullable EnumFacing facing) {
            return capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCapability(@Nonnull Capability<T> capability,
              @Nullable EnumFacing facing) {
            return hasCapability(capability, facing) ? (T) this : null;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            owner.serializeCalls++;
            NBTTagCompound data = new NBTTagCompound();
            data.setBoolean("filled", stored != null);
            return data;
        }

        @Override
        public void deserializeNBT(NBTTagCompound data) {
            stored = data.getBoolean("filled") ?
                  new FluidStack(FluidRegistry.WATER, 1_000) : null;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            return new IFluidTankProperties[]{new IFluidTankProperties() {
                @Nullable
                @Override
                public FluidStack getContents() {
                    return stored == null ? null : stored.copy();
                }

                @Override
                public int getCapacity() {
                    return 1_000;
                }

                @Override
                public boolean canFill() {
                    return false;
                }

                @Override
                public boolean canDrain() {
                    return true;
                }

                @Override
                public boolean canFillFluidType(FluidStack fluidStack) {
                    return false;
                }

                @Override
                public boolean canDrainFluidType(FluidStack fluidStack) {
                    return stored != null && stored.isFluidEqual(fluidStack);
                }
            }};
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (stored == null || resource == null || resource.amount < stored.amount ||
                !stored.isFluidEqual(resource)) {
                return null;
            }
            FluidStack drained = stored.copy();
            if (doDrain) stored = null;
            return drained;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (stored == null || maxDrain < stored.amount) return null;
            FluidStack drained = stored.copy();
            if (doDrain) stored = null;
            return drained;
        }

        @Nonnull
        @Override
        public ItemStack getContainer() {
            return container;
        }
    }
}
