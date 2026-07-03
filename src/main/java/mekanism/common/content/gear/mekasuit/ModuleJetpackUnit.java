package mekanism.common.content.gear.mekasuit;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.annotations.ParametersAreNotNullByDefault;
import mekanism.api.gas.GasStack;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IHUDElement;
import mekanism.api.gear.IModule;
import mekanism.api.gear.config.IModuleConfigItem;
import mekanism.api.gear.config.ModuleConfigItemCreator;
import mekanism.api.gear.config.ModuleEnumData;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.TextComponentGroup;
import mekanism.common.MekanismFluids;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.item.armor.ItemMekaSuitArmor;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import mekanism.common.util.StorageUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import java.util.function.Consumer;

@ParametersAreNotNullByDefault
public class ModuleJetpackUnit implements ICustomModule<ModuleJetpackUnit> {

    private IModuleConfigItem<JetpackMode> jetpackMode;
    private IModuleConfigItem<ThrustMultiplier> jetpack_mult;
    private IModuleConfigItem<ThrustMultiplier> jetpack_hover_mult;

    @Override
    public void init(IModule<ModuleJetpackUnit> module, ModuleConfigItemCreator configItemCreator) {
        jetpackMode = configItemCreator.createConfigItem("jetpack_mode", MekanismLang.MODULE_JETPACK_MODE, new ModuleEnumData<>(JetpackMode.NORMAL));
        jetpack_mult = configItemCreator.createConfigItem("jetpack_mult", MekanismLang.MODULE_JETPACK_MULT, new ModuleEnumData<>(ThrustMultiplier.NORMAL, module.getInstalledCount() + 1));
        jetpack_hover_mult = configItemCreator.createConfigItem("jetpack_hover_mult", MekanismLang.MODULE_JETPACK_HOVER_MULT, new ModuleEnumData<>(ThrustMultiplier.NORMAL, module.getInstalledCount() + 1));
    }

    @Override
    public void addHUDElements(IModule<ModuleJetpackUnit> module, EntityPlayer player, Consumer<IHUDElement> hudElementAdder) {
        if (module.isEnabled()) {
            ItemStack container = module.getContainer();
            GasStack stored = ((ItemMekaSuitArmor) container.getItem()).getContainedGas(container, MekanismFluids.Hydrogen);
            int amount = stored != null && stored.getGas() == MekanismFluids.Hydrogen ? stored.amount : 0;
            double ratio = StorageUtils.getRatio(amount, MekanismConfig.current().meka.mekaSuitJetpackMaxStorage.val() * module.getInstalledCount());
            hudElementAdder.accept(ModuleHelper.get().hudElementPercent(jetpackMode.get().getHUDIcon(), ratio));
        }
    }

    @Override
    public void changeMode(IModule<ModuleJetpackUnit> module, EntityPlayer player, ItemStack stack, int shift, boolean displayChangeMessage) {
        JetpackMode currentMode = getMode();
        JetpackMode newMode = currentMode.adjust(shift);
        if (currentMode != newMode) {
            jetpackMode.set(newMode);
            if (displayChangeMessage) {
                module.displayModeChange(player, MekanismLang.MODULE_JETPACK_MODE.getTranslationKey(), newMode);
            }
        }
    }

    public JetpackMode getMode() {
        return jetpackMode.get();
    }

    public float getThrustMultiplier() {
        if (jetpackMode.get() == JetpackMode.HOVER) {
            return jetpack_hover_mult.get().getMultiplier();
        }
        return jetpack_mult.get().getMultiplier();
    }

    @NothingNullByDefault
    public enum ThrustMultiplier implements IHasTextComponent {
        HALF(0.5F),
        NORMAL(1F),
        FAST(2F),
        FASTER(3F),
        FASTEST(4F);

        private final float mult;
        private final ITextComponent label;

        ThrustMultiplier(float mult) {
            this.mult = mult;
            this.label = new TextComponentGroup().getString(Float.toString(mult));
        }

        @Override
        public ITextComponent getTextComponent() {
            return label;
        }

        public float getMultiplier() {
            return mult;
        }
    }
}
