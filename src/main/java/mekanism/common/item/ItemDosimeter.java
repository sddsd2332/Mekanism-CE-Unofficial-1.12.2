package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.MekanismLang;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.util.UnitDisplayUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
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
                if (player.hasCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)) {
                    IRadiationEntity c = player.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
                    if (c != null) {
                        player.sendMessage(new TextComponentString(EnumColor.GREY + MekanismLang.RADIATION_DOSE.getTranslationKey() + RadiationManager.RadiationScale.getSeverityColor(c.getRadiation()) + UnitDisplayUtils.getDisplayShort(c.getRadiation(), UnitDisplayUtils.RadiationUnit.SV, 3)));
                        if (player instanceof EntityPlayerMP playerMP) {
                            MekanismCriteriaTriggers.USE_DOSIMETER.trigger(playerMP);
                        }
                    }
                }
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
        }
        return new ActionResult<>(EnumActionResult.PASS, itemstack);
    }
}
