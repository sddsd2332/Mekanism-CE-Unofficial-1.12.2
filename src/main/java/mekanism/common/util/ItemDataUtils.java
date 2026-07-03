package mekanism.common.util;

import mekanism.api.DataHandlerUtils;
import mekanism.api.NBTConstants;
import mekanism.api.gas.GasStack;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.gas.BasicGasTank;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemDataUtils {

    public static final String DATA_ID = "mekData";

    @Nonnull
    public static NBTTagCompound getDataMap(ItemStack stack) {
        initStack(stack);
        return stack.getTagCompound().getCompoundTag(DATA_ID);
    }

    @Nullable
    public static NBTTagCompound getDataMapIfPresent(ItemStack stack) {
        return hasDataTag(stack) ? getDataMap(stack) : null;
    }

    @Nonnull
    public static NBTTagCompound getDataMapIfPresentNN(ItemStack stack) {
        return hasDataTag(stack) ? getDataMap(stack) : new NBTTagCompound();
    }

    public static boolean hasData(ItemStack stack, String key) {
        if (!hasDataTag(stack)) {
            return false;
        }
        return getDataMap(stack).hasKey(key);
    }

    public static boolean hasData(ItemStack stack, String key, int type) {
        return hasDataTag(stack) && getDataMap(stack).hasKey(key, type);
    }

    public static void removeData(ItemStack stack, String key) {
        if (!hasDataTag(stack)) {
            return;
        }
        getDataMap(stack).removeTag(key);
    }

    public static int getInt(ItemStack stack, String key) {
        if (!hasDataTag(stack)) {
            return 0;
        }
        return getDataMap(stack).getInteger(key);
    }

    public static boolean getBoolean(ItemStack stack, String key) {
        if (!hasDataTag(stack)) {
            return false;
        }
        return getDataMap(stack).getBoolean(key);
    }

    public static double getDouble(ItemStack stack, String key) {
        if (!hasDataTag(stack)) {
            return 0;
        }
        return getDataMap(stack).getDouble(key);
    }

    public static String getString(ItemStack stack, String key) {
        if (!hasDataTag(stack)) {
            return "";
        }
        return getDataMap(stack).getString(key);
    }

    public static NBTTagCompound getCompound(ItemStack stack, String key) {
        if (!hasDataTag(stack)) {
            return new NBTTagCompound();
        }
        return getDataMap(stack).getCompoundTag(key);
    }

    public static NBTTagCompound getOrAddCompound(ItemStack stack, String key) {
        NBTTagCompound dataMap = getDataMap(stack);
        if (dataMap.hasKey(key, 10)) {
            return dataMap.getCompoundTag(key);
        }
        NBTTagCompound compound = new NBTTagCompound();
        dataMap.setTag(key, compound);
        return compound;
    }

    public static NBTTagList getList(ItemStack stack, String key) {
        if (!hasDataTag(stack)) {
            return new NBTTagList();
        }
        return getDataMap(stack).getTagList(key, NBT.TAG_COMPOUND);
    }

    @Nullable
    public static FluidStack getFirstFluidStack(ItemStack stack, String key) {
        if (!hasData(stack, key, NBT.TAG_LIST)) {
            return null;
        }
        NBTTagList tanks = getList(stack, key);
        for (int tank = 0; tank < tanks.tagCount(); tank++) {
            NBTTagCompound tankData = tanks.getCompoundTagAt(tank);
            NBTTagCompound stored = tankData.hasKey(NBTConstants.STORED, NBT.TAG_COMPOUND) ? tankData.getCompoundTag(NBTConstants.STORED) : tankData;
            FluidStack fluidStack = FluidStack.loadFluidStackFromNBT(stored);
            if (fluidStack != null && fluidStack.amount > 0) {
                return fluidStack;
            }
        }
        return null;
    }

    @Nullable
    public static GasStack getFirstGasStack(ItemStack stack, String key) {
        if (!hasData(stack, key, NBT.TAG_LIST)) {
            return null;
        }
        NBTTagList tanks = getList(stack, key);
        for (int tank = 0; tank < tanks.tagCount(); tank++) {
            NBTTagCompound tankData = tanks.getCompoundTagAt(tank);
            NBTTagCompound stored = tankData.hasKey(NBTConstants.STORED, NBT.TAG_COMPOUND) ? tankData.getCompoundTag(NBTConstants.STORED) : tankData;
            GasStack gasStack = GasStack.readFromNBT(stored);
            if (gasStack != null && gasStack.amount > 0) {
                return gasStack;
            }
        }
        return null;
    }

    @Nullable
    public static GasStack getStoredGas(ItemStack stack, String legacyKey) {
        return getFirstGasStack(stack, NBTConstants.GAS_TANKS);
    }

    @Nullable
    public static FluidStack getStoredFluid(ItemStack stack, String legacyKey) {
        return getFirstFluidStack(stack, NBTConstants.FLUID_TANKS);
    }

    public static void setInt(ItemStack stack, String key, int i) {
        initStack(stack);
        getDataMap(stack).setInteger(key, i);
    }

    public static void setBoolean(ItemStack stack, String key, boolean b) {
        initStack(stack);
        getDataMap(stack).setBoolean(key, b);
    }

    public static void setDouble(ItemStack stack, String key, double d) {
        initStack(stack);
        getDataMap(stack).setDouble(key, d);
    }

    public static void setStoredGas(ItemStack stack, String legacyKey, @Nullable GasStack gasStack, int maxGas) {
        if (gasStack == null || gasStack.amount <= 0 || gasStack.getGas() == null) {
            removeData(stack, legacyKey);
            removeData(stack, NBTConstants.GAS_TANKS);
            cleanEmptyDataMap(stack);
        } else {
            int amount = Math.max(0, Math.min(gasStack.amount, maxGas));
            if (amount <= 0) {
                removeData(stack, legacyKey);
                removeData(stack, NBTConstants.GAS_TANKS);
                cleanEmptyDataMap(stack);
                return;
            }
            GasStack storedGas = new GasStack(gasStack.getGas(), amount);
            NBTTagCompound stored = storedGas.write(new NBTTagCompound());
            removeData(stack, legacyKey);
            NBTTagCompound tank = new NBTTagCompound();
            tank.setByte(NBTConstants.TANK, (byte) 0);
            tank.setTag(NBTConstants.STORED, stored.copy());
            NBTTagList tanks = new NBTTagList();
            tanks.appendTag(tank);
            setList(stack, NBTConstants.GAS_TANKS, tanks);
        }
    }

    public static void setLegacyGas(ItemStack stack, String legacyKey, @Nullable GasStack gasStack) {
        if (gasStack == null || gasStack.amount <= 0 || gasStack.getGas() == null) {
            removeData(stack, legacyKey);
            cleanEmptyDataMap(stack);
        } else {
            setCompound(stack, legacyKey, gasStack.write(new NBTTagCompound()));
        }
    }

    @Nullable
    public static GasStack getLegacyGas(ItemStack stack, String legacyKey) {
        return hasData(stack, legacyKey, NBT.TAG_COMPOUND) ? GasStack.readFromNBT(getCompound(stack, legacyKey)) : null;
    }

    public static void setLegacyGasTank(ItemStack stack, String legacyKey, @Nullable BasicGasTank tank) {
        GasStack gasStack = tank == null ? null : tank.getGas();
        if (gasStack == null || gasStack.amount <= 0 || gasStack.getGas() == null) {
            removeData(stack, legacyKey);
            cleanEmptyDataMap(stack);
        } else {
            setCompound(stack, legacyKey, tank.write(new NBTTagCompound()));
        }
    }

    public static boolean readLegacyGasTank(ItemStack stack, String legacyKey, BasicGasTank tank) {
        if (hasData(stack, legacyKey, NBT.TAG_COMPOUND)) {
            tank.read(getCompound(stack, legacyKey));
            return true;
        }
        return false;
    }

    public static void setStoredFluid(ItemStack stack, String legacyKey, @Nullable FluidStack fluidStack, int maxFluid) {
        if (fluidStack == null || fluidStack.amount <= 0 || fluidStack.getFluid() == null) {
            removeData(stack, legacyKey);
            removeData(stack, NBTConstants.FLUID_TANKS);
            cleanEmptyDataMap(stack);
        } else {
            int amount = Math.max(0, Math.min(fluidStack.amount, maxFluid));
            if (amount <= 0) {
                removeData(stack, legacyKey);
                removeData(stack, NBTConstants.FLUID_TANKS);
                cleanEmptyDataMap(stack);
                return;
            }
            FluidStack storedFluid = new FluidStack(fluidStack, amount);
            NBTTagCompound stored = storedFluid.writeToNBT(new NBTTagCompound());
            removeData(stack, legacyKey);
            NBTTagCompound tank = new NBTTagCompound();
            tank.setByte(NBTConstants.TANK, (byte) 0);
            tank.setTag(NBTConstants.STORED, stored.copy());
            NBTTagList tanks = new NBTTagList();
            tanks.appendTag(tank);
            setList(stack, NBTConstants.FLUID_TANKS, tanks);
        }
    }

    public static void setLegacyFluid(ItemStack stack, String legacyKey, @Nullable FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.amount <= 0 || fluidStack.getFluid() == null) {
            removeData(stack, legacyKey);
            cleanEmptyDataMap(stack);
        } else {
            setCompound(stack, legacyKey, fluidStack.writeToNBT(new NBTTagCompound()));
        }
    }

    @Nullable
    public static FluidStack getLegacyFluid(ItemStack stack, String legacyKey) {
        return hasData(stack, legacyKey, NBT.TAG_COMPOUND) ? FluidStack.loadFluidStackFromNBT(getCompound(stack, legacyKey)) : null;
    }

    public static void setLegacyFluidTank(ItemStack stack, String legacyKey, @Nullable BasicFluidTank tank) {
        FluidStack fluidStack = tank == null ? null : tank.getFluid();
        if (fluidStack == null || fluidStack.amount <= 0 || fluidStack.getFluid() == null) {
            removeData(stack, legacyKey);
            cleanEmptyDataMap(stack);
        } else {
            setCompound(stack, legacyKey, tank.writeToNBT(new NBTTagCompound()));
        }
    }

    public static boolean readLegacyFluidTank(ItemStack stack, String legacyKey, BasicFluidTank tank) {
        if (hasData(stack, legacyKey, NBT.TAG_COMPOUND)) {
            tank.readFromNBT(getCompound(stack, legacyKey));
            return true;
        }
        return false;
    }

    public static void setString(ItemStack stack, String key, String s) {
        initStack(stack);
        getDataMap(stack).setString(key, s);
    }

    public static void setCompound(ItemStack stack, String key, NBTTagCompound tag) {
        initStack(stack);
        getDataMap(stack).setTag(key, tag);
    }

    public static void setList(ItemStack stack, String key, NBTTagList tag) {
        initStack(stack);
        getDataMap(stack).setTag(key, tag);
    }

    public static void setListOrRemove(ItemStack stack, String key, NBTTagList tag) {
        if (tag.isEmpty()) {
            removeData(stack, key);
        } else {
            setList(stack, key, tag);
        }
    }

    public static void readContainers(ItemStack stack, String key, List<? extends INBTSerializable<NBTTagCompound>> containers) {
        DataHandlerUtils.readContainers(containers, getList(stack, key));
    }

    public static void writeContainers(ItemStack stack, String key, List<? extends INBTSerializable<NBTTagCompound>> containers) {
        setListOrRemove(stack, key, DataHandlerUtils.writeContainers(containers));
    }

    private static void cleanEmptyDataMap(ItemStack stack) {
        if (!hasDataTag(stack)) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (getDataMap(stack).isEmpty()) {
            tag.removeTag(DATA_ID);
        }
        if (tag.isEmpty()) {
            stack.setTagCompound(null);
        }
    }

    private static boolean hasDataTag(ItemStack stack) {
        return stack.getTagCompound() != null && stack.getTagCompound().hasKey(DATA_ID);
    }

    private static void initStack(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        if (!stack.getTagCompound().hasKey(DATA_ID)) {
            stack.getTagCompound().setTag(DATA_ID, new NBTTagCompound());
        }
    }
}
