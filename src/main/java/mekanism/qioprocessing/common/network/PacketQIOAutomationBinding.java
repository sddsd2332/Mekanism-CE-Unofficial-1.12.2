package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.IdentitySerializer;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.qioprocessing.common.machine.QIOAutomationBindingService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * QIO 处理模块中的 PacketQIOAutomationBinding 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOAutomationBinding implements IMessageHandler<PacketQIOAutomationBinding.Message, IMessage> {

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.valid) {
            return null;
        }
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> handle(message, player), player);
        return null;
    }

    private static void handle(Message message, EntityPlayer player) {
        if (!(player.openContainer instanceof MekanismTileContainer<?> container) ||
            container.windowId != message.windowId || !container.canInteractWith(player)) {
            return;
        }
        TileEntity tile = message.position.getTileEntity(player.world);
        if (tile == null || tile != container.getTileEntity() ||
            !PacketHandler.canAccessTile(player, tile, true)) {
            return;
        }
        if (message.bind) {
            QIOAutomationBindingService.bind(tile, message.identity, message.frequencyUUID,
                  player.getUniqueID());
        } else {
            QIOAutomationBindingService.unbind(tile, player.getUniqueID());
        }
    }

    public static final class Message implements IMessage {

        private boolean bind;
        private int windowId;
        private Coord4D position;
        @Nullable
        private FrequencyIdentity identity;
        @Nullable
        private UUID frequencyUUID;
        private boolean valid;

        public Message() {
        }

        private Message(boolean bind, int windowId, Coord4D position,
              @Nullable FrequencyIdentity identity, @Nullable UUID frequencyUUID) {
            this.bind = bind;
            this.windowId = windowId;
            this.position = position;
            this.identity = identity;
            this.frequencyUUID = frequencyUUID;
            valid = windowId >= 0 && position != null && (!bind || identity != null && frequencyUUID != null);
        }

        public static Message bind(int windowId, TileEntity tile, FrequencyIdentity identity,
              UUID frequencyUUID) {
            return new Message(true, windowId, Coord4D.get(tile), identity, frequencyUUID);
        }

        static Message bind(int windowId, Coord4D position, FrequencyIdentity identity,
              UUID frequencyUUID) {
            return new Message(true, windowId, position, identity, frequencyUUID);
        }

        public static Message unbind(int windowId, TileEntity tile) {
            return new Message(false, windowId, Coord4D.get(tile), null, null);
        }

        static Message unbind(int windowId, Coord4D position) {
            return new Message(false, windowId, position, null, null);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeBoolean(bind);
            buffer.writeInt(windowId);
            position.write(buffer);
            if (bind) {
                IdentitySerializer.NAME.write(buffer, identity);
                PacketHandler.writeString(buffer, frequencyUUID.toString());
            }
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                bind = buffer.readBoolean();
                windowId = buffer.readInt();
                position = Coord4D.read(buffer);
                if (bind) {
                    identity = IdentitySerializer.NAME.read(buffer);
                    frequencyUUID = UUID.fromString(PacketHandler.readString(buffer));
                } else {
                    identity = null;
                    frequencyUUID = null;
                }
                valid = windowId >= 0 && position != null && (!bind || identity != null && frequencyUUID != null);
            } catch (RuntimeException e) {
                valid = false;
            }
        }

        public boolean isValid() {
            return valid;
        }
    }
}
