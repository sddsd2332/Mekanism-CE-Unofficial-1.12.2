package mekanism.common.tile.transmitter;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.*;
import mekanism.api.math.MathUtils;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.gas.DynamicGasHandler;
import mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder;
import mekanism.common.capabilities.resolver.manager.GasHandlerManager;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.tier.AlloyTier;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.TubeTier;
import mekanism.common.transmitters.grid.GasNetwork;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TileEntityPressurizedTube extends TileEntityTransmitter<IGasHandler, GasNetwork, GasStack> implements IMekanismGasHandler {

    public TubeTier tier = TubeTier.BASIC;

    public float currentScale;

    public GasTank buffer = new GasTank(getCapacity());

    public GasStack lastWrite;

    private int nextTransfer = 0;
    private final PressurizedTubeGasTank gasTank = new PressurizedTubeGasTank();
    private final List<IExtendedGasTank> gasTanks = Collections.singletonList(gasTank);
    private final DynamicGasHandler gasHandler = new DynamicGasHandler(this::getTubeGasTanks, this::canExtractGasTank, this::canInsertGasTank, this);
    private final GasHandlerManager gasHandlerManager = new GasHandlerManager(ProxiedGasTankHolder.create(
            this::canInsertGas, this::canExtractGas, this::getTubeGasTanks
    ), gasHandler);

    public TileEntityPressurizedTube() {
        addCapabilityResolver(gasHandlerManager);
    }

    @Override
    public BaseTier getBaseTier() {
        return tier.getBaseTier();
    }

    @Override
    public void setBaseTier(BaseTier baseTier) {
        tier = TubeTier.get(baseTier);
        buffer.setMaxGas(getCapacity());
    }

    @Override
    public void doRestrictedTick() {
        if (!getWorld().isRemote) {
            if (nextTransfer <= 0 && getAvailablePull() > 0) {
                byte pullMask = getPullConnectionMask();
                boolean successAtLeaseOnce = false;
                for (EnumFacing side : EnumFacing.VALUES) {
                    if (!connectionMapContainsSide(pullMask, side)) continue;
                    int available = getAvailablePull();
                    if (available <= 0) break;
                    IGasHandler container = getConnectedAcceptor(side, true);
                    if (container != null) {
                        GasStack bufferWithFallback = getBufferWithFallback();
                        Gas gasType = bufferWithFallback == null ? null : bufferWithFallback.getGas();
                        GasStack received = GasInventorySlot.extractGas(container, side.getOpposite(), gasType, available, false);
                        if (received != null && received.amount != 0 && takeGas(received, false) == received.amount) {
                            GasStack extracted = GasInventorySlot.extractGas(container, side.getOpposite(), received.getGas(), received.amount, true);
                            if (extracted != null && extracted.amount != 0) {
                                takeGas(extracted, true);
                                successAtLeaseOnce = true;
                            }
                        }
                    }
                }
                if (!successAtLeaseOnce) {
                    nextTransfer = 20;
                }
            } else if (nextTransfer > 0) {
                nextTransfer--;
            }
        } else {
            float targetScale = getTransmitter().hasTransmitterNetwork() ? getTransmitter().getTransmitterNetwork().gasScale : (float) buffer.getStored() / (float) buffer.getMaxGas();
            if (Math.abs(currentScale - targetScale) > 0.01) {
                currentScale = (9 * currentScale + targetScale) / 10;
            }
        }
        super.doRestrictedTick();
    }

    public int getAvailablePull() {
        if (getTransmitter().hasTransmitterNetwork()) {
            return Math.min(tier.getTubePullAmount(), getTransmitter().getTransmitterNetwork().getGasNeeded());
        }
        return Math.min(tier.getTubePullAmount(), buffer.getNeeded());
    }

    @Override
    public void updateShare() {
        GasStack last = !unloaded && getTransmitter().hasTransmitterNetwork() ? getSaveShare() : buffer.getGas();
        lastWrite = last == null ? null : last.copy();
    }

    private GasStack getSaveShare() {
        if (!getTransmitter().hasTransmitterNetwork()) return buffer.getGas();
        GasNetwork network = getTransmitter().getTransmitterNetwork();
        GasStack stored = network.getBuffer();
        int amount = (int) network.getSaveShare(getTransmitter());
        return stored == null || amount <= 0 ? null : new GasStack(stored.getGas(), amount);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (nbtTags.hasKey("tier")) {
            tier = MekanismUtils.getByIndex(TubeTier.values(), nbtTags.getInteger("tier"), tier);
        }
        buffer.setMaxGas(getCapacity());
        if (nbtTags.hasKey("cacheGas")) {
            buffer.setGas(GasStack.readFromNBT(nbtTags.getCompoundTag("cacheGas")));
        } else {
            buffer.setGas(null);
        }
        GasStack stored = buffer.getGas();
        lastWrite = stored == null ? null : stored.copy();
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        updateShare();
        super.writeCustomNBT(nbtTags);
        if (lastWrite != null && lastWrite.amount > 0) {
            nbtTags.setTag("cacheGas", lastWrite.write(new NBTTagCompound()));
        } else {
            nbtTags.removeTag("cacheGas");
        }
        nbtTags.setInteger("tier", tier.ordinal());
    }

    @Override
    public TransmissionType getTransmissionType() {
        return TransmissionType.GAS;
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.PRESSURIZED_TUBE;
    }

    @Override
    public boolean isValidAcceptor(TileEntity tile, EnumFacing side) {
        return tile != null && !CapabilityUtils.hasCapability(tile, Capabilities.GRID_TRANSMITTER_CAPABILITY, side.getOpposite()) &&
                CapabilityUtils.hasCapability(tile, Capabilities.GAS_HANDLER_CAPABILITY, side.getOpposite());
    }

    @Override
    public boolean isValidTransmitter(TileEntity tileEntity) {
        if (!super.isValidTransmitter(tileEntity)) {
            return false;
        }
        if (!(tileEntity instanceof TileEntityPressurizedTube tube)) {
            return true;
        }
        GasStack buffer = getBufferWithFallback();
        GasStack otherBuffer = tube.getBufferWithFallback();
        return buffer == null || otherBuffer == null || buffer.isGasEqual(otherBuffer);
    }

    @Override
    public GasNetwork createNewNetwork() {
        return new GasNetwork();
    }

    @Override
    public GasNetwork createNetworkByMerging(Collection<GasNetwork> networks) {
        return new GasNetwork(networks);
    }

    @Override
    protected boolean canHaveIncompatibleNetworks() {
        return true;
    }

    @Override
    public int getCapacity() {
        return tier.getTubeCapacity();
    }

    @Nullable
    @Override
    public GasStack getBuffer() {
        if (buffer == null) {
            return null;
        }
        GasStack gas = buffer.getGas();
        return gas == null || gas.amount == 0 ? null : gas;
    }

    @Override
    public void clearBuffer() {
        buffer.setGas(null);
        onContentsChanged();
    }

    @Override
    public void takeShare() {
        updateShare();
        if (getTransmitter().hasTransmitterNetwork()) {
            GasNetwork network = getTransmitter().getTransmitterNetwork();
            if (lastWrite == null) buffer.setGas(null);
            else {
                int removed = network.shrinkBuffer(lastWrite.amount);
                lastWrite = removed <= 0 ? null : new GasStack(lastWrite.getGas(), removed);
                buffer.setGas(lastWrite);
            }
        }
    }

    public int takeGas(GasStack gasStack, boolean doEmit) {
        GasStack remainder = gasTank.insert(gasStack, Action.get(doEmit), AutomationType.INTERNAL);
        return gasStack == null ? 0 : gasStack.amount - (remainder == null ? 0 : remainder.amount);
    }

    @Override
    public IGasHandler getCachedAcceptor(EnumFacing side) {
        return getConnectedAcceptor(getCachedTile(side), side);
    }

    @Nullable
    private IGasHandler getConnectedAcceptor(EnumFacing side, boolean allowPullConnection) {
        ConnectionType connectionType = getConnectionType(side);
        if (!allowPullConnection && (connectionType == ConnectionType.PULL || connectionType == ConnectionType.NONE)) {
            return null;
        }
        TileEntity tile = MekanismUtils.getTileEntity(world, getPos().offset(side));
        return getConnectedAcceptor(tile, side);
    }

    @Nullable
    private IGasHandler getConnectedAcceptor(@Nullable TileEntity tile, EnumFacing side) {
        return tile == null ? null : CapabilityUtils.getCapability(tile, Capabilities.GAS_HANDLER_CAPABILITY, side.getOpposite());
    }

    @Override
    public boolean upgrade(AlloyTier tierOrdinal) {
        if (tier.ordinal() < BaseTier.ULTIMATE.ordinal() && tierOrdinal.ordinal() == tier.ordinal()) {
            tier = TubeTier.values()[tier.ordinal() + 1];
            markDirtyTransmitters();
            sendDesc = true;
            return true;
        }
        return false;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) throws Exception {
        tier = MekanismUtils.getByIndex(TubeTier.values(), dataStream.readInt(), tier);
        super.handlePacketData(dataStream);
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        data.add(tier.ordinal());
        super.getNetworkedData(data);
        return data;
    }

    private boolean canInsertGasTank(@Nullable EnumFacing side) {
        if (side == null) {
            return true;
        }
        ConnectionType connectionType = getConnectionType(side);
        return connectionType == ConnectionType.NORMAL || connectionType == ConnectionType.PULL;
    }

    private boolean canExtractGasTank(@Nullable EnumFacing side) {
        if (side == null) {
            return true;
        }
        ConnectionType connectionType = getConnectionType(side);
        return connectionType == ConnectionType.NORMAL || connectionType == ConnectionType.PUSH;
    }

    @Nonnull
    private List<IExtendedGasTank> getTubeGasTanks(@Nullable EnumFacing side) {
        return isRedstoneActivated() || side != null && !canConnect(side) ? Collections.emptyList() : gasTanks;
    }

    @Nonnull
    @Override
    public List<IExtendedGasTank> getGasTanks(@Nullable EnumFacing side) {
        return gasHandler.getGasTanks(side);
    }

    @Override
    public boolean canInsertGas(@Nullable EnumFacing side) {
        return gasHandler.canInsertGas(side);
    }

    @Override
    public boolean canExtractGas(@Nullable EnumFacing side) {
        return gasHandler.canExtractGas(side);
    }

    @Nullable
    @Override
    public GasStack insertGas(int tank, @Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return gasHandler.insertGas(tank, stack, side, action);
    }

    @Nullable
    @Override
    public GasStack extractGas(int tank, int amount, @Nullable EnumFacing side, Action action) {
        return gasHandler.extractGas(tank, amount, side, action);
    }

    @Nullable
    @Override
    public GasStack insertGas(@Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return gasHandler.insertGas(stack, side, action);
    }

    @Nullable
    @Override
    public GasStack extractGas(int amount, @Nullable EnumFacing side, Action action) {
        return gasHandler.extractGas(amount, side, action);
    }

    @Nullable
    @Override
    public GasStack extractGas(@Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return gasHandler.extractGas(stack, side, action);
    }

    @Override
    public void onContentsChanged() {
        markChunkDirty();
    }

    @Override
    public int getRadiationParticleCount() {
        return MathUtils.clampToInt(3 * getRadiationScale());
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }

    private class PressurizedTubeGasTank implements IExtendedGasTank {

        @Nullable
        @Override
        public GasStack getGas() {
            GasStack stack = getActiveGas();
            return stack == null || stack.amount <= 0 ? null : stack;
        }

        @Override
        public int getGasAmount() {
            GasStack stack = getGas();
            return stack == null ? 0 : stack.amount;
        }

        @Override
        public int getMaxGas() {
            return getCapacity();
        }

        @Override
        public int getCapacity() {
            return getTransmitter().hasTransmitterNetwork() ? getTransmitter().getTransmitterNetwork().getCapacity() : TileEntityPressurizedTube.this.getCapacity();
        }

        @Override
        public int getNeeded() {
            return Math.max(0, getCapacity() - getGasAmount());
        }

        @Override
        public void setStack(@Nullable GasStack stack) {
            setStack(stack, true);
        }

        @Override
        public void setStackUnchecked(@Nullable GasStack stack) {
            setStack(stack, false);
        }

        private void setStack(@Nullable GasStack stack, boolean validateStack) {
            if (stack == null || stack.amount <= 0) {
                setActiveGas(null);
            } else if (!validateStack || isValid(stack)) {
                setActiveGas(stack.copy().withAmount(Math.min(stack.amount, getCapacity())));
            } else {
                throw new RuntimeException("Invalid gas for tank: " + stack.getGas().getName() + " " + stack.amount);
            }
        }

        @Override
        public boolean isValid(@Nullable GasStack stack) {
            return stack != null && stack.getGas() != null;
        }

        @Override
        public boolean canReceive(Gas gas) {
            GasStack stored = getGas();
            return getNeeded() > 0 && (stored == null || gas == null || gas == stored.getGas());
        }

        @Override
        public boolean canReceiveType(Gas gas) {
            GasStack stored = getGas();
            return stored == null || gas == null || gas == stored.getGas();
        }

        @Override
        public boolean canDraw(Gas gas) {
            GasStack stored = getGas();
            return stored != null && (gas == null || gas == stored.getGas());
        }

        @Nullable
        @Override
        public GasStack insert(@Nullable GasStack stack, Action action, AutomationType automationType) {
            if (stack == null || stack.amount <= 0 || !isValid(stack)) {
                return stack;
            }
            GasStack stored = getGas();
            if (stored != null && !stored.isGasEqual(stack)) {
                return stack;
            }
            int toAdd = Math.min(stack.amount, getNeeded());
            if (toAdd <= 0) {
                return stack;
            }
            if (action.execute()) {
                if (stored == null) {
                    setActiveGas(stack.copy().withAmount(toAdd));
                } else {
                    setActiveGas(stored.copy().withAmount(stored.amount + toAdd));
                }
            }
            return stack.amount == toAdd ? null : stack.copy().withAmount(stack.amount - toAdd);
        }

        @Nullable
        @Override
        public GasStack extract(int amount, Action action, AutomationType automationType) {
            GasStack stored = getGas();
            if (stored == null || amount <= 0) {
                return null;
            }
            int toRemove = Math.min(amount, stored.amount);
            if (toRemove <= 0) {
                return null;
            }
            GasStack ret = stored.copy().withAmount(toRemove);
            if (action.execute()) {
                setActiveGas(stored.amount <= toRemove ? null : stored.copy().withAmount(stored.amount - toRemove));
            }
            return ret;
        }

        @Nullable
        private GasStack getActiveGas() {
            return getTransmitter().hasTransmitterNetwork() ? getTransmitter().getTransmitterNetwork().getBuffer() : buffer.getGas();
        }

        private void setActiveGas(@Nullable GasStack stack) {
            GasStack stored = stack == null || stack.amount <= 0 ? null : stack.copy().withAmount(Math.min(stack.amount, getCapacity()));
            if (getTransmitter().hasTransmitterNetwork()) {
                getTransmitter().getTransmitterNetwork().setBuffer(stored);
            } else {
                buffer.setGas(stored);
            }
            onContentsChanged();
        }

        @Override
        public void onContentsChanged() {
            TileEntityPressurizedTube.this.onContentsChanged();
        }
    }
}
