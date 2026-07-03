package mekanism.common;

import io.netty.buffer.ByteBuf;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.api.infuse.InfuseObject;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.infuse.InfuseType;
import mekanism.common.base.ISustainedData;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;

public class InfuseStorage implements ISustainedData {

    private static final String LEGACY_AMOUNT_KEY = "infuseStored";
    private static final String LEGACY_ITEM_AMOUNT_KEY = "infuseAmount";

    private InfuseType type;

    private int amount;

    public InfuseStorage() {
    }

    public InfuseStorage(InfuseType infuseType, int infuseAmount) {
        type = infuseType;
        amount = infuseAmount;
    }

    /**
     * Replace this instance's properties with that from another
     *
     * @param other the instance to copy from
     */
    public void copyFrom(@Nonnull InfuseStorage other) {
        this.type = other.getType();
        this.amount = other.getAmount();
    }

    public boolean contains(InfuseStorage storage) {
        return type == storage.type && amount >= storage.amount;
    }

    public void subtract(InfuseStorage storage) {
        if (contains(storage)) {
            amount -= storage.amount;
        } else if (type == storage.type) {
            amount = 0;
        }
        if (amount <= 0) {
            type = null;
            amount = 0;
        }
    }

    public void increase(InfuseStorage input) {
        if (type == null) {
            type = input.type;
            amount = input.amount;
        } else if (type == input.type) {
            amount += input.amount;
        } else {
            Mekanism.logger.error("Tried to increase infusion storage with an incompatible type", new Exception());
        }
    }

    public void increase(InfuseObject input) {
        increase(input, 1);
    }

    public void increase(InfuseObject input, int operations) {
        if (input == null || input.stored <= 0 || operations <= 0) {
            return;
        }
        long toAdd = (long) input.stored * operations;
        if (type == null) {
            type = input.type;
            amount = (int) Math.min(Integer.MAX_VALUE, toAdd);
        } else if (type == input.type) {
            amount = (int) Math.min(Integer.MAX_VALUE, (long) amount + toAdd);
        } else {
            Mekanism.logger.error("Tried to increase infusion storage with an incompatible type", new Exception());
        }
    }

    public boolean canReceive(InfuseObject input) {
        return input != null && canReceive(input.type);
    }

    public boolean canReceive(InfuseType infuseType) {
        return getType() == null || getType() == infuseType;
    }

    public boolean canAdd(InfuseObject input, int capacity) {
        return canReceive(input) && (long) getAmount() + input.stored <= capacity;
    }

    public int getSupportedConversionOperations(InfuseObject input, int capacity, int availableItems, boolean allowBulk) {
        if (input == null || input.stored <= 0 || capacity <= 0 || availableItems <= 0 || !canReceive(input)) {
            return 0;
        }
        int maxOperations = allowBulk ? availableItems : 1;
        long needed = (long) capacity - getAmount();
        if (needed < input.stored) {
            return 0;
        }
        return (int) Math.min(maxOperations, needed / input.stored);
    }

    public boolean add(InfuseObject input, int capacity) {
        if (canAdd(input, capacity)) {
            increase(input);
            return true;
        }
        return false;
    }

    public void setToCapacity(InfuseObject input, int capacity) {
        if (input == null || capacity <= 0) {
            setEmpty();
        } else {
            type = input.type;
            amount = capacity;
        }
    }

    public void read(NBTTagCompound nbtTags) {
        int storedAmount = nbtTags.getInteger(LEGACY_AMOUNT_KEY);
        if (storedAmount > 0) {
            InfuseType storedType = readInfuseType(nbtTags.getString(NBTConstants.TYPE));
            if (storedType != null) {
                type = storedType;
                amount = storedAmount;
                return;
            }
        }
        setEmpty();
    }

    public void write(NBTTagCompound nbtTags) {
        if (getType() != null) {
            nbtTags.setString(NBTConstants.TYPE, getType().name);
            nbtTags.setInteger(LEGACY_AMOUNT_KEY, getAmount());
        } else {
            nbtTags.setString(NBTConstants.TYPE, "null");
        }
    }

    public void readFromPacket(ByteBuf dataStream) {
        int storedAmount = dataStream.readInt();
        if (storedAmount > 0) {
            InfuseType storedType = readInfuseType(PacketHandler.readString(dataStream));
            if (storedType != null) {
                type = storedType;
                amount = storedAmount;
                return;
            }
        }
        setEmpty();
    }

    public void addToNetworkList(TileNetworkList data) {
        data.add(getAmount());
        if (getAmount() > 0) {
            data.add(getType().name);
        }
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        if (type != null && amount > 0) {
            ItemDataUtils.setString(itemStack, NBTConstants.INFUSE_TYPE_STORED, type.name);
            ItemDataUtils.setInt(itemStack, LEGACY_ITEM_AMOUNT_KEY, amount);
            NBTTagCompound stored = new NBTTagCompound();
            stored.setString(NBTConstants.INFUSE_TYPE_NAME, "mekanism:" + type.name.toLowerCase());
            stored.setLong(NBTConstants.AMOUNT, amount);
            NBTTagCompound tank = new NBTTagCompound();
            tank.setByte(NBTConstants.TANK, (byte) 0);
            tank.setTag(NBTConstants.STORED, stored);
            NBTTagList tanks = new NBTTagList();
            tanks.appendTag(tank);
            ItemDataUtils.setList(itemStack, NBTConstants.INFUSION_TANKS, tanks);
        } else {
            ItemDataUtils.removeData(itemStack, NBTConstants.INFUSE_TYPE_STORED);
            ItemDataUtils.removeData(itemStack, LEGACY_ITEM_AMOUNT_KEY);
            ItemDataUtils.removeData(itemStack, NBTConstants.INFUSION_TANKS);
        }
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (ItemDataUtils.hasData(itemStack, NBTConstants.INFUSE_TYPE_STORED) && ItemDataUtils.hasData(itemStack, LEGACY_ITEM_AMOUNT_KEY)) {
            type = readInfuseType(ItemDataUtils.getString(itemStack, NBTConstants.INFUSE_TYPE_STORED));
            if (type != null) {
                amount = ItemDataUtils.getInt(itemStack, LEGACY_ITEM_AMOUNT_KEY);
                if (amount <= 0) {
                    setEmpty();
                }
            } else {
                setEmpty();
            }
        } else if (ItemDataUtils.hasData(itemStack, NBTConstants.INFUSION_TANKS, NBT.TAG_LIST)) {
            readInfusionTanks(itemStack);
        } else {
            setEmpty();
        }
    }

    private void readInfusionTanks(ItemStack itemStack) {
        NBTTagList tanks = ItemDataUtils.getList(itemStack, NBTConstants.INFUSION_TANKS);
        for (int tank = 0; tank < tanks.tagCount(); tank++) {
            NBTTagCompound tankData = tanks.getCompoundTagAt(tank);
            NBTTagCompound stored = tankData.hasKey(NBTConstants.STORED, NBT.TAG_COMPOUND) ? tankData.getCompoundTag(NBTConstants.STORED) : tankData;
            InfuseType storedType = readInfuseType(stored.getString(NBTConstants.INFUSE_TYPE_NAME));
            int storedAmount = (int) Math.min(stored.getLong(NBTConstants.AMOUNT), Integer.MAX_VALUE);
            if (storedType != null && storedAmount > 0) {
                type = storedType;
                amount = storedAmount;
                return;
            }
        }
        setEmpty();
    }

    private static InfuseType readInfuseType(String name) {
        InfuseType infuseType = InfuseRegistry.get(name);
        if (infuseType == null && name != null) {
            int namespaceIndex = name.indexOf(':');
            String normalized = namespaceIndex == -1 ? name : name.substring(namespaceIndex + 1);
            infuseType = InfuseRegistry.get(normalized.toUpperCase());
        }
        return infuseType;
    }

    public InfuseType getType() {
        return amount == 0 ? null : type;
    }

    public InfuseStorage setType(InfuseType type) {
        this.type = type;
        return this;
    }

    public int getAmount() {
        return type == null ? 0 : amount;
    }

    public InfuseStorage setAmount(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.amount = amount;
        return this;
    }

    public void setEmpty() {
        this.amount = 0;
        this.type = null;
    }
}
