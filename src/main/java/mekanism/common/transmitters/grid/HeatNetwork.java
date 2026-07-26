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
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HeatNetwork extends DynamicNetwork<IHeatHandler, HeatNetwork, Void> {

    static final int QUIET_TICKS_BEFORE_SLEEP = 20;
    static final int SLEEP_PROBE_INTERVAL = 20;

    public double meanTemp = HeatAPI.AMBIENT_TEMP;

    public double heatLost = 0;
    public double heatTransferred = 0;

    private List<TileEntityThermodynamicConductor> conductorSnapshot = Collections.emptyList();
    private double[] lastConductorHeat = new double[0];
    private int snapshotTransmitterCount = -1;
    private boolean snapshotDirty = true;
    private boolean sleeping;
    private int quietTicks;
    private int sleepingTicks;

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

    /** Wakes an idle network after heat, acceptors, or topology changed. */
    public void wakeUp() {
        sleeping = false;
        quietTicks = 0;
        sleepingTicks = 0;
    }

    @Override
    public void addNewTransmitters(Collection<IGridTransmitter<IHeatHandler, HeatNetwork, Void>> newTransmitters) {
        super.addNewTransmitters(newTransmitters);
        if (!newTransmitters.isEmpty()) {
            invalidateConductorSnapshot();
        }
    }

    @Override
    public void commit() {
        boolean topologyChanged = !transmittersToAdd.isEmpty();
        boolean acceptorsChanged = !changedAcceptors.isEmpty();
        super.commit();
        if (topologyChanged) {
            invalidateConductorSnapshot();
        } else if (acceptorsChanged) {
            wakeUp();
        }
    }

    @Override
    public void acceptorChanged(IGridTransmitter<IHeatHandler, HeatNetwork, Void> transmitter, EnumFacing side) {
        super.acceptorChanged(transmitter, side);
        wakeUp();
    }

    @Override
    public void adoptTransmittersAndAcceptorsFrom(HeatNetwork network) {
        super.adoptTransmittersAndAcceptorsFrom(network);
        invalidateConductorSnapshot();
    }

    @Override
    public boolean addTransmitter(IGridTransmitter<IHeatHandler, HeatNetwork, Void> transmitter) {
        boolean added = super.addTransmitter(transmitter);
        if (added) {
            invalidateConductorSnapshot();
        }
        return added;
    }

    @Override
    public boolean removeTransmitter(IGridTransmitter<IHeatHandler, HeatNetwork, Void> transmitter) {
        boolean removed = super.removeTransmitter(transmitter);
        if (removed) {
            invalidateConductorSnapshot();
        }
        return removed;
    }

    @Override
    public void deregister() {
        super.deregister();
        conductorSnapshot = Collections.emptyList();
        lastConductorHeat = new double[0];
        snapshotTransmitterCount = 0;
        snapshotDirty = false;
        wakeUp();
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

        List<TileEntityThermodynamicConductor> currentConductors = getConductorSnapshot();
        double newHeatLost = 0;
        double newHeatTransferred = 0;
        boolean server = FMLCommonHandler.instance().getEffectiveSide() != null && FMLCommonHandler.instance().getEffectiveSide().isServer();
        boolean safetyProbe = server && sleeping;

        if (server && !shouldRunServerSimulation()) {
            return;
        }

        if (server) {
            for (TileEntityThermodynamicConductor conductor : currentConductors) {
                HeatTransfer transfer = conductor.simulate();
                double adjacent = transfer.adjacentTransfer();
                double environment = transfer.environmentTransfer();
                newHeatTransferred = addFlowValue(newHeatTransferred, adjacent);
                newHeatLost = addFlowValue(newHeatLost, environment);
            }
        }
        double mean = 0;
        int count = 0;
        boolean heatChanged = false;
        for (TileEntityThermodynamicConductor conductor : currentConductors) {
            double temperature = HeatAPI.sanitizeTemperature(conductor.buffer.getTemperature());
            double heat = HeatAPI.sanitizeHeat(conductor.buffer.getHeat(), HeatAPI.multiplyHeat(temperature, conductor.buffer.getHeatCapacity()));
            if (Double.compare(lastConductorHeat[count], heat) != 0) {
                heatChanged = true;
            }
            lastConductorHeat[count] = heat;
            count++;
            // Incremental averaging avoids overflowing a raw temperature sum.
            mean += (temperature - mean) / count;
        }
        if (server) {
            heatLost = newHeatLost;
            heatTransferred = newHeatTransferred;
            updateSleepState(hasEffectiveActivity(newHeatTransferred, newHeatLost, heatChanged), safetyProbe);
        }
        meanTemp = count == 0 ? HeatAPI.AMBIENT_TEMP : HeatAPI.sanitizeTemperature(mean);
    }

    private void invalidateConductorSnapshot() {
        snapshotDirty = true;
        wakeUp();
    }

    List<TileEntityThermodynamicConductor> getConductorSnapshot() {
        // The count check also catches direct additions/removals through the legacy public transmitter set.
        if (snapshotDirty || snapshotTransmitterCount != transmitters.size()) {
            List<TileEntityThermodynamicConductor> snapshot = new ArrayList<>(transmitters.size());
            for (IGridTransmitter<IHeatHandler, HeatNetwork, Void> transmitter : transmitters) {
                if (transmitter instanceof TransmitterImpl<?, ?, ?> imp && imp.getTileEntity() instanceof TileEntityThermodynamicConductor conductor) {
                    snapshot.add(conductor);
                }
            }
            conductorSnapshot = snapshot;
            lastConductorHeat = new double[snapshot.size()];
            Arrays.fill(lastConductorHeat, Double.NaN);
            snapshotTransmitterCount = transmitters.size();
            snapshotDirty = false;
            wakeUp();
        }
        return conductorSnapshot;
    }

    boolean shouldRunServerSimulation() {
        if (!sleeping) {
            return true;
        }
        sleepingTicks++;
        if (sleepingTicks < SLEEP_PROBE_INTERVAL) {
            return false;
        }
        sleepingTicks = 0;
        return true;
    }

    void updateSleepState(boolean active, boolean safetyProbe) {
        if (active) {
            wakeUp();
        } else if (safetyProbe) {
            sleeping = true;
            quietTicks = QUIET_TICKS_BEFORE_SLEEP;
        } else if (++quietTicks >= QUIET_TICKS_BEFORE_SLEEP) {
            sleeping = true;
            sleepingTicks = 0;
        }
    }

    boolean isSleeping() {
        return sleeping;
    }

    static boolean hasEffectiveActivity(double transferred, double lost, boolean heatChanged) {
        return heatChanged || sanitizeFlowValue(transferred) > HeatAPI.EPSILON || sanitizeFlowValue(lost) > HeatAPI.EPSILON;
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
