package mekanism.common.integration.forgeenergy;

import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.ItemCapabilityWrapper.ItemCapability;
import mekanism.common.util.StorageUtils;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public class ForgeEnergyItemWrapper extends ItemCapability implements IEnergyStorage {

    @Override
    public boolean canProcess(Capability<?> capability) {
        return capability == CapabilityEnergy.ENERGY;
    }

    private IStrictEnergyHandler getEnergyHandler() {
        return StorageUtils.getEnergyHandler(getStack());
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        IStrictEnergyHandler energyHandler = getEnergyHandler();
        if (energyHandler != null && maxReceive > 0) {
            double amount = ForgeEnergyIntegration.fromForge(maxReceive);
            double remainder = energyHandler.insertEnergy(amount, Action.get(!simulate));
            return ForgeEnergyIntegration.toForge(amount - remainder);
        }
        return 0;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        IStrictEnergyHandler energyHandler = getEnergyHandler();
        if (energyHandler != null && maxExtract > 0) {
            return ForgeEnergyIntegration.toForge(energyHandler.extractEnergy(ForgeEnergyIntegration.fromForge(maxExtract), Action.get(!simulate)));
        }
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return ForgeEnergyIntegration.toForge(StorageUtils.getStoredEnergy(getStack()));
    }

    @Override
    public int getMaxEnergyStored() {
        return ForgeEnergyIntegration.toForge(StorageUtils.getMaxEnergy(getStack()));
    }

    @Override
    public boolean canExtract() {
        return StorageUtils.canExtractEnergy(getStack());
    }

    @Override
    public boolean canReceive() {
        return StorageUtils.canReceiveEnergy(getStack());
    }
}
