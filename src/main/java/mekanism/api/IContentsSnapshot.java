package mekanism.api;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Provides a listener-free snapshot/restore path for local container transaction rollback.
 */
public interface IContentsSnapshot {

    NBTTagCompound createContentsSnapshot();

    void restoreContentsSnapshot(NBTTagCompound snapshot);
}
