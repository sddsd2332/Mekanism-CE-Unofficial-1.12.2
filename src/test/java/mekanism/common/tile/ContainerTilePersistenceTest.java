package mekanism.common.tile;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.common.TestBootstrap;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tier.FluidTankTier;
import mekanism.common.tier.GasTankTier;
import mekanism.common.tile.base.TileEntitySynchronized;
import mekanism.common.tile.factory.TileEntityUltimateFactory;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContainerTilePersistenceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void fluidTankRestoresTierAndContentsBeforeWorldAssignment() {
        TileEntityFluidTank source = new TileEntityFluidTank();
        source.tier = FluidTankTier.ULTIMATE;
        source.fluidTank.setFluid(new FluidStack(FluidRegistry.WATER, 200_000));

        NBTTagCompound saved = new NBTTagCompound();
        source.writeCustomNBT(saved);

        TileEntityFluidTank loaded = new TileEntityFluidTank();
        assertDoesNotThrow(() -> loaded.readFromNBT(saved));
        assertEquals(FluidTankTier.ULTIMATE, loaded.tier);
        assertNotNull(loaded.fluidTank.getFluid());
        assertEquals(200_000, loaded.fluidTank.getFluidAmount());
    }

    @Test
    void gasTankRestoresTierAndContentsBeforeWorldAssignment() {
        Gas gas = GasRegistry.register(new Gas("container_tile_persistence_gas_" + UUID.randomUUID(), 0x3F7FBF));
        TileEntityGasTank source = new TileEntityGasTank();
        source.tier = GasTankTier.ULTIMATE;
        source.gasTank.setGas(new GasStack(gas, 2_000_000));

        NBTTagCompound saved = new NBTTagCompound();
        source.writeCustomNBT(saved);

        TileEntityGasTank loaded = new TileEntityGasTank();
        assertDoesNotThrow(() -> loaded.readFromNBT(saved));
        assertEquals(GasTankTier.ULTIMATE, loaded.tier);
        assertNotNull(loaded.gasTank.getGas());
        assertEquals(2_000_000, loaded.gasTank.getStored());
    }

    @Test
    void energyCubeRestoresTierAndEnergyBeforeWorldAssignment() {
        TileEntityEnergyCube source = new TileEntityEnergyCube();
        source.tier = EnergyCubeTier.ULTIMATE;
        source.setEnergy(12_345_678);

        NBTTagCompound saved = new NBTTagCompound();
        source.writeCustomNBT(saved);

        TileEntityEnergyCube loaded = new TileEntityEnergyCube();
        assertDoesNotThrow(() -> loaded.readFromNBT(saved));
        assertEquals(EnergyCubeTier.ULTIMATE, loaded.tier);
        assertEquals(12_345_678, loaded.getEnergy(), 0.001);
    }

    @Test
    void ultimateFactoryRestoresDynamicTanksAndInventoryBeforeWorldAssignment() {
        Gas gas = GasRegistry.register(new Gas("factory_tile_persistence_gas_" + UUID.randomUUID(), 0x7F3FBF));
        TileEntityUltimateFactory source = new TileEntityUltimateFactory();
        source.setRecipeType(RecipeType.PRC);
        ((BasicInventorySlot) source.getRecipeInputSlot(0)).setStackUnchecked(new ItemStack(Items.IRON_INGOT, 8));
        source.getFluidTanks(null).get(0).setStackUnchecked(new FluidStack(FluidRegistry.WATER, 4_000));
        source.getGasTanks(null).get(0).setStackUnchecked(new GasStack(gas, 8_000));
        source.getGasTanks(null).get(1).setStackUnchecked(new GasStack(gas, 6_000));

        NBTTagCompound saved = new NBTTagCompound();
        source.writeCustomNBT(saved);

        TileEntityUltimateFactory loaded = new TileEntityUltimateFactory();
        assertDoesNotThrow(() -> loaded.readFromNBT(saved));
        assertEquals(RecipeType.PRC, loaded.getRecipeType());
        assertEquals(8, loaded.getRecipeInputSlot(0).getCount());
        assertEquals(4_000, loaded.getFluidTanks(null).get(0).getFluidAmount());
        assertEquals(8_000, loaded.getGasTanks(null).get(0).getStored());
        assertEquals(6_000, loaded.getGasTanks(null).get(1).getStored());
    }

    @Test
    void contentsLoadedBeforeWorldAssignmentDoNotLatchPendingMarkTask() throws Exception {
        TileEntityEnergyCube tile = new TileEntityEnergyCube();

        tile.onContentsChanged();

        java.lang.reflect.Field pendingMark = TileEntitySynchronized.class.getDeclaredField("inMarkTask");
        pendingMark.setAccessible(true);
        assertFalse(pendingMark.getBoolean(tile));
    }
}
