package mekanism.common.content.gear.mekasuit;

import mekanism.api.gas.GasStack;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IHUDElement;
import mekanism.api.gear.IModule;
import mekanism.common.MekanismFluids;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.item.armor.ItemMekaSuitArmor;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.common.util.StorageUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Consumer;

@ParametersAreNonnullByDefault
public class ModuleNutritionalInjectionUnit implements ICustomModule<ModuleNutritionalInjectionUnit> {

    private static final ResourceLocation icon = MekanismUtils.getResource(ResourceType.GUI_HUD, "nutritional_injection_unit.png");

    @Override
    public void tickServer(IModule<ModuleNutritionalInjectionUnit> module, EntityPlayer player) {
        double usage = MekanismConfig.current().meka.mekaSuitEnergyUsageNutritionalInjection.val();
        if (MekanismUtils.isPlayingMode(player) && player.canEat(false)) {
            //Check if we can use a single iteration of it
            ItemStack container = module.getContainer();
            GasStack stored = ((ItemMekaSuitArmor) container.getItem()).getContainedGas(container, MekanismFluids.NutritionalPaste);
            if (stored != null && stored.getGas() == MekanismFluids.NutritionalPaste) {
                int needed = Math.min(20 - player.getFoodStats().getFoodLevel(), stored.amount / MekanismConfig.current().general.nutritionalPasteMBPerFood.val());
                int toFeed = Math.min((int) (module.getContainerEnergy() / usage), needed);
                if (toFeed > 0) {
                    int amountToDrain = toFeed * MekanismConfig.current().general.nutritionalPasteMBPerFood.val();
                    GasStack used = ((ItemMekaSuitArmor) container.getItem()).useGas(container, MekanismFluids.NutritionalPaste, amountToDrain);
                    if (used != null && used.amount > 0) {
                        int fed = used.amount / MekanismConfig.current().general.nutritionalPasteMBPerFood.val();
                        if (fed > 0) {
                            module.useEnergy(player, usage * fed);
                            player.getFoodStats().addStats(fed, fed * MekanismConfig.current().general.nutritionalPasteSaturation.val());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void addHUDElements(IModule<ModuleNutritionalInjectionUnit> module, EntityPlayer player, Consumer<IHUDElement> hudElementAdder) {
        if (module.isEnabled()) {
            ItemStack container = module.getContainer();
            GasStack stored = ((ItemMekaSuitArmor) container.getItem()).getContainedGas(container, MekanismFluids.NutritionalPaste);
            int amount = stored != null && stored.getGas() == MekanismFluids.NutritionalPaste ? stored.amount : 0;
            double ratio = StorageUtils.getRatio(amount, MekanismConfig.current().meka.mekaSuitNutritionalMaxStorage.val());
            hudElementAdder.accept(ModuleHelper.get().hudElementPercent(icon, ratio));
        }
    }
}
