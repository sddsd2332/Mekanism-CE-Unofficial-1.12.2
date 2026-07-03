package mekanism.common.multiblock;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.api.DataHandlerUtils;
import mekanism.api.NBTConstants;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class MultiblockCache<T extends SynchronizedData<T>> implements IMekanismInventory {

    public Set<Coord4D> locations = new ObjectOpenHashSet<>();
    private final List<IInventorySlot> inventorySlots = new ArrayList<>();

    public abstract void apply(T data);

    public abstract void sync(T data);

    public abstract void load(NBTTagCompound nbtTags);

    public abstract void save(NBTTagCompound nbtTags);

    protected void applyInventory(T data) {
        List<IInventorySlot> dataSlots = data.getInternalInventorySlots();
        for (int i = 0; i < inventorySlots.size() && i < dataSlots.size(); i++) {
            dataSlots.get(i).deserializeNBT(inventorySlots.get(i).serializeNBT());
        }
    }

    protected void syncInventory(T data) {
        List<IInventorySlot> dataSlots = data.getInternalInventorySlots();
        ensureInventorySlots(dataSlots.size());
        for (int i = 0; i < dataSlots.size(); i++) {
            inventorySlots.get(i).deserializeNBT(dataSlots.get(i).serializeNBT());
        }
    }

    protected void loadInventory(NBTTagCompound nbtTags) {
        ensureInventorySlots(nbtTags.getInteger(NBTConstants.ITEMS + "_stored"));
        DataHandlerUtils.readContainers(inventorySlots, nbtTags.getTagList(NBTConstants.ITEMS, NBT.TAG_COMPOUND));
    }

    protected void saveInventory(NBTTagCompound nbtTags) {
        nbtTags.setInteger(NBTConstants.ITEMS + "_stored", inventorySlots.size());
        nbtTags.setTag(NBTConstants.ITEMS, DataHandlerUtils.writeContainers(inventorySlots));
    }

    protected void ensureInventorySlots(int count) {
        while (inventorySlots.size() < count) {
            inventorySlots.add(BasicInventorySlot.at(this, 0, 0));
        }
        while (inventorySlots.size() > count) {
            inventorySlots.remove(inventorySlots.size() - 1);
        }
    }

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing side) {
        return inventorySlots;
    }

    @Override
    public boolean hasInventory() {
        return !inventorySlots.isEmpty();
    }

    @Override
    public void onContentsChanged() {
    }
}
