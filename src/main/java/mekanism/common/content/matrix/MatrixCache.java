package mekanism.common.content.matrix;

import mekanism.common.multiblock.MultiblockCache;
import net.minecraft.nbt.NBTTagCompound;

import java.io.IOException;

public class MatrixCache extends MultiblockCache<SynchronizedMatrixData> {

    @Override
    public void validateCapacity(SynchronizedMatrixData target) throws IOException {
        super.validateCapacity(target);
        requireMerge(target.hasValidEnergy(), "Induction cells have invalid energy or matrix capacity");
    }

    @Override
    public void apply(SynchronizedMatrixData data) {
        applyInventory(data);
    }

    @Override
    public void sync(SynchronizedMatrixData data) {
        data.flushEnergy();
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
