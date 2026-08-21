package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.config.QIOAutomationRecipeProfileMutation;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkAccess;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.inventory.container.QIOManagementRecipeContainer;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalFrequencyContainer;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeService;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeService.Context;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeService.MutationStatus;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeService.ProductFilter;
import mekanism.qioprocessing.common.terminal.QIOManagementRecipeSnapshot;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** Reads or mutates a retained machine recipe profile through a management session. */
/**
 * QIO 处理模块中的 PacketQIOManagementRecipeRequest 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class PacketQIOManagementRecipeRequest implements
      IMessageHandler<PacketQIOManagementRecipeRequest.Message, IMessage> {

    public enum Operation {
        PRODUCTS,
        ROUTES,
        MUTATE
    }

    @Override
    public IMessage onMessage(Message message, MessageContext context) {
        if (!message.valid) return null;
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player != null) {
            PacketHandler.handlePacket(() -> handle(message, player), player);
        }
        return null;
    }

    private static void handle(Message message, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP playerMP) ||
            !(player.openContainer instanceof QIOManagementRecipeContainer container) ||
            !(player.openContainer instanceof QIOProcessingTerminalFrequencyContainer frequencyContainer) ||
            player.openContainer.windowId != message.windowId ||
            !player.openContainer.canInteractWith(player) ||
            !container.tryRequestManagementRecipe(player.world.getTotalWorldTime())) {
            return;
        }
        QIOProcessingTerminalSession session = container.getTerminalSession();
        if (session == null || session.getTerminalType() != QIOProcessingTerminalType.MANAGEMENT ||
            session.getFrequencyUUID() == null ||
            session.validate(message.sessionNonce, player.getUniqueID(),
                  session.getTargetKind(), QIOProcessingTerminalType.MANAGEMENT,
                  message.terminalUUID, message.expectedTargetRevision,
                  session.getFrequencyUUID(), session.getAccessRevision()) !=
                  QIOProcessingTerminalSession.Validation.ACCEPTED) {
            return;
        }
        QIOProcessingNetworkData network = QIOProcessingNetworkAccess.getOrCreate(
              frequencyContainer, session, player);
        if (network == null) return;
        PacketQIOManagementRecipeData.Status status =
              PacketQIOManagementRecipeData.Status.UNAVAILABLE;
        QIOManagementRecipeSnapshot snapshot = null;
        try {
            Context recipe = QIOManagementRecipeService.open(session, network,
                  session.getAccessRevision(), player.getUniqueID(), message.deviceUUID);
            if (recipe != null) {
                switch (message.operation) {
                    case PRODUCTS -> {
                        snapshot = QIOManagementRecipeService.products(recipe, message.offset,
                              message.pageSize, message.query, message.productFilter);
                        status = PacketQIOManagementRecipeData.Status.OK;
                    }
                    case ROUTES -> {
                        snapshot = QIOManagementRecipeService.routes(recipe,
                              message.productKey, message.offset, message.pageSize,
                              message.query);
                        status = PacketQIOManagementRecipeData.Status.OK;
                    }
                    case MUTATE -> status = mutationStatus(QIOManagementRecipeService.mutate(
                          recipe, message.expectedProfileRevision, message.mutation));
                }
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            status = PacketQIOManagementRecipeData.Status.INVALID_TARGET;
            snapshot = null;
        }
        QIOProcessingPacketHandler.INSTANCE.sendTo(
              PacketQIOManagementRecipeData.Message.create(message.windowId, session,
                    message.requestId, message.operation, status, snapshot), playerMP);
    }

    private static PacketQIOManagementRecipeData.Status mutationStatus(MutationStatus status) {
        return switch (status) {
            case APPLIED -> PacketQIOManagementRecipeData.Status.APPLIED;
            case UNCHANGED -> PacketQIOManagementRecipeData.Status.UNCHANGED;
            case REVISION_CONFLICT -> PacketQIOManagementRecipeData.Status.REVISION_CONFLICT;
            case INVALID_TARGET -> PacketQIOManagementRecipeData.Status.INVALID_TARGET;
            case READ_ONLY -> PacketQIOManagementRecipeData.Status.READ_ONLY;
        };
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        private UUID requestId;
        private UUID deviceUUID;
        private Operation operation = Operation.PRODUCTS;
        private int offset;
        private int pageSize;
        private String query = "";
        private String productKey = "";
        private ProductFilter productFilter = ProductFilter.ALL;
        private long expectedProfileRevision = -1;
        @Nullable private QIOAutomationRecipeProfileMutation mutation;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, QIOProcessingTerminalContainerState state,
              UUID requestId, UUID deviceUUID, Operation operation, int offset,
              int pageSize, String query, String productKey, ProductFilter productFilter,
              long expectedProfileRevision,
              @Nullable QIOAutomationRecipeProfileMutation mutation) {
            this.windowId = windowId;
            if (state != null && state.isValid()) {
                sessionNonce = state.getSessionNonce();
                terminalUUID = state.getTerminalUUID();
                expectedTargetRevision = state.getTargetRevision();
            }
            this.requestId = requestId;
            this.deviceUUID = deviceUUID;
            this.operation = operation;
            this.offset = offset;
            this.pageSize = pageSize;
            this.query = query == null ? "" : query;
            this.productKey = productKey == null ? "" : productKey;
            this.productFilter = productFilter == null ? ProductFilter.ALL : productFilter;
            this.expectedProfileRevision = expectedProfileRevision;
            this.mutation = mutation;
            valid = shape();
        }

        @Nonnull
        public static Message products(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId, UUID deviceUUID,
              int offset, int pageSize, String query, ProductFilter filter) {
            return new Message(windowId, state, requestId, deviceUUID, Operation.PRODUCTS,
                  offset, pageSize, query, "", filter, -1, null);
        }

        @Nonnull
        public static Message routes(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId, UUID deviceUUID,
              String productKey, int offset, int pageSize, String query) {
            return new Message(windowId, state, requestId, deviceUUID, Operation.ROUTES,
                  offset, pageSize, query, productKey, ProductFilter.ALL, -1, null);
        }

        @Nonnull
        public static Message mutate(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId, UUID deviceUUID,
              long expectedProfileRevision,
              QIOAutomationRecipeProfileMutation mutation) {
            return new Message(windowId, state, requestId, deviceUUID, Operation.MUTATE,
                  0, 0, "", "", ProductFilter.ALL, expectedProfileRevision, mutation);
        }

        static Message products(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, UUID deviceUUID, int offset, int pageSize, String query,
              ProductFilter filter) {
            return create(windowId, session, requestId, deviceUUID, Operation.PRODUCTS,
                  offset, pageSize, query, "", filter, -1, null);
        }

        static Message routes(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, UUID deviceUUID, String productKey, int offset,
              int pageSize, String query) {
            return create(windowId, session, requestId, deviceUUID, Operation.ROUTES,
                  offset, pageSize, query, productKey, ProductFilter.ALL, -1, null);
        }

        static Message mutate(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, UUID deviceUUID, long expectedProfileRevision,
              QIOAutomationRecipeProfileMutation mutation) {
            return create(windowId, session, requestId, deviceUUID, Operation.MUTATE,
                  0, 0, "", "", ProductFilter.ALL, expectedProfileRevision, mutation);
        }

        private static Message create(int windowId, QIOProcessingTerminalSession session,
              UUID requestId, UUID deviceUUID, Operation operation, int offset,
              int pageSize, String query, String productKey, ProductFilter productFilter,
              long expectedProfileRevision,
              @Nullable QIOAutomationRecipeProfileMutation mutation) {
            Message message = new Message();
            if (session != null) {
                message.windowId = windowId;
                message.sessionNonce = session.getSessionNonce();
                message.terminalUUID = session.getTerminalUUID();
                message.expectedTargetRevision = session.getTargetRevision();
                message.requestId = requestId;
                message.deviceUUID = deviceUUID;
                message.operation = operation;
                message.offset = offset;
                message.pageSize = pageSize;
                message.query = query == null ? "" : query;
                message.productKey = productKey == null ? "" : productKey;
                message.productFilter = productFilter == null ? ProductFilter.ALL :
                      productFilter;
                message.expectedProfileRevision = expectedProfileRevision;
                message.mutation = mutation;
                message.valid = message.shape();
            }
            return message;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(expectedTargetRevision);
            writeUUID(buffer, requestId);
            writeUUID(buffer, deviceUUID);
            buffer.writeByte(operation.ordinal());
            buffer.writeInt(offset);
            buffer.writeInt(pageSize);
            PacketHandler.writeString(buffer, query);
            PacketHandler.writeString(buffer, productKey);
            buffer.writeByte(productFilter.ordinal());
            buffer.writeLong(expectedProfileRevision);
            buffer.writeBoolean(mutation != null);
            if (mutation != null) PacketHandler.writeNBT(buffer, mutation.write());
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            valid = false;
            try {
                windowId = buffer.readInt();
                sessionNonce = readUUID(buffer);
                terminalUUID = readUUID(buffer);
                expectedTargetRevision = buffer.readLong();
                requestId = readUUID(buffer);
                deviceUUID = readUUID(buffer);
                int operationIndex = buffer.readUnsignedByte();
                if (operationIndex >= Operation.values().length) return;
                operation = Operation.values()[operationIndex];
                offset = buffer.readInt();
                pageSize = buffer.readInt();
                query = PacketHandler.readString(buffer);
                productKey = PacketHandler.readString(buffer);
                int filterIndex = buffer.readUnsignedByte();
                if (filterIndex >= ProductFilter.values().length) return;
                productFilter = ProductFilter.values()[filterIndex];
                expectedProfileRevision = buffer.readLong();
                NBTTagCompound storedMutation = buffer.readBoolean() ?
                      PacketHandler.readNBT(buffer) : null;
                mutation = storedMutation == null ? null :
                      QIOAutomationRecipeProfileMutation.read(storedMutation);
                valid = shape();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                valid = false;
            }
        }

        public boolean isValid() { return valid; }

        private boolean shape() {
            if (windowId < 0 || sessionNonce == null || terminalUUID == null ||
                expectedTargetRevision < 0 || requestId == null || deviceUUID == null ||
                operation == null || offset < 0 || pageSize < 0 || query == null ||
                query.length() > QIOManagementRecipeService.MAX_QUERY_LENGTH ||
                productKey == null || productKey.length() > 8_192 || productFilter == null) {
                return false;
            }
            return switch (operation) {
                case PRODUCTS -> pageSize > 0 &&
                      pageSize <= QIOManagementRecipeSnapshot.MAX_PAGE_SIZE &&
                      productKey.isEmpty() && mutation == null && expectedProfileRevision == -1;
                case ROUTES -> pageSize > 0 &&
                      pageSize <= QIOManagementRecipeSnapshot.MAX_PAGE_SIZE &&
                      !productKey.isEmpty() && mutation == null &&
                      expectedProfileRevision == -1;
                case MUTATE -> offset == 0 && pageSize == 0 && query.isEmpty() &&
                      productKey.isEmpty() && mutation != null &&
                      expectedProfileRevision >= 0;
            };
        }

        private static void writeUUID(ByteBuf buffer, UUID uuid) {
            buffer.writeLong(uuid.getMostSignificantBits());
            buffer.writeLong(uuid.getLeastSignificantBits());
        }

        private static UUID readUUID(ByteBuf buffer) {
            return new UUID(buffer.readLong(), buffer.readLong());
        }
    }
}
