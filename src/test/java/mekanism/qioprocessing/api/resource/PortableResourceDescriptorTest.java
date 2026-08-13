package mekanism.qioprocessing.api.resource;

import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableResourceDescriptorTest {

    private static CapabilityItem capabilityItem;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
        capabilityItem = new CapabilityItem();
        capabilityItem.setRegistryName(new ResourceLocation("test", "portable_capability_item"));
        ForgeRegistries.ITEMS.register(capabilityItem);
    }

    @Test
    void itemIdentityIsAmountFreeAndNbtOrderIndependent() {
        ItemStack first = new ItemStack(Blocks.STONE, 32, 1);
        NBTTagCompound firstTag = new NBTTagCompound();
        firstTag.setString("z", "last");
        firstTag.setInteger("a", 4);
        first.setTagCompound(firstTag);
        ItemStack second = new ItemStack(Blocks.STONE, 1, 1);
        NBTTagCompound secondTag = new NBTTagCompound();
        secondTag.setInteger("a", 4);
        secondTag.setString("z", "last");
        second.setTagCompound(secondTag);

        PortableResourceDescriptor left = PortableResourceDescriptor.item(first);
        PortableResourceDescriptor right = PortableResourceDescriptor.item(second);
        PortableResourceDescriptor restored = PortableResourceDescriptor.read(left.write());

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertEquals(left, restored);
        assertEquals(1, restored.resolveItem().getCount());
        assertEquals(1, restored.resolveItem().getMetadata());
        assertEquals(firstTag, restored.resolveItem().getTagCompound());
    }

    @Test
    void fluidIdentityPreservesTagButNotAmount() {
        FluidStack fluid = new FluidStack(FluidRegistry.WATER, 8_000);
        fluid.tag = new NBTTagCompound();
        fluid.tag.setString("quality", "pure");

        PortableResourceDescriptor descriptor = PortableResourceDescriptor.fluid(fluid);
        FluidStack restored = PortableResourceDescriptor.read(descriptor.write()).resolveFluid();

        assertEquals(PortableResourceDescriptor.Kind.FLUID, descriptor.getKind());
        assertEquals(1, restored.amount);
        assertEquals(fluid.tag, restored.tag);
    }

    @Test
    void itemIdentityPreservesForgeCapabilities() {
        NBTTagCompound firstCapabilities = new NBTTagCompound();
        firstCapabilities.setLong("identity", 1);
        NBTTagCompound secondCapabilities = new NBTTagCompound();
        secondCapabilities.setLong("identity", 2);
        ItemStack first = new ItemStack(capabilityItem, 1, 0, firstCapabilities);
        ItemStack second = new ItemStack(capabilityItem, 1, 0, secondCapabilities);

        PortableResourceDescriptor left = PortableResourceDescriptor.item(first);
        PortableResourceDescriptor right = PortableResourceDescriptor.item(second);
        PortableResourceDescriptor restored = PortableResourceDescriptor.read(left.write());

        assertNotEquals(left, right);
        assertNotEquals(left.toString(), right.toString());
        assertEquals(left, restored);
        assertEquals(first.writeToNBT(new NBTTagCompound()).getCompoundTag("ForgeCaps"),
              restored.resolveItem().writeToNBT(new NBTTagCompound()).getCompoundTag("ForgeCaps"));
    }

    @Test
    void missingRegistryEntriesRemainDiagnosableWithoutResolving() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("kind", PortableResourceDescriptor.Kind.ITEM.name());
        data.setString("registryName", "missing_test_mod:removed_item");
        data.setInteger("metadata", 7);

        PortableResourceDescriptor descriptor = PortableResourceDescriptor.read(data);

        assertEquals("missing_test_mod:removed_item", descriptor.getRegistryName());
        assertEquals("ITEM|missing_test_mod:removed_item|7|", descriptor.toString());
        assertTrue(descriptor.resolveItem().isEmpty());
        assertNull(descriptor.resolveFluid());
        assertNull(descriptor.resolveGas());
        assertFalse(descriptor.write().isEmpty());
    }

    private static final class CapabilityItem extends net.minecraft.item.Item {

        @Override
        public ICapabilityProvider initCapabilities(ItemStack stack,
              @Nullable NBTTagCompound nbt) {
            return new CapabilityState(nbt);
        }
    }

    private static final class CapabilityState implements ICapabilityProvider,
          INBTSerializable<NBTTagCompound> {

        private NBTTagCompound state;

        private CapabilityState(@Nullable NBTTagCompound state) {
            this.state = state == null ? new NBTTagCompound() : state.copy();
        }

        @Override
        public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
            return false;
        }

        @Nullable
        @Override
        public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
            return null;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            return state.copy();
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbt) {
            state = nbt.copy();
        }
    }
}
