package mekanism.common.capabilities;

import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.DefaultStorageHelper.NullStorage;
import net.minecraftforge.common.capabilities.CapabilityManager;

public class DefaultStrictEnergyHandler implements IStrictEnergyHandler {

    public static void register() {
        CapabilityManager.INSTANCE.register(IStrictEnergyHandler.class, new NullStorage<>(), DefaultStrictEnergyHandler::new);
    }

    @Override
    public int getEnergyContainerCount() {
        return 0;
    }

    @Override
    public double getEnergy(int container) {
        return 0;
    }

    @Override
    public void setEnergy(int container, double energy) {
    }

    @Override
    public double getMaxEnergy(int container) {
        return 0;
    }

    @Override
    public double getNeededEnergy(int container) {
        return 0;
    }

    @Override
    public double insertEnergy(int container, double amount, Action action) {
        return amount;
    }

    @Override
    public double extractEnergy(int container, double amount, Action action) {
        return 0;
    }
}
