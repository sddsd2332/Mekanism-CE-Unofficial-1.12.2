package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.TileNetworkList;
import mekanism.common.PacketHandler;
import mekanism.common.content.filter.IFilter;
import mekanism.common.content.miner.MinerFilter;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.network.PacketEditFilter.EditFilterMessage;
import mekanism.common.tile.interfaces.ITileFilterHolder;
import mekanism.common.tile.machine.TileEntityOredictionificator.OredictionificatorFilter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketEditFilter implements IMessageHandler<EditFilterMessage, IMessage> {

    @Override
    public IMessage onMessage(EditFilterMessage message, MessageContext context) {
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
            IFilter edited = message.getEdited();
            if (tile instanceof ITileFilterHolder<?> filterHolder && PacketHandler.canAccessTile(player, tile) && filter != null && (message.delete || edited != null)) {
                filterHolder.getFilterManager().tryEditFilter(filter, message.delete ? null : edited);
            }
        });
        return null;
    }

    public static class EditFilterMessage implements IMessage {

        public Coord4D coord4D;

        public TransporterFilter tFilter;
        public TransporterFilter tEdited;

        public MinerFilter mFilter;
        public MinerFilter mEdited;

        public OredictionificatorFilter oFilter;
        public OredictionificatorFilter oEdited;

        public byte type = -1;

        public boolean delete;

        public EditFilterMessage() {
        }

        public EditFilterMessage(Coord4D coord, boolean deletion, Object filter, Object edited) {
            coord4D = coord;
            delete = deletion;

            if (filter instanceof TransporterFilter transporterFilter) {
                tFilter = transporterFilter;
                if (!delete) {
                    tEdited = (TransporterFilter) edited;
                }
                type = 0;
            } else if (filter instanceof MinerFilter minerFilter) {
                mFilter = minerFilter;
                if (!delete) {
                    mEdited = (MinerFilter) edited;
                }
                type = 1;
            } else if (filter instanceof OredictionificatorFilter oredictionificatorFilter) {
                oFilter = oredictionificatorFilter;
                if (!delete) {
                    oEdited = (OredictionificatorFilter) edited;
                }
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

        public IFilter getEdited() {
            if (type == 0) {
                return tEdited;
            } else if (type == 1) {
                return mEdited;
            } else if (type == 2) {
                return oEdited;
            }
            return null;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            coord4D.write(dataStream);

            dataStream.writeByte(type);

            dataStream.writeBoolean(delete);

            TileNetworkList data = new TileNetworkList();

            if (type == 0) {
                tFilter.write(data);
                if (!delete) {
                    tEdited.write(data);
                }
            } else if (type == 1) {
                mFilter.write(data);
                if (!delete) {
                    mEdited.write(data);
                }
            } else if (type == 2) {
                oFilter.write(data);
                if (!delete) {
                    oEdited.write(data);
                }
            }
            PacketHandler.encode(data.toArray(), dataStream);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            coord4D = Coord4D.read(dataStream);

            type = dataStream.readByte();
            delete = dataStream.readBoolean();
            if (type == 0) {
                tFilter = TransporterFilter.readFromPacket(dataStream);
                if (!delete) {
                    tEdited = TransporterFilter.readFromPacket(dataStream);
                }
            } else if (type == 1) {
                mFilter = MinerFilter.readFromPacket(dataStream);
                if (!delete) {
                    mEdited = MinerFilter.readFromPacket(dataStream);
                }
            } else if (type == 2) {
                oFilter = OredictionificatorFilter.readFromPacket(dataStream);
                if (!delete) {
                    oEdited = OredictionificatorFilter.readFromPacket(dataStream);
                }
            }
        }
    }
}
