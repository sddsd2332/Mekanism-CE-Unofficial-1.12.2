package mekanism.common.content.matrix;

import mekanism.common.multiblock.MultiblockCache;
import net.minecraft.nbt.NBTTagCompound;

public class MatrixCache extends MultiblockCache<SynchronizedMatrixData> {

    @Override
    public void apply(SynchronizedMatrixData data) {
        applyInventory(data);
    }

    @Override
    public void sync(SynchronizedMatrixData data) {
        syncInventory(data);
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        loadInventory(nbtTags);
    }

    @Override
    public void save(NBTTagCompound nbtTags) {
        saveInventory(nbtTags);
    }
}
