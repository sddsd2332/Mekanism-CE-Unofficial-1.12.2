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
        if (player == null) {
            return null;
        }

        PacketHandler.handlePacket(() -> {
            TileEntity tile = message.coord4D.getTileEntity(player.world);
            if (!PacketHandler.canAccessTile(player, tile)) {
                return;
            }
            if (tile instanceof ISideConfiguration config) {
                ITileNetwork network = tile instanceof ITileNetwork tileNetwork ? tileNetwork :
                        CapabilityUtils.getCapability(tile, Capabilities.TILE_NETWORK_CAPABILITY, null);
                boolean updated = false;

                if (message.packetType == ConfigurationPacket.EJECT) {
                    if (message.transmission == null) {
                        return;
                    }
                    config.getConfig().setEjecting(message.transmission, !config.getConfig().isEjecting(message.transmission));
                    updated = true;
                } else if (message.packetType == ConfigurationPacket.SIDE_DATA) {
                    if (message.transmission == null || message.configIndex == null) {
                        return;
                    }
                    if (message.clickType == 0) {
                        MekanismUtils.incrementOutput(config, message.transmission, message.configIndex);
                    } else if (message.clickType == 1) {
                        MekanismUtils.decrementOutput(config, message.transmission, message.configIndex);
                    } else if (message.clickType == 2) {
                        if (!config.getConfig().clearOutput(message.transmission, message.configIndex)) {
                            return;
                        }
                    }
                    updated = true;
                } else if (message.packetType == ConfigurationPacket.EJECT_COLOR) {
                    TileComponentEjector ejector = config.getEjector();
                    if (message.clickType == 0) {
                        ejector.setOutputColor(TransporterUtils.increment(ejector.getOutputColor()));
                    } else if (message.clickType == 1) {
                        ejector.setOutputColor(TransporterUtils.decrement(ejector.getOutputColor()));
                    } else if (message.clickType == 2) {
                        ejector.setOutputColor(null);
                    }
                    updated = true;
                } else if (message.packetType == ConfigurationPacket.INPUT_COLOR) {
                    EnumFacing side = MekanismUtils.getByIndex(EnumFacing.values(), message.inputSide, null);
                    if (side == null) {
                        return;
                    }
                    TileComponentEjector ejector = config.getEjector();
                    if (message.clickType == 0) {
                        ejector.setInputColor(side, TransporterUtils.increment(ejector.getInputColor(side)));
                    } else if (message.clickType == 1) {
                        ejector.setInputColor(side, TransporterUtils.decrement(ejector.getInputColor(side)));
                    } else if (message.clickType == 2) {
                        ejector.setInputColor(side, null);
                    }
                    updated = true;
                } else if (message.packetType == ConfigurationPacket.STRICT_INPUT) {
                    config.getEjector().setStrictInput(!config.getEjector().hasStrictInput());
                    updated = true;
                }
                if (updated) {
                    tile.markDirty();
                    if (network != null) {
                        Mekanism.packetHandler.sendToAllTracking(new TileEntityMessage(message.coord4D, network.getNetworkedData()), message.coord4D);
                        if (player instanceof EntityPlayerMP playerMP) {
                            //Guarantee the click initiator always gets refreshed, even if not in playersUsing/tracking.
                            Mekanism.packetHandler.sendTo(new TileEntityMessage(message.coord4D, network.getNetworkedData()), playerMP);
                        }
                        if (tile instanceof TileEntityBasicBlock basicTile) {
                            basicTile.playersUsing.stream().filter(p -> p != player)
                                    .forEach(p -> Mekanism.packetHandler.sendTo(new TileEntityMessage(message.coord4D, network.getNetworkedData()), (EntityPlayerMP) p));
                        }
                    }
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
                configIndex = MekanismUtils.getByIndex(EnumFacing.values(), extra, EnumFacing.NORTH);
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
            packetType = MekanismUtils.getByIndex(ConfigurationPacket.values(), dataStream.readInt(), ConfigurationPacket.EJECT);
            coord4D = Coord4D.read(dataStream);

            if (packetType == ConfigurationPacket.EJECT) {
                transmission = MekanismUtils.getByIndex(TransmissionType.values(), dataStream.readInt(), TransmissionType.ITEM);
            } else if (packetType == ConfigurationPacket.SIDE_DATA) {
                clickType = dataStream.readInt();
                configIndex = MekanismUtils.getByIndex(EnumFacing.values(), dataStream.readInt(), EnumFacing.NORTH);
                transmission = MekanismUtils.getByIndex(TransmissionType.values(), dataStream.readInt(), TransmissionType.ITEM);
            } else if (packetType == ConfigurationPacket.EJECT_COLOR) {
                clickType = dataStream.readInt();
            } else if (packetType == ConfigurationPacket.INPUT_COLOR) {
                clickType = dataStream.readInt();
                inputSide = dataStream.readInt();
            }
        }
    }
}
