package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ITileNetwork;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.network.PacketConfigurationUpdate.ConfigurationUpdateMessage;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TransporterUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketConfigurationUpdate implements IMessageHandler<ConfigurationUpdateMessage, IMessage> {

    @Override
    public IMessage onMessage(ConfigurationUpdateMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);

        PacketHandler.handlePacket(() -> {
            TileEntity tile = message.coord4D.getTileEntity(player.world);
            if (tile instanceof ISideConfiguration config) {
                ITileNetwork network = CapabilityUtils.getCapability(tile, Capabilities.TILE_NETWORK_CAPABILITY, null);

                if (message.packetType == ConfigurationPacket.EJECT) {
                    config.getConfig().setEjecting(message.transmission, !config.getConfig().isEjecting(message.transmission));
                } else if (message.packetType == ConfigurationPacket.SIDE_DATA) {
                    if (message.clickType == 0) {
                        MekanismUtils.incrementOutput(config, message.transmission, message.configIndex);
                    } else if (message.clickType == 1) {
                        MekanismUtils.decrementOutput(config, message.transmission, message.configIndex);
                    } else if (message.clickType == 2) {
                        if (config.getConfig().getConfig(message.transmission).get(message.configIndex) == -1) {
                            return;
                        } else {
                            config.getConfig().getConfig(message.transmission).set(message.configIndex, (byte) 0);
                        }
                    }

                    tile.markDirty();
                    Mekanism.packetHandler.sendToAllTracking(new TileEntityMessage(message.coord4D, network.getNetworkedData()), message.coord4D);
                    //Notify the neighbor on that side our state changed
                    MekanismUtils.notifyNeighborOfChange(tile.getWorld(), message.configIndex, tile.getPos());
                } else if (message.packetType == ConfigurationPacket.EJECT_COLOR) {
                    TileComponentEjector ejector = config.getEjector();
                    if (message.clickType == 0) {
                        ejector.setOutputColor(TransporterUtils.increment(ejector.getOutputColor()));
                    } else if (message.clickType == 1) {
                        ejector.setOutputColor(TransporterUtils.decrement(ejector.getOutputColor()));
                    } else if (message.clickType == 2) {
                        ejector.setOutputColor(null);
                    }
                } else if (message.packetType == ConfigurationPacket.INPUT_COLOR) {
                    EnumFacing side = EnumFacing.byIndex(message.inputSide);
                    TileComponentEjector ejector = config.getEjector();
                    if (message.clickType == 0) {
                        ejector.setInputColor(side, TransporterUtils.increment(ejector.getInputColor(side)));
                    } else if (message.clickType == 1) {
                        ejector.setInputColor(side, TransporterUtils.decrement(ejector.getInputColor(side)));
                    } else if (message.clickType == 2) {
                        ejector.setInputColor(side, null);
                    }
                } else if (message.packetType == ConfigurationPacket.STRICT_INPUT) {
                    config.getEjector().setStrictInput(!config.getEjector().hasStrictInput());
                }
                for (EntityPlayer p : ((TileEntityBasicBlock) config).playersUsing) {
                    Mekanism.packetHandler.sendTo(new TileEntityMessage(message.coord4D, network.getNetworkedData()), (EntityPlayerMP) p);
                }
            }
        }, player);
        return null;
    }

    public enum ConfigurationPacket {
        EJECT,
        SIDE_DATA,
        EJECT_COLOR,
        INPUT_COLOR,
        STRICT_INPUT
    }

    public static class ConfigurationUpdateMessage implements IMessage {

        public Coord4D coord4D;

        public EnumFacing configIndex;

        public int inputSide;

        public TransmissionType transmission;

        public int clickType;

        public ConfigurationPacket packetType;

        public ConfigurationUpdateMessage() {
        }

        public ConfigurationUpdateMessage(ConfigurationPacket type, Coord4D coord, int click, int extra, TransmissionType trans) {
            packetType = type;
            coord4D = coord;

            if (packetType == ConfigurationPacket.EJECT) {
                transmission = trans;
            }
            if (packetType == ConfigurationPacket.EJECT_COLOR) {
                clickType = click;
            }
            if (packetType == ConfigurationPacket.SIDE_DATA) {
                clickType = click;
                configIndex = EnumFacing.byIndex(extra);
                transmission = trans;
            }
            if (packetType == ConfigurationPacket.INPUT_COLOR) {
                clickType = click;
                inputSide = extra;
            }
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeInt(packetType.ordinal());
            coord4D.write(dataStream);

            if (packetType != ConfigurationPacket.EJECT && packetType != ConfigurationPacket.STRICT_INPUT) {
                dataStream.writeInt(clickType);
            }
            if (packetType == ConfigurationPacket.EJECT) {
                dataStream.writeInt(transmission.ordinal());
            }
            if (packetType == ConfigurationPacket.SIDE_DATA) {
                dataStream.writeInt(configIndex.ordinal());
                dataStream.writeInt(transmission.ordinal());
            }
            if (packetType == ConfigurationPacket.INPUT_COLOR) {
                dataStream.writeInt(inputSide);
            }
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            packetType = ConfigurationPacket.values()[dataStream.readInt()];
            coord4D = Coord4D.read(dataStream);

            if (packetType == ConfigurationPacket.EJECT) {
                transmission = TransmissionType.values()[dataStream.readInt()];
            } else if (packetType == ConfigurationPacket.SIDE_DATA) {
                clickType = dataStream.readInt();
                configIndex = EnumFacing.byIndex(dataStream.readInt());
                transmission = TransmissionType.values()[dataStream.readInt()];
            } else if (packetType == ConfigurationPacket.EJECT_COLOR) {
                clickType = dataStream.readInt();
            } else if (packetType == ConfigurationPacket.INPUT_COLOR) {
                clickType = dataStream.readInt();
                inputSide = dataStream.readInt();
            }
        }
    }
}
