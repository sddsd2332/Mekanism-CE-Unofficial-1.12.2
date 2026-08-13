package mekanism.qioprocessing.common.machine;

import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QIOAutomationHostProvider implements ICapabilitySerializable<NBTTagCompound> {

    private final DefaultQIOAutomationHost host;

    public QIOAutomationHostProvider(@Nonnull TileEntity tile) {
        host = new DefaultQIOAutomationHost(tile);
    }

    DefaultQIOAutomationHost host() {
        return host;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == QIOAutomationCapabilities.AUTOMATION_HOST;
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == QIOAutomationCapabilities.AUTOMATION_HOST ?
              QIOAutomationCapabilities.AUTOMATION_HOST.cast(host) : null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return host.serializeNBT();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        host.deserializeNBT(nbt);
    }
}
