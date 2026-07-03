package mekanism.common.item;

import mekanism.api.Action;
import mekanism.api.Chunk3D;
import mekanism.api.EnumColor;
import mekanism.client.MekKeyHandler;
import mekanism.client.MekanismKeyHandler;
import mekanism.common.Mekanism;
import mekanism.common.advancements.MekanismCriteriaTriggers;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.List;

public class ItemSeismicReader extends ItemEnergized {

    public static final double ENERGY_USAGE = 250;

    public ItemSeismicReader() {
        super(12000);
        setMaxStackSize(1);
        setRarity(EnumRarity.UNCOMMON);
    }

    @Override
    public boolean canSendEnergy(ItemStack itemStack) {
        return false;
    }

    @Override
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.sneakKey)) {
            list.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.INDIGO + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) +
                    EnumColor.GREY + " " + LangUtils.localize("tooltip.forDetails") + ".");
            list.add(LangUtils.localize("tooltip.hold") + " " + EnumColor.AQUA + GameSettings.getKeyDisplayString(MekanismKeyHandler.sneakKey.getKeyCode()) +
                    EnumColor.GREY + " " + LangUtils.localize("tooltip.and") + " " + EnumColor.AQUA +
                    GameSettings.getKeyDisplayString(MekanismKeyHandler.handModeSwitchKey.getKeyCode()) + EnumColor.GREY + " " + LangUtils.localize("tooltip.forDesc") + ".");
        } else if (!MekKeyHandler.getIsKeyPressed(MekanismKeyHandler.handModeSwitchKey)) {
            super.addInformation(itemstack, world, list, flag);
        } else {
            list.addAll(MekanismUtils.splitTooltip(LangUtils.localize("tooltip.mekanism.SeismicReader"), itemstack));
        }
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer entityplayer, @Nonnull EnumHand hand) {
        Chunk3D chunk = new Chunk3D(entityplayer);
        ItemStack itemstack = entityplayer.getHeldItem(hand);

        if (StorageUtils.getStoredEnergy(itemstack) < ENERGY_USAGE && !entityplayer.capabilities.isCreativeMode) {
            if (!world.isRemote) {
                entityplayer.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.RED +
                        LangUtils.localize("tooltip.seismicReader.needsEnergy")));
            }

            return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
        } else if (!MekanismUtils.isChunkVibrated(chunk)) {
            if (!world.isRemote) {
                entityplayer.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.RED +
                        LangUtils.localize("tooltip.seismicReader.noVibrations")));
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
        }
        if (!entityplayer.capabilities.isCreativeMode) {
            StorageUtils.extractEnergy(itemstack, ENERGY_USAGE, Action.EXECUTE);
        }
        if (!world.isRemote && entityplayer instanceof EntityPlayerMP playerMP) {
            MekanismCriteriaTriggers.VIEW_VIBRATIONS.trigger(playerMP);
        }
        MekanismUtils.openItemGui(entityplayer, hand, 38);
        return new ActionResult<>(EnumActionResult.PASS, itemstack);
    }
}
