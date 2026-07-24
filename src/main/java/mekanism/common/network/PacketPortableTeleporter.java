package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.content.teleporter.TeleporterFrequency;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.item.ItemPortableTeleporter;
import mekanism.common.inventory.container.item.ItemStackSlotAccess;
import mekanism.common.network.PacketPortableTeleporter.PortableTeleporterMessage;
import mekanism.common.network.PacketPortalFX.PortalFXMessage;
import mekanism.common.tile.TileEntityTeleporter;
import mekanism.common.util.SecurityUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPortableTeleporter implements IMessageHandler<PortableTeleporterMessage, IMessage> {

    @Override
    public IMessage onMessage(PortableTeleporterMessage message, MessageContext context) {
        if (!message.valid) {
            return null;
        }
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            ItemStack itemstack = ItemStackSlotAccess.getStack(player.inventory, message.currentHand, message.itemSlot);
            World world = player.world;
            if (itemstack.isEmpty() || !(itemstack.getItem() instanceof ItemPortableTeleporter teleporterItem) ||
                  !SecurityUtils.canAccess(player, itemstack) || message.identity == null ||
                  !message.identity.equals(teleporterItem.getFrequency(itemstack))) {
                return;
            }
            TeleporterFrequency found = FrequencyType.TELEPORTER.getFrequency(message.identity, player.getUniqueID());
            if (found == null) {
                return;
            }
            Coord4D coords = found.getClosestCoords(new Coord4D(player));
            if (coords == null) {
                return;
            }
            World teleWorld = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(coords.dimensionId);
            if (teleWorld == null || !(coords.getTileEntity(teleWorld) instanceof TileEntityTeleporter teleporter) || !SecurityUtils.canAccess(player, teleporter)) {
                return;
            }
            double energyCost = ItemPortableTeleporter.calculateEnergyCost(player, coords);
            if (energyCost > StorageUtils.getStoredEnergy(itemstack)) {
                return;
            }
            teleporter.didTeleport.add(player.getPersistentID());
            teleporter.teleDelay = 5;
            StorageUtils.extractFromContainer(itemstack, energyCost, mekanism.api.Action.EXECUTE);
            if (player instanceof EntityPlayerMP mp) {
                mp.connection.floatingTickCount = 0;
            }
            player.closeScreen();
            Mekanism.packetHandler.sendToAllTracking(new PortalFXMessage(new Coord4D(player)), coords);
            if (player instanceof EntityPlayerMP mp) {
                TileEntityTeleporter.teleportPlayerTo(mp, coords, teleporter);
                TileEntityTeleporter.alignPlayer(mp, coords);
            }
            world.playSound(player, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
            Mekanism.packetHandler.sendToAllTracking(new PortalFXMessage(coords), coords);
        }, player);
        return null;
    }

    public enum PortableTeleporterPacketType {
        TELEPORT
    }

    public static class PortableTeleporterMessage implements IMessage {

        public PortableTeleporterPacketType packetType = PortableTeleporterPacketType.TELEPORT;
        public EnumHand currentHand;
        public int itemSlot = -1;
        public FrequencyIdentity identity;
        private boolean valid;

        public PortableTeleporterMessage() {
        }

        public PortableTeleporterMessage(PortableTeleporterPacketType type, EnumHand hand, FrequencyIdentity identity) {
            this(type, hand, -1, identity);
        }

        public PortableTeleporterMessage(PortableTeleporterPacketType type, EnumHand hand, int itemSlot, FrequencyIdentity identity) {
            packetType = type;
            currentHand = hand;
            this.itemSlot = itemSlot;
            this.identity = identity;
            valid = type != null && hand != null && ItemStackSlotAccess.isValidSlot(hand, itemSlot) && identity != null;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(packetType.ordinal());
            buffer.writeInt(currentHand.ordinal());
            buffer.writeInt(itemSlot);
            FrequencyType.writeIdentity(buffer, FrequencyType.TELEPORTER, identity);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                int typeOrdinal = buffer.readInt();
                int handOrdinal = buffer.readInt();
                itemSlot = buffer.readInt();
                if (typeOrdinal < 0 || typeOrdinal >= PortableTeleporterPacketType.values().length ||
                      handOrdinal < 0 || handOrdinal >= EnumHand.values().length) {
                    return;
                }
                packetType = PortableTeleporterPacketType.values()[typeOrdinal];
                currentHand = EnumHand.values()[handOrdinal];
                identity = FrequencyType.readIdentity(buffer, FrequencyType.TELEPORTER);
                valid = ItemStackSlotAccess.isValidSlot(currentHand, itemSlot) && identity != null;
            } catch (RuntimeException ex) {
                packetType = PortableTeleporterPacketType.TELEPORT;
                currentHand = EnumHand.MAIN_HAND;
                itemSlot = -1;
                identity = null;
            }
        }

        public boolean isValid() {
            return valid;
        }
    }
}
