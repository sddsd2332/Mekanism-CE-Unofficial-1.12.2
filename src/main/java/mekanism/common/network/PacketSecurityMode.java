package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.entity.EntityRobit;
import mekanism.common.network.PacketSecurityMode.SecurityModeMessage;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

public class PacketSecurityMode implements IMessageHandler<SecurityModeMessage, IMessage> {

    @Override
    public IMessage onMessage(SecurityModeMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (message.packetType == SecurityPacketType.BLOCK) {
                TileEntity tileEntity = message.coord4D.getTileEntity(player.world);
                if (!PacketHandler.canAccessTile(player, tileEntity)) {
                    return;
                }
                if (tileEntity instanceof ISecurityTile securityTile) {
                    UUID owner = securityTile.getSecurity().getOwnerUUID();
                    if (owner != null && player.getUniqueID().equals(owner)) {
                        securityTile.getSecurity().setMode(message.value);
                        tileEntity.markDirty();
                    }
                }
            } else if (message.packetType == SecurityPacketType.ITEM) {
                ItemStack stack = player.getHeldItem(message.currentHand);
                if (!stack.isEmpty() && stack.getItem() instanceof ISecurityItem item && SecurityUtils.canAccess(player, stack)) {
                    item.setSecurity(stack, message.value);
                }
            } else if (message.packetType == SecurityPacketType.ENTITY) {
                if (player.world.getEntityByID(message.entityId) instanceof EntityRobit robit && player.getDistanceSq(robit) <= 64) {
                    if (player.getUniqueID().equals(robit.getOwnerUUID())) {
                        robit.setSecurityMode(message.value);
                    }
                }
            }
        }, player);
        return null;
    }

    public enum SecurityPacketType {
        BLOCK,
        ITEM,
        ENTITY
    }

    public static class SecurityModeMessage implements IMessage {

        public SecurityPacketType packetType;
        public Coord4D coord4D;
        public EnumHand currentHand;
        public int entityId;
        public SecurityMode value;

        public SecurityModeMessage() {
        }

        public SecurityModeMessage(Coord4D coord, SecurityMode control) {
            packetType = SecurityPacketType.BLOCK;
            coord4D = coord;
            value = control;
        }

        public SecurityModeMessage(EnumHand hand, SecurityMode control) {
            packetType = SecurityPacketType.ITEM;
            currentHand = hand;
            value = control;
        }

        public SecurityModeMessage(EntityRobit robit, SecurityMode control) {
            packetType = SecurityPacketType.ENTITY;
            entityId = robit.getEntityId();
            value = control;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeInt(packetType.ordinal());
            if (packetType == SecurityPacketType.BLOCK) {
                coord4D.write(dataStream);
            } else if (packetType == SecurityPacketType.ITEM) {
                dataStream.writeInt(currentHand.ordinal());
            } else if (packetType == SecurityPacketType.ENTITY) {
                dataStream.writeInt(entityId);
            }
            dataStream.writeInt(value.ordinal());
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            packetType = MekanismUtils.getByIndex(SecurityPacketType.values(), dataStream.readInt(), SecurityPacketType.BLOCK);
            if (packetType == SecurityPacketType.BLOCK) {
                coord4D = Coord4D.read(dataStream);
            } else if (packetType == SecurityPacketType.ITEM) {
                currentHand = MekanismUtils.getByIndex(EnumHand.values(), dataStream.readInt(), EnumHand.MAIN_HAND);
            } else if (packetType == SecurityPacketType.ENTITY) {
                entityId = dataStream.readInt();
            }
            value = MekanismUtils.getByIndex(SecurityMode.values(), dataStream.readInt(), SecurityMode.PUBLIC);
        }
    }
}
