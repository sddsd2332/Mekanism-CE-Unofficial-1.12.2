package mekanism.common.transmitters.grid;

import mekanism.api.Coord4D;
import mekanism.api.energy.EnergyStack;
import mekanism.api.transmitters.DynamicNetwork;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.base.EnergyAcceptorWrapper;
import mekanism.common.content.network.distribution.EnergyAcceptorTarget;
import mekanism.common.util.EmitUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.Event;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.EnumSet;

public class EnergyNetwork extends DynamicNetwork<EnergyAcceptorWrapper, EnergyNetwork, EnergyStack> {

    public double clientEnergyScale = 0;
    public EnergyStack buffer = new EnergyStack(0);
    private double lastPowerScale = 0;
    private double joulesTransmitted = 0;
    private double jouleBufferLastTick = 0;

    private EnergyAcceptorTarget target;

    public EnergyNetwork() {
    }

    public EnergyNetwork(Collection<EnergyNetwork> networks) {
        networks.forEach(net -> {
            if (net != null) {
                adoptTransmittersAndAcceptorsFrom(net);
                net.deregister();
            }
        });
        register();
    }

    @Override
    public void adoptTransmittersAndAcceptorsFrom(EnergyNetwork net) {
        if (net.jouleBufferLastTick > jouleBufferLastTick || net.clientEnergyScale > clientEnergyScale) {
            clientEnergyScale = net.clientEnergyScale;
            jouleBufferLastTick = net.jouleBufferLastTick;
            joulesTransmitted = net.joulesTransmitted;
            lastPowerScale = net.lastPowerScale;
        }
        growBuffer(net.buffer.amount);
        net.setBufferAmount(0);
        super.adoptTransmittersAndAcceptorsFrom(net);
    }

    public static double round(double d) {
        return Math.round(d * 10000) / 10000;
    }

    @Nullable
    @Override
    public EnergyStack getBuffer() {
        return buffer;
    }

    @Override
    public void absorbBuffer(IGridTransmitter<EnergyAcceptorWrapper, EnergyNetwork, EnergyStack> transmitter) {
        EnergyStack energy = transmitter.getBuffer();
        growBuffer(energy.amount);
        transmitter.clearBuffer();
    }

    @Override
    public void clampBuffer() {
        setBufferAmount(buffer.amount);
    }

    public double getEnergyNeeded() {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            return 0;
        }
        return getCapacityAsDouble() - getBufferAmount();
    }

    public double getBufferAmount() {
        return buffer.amount;
    }

    public double emit(double energyToSend, boolean doEmit) {
        double toUse = Math.min(getEnergyNeeded(), energyToSend);
        if (doEmit) {
            growBuffer(toUse);
        }
        return energyToSend - toUse;
    }

    public void setBufferAmount(double amount) {
        double capacity = getCapacityAsDouble();
        buffer.setAmountClamped(amount, capacity);
    }

    public double growBuffer(double amount) {
        if (amount <= 0) {
            return 0;
        }
        double current = buffer.amount;
        setBufferAmount(current + amount);
        return buffer.amount - current;
    }

    public double shrinkBuffer(double amount) {
        if (amount <= 0) {
            return 0;
        }
        double current = buffer.amount;
        setBufferAmount(current - amount);
        return current - buffer.amount;
    }

    @Override
    public String toString() {
        return "[EnergyNetwork] " + transmitters.size() + " transmitters, " + possibleAcceptors.size() + " acceptors.";
    }

    @Override
    public void preTick() {
        super.onUpdate();
        clearJoulesTransmitted();

        if (FMLCommonHandler.instance().getEffectiveSide() != null && FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            double currentPowerScale = getPowerScale();
            if (Math.abs(currentPowerScale - lastPowerScale) > 0.01 || (currentPowerScale != lastPowerScale && (currentPowerScale == 0 || currentPowerScale == 1))) {
                needsUpdate = true;
            }
            if (needsUpdate) {
                MinecraftForge.EVENT_BUS.post(new EnergyTransferEvent(this, currentPowerScale));
                lastPowerScale = currentPowerScale;
                needsUpdate = false;
            }
        }
    }

    @Override
    public void onParallelTick() {
        if (buffer.amount > 0) {
            collectTargets();
        }
    }

    @Override
    public void onUpdate() {
        if (FMLCommonHandler.instance().getEffectiveSide() != null && FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            if (buffer.amount > 0) {
                joulesTransmitted = tickEmit(buffer.amount);
                shrinkBuffer(joulesTransmitted);
            }
        }
    }

    private double tickEmit(double energyToSend) {
        EnergyAcceptorTarget target = this.target;
        return target == null || target.getHandlerCount() == 0 ? 0 : EmitUtils.sendToAcceptors(target, energyToSend);
    }

    private void collectTargets() {
        EnergyAcceptorTarget target = new EnergyAcceptorTarget(possibleAcceptors.size() * 2);
        for (Coord4D coord : possibleAcceptors) {
            EnumSet<EnumFacing> sides = acceptorDirections.get(coord);
            if (sides == null || sides.isEmpty()) {
                continue;
            }
            TileEntity tile = coord.getTileEntity(getWorld());
            if (tile == null) {
                continue;
            }
            for (EnumFacing side : sides) {
                EnergyAcceptorWrapper acceptor = EnergyAcceptorWrapper.get(tile, side);
                if (acceptor != null && acceptor.canReceiveEnergy(side) && acceptor.needsEnergy(side)) {
                    target.addHandler(side, acceptor);
                }
            }
        }
        this.target = target;
    }

    public double getPowerScale() {
        return Math.max(jouleBufferLastTick == 0 ? 0 : Math.min(Math.ceil(Math.log10(getPower()) * 2) / 10, 1), getCapacityAsDouble() == 0 ? 0 : buffer.amount / getCapacityAsDouble());
    }

    public void clearJoulesTransmitted() {
        jouleBufferLastTick = buffer.amount;
        joulesTransmitted = 0;
    }

    public double getPower() {
        return jouleBufferLastTick * 20;
    }

    @Override
    public String getNeededInfo() {
        return MekanismUtils.getEnergyDisplay(getEnergyNeeded());
    }

    @Override
    public String getStoredInfo() {
        return MekanismUtils.getEnergyDisplay(buffer.amount);
    }

    @Override
    public String getFlowInfo() {
        return MekanismUtils.getEnergyDisplay(joulesTransmitted) + "/t";
    }

    public static class EnergyTransferEvent extends Event {

        public final EnergyNetwork energyNetwork;

        public final double power;

        public EnergyTransferEvent(EnergyNetwork network, double currentPower) {
            energyNetwork = network;
            power = currentPower;
        }
    }
}
