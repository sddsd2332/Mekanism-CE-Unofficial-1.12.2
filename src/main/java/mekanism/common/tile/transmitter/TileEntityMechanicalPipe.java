package mekanism.common.tile.transmitter;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.TileNetworkList;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.fluid.DynamicFluidHandler;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.capabilities.holder.fluid.ProxiedFluidTankHolder;
import mekanism.common.capabilities.resolver.manager.FluidHandlerManager;
import mekanism.common.tier.AlloyTier;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.PipeTier;
import mekanism.common.transmitters.grid.FluidNetwork;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.PipeUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TileEntityMechanicalPipe extends TileEntityTransmitter<IFluidHandler, FluidNetwork, FluidStack> implements IMekanismFluidHandler {

    public PipeTier tier = PipeTier.BASIC;

    public float currentScale;

    public VariableCapacityFluidTank buffer = VariableCapacityFluidTank.create(this::getCapacity, BasicFluidTank.alwaysTrueBi, BasicFluidTank.alwaysTrueBi,
          BasicFluidTank.alwaysTrue, null);

    public FluidStack lastWrite;

    private int nextTransfer = 0;
    private final MechanicalPipeFluidTank fluidTank = new MechanicalPipeFluidTank();
    private final List<IExtendedFluidTank> fluidTanks = Collections.singletonList(fluidTank);
    private final DynamicFluidHandler fluidHandler = new DynamicFluidHandler(this::getPipeFluidTanks, this::canExtractFluidTank, this::canInsertFluidTank, this);
    private final FluidHandlerManager fluidHandlerManager = new FluidHandlerManager(ProxiedFluidTankHolder.create(
          this::canInsertFluid, this::canExtractFluid, this::getPipeFluidTanks
    ), fluidHandler);

    public TileEntityMechanicalPipe() {
        addCapabilityResolver(fluidHandlerManager);
    }

    @Override
    public BaseTier getBaseTier() {
        return tier.getBaseTier();
    }

    @Override
    public void setBaseTier(BaseTier baseTier) {
        tier = PipeTier.get(baseTier);
    }

    @Override
    public void doRestrictedTick() {
        super.doRestrictedTick();

        if (getWorld().isRemote) {
            return;
        }

        updateShare();

        if (nextTransfer > 0) {
            nextTransfer--;
            return;
        }

        List<EnumFacing> connections = getConnections(ConnectionType.PULL);
        if (connections.isEmpty()) {
            nextTransfer = 40;
            return;
        }

        IFluidHandler[] connectedAcceptors = PipeUtils.getConnectedAcceptors(connections, getPos(), getWorld());
        boolean successAtLeaseOnce = false;
        for (EnumFacing side : connections) {
            IFluidHandler container = connectedAcceptors[side.ordinal()];
            if (container == null) {
                continue;
            }
            FluidStack received = container.drain(getAvailablePull(), false);
            if (received != null && received.amount != 0 && takeFluid(received, false) == received.amount) {
                container.drain(takeFluid(received, true), true);
                successAtLeaseOnce = true;
            }
        }

        if (!successAtLeaseOnce) {
            nextTransfer = 20;
        }
    }

    @Override
    public void updateShare() {
        if (getTransmitter().hasTransmitterNetwork() && getTransmitter().getTransmitterNetworkSize() > 0) {
            FluidStack last = getSaveShare();
            if ((last != null && !(lastWrite != null && lastWrite.amount == last.amount && lastWrite.getFluid() == last.getFluid())) || (last == null && lastWrite != null)) {
                lastWrite = last;
                markChunkDirty();
                //markDirty();
//                this.world.markChunkDirty(this.pos, this);
            }
        }
    }

    private FluidStack getSaveShare() {
        FluidNetwork transmitterNetwork = getTransmitter().getTransmitterNetwork();
        FluidStack networkBuffer = transmitterNetwork.getBuffer();
        if (getTransmitter().hasTransmitterNetwork() && networkBuffer != null) {
            int bufferAmount = transmitterNetwork.getBufferAmount();
            int remain = bufferAmount % transmitterNetwork.transmittersSize();
            int toSave = bufferAmount / transmitterNetwork.transmittersSize();
            if (transmitterNetwork.firstTransmitter().equals(getTransmitter())) {
                toSave += remain;
            }
            return PipeUtils.copy(networkBuffer, toSave);
        }
        return null;
    }

    @Override
    public void onChunkUnload() {
        if (!getWorld().isRemote && getTransmitter().hasTransmitterNetwork()) {
            if (lastWrite != null && getTransmitter().getTransmitterNetwork().getBuffer() != null) {
                getTransmitter().getTransmitterNetwork().shrinkBuffer(lastWrite.amount);
            }
        }
        super.onChunkUnload();
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (nbtTags.hasKey("tier")) {
            tier = MekanismUtils.getByIndex(PipeTier.values(), nbtTags.getInteger("tier"), tier);
        }
        if (nbtTags.hasKey("cacheFluid")) {
            buffer.setFluid(FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("cacheFluid")));
        } else {
            buffer.setEmpty();
        }
        FluidStack stored = buffer.getFluid();
        lastWrite = stored == null ? null : PipeUtils.copy(stored, stored.amount);
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        if (lastWrite != null && lastWrite.amount > 0) {
            nbtTags.setTag("cacheFluid", lastWrite.writeToNBT(new NBTTagCompound()));
        } else {
            nbtTags.removeTag("cacheFluid");
        }
        nbtTags.setInteger("tier", tier.ordinal());
    }

    @Override
    public TransmissionType getTransmissionType() {
        return TransmissionType.FLUID;
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.MECHANICAL_PIPE;
    }

    @Override
    public boolean isValidAcceptor(TileEntity acceptor, EnumFacing side) {
        return PipeUtils.isValidAcceptorOnSide(acceptor, side);
    }

    @Override
    public boolean isValidTransmitter(TileEntity tileEntity) {
        if (!super.isValidTransmitter(tileEntity)) {
            return false;
        }
        if (!(tileEntity instanceof TileEntityMechanicalPipe pipe)) {
            return true;
        }
        FluidStack buffer = getBufferWithFallback();
        FluidStack otherBuffer = pipe.getBufferWithFallback();
        return buffer == null || otherBuffer == null || buffer.isFluidEqual(otherBuffer);
    }

    @Override
    public FluidNetwork createNewNetwork() {
        return new FluidNetwork();
    }

    @Override
    public FluidNetwork createNetworkByMerging(Collection<FluidNetwork> networks) {
        return new FluidNetwork(networks);
    }

    @Override
    protected boolean canHaveIncompatibleNetworks() {
        return true;
    }

    @Override
    public int getCapacity() {
        return tier.getPipeCapacity();
    }

    @Nullable
    @Override
    public FluidStack getBuffer() {
        return buffer == null ? null : buffer.getFluid();
    }

    @Override
    public void clearBuffer() {
        buffer.setEmpty();
        onContentsChanged();
    }

    @Override
    public void takeShare() {
        if (getTransmitter().hasTransmitterNetwork() && getTransmitter().getTransmitterNetwork().getBuffer() != null && lastWrite != null) {
            getTransmitter().getTransmitterNetwork().shrinkBuffer(lastWrite.amount);
            buffer.setFluid(lastWrite);
        }
    }

    public int getPullAmount() {
        return tier.getPipePullAmount();
    }

    @Override
    public IFluidHandler getCachedAcceptor(EnumFacing side) {
        TileEntity tile = getCachedTile(side);
        if (CapabilityUtils.hasCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite())) {
            return CapabilityUtils.getCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
        }
        return null;
    }

    public int getAvailablePull() {
        if (getTransmitter().hasTransmitterNetwork()) {
            return Math.min(getPullAmount(), getTransmitter().getTransmitterNetwork().getFluidNeeded());
        }
        return Math.min(getPullAmount(), buffer.getCapacity() - buffer.getFluidAmount());
    }

    public int takeFluid(FluidStack fluid, boolean doEmit) {
        FluidStack remainder = fluidTank.insert(fluid, Action.get(doEmit), AutomationType.INTERNAL);
        return fluid == null ? 0 : fluid.amount - (remainder == null ? 0 : remainder.amount);
    }

    @Override
    public boolean upgrade(AlloyTier tierOrdinal) {
        if (tier.ordinal() < BaseTier.ULTIMATE.ordinal() && tierOrdinal.ordinal() == tier.ordinal()) {
            tier = PipeTier.values()[tier.ordinal() + 1];
            markDirtyTransmitters();
            sendDesc = true;
            return true;
        }
        return false;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) throws Exception {
        tier = MekanismUtils.getByIndex(PipeTier.values(), dataStream.readInt(), tier);
        super.handlePacketData(dataStream);
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        data.add(tier.ordinal());
        super.getNetworkedData(data);
        return data;
    }

    private boolean canInsertFluidTank(int tank, @Nullable EnumFacing side) {
        return tank >= 0 && tank < getPipeFluidTanks(side).size() && canInsertFluidSide(side);
    }

    private boolean canExtractFluidTank(int tank, @Nullable EnumFacing side) {
        return tank >= 0 && tank < getPipeFluidTanks(side).size() && canExtractFluidSide(side);
    }

    private boolean canInsertFluidSide(@Nullable EnumFacing side) {
        if (side == null) {
            return true;
        }
        ConnectionType connectionType = getConnectionType(side);
        return connectionType == ConnectionType.NORMAL || connectionType == ConnectionType.PULL;
    }

    private boolean canExtractFluidSide(@Nullable EnumFacing side) {
        if (side == null) {
            return true;
        }
        ConnectionType connectionType = getConnectionType(side);
        return connectionType == ConnectionType.NORMAL || connectionType == ConnectionType.PUSH;
    }

    @Nonnull
    private List<IExtendedFluidTank> getPipeFluidTanks(@Nullable EnumFacing side) {
        return isRedstoneActivated() || side != null && !canConnect(side) ? Collections.emptyList() : fluidTanks;
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getFluidTanks(@Nullable EnumFacing side) {
        return fluidHandler.getFluidTanks(side);
    }

    public boolean canInsertFluid(@Nullable EnumFacing side) {
        return fluidHandler.canInsertFluid(side);
    }

    public boolean canExtractFluid(@Nullable EnumFacing side) {
        return fluidHandler.canExtractFluid(side);
    }

    @Nullable
    @Override
    public FluidStack insertFluid(int tank, @Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return fluidHandler.insertFluid(tank, stack, side, action);
    }

    @Nullable
    @Override
    public FluidStack extractFluid(int tank, int amount, @Nullable EnumFacing side, Action action) {
        return fluidHandler.extractFluid(tank, amount, side, action);
    }

    @Nullable
    @Override
    public FluidStack insertFluid(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return fluidHandler.insertFluid(stack, side, action);
    }

    @Nullable
    @Override
    public FluidStack extractFluid(int amount, @Nullable EnumFacing side, Action action) {
        return fluidHandler.extractFluid(amount, side, action);
    }

    @Nullable
    @Override
    public FluidStack extractFluid(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return fluidHandler.extractFluid(stack, side, action);
    }

    @Override
    public void onContentsChanged() {
        markChunkDirty();
    }

    private class MechanicalPipeFluidTank implements IExtendedFluidTank {

        @Nullable
        @Override
        public FluidStack getFluid() {
            FluidStack stack = getActiveFluid();
            return stack == null || stack.amount <= 0 ? null : stack;
        }

        @Override
        public int getFluidAmount() {
            FluidStack stack = getFluid();
            return stack == null ? 0 : stack.amount;
        }

        @Override
        public int getCapacity() {
            return getTransmitter().hasTransmitterNetwork() ? getTransmitter().getTransmitterNetwork().getCapacity() : TileEntityMechanicalPipe.this.getCapacity();
        }

        @Override
        public void setStack(@Nullable FluidStack stack) {
            setStack(stack, true);
        }

        @Override
        public void setStackUnchecked(@Nullable FluidStack stack) {
            setStack(stack, false);
        }

        private void setStack(@Nullable FluidStack stack, boolean validateStack) {
            if (stack == null || stack.amount <= 0) {
                setActiveFluid(null);
            } else if (!validateStack || isFluidValid(stack)) {
                setActiveFluid(new FluidStack(stack, Math.min(stack.amount, getCapacity())));
            } else {
                throw new RuntimeException("Invalid fluid for tank: " + stack.getFluid().getName() + " " + stack.amount);
            }
        }

        @Override
        public boolean isFluidValid(@Nullable FluidStack stack) {
            return stack != null && stack.getFluid() != null;
        }

        @Nullable
        @Override
        public FluidStack insert(@Nullable FluidStack stack, Action action, AutomationType automationType) {
            if (stack == null || stack.amount <= 0 || !isFluidValid(stack)) {
                return stack;
            }
            FluidStack stored = getFluid();
            if (stored != null && !stored.isFluidEqual(stack)) {
                return stack;
            }
            int toAdd = Math.min(stack.amount, getNeeded());
            if (toAdd <= 0) {
                return stack;
            }
            if (action.execute()) {
                if (stored == null) {
                    setActiveFluid(new FluidStack(stack, toAdd));
                } else {
                    setActiveFluid(PipeUtils.copy(stored, stored.amount + toAdd));
                }
            }
            return stack.amount == toAdd ? null : new FluidStack(stack, stack.amount - toAdd);
        }

        @Nullable
        @Override
        public FluidStack extract(int amount, Action action, AutomationType automationType) {
            FluidStack stored = getFluid();
            if (stored == null || amount <= 0) {
                return null;
            }
            int toRemove = Math.min(amount, stored.amount);
            if (toRemove <= 0) {
                return null;
            }
            FluidStack ret = new FluidStack(stored, toRemove);
            if (action.execute()) {
                setActiveFluid(stored.amount <= toRemove ? null : PipeUtils.copy(stored, stored.amount - toRemove));
            }
            return ret;
        }

        @Nullable
        private FluidStack getActiveFluid() {
            return getTransmitter().hasTransmitterNetwork() ? getTransmitter().getTransmitterNetwork().getBuffer() : buffer.getFluid();
        }

        private void setActiveFluid(@Nullable FluidStack stack) {
            FluidStack stored = stack == null || stack.amount <= 0 ? null : new FluidStack(stack, Math.min(stack.amount, getCapacity()));
            if (getTransmitter().hasTransmitterNetwork()) {
                getTransmitter().getTransmitterNetwork().setBuffer(stored);
            } else {
                buffer.setFluid(stored);
            }
            onContentsChanged();
        }

        @Override
        public void onContentsChanged() {
            TileEntityMechanicalPipe.this.onContentsChanged();
        }
    }
}
