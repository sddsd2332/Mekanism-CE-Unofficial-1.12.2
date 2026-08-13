package mekanism.qioprocessing.common.machine;

import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

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
                return instance.serializeNBT();
            }

            @Override
            public void readNBT(Capability<QIOAutomationHost> capability, QIOAutomationHost instance,
                  EnumFacing side, NBTBase nbt) {
                if (nbt instanceof NBTTagCompound compound) {
                    instance.deserializeNBT(compound);
                }
            }
        }, DefaultQIOAutomationHost::new);
        registered = true;
    }
}
