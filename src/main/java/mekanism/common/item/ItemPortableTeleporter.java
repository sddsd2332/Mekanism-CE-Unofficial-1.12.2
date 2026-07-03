package mekanism.common.item;

import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.client.MekanismClient;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyItem;
import mekanism.common.network.PacketSecurityUpdate.SecurityPacket;
import mekanism.common.network.PacketSecurityUpdate.SecurityUpdateMessage;
import mekanism.common.security.IOwnerItem;
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

public class ItemPortableTeleporter extends ItemEnergized implements IOwnerItem, IFrequencyItem {

    public ItemPortableTeleporter() {
        super(1000000);
        setMaxStackSize(1);
        setRarity(EnumRarity.RARE);
    }

    public static double calculateEnergyCost(Entity entity, Coord4D coords) {
        if (coords == null) {
            return 0;
        }
        int neededEnergy = MekanismConfig.current().usage.teleporterBase.val();
        if (entity.world.provider.getDimension() != coords.dimensionId) {
            neededEnergy += MekanismConfig.current().usage.teleporterDimensionPenalty.val();
        } else {
            int distance = (int) entity.getDistance(coords.x, coords.y, coords.z);
            neededEnergy += distance * MekanismConfig.current().usage.teleporterDistance.val();
        }
        return neededEnergy;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, World world, List<String> list, ITooltipFlag flag) {
        list.add(SecurityUtils.getOwnerDisplay(Minecraft.getMinecraft().player, MekanismClient.clientUUIDMap.get(getOwnerUUID(itemstack))));
        if (getFrequency(itemstack) != null) {
            list.add(EnumColor.INDIGO + LangUtils.localize("gui.frequency") + ": " + EnumColor.GREY + getFrequency(itemstack).key);
            list.add(EnumColor.INDIGO + LangUtils.localize("gui.mode") + ": " + EnumColor.GREY +
                    LangUtils.localize("gui." + getFrequency(itemstack).securityMode.name().toLowerCase(java.util.Locale.ROOT)));
        }
        super.addInformation(itemstack, world, list, flag);
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer entityplayer, @Nonnull EnumHand hand) {
        ItemStack itemstack = entityplayer.getHeldItem(hand);
        if (!world.isRemote) {
            if (getOwnerUUID(itemstack) == null) {
                setOwnerUUID(itemstack, entityplayer.getUniqueID());
                Mekanism.packetHandler.sendToAll(new SecurityUpdateMessage(SecurityPacket.UPDATE, entityplayer.getUniqueID(), null));
                entityplayer.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY + LangUtils.localize("gui.nowOwn")));
            } else if (SecurityUtils.canAccess(entityplayer, itemstack)) {
                MekanismUtils.openItemGui(entityplayer, hand, 14);
            } else {
                SecurityUtils.displayNoAccess(entityplayer);
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
    }

    @Override
    public boolean canSendEnergy(ItemStack itemStack) {
        return false;
    }

    @Override
    public UUID getOwnerUUID(ItemStack stack) {
        if (ItemDataUtils.hasData(stack, "ownerUUID")) {
            try {
                return UUID.fromString(ItemDataUtils.getString(stack, "ownerUUID"));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void setOwnerUUID(ItemStack stack, UUID owner) {
        setFrequency(stack, null);
        if (owner == null) {
            ItemDataUtils.removeData(stack, "ownerUUID");
            return;
        }
        ItemDataUtils.setString(stack, "ownerUUID", owner.toString());
    }

    @Override
    public boolean hasOwner(ItemStack stack) {
        return true;
    }

    @Override
    public FrequencyType<?> getFrequencyType() {
        return FrequencyType.TELEPORTER;
    }
}
