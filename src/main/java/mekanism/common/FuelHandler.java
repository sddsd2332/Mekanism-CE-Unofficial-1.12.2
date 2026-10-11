package mekanism.common;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.IFuel;
import buildcraft.api.mj.MjAPI;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import mekanism.api.gas.Gas;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.util.MekanismUtils;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.ModAPIManager;

@Deprecated
/**
 /**
 * @author sddsd2332
 * @reason 使用配方系统, 来处理这个.
 */
public class FuelHandler {

    public static Reference2ObjectMap<Gas, FuelGas> fuels = new Reference2ObjectOpenHashMap<>();

    public static void addGas(Gas gas, int burnTicks, double energyPerMilliBucket) {
        fuels.put(gas, new FuelGas(burnTicks, energyPerMilliBucket));
    }

    public static FuelGas getFuel(Gas gas) {
        FuelGas fuelGas = fuels.get(gas);
        if (fuelGas != null) {
            return fuelGas;
        }
        if (BCPresent() && gas.hasFluid() && BuildcraftFuelRegistry.fuel != null) {
            IFuel bcFuel = BuildcraftFuelRegistry.fuel.getFuel(new FluidStack(gas.getFluid(), 1));
            if (bcFuel != null) {
                FuelGas fuel = new FuelGas(bcFuel);
                fuels.put(gas, fuel);
                return fuel;
            }
        }
        return null;
    }

    public static boolean BCPresent() {
        return ModAPIManager.INSTANCE.hasAPI("BuildCraftAPI|fuels") && MekanismUtils.classExists("buildcraft.api.fuels.BuildcraftFuelRegistry") &&
                MekanismUtils.classExists("buildcraft.api.fuels.IFuel");
    }

    public static class FuelGas {

        public int burnTicks;
        public double energyPerTick;

        public FuelGas(int duration, double energyDensity) {
            burnTicks = duration;
            energyPerTick = energyDensity / duration;
        }

        public FuelGas(IFuel bcFuel) {
            burnTicks = bcFuel.getTotalBurningTime() / Fluid.BUCKET_VOLUME;

            // getPowerPerCycle returns value in 1 BuildCraft micro MJ
            // 1 BuildCraft MJ equals 20 RF
            energyPerTick = RFIntegration.fromRF(bcFuel.getPowerPerCycle() / (double) MjAPI.MJ * 20);
        }
    }
}
