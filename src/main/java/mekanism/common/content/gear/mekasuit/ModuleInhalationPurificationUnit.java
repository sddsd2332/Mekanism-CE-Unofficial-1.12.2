package mekanism.common.content.gear.mekasuit;

import mekanism.api.annotations.ParametersAreNotNullByDefault;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.config.IModuleConfigItem;
import mekanism.api.gear.config.ModuleBooleanData;
import mekanism.api.gear.config.ModuleConfigItemCreator;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

@ParametersAreNotNullByDefault
public class ModuleInhalationPurificationUnit implements ICustomModule<ModuleInhalationPurificationUnit> {

    private static final ModuleDamageAbsorbInfo INHALATION_ABSORB_INFO = new ModuleDamageAbsorbInfo(MekanismConfig.current().meka.mekaSuitMagicDamageRatio,
            MekanismConfig.current().meka.mekaSuitEnergyUsageMagicReduce.val());

    private IModuleConfigItem<Boolean> beneficialEffects;
    private IModuleConfigItem<Boolean> harmfulEffects;

    @Override
    public void init(IModule<ModuleInhalationPurificationUnit> module, ModuleConfigItemCreator configItemCreator) {
        beneficialEffects = configItemCreator.createConfigItem("beneficial_effects", MekanismLang.MODULE_PURIFICATION_BENEFICIAL, new ModuleBooleanData(false));
        harmfulEffects = configItemCreator.createConfigItem("harmful_effects", MekanismLang.MODULE_PURIFICATION_HARMFUL, new ModuleBooleanData());
    }

    @Override
    public void tickClient(IModule<ModuleInhalationPurificationUnit> module, EntityPlayer player) {
        //Messy rough estimate version of tickServer so that the timer actually properly updates
        if (!player.isSpectator()) {
            double usage = MekanismConfig.current().meka.mekaSuitEnergyUsagePotionTick.val();
            boolean free = usage == 0 || player.isCreative();
            double energy = free ? 0 : module.getContainerEnergy();
            if (free || energy >= usage) {
                //Gather all the active effects that we can handle, so that we have them in their own list and
                // don't run into any issues related to CMEs
                List<PotionEffect> effects = player.getActivePotionEffects().stream().filter(this::canHandle).collect(Collectors.toList());
                for (PotionEffect effect : effects) {
                    if (free) {
                        speedupEffect(player, effect);
                    } else {
                        energy = energy - (usage);
                        speedupEffect(player, effect);
                        if (energy < (usage)) {
                            //If after using energy, our remaining energy is now smaller than how much we need to use, exit
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void tickServer(IModule<ModuleInhalationPurificationUnit> module, EntityPlayer player) {
        double usage = MekanismConfig.current().meka.mekaSuitEnergyUsagePotionTick.val();
        boolean free = usage == 0 || player.isCreative();
        IEnergyContainer energyContainer = free ? null : module.getEnergyContainer();
        if (free || (energyContainer != null && energyContainer.getEnergy() >= usage)) {
            //Gather all the active effects that we can handle, so that we have them in their own list and
            // don't run into any issues related to CMEs
            List<PotionEffect> effects = player.getActivePotionEffects().stream().filter(this::canHandle).collect(Collectors.toList());
            for (PotionEffect effect : effects) {
                if (free) {
                    speedupEffect(player, effect);
                } else if (module.useEnergy(player, energyContainer, usage, true) == 0) {
                    //If we can't actually extract energy, exit
                    break;
                } else {
                    speedupEffect(player, effect);
                    if (energyContainer.getEnergy() < usage) {
                        //If after using energy, our remaining energy is now smaller than how much we need to use, exit
                        break;
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public ModuleDamageAbsorbInfo getDamageAbsorbInfo(IModule<ModuleInhalationPurificationUnit> module, DamageSource damageSource) {
        return damageSource.isMagicDamage() ? INHALATION_ABSORB_INFO : null;
    }

    private void speedupEffect(EntityPlayer player, PotionEffect effect) {
        for (int i = 0; i < 9; i++) {
            MekanismUtils.speedUpEffectSafely(player, effect);
        }
    }

    private boolean canHandle(PotionEffect effectInstance) {
        if (effectInstance.getPotion().isBadEffect()) {
            return harmfulEffects.get();
        } else {
            return beneficialEffects.get();
        }
    }
}
