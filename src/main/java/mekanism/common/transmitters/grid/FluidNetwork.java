package mekanism.common.transmitters.grid;

import mekanism.api.Coord4D;
import mekanism.api.transmitters.DynamicNetwork;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.content.network.distribution.FluidHandlerTarget;
import mekanism.common.util.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.Event;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.EnumSet;

public class FluidNetwork extends DynamicNetwork<IFluidHandler, FluidNetwork, FluidStack> {

    public int transferDelay = 0;

    public boolean didTransfer;
    public boolean prevTransfer;

    public float fluidScale;

    public Fluid refFluid;

    public FluidStack buffer;
    public int prevStored;

    public int prevTransferAmount = 0;

    private FluidHandlerTarget target;
    private FluidHandlerTarget reusableTarget;

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
        super.adoptTransmittersAndAcceptorsFrom(net);
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
            setBuffer(buffer);
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
        if (stack == null || stack.amount <= 0 || stack.getFluid() == null) {
            buffer = null;
        } else {
            int capacity = getCapacity();
            buffer = FluidContainerUtils.copyWithAmount(stack, capacity <= 0 ? stack.amount : Math.min(stack.amount, capacity));
        }
    }

    public int growBuffer(int amount) {
        if (buffer == null || amount <= 0) {
            return 0;
        }
        int current = buffer.amount;
        int capacity = getCapacity();
        int newAmount = capacity <= 0 ? current + amount : Math.min(capacity, current + amount);
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
            int stored = buffer != null ? buffer.amount : 0;
            if (stored != prevStored) {
                needsUpdate = true;
            }
            prevStored = stored;
            if (didTransfer != prevTransfer || needsUpdate) {
                MinecraftForge.EVENT_BUS.post(new FluidTransferEvent(this, buffer, didTransfer));
                needsUpdate = false;
            }
            prevTransfer = didTransfer;
        }
    }

    @Override
    public void onParallelTick() {
        if (buffer != null) {
            collectTargets(buffer);
        }
    }

    @Override
    public void onUpdate() {
        if (FMLCommonHandler.instance().getEffectiveSide() != null && FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            if (buffer == null) {
                return;
            }
            prevTransferAmount = tickEmit(buffer);
            if (prevTransferAmount > 0) {
                didTransfer = true;
                transferDelay = 2;
            }
            shrinkBuffer(prevTransferAmount);
        }
    }

    private void collectTargets(FluidStack fluidToSend) {
        target = null;
        FluidHandlerTarget collectedTarget = reusableTarget;
        if (collectedTarget == null) {
            collectedTarget = reusableTarget = new FluidHandlerTarget(fluidToSend, possibleAcceptors.size() * 2);
        } else {
            collectedTarget.reset(fluidToSend);
        }
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
                if (CapabilityUtils.hasCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side)) {
                    IFluidHandler acceptor = CapabilityUtils.getCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
                    if (acceptor != null && PipeUtils.canFill(acceptor, fluidToSend)) {
                        collectedTarget.addHandler(acceptor);
                    }
                }
            }
        }
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
