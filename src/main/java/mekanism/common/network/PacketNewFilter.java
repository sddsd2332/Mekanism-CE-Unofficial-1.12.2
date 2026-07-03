package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.TileNetworkList;
import mekanism.common.PacketHandler;
import mekanism.common.content.filter.IFilter;
import mekanism.common.content.miner.MinerFilter;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.network.PacketNewFilter.NewFilterMessage;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.machine.TileEntityOredictionificator.OredictionificatorFilter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketNewFilter implements IMessageHandler<NewFilterMessage, IMessage> {

    @Override
    public IMessage onMessage(NewFilterMessage message, MessageContext context) {
        EntityPlayerMP player = context.getServerHandler().player;
        if (player == null) {
            return null;
        }
        WorldServer worldServer = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(message.coord4D.dimensionId);
        if (worldServer == null) {
            return null;
        }

        worldServer.addScheduledTask(() -> {
            TileEntity tile = message.coord4D.getTileEntity(worldServer);
            IFilter filter = message.getFilter();
            if (tile instanceof ITileFilterHolder<?> filterHolder && PacketHandler.canAccessTile(player, tile) && filter != null) {
                filterHolder.getFilterManager().tryAddFilter(filter, true);
            }
        });
        return null;
    }

    public static class NewFilterMessage implements IMessage {

        public Coord4D coord4D;

        public TransporterFilter tFilter;

        public MinerFilter mFilter;

        public OredictionificatorFilter oFilter;

        public byte type = -1;

        public NewFilterMessage() {
        }

        public NewFilterMessage(Coord4D coord, Object filter) {
            coord4D = coord;

            if (filter instanceof TransporterFilter transporterFilter) {
                tFilter = transporterFilter;
                type = 0;
            } else if (filter instanceof MinerFilter minerFilter) {
                mFilter = minerFilter;
                type = 1;
            } else if (filter instanceof OredictionificatorFilter oredictionificatorFilter) {
                oFilter = oredictionificatorFilter;
                type = 2;
            }
        }

        public IFilter getFilter() {
            if (type == 0) {
                return tFilter;
            } else if (type == 1) {
                return mFilter;
            } else if (type == 2) {
                return oFilter;
            }
            return null;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            coord4D.write(dataStream);
            dataStream.writeByte(type);
            TileNetworkList data = new TileNetworkList();
            if (type == 0) {
                tFilter.write(data);
            } else if (type == 1) {
                mFilter.write(data);
            } else if (type == 2) {
                oFilter.write(data);
            }
            PacketHandler.encode(data.toArray(), dataStream);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            coord4D = Coord4D.read(dataStream);
            type = dataStream.readByte();
            if (type == 0) {
                tFilter = TransporterFilter.readFromPacket(dataStream);
            } else if (type == 1) {
                mFilter = MinerFilter.readFromPacket(dataStream);
            } else if (type == 2) {
                oFilter = OredictionificatorFilter.readFromPacket(dataStream);
            }
        }
    }
}
