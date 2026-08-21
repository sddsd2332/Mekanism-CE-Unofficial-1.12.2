package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.network.PacketOpenGui.OpenGui;
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

import static mekanism.common.network.PacketSimpleGui.handlers;
import static mekanism.common.network.PacketSimpleGui.hasGuiHandler;

public class PacketOpenGui implements IMessageHandler<OpenGui, IMessage> {

    private static final int CORE_GUI_HANDLER = 0;
    private static final int MODULE_TWEAKER_GUI = 77;

    @Override
    public IMessage onMessage(OpenGui message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (!player.world.isRemote) {
                // The only client-initiated OpenGui request is the item-independent module tweaker.
                // Block and entity GUIs are opened by the server through Forge's GUI handler.
                if (message.guiHandler == CORE_GUI_HANDLER && message.guiId == MODULE_TWEAKER_GUI) {
                    OpenGui.openServerGui(message.guiHandler, message.guiId, (EntityPlayerMP) player, player.world,
                          Coord4D.get(player));
                }
            } else {
                GuiScreen gui = OpenGui.getGui(message.guiHandler, message.guiId, player, player.world, message.coord4D);
                if (gui != null) {
                    FMLCommonHandler.instance().showGuiScreen(gui);
                    if (player.openContainer != null) {
                        player.openContainer.windowId = message.windowId;
                    }
                }
            }
        }, player);
        return null;
    }

    public static class OpenGui implements IMessage {

        public Coord4D coord4D;

        public int guiHandler;

        public int guiId;

        public int windowId;


        public OpenGui() {
        }

        public OpenGui(Coord4D coord, int handler, int gui) {
            coord4D = coord;
            guiHandler = handler;
            guiId = gui;
        }


        public OpenGui(Coord4D coord, int handler, int gui, int id) {
            this(coord, handler, gui);
            windowId = id;
        }


        public static void openServerGui(int handler, int id, EntityPlayerMP playerMP, World world, Coord4D obj) {
            if (!hasGuiHandler(handler)) {
                return;
            }
            playerMP.closeContainer();
            Container container = handlers.get(handler).getServerGui(id, playerMP, world, playerMP.getPosition());
            if (container == null) {
                return;
            }
            playerMP.getNextWindowId();
            int window = playerMP.currentWindowId;
            Mekanism.packetHandler.sendTo(new OpenGui(obj, handler, id, window), playerMP);
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
        public void fromBytes(ByteBuf dataStream) {
            coord4D = Coord4D.read(dataStream);
            guiHandler = dataStream.readInt();
            guiId = dataStream.readInt();
            windowId = dataStream.readInt();
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            coord4D.write(dataStream);
            dataStream.writeInt(guiHandler);
            dataStream.writeInt(guiId);
            dataStream.writeInt(windowId);
        }
    }
}
