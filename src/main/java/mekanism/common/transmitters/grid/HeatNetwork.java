package mekanism.common.transmitters.grid;

import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.transmitters.DynamicNetwork;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.tile.transmitter.TileEntityThermodynamicConductor;
import mekanism.common.transmitters.TransmitterImpl;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HeatNetwork extends DynamicNetwork<IHeatHandler, HeatNetwork, Void> {

    public double meanTemp = HeatAPI.AMBIENT_TEMP;

    public double heatLost = 0;
    public double heatTransferred = 0;

    public HeatNetwork() {
    }

    public HeatNetwork(Collection<HeatNetwork> networks) {
        networks.forEach(net -> {
            if (net != null) {
                adoptTransmittersAndAcceptorsFrom(net);
                net.deregister();
            }
        });
        register();
    }

    @Override
    public String getNeededInfo() {
        return "Not Applicable";
    }

    @Override
    public String getStoredInfo() {
        return MekanismUtils.getTemperatureDisplay(meanTemp, TemperatureUnit.KELVIN);
    }

    @Override
    public String getFlowInfo() {
        double transferred = sanitizeFlowValue(heatTransferred);
        double lost = sanitizeFlowValue(heatLost);
        return MekanismUtils.getTemperatureDisplay(transferred, TemperatureUnit.KELVIN) + " transferred to acceptors, " +
                MekanismUtils.getTemperatureDisplay(lost, TemperatureUnit.KELVIN) + " lost to environment, " +
                (transferred <= 0 && lost <= 0 ? "" : getEfficiency(transferred, lost) * 100 + "% efficiency");
    }

    static double getEfficiency(double transferred, double lost) {
        transferred = sanitizeFlowValue(transferred);
        lost = sanitizeFlowValue(lost);
        if (transferred <= 0) {
            return 0;
        } else if (lost <= 0) {
            return 1;
        } else if (transferred >= lost) {
            return 1 / (1 + lost / transferred);
        }
        double ratio = transferred / lost;
        return ratio / (1 + ratio);
    }

    @Override
    public void absorbBuffer(IGridTransmitter<IHeatHandler, HeatNetwork, Void> transmitter) {
    }

    @Override
    public void clampBuffer() {
    }

    @Override
    public void updateCapacity() {
        //The capacity is always zero so no point in doing calculations.
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        List<IGridTransmitter<IHeatHandler, HeatNetwork, Void>> currentTransmitters = new ArrayList<>(transmitters);
        double newHeatLost = 0;
        double newHeatTransferred = 0;
        boolean server = FMLCommonHandler.instance().getEffectiveSide() != null && FMLCommonHandler.instance().getEffectiveSide().isServer();

        if (server) {
            for (IGridTransmitter<IHeatHandler, HeatNetwork, Void> transmitter : currentTransmitters) {
                if (transmitter instanceof TransmitterImpl<?, ?, ?> imp && imp.getTileEntity() instanceof TileEntityThermodynamicConductor conductor) {
                    HeatTransfer transfer = conductor.simulate();
                    double adjacent = transfer.adjacentTransfer();
                    double environment = transfer.environmentTransfer();
                    newHeatTransferred = addFlowValue(newHeatTransferred, adjacent);
                    newHeatLost = addFlowValue(newHeatLost, environment);
                }
            }
        }
        double mean = 0;
        int count = 0;
        for (IGridTransmitter<IHeatHandler, HeatNetwork, Void> transmitter : currentTransmitters) {
            if (transmitter instanceof TransmitterImpl<?, ?, ?> imp && imp.getTileEntity() instanceof TileEntityThermodynamicConductor conductor) {
                double temperature = HeatAPI.sanitizeTemperature(conductor.buffer.getTemperature());
                count++;
                // Incremental averaging avoids overflowing a raw temperature sum.
                mean += (temperature - mean) / count;
            }
        }
        if (server) {
            heatLost = newHeatLost;
            heatTransferred = newHeatTransferred;
        }
        meanTemp = count == 0 ? HeatAPI.AMBIENT_TEMP : HeatAPI.sanitizeTemperature(mean);
    }

    private static double addFlowValue(double current, double value) {
        if (!HeatAPI.isFinite(value) || value <= 0) {
            return HeatAPI.isFinite(current) ? Math.max(0, current) : 0;
        }
        current = HeatAPI.isFinite(current) ? Math.max(0, current) : 0;
        return value >= HeatAPI.MAX_HEAT - current ? HeatAPI.MAX_HEAT : current + value;
    }

    private static double sanitizeFlowValue(double value) {
        return HeatAPI.isFinite(value) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, value)) : 0;
    }
}
