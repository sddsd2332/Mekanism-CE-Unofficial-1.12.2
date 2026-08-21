package mekanism.common.util;

import mekanism.api.EnumColor;
import mekanism.client.MekanismClient;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.entity.EntityRobit;
import mekanism.common.frequency.Frequency;
import mekanism.common.security.*;
import mekanism.common.security.ISecurityTile.SecurityMode;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;

import java.util.UUID;
import javax.annotation.Nullable;

public final class SecurityUtils {

    public static boolean canAccess(EntityPlayer player, ItemStack stack) {
        // If protection is disabled, access is always granted
        if (!MekanismConfig.current().general.allowProtection.val()) {
            return true;
        }
        if (!(stack.getItem() instanceof ISecurityItem) && stack.getItem() instanceof IOwnerItem iOwnerItem) {
            UUID owner = iOwnerItem.getOwnerUUID(stack);
            return owner == null || owner.equals(player.getUniqueID());
        }
        if (stack.isEmpty() || !(stack.getItem() instanceof ISecurityItem security)) {
            return true;
        }
        if (MekanismUtils.isOp(player)) {
            return true;
        }
        return canAccess(security.getSecurity(stack), player, security.getOwnerUUID(stack));
    }

    public static boolean canAccess(EntityPlayer player, TileEntity tile) {
        if (!(tile instanceof ISecurityTile security)) {
            return true;
        }
        if (MekanismUtils.isOp(player)) {
            return true;
        }
        return canAccess(security.getSecurity().getMode(), player, security.getSecurity().getOwnerUUID());
    }

    /**
     * Applies the complete Mekanism security policy to a network-owned UUID.
     * Offline subjects do not receive operator bypass privileges.
     */
    public static boolean canAccess(@Nullable UUID subject, TileEntity tile) {
        if (!(tile instanceof ISecurityTile security)) {
            return true;
        }
        if (isOnlineOperator(subject)) {
            return true;
        }
        return canAccess(security.getSecurity().getMode(), subject, security.getSecurity().getOwnerUUID());
    }

    /** Applies owner, trusted-list and security-station override rules without requiring a player entity. */
    public static boolean canAccess(SecurityMode mode, @Nullable UUID subject, @Nullable UUID owner) {
        if (!MekanismConfig.current().general.allowProtection.val()) {
            return true;
        }
        if (owner == null || owner.equals(subject)) {
            return true;
        }
        SecurityFrequency frequency = getFrequency(owner);
        if (frequency != null && frequency.override) {
            mode = frequency.securityMode;
        }
        if (mode == null || mode == SecurityMode.PUBLIC) {
            return true;
        }
        return mode == SecurityMode.TRUSTED && frequency != null && frequency.isTrusted(subject);
    }

    public static boolean canAccess(EntityPlayer player, EntityRobit robit) {
        if (robit == null) {
            return true;
        }
        if (MekanismUtils.isOp(player)) {
            return true;
        }
        return canAccess(robit.getSecurityMode(), player, robit.getOwnerUUID());
    }

    private static boolean canAccess(SecurityMode mode, EntityPlayer player, UUID owner) {
        // If protection is disabled, access is always granted
        if (!MekanismConfig.current().general.allowProtection.val()) {
            return true;
        }
        return canAccess(mode, player.getUniqueID(), owner);
    }

    private static boolean isOnlineOperator(@Nullable UUID subject) {
        if (subject == null || !MekanismConfig.current().general.opsBypassRestrictions.val()) {
            return false;
        }
        net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance()
              .getMinecraftServerInstance();
        if (server == null) {
            return false;
        }
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(subject);
        return player != null && MekanismUtils.isOp(player);
    }

    public static SecurityFrequency getFrequency(UUID uuid) {
        return uuid == null ? null : Mekanism.securityFrequencies.getFrequency(uuid);
    }

    public static boolean isTrusted(SecurityMode mode, UUID owner, UUID subject) {
        if (mode != SecurityMode.TRUSTED) {
            return false;
        }
        if (owner == null || subject == null) {
            return false;
        }
        if (owner.equals(subject)) {
            return true;
        }
        SecurityFrequency frequency = getFrequency(owner);
        return frequency != null && frequency.isTrusted(subject);
    }

    public static String getOwnerDisplay(EntityPlayer player, String ownerName) {
        if (ownerName == null) {
            return EnumColor.RED + LangUtils.localize("gui.noOwner");
        }
        return EnumColor.GREY + LangUtils.localize("gui.owner") + ": " + (player.getName().equals(ownerName)
                ? EnumColor.BRIGHT_GREEN : EnumColor.RED) + ownerName;
    }

    public static void displayNoAccess(EntityPlayer player) {
        player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.RED + LangUtils.localize("gui.noAccessDesc")));
    }

    public static SecurityMode getSecurity(ISecurityTile security, Side side) {
        if (side == Side.SERVER) {
            SecurityFrequency freq = security.getSecurity().getFrequency();
            if (freq != null && freq.override) {
                return freq.securityMode;
            }
        } else if (side == Side.CLIENT) {
            SecurityData data = MekanismClient.clientSecurityMap.get(security.getSecurity().getOwnerUUID());
            if (data != null && data.override) {
                return data.mode;
            }
        }
        return security.getSecurity().getMode();
    }

    public static String getSecurityDisplay(ItemStack stack, Side side) {
        ISecurityItem security = (ISecurityItem) stack.getItem();
        SecurityMode mode = security.getSecurity(stack);
        if (security.getOwnerUUID(stack) != null) {
            if (side == Side.SERVER) {
                SecurityFrequency freq = getFrequency(security.getOwnerUUID(stack));
                if (freq != null && freq.override) {
                    mode = freq.securityMode;
                }
            } else if (side == Side.CLIENT) {
                SecurityData data = MekanismClient.clientSecurityMap.get(security.getOwnerUUID(stack));
                if (data != null && data.override) {
                    mode = data.mode;
                }
            }
        }
        return mode.getDisplay();
    }

    public static String getSecurityDisplay(TileEntity tile, Side side) {
        ISecurityTile security = (ISecurityTile) tile;
        SecurityMode mode = security.getSecurity().getMode();
        if (security.getSecurity().getOwnerUUID() != null) {
            if (side == Side.SERVER) {
                SecurityFrequency freq = getFrequency(security.getSecurity().getOwnerUUID());
                if (freq != null && freq.override) {
                    mode = freq.securityMode;
                }
            } else if (side == Side.CLIENT) {
                SecurityData data = MekanismClient.clientSecurityMap.get(security.getSecurity().getOwnerUUID());
                if (data != null && data.override) {
                    mode = data.mode;
                }
            }
        }
        return mode.getDisplay();
    }

    public static String getSecurityDisplay(EntityRobit robit, Side side) {
        SecurityMode mode = robit.getSecurityMode();
        UUID owner = robit.getOwnerUUID();
        if (owner != null) {
            if (side == Side.SERVER) {
                SecurityFrequency freq = getFrequency(owner);
                if (freq != null && freq.override) {
                    mode = freq.securityMode;
                }
            } else if (side == Side.CLIENT) {
                SecurityData data = MekanismClient.clientSecurityMap.get(owner);
                if (data != null && data.override) {
                    mode = data.mode;
                }
            }
        }
        return mode.getDisplay();
    }

    public static boolean isOverridden(ItemStack stack, Side side) {
        ISecurityItem security = (ISecurityItem) stack.getItem();
        if (security.getOwnerUUID(stack) == null) {
            return false;
        }
        if (side == Side.SERVER) {
            SecurityFrequency freq = getFrequency(security.getOwnerUUID(stack));
            return freq != null && freq.override;
        }
        SecurityData data = MekanismClient.clientSecurityMap.get(security.getOwnerUUID(stack));
        return data != null && data.override;
    }

    public static boolean isOverridden(TileEntity tile, Side side) {
        ISecurityTile security = (ISecurityTile) tile;
        if (security.getSecurity().getOwnerUUID() == null) {
            return false;
        }
        if (side == Side.SERVER) {
            SecurityFrequency freq = getFrequency(security.getSecurity().getOwnerUUID());
            return freq != null && freq.override;
        }
        SecurityData data = MekanismClient.clientSecurityMap.get(security.getSecurity().getOwnerUUID());
        return data != null && data.override;
    }

    public static boolean isOverridden(EntityRobit robit, Side side) {
        UUID owner = robit.getOwnerUUID();
        if (owner == null) {
            return false;
        }
        if (side == Side.SERVER) {
            SecurityFrequency freq = getFrequency(owner);
            return freq != null && freq.override;
        }
        SecurityData data = MekanismClient.clientSecurityMap.get(owner);
        return data != null && data.override;
    }
}
