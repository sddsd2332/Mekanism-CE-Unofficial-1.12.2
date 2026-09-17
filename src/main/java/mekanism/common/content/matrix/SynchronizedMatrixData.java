package mekanism.common.content.matrix;

import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.TileNetworkList;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.multiblock.SynchronizedData;
import mekanism.common.tile.multiblock.TileEntityInductionCasing;
import mekanism.common.tile.multiblock.TileEntityInductionCell;
import mekanism.common.tile.multiblock.TileEntityInductionProvider;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;

public class SynchronizedMatrixData extends SynchronizedData<SynchronizedMatrixData> implements IEnergyContainer {

    private final Map<Coord4D, TileEntityInductionProvider> providers = new LinkedHashMap<>();
    private final Map<Coord4D, TileEntityInductionCell> cells = new LinkedHashMap<>();
    private final TileEntityInductionCasing controller;
    private final EnergyInventorySlot chargeSlot;
    private final EnergyInventorySlot dischargeSlot;
    private BigDecimal queuedOutput = BigDecimal.ZERO;
    private BigDecimal queuedInput = BigDecimal.ZERO;
    private BigDecimal exactTotal = BigDecimal.ZERO;
    private BigDecimal exactCapacity = BigDecimal.ZERO;
    private double lastOutput;
    private double lastInput;
    private double tickInput;
    private double tickOutput;
    private long rateTick = Long.MIN_VALUE;
    private boolean flushing;

    private double cachedTotal;
    private double transferCap;
    private double storageCap;

    private int clientProviders;
    private int clientCells;

    public SynchronizedMatrixData(TileEntityInductionCasing tile) {
        controller = tile;
        energyContainers.add(this);
        inventorySlots.add(chargeSlot = EnergyInventorySlot.drain(this, this, 146, 20));
        chargeSlot.setTransferAllowed(this::isFormed);
        chargeSlot.setSlotOverlay(SlotOverlay.PLUS);
        inventorySlots.add(dischargeSlot = EnergyInventorySlot.fillOrConvert(this, tile::getWorld, this, 146, 51));
        dischargeSlot.setTransferAllowed(this::isFormed);
        dischargeSlot.setSlotOverlay(SlotOverlay.MINUS);
    }

    public void manageInventory() {
        if (!isFormed()) return;
        chargeSlot.drainContainer();
        dischargeSlot.fillContainerOrConvert();
    }

    public void addCell(Coord4D coord, TileEntityInductionCell cell) {
        if (cells.putIfAbsent(coord, cell) == null) {
            storageCap += cell.getMaxEnergy();
            double energy = cell.getEnergy();
            cachedTotal += energy;
            if (cell.hasValidExactEnergy()) exactTotal = exactTotal.add(cell.getExactEnergy());
            else if (Double.isFinite(energy)) exactTotal = exactTotal.add(new BigDecimal(energy));
            if (Double.isFinite(cell.getMaxEnergy())) exactCapacity = exactCapacity.add(new BigDecimal(cell.getMaxEnergy()));
        }
    }

    public void addProvider(Coord4D coord, TileEntityInductionProvider provider) {
        if (providers.putIfAbsent(coord, provider) == null) transferCap += provider.getTransferCapacity();
    }

    public double getEnergyPostQueue() {
        return energyPostQueue().doubleValue();
    }

    private BigDecimal energyPostQueue() { return exactTotal.add(queuedInput).subtract(queuedOutput); }

    private static double boundedDouble(BigDecimal value) {
        double rounded = value.doubleValue();
        return new BigDecimal(rounded).compareTo(value) > 0 ? Math.nextDown(rounded) : rounded;
    }

    public boolean hasValidEnergy() {
        if (!Double.isFinite(cachedTotal) || !Double.isFinite(storageCap) || !Double.isFinite(transferCap) ||
              cachedTotal < 0 || storageCap < 0 || transferCap < 0 || cachedTotal > storageCap) return false;
        for (TileEntityInductionCell cell : cells.values()) {
            if (!cell.hasValidExactEnergy()) return false;
            double energy = cell.getEnergy();
            double capacity = cell.getMaxEnergy();
            if (!Double.isFinite(energy) || !Double.isFinite(capacity) || energy < 0 || capacity < 0 || energy > capacity) return false;
        }
        for (TileEntityInductionProvider provider : providers.values()) {
            double capacity = provider.getTransferCapacity();
            if (!Double.isFinite(capacity) || capacity < 0) return false;
        }
        return true;
    }

    public void tick(World world) {
        flushEnergy();
        refreshRateTick();
    }

    /** Saves change to the original cell objects without granting another tick's transfer budget. */
    public void flushEnergy() {
        if (flushing || queuedInput.signum() == 0 && queuedOutput.signum() == 0) return;
        flushing = true;
        try {
            BigDecimal remaining = queuedInput.subtract(queuedOutput);
            for (TileEntityInductionCell cell : cells.values()) {
                if (remaining.signum() == 0) break;
                BigDecimal before = cell.getExactEnergy();
                BigDecimal change = remaining.signum() > 0 ? remaining.min(new BigDecimal(cell.getMaxEnergy()).subtract(before)) :
                      remaining.negate().min(before).negate();
                cell.setExactEnergy(before.add(change));
                BigDecimal applied = cell.getExactEnergy().subtract(before);
                remaining = remaining.subtract(applied);
                exactTotal = exactTotal.add(applied);
            }
            cachedTotal = exactTotal.doubleValue();
            queuedInput = remaining.max(BigDecimal.ZERO);
            queuedOutput = remaining.negate().max(BigDecimal.ZERO);
        } finally {
            flushing = false;
        }
    }

    public void cellEnergyChanged(BigDecimal before, BigDecimal after) {
        if (!flushing) {
            exactTotal = exactTotal.add(after.subtract(before));
            cachedTotal = exactTotal.doubleValue();
        }
    }

    public void cellEnergyChanged(double before, double after) {
        cellEnergyChanged(new BigDecimal(before), new BigDecimal(after));
    }

    @Override
    public void setFormed(boolean formed) {
        if (!formed) {
            flushEnergy();
            for (TileEntityInductionCell cell : cells.values()) cell.unbindMatrix(this);
            for (TileEntityInductionProvider provider : providers.values()) provider.unbindMatrix(this);
        } else if (controller.getWorld() != null && !controller.getWorld().isRemote) {
            // A candidate may have been scanned before the old structure settled its queue.
            for (TileEntityInductionCell cell : cells.values()) cell.bindMatrix(this);
            for (TileEntityInductionProvider provider : providers.values()) provider.bindMatrix(this);
            cachedTotal = 0;
            exactTotal = BigDecimal.ZERO;
            for (TileEntityInductionCell cell : cells.values()) exactTotal = exactTotal.add(cell.getExactEnergy());
            cachedTotal = exactTotal.doubleValue();
        }
        super.setFormed(formed);
    }

    /** Called before a cell/provider is invalidated or its chunk is unloaded. */
    public void internalRemoved() {
        flushEnergy();
        World world = controller.getWorld();
        if (world != null && !world.isRemote) {
            for (Coord4D coord : locations) {
                TileEntity tile = coord.getTileEntity(world);
                if (tile instanceof TileEntityInductionCasing casing && casing.structure == this) {
                    casing.detachMatrixForInternalChange(this);
                    return;
                }
            }
            controller.getManager().detached(world, this);
        }
        setFormed(false);
    }

    private long refreshRateTick() {
        World world = controller.getWorld();
        long now = world == null ? 0 : world.getTotalWorldTime();
        if (rateTick != now) {
            lastInput = tickInput;
            lastOutput = tickOutput;
            tickInput = 0;
            tickOutput = 0;
            rateTick = now;
        }
        return now;
    }

    private double reserveTransfer(double amount, boolean input, boolean simulate) {
        long now = refreshRateTick();
        BigDecimal remaining = new BigDecimal(amount);
        for (TileEntityInductionProvider provider : providers.values()) {
            double requested = boundedDouble(remaining);
            double accepted = simulate ? Math.min(requested, provider.getRemainingTransfer(now, input)) : provider.useTransfer(now, requested, input);
            remaining = remaining.subtract(new BigDecimal(accepted));
            if (remaining.signum() == 0) break;
        }
        return boundedDouble(new BigDecimal(amount).subtract(remaining));
    }

    public double queueEnergyAddition(double energy, boolean simulate) {
        if (!isFormed() || !Double.isFinite(energy)) return 0;
        if (energy < 0) {
            //Ensure that the correct queue type gets called
            return queueEnergyRemoval(-energy, simulate);
        }
        double remainingInput = getRemainingInput();
        if (energy > remainingInput) {
            energy = remainingInput;
        }
        //Check to see if we are trying to add more energy than we have room for,
        // as we want to be as accurate as possible with the values we return
        // It is possible that the energy we have space for is a lot less than the amount we
        // can input at once such as if the matrix is almost full.
        double availableEnergy = boundedDouble(exactCapacity.subtract(energyPostQueue()).max(BigDecimal.ZERO));
        if (energy > availableEnergy) {
            //Only allow addition of
            energy = availableEnergy;
        }
        energy = reserveTransfer(Math.max(0, energy), true, simulate);
        if (!simulate) {
            //Increase how much we are inputting
            queuedInput = queuedInput.add(new BigDecimal(energy));
            tickInput += energy;
        }
        return energy;
    }

    public double queueEnergyRemoval(double energy, boolean simulate) {
        if (!isFormed() || !Double.isFinite(energy)) return 0;
        if (energy < 0) {
            //Ensure that the correct queue type gets called
            return queueEnergyAddition(-energy, simulate);
        }
        double remainingOutput = getRemainingOutput();
        if (energy > remainingOutput) {
            //If it is more than we can output lower it further
            energy = remainingOutput;
        }
        //Check to see if we are trying to remove more energy than we have to remove,
        // as we want to be as accurate as possible with the values we return
        // It is possible that the energy we have stored is a lot less than the amount we
        // can output at once such as if the matrix is almost empty.
        double availableEnergy = boundedDouble(energyPostQueue().max(BigDecimal.ZERO));
        if (energy > availableEnergy) {
            //If it is more than we have lower it further
            energy = availableEnergy;
        }
        energy = reserveTransfer(Math.max(0, energy), false, simulate);
        if (!simulate) {
            //Increase how much we are outputting by the amount we accepted
            queuedOutput = queuedOutput.add(new BigDecimal(energy));
            tickOutput += energy;
        }
        return energy;
    }

    @Override
    public double insert(double amount, Action action, AutomationType automationType) {
        if (amount <= 0 || !Double.isFinite(amount)) return amount;
        BigDecimal requested = new BigDecimal(amount);
        double remainder = insertionRemainder(requested, queueEnergyAddition(amount, true));
        double representable = requested.subtract(new BigDecimal(remainder)).doubleValue();
        if (!action.execute() || representable == 0) return remainder;
        double accepted = queueEnergyAddition(representable, false);
        remainder = insertionRemainder(requested, accepted);
        // The returned remainder and the exact cell credit must describe the same transfer.
        BigDecimal correction = new BigDecimal(accepted).subtract(requested.subtract(new BigDecimal(remainder)));
        queuedInput = queuedInput.subtract(correction);
        tickInput -= correction.doubleValue();
        return remainder;
    }

    private static double insertionRemainder(BigDecimal requested, double accepted) {
        BigDecimal exact = requested.subtract(new BigDecimal(accepted));
        double remainder = exact.doubleValue();
        return new BigDecimal(remainder).compareTo(exact) < 0 ? Math.nextUp(remainder) : remainder;
    }

    /** Returns unaccepted external units, crediting only their configured double conversion. */
    public double insertConverted(double amount, double joulesPerUnit, boolean simulate) {
        if (!(amount > 0) || !Double.isFinite(amount) || !(joulesPerUnit > 0) || !Double.isFinite(joulesPerUnit)) return amount;
        BigDecimal requested = new BigDecimal(amount);
        double available = queueEnergyAddition(Double.MAX_VALUE, true);
        double remainder = convertedRemainder(requested, available, joulesPerUnit);
        double joules = requested.subtract(new BigDecimal(remainder)).doubleValue() * joulesPerUnit;
        if (simulate || joules == 0) return remainder;
        double accepted = queueEnergyAddition(joules, false);
        remainder = convertedRemainder(requested, accepted, joulesPerUnit);
        double credited = requested.subtract(new BigDecimal(remainder)).doubleValue() * joulesPerUnit;
        BigDecimal correction = new BigDecimal(accepted).subtract(new BigDecimal(credited));
        queuedInput = queuedInput.subtract(correction);
        tickInput -= correction.doubleValue();
        return remainder;
    }

    private static double convertedRemainder(BigDecimal requested, double available, double factor) {
        double units = Math.min(requested.doubleValue(), available / factor);
        while (units > 0 && units * factor > available) units = Math.nextDown(units);
        double remainder = insertionRemainder(requested, units);
        double credited = requested.subtract(new BigDecimal(remainder)).doubleValue() * factor;
        return credited > 0 ? remainder : requested.doubleValue();
    }

    public long transferWholeUnits(long maximum, double factor, boolean input, boolean simulate) {
        if (maximum <= 0 || !(factor > 0) || !Double.isFinite(factor)) return 0;
        double available = input ? queueEnergyAddition(Double.MAX_VALUE, true) : queueEnergyRemoval(Double.MAX_VALUE, true);
        long units = wholeUnitsWithin(maximum, factor, available);
        if (simulate || units == 0) return units;
        double requested = units * factor;
        double accepted = input ? queueEnergyAddition(requested, false) : queueEnergyRemoval(requested, false);
        units = wholeUnitsWithin(units, factor, accepted);
        BigDecimal correction = new BigDecimal(accepted).subtract(new BigDecimal(units * factor));
        if (input) {
            queuedInput = queuedInput.subtract(correction);
            tickInput -= correction.doubleValue();
        } else {
            queuedOutput = queuedOutput.subtract(correction);
            tickOutput -= correction.doubleValue();
        }
        return units;
    }

    private static long wholeUnitsWithin(long maximum, double factor, double available) {
        long low = 0, high = maximum;
        while (low < high) {
            long distance = high - low;
            long middle = low + (distance >>> 1) + (distance & 1);
            if (middle * factor <= available) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    @Override
    public double extract(double amount, Action action, AutomationType automationType) {
        return amount <= 0 ? 0 : queueEnergyRemoval(amount, !action.execute());
    }

    public void queueSetEnergy(double energy) {
        if (energy > storageCap) {
            energy = storageCap;
        }
        //TODO: Potentially should allow setting it directly to something bypassing rate limit
        // API wise that makes sense, however this is only *really* used by IC2's setStored
        double difference = energy - getEnergyPostQueue();
        if (difference != 0) {
            //We call addition as values greater than zero are for addition
            // The queue methods also ensure that it is using the correct method
            // as IC2 changes energy by passing negative values for removal.
            // This also adds extra safety in case a mod ends up using the wrong
            // method for adding/removing energy. So when it is negative
            // queueEnergyAddition will pass it to queueEnergyRemoval
            queueEnergyAddition(difference, false);
        }
    }

    public TileNetworkList addStructureData(TileNetworkList data) {
        data.add(getEnergyPostQueue());
        data.add(storageCap);
        data.add(transferCap);
        data.add(lastInput);
        data.add(lastOutput);

        data.add(volWidth);
        data.add(volHeight);
        data.add(volLength);

        data.add(cells.size());
        data.add(providers.size());
        return data;
    }

    public void readStructureData(ByteBuf dataStream) {
        cachedTotal = dataStream.readDouble();
        if (Double.isFinite(cachedTotal)) exactTotal = new BigDecimal(cachedTotal);
        storageCap = dataStream.readDouble();
        transferCap = dataStream.readDouble();
        lastInput = dataStream.readDouble();
        lastOutput = dataStream.readDouble();

        volWidth = dataStream.readInt();
        volHeight = dataStream.readInt();
        volLength = dataStream.readInt();

        clientCells = dataStream.readInt();
        clientProviders = dataStream.readInt();
    }

    public double getStorageCap() {
        return storageCap;
    }

    public double getTransferCap() {
        return transferCap;
    }

    @Override
    public double getEnergy() {
        return getEnergyPostQueue();
    }

    @Override
    public void setEnergy(double energy) {
        queueSetEnergy(energy);
    }

    @Override
    public double getMaxEnergy() {
        return storageCap;
    }

    public double getLastInput() {
        return lastInput;
    }

    public double getLastOutput() {
        return lastOutput;
    }

    public double getRemainingInput() {
        long now = refreshRateTick();
        double remaining = 0;
        for (TileEntityInductionProvider provider : providers.values()) remaining += provider.getRemainingTransfer(now, true);
        return remaining;
    }

    public double getRemainingOutput() {
        long now = refreshRateTick();
        double remaining = 0;
        for (TileEntityInductionProvider provider : providers.values()) remaining += provider.getRemainingTransfer(now, false);
        return remaining;
    }

    public int getCellCount() {
        return cells.isEmpty() ? clientCells : cells.size();
    }

    public int getProviderCount() {
        return providers.isEmpty() ? clientProviders : providers.size();
    }
}
