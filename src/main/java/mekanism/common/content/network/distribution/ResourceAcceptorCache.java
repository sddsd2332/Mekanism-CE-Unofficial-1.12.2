package mekanism.common.content.network.distribution;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import mekanism.api.Coord4D;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.capabilities.Capability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Network-owned connection descriptions plus credentials; Chunk references live for one pass only.
 */
public final class ResourceAcceptorCache<T> {
    private long revision = Long.MIN_VALUE;
    private Connection<T>[] connections;
    private final Long2ObjectOpenHashMap<Chunk> chunks = new Long2ObjectOpenHashMap<>();

    @SuppressWarnings("unchecked")
    public void collect(World world, Collection<Coord4D> positions, Map<Coord4D, EnumSet<EnumFacing>> sides,
                        long generation, Capability<T> capability, BiConsumer<EnumFacing, T> consumer) {
        if (connections == null || revision != generation) {
            ArrayList<Connection<T>> rebuilt = new ArrayList<>();
            for (Coord4D pos : positions) {
                EnumSet<EnumFacing> connected = sides.get(pos);
                if (connected != null)
                    for (EnumFacing side : connected) rebuilt.add(new Connection<>(pos.getPos(), side));
            }
            connections = rebuilt.toArray(new Connection[0]);
            revision = generation;
        }
        IChunkProvider provider = world.getChunkProvider();
        try {
            for (Connection<T> connection : connections) {
                long key = connection.chunk;
                Chunk chunk = chunks.get(key);
                if ((chunk == null && !chunks.containsKey(key)) || (chunk != null && !chunk.isLoaded())) {
                    chunk = provider.getLoadedChunk(connection.pos.getX() >> 4, connection.pos.getZ() >> 4);
                    chunks.put(key, chunk);
                }
                TileEntity tile = chunk == null ? null : chunk.getTileEntity(connection.pos, Chunk.EnumCreateEntityType.CHECK);
                T acceptor = connection.acceptor.resolve(tile, capability, connection.side);
                if (acceptor != null) consumer.accept(connection.side, acceptor);
            }
        } finally {
            chunks.clear();
        }
    }

    public void clear() {
        connections = null;
        chunks.clear();
    }

    private static final class Connection<T> {
        final BlockPos pos;
        final long chunk;
        final EnumFacing side;
        final ManagedAcceptor<T> acceptor = new ManagedAcceptor<>();

        Connection(BlockPos pos, EnumFacing side) {
            this.pos = pos.toImmutable();
            this.side = side;
            chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }
}
