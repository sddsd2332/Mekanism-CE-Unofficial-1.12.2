package mekanism.common.recipe.cache;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImmutableResourceSnapshotTest {
    @BeforeAll
    static void bootstrap() { Bootstrap.register(); }

    @Test
    void quantityChangesDoNotInvokeStackCapabilityCallbacks() {
        CapabilityCountingItem item = new CapabilityCountingItem();
        ItemStack stack = new ItemStack(item, 8);
        int beforeCapture = item.initializations;
        ImmutableResourceSnapshot original = ImmutableResourceSnapshot.of(stack);
        assertTrue(item.initializations > beforeCapture, "Capture must construct a detached stack");
        int beforeArithmetic = item.initializations;
        ImmutableResourceSnapshot smaller = original.withAmount(3);
        original.withAmount(0);
        assertEquals(beforeArithmetic, item.initializations, "Value arithmetic invoked capability initialization");
        assertEquals(3, smaller.getAmount());
        assertEquals(8, original.getAmount());
        assertEquals(3, smaller.getItemCopy().getCount());
        assertEquals(ImmutableResourceSnapshot.of(new ItemStack(item, 3)), smaller);
    }

    @Test
    void derivedValuesRemainIsolatedFromSourceAndReturnedStacks() {
        ItemStack source = new ItemStack(Items.DIAMOND, 8);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("variant", "original");
        source.setTagCompound(tag);
        ImmutableResourceSnapshot original = ImmutableResourceSnapshot.of(source);
        ImmutableResourceSnapshot derived = original.withAmount(3);
        String key = derived.semanticKey();
        int hash = derived.hashCode();
        source.getTagCompound().setString("variant", "changed");
        ItemStack returned = derived.getItemCopy();
        returned.setCount(1);
        returned.getTagCompound().setString("variant", "returned");
        assertEquals(key, derived.semanticKey());
        assertEquals(hash, derived.hashCode());
        assertEquals("original", derived.getItemCopy().getTagCompound().getString("variant"));
        assertEquals(8, original.getItemCopy().getCount());
        assertEquals(3, derived.getItemCopy().getCount());
        assertEquals(original.identityKey(), derived.identityKey());
        assertNotEquals(original, derived);
    }

    @Test
    void fluidGasAndScalarQuantitiesKeepTheirExistingBoundsAndValueIdentity() {
        FluidStack water = new FluidStack(FluidRegistry.WATER, 100);
        ImmutableResourceSnapshot fluid = ImmutableResourceSnapshot.of(water).withAmount(25);
        water.amount = 500;
        assertEquals(25, fluid.getFluidCopy().amount);
        assertEquals(ImmutableResourceSnapshot.of(new FluidStack(FluidRegistry.WATER, 25)), fluid);
        Gas gas = new Gas("snapshot_gas", 0);
        ImmutableResourceSnapshot gasValue = ImmutableResourceSnapshot.of(new GasStack(gas, 8)).withAmount(2);
        assertEquals(2, gasValue.getGasCopy().amount);
        assertEquals(ImmutableResourceSnapshot.of(new GasStack(gas, 2)), gasValue);
        assertEquals(Integer.MAX_VALUE, fluid.withAmount(Long.MAX_VALUE).getAmount());
        assertEquals(Long.MAX_VALUE, ImmutableResourceSnapshot.descriptor("units", 1).withAmount(Long.MAX_VALUE).getAmount());
        assertTrue(fluid.withAmount(-1).isEmpty());
        assertTrue(ImmutableResourceSnapshot.empty().getItemCopy().isEmpty());
        assertNull(ImmutableResourceSnapshot.empty().getFluidCopy());
        assertNull(ImmutableResourceSnapshot.empty().getGasCopy());
    }

    private static final class CapabilityCountingItem extends Item {
        int initializations;
        @Override public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound tag) {
            initializations++;
            return null;
        }
    }
}
