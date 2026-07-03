package mekanism.api.gas;

import net.minecraft.nbt.NBTTagCompound;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An optional way of managing and/or storing gasses. Would be very useful in TileEntity and Entity gas storage.
 *
 * @author aidancbrady
 */
public class GasTank implements GasTankInfo, IGasTank {

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public volatile GasStack stored;

    private volatile int maxGas;

    private GasTank() {
    }

    /**
     * Creates a tank with a defined capacity.
     *
     * @param max - the maximum amount of gas this GasTank can hold
     */
    public GasTank(int max) {
        maxGas = max;
    }

    /**
     * Returns the tank stored in the defined tag compound, or null if it doesn't exist.
     *
     * @param nbtTags - tag compound to read from
     * @return tank stored in the tag compound
     */
    public static GasTank readFromNBT(NBTTagCompound nbtTags) {
        if (nbtTags == null || nbtTags.isEmpty()) {
            return null;
        }

        GasTank tank = new GasTank();
        tank.read(nbtTags);
        return tank;
    }

    /**
     * Draws a specified amount of gas out of this tank.
     *
     * @param amount - amount to draw
     * @param doDraw - if the gas should actually be removed from this tank
     * @return gas taken from this GasTank as a GasStack value
     */
    public synchronized GasStack draw(int amount, boolean doDraw) {
        if (stored == null || amount <= 0) {
            return null;
        }

        try {
            rwLock.readLock().lock();

            GasStack ret = new GasStack(stored.getGas(), Math.min(getStored(), amount));
            if (ret.amount > 0) {
                if (doDraw) {
                    stored.amount -= ret.amount;
                    if (stored.amount <= 0) {
                        stored = null;
                    }
                }
                return ret;
            }
            return null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Adds a specified amount of gas to this tank.
     *
     * @param amount    - the GasStack for this tank to receive
     * @param doReceive - if the gas should actually be added to this tank
     * @return the amount of gas accepted by this tank
     */
    public synchronized int receive(GasStack amount, boolean doReceive) {
        if (amount == null || (stored != null && !stored.isGasEqual(amount))) {
            return 0;
        }

        try {
            rwLock.readLock().lock();

            int toFill = Math.min(maxGas - getStored(), amount.amount);

            if (doReceive) {
                if (stored == null) {
                    stored = amount.copy().withAmount(getStored() + toFill);
                } else {
                    stored.amount = Math.min(maxGas, getStored() + amount.amount);
                }
            }

            return toFill;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * If this GasTank can receive the specified type of gas. Will return false if this tank does not need anymore gas.
     *
     * @param gas - gas to check
     * @return if this GasTank can accept the defined gas
     */
    public boolean canReceive(Gas gas) {
        try {
            rwLock.readLock().lock();
            return getNeeded() != 0 && (stored == null || gas == null || gas == stored.getGas());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * If this GasTank can receive the specified type of gas. Will return TRUE if this tank does not need anymore gas.
     *
     * @param gas - gas to check
     * @return if this GasTank can accept the defined gas
     */
    public boolean canReceiveType(Gas gas) {
        try {
            rwLock.readLock().lock();
            return stored == null || gas == null || gas == stored.getGas();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * If this GasTank can be drawn of the specified type of gas. Will return false if this tank does not contain any gas.
     *
     * @param gas - gas to check
     * @return if this GasTank can be drawn of the defined gas
     */
    public boolean canDraw(Gas gas) {
        try {
            rwLock.readLock().lock();
            return stored != null && (gas == null || gas == stored.getGas());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public GasTankInfo getInfo() {
        return this;
    }

    /**
     * Gets the amount of gas needed by this GasTank.
     *
     * @return Amount of gas needed
     */
    public int getNeeded() {
        return maxGas - getStored();
    }

    @Override
    public int input(GasStack resource, boolean input) {
        return receive(resource, input);
    }

    @Override
    public GasStack output(int Maxoutput, boolean output) {
        return draw(Maxoutput, output);
    }

    /**
     * Gets the maximum amount of gas this tank can hold.
     *
     * @return - max gas
     */
    @Override
    public int getMaxGas() {
        return maxGas;
    }

    /**
     * Sets the maximum amount of gas this tank can hold
     */
    public void setMaxGas(int capacity) {
        maxGas = capacity;
    }

    /**
     * Gets the GasStack held by this GasTank.
     *
     * @return - GasStakc held by this tank
     */
    @Override
    public GasStack getGas() {
        try {
            rwLock.readLock().lock();
            return stored;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Sets this tank's GasStack value to a new value. Will cap the amount to this GasTank's capacity.
     *
     * @param stack - value to set this tank's GasStack value to
     */
    public void setGas(GasStack stack) {
        try {
            rwLock.readLock().lock();

            stored = stack;
            if (stored != null) {
                stored.amount = Math.min(maxGas, stored.amount);
                if (stored.amount <= 0) {
                    stored = null;
                }
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Gets the type of gas currently stored in this GasTank.
     *
     * @return gas type contained
     */
    public Gas getGasType() {
        try {
            rwLock.readLock().lock();
            return stored != null ? stored.getGas() : null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Gets the amount of gas stored by this GasTank.
     *
     * @return amount of gas stored
     */
    @Override
    public int getStored() {
        try {
            rwLock.readLock().lock();
            return stored != null ? stored.amount : 0;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public int getGasAmount() {
        return getStored();
    }

    /**
     * Writes this tank to a defined tag compound.
     *
     * @param nbtTags - tag compound to write to
     * @return tag compound with this tank's data
     */
    public NBTTagCompound write(NBTTagCompound nbtTags) {
        if (stored != null && stored.getGas() != null && stored.amount > 0) {
            nbtTags.setTag("stored", stored.write(new NBTTagCompound()));
        }
        nbtTags.setInteger("maxGas", maxGas);
        return nbtTags;
    }

    /**
     * Reads this tank's data from a defined tag compound.
     *
     * @param nbtTags - tag compound to read from
     */
    public void read(NBTTagCompound nbtTags) {
        if (nbtTags.hasKey("stored")) {
            stored = GasStack.readFromNBT(nbtTags.getCompoundTag("stored"));
            if (stored != null && stored.amount <= 0) {
                //fix any old data that may be broken
                stored = null;
            }
        }
        //todo: this is weird, remove in v10?
        if (nbtTags.hasKey("maxGas") && nbtTags.getInteger("maxGas") != 0) {
            maxGas = nbtTags.getInteger("maxGas");
        }
    }
}
