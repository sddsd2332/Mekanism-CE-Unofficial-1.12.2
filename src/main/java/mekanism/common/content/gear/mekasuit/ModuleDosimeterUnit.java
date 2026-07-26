package mekanism.common.content.gear.mekasuit;

import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IHUDElement;
import mekanism.api.gear.IHUDElement.HUDColor;
import mekanism.api.gear.IModule;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.ModuleHelper;
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
public class ModuleDosimeterUnit implements ICustomModule<ModuleDosimeterUnit> {

    private static final ResourceLocation icon = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI_HUD, "dosimeter.png");

    @Override
    public void addHUDElements(IModule<ModuleDosimeterUnit> module, EntityPlayer player, Consumer<IHUDElement> hudElementAdder) {
        if (module.isEnabled()) {
            if (player.hasCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)) {
                IRadiationEntity entity = player.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
                assert entity != null;
                double radiation = entity.getRadiation();
                String text = UnitDisplayUtils.getDisplayShort(radiation, UnitDisplayUtils.RadiationUnit.SV, 2);
                if (MekanismConfig.current().general.radiationDecayTimers.val() && radiation > RadiationManager.MIN_MAGNITUDE) {
                    text += " (" + TextUtils.getHoursMinutesFromTicks(RadiationUtil.getDecayTime(radiation, false)) + ")";
                }
                hudElementAdder.accept(ModuleHelper.get().hudElement(icon, text, radiation < RadiationManager.MIN_MAGNITUDE ? HUDColor.REGULAR : (radiation < 0.1 ? HUDColor.WARNING : HUDColor.DANGER)));
            }
        }
    }


}
