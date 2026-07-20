package mekanism.common.item;

import mekanism.api.EnumColor;
import mekanism.common.Mekanism;
import mekanism.common.QIOGuiConstants;
import mekanism.common.frequency.FrequencyAware;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyItem;
import mekanism.common.item.interfaces.IColoredItem;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/** Portable QIO viewer with frequency/security identity and a derived color cache. */
public class ItemPortableQIODashboard extends ItemMekanism implements IFrequencyItem, ISecurityItem, IColoredItem {

    public ItemPortableQIODashboard() {
        setMaxStackSize(1);
        setRarity(EnumRarity.RARE);
    }

    @Override
    public FrequencyType<?> getFrequencyType() {
        return FrequencyType.QIO;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            if (getOwnerUUID(stack) == null) {
                setOwnerUUID(stack, player.getUniqueID());
                player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY + LangUtils.localize("gui.nowOwn")));
            } else if (SecurityUtils.canAccess(player, stack)) {
                MekanismUtils.openItemGui(player, hand, QIOGuiConstants.PORTABLE_DASHBOARD);
            } else {
                SecurityUtils.displayNoAccess(player);
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        if (getFrequency(stack) != null) {
            tooltip.add(EnumColor.INDIGO + LangUtils.localize("gui.frequency") + ": " + EnumColor.GREY + getFrequency(stack).key);
            tooltip.add(EnumColor.INDIGO + LangUtils.localize("gui.mode") + ": " + EnumColor.GREY +
                  LangUtils.localize("gui." + getFrequency(stack).securityMode.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    @Override
    public UUID getOwnerUUID(ItemStack stack) {
        String value = ItemDataUtils.getString(stack, "ownerUUID");
        if (value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public void setOwnerUUID(ItemStack stack, UUID owner) {
        setFrequencyAware(stack, null);
        if (owner == null) {
            ItemDataUtils.removeData(stack, "ownerUUID");
        } else {
            ItemDataUtils.setString(stack, "ownerUUID", owner.toString());
        }
    }

    @Override
    public boolean hasOwner(ItemStack stack) {
        return true;
    }

    @Override
    public SecurityMode getSecurity(ItemStack stack) {
        int ordinal = ItemDataUtils.getInt(stack, "security");
        return ordinal >= 0 && ordinal < SecurityMode.values().length ? SecurityMode.values()[ordinal] : SecurityMode.PUBLIC;
    }

    @Override
    public void setSecurity(ItemStack stack, SecurityMode mode) {
        if (mode == null || mode == SecurityMode.PUBLIC) {
            ItemDataUtils.removeData(stack, "security");
        } else {
            ItemDataUtils.setInt(stack, "security", mode.ordinal());
        }
    }

    @Override
    public boolean hasSecurity(ItemStack stack) {
        return true;
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);
        if (world == null || world.isRemote || world.getTotalWorldTime() % 100 != 0 || !(entity instanceof EntityPlayer)) {
            return;
        }
        syncColorWithFrequency(stack);
    }

    /**
     * Cache the server-resolved QIO channel color on the item so the client
     * can tint the portable dashboard without resolving a frequency locally.
     */
    @Override
    public void setFrequencyAware(ItemStack stack, FrequencyAware<?> frequencyAware) {
        IFrequencyItem.super.setFrequencyAware(stack, frequencyAware);
        syncColorWithFrequency(stack, frequencyAware == null ? null : frequencyAware.frequency());
    }

}
