package mekanism.qioprocessing.common.machine;

import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * QIO 处理模块中的 QIOAutomationHostProvider 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
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
        return host.shouldPersistCapabilityNbt() ? host.serializeNBT() : new NBTTagCompound();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        host.deserializeNBT(nbt);
    }
}
