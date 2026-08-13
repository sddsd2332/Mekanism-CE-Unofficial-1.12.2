package mekanism.qioprocessing.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.PacketHandler;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkAccess;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchClosureMode;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalContainerState;
import mekanism.qioprocessing.common.inventory.container.QIOProcessingTerminalFrequencyContainer;
import mekanism.qioprocessing.common.inventory.container.QIOWorkbenchConfigurationContainer;
import mekanism.qioprocessing.common.inventory.container.QIOWorkbenchConfigurationContainer.RequestStream;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalSession;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationService;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationService.Context;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchConfigurationSnapshot;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchCopyPreview;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchCopyService;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchClosureService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/** Session-bound workbench browse, mutation, and copy requests. */
public final class PacketQIOWorkbenchConfigurationRequest implements
      IMessageHandler<PacketQIOWorkbenchConfigurationRequest.Message, IMessage> {

    public enum Operation {
        PRODUCTS,
        RECIPES,
        CANDIDATES,
        MUTATE,
        COPY_PREVIEW,
        COPY_CONFIRM,
        CLOSURE_PREVIEW,
        CLOSURE_CONFIRM
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
            !(player.openContainer instanceof QIOWorkbenchConfigurationContainer container) ||
            !(player.openContainer instanceof QIOProcessingTerminalFrequencyContainer frequency) ||
            player.openContainer.windowId != message.windowId ||
            !player.openContainer.canInteractWith(player) ||
            !container.tryRequestWorkbenchConfiguration(player.world.getTotalWorldTime(),
                  requestStream(message.operation))) {
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
              frequency, session, player);
        if (network == null) return;
        PacketQIOWorkbenchConfigurationData.Status status =
              PacketQIOWorkbenchConfigurationData.Status.UNAVAILABLE;
        QIOWorkbenchConfigurationSnapshot snapshot = null;
        QIOWorkbenchCopyPreview copyPreview = null;
        try {
            Context workbench = QIOWorkbenchConfigurationService.open(session, network,
                  session.getAccessRevision(), player.getUniqueID(), player.world);
            switch (message.operation) {
                case PRODUCTS -> {
                    snapshot = QIOWorkbenchConfigurationService.products(workbench,
                          message.offset, message.pageSize, message.query);
                    status = PacketQIOWorkbenchConfigurationData.Status.OK;
                }
                case RECIPES -> {
                    snapshot = QIOWorkbenchConfigurationService.recipes(workbench,
                          message.productKey, message.offset, message.pageSize);
                    status = PacketQIOWorkbenchConfigurationData.Status.OK;
                }
                case CANDIDATES -> {
                    snapshot = QIOWorkbenchConfigurationService.candidates(workbench,
                          message.productKey, message.recipeId, message.recipeSignature,
                          message.ingredientSlot, message.offset, message.pageSize);
                    status = PacketQIOWorkbenchConfigurationData.Status.OK;
                }
                case MUTATE -> status = mutationStatus(
                      QIOWorkbenchConfigurationService.mutate(workbench,
                            message.expectedConfigurationRevision,
                            message.expectedCatalogRevision, message.mutation));
                case COPY_PREVIEW -> {
                    QIOWorkbenchCopyService.PreviewResult result =
                          QIOWorkbenchCopyService.preview(workbench, session,
                                player.getUniqueID(), message.sourceConfigUUID,
                                player.world.getTotalWorldTime());
                    status = copyStatus(result.getStatus());
                    copyPreview = result.getPreview();
                }
                case COPY_CONFIRM -> {
                    QIOWorkbenchCopyService.ConfirmResult result =
                          QIOWorkbenchCopyService.confirm(workbench, session,
                                player.getUniqueID(), message.confirmationNonce,
                                player.world.getTotalWorldTime());
                    status = copyStatus(result.getStatus());
                }
                case CLOSURE_PREVIEW -> {
                    QIOWorkbenchClosureService.preview(workbench, session,
                          player.getUniqueID(), message.expectedConfigurationRevision,
                          message.expectedCatalogRevision, message.mutation,
                          message.closureMode, message.skipCyclicRecipes, player.world,
                          player.world.getTotalWorldTime(), result ->
                                QIOProcessingPacketHandler.INSTANCE.sendTo(
                                      PacketQIOWorkbenchConfigurationData.Message.create(
                                            message.windowId, session, message.requestId,
                                            message.operation,
                                            closureStatus(result.getStatus()), null, null,
                                            result.getPreview()), playerMP));
                    return;
                }
                case CLOSURE_CONFIRM -> {
                    QIOWorkbenchClosureService.ConfirmResult result =
                          QIOWorkbenchClosureService.confirm(workbench, session,
                                player.getUniqueID(), message.confirmationNonce,
                                player.world, player.world.getTotalWorldTime());
                    status = closureStatus(result.getStatus());
                }
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ignored) {
            status = PacketQIOWorkbenchConfigurationData.Status.INVALID_TARGET;
            snapshot = null;
            copyPreview = null;
        }
        QIOProcessingPacketHandler.INSTANCE.sendTo(
              PacketQIOWorkbenchConfigurationData.Message.create(message.windowId, session,
                    message.requestId, message.operation, status, snapshot, copyPreview),
              playerMP);
    }

    private static PacketQIOWorkbenchConfigurationData.Status mutationStatus(
          QIOWorkbenchConfigurationService.MutationStatus status) {
        return switch (status) {
            case APPLIED -> PacketQIOWorkbenchConfigurationData.Status.APPLIED;
            case UNCHANGED -> PacketQIOWorkbenchConfigurationData.Status.UNCHANGED;
            case REVISION_CONFLICT -> PacketQIOWorkbenchConfigurationData.Status.REVISION_CONFLICT;
            case CATALOG_CHANGED -> PacketQIOWorkbenchConfigurationData.Status.CATALOG_CHANGED;
            case INVALID_TARGET -> PacketQIOWorkbenchConfigurationData.Status.INVALID_TARGET;
            case INVALID_PATTERN -> PacketQIOWorkbenchConfigurationData.Status.INVALID_PATTERN;
            case READ_ONLY -> PacketQIOWorkbenchConfigurationData.Status.READ_ONLY;
            case LAST_CANDIDATE -> PacketQIOWorkbenchConfigurationData.Status.LAST_CANDIDATE;
        };
    }

    private static PacketQIOWorkbenchConfigurationData.Status copyStatus(
          QIOWorkbenchCopyService.Status status) {
        return PacketQIOWorkbenchConfigurationData.Status.valueOf(status.name());
    }

    private static PacketQIOWorkbenchConfigurationData.Status closureStatus(
          QIOWorkbenchClosureService.Status status) {
        return PacketQIOWorkbenchConfigurationData.Status.valueOf(status.name());
    }

    private static RequestStream requestStream(Operation operation) {
        return switch (operation) {
            case PRODUCTS -> RequestStream.PRODUCTS;
            case RECIPES -> RequestStream.RECIPES;
            case CANDIDATES -> RequestStream.CANDIDATES;
            case MUTATE, COPY_PREVIEW, COPY_CONFIRM, CLOSURE_PREVIEW, CLOSURE_CONFIRM ->
                  RequestStream.COMMANDS;
        };
    }

    public static final class Message implements IMessage {

        private int windowId;
        private UUID sessionNonce;
        private UUID terminalUUID;
        private long expectedTargetRevision;
        private UUID requestId;
        private Operation operation = Operation.PRODUCTS;
        private int offset;
        private int pageSize;
        private String query = "";
        private String productKey = "";
        private String recipeId = "";
        private String recipeSignature = "";
        private int ingredientSlot = -1;
        private long expectedConfigurationRevision = -1;
        private long expectedCatalogRevision = -1;
        @Nullable private QIOWorkbenchConfigurationMutation mutation;
        @Nullable private UUID sourceConfigUUID;
        @Nullable private UUID confirmationNonce;
        private QIOWorkbenchClosureMode closureMode = QIOWorkbenchClosureMode.NONE;
        private boolean skipCyclicRecipes;
        private boolean valid;

        public Message() {
        }

        private Message(int windowId, QIOProcessingTerminalContainerState state,
              UUID requestId, Operation operation, int offset, int pageSize, String query,
              String productKey, String recipeId, String recipeSignature,
              int ingredientSlot, long expectedConfigurationRevision,
              long expectedCatalogRevision,
              @Nullable QIOWorkbenchConfigurationMutation mutation,
              @Nullable UUID sourceConfigUUID, @Nullable UUID confirmationNonce,
              @Nullable QIOWorkbenchClosureMode closureMode,
              boolean skipCyclicRecipes) {
            this.windowId = windowId;
            if (state != null && state.isValid()) {
                sessionNonce = state.getSessionNonce();
                terminalUUID = state.getTerminalUUID();
                expectedTargetRevision = state.getTargetRevision();
            }
            this.requestId = requestId;
            this.operation = operation;
            this.offset = offset;
            this.pageSize = pageSize;
            this.query = query == null ? "" : query;
            this.productKey = productKey == null ? "" : productKey;
            this.recipeId = recipeId == null ? "" : recipeId;
            this.recipeSignature = recipeSignature == null ? "" : recipeSignature;
            this.ingredientSlot = ingredientSlot;
            this.expectedConfigurationRevision = expectedConfigurationRevision;
            this.expectedCatalogRevision = expectedCatalogRevision;
            this.mutation = mutation;
            this.sourceConfigUUID = sourceConfigUUID;
            this.confirmationNonce = confirmationNonce;
            this.closureMode = closureMode == null ? QIOWorkbenchClosureMode.NONE : closureMode;
            this.skipCyclicRecipes = skipCyclicRecipes;
            valid = shape();
        }

        @Nonnull
        public static Message products(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId, int offset,
              int pageSize, String query) {
            return new Message(windowId, state, requestId, Operation.PRODUCTS, offset,
                  pageSize, query, "", "", "", -1, -1, -1, null, null, null, null,
                  false);
        }

        @Nonnull
        public static Message recipes(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId, String productKey,
              int offset, int pageSize) {
            return new Message(windowId, state, requestId, Operation.RECIPES, offset,
                  pageSize, "", productKey, "", "", -1, -1, -1, null, null, null, null,
                  false);
        }

        @Nonnull
        public static Message candidates(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId, String productKey,
              String recipeId, String recipeSignature, int ingredientSlot, int offset,
              int pageSize) {
            return new Message(windowId, state, requestId, Operation.CANDIDATES, offset,
                  pageSize, "", productKey, recipeId, recipeSignature, ingredientSlot,
                  -1, -1, null, null, null, null, false);
        }

        @Nonnull
        public static Message mutate(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId,
              long expectedConfigurationRevision, long expectedCatalogRevision,
              QIOWorkbenchConfigurationMutation mutation) {
            return new Message(windowId, state, requestId, Operation.MUTATE, 0, 0, "",
                  "", "", "", -1, expectedConfigurationRevision,
                  expectedCatalogRevision, mutation, null, null, null, false);
        }

        @Nonnull
        public static Message copyPreview(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId,
              UUID sourceConfigUUID) {
            return new Message(windowId, state, requestId, Operation.COPY_PREVIEW, 0, 0,
                  "", "", "", "", -1, -1, -1, null, sourceConfigUUID, null, null,
                  false);
        }

        @Nonnull
        public static Message copyConfirm(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId,
              UUID confirmationNonce) {
            return new Message(windowId, state, requestId, Operation.COPY_CONFIRM, 0, 0,
                  "", "", "", "", -1, -1, -1, null, null, confirmationNonce, null,
                  false);
        }

        @Nonnull
        public static Message closurePreview(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId,
              long expectedConfigurationRevision, long expectedCatalogRevision,
              QIOWorkbenchConfigurationMutation mutation,
              QIOWorkbenchClosureMode closureMode, boolean skipCyclicRecipes) {
            return new Message(windowId, state, requestId, Operation.CLOSURE_PREVIEW,
                  0, 0, "", "", "", "", -1, expectedConfigurationRevision,
                  expectedCatalogRevision, mutation, null, null, closureMode,
                  skipCyclicRecipes);
        }

        @Nonnull
        public static Message closureConfirm(int windowId,
              QIOProcessingTerminalContainerState state, UUID requestId,
              UUID confirmationNonce) {
            return new Message(windowId, state, requestId, Operation.CLOSURE_CONFIRM,
                  0, 0, "", "", "", "", -1, -1, -1, null, null,
                  confirmationNonce, QIOWorkbenchClosureMode.NONE, false);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            writeUUID(buffer, sessionNonce);
            writeUUID(buffer, terminalUUID);
            buffer.writeLong(expectedTargetRevision);
            writeUUID(buffer, requestId);
            buffer.writeByte(operation.ordinal());
            buffer.writeInt(offset);
            buffer.writeInt(pageSize);
            PacketHandler.writeString(buffer, query);
            PacketHandler.writeString(buffer, productKey);
            PacketHandler.writeString(buffer, recipeId);
            PacketHandler.writeString(buffer, recipeSignature);
            buffer.writeInt(ingredientSlot);
            buffer.writeLong(expectedConfigurationRevision);
            buffer.writeLong(expectedCatalogRevision);
            buffer.writeBoolean(mutation != null);
            if (mutation != null) PacketHandler.writeNBT(buffer, mutation.write());
            writeOptionalUUID(buffer, sourceConfigUUID);
            writeOptionalUUID(buffer, confirmationNonce);
            buffer.writeByte(closureMode.ordinal());
            buffer.writeBoolean(skipCyclicRecipes);
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
                int operationIndex = buffer.readUnsignedByte();
                if (operationIndex >= Operation.values().length) return;
                operation = Operation.values()[operationIndex];
                offset = buffer.readInt();
                pageSize = buffer.readInt();
                query = PacketHandler.readString(buffer);
                productKey = PacketHandler.readString(buffer);
                recipeId = PacketHandler.readString(buffer);
                recipeSignature = PacketHandler.readString(buffer);
                ingredientSlot = buffer.readInt();
                expectedConfigurationRevision = buffer.readLong();
                expectedCatalogRevision = buffer.readLong();
                NBTTagCompound storedMutation = buffer.readBoolean() ?
                      PacketHandler.readNBT(buffer) : null;
                mutation = storedMutation == null ? null :
                      QIOWorkbenchConfigurationMutation.read(storedMutation);
                sourceConfigUUID = readOptionalUUID(buffer);
                confirmationNonce = readOptionalUUID(buffer);
                int closureModeIndex = buffer.readUnsignedByte();
                if (closureModeIndex >= QIOWorkbenchClosureMode.values().length) return;
                closureMode = QIOWorkbenchClosureMode.values()[closureModeIndex];
                skipCyclicRecipes = buffer.readBoolean();
                valid = shape();
            } catch (QIOProcessingDataException | RuntimeException ignored) {
                valid = false;
            }
        }

        public boolean isValid() { return valid; }
        public boolean isSkipCyclicRecipes() { return skipCyclicRecipes; }

        private boolean shape() {
            if (windowId < 0 || sessionNonce == null || terminalUUID == null ||
                expectedTargetRevision < 0 || requestId == null || operation == null ||
                offset < 0 || pageSize < 0 || query == null ||
                query.length() > QIOWorkbenchConfigurationService.MAX_QUERY_LENGTH ||
                productKey == null || productKey.length() > 64 || recipeId == null ||
                recipeId.length() > 256 || recipeSignature == null ||
                recipeSignature.length() > 64) {
                return false;
            }
            return switch (operation) {
                case PRODUCTS -> page() && productKey.isEmpty() && recipeId.isEmpty() &&
                      recipeSignature.isEmpty() && ingredientSlot == -1 &&
                      noMutationOrCopy();
                case RECIPES -> page() && hash(productKey) && query.isEmpty() &&
                      recipeId.isEmpty() && recipeSignature.isEmpty() && ingredientSlot == -1 &&
                      noMutationOrCopy();
                case CANDIDATES -> page() && hash(productKey) && query.isEmpty() &&
                      !recipeId.isEmpty() && hash(recipeSignature) && ingredientSlot >= 0 &&
                      ingredientSlot < 9 && noMutationOrCopy();
                case MUTATE -> offset == 0 && pageSize == 0 && query.isEmpty() &&
                      productKey.isEmpty() && recipeId.isEmpty() && recipeSignature.isEmpty() &&
                      ingredientSlot == -1 && expectedConfigurationRevision >= 0 &&
                      expectedCatalogRevision >= 0 && mutation != null &&
                      sourceConfigUUID == null && confirmationNonce == null &&
                      closureMode == QIOWorkbenchClosureMode.NONE && !skipCyclicRecipes;
                case COPY_PREVIEW -> emptyAction() && sourceConfigUUID != null &&
                      confirmationNonce == null;
                case COPY_CONFIRM -> emptyAction() && sourceConfigUUID == null &&
                      confirmationNonce != null;
                case CLOSURE_PREVIEW -> offset == 0 && pageSize == 0 && query.isEmpty() &&
                      productKey.isEmpty() && recipeId.isEmpty() &&
                      recipeSignature.isEmpty() && ingredientSlot == -1 &&
                      expectedConfigurationRevision >= 0 && expectedCatalogRevision >= 0 &&
                      mutation != null && (mutation.getAction() ==
                            QIOWorkbenchConfigurationMutation.Action.ENCODE_PATTERN ||
                            mutation.getAction() ==
                                  QIOWorkbenchConfigurationMutation.Action.ENCODE_TARGETS) &&
                      sourceConfigUUID == null && confirmationNonce == null &&
                      closureMode != QIOWorkbenchClosureMode.NONE;
                case CLOSURE_CONFIRM -> emptyAction() && sourceConfigUUID == null &&
                      confirmationNonce != null && closureMode == QIOWorkbenchClosureMode.NONE;
            };
        }

        private boolean page() {
            return pageSize > 0 && pageSize <= QIOWorkbenchConfigurationSnapshot.MAX_PAGE_SIZE &&
                  expectedConfigurationRevision == -1 && expectedCatalogRevision == -1;
        }

        private boolean noMutationOrCopy() {
            return mutation == null && sourceConfigUUID == null && confirmationNonce == null &&
                  closureMode == QIOWorkbenchClosureMode.NONE && !skipCyclicRecipes;
        }

        private boolean emptyAction() {
            return offset == 0 && pageSize == 0 && query.isEmpty() && productKey.isEmpty() &&
                  recipeId.isEmpty() && recipeSignature.isEmpty() && ingredientSlot == -1 &&
                  expectedConfigurationRevision == -1 && expectedCatalogRevision == -1 &&
                  mutation == null && closureMode == QIOWorkbenchClosureMode.NONE &&
                  !skipCyclicRecipes;
        }

        private static boolean hash(String value) {
            return value.length() == 64 && value.chars().allMatch(character ->
                  character >= '0' && character <= '9' ||
                        character >= 'a' && character <= 'f');
        }

        private static void writeUUID(ByteBuf buffer, UUID uuid) {
            buffer.writeLong(uuid.getMostSignificantBits());
            buffer.writeLong(uuid.getLeastSignificantBits());
        }

        private static UUID readUUID(ByteBuf buffer) {
            return new UUID(buffer.readLong(), buffer.readLong());
        }

        private static void writeOptionalUUID(ByteBuf buffer, @Nullable UUID uuid) {
            buffer.writeBoolean(uuid != null);
            if (uuid != null) writeUUID(buffer, uuid);
        }

        @Nullable
        private static UUID readOptionalUUID(ByteBuf buffer) {
            return buffer.readBoolean() ? readUUID(buffer) : null;
        }
    }
}
