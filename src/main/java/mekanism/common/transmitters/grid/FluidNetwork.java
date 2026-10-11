package mekanism.common.transmitters.grid;

import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.content.network.distribution.FluidHandlerTarget;
import mekanism.common.content.network.distribution.ResourceAcceptorCache;
import mekanism.common.util.EmitUtils;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.LangUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.Event;

import javax.annotation.Nullable;
import java.util.Collection;

public class FluidNetwork extends BufferedNetwork<IFluidHandler, FluidNetwork, FluidStack> {

    public int transferDelay = 0;

    public boolean didTransfer;
    public boolean prevTransfer;

    public float fluidScale;

    public Fluid refFluid;

    public FluidStack buffer;
    public int prevStored;
    private float lastSentScale;
    private FluidStack lastSentFluid;

    public int prevTransferAmount = 0;

    private final ResourceAcceptorCache<IFluidHandler> acceptorCache = new ResourceAcceptorCache<>();
    private FluidHandlerTarget target;
    private FluidHandlerTarget reusableTarget;

    @Override
    protected double getSaveAmount() {
        return getBufferAmount();
    }

    @Override
    protected boolean hasIntegerShares() {
        return true;
    }

    public FluidNetwork() {
    }

    public FluidNetwork(Collection<FluidNetwork> networks) {
        networks.forEach(net -> {
            if (net != null) {
                adoptTransmittersAndAcceptorsFrom(net);
                net.deregister();
            }
        });
        fluidScale = getScale();
        register();
    }

    @Override
    public void adoptTransmittersAndAcceptorsFrom(FluidNetwork net) {
        super.adoptTransmittersAndAcceptorsFrom(net);
        if (net.buffer != null) {
            if (buffer == null) {
                setBuffer(net.buffer);
            } else if (buffer.getFluid() == net.buffer.getFluid()) {
                growBuffer(net.buffer.amount);
            } else if (net.buffer.amount > buffer.amount) {
                setBuffer(net.buffer);
            }
            net.setBuffer(null);
        }
    }

    @Nullable
    @Override
    public FluidStack getBuffer() {
        return buffer;
    }

    @Override
    public void absorbBuffer(IGridTransmitter<IFluidHandler, FluidNetwork, FluidStack> transmitter) {
        FluidStack fluid = transmitter.getBuffer();
        if (fluid == null || fluid.amount == 0) {
            return;
        }
        if (buffer == null || buffer.amount == 0) {
            setBuffer(fluid);
            transmitter.clearBuffer();
            return;
        }

        //TODO better multiple buffer impl
        if (buffer.isFluidEqual(fluid)) {
            growBuffer(fluid.amount);
        }
        transmitter.clearBuffer();
    }

    @Override
    public void clampBuffer() {
        if (buffer != null && buffer.amount > getCapacity()) {
            setBuffer(getCapacity() <= 0 ? null : buffer);
        }
    }

    public int getFluidNeeded() {
        return getCapacity() - getBufferAmount();
    }

    public int getBufferAmount() {
        return buffer == null ? 0 : buffer.amount;
    }

    public int emit(FluidStack fluidToSend, boolean doTransfer) {
        if (fluidToSend == null || (buffer != null && buffer.getFluid() != fluidToSend.getFluid())) {
            return 0;
        }
        int toUse = Math.min(getFluidNeeded(), fluidToSend.amount);
        if (doTransfer) {
            if (buffer == null) {
                setBuffer(FluidContainerUtils.copyWithAmount(fluidToSend, toUse));
            } else {
                growBuffer(toUse);
            }
        }
        return toUse;
    }

    public void setBuffer(@Nullable FluidStack stack) {
        FluidStack previous = buffer;
        if (stack == null || stack.amount <= 0 || stack.getFluid() == null) {
            buffer = null;
        } else {
            int capacity = getCapacity();
            buffer = FluidContainerUtils.copyWithAmount(stack, capacity <= 0 ? stack.amount : Math.min(stack.amount, capacity));
        }
        if (previous == null ? buffer != null : buffer == null || previous.amount != buffer.amount || !previous.isFluidEqual(buffer))
            bufferChanged();
    }

    public int growBuffer(int amount) {
        if (buffer == null || amount <= 0) {
            return 0;
        }
        int current = buffer.amount;
        int capacity = getCapacity();
        int newAmount = (int) Math.min(capacity <= 0 ? Integer.MAX_VALUE : capacity, (long) current + amount);
        setBuffer(FluidContainerUtils.copyWithAmount(buffer, newAmount));
        return newAmount - current;
    }

    public int shrinkBuffer(int amount) {
        if (buffer == null || amount <= 0) {
            return 0;
        }
        int removed = Math.min(buffer.amount, amount);
        int remaining = buffer.amount - removed;
        setBuffer(remaining <= 0 ? null : FluidContainerUtils.copyWithAmount(buffer, remaining));
        return removed;
    }

    @Override
    public void preTick() {
        super.onUpdate();
        if (FMLCommonHandler.instance().getEffectiveSide() != null && FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            prevTransferAmount = 0;
            if (transferDelay == 0) {
                didTransfer = false;
            } else {
                transferDelay--;
            }
            float scale = getScale();
            boolean typeChanged = buffer == null ? lastSentFluid != null : lastSentFluid == null || !buffer.isFluidEqual(lastSentFluid);
            prevStored = getBufferAmount();
            if (didTransfer != prevTransfer || needsUpdate || typeChanged || scaleChanged(scale, lastSentScale)) {
                MinecraftForge.EVENT_BUS.post(new FluidTransferEvent(this, buffer, didTransfer));
                lastSentScale = scale;
                lastSentFluid = buffer == null ? null : buffer.copy();
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

    private void collectTargets(FluidStack fluidToSend) {
        target = null;
        if (reusableTarget == null) reusableTarget = new FluidHandlerTarget(fluidToSend, possibleAcceptors.size() * 2);
        else reusableTarget.reset(fluidToSend);
        FluidHandlerTarget collectedTarget = reusableTarget;
        acceptorCache.collect(getWorld(), possibleAcceptors, acceptorDirections, acceptorRevision,
                CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, (side, handler) -> collectedTarget.addHandler(handler));
        target = collectedTarget;
    }

    private int tickEmit(FluidStack fluidToSend) {
        FluidHandlerTarget target = this.target;
        return target == null || target.getHandlerCount() == 0 ? 0 : EmitUtils.sendToAcceptors(target, fluidToSend.amount, fluidToSend);
    }

    @Override
    public void clientTick() {
        super.clientTick();
        fluidScale = Math.max(fluidScale, getScale());
        if (didTransfer && fluidScale < 1) {
            fluidScale = Math.max(getScale(), Math.min(1, fluidScale + 0.02F));
        } else if (!didTransfer && fluidScale > 0) {
            fluidScale = getScale();
            if (fluidScale == 0) {
                setBuffer(null);
            }
        }
    }

    public float getScale() {
        return Math.min(1, buffer == null || getCapacity() == 0 ? 0 : (float) buffer.amount / getCapacity());
    }

    @Override
    public String toString() {
        return "[FluidNetwork] " + transmitters.size() + " transmitters, " + possibleAcceptors.size() + " acceptors.";
    }

    @Override
    public String getNeededInfo() {
        return (float) getFluidNeeded() / 1000F + " buckets";
    }

    @Override
    public String getStoredInfo() {
        return buffer != null ? LangUtils.localizeFluidStack(buffer) + " (" + buffer.amount + " mB)" : "None";
    }

    @Override
    public String getFlowInfo() {
        return prevTransferAmount + " mB/t";
    }

    @Override
    public boolean isCompatibleWith(FluidNetwork other) {
        return super.isCompatibleWith(other) && (this.buffer == null || other.buffer == null || this.buffer.isFluidEqual(other.buffer));
    }

    @Override
    public boolean compatibleWithBuffer(FluidStack buffer) {
        return super.compatibleWithBuffer(buffer) && (this.buffer == null || buffer == null || this.buffer.isFluidEqual(buffer));
    }

    public static class FluidTransferEvent extends Event {

        public final FluidNetwork fluidNetwork;

        public final FluidStack fluidType;
        public final boolean didTransfer;

        public FluidTransferEvent(FluidNetwork network, FluidStack type, boolean did) {
            fluidNetwork = network;
            fluidType = type;
            didTransfer = did;
        }
    }
}
