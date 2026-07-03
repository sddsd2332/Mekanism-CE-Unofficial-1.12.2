package mekanism.common.integration.tesla;

import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.ItemCapabilityWrapper.ItemCapability;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.util.StorageUtils;
import net.darkhax.tesla.api.ITeslaConsumer;
import net.darkhax.tesla.api.ITeslaHolder;
import net.darkhax.tesla.api.ITeslaProducer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.InterfaceList;
import net.minecraftforge.fml.common.Optional.Method;

@InterfaceList({
        @Interface(iface = "net.darkhax.tesla.api.ITeslaConsumer", modid = MekanismHooks.TESLA_MOD_ID),
        @Interface(iface = "net.darkhax.tesla.api.ITeslaProducer", modid = MekanismHooks.TESLA_MOD_ID),
        @Interface(iface = "net.darkhax.tesla.api.ITeslaHolder", modid = MekanismHooks.TESLA_MOD_ID)
})
public class TeslaItemWrapper extends ItemCapability implements ITeslaHolder, ITeslaConsumer, ITeslaProducer {

    @Override
    public boolean canProcess(Capability<?> capability) {
        return capability == Capabilities.TESLA_HOLDER_CAPABILITY || capability == Capabilities.TESLA_CONSUMER_CAPABILITY && StorageUtils.canReceiveEnergy(getStack()) ||
                capability == Capabilities.TESLA_PRODUCER_CAPABILITY && StorageUtils.canExtractEnergy(getStack());
    }

    private IStrictEnergyHandler getEnergyHandler() {
        return StorageUtils.getEnergyHandler(getStack());
    }

    @Override
    @Method(modid = MekanismHooks.TESLA_MOD_ID)
    public long takePower(long power, boolean simulate) {
        IStrictEnergyHandler energyHandler = getEnergyHandler();
        if (energyHandler != null && power > 0) {
            return TeslaIntegration.toTesla(energyHandler.extractEnergy(TeslaIntegration.fromTesla(power), Action.get(!simulate)));
        }
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.TESLA_MOD_ID)
    public long givePower(long power, boolean simulate) {
        IStrictEnergyHandler energyHandler = getEnergyHandler();
        if (energyHandler != null && power > 0) {
            double amount = TeslaIntegration.fromTesla(power);
            double remainder = energyHandler.insertEnergy(amount, Action.get(!simulate));
            return TeslaIntegration.toTesla(amount - remainder);
        }
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.TESLA_MOD_ID)
    public long getStoredPower() {
        return TeslaIntegration.toTesla(StorageUtils.getStoredEnergy(getStack()));
    }

    @Override
    @Method(modid = MekanismHooks.TESLA_MOD_ID)
    public long getCapacity() {
        return TeslaIntegration.toTesla(StorageUtils.getMaxEnergy(getStack()));
    }
}
