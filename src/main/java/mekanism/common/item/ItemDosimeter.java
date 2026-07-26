package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.MekanismLang;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.lib.radiation.RadiationUtil;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

public class ItemDosimeter extends ItemMekanism {

    public ItemDosimeter() {
        super();
        setRarity(EnumRarity.RARE);
        setMaxStackSize(1);
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(@NotNull World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack itemstack = player.getHeldItem(hand);
        if (!player.isSneaking()) {
            if (!world.isRemote) {
                sendDose(player, player, MekanismLang.RADIATION_DOSE);
                if (player instanceof EntityPlayerMP playerMP) {
                    MekanismCriteriaTriggers.USE_DOSIMETER.trigger(playerMP);
                }
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
        }
        return new ActionResult<>(EnumActionResult.PASS, itemstack);
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase target, EnumHand hand) {
        if (player.isSneaking()) {
            return false;
        }
        if (!player.world.isRemote) {
            sendDose(target, player, MekanismLang.RADIATION_EXPOSURE_ENTITY);
        }
        return true;
    }

    private static void sendDose(EntityLivingBase target, EntityPlayer player, MekanismLang label) {
        IRadiationEntity radiationCapability = target.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
        if (radiationCapability == null) {
            return;
        }
        double radiation = RadiationManager.INSTANCE.isRadiationEnabled() ? radiationCapability.getRadiation() : RadiationManager.BASELINE;
        RadiationManager.RadiationScale scale = RadiationManager.RadiationScale.get(radiation);
        player.sendMessage(label.translateColored(EnumColor.GREY)
              .appendText(scale.getSeverityColor(radiation) +
                    UnitDisplayUtils.getDisplayShort(radiation, UnitDisplayUtils.RadiationUnit.SV, 3)));
        if (MekanismConfig.current().general.radiationDecayTimers.val() && radiation > RadiationManager.MIN_MAGNITUDE) {
            player.sendMessage(MekanismLang.RADIATION_DECAY_TIME.translateColored(EnumColor.GREY)
                  .appendText(scale.getSeverityColor(radiation) +
                        TextUtils.getHoursMinutesFromTicks(RadiationUtil.getDecayTime(radiation, false))));
        }
    }
}
