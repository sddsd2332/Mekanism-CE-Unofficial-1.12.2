package mekanism.common.capabilities.energy.item;

import mekanism.api.NBTConstants;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.ItemCapabilityWrapper.ItemCapability;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Helper class for implementing strict energy handlers for items.
 */
public abstract class ItemStackMekanismEnergyHandler extends ItemCapability implements IMekanismStrictEnergyHandler {

    protected List<IEnergyContainer> energyContainers;

    protected abstract List<IEnergyContainer> getInitialContainers();

    @Override
    protected void init() {
        super.init();
        energyContainers = getInitialContainers();
    }

    @Override
    protected void load() {
        super.load();
        ItemDataUtils.readContainers(getStack(), NBTConstants.ENERGY_CONTAINERS, serializableContainers());
    }

    @Nonnull
    @Override
    public List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing side) {
        return energyContainers;
    }

    @Override
    public void onContentsChanged() {
        ItemDataUtils.writeContainers(getStack(), NBTConstants.ENERGY_CONTAINERS, serializableContainers());
    }

    @Override
    public boolean canProcess(Capability<?> capability) {
        return capability == Capabilities.STRICT_ENERGY_CAPABILITY || capability == Capabilities.ENERGY_STORAGE_CAPABILITY ||
               capability == Capabilities.ENERGY_ACCEPTOR_CAPABILITY || capability == Capabilities.ENERGY_OUTPUTTER_CAPABILITY;
    }

    @SuppressWarnings("unchecked")
    private List<? extends INBTSerializable<NBTTagCompound>> serializableContainers() {
        return (List<? extends INBTSerializable<NBTTagCompound>>) (List<?>) energyContainers;
    }
}
