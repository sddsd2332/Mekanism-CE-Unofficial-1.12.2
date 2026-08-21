package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.base.IGuiProvider;
import mekanism.common.network.PacketDataRequest.DataRequestMessage;
import mekanism.common.network.PacketSimpleGui.SimpleGuiMessage;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

public class PacketSimpleGui implements IMessageHandler<SimpleGuiMessage, IMessage> {

    public static List<IGuiProvider> handlers = new ArrayList<>();

    public static boolean hasGuiHandler(int handler) {
        return handler >= 0 && handler < handlers.size() && handlers.get(handler) != null;
    }

    @Override
    public IMessage onMessage(SimpleGuiMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (!player.world.isRemote) {
                World worldServer = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(message.coord4D.dimensionId);
                if (worldServer != null && message.coord4D.getTileEntity(worldServer) instanceof TileEntityBasicBlock tile && PacketHandler.canAccessTile(player, tile)) {
                    if (message.guiId == -1 || !hasGuiHandler(message.guiHandler)) {
                        return;
                    }
                    IGuiProvider guiProvider = handlers.get(message.guiHandler);
                    if (!guiProvider.isValidServerGui(message.guiId, tile)) {
                        return;
                    }
                    SimpleGuiMessage.openServerGui(message.guiHandler, message.guiId, (EntityPlayerMP) player, worldServer, message.coord4D);
                }
            } else {
                GuiScreen gui = SimpleGuiMessage.getGui(message.guiHandler, message.guiId, player, player.world, message.coord4D);
                if (gui != null) {
                    FMLCommonHandler.instance().showGuiScreen(gui);
                    if (player.openContainer != null) {
                        player.openContainer.windowId = message.windowId;
                    }
                    //Force-refresh the tile data when switching GUIs so button states aren't stale.
                    Mekanism.packetHandler.sendToServer(new DataRequestMessage(message.coord4D));
                }
            }
        }, player);
        return null;
    }

    public static class SimpleGuiMessage implements IMessage {

        public Coord4D coord4D;

        public int guiHandler;

        public int guiId;

        public int windowId;

        public SimpleGuiMessage() {
        }

        public SimpleGuiMessage(Coord4D coord, int handler, int gui) {
            coord4D = coord;
            guiHandler = handler;
            guiId = gui;
        }

        public SimpleGuiMessage(Coord4D coord, int handler, int gui, int id) {
            this(coord, handler, gui);
            windowId = id;
        }

        public static void openServerGui(int handler, int id, EntityPlayerMP playerMP, World world, Coord4D obj) {
            if (!hasGuiHandler(handler)) {
                return;
            }
            playerMP.closeContainer();
            Container container = handlers.get(handler).getServerGui(id, playerMP, world, obj.getPos());
            if (container == null) {
                return;
            }
            playerMP.getNextWindowId();
            int window = playerMP.currentWindowId;
            Mekanism.packetHandler.sendTo(new SimpleGuiMessage(obj, handler, id, window), playerMP);
            playerMP.openContainer = container;
            playerMP.openContainer.windowId = window;
            playerMP.openContainer.addListener(playerMP);
        }

        @SideOnly(Side.CLIENT)
        public static GuiScreen getGui(int handler, int id, EntityPlayer player, World world, Coord4D obj) {
            if (!hasGuiHandler(handler)) {
                return null;
            }
            return (GuiScreen) handlers.get(handler).getClientGui(id, player, world, obj.getPos());
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            coord4D.write(dataStream);
            dataStream.writeInt(guiHandler);
            dataStream.writeInt(guiId);
            dataStream.writeInt(windowId);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            coord4D = Coord4D.read(dataStream);
            guiHandler = dataStream.readInt();
            guiId = dataStream.readInt();
            windowId = dataStream.readInt();
        }
    }
}
