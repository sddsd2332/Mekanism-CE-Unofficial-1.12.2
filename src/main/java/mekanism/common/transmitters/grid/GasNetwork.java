package mekanism.common.transmitters.grid;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.network.distribution.GasHandlerTarget;
import mekanism.common.content.network.distribution.ResourceAcceptorCache;
import mekanism.common.util.EmitUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.Event;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * A DynamicNetwork extension created specifically for the transfer of Gasses. By default this is server-only, but if ticked on the client side and if it's posted events
 * are handled properly, it has the capability to visually display gasses network-wide.
 *
 * @author aidancbrady
 */
public class GasNetwork extends BufferedNetwork<IGasHandler, GasNetwork, GasStack> {

    public int transferDelay = 0;

    public boolean didTransfer;
    public boolean prevTransfer;

    public float gasScale;

    public Gas refGas;

    public GasStack buffer;
    public int prevStored;
    private float lastSentScale;
    private Gas lastSentGas;

    public int prevTransferAmount = 0;

    private final ResourceAcceptorCache<IGasHandler> acceptorCache = new ResourceAcceptorCache<>();
    private GasHandlerTarget target;
    private GasHandlerTarget reusableTarget;

    @Override
    protected double getSaveAmount() {
        return getBufferAmount();
    }

    @Override
    protected boolean hasIntegerShares() {
        return true;
    }

    public GasNetwork() {
    }

    public GasNetwork(Collection<GasNetwork> networks) {
        networks.forEach(net -> {
            if (net != null) {
                adoptTransmittersAndAcceptorsFrom(net);
                net.deregister();
            }
        });
        gasScale = getScale();
        register();
    }

    @Override
    public void adoptTransmittersAndAcceptorsFrom(GasNetwork net) {
        super.adoptTransmittersAndAcceptorsFrom(net);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            if (net.refGas != null && net.gasScale > gasScale) {
                gasScale = net.gasScale;
                refGas = net.refGas;
                setBuffer(net.buffer);

                net.gasScale = 0;
                net.refGas = null;
                net.setBuffer(null);
            }
        } else {
            if (net.buffer != null) {
                if (buffer == null) {
                    setBuffer(net.buffer);
                } else if (buffer.isGasEqual(net.buffer)) {
                    growBuffer(net.buffer.amount);
                } else if (net.buffer.amount > buffer.amount) {
                    setBuffer(net.buffer);
                }
                net.setBuffer(null);
            }
        }
    }

    @Nullable
    @Override
    public GasStack getBuffer() {
        return buffer;
    }

    @Override
    public void absorbBuffer(IGridTransmitter<IGasHandler, GasNetwork, GasStack> transmitter) {
        GasStack gas = transmitter.getBuffer();
        if (gas == null || gas.getGas() == null || gas.amount == 0) {
            return;
        }
        if (buffer == null || buffer.getGas() == null || buffer.amount == 0) {
            setBuffer(gas);
            transmitter.clearBuffer();
            return;
        }

        //TODO better multiple buffer impl
        if (buffer.isGasEqual(gas)) {
            growBuffer(gas.amount);
        }
        transmitter.clearBuffer();
    }

    @Override
    public void clampBuffer() {
        if (buffer != null && buffer.amount > getCapacity()) {
            setBuffer(getCapacity() <= 0 ? null : buffer);
        }
    }

    public int getGasNeeded() {
        return getCapacity() - getBufferAmount();
    }

    public int getBufferAmount() {
        return buffer == null ? 0 : buffer.amount;
    }

    public int emit(GasStack stack, boolean doTransfer) {
        if (buffer != null && buffer.getGas() != stack.getGas()) {
            return 0;
        }
        int toUse = Math.min(getGasNeeded(), stack.amount);
        if (doTransfer) {
            if (buffer == null) {
                setBuffer(stack.copy().withAmount(toUse));
            } else {
                growBuffer(toUse);
            }
        }
        return toUse;
    }

    public void setBuffer(@Nullable GasStack stack) {
        GasStack previous = buffer;
        if (stack == null || stack.getGas() == null || stack.amount <= 0) {
            buffer = null;
        } else {
            int capacity = getCapacity();
            buffer = stack.copy().withAmount(capacity <= 0 ? stack.amount : Math.min(stack.amount, capacity));
        }
        if (previous == null ? buffer != null : buffer == null || previous.amount != buffer.amount || !previous.isGasEqual(buffer))
            bufferChanged();
    }

    public int growBuffer(int amount) {
        if (buffer == null || amount <= 0) {
            return 0;
        }
        int current = buffer.amount;
        int capacity = getCapacity();
        int newAmount = (int) Math.min(capacity <= 0 ? Integer.MAX_VALUE : capacity, (long) current + amount);
        setBuffer(buffer.copy().withAmount(newAmount));
        return newAmount - current;
    }

    public int shrinkBuffer(int amount) {
        if (buffer == null || amount <= 0) {
            return 0;
        }
        int removed = Math.min(buffer.amount, amount);
        int remaining = buffer.amount - removed;
        setBuffer(remaining <= 0 ? null : buffer.copy().withAmount(remaining));
        return removed;
    }

    @Override
    public void preTick() {
        if (FMLCommonHandler.instance().getEffectiveSide() != null && FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            super.onUpdate();
            prevTransferAmount = 0;
            if (transferDelay == 0) {
                didTransfer = false;
            } else {
                transferDelay--;
            }

            float scale = getScale();
            Gas type = buffer == null ? null : buffer.getGas();
            prevStored = getBufferAmount();
            if (didTransfer != prevTransfer || needsUpdate || type != lastSentGas || scaleChanged(scale, lastSentScale)) {
                MinecraftForge.EVENT_BUS.post(new GasTransferEvent(this, buffer, didTransfer));
                lastSentScale = scale;
                lastSentGas = type;
                needsUpdate = false;
            }

            prevTransfer = didTransfer;
        }
    }

    @Override
    public void onParallelTick() {
        // Collect beside distribution: do not retain live targets across other networks' callbacks.
    }

    @Override
    public void onUpdate() {
        if (!isRemote()) {
            try {
                if (buffer == null) {
                    acceptorCache.clear();
                    return;
                }
                collectTargets(buffer);
                prevTransferAmount = tickEmit(buffer);
                if (prevTransferAmount > 0) {
                    didTransfer = true;
                    transferDelay = 2;
                }
                shrinkBuffer(prevTransferAmount);
            } finally {
                if (reusableTarget != null) reusableTarget.reset(null);
                target = null;
            }
        }
    }

    @Override
    public void deregister() {
        acceptorCache.clear();
        if (reusableTarget != null) reusableTarget.reset(null);
        target = null;
        super.deregister();
    }

    private void collectTargets(GasStack stack) {
        target = null;
        if (reusableTarget == null) reusableTarget = new GasHandlerTarget(stack, possibleAcceptors.size() * 2);
        else reusableTarget.reset(stack);
        GasHandlerTarget collectedTarget = reusableTarget;
        acceptorCache.collect(getWorld(), possibleAcceptors, acceptorDirections, acceptorRevision,
                Capabilities.GAS_HANDLER_CAPABILITY, collectedTarget::addHandler);
        target = collectedTarget;
    }

    private int tickEmit(GasStack stack) {
        GasHandlerTarget target = this.target;
        return target == null || target.getHandlerCount() == 0 ? 0 : EmitUtils.sendToAcceptors(target, stack.amount, stack);
    }

    @Override
    public void clientTick() {
        super.clientTick();
        gasScale = Math.max(gasScale, getScale());
        if (didTransfer && gasScale < 1) {
            gasScale = Math.max(getScale(), Math.min(1, gasScale + 0.02F));
        } else if (!didTransfer && gasScale > 0) {
            gasScale = Math.max(getScale(), Math.max(0, gasScale - 0.02F));
            if (gasScale == 0) {
                setBuffer(null);
            }
        }
    }

    public float getScale() {
        return Math.min(1, buffer == null || getCapacity() == 0 ? 0 : (float) buffer.amount / getCapacity());
    }

    @Override
    public String toString() {
        return "[GasNetwork] " + transmitters.size() + " transmitters, " + possibleAcceptors.size() + " acceptors.";
    }

    @Override
    public String getNeededInfo() {
        return Integer.toString(getGasNeeded());
    }

    @Override
    public String getStoredInfo() {
        return buffer != null ? buffer.getGas().getLocalizedName() + " (" + buffer.amount + ")" : "None";
    }

    @Override
    public String getFlowInfo() {
        return prevTransferAmount + "/t";
    }

    @Override
    public boolean isCompatibleWith(GasNetwork other) {
        return super.isCompatibleWith(other) && (this.buffer == null || other.buffer == null || this.buffer.isGasEqual(other.buffer));
    }

    @Override
    public boolean compatibleWithBuffer(GasStack buffer) {
        return super.compatibleWithBuffer(buffer) && (this.buffer == null || buffer == null || this.buffer.isGasEqual(buffer));
    }

    public static class GasTransferEvent extends Event {

        public final GasNetwork gasNetwork;

        public final GasStack transferType;
        public final boolean didTransfer;

        public GasTransferEvent(GasNetwork network, GasStack type, boolean did) {
            gasNetwork = network;
            transferType = type;
            didTransfer = did;
        }
    }
}
