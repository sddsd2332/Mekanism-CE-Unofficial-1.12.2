package mekanism.common.content.gear.mekasuit;

import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IHUDElement;
import mekanism.api.gear.IModule;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.lib.radiation.RadiationUtil;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Consumer;

@ParametersAreNonnullByDefault
public class ModuleGeigerUnit implements ICustomModule<ModuleGeigerUnit> {

    private static final ResourceLocation icon = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_HUD, "geiger_counter.png");

    @Override
    public void addHUDElements(IModule<ModuleGeigerUnit> module, EntityPlayer player, Consumer<IHUDElement> hudElementAdder) {
        if (module.isEnabled()) {
            double magnitude = RadiationManager.INSTANCE.getClientEnvironmentalRadiation();
            String text = UnitDisplayUtils.getDisplayShort(magnitude, UnitDisplayUtils.RadiationUnit.SVH, 2);
            if (MekanismConfig.current().general.radiationDecayTimers.val() && magnitude > RadiationManager.BASELINE) {
                text += " (" + TextUtils.getHoursMinutesFromTicks(
                      RadiationUtil.getDecayTime(RadiationManager.INSTANCE.getClientMaxMagnitude(), true)) + ")";
            }
            hudElementAdder.accept(ModuleHelper.get().hudElement(icon, text, magnitude <= RadiationManager.BASELINE ? IHUDElement.HUDColor.REGULAR : (magnitude < 0.1 ? IHUDElement.HUDColor.WARNING : IHUDElement.HUDColor.DANGER)));
        }
    }


}
