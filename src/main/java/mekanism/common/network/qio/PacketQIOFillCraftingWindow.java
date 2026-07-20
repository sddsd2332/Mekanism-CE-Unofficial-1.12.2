package mekanism.common.network.qio;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectArrayMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.content.qio.QIOCraftingTransferHelper.SingularHashedItemSource;
import mekanism.common.content.qio.QIOServerCraftingTransferHandler;
import mekanism.common.inventory.container.IQIOItemViewerContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** JEI-to-server crafting transfer request. */
public class PacketQIOFillCraftingWindow implements IMessageHandler<PacketQIOFillCraftingWindow.Message, IMessage> {

    private static final int MAX_TARGETS = 9;
    private static final int MAX_SOURCES_PER_TARGET = 9;
    private static final int MAX_AMOUNT = Integer.MAX_VALUE;

    @Override
    @Nullable
    public IMessage onMessage(Message message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || player.world.isRemote || message.invalid) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (!(player.openContainer instanceof IQIOItemViewerContainer) || player.openContainer.windowId != message.windowId) {
                return;
            }
            IQIOItemViewerContainer container = (IQIOItemViewerContainer) player.openContainer;
            if (!((net.minecraft.inventory.Container) player.openContainer).canInteractWith(player)) {
                return;
            }
            byte selected = container.getSelectedCraftingGrid(player.getUniqueID());
            if (selected < 0 || selected >= 3) {
                return;
            }
            IRecipe recipe = null;
            if (message.recipeId != null) {
                recipe = ForgeRegistries.RECIPES.getValue(message.recipeId);
                if (recipe == null) {
                    Mekanism.logger.warn("Ignoring QIO crafting transfer with unknown recipe {}", message.recipeId);
                    return;
                }
            }
            QIOServerCraftingTransferHandler.tryTransfer(container, selected, player, recipe, message.sources);
        }, player);
        return null;
    }

    public static class Message implements IMessage {
        private int windowId;
        @Nullable
        private ResourceLocation recipeId;
        private boolean maxTransfer;
        private Byte2ObjectMap<List<SingularHashedItemSource>> sources = new Byte2ObjectArrayMap<>();
        private boolean invalid;

        public Message() {
        }

        public Message(int windowId, @Nullable ResourceLocation recipeId, boolean maxTransfer,
              Byte2ObjectMap<List<SingularHashedItemSource>> sources) {
            this.windowId = windowId;
            this.recipeId = recipeId;
            this.maxTransfer = maxTransfer;
            this.sources = sources == null ? new Byte2ObjectArrayMap<>() : sources;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeBoolean(recipeId != null);
            if (recipeId != null) {
                PacketHandler.writeString(buffer, recipeId.toString());
            }
            buffer.writeBoolean(maxTransfer);
            buffer.writeByte(Math.min(MAX_TARGETS, sources.size()));
            int written = 0;
            for (Byte2ObjectMap.Entry<List<SingularHashedItemSource>> entry : sources.byte2ObjectEntrySet()) {
                if (written++ >= MAX_TARGETS) {
                    break;
                }
                buffer.writeByte(entry.getByteKey());
                List<SingularHashedItemSource> list = entry.getValue() == null ? java.util.Collections.emptyList() : entry.getValue();
                int count = maxTransfer ? Math.min(MAX_SOURCES_PER_TARGET, list.size()) : Math.min(1, list.size());
                if (maxTransfer) {
                    buffer.writeByte(count);
                }
                for (int i = 0; i < count; i++) {
                    SingularHashedItemSource source = list.get(i);
                    buffer.writeByte(source.getSlot());
                    if (maxTransfer) {
                        buffer.writeInt(Math.min(MAX_AMOUNT, Math.max(0, source.getUsed())));
                    }
                    if (source.getSlot() == -1 && source.getQioSource() != null) {
                        PacketHandler.writeUUID(buffer, source.getQioSource());
                    }
                }
            }
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            try {
                windowId = buffer.readInt();
                recipeId = buffer.readBoolean() ? new ResourceLocation(PacketHandler.readString(buffer)) : null;
                maxTransfer = buffer.readBoolean();
                int targetCount = buffer.readUnsignedByte();
                if (targetCount > MAX_TARGETS) {
                    invalid = true;
                    return;
                }
                sources = new Byte2ObjectArrayMap<>(targetCount);
                for (int targetIndex = 0; targetIndex < targetCount; targetIndex++) {
                    byte target = buffer.readByte();
                    if (target < 0 || target >= 9 || sources.containsKey(target)) {
                        invalid = true;
                        return;
                    }
                    int sourceCount = maxTransfer ? buffer.readUnsignedByte() : 1;
                    if (sourceCount <= 0 || sourceCount > MAX_SOURCES_PER_TARGET) {
                        invalid = true;
                        return;
                    }
                    List<SingularHashedItemSource> list = new ArrayList<>(sourceCount);
                    for (int i = 0; i < sourceCount; i++) {
                        byte sourceSlot = buffer.readByte();
                        int amount = maxTransfer ? buffer.readInt() : 1;
                        if (amount <= 0 || amount > MAX_AMOUNT || sourceSlot < -1 || sourceSlot >= 45) {
                            invalid = true;
                            return;
                        }
                        if (sourceSlot == -1) {
                            UUID uuid = PacketHandler.readUUID(buffer);
                            list.add(new SingularHashedItemSource(uuid, amount));
                        } else {
                            list.add(new SingularHashedItemSource(sourceSlot, amount));
                        }
                    }
                    sources.put(target, list);
                }
            } catch (RuntimeException ex) {
                invalid = true;
            }
        }
    }

    /** Source-compatible name used by the original 1.12 QIO port. */
    public static class QIOFillCraftingWindowMessage extends Message {
        public QIOFillCraftingWindowMessage() {
            super();
        }

        public QIOFillCraftingWindowMessage(int windowId, @Nullable ResourceLocation recipeId, boolean maxTransfer,
              Byte2ObjectMap<List<SingularHashedItemSource>> sources) {
            super(windowId, recipeId, maxTransfer, sources);
        }
    }
}
