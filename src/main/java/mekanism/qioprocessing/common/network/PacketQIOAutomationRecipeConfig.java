package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.PacketHandler;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigClientCache;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigSnapshot;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeConfigType;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation;
import mekanism.qioprocessing.common.machine.QIOAutomationRecipeConfigService;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * AE-style single-message protocol for QIO machine route configuration.
 * Requests, mutations, and bounded snapshots deliberately share one wire shape.
 */
/**
 * QIO 处理模块中的 PacketQIOAutomationRecipeConfig 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOAutomationRecipeConfig implements
      IMessageHandler<PacketQIOAutomationRecipeConfig.QIOAutomationRecipeConfigMessage, IMessage> {

    @Override
    public IMessage onMessage(QIOAutomationRecipeConfigMessage message, MessageContext context) {
        if (!message.valid) {
            return null;
        }
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (player.world.isRemote) {
                handleClient(message);
                return;
            }
            if (!message.valid || message.position == null ||
                message.position.dimensionId != player.world.provider.getDimension()) {
                return;
            }
            TileEntity tile = message.position.getTileEntity(player.world);
            if (!(tile instanceof TileEntityContainerBlock containerTile) ||
                !PacketHandler.canAccessTile(player, tile, true) ||
                !(player.openContainer instanceof mekanism.common.inventory.container.MekanismTileContainer<?> container) ||
                container.getTileEntity() != tile || !container.canInteractWith(player)) {
                return;
            }
            if (message.packetType == RecipeConfigPacket.SNAPSHOT) {
                return;
            }
            if (!message.configType.isInstalledIn(containerTile)) {
                sendUnavailable(tile, player, message.configType, message.requestId);
                return;
            }
            QIOAutomationRecipeConfigService.Context config =
                  QIOAutomationRecipeConfigService.open(player, tile, message.configType);
            if (config == null) {
                sendUnavailable(tile, player, message.configType, message.requestId);
                return;
            }
            QIOAutomationRecipeConfigService.MutationStatus status =
                  QIOAutomationRecipeConfigService.MutationStatus.UNCHANGED;
            if (message.packetType == RecipeConfigPacket.MUTATE) {
                if (message.mutation == null) {
                    status = QIOAutomationRecipeConfigService.MutationStatus.INVALID_TARGET;
                } else {
                    status = QIOAutomationRecipeConfigService.mutate(config,
                          message.expectedRevision, message.mutation).getStatus();
                }
            }
            sendSnapshots(tile, player, message.configType, message.requestId, status,
                  message.offset, message.pageSize, message.query);
        }, player);
        return null;
    }

    private void sendSnapshots(TileEntity tile, EntityPlayer fallback,
          QIOAutomationRecipeConfigType type, UUID requestId,
          QIOAutomationRecipeConfigService.MutationStatus status,
          int offset, int pageSize, String query) {
        boolean requesterSent = false;
        if (tile instanceof TileEntityBasicBlock basic && !basic.playersUsing.isEmpty()) {
            for (EntityPlayer user : basic.playersUsing) {
                if (user instanceof EntityPlayerMP userMP && PacketHandler.canAccessTile(user, tile, true)) {
                    boolean requester = user.getUniqueID().equals(fallback.getUniqueID());
                    sendSnapshot(tile, userMP, type,
                          requester ? requestId : null,
                          requester ? status : QIOAutomationRecipeConfigService.MutationStatus.UNCHANGED,
                          offset, pageSize, query);
                    requesterSent |= requester;
                }
            }
        }
        if (!requesterSent && fallback instanceof EntityPlayerMP playerMP) {
            sendSnapshot(tile, playerMP, type, requestId, status, offset, pageSize, query);
        }
    }

    private void sendSnapshot(TileEntity tile, EntityPlayer player,
          QIOAutomationRecipeConfigType type, UUID requestId,
          QIOAutomationRecipeConfigService.MutationStatus status,
          int offset, int pageSize, String query) {
        if (!(player instanceof EntityPlayerMP playerMP)) {
            return;
        }
        QIOAutomationRecipeConfigService.Context context =
              QIOAutomationRecipeConfigService.open(player, tile, type);
        QIOAutomationRecipeConfigSnapshot snapshot;
        try {
            snapshot = context == null ? null :
                  QIOAutomationRecipeConfigService.page(context, offset, pageSize, query);
        } catch (RuntimeException ignored) {
            snapshot = null;
            status = QIOAutomationRecipeConfigService.MutationStatus.UNAVAILABLE;
        }
        QIOAutomationRecipeConfigClientCache.Status clientStatus = toClientStatus(status,
              snapshot == null);
        QIOProcessingPacketHandler.INSTANCE.sendTo(QIOAutomationRecipeConfigMessage.snapshot(Coord4D.get(tile),
              type, requestId, clientStatus, snapshot), playerMP);
    }

    private void sendUnavailable(TileEntity tile, EntityPlayer player,
          QIOAutomationRecipeConfigType type, UUID requestId) {
        if (player instanceof EntityPlayerMP playerMP) {
            QIOProcessingPacketHandler.INSTANCE.sendTo(QIOAutomationRecipeConfigMessage.snapshot(Coord4D.get(tile),
                  type, requestId, QIOAutomationRecipeConfigClientCache.Status.UNAVAILABLE,
                  null), playerMP);
        }
    }

    private static QIOAutomationRecipeConfigClientCache.Status toClientStatus(
          QIOAutomationRecipeConfigService.MutationStatus status, boolean unavailable) {
        if (unavailable || status == QIOAutomationRecipeConfigService.MutationStatus.UNAVAILABLE) {
            return QIOAutomationRecipeConfigClientCache.Status.UNAVAILABLE;
        }
        return switch (status) {
            case REVISION_CONFLICT -> QIOAutomationRecipeConfigClientCache.Status.REVISION_CONFLICT;
            case INVALID_TARGET -> QIOAutomationRecipeConfigClientCache.Status.INVALID_TARGET;
            default -> QIOAutomationRecipeConfigClientCache.Status.OK;
        };
    }

    private void handleClient(QIOAutomationRecipeConfigMessage message) {
        if (message.packetType == RecipeConfigPacket.SNAPSHOT && message.position != null &&
            message.snapshotStatus != null) {
            try {
                QIOAutomationRecipeConfigSnapshot snapshot = message.payload.getKeySet().isEmpty() ? null :
                      QIOAutomationRecipeConfigSnapshot.read(message.payload);
                QIOAutomationRecipeConfigClientCache.apply(message.position, message.configType,
                      message.requestId, message.snapshotStatus, snapshot);
            } catch (Exception ignored) {
                // Invalid client payloads are discarded; the window will retry after its timeout.
            }
        }
    }

    public enum RecipeConfigPacket {
        REQUEST,
        MUTATE,
        SNAPSHOT
    }

    public static final class QIOAutomationRecipeConfigMessage implements IMessage {

        private RecipeConfigPacket packetType = RecipeConfigPacket.REQUEST;
        private QIOAutomationRecipeConfigType configType = QIOAutomationRecipeConfigType.SCHEDULED;
        @Nullable
        private Coord4D position;
        private UUID requestId;
        private long expectedRevision;
        private int offset;
        private int pageSize;
        private String query = "";
        @Nullable
        private QIOAutomationRecipeProfileMutation mutation;
        private QIOAutomationRecipeConfigClientCache.Status snapshotStatus;
        private NBTTagCompound payload = new NBTTagCompound();
        private boolean valid;

        public QIOAutomationRecipeConfigMessage() {
        }

        private QIOAutomationRecipeConfigMessage(Coord4D position, QIOAutomationRecipeConfigType type,
              RecipeConfigPacket packetType, UUID requestId, long expectedRevision,
              int offset, int pageSize, String query,
              @Nullable QIOAutomationRecipeProfileMutation mutation) {
            this.position = position;
            this.configType = type == null ? QIOAutomationRecipeConfigType.SCHEDULED : type;
            this.packetType = packetType;
            this.requestId = requestId;
            this.expectedRevision = expectedRevision;
            this.offset = offset;
            this.pageSize = pageSize;
            this.query = query == null ? "" : query;
            this.mutation = mutation;
            valid = structurallyValid();
        }

        public static QIOAutomationRecipeConfigMessage request(Coord4D position,
              QIOAutomationRecipeConfigType type,
              UUID requestId, int offset, int pageSize, String query) {
            return new QIOAutomationRecipeConfigMessage(position, type,
                  RecipeConfigPacket.REQUEST, requestId,
                  -1, offset, pageSize, query, null);
        }

        public static QIOAutomationRecipeConfigMessage mutate(Coord4D position,
              QIOAutomationRecipeConfigType type,
              UUID requestId, long expectedRevision,
              QIOAutomationRecipeProfileMutation mutation,
              int offset, int pageSize, String query) {
            return new QIOAutomationRecipeConfigMessage(position, type,
                  RecipeConfigPacket.MUTATE, requestId,
                  expectedRevision, offset, pageSize, query, mutation);
        }

        public static QIOAutomationRecipeConfigMessage snapshot(Coord4D position,
              QIOAutomationRecipeConfigType type, UUID requestId,
              QIOAutomationRecipeConfigClientCache.Status status,
              @Nullable QIOAutomationRecipeConfigSnapshot snapshot) {
            QIOAutomationRecipeConfigMessage message = new QIOAutomationRecipeConfigMessage();
            message.position = position;
            message.configType = type;
            message.packetType = RecipeConfigPacket.SNAPSHOT;
            message.requestId = requestId;
            message.snapshotStatus = status;
            message.payload = snapshot == null ? new NBTTagCompound() : snapshot.write();
            message.valid = message.structurallyValid();
            return message;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(packetType.ordinal());
            buffer.writeInt(configType.ordinal());
            buffer.writeBoolean(position != null);
            if (position != null) {
                position.write(buffer);
            }
            buffer.writeBoolean(requestId != null);
            if (requestId != null) {
                PacketHandler.writeUUID(buffer, requestId);
            }
            buffer.writeLong(expectedRevision);
            buffer.writeInt(offset);
            buffer.writeInt(pageSize);
            PacketHandler.writeString(buffer, query);
            buffer.writeBoolean(mutation != null);
            if (mutation != null) {
                PacketHandler.writeNBT(buffer, mutation.write());
            }
            buffer.writeInt(snapshotStatus == null ? -1 : snapshotStatus.ordinal());
            PacketHandler.writeNBT(buffer, payload);
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                int packetTypeIndex = buffer.readInt();
                if (packetTypeIndex < 0 || packetTypeIndex >= RecipeConfigPacket.values().length) {
                    return;
                }
                packetType = RecipeConfigPacket.values()[packetTypeIndex];
                configType = QIOAutomationRecipeConfigType.byIndex(buffer.readInt());
                position = buffer.readBoolean() ? Coord4D.read(buffer) : null;
                requestId = buffer.readBoolean() ? PacketHandler.readUUID(buffer) : null;
                expectedRevision = buffer.readLong();
                offset = buffer.readInt();
                pageSize = buffer.readInt();
                query = PacketHandler.readString(buffer);
                mutation = buffer.readBoolean() ?
                      QIOAutomationRecipeProfileMutation.read(PacketHandler.readNBT(buffer)) : null;
                int status = buffer.readInt();
                if (status < -1 || status >= QIOAutomationRecipeConfigClientCache.Status.values().length) {
                    return;
                }
                snapshotStatus = status == -1 ? null :
                      QIOAutomationRecipeConfigClientCache.Status.values()[status];
                payload = PacketHandler.readNBT(buffer);
                if (payload == null) {
                    payload = new NBTTagCompound();
                }
                valid = structurallyValid();
            } catch (Exception ignored) {
                valid = false;
                payload = new NBTTagCompound();
            }
        }

        private boolean structurallyValid() {
            if (packetType == null || configType == null || position == null ||
                offset < 0 || pageSize < 0 || query == null || query.length() >
                QIOAutomationRecipeConfigService.MAX_QUERY_LENGTH || payload == null) {
                return false;
            }
            return switch (packetType) {
                case REQUEST -> requestId != null && mutation == null && snapshotStatus == null &&
                      payload.getKeySet().isEmpty();
                case MUTATE -> requestId != null && mutation != null && snapshotStatus == null &&
                      payload.getKeySet().isEmpty();
                case SNAPSHOT -> mutation == null && snapshotStatus != null &&
                      (snapshotStatus == QIOAutomationRecipeConfigClientCache.Status.UNAVAILABLE) ==
                            payload.getKeySet().isEmpty();
            };
        }

        public boolean isValid() {
            return valid;
        }
    }
}
