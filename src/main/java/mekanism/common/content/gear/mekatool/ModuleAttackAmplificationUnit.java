package mekanism.common.content.gear.mekatool;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.EnumColor;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.config.IModuleConfigItem;
import mekanism.api.gear.config.ModuleConfigItemCreator;
import mekanism.api.gear.config.ModuleEnumData;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.TextComponentGroup;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Consumer;

@ParametersAreNonnullByDefault
public class ModuleAttackAmplificationUnit implements ICustomModule<ModuleAttackAmplificationUnit> {

    private IModuleConfigItem<AttackDamage> attackDamage;

    @Override
    public void init(IModule<ModuleAttackAmplificationUnit> module, ModuleConfigItemCreator configItemCreator) {
        attackDamage = configItemCreator.createConfigItem("attack_damage", MekanismLang.MODULE_ATTACK_DAMAGE, new ModuleEnumData<>(AttackDamage.MED, module.getInstalledCount() + 2));
    }

    public int getDamage() {
        return attackDamage.get().getDamage();
    }

    @Override
    public void addHUDStrings(IModule<ModuleAttackAmplificationUnit> module, EntityPlayer player, Consumer<String> hudStringAdder) {
        if (module.isEnabled()) {
            hudStringAdder.accept(MekanismLang.MODULE_DAMAGE.translateColored(EnumColor.DARK_GREY).getFormattedText() + " " + EnumColor.INDIGO + attackDamage.get().getDamage());
        }
    }


    @Override
    public void hitEntity(IModule<ModuleAttackAmplificationUnit> module, ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        int unitDamage = getDamage();
        if (unitDamage > 0) {
            IEnergyContainer energyContainer = module.getEnergyContainer();
            if (energyContainer != null && !energyContainer.isEmpty()) {
                //Try to extract full energy, even if we have a lower damage amount this is fine as that just means
                // we don't have enough energy, but we will remove as much as we can, which is how much corresponds
                // to the amount of damage we will actually do
                energyContainer.extract(MekanismConfig.current().meka.mekaToolEnergyUsageWeapon.val() * (unitDamage / 4D), Action.EXECUTE, AutomationType.MANUAL);
            }
        }
    }


    public enum AttackDamage implements IHasTextComponent {
        OFF(0),
        LOW(4),
        MED(8),
        HIGH(16),
        EXTREME(24),
        MAX(32);

        private final int damage;
        private final ITextComponent label;

        AttackDamage(int damage) {
            this.damage = damage;
            this.label = new TextComponentGroup().getString(Integer.toString(damage));
        }

        @Override
        public ITextComponent getTextComponent() {
            return label;
        }

        public int getDamage() {
            return damage;
        }
    }
}
