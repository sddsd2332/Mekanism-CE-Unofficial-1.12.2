package mekanism.common.multiblock;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.TestBootstrap;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockCacheOptimizationTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void gasCacheReusesOnlyMatchingGasStacks() {
        Gas firstGas = new Gas("multiblock_cache_first", 0x123456);
        Gas secondGas = new Gas("multiblock_cache_second", 0x654321);
        TestCache cache = new TestCache();
        TestData data = new TestData();
        GasStack source = new GasStack(firstGas, 10);
        data.gas = source;

        cache.sync(data);
        GasStack cached = cache.gas;
        assertNotSame(source, cached);

        data.gas = new GasStack(firstGas, 25);
        cache.sync(data);
        assertSame(cached, cache.gas);
        assertEquals(25, cache.gas.amount);

        data.gas = new GasStack(secondGas, 25);
        cache.sync(data);
        assertNotSame(cached, cache.gas);
        assertSame(secondGas, cache.gas.getGas());

        data.gas = null;
        cache.sync(data);
        assertNull(cache.gas);
    }

    @Test
    void fluidCacheReusesMatchingNbtAndReplacesChangedNbt() {
        TestCache cache = new TestCache();
        TestData data = new TestData();
        FluidStack source = taggedWater(10, "cold");
        data.fluid = source;

        cache.sync(data);
        FluidStack cached = cache.fluid;
        assertNotSame(source, cached);

        data.fluid = taggedWater(30, "cold");
        cache.sync(data);
        assertSame(cached, cache.fluid);
        assertEquals(30, cache.fluid.amount);

        data.fluid = taggedWater(30, "hot");
        cache.sync(data);
        assertNotSame(cached, cache.fluid);
        assertEquals("hot", cache.fluid.tag.getString("temperature"));
    }

    @Test
    void inventoryCacheOnlyReplacesStacksWhenIdentityDataChanges() {
        TestCache cache = new TestCache();
        TestData data = new TestData();
        ItemStack source = taggedIron(4, "first");
        data.slot.setStackUnchecked(source);

        cache.sync(data);
        ItemStack cached = cache.getInventorySlots(null).get(0).getStack();
        assertNotSame(source, cached);

        data.slot.setStackUnchecked(taggedIron(12, "first"));
        cache.sync(data);
        assertSame(cached, cache.getInventorySlots(null).get(0).getStack());
        assertEquals(12, cached.getCount());

        data.slot.setStackUnchecked(taggedIron(12, "second"));
        cache.sync(data);
        ItemStack replaced = cache.getInventorySlots(null).get(0).getStack();
        assertNotSame(cached, replaced);
        assertEquals("second", replaced.getTagCompound().getString("variant"));

        data.slot.setEmpty();
        cache.sync(data);
        assertTrue(cache.getInventorySlots(null).get(0).isEmpty());
    }

    @Test
    void canonicalCacheSyncClaimIsScopedByDimensionIdAndTick() {
        MultiblockManager<TestData> manager = new MultiblockManager<>("cache-sync-claim-test");

        assertTrue(manager.tryClaimCacheSync(0, "structure", 42));
        assertFalse(manager.tryClaimCacheSync(0, "structure", 42));
        assertTrue(manager.tryClaimCacheSync(0, "other-structure", 42));
        assertTrue(manager.tryClaimCacheSync(1, "structure", 42));
        assertTrue(manager.tryClaimCacheSync(0, "structure", 43));
    }

    private static FluidStack taggedWater(int amount, String temperature) {
        FluidStack stack = new FluidStack(FluidRegistry.WATER, amount);
        stack.tag = new NBTTagCompound();
        stack.tag.setString("temperature", temperature);
        return stack;
    }

    private static ItemStack taggedIron(int amount, String variant) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT, amount);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("variant", variant);
        stack.setTagCompound(tag);
        return stack;
    }

    private static class TestData extends SynchronizedData<TestData> {

        private final BasicInventorySlot slot = BasicInventorySlot.at(this, 0, 0);
        private GasStack gas;
        private FluidStack fluid;

        private TestData() {
            inventorySlots.add(slot);
        }
    }

    private static class TestCache extends MultiblockCache<TestData> {

        private GasStack gas;
        private FluidStack fluid;

        @Override
        public void apply(TestData data) {
            applyInventory(data);
            data.gas = gas == null ? null : gas.copy();
            data.fluid = fluid == null ? null : fluid.copy();
        }

        @Override
        public void sync(TestData data) {
            syncInventory(data);
            gas = syncGasStack(gas, data.gas);
            fluid = syncFluidStack(fluid, data.fluid);
        }

        @Override
        public void load(NBTTagCompound nbtTags) {
        }

        @Override
        public void save(NBTTagCompound nbtTags) {
        }
    }
}
