package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.MekanismAPI;
import mekanism.api.util.BlockInfo;
import mekanism.common.Mekanism;
import mekanism.common.network.PacketBoxBlacklist.BoxBlacklistMessage;
import net.minecraft.block.Block;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Set;

public class PacketBoxBlacklist implements IMessageHandler<BoxBlacklistMessage, IMessage> {

    private static final int MAX_BLACKLIST_ENTRIES = 65_536;

    @Override
    public IMessage onMessage(BoxBlacklistMessage message, MessageContext context) {
        return null;
    }

    public static class BoxBlacklistMessage implements IMessage {

        @Override
        public void toBytes(ByteBuf dataStream) {
            Set<BlockInfo> boxIgnore = MekanismAPI.getBoxIgnore();
            dataStream.writeInt(boxIgnore.size());
            boxIgnore.forEach(info -> {
                dataStream.writeInt(Block.getIdFromBlock(info.block));
                dataStream.writeInt(info.meta);
            });
            Set<String> boxModIgnore = MekanismAPI.getBoxModIgnore();
            dataStream.writeInt(boxModIgnore.size());
            boxModIgnore.forEach(modid -> ByteBufUtils.writeUTF8String(dataStream, modid));
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            int amount = dataStream.readInt();
            if (amount < 0 || amount > MAX_BLACKLIST_ENTRIES || (long) amount * 8 > dataStream.readableBytes()) {
                throw new IllegalArgumentException("Invalid explicit box blacklist size: " + amount);
            }
            MekanismAPI.getBoxIgnore().clear();
            for (int i = 0; i < amount; i++) {
                MekanismAPI.addBoxBlacklist(Block.getBlockById(dataStream.readInt()), dataStream.readInt());
            }
            int amountMods = dataStream.readInt();
            if (amountMods < 0 || amountMods > MAX_BLACKLIST_ENTRIES) {
                throw new IllegalArgumentException("Invalid mod box blacklist size: " + amountMods);
            }
            for (int i = 0; i < amountMods; i++) {
                MekanismAPI.addBoxBlacklistMod(ByteBufUtils.readUTF8String(dataStream));
            }
            Mekanism.logger.info("Received Cardboard Box blacklist entries from server (" + amount + " explicit blocks, " + amountMods + " mod wildcards)");
        }
    }
}
