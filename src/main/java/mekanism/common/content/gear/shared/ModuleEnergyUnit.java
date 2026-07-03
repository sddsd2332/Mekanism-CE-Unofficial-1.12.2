package mekanism.common.content.gear.shared;

import mekanism.api.annotations.ParametersAreNotNullByDefault;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.common.config.MekanismConfig;
import mekanism.common.item.armor.ItemMekaSuitArmor;

@ParametersAreNotNullByDefault
public class ModuleEnergyUnit implements ICustomModule<ModuleEnergyUnit> {

    public double getEnergyCapacity(IModule<ModuleEnergyUnit> module, double base) {
        return base * (Math.pow(2, module.getInstalledCount()));
    }

    public double getEnergyCapacity(IModule<ModuleEnergyUnit> module) {
        double base = module.getContainer().getItem() instanceof ItemMekaSuitArmor ? MekanismConfig.current().meka.mekaSuitBaseEnergyCapacity.val() : MekanismConfig.current().meka.mekaToolBaseEnergyCapacity.val();
        return base * (Math.pow(2, module.getInstalledCount()));
    }

    public double getChargeRate(IModule<ModuleEnergyUnit> module, double base) {
        return base * (Math.pow(2, module.getInstalledCount()));
    }

    public double getChargeRate(IModule<ModuleEnergyUnit> module) {
        double base = module.getContainer().getItem() instanceof ItemMekaSuitArmor ? MekanismConfig.current().meka.mekaSuitBaseChargeRate.val()
                : MekanismConfig.current().meka.mekaToolBaseChargeRate.val();
        return base * (Math.pow(2, module.getInstalledCount()));
    }

    @Override
    public void onRemoved(IModule<ModuleEnergyUnit> module, boolean last) {
        IEnergyContainer energyContainer = module.getEnergyContainer();
        if (energyContainer != null) {
            energyContainer.setEnergy(Math.min(energyContainer.getEnergy(), energyContainer.getMaxEnergy()));
        }
    }
}
