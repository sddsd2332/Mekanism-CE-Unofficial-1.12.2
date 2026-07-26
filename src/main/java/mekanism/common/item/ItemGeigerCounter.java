package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.radiation.LevelAndMaxMagnitude;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.lib.radiation.RadiationManager.RadiationScale;
import mekanism.common.lib.radiation.RadiationUtil;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.text.TextUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ItemGeigerCounter extends ItemMekanism {

    public ItemGeigerCounter() {
        super();
        setRarity(EnumRarity.UNCOMMON);
        setMaxStackSize(1);
        this.addPropertyOverride(Mekanism.rl("radiation"), new IItemPropertyGetter() {
            @SideOnly(Side.CLIENT)
            public float apply(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn) {
                if (entityIn instanceof EntityPlayer) {
                    return RadiationManager.INSTANCE.getClientScale().ordinal();
                }
                return 0.0F;
            }
        });
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(@NotNull World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack itemstack = player.getHeldItem(hand);
        if (!player.isSneaking()) {
            if (!world.isRemote) {
                LevelAndMaxMagnitude radiation = RadiationManager.INSTANCE.getRadiationLevelAndMaxMagnitude(player);
                double magnitude = radiation.getLevel();
                player.sendMessage(MekanismLang.RADIATION_EXPOSURE.translateColored(EnumColor.GREY)
                      .appendText(RadiationScale.getSeverityColor(magnitude) +
                            UnitDisplayUtils.getDisplayShort(magnitude, UnitDisplayUtils.RadiationUnit.SVH, 3)));
                if (MekanismConfig.current().general.radiationDecayTimers.val() && magnitude > RadiationManager.BASELINE) {
                    player.sendMessage(MekanismLang.RADIATION_DECAY_TIME.translateColored(EnumColor.GREY)
                          .appendText(RadiationScale.getSeverityColor(magnitude) + TextUtils.getHoursMinutesFromTicks(
                                RadiationUtil.getDecayTime(radiation.getMaxMagnitude(), true))));
                }
                if (player instanceof EntityPlayerMP playerMP) {
                    MekanismCriteriaTriggers.USE_GEIGER_COUNTER.trigger(playerMP);
                }
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
        }
        return new ActionResult<>(EnumActionResult.PASS, itemstack);
    }
}
