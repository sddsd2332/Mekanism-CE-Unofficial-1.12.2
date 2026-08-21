package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.machine.DefaultQIOAutomationHost;
import mekanism.qioprocessing.common.machine.QIOAutomationCapabilities;
import mekanism.qioprocessing.common.machine.QIOAutomationForcedRecoveryService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server-authoritative local recovery request for a QIO automation host. */
/**
 * QIO 处理模块中的 PacketQIOAutomationRecovery 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOAutomationRecovery implements
      IMessageHandler<PacketQIOAutomationRecovery.Message, IMessage> {

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.valid) {
            return null;
        }
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player != null) {
            PacketHandler.handlePacket(() -> handle(message, player), player);
        }
        return null;
    }

    private static void handle(Message message, EntityPlayer player) {
        if (!(player.openContainer instanceof MekanismTileContainer<?> container) ||
            container.windowId != message.windowId || !container.canInteractWith(player)) {
            return;
        }
        TileEntity tile = message.position.getTileEntity(player.world);
        if (tile == null || tile != container.getTileEntity() ||
            !PacketHandler.canAccessTile(player, tile, true) ||
            !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) {
            return;
        }
        Object capability = tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
        if (capability instanceof DefaultQIOAutomationHost host &&
            (host.getRecoveryState() == QIOAutomationHost.RecoveryState.QUARANTINED ||
                  host.getState() == QIOAutomationHost.State.DATA_ERROR)) {
            QIOAutomationForcedRecoveryService.forceClear(host);
        }
    }

    public static final class Message implements IMessage {

        private int windowId;
        private Coord4D position;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, Coord4D position) {
            this.windowId = windowId;
            this.position = position;
            valid = windowId >= 0 && position != null;
        }

        public static Message create(int windowId, TileEntity tile) {
            return new Message(windowId, Coord4D.get(tile));
        }

        static Message create(int windowId, Coord4D position) {
            return new Message(windowId, position);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            position.write(buffer);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                position = Coord4D.read(buffer);
                valid = windowId >= 0 && position != null;
            } catch (RuntimeException ignored) {
            }
        }

        public boolean isValid() {
            return valid;
        }
    }
}
