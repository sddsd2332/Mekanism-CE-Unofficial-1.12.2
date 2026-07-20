package mekanism.common.tile.qio;

import mekanism.api.Action;
import mekanism.api.Coord4D;
import mekanism.api.NBTConstants;
import mekanism.api.gas.GasStack;
import mekanism.common.base.ILogisticalTransporter;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.common.content.qio.QIORollback;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.lib.SidedBlockPos;
import mekanism.common.lib.inventory.IAdvancedTransportEjector;
import mekanism.common.lib.inventory.TransitRequest;
import mekanism.common.lib.inventory.TransitRequest.TransitResponse;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

/** Pushes resources from QIO into the adjacent item/fluid/gas handler. */
public class TileEntityQIOExporter extends TileEntityQIOFilterHandler implements IAdvancedTransportEjector {

    private static final int MAX_DELAY = 10;
    private int delay;
    private boolean roundRobin;
    @Nullable
    private SidedBlockPos roundRobinTarget;

    public TileEntityQIOExporter() {
        super("QIOExporter");
    }

    public boolean getExportWithoutFilter() {
        return isFilterless();
    }

    public void setExportWithoutFilter(boolean value) {
        setFilterless(value);
    }

    public void toggleExportWithoutFilter() {
        toggleFilterless();
    }

    @Override
    public boolean getRoundRobin() {
        return roundRobin;
    }

    public void setRoundRobin(boolean roundRobin) {
        if (this.roundRobin != roundRobin) {
            this.roundRobin = roundRobin;
            setRoundRobinTarget((SidedBlockPos) null);
            markDirty();
        }
    }

    @Override
    public void toggleRoundRobin() {
        setRoundRobin(!roundRobin);
    }

    @Override
    @Nullable
    public SidedBlockPos getRoundRobinTarget() {
        return roundRobinTarget;
    }

    @Override
    public void setRoundRobinTarget(@Nullable SidedBlockPos target) {
        roundRobinTarget = target;
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        if (!MekanismUtils.canFunction(this)) {
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }
        QIOFrequency frequency = getFrequencyForTransfer();
        TileEntity adjacent = getAdjacentTile();
        if (frequency != null && adjacent != null) {
            ILogisticalTransporter transporter = CapabilityUtils.getCapability(adjacent,
                  Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY, getHandlerSide());
            if (transporter == null) {
                exportItems(frequency, adjacent);
            } else {
                exportItems(frequency, transporter);
            }
            exportFluids(frequency, adjacent);
            exportGases(frequency, adjacent);
        }
        delay = MAX_DELAY;
    }

    private boolean exportItems(QIOFrequency frequency, TileEntity adjacent) {
        IItemHandler handler = CapabilityUtils.getCapability(adjacent, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, getHandlerSide());
        return handler != null && exportItems(frequency, handler);
    }

    boolean exportItems(QIOFrequency frequency, IItemHandler handler) {
        if (frequency == null || handler == null) {
            return false;
        }
        int count = 0;
        int types = 0;
        boolean moved = false;
        for (QIOResourceEntry entry : frequency.getResourceEntries()) {
            if (entry.getKind() != QIOResourceKind.ITEM || !acceptsItem(entry.getItem()) || count >= getMaxTransitCount() || types >= getMaxTransitTypes()) {
                continue;
            }
            int requested = (int) Math.min(entry.getAmount(), getMaxTransitCount() - count);
            ItemStack source = entry.createItemStack(requested);
            ItemStack remainder = insert(handler, source, true);
            int accepted = source.getCount() - remainder.getCount();
            if (accepted <= 0) {
                continue;
            }
            long extracted = frequency.massExtract(entry.getUUID(), accepted, Action.EXECUTE);
            if (extracted <= 0) {
                continue;
            }
            ItemStack toInsert = entry.createItemStack((int) extracted);
            ItemStack failed = insert(handler, toInsert, false);
            int inserted = toInsert.getCount() - failed.getCount();
            if (inserted < extracted) {
                QIORollback.restore(frequency, entry.getUUID(), extracted - inserted, "exporter item transfer");
            }
            if (inserted > 0) {
                count += inserted;
                types++;
                moved = true;
            }
        }
        return moved;
    }

    boolean exportItems(QIOFrequency frequency, ILogisticalTransporter transporter) {
        if (frequency == null || transporter == null) {
            return false;
        }
        int count = 0;
        int types = 0;
        boolean moved = false;
        for (QIOResourceEntry entry : frequency.getResourceEntries()) {
            if (entry.getKind() != QIOResourceKind.ITEM || !acceptsItem(entry.getItem()) || count >= getMaxTransitCount()
                  || types >= getMaxTransitTypes()) {
                continue;
            }
            int requested = (int) Math.min(entry.getAmount(), getMaxTransitCount() - count);
            ItemStack source = entry.createItemStack(requested);
            SidedBlockPos previousTarget = getRoundRobinTarget();
            TransitResponse simulated = transporter.insertMaybeRR(this, getOutputterCoord(), TransitRequest.simple(source), null, false, 1);
            setRoundRobinTarget(previousTarget);
            int accepted = Math.min(requested, simulated.getSendingAmount());
            if (accepted <= 0) {
                continue;
            }
            long extracted = frequency.massExtract(entry.getUUID(), accepted, Action.EXECUTE);
            if (extracted <= 0) {
                continue;
            }
            ItemStack toSend = entry.createItemStack((int) extracted);
            TransitResponse sent = transporter.insertMaybeRR(this, getOutputterCoord(), TransitRequest.simple(toSend), null, true, 1);
            int sentAmount = Math.min((int) extracted, sent.getSendingAmount());
            if (sentAmount <= 0) {
                setRoundRobinTarget(previousTarget);
            } else if (!java.util.Objects.equals(previousTarget, getRoundRobinTarget())) {
                markDirty();
            }
            if (sentAmount < extracted) {
                QIORollback.restore(frequency, entry.getUUID(), extracted - sentAmount, "exporter transporter transfer");
            }
            if (sentAmount > 0) {
                count += sentAmount;
                types++;
                moved = true;
            }
        }
        return moved;
    }

    private Coord4D getOutputterCoord() {
        return world == null ? new Coord4D(getPos(), 0) : Coord4D.get(this);
    }

    private boolean exportFluids(QIOFrequency frequency, TileEntity adjacent) {
        IFluidHandler handler = CapabilityUtils.getCapability(adjacent, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, getHandlerSide());
        return handler != null && exportFluids(frequency, handler);
    }

    boolean exportFluids(QIOFrequency frequency, IFluidHandler handler) {
        if (frequency == null || handler == null) {
            return false;
        }
        int remaining = getMaxTransitCount() * 1000;
        int types = 0;
        boolean moved = false;
        for (QIOResourceEntry entry : frequency.getResourceEntries()) {
            if (entry.getKind() != QIOResourceKind.FLUID || remaining <= 0 || types >= getMaxTransitTypes() || !acceptsFluid(entry.getFluid())) {
                continue;
            }
            FluidStack fluid = entry.getFluid();
            if (fluid == null) {
                continue;
            }
            fluid.amount = (int) Math.min(Math.min(entry.getAmount(), Integer.MAX_VALUE), remaining);
            int accepted = handler.fill(fluid.copy(), false);
            if (accepted <= 0) {
                continue;
            }
            long extracted = frequency.massExtract(entry.getUUID(), accepted, Action.EXECUTE);
            if (extracted <= 0) {
                continue;
            }
            FluidStack toFill = new FluidStack(fluid, (int) extracted);
            int filled = handler.fill(toFill, true);
            if (filled < extracted) {
                QIORollback.restore(frequency, entry.getUUID(), extracted - filled, "exporter fluid transfer");
            }
            if (filled > 0) {
                remaining -= filled;
                types++;
                moved = true;
            }
        }
        return moved;
    }

    private boolean exportGases(QIOFrequency frequency, TileEntity adjacent) {
        mekanism.api.gas.IGasHandler handler = CapabilityUtils.getCapability(adjacent, Capabilities.GAS_HANDLER_CAPABILITY, getHandlerSide());
        return handler != null && exportGases(frequency, handler);
    }

    boolean exportGases(QIOFrequency frequency, mekanism.api.gas.IGasHandler handler) {
        if (frequency == null || handler == null) {
            return false;
        }
        int remaining = getMaxTransitCount() * 1000;
        int types = 0;
        boolean moved = false;
        for (QIOResourceEntry entry : frequency.getResourceEntries()) {
            if (entry.getKind() != QIOResourceKind.GAS || remaining <= 0 || types >= getMaxTransitTypes() || !acceptsGas(entry.getGas())) {
                continue;
            }
            GasStack gas = entry.getGas();
            if (gas == null || gas.getGas() == null) {
                continue;
            }
            int amount = (int) Math.min(Math.min(entry.getAmount(), Integer.MAX_VALUE), remaining);
            GasStack toSend = new GasStack(gas.getGas(), amount);
            int accepted = handler.receiveGas(getHandlerSide(), toSend.copy(), false);
            if (accepted <= 0) {
                continue;
            }
            long extracted = frequency.massExtract(entry.getUUID(), accepted, Action.EXECUTE);
            if (extracted <= 0) {
                continue;
            }
            GasStack actual = new GasStack(gas.getGas(), (int) extracted);
            int sent = handler.receiveGas(getHandlerSide(), actual, true);
            if (sent < extracted) {
                QIORollback.restore(frequency, entry.getUUID(), extracted - sent, "exporter gas transfer");
            }
            if (sent > 0) {
                remaining -= sent;
                types++;
                moved = true;
            }
        }
        return moved;
    }

    private ItemStack insert(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            if (handler.isItemValid(slot, remainder)) {
                remainder = handler.insertItem(slot, remainder, simulate);
            }
        }
        return remainder;
    }

    @Override
    public boolean canSendHome(ItemStack stack) {
        QIOFrequency frequency = getFrequencyForTransfer();
        return frequency != null && stack != null && !stack.isEmpty()
              && frequency.massInsert(stack, stack.getCount(), Action.SIMULATE) > 0;
    }

    @Override
    public TransitResponse sendHome(TransitRequest request) {
        if (request == null || request.isEmpty()) {
            return TransitResponse.EMPTY;
        }
        QIOFrequency frequency = getFrequencyForTransfer();
        if (frequency == null) {
            return request.getEmptyResponse();
        }
        for (TransitRequest.ItemData data : request) {
            ItemStack stack = data.getStack();
            long inserted = frequency.massInsert(stack, stack.getCount(), Action.EXECUTE);
            if (inserted > 0) {
                return request.createResponse(data.getItemType().createStack((int) inserted), data);
            }
        }
        return request.getEmptyResponse();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean(NBTConstants.ROUND_ROBIN, roundRobin);
        if (roundRobinTarget != null) {
            nbtTags.setTag(NBTConstants.ROUND_ROBIN_TARGET, roundRobinTarget.serialize());
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        roundRobin = nbtTags.getBoolean(NBTConstants.ROUND_ROBIN);
        roundRobinTarget = nbtTags.hasKey(NBTConstants.ROUND_ROBIN_TARGET)
              ? SidedBlockPos.deserialize(nbtTags.getCompoundTag(NBTConstants.ROUND_ROBIN_TARGET)) : null;
    }

    @Override
    public void writeSustainedQIOData(NBTTagCompound nbtTags) {
        super.writeSustainedQIOData(nbtTags);
        nbtTags.setBoolean(NBTConstants.ROUND_ROBIN, roundRobin);
    }

    @Override
    public void readSustainedQIOData(NBTTagCompound nbtTags) {
        super.readSustainedQIOData(nbtTags);
        roundRobin = nbtTags.getBoolean(NBTConstants.ROUND_ROBIN);
        roundRobinTarget = null;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(this::getRoundRobin, value -> roundRobin = value));
    }
}
