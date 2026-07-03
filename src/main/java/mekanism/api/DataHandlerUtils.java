package mekanism.api;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.List;

public class DataHandlerUtils {

    private DataHandlerUtils() {
    }

    public static void readContainers(List<? extends INBTSerializable<NBTTagCompound>> containers, NBTTagList storedContainers) {
        readContents(containers, storedContainers, getTagByType(containers));
    }

    public static NBTTagList writeContainers(List<? extends INBTSerializable<NBTTagCompound>> containers) {
        return writeContents(containers, getTagByType(containers));
    }

    public static void readContents(List<? extends INBTSerializable<NBTTagCompound>> contents, NBTTagList storedContents, String key) {
        int size = contents.size();
        for (int tagCount = 0; tagCount < storedContents.tagCount(); tagCount++) {
            NBTTagCompound tagCompound = storedContents.getCompoundTagAt(tagCount);
            int id = tagCompound.getInteger(key);
            if (id == 0 && NBTConstants.TANK.equals(key) && !tagCompound.hasKey(key) && tagCompound.hasKey(NBTConstants.CONTAINER)) {
                id = tagCompound.getInteger(NBTConstants.CONTAINER);
            }
            if (id >= 0 && id < size) {
                contents.get(id).deserializeNBT(tagCompound);
            }
        }
    }

    public static NBTTagList writeContents(List<? extends INBTSerializable<NBTTagCompound>> contents, String key) {
        NBTTagList storedContents = new NBTTagList();
        for (int container = 0; container < contents.size(); container++) {
            NBTTagCompound tagCompound = contents.get(container).serializeNBT();
            if (!tagCompound.isEmpty()) {
                tagCompound.setInteger(key, container);
                storedContents.appendTag(tagCompound);
            }
        }
        return storedContents;
    }

    private static String getTagByType(List<? extends INBTSerializable<NBTTagCompound>> containers) {
        if (containers.isEmpty()) {
            return NBTConstants.CONTAINER;
        }
        INBTSerializable<NBTTagCompound> container = containers.get(0);
        if (container instanceof IInventorySlot) {
            return NBTConstants.SLOT;
        } else if (container instanceof IExtendedFluidTank || container instanceof IExtendedGasTank) {
            return NBTConstants.TANK;
        } else if (container instanceof IEnergyContainer || container instanceof IHeatCapacitor) {
            return NBTConstants.CONTAINER;
        }
        return NBTConstants.CONTAINER;
    }

    public static int getMaxId(NBTTagList storedContents, String key) {
        int maxId = -1;
        for (int tagCount = 0; tagCount < storedContents.tagCount(); tagCount++) {
            NBTTagCompound tagCompound = storedContents.getCompoundTagAt(tagCount);
            int id = tagCompound.getInteger(key);
            if (id == 0 && NBTConstants.TANK.equals(key) && !tagCompound.hasKey(key) && tagCompound.hasKey(NBTConstants.CONTAINER)) {
                id = tagCompound.getInteger(NBTConstants.CONTAINER);
            }
            if (id > maxId) {
                maxId = id;
            }
        }
        return maxId + 1;
    }
}
