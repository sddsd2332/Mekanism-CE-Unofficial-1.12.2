package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.CommonProxy;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.network.PacketDataRequest.DataRequestMessage;
import mekanism.common.network.PacketLogisticalSorterGui.LogisticalSorterGuiMessage;
import mekanism.common.tile.TileEntityLogisticalSorter;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PacketLogisticalSorterGui implements IMessageHandler<LogisticalSorterGuiMessage, IMessage> {

    @Override
    public IMessage onMessage(LogisticalSorterGuiMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (!player.world.isRemote) {
                World worldServer = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(message.coord4D.dimensionId);
                if (worldServer != null && message.coord4D.getTileEntity(worldServer) instanceof TileEntityLogisticalSorter tile
                        && PacketHandler.canAccessTile(player, tile)) {
                    LogisticalSorterGuiMessage.openServerGui(message.packetType, message.guiType, worldServer, (EntityPlayerMP) player, message.coord4D, message.index);
                }
            } else if (message.coord4D.getTileEntity(player.world) instanceof TileEntityLogisticalSorter) {
                try {
                    GuiScreen gui = null;
                    if (message.packetType == SorterGuiPacket.CLIENT) {
                        gui = LogisticalSorterGuiMessage.getGui(message.packetType, message.guiType, player, player.world,
                                message.coord4D.getPos(), -1);
                    } else if (message.packetType == SorterGuiPacket.CLIENT_INDEX) {
                        gui = LogisticalSorterGuiMessage.getGui(message.packetType, message.guiType, player, player.world,
                                message.coord4D.getPos(), message.index);
                    }
                    if (gui != null) {
                        FMLCommonHandler.instance().showGuiScreen(gui);
                        if (player.openContainer != null) {
                            player.openContainer.windowId = message.windowId;
                        }
                        Mekanism.packetHandler.sendToServer(new DataRequestMessage(message.coord4D));
                    }
                } catch (Exception e) {
                    Mekanism.logger.error("FIXME: Packet handling error", e);
                }
            }
        }, player);
        return null;
    }

    public enum SorterGuiPacket {
        SERVER,
        CLIENT,
        SERVER_INDEX,
        CLIENT_INDEX
    }

    public static class LogisticalSorterGuiMessage implements IMessage {

        public Coord4D coord4D;

        public SorterGuiPacket packetType;

        public int guiType;

        public int windowId = -1;

        public int index = -1;

        public LogisticalSorterGuiMessage() {
        }

        public LogisticalSorterGuiMessage(SorterGuiPacket type, Coord4D coord, int guiId, int extra, int extra2) {
            packetType = type;

            coord4D = coord;
            guiType = guiId;

            if (packetType == SorterGuiPacket.CLIENT) {
                windowId = extra;
            } else if (packetType == SorterGuiPacket.SERVER_INDEX) {
                index = extra;
            } else if (packetType == SorterGuiPacket.CLIENT_INDEX) {
                windowId = extra;
                index = extra2;
            }
        }

        public static void openServerGui(SorterGuiPacket t, int guiType, World world, EntityPlayerMP playerMP, Coord4D obj, int i) {
            Container container = null;
            TileEntityContainerBlock tile = (TileEntityContainerBlock) obj.getTileEntity(world);
            if (!CommonProxy.canOpenServerGui(playerMP, tile)) {
                return;
            }

            playerMP.closeContainer();

            if (guiType == 0) {
                container = new ContainerNull(playerMP, tile);
            }
            if (container == null) {
                return;
            }
            playerMP.getNextWindowId();
            int window = playerMP.currentWindowId;
            if (t == SorterGuiPacket.SERVER) {
                Mekanism.packetHandler.sendTo(new LogisticalSorterGuiMessage(SorterGuiPacket.CLIENT, obj, guiType, window, 0), playerMP);
            } else if (t == SorterGuiPacket.SERVER_INDEX) {
                Mekanism.packetHandler.sendTo(new LogisticalSorterGuiMessage(SorterGuiPacket.CLIENT_INDEX, obj, guiType, window, i), playerMP);
            }
            playerMP.openContainer = container;
            playerMP.openContainer.windowId = window;
            playerMP.openContainer.addListener(playerMP);
        }

        @SideOnly(Side.CLIENT)
        public static GuiScreen getGui(SorterGuiPacket packetType, int type, EntityPlayer player, World world, BlockPos pos, int index) {
            if (type == 0) {
                return new mekanism.client.gui.GuiLogisticalSorter(player.inventory, (TileEntityLogisticalSorter) world.getTileEntity(pos));
            }
            return null;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeInt(packetType.ordinal());

            coord4D.write(dataStream);

            dataStream.writeInt(guiType);

            if (packetType == SorterGuiPacket.CLIENT || packetType == SorterGuiPacket.CLIENT_INDEX) {
                dataStream.writeInt(windowId);
            }
            if (packetType == SorterGuiPacket.SERVER_INDEX || packetType == SorterGuiPacket.CLIENT_INDEX) {
                dataStream.writeInt(index);
            }
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            packetType = MekanismUtils.getByIndex(SorterGuiPacket.values(), dataStream.readInt(), SorterGuiPacket.SERVER);

            coord4D = Coord4D.read(dataStream);

            guiType = dataStream.readInt();

            if (packetType == SorterGuiPacket.CLIENT || packetType == SorterGuiPacket.CLIENT_INDEX) {
                windowId = dataStream.readInt();
            }
            if (packetType == SorterGuiPacket.SERVER_INDEX || packetType == SorterGuiPacket.CLIENT_INDEX) {
                index = dataStream.readInt();
            }
        }
    }
}
