package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.common.entity.EntityRobit;
import mekanism.common.network.PacketRobit.RobitMessage;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;

public class PacketRobit implements IMessageHandler<RobitMessage, IMessage> {

    @Override
    public IMessage onMessage(RobitMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (player.world.getEntityByID(message.entityId) instanceof EntityRobit robit) {
                if (!SecurityUtils.canAccess(player, robit)) {
                    return;
                }
                // Do not allow remote control packets from arbitrarily far away.
                if (player.getDistanceSq(robit) > 64) {
                    return;
                }
                switch (message.activeType) {
                    case GUI:
                        MekanismUtils.openEntityGui(player, robit, message.guiID);
                        break;
                    case FOLLOW:
                        robit.setFollowing(!robit.getFollowing());
                        break;
                    case NAME:
                        if (RobitMessage.hasContent(message.name)) {
                            robit.setCustomNameTag(message.name);
                        }
                        break;
                    case GO_HOME:
                        robit.goHome();
                        break;
                    case DROP_PICKUP:
                        robit.setDropPickup(!robit.getDropPickup());
                        break;
                }
            }
        }, player);
        return null;
    }

    public enum RobitPacketType {
        GUI,
        FOLLOW,
        NAME,
        GO_HOME,
        DROP_PICKUP
    }

    public static class RobitMessage implements IMessage {

        public static final int MAX_NAME_LENGTH = 50;

        public RobitPacketType activeType;

        public int entityId;
        public int guiID;
        public String name;

        public RobitMessage() {
        }

        public RobitMessage(RobitPacketType type, int entityId) {
            activeType = type;
            this.entityId = entityId;
        }

        public RobitMessage(int entityId, @Nonnull String name) {
            activeType = RobitPacketType.NAME;
            this.entityId = entityId;
            this.name = trimName(name);
        }

        public RobitMessage(int entityId, int guiID) {
            activeType = RobitPacketType.GUI;
            this.entityId = entityId;
            this.guiID = guiID;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeInt(activeType.ordinal());
            dataStream.writeInt(entityId);
            if (activeType == RobitPacketType.NAME) {
                PacketHandler.writeString(dataStream, trimName(name));
            } else if (activeType == RobitPacketType.GUI) {
                dataStream.writeInt(guiID);
            }
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            activeType = MekanismUtils.getByIndex(RobitPacketType.values(), dataStream.readInt(), RobitPacketType.GUI);
            entityId = dataStream.readInt();
            if (activeType == RobitPacketType.NAME) {
                name = trimName(PacketHandler.readString(dataStream));
            } else if (activeType == RobitPacketType.GUI) {
                guiID = dataStream.readInt();
            }
        }

        private static String trimName(String name) {
            if (name == null) {
                return "";
            }
            name = name.trim();
            return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
        }

        public static boolean hasContent(String text) {
            if (text == null) {
                return false;
            }
            text = trimName(text);
            if (!text.isEmpty()) {
                boolean wasColorSymbol = false;
                for (char c : text.toCharArray()) {
                    if (c == '\u00A7') {
                        wasColorSymbol = true;
                    } else if (!wasColorSymbol) {
                        return true;
                    } else {
                        wasColorSymbol = false;
                    }
                }
            }
            return false;
        }
    }
}
