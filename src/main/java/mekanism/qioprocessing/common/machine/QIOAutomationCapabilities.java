package mekanism.qioprocessing.common.machine;

import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

/**
 * QIO 处理模块中的 QIOAutomationCapabilities 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationCapabilities {

    public static final ResourceLocation NAME = new ResourceLocation("mekanismqioprocessing", "automation_host");

    @CapabilityInject(QIOAutomationHost.class)
    public static Capability<QIOAutomationHost> AUTOMATION_HOST = null;

    private static boolean registered;

    private QIOAutomationCapabilities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        CapabilityManager.INSTANCE.register(QIOAutomationHost.class, new Capability.IStorage<>() {
            @Override
            public NBTBase writeNBT(Capability<QIOAutomationHost> capability, QIOAutomationHost instance,
                  EnumFacing side) {
                if (instance instanceof DefaultQIOAutomationHost host && !host.shouldPersistCapabilityNbt()) {
                    return new NBTTagCompound();
                }
                NBTBase serialized = instance.serializeNBT();
                // CapabilityDispatcher writes the returned tag unconditionally. A provider
                // supplied by an addon must not be able to turn a transient empty state into
                // an IllegalArgumentException by returning null.
                return serialized == null ? new NBTTagCompound() : serialized;
            }

            @Override
            public void readNBT(Capability<QIOAutomationHost> capability, QIOAutomationHost instance,
                  EnumFacing side, NBTBase nbt) {
                // Forge may pass null or a non-compound tag when a capability key is absent or
                // damaged. Always drive the host through its clean empty-state path instead of
                // leaving a reused instance carrying stale automation ownership.
                instance.deserializeNBT(nbt instanceof NBTTagCompound compound ?
                      compound : new NBTTagCompound());
            }
        }, DefaultQIOAutomationHost::new);
        registered = true;
    }
}
