package mekanism.common.tile.multiblock;

import mekanism.common.TestBootstrap;
import mekanism.api.heat.IHeatCapacitor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThermalEvaporationPersistenceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void nbtLoadDefersCapacityClampingUntilStructureRebuild() {
        TileEntityThermalEvaporationController controller = new TileEntityThermalEvaporationController();
        controller.height = 3;
        int storedAmount = controller.getMaxFluid() + 1_000;
        NBTTagCompound saved = new NBTTagCompound();
        NBTTagCompound inputTank = new NBTTagCompound();
        new FluidStack(FluidRegistry.WATER, storedAmount).writeToNBT(inputTank);
        saved.setTag("waterTank", inputTank);

        controller.readCustomNBT(saved);

        assertEquals(storedAmount, controller.inputTank.getFluidAmount());
    }

    @Test
    void nbtRoundTripsStoredHeatAndCapacity() {
        TileEntityThermalEvaporationController source = new TileEntityThermalEvaporationController();
        source.structured = true;
        IHeatCapacitor sourceHeat = source.getStructureHeatCapacitor();
        sourceHeat.setHeat(150_000);
        NBTTagCompound saved = new NBTTagCompound();

        source.writeCustomNBT(saved);
        TileEntityThermalEvaporationController loaded = new TileEntityThermalEvaporationController();
        loaded.readCustomNBT(saved);
        loaded.structured = true;
        IHeatCapacitor loadedHeat = loaded.getStructureHeatCapacitor();

        assertEquals(sourceHeat.getHeatCapacity(), loadedHeat.getHeatCapacity());
        assertEquals(sourceHeat.getHeat(), loadedHeat.getHeat());
        assertEquals(sourceHeat.getTemperature(), loadedHeat.getTemperature());
    }

    @Test
    void malformedThermalNbtCannotPoisonControllerState() {
        TileEntityThermalEvaporationController controller = new TileEntityThermalEvaporationController();
        NBTTagCompound saved = new NBTTagCompound();
        NBTTagCompound heat = new NBTTagCompound();
        heat.setDouble(mekanism.api.NBTConstants.HEAT_CAPACITY, Double.POSITIVE_INFINITY);
        heat.setDouble(mekanism.api.NBTConstants.STORED, Double.NaN);
        saved.setTag(mekanism.api.NBTConstants.HEAT_STORED, heat);

        controller.readCustomNBT(saved);
        controller.structured = true;
        IHeatCapacitor loadedHeat = controller.getStructureHeatCapacitor();

        assertTrue(Double.isFinite(loadedHeat.getHeatCapacity()));
        assertTrue(Double.isFinite(loadedHeat.getHeat()));
        assertTrue(Double.isFinite(loadedHeat.getTemperature()));
    }
}
