package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.base.ITileNetwork;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.network.PacketDataRequest.DataRequestMessage;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.SecurityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class PacketDataRequest implements IMessageHandler<DataRequestMessage, IMessage> {

    private final Map<EntityPlayer, RequestLimiter> requestLimiters = new WeakHashMap<>();

    @Override
    public IMessage onMessage(DataRequestMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            World worldServer = DimensionManager.getWorld(message.coord4D.dimensionId);
            if (worldServer == null || player.world != worldServer) {
                return;
            }
            if (!requestLimiters.computeIfAbsent(player, ignored -> new RequestLimiter())
                  .allow(message.coord4D, worldServer.getTotalWorldTime())) {
                return;
            }
            TileEntity tileEntity = message.coord4D.getTileEntity(worldServer);
            if (tileEntity == null || !SecurityUtils.canAccess(player, tileEntity)) {
                return;
            }
            if (tileEntity instanceof TileEntityMultiblock<?> multiblock) {
                multiblock.sendStructure = true;
            }
            if (CapabilityUtils.hasCapability(tileEntity, Capabilities.GRID_TRANSMITTER_CAPABILITY, null)) {
                IGridTransmitter<?, ?, ?> transmitter = CapabilityUtils.getCapability(tileEntity, Capabilities.GRID_TRANSMITTER_CAPABILITY, null);
                if (transmitter != null) {
                    transmitter.setRequestsUpdate();
                    if (transmitter.hasTransmitterNetwork()) {
                        transmitter.getTransmitterNetwork().addUpdate(player);
                    }
                }
            }
            if (CapabilityUtils.hasCapability(tileEntity, Capabilities.TILE_NETWORK_CAPABILITY, null)) {
                ITileNetwork network = CapabilityUtils.getCapability(tileEntity, Capabilities.TILE_NETWORK_CAPABILITY, null);
                if (network != null) {
                    Mekanism.packetHandler.sendTo(new TileEntityMessage(tileEntity, network.getNetworkedData()), (EntityPlayerMP) player);
                }
            }
        }, player);
        return null;
    }

    static final class RequestLimiter {

        private static final int MAX_REQUESTS_PER_TICK = 512;
        private static final int SAME_COORD_COOLDOWN_TICKS = 5;
        private static final int MAX_TRACKED_COORDS = 2_048;

        private final Map<Coord4D, Long> recentCoordinates = new LinkedHashMap<Coord4D, Long>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Coord4D, Long> eldest) {
                return size() > MAX_TRACKED_COORDS;
            }
        };
        private long requestTick = Long.MIN_VALUE;
        private int requestsThisTick;

        boolean allow(Coord4D coord, long tick) {
            if (requestTick != tick) {
                requestTick = tick;
                requestsThisTick = 0;
            }
            Long lastRequest = recentCoordinates.get(coord);
            if (lastRequest != null && tick >= lastRequest && tick - lastRequest < SAME_COORD_COOLDOWN_TICKS) {
                return false;
            }
            if (requestsThisTick >= MAX_REQUESTS_PER_TICK) {
                return false;
            }
            requestsThisTick++;
            recentCoordinates.put(new Coord4D(coord.x, coord.y, coord.z, coord.dimensionId), tick);
            return true;
        }
    }

    public static class DataRequestMessage implements IMessage {

        public Coord4D coord4D;

        public DataRequestMessage() {
        }

        public DataRequestMessage(Coord4D coord) {
            coord4D = coord;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            coord4D.write(dataStream);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            coord4D = Coord4D.read(dataStream);
        }
    }
}
