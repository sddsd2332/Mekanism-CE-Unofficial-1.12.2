package mekanism.qioprocessing.common.execution;

import mekanism.common.Mekanism;
import mekanism.qioprocessing.common.machine.DefaultQIOAutomationHost;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Captures completed external endpoint state without waiting for the next global world save.
 * Requests are grouped by chunk and acknowledged only after the asynchronous chunk writer has
 * consumed the captured snapshot.
 */
/**
 * QIO 处理模块中的 QIOEndpointPersistenceService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOEndpointPersistenceService {

    public static final QIOEndpointPersistenceService INSTANCE =
          new QIOEndpointPersistenceService();

    private final Map<TileEntity, PendingEndpoint> pending = new IdentityHashMap<>();
    private final Map<AnvilChunkLoader, ChunkLoaderFields> loaderFields = new IdentityHashMap<>();
    /**
     * Pending snapshots, ordered per loader/chunk.  A loader replaces a queued snapshot when
     * another save is requested for the same chunk, so a new request must never be appended to
     * an already queued generation.  Keeping generations separate prevents acknowledging an
     * operation that was not present in the snapshot which was actually written.
     */
    private final Map<AnvilChunkLoader, Map<Long, List<PendingChunkWrite>>> writes =
          new IdentityHashMap<>();

    private QIOEndpointPersistenceService() {
    }

    /** 请求在处理器端点快照中持久化一个已完成操作。 */
    public synchronized void requestProcessor(@Nonnull QIOCraftingProcessor processor,
          @Nonnull UUID operationId) {
        UUID checked = Objects.requireNonNull(operationId, "operationId");
        if (isOperationTracked(processor, checked)) {
            return;
        }
        PendingEndpoint endpoint = pending.get(processor);
        if (endpoint == null) {
            endpoint = PendingEndpoint.processor(processor);
            pending.put(processor, endpoint);
        }
        endpoint.operationIds.add(checked);
    }

    /** 请求在机器主机端点快照中持久化一个已完成操作。 */
    public synchronized void requestAutomationHost(@Nonnull TileEntity tile,
          @Nonnull DefaultQIOAutomationHost host, @Nonnull UUID operationId) {
        UUID checked = Objects.requireNonNull(operationId, "operationId");
        if (isOperationTracked(tile, checked)) {
            return;
        }
        PendingEndpoint endpoint = pending.get(tile);
        if (endpoint == null) {
            endpoint = PendingEndpoint.host(tile, host);
            pending.put(tile, endpoint);
        }
        endpoint.operationIds.add(checked);
    }

    /** 请求在端点快照中持久化一条传输回执。 */
    public synchronized void requestAutomationHostReceipt(@Nonnull TileEntity tile,
          @Nonnull QIOAutomationHost host, @Nonnull UUID operationId,
          @Nonnull UUID receiptId) {
        UUID checkedOperation = Objects.requireNonNull(operationId, "operationId");
        UUID checkedReceipt = Objects.requireNonNull(receiptId, "receiptId");
        if (isReceiptTracked(tile, checkedOperation, checkedReceipt)) {
            return;
        }
        PendingEndpoint endpoint = pending.get(tile);
        if (endpoint == null) {
            endpoint = PendingEndpoint.host(tile, host);
            pending.put(tile, endpoint);
        }
        endpoint.receipts.computeIfAbsent(checkedOperation, ignored -> new LinkedHashSet<>())
              .add(checkedReceipt);
    }

    /** 按区块合并待写快照，并只确认实际完成写入的快照。 */
    public void flushPending() {
        List<PendingEndpoint> batch;
        synchronized (this) {
            batch = new ArrayList<>(pending.values());
            pending.clear();
        }
        Map<WorldServer, Map<Long, PendingChunkWrite>> grouped = new IdentityHashMap<>();
        for (PendingEndpoint endpoint : batch) {
            addToChunkGroup(grouped, endpoint);
        }

        for (Map<Long, PendingChunkWrite> values : grouped.values()) {
            for (PendingChunkWrite write : values.values()) {
                synchronized (this) {
                    Map<Long, List<PendingChunkWrite>> byChunk = writes.computeIfAbsent(
                          write.loader, ignored -> new LinkedHashMap<>());
                    List<PendingChunkWrite> generations = byChunk.computeIfAbsent(write.key,
                          ignored -> new ArrayList<>());
                    PendingChunkWrite tail = generations.isEmpty() ? null :
                          generations.get(generations.size() - 1);
                    // Requests collected before the first save in this flush share one snapshot.
                    // Once saveChunk has queued that snapshot, subsequent requests need a new
                    // generation and must wait for the first one to finish.
                    if (tail != null && !tail.queued) {
                        tail.merge(write);
                    } else {
                        generations.add(write);
                    }
                }
            }
        }

        List<PendingChunkWrite> current;
        synchronized (this) {
            current = new ArrayList<>();
            for (Map<Long, List<PendingChunkWrite>> value : writes.values()) {
                // Only advance the head generation.  Advancing a later generation in the same
                // tick could replace the loader's still-pending snapshot.
                for (List<PendingChunkWrite> generations : value.values()) {
                    if (!generations.isEmpty()) {
                        current.add(generations.get(0));
                    }
                }
            }
        }
        for (PendingChunkWrite write : current) {
            advance(write);
        }
    }

    /** 丢弃指定世界的待写快照，避免卸载世界后访问失效端点。 */
    public synchronized void discardWorld(@Nonnull World world) {
        pending.entrySet().removeIf(entry -> entry.getKey().getWorld() == world);
        writes.values().forEach(value -> value.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(write -> write.world == world);
            return entry.getValue().isEmpty();
        }));
        writes.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (world instanceof WorldServer serverWorld &&
              serverWorld.getChunkProvider().chunkLoader instanceof AnvilChunkLoader loader) {
            loaderFields.remove(loader);
        }
    }

    /** 清空所有待写端点和区块生成记录。 */
    public synchronized void clear() {
        pending.clear();
        writes.clear();
        loaderFields.clear();
    }

    private void addToChunkGroup(Map<WorldServer, Map<Long, PendingChunkWrite>> grouped,
          PendingEndpoint endpoint) {
        TileEntity tile = endpoint.tile;
        World world = tile.getWorld();
        if (!(world instanceof WorldServer serverWorld)) {
            return;
        }
        if (serverWorld.disableLevelSaving) {
            // /save-off must not lose the completion request.  It will be retried when saving is
            // enabled again.
            requeue(endpoint);
            return;
        }
        if (tile.isInvalid() || !world.isBlockLoaded(tile.getPos(), false) ||
              world.getTileEntity(tile.getPos()) != tile) {
            return;
        }
        if (!(serverWorld.getChunkProvider().chunkLoader instanceof AnvilChunkLoader loader)) {
            try {
                // IChunkLoader exposes no queue state for custom implementations.  Its flush
                // contract is the only portable way to know that saveChunk has reached storage.
                serverWorld.getChunkProvider().chunkLoader.saveChunk(serverWorld,
                      world.getChunk(tile.getPos()));
                serverWorld.getChunkProvider().chunkLoader.flush();
                endpoint.acknowledge();
            } catch (MinecraftException | IOException | RuntimeException e) {
                requeue(endpoint);
            }
            return;
        }
        Chunk chunk = world.getChunk(tile.getPos());
        long key = chunkKey(chunk.x, chunk.z);
        grouped.computeIfAbsent(serverWorld, ignored -> new LinkedHashMap<>())
              .computeIfAbsent(key, ignored -> new PendingChunkWrite(serverWorld, loader, chunk,
                    key)).endpoints.add(endpoint);
    }

    /** Returns true when the operation is already waiting in memory or in a chunk generation. */
    private boolean isOperationTracked(@Nonnull TileEntity tile, @Nonnull UUID operationId) {
        PendingEndpoint waiting = pending.get(tile);
        if (waiting != null && waiting.operationIds.contains(operationId)) {
            return true;
        }
        World world = tile.getWorld();
        if (!(world instanceof WorldServer)) {
            return false;
        }
        WorldServer serverWorld = (WorldServer) world;
        if (!(serverWorld.getChunkProvider().chunkLoader instanceof AnvilChunkLoader loader)) {
            return false;
        }
        long key = chunkKey(tile.getPos().getX() >> 4, tile.getPos().getZ() >> 4);
        Map<Long, List<PendingChunkWrite>> byChunk = writes.get(loader);
        if (byChunk == null) {
            return false;
        }
        List<PendingChunkWrite> generations = byChunk.get(key);
        if (generations == null) {
            return false;
        }
        for (PendingChunkWrite generation : generations) {
            for (PendingEndpoint endpoint : generation.endpoints) {
                if (endpoint.tile == tile && endpoint.operationIds.contains(operationId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isReceiptTracked(@Nonnull TileEntity tile, @Nonnull UUID operationId,
          @Nonnull UUID receiptId) {
        PendingEndpoint waiting = pending.get(tile);
        if (waiting != null && waiting.hasReceipt(operationId, receiptId)) {
            return true;
        }
        World world = tile.getWorld();
        if (!(world instanceof WorldServer serverWorld) ||
              !(serverWorld.getChunkProvider().chunkLoader instanceof AnvilChunkLoader loader)) {
            return false;
        }
        long key = chunkKey(tile.getPos().getX() >> 4, tile.getPos().getZ() >> 4);
        Map<Long, List<PendingChunkWrite>> byChunk = writes.get(loader);
        List<PendingChunkWrite> generations = byChunk == null ? null : byChunk.get(key);
        if (generations == null) return false;
        for (PendingChunkWrite generation : generations) {
            for (PendingEndpoint endpoint : generation.endpoints) {
                if (endpoint.tile == tile && endpoint.hasReceipt(operationId, receiptId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void advance(PendingChunkWrite write) {
        try {
            if (!write.queued) {
                // Waiting for both queue states closes the race where the IO thread moves an
                // older snapshot from chunksToSave to chunksBeingSaved while saveChunk is
                // serializing ours.  AnvilChunkLoader drops snapshots added during that window.
                if (isChunkWritePending(write.loader, write.chunk)) {
                    return;
                }
                write.world.getChunkProvider().chunkLoader.saveChunk(write.world, write.chunk);
                write.queued = true;
                return;
            }
            if (isChunkWritePending(write.loader, write.chunk)) {
                return;
            }
            write.endpoints.forEach(PendingEndpoint::acknowledge);
            synchronized (this) {
                Map<Long, List<PendingChunkWrite>> byChunk = writes.get(write.loader);
                if (byChunk != null) {
                    List<PendingChunkWrite> generations = byChunk.get(write.key);
                    if (generations != null) {
                        generations.remove(write);
                        if (generations.isEmpty()) {
                            byChunk.remove(write.key);
                        }
                    }
                    if (byChunk.isEmpty()) {
                        writes.remove(write.loader);
                    }
                }
            }
        } catch (MinecraftException | IOException | ReflectiveOperationException | RuntimeException e) {
            requeue(write.endpoints);
            synchronized (this) {
                Map<Long, List<PendingChunkWrite>> byChunk = writes.get(write.loader);
                if (byChunk != null) {
                    List<PendingChunkWrite> generations = byChunk.get(write.key);
                    if (generations != null) {
                        generations.remove(write);
                        if (generations.isEmpty()) {
                            byChunk.remove(write.key);
                        }
                    }
                    if (byChunk.isEmpty()) {
                        writes.remove(write.loader);
                    }
                }
            }
            Mekanism.logger.error("Unable to checkpoint completed QIO endpoint chunk {},{} in dimension {}",
                  write.chunk.x, write.chunk.z, write.world.provider.getDimension(), e);
        }
    }

    private boolean isChunkWritePending(AnvilChunkLoader loader, Chunk chunk)
          throws ReflectiveOperationException {
        ChunkLoaderFields fields = fields(loader);
        ChunkPos pos = new ChunkPos(chunk.x, chunk.z);
        Object saving = fields.saving.get(loader);
        Object queued = fields.queued.get(loader);
        // Anvil moves entries from queued to saving.  Checking the source first ensures a move
        // between the two contains calls is still observed by the destination check.
        return queued instanceof Map && ((Map<?, ?>) queued).containsKey(pos) ||
              saving instanceof Set && ((Set<?>) saving).contains(pos);
    }

    private ChunkLoaderFields fields(AnvilChunkLoader loader) throws ReflectiveOperationException {
        synchronized (this) {
            ChunkLoaderFields cached = loaderFields.get(loader);
            if (cached != null) {
                return cached;
            }
            Field map = null;
            Field set = null;
            for (Field field : AnvilChunkLoader.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    map = field;
                } else if (Set.class.isAssignableFrom(field.getType())) {
                    set = field;
                }
            }
            if (map == null || set == null) {
                throw new NoSuchFieldException("Anvil chunk save queues");
            }
            map.setAccessible(true);
            set.setAccessible(true);
            cached = new ChunkLoaderFields(map, set);
            loaderFields.put(loader, cached);
            return cached;
        }
    }

    private synchronized void requeue(PendingEndpoint endpoint) {
        PendingEndpoint current = pending.get(endpoint.tile);
        if (current == null) {
            pending.put(endpoint.tile, endpoint);
        } else {
            current.operationIds.addAll(endpoint.operationIds);
            endpoint.receipts.forEach((operationId, values) ->
                  current.receipts.computeIfAbsent(operationId,
                        ignored -> new LinkedHashSet<>()).addAll(values));
        }
    }

    private synchronized void requeue(List<PendingEndpoint> endpoints) {
        endpoints.forEach(this::requeue);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    private static final class ChunkLoaderFields {
        private final Field queued;
        private final Field saving;

        private ChunkLoaderFields(Field queued, Field saving) {
            this.queued = queued;
            this.saving = saving;
        }
    }

    private static final class PendingChunkWrite {
        private final WorldServer world;
        private final AnvilChunkLoader loader;
        private final Chunk chunk;
        private final long key;
        private final List<PendingEndpoint> endpoints = new ArrayList<>();
        private boolean queued;

        private PendingChunkWrite(WorldServer world, AnvilChunkLoader loader, Chunk chunk, long key) {
            this.world = world;
            this.loader = loader;
            this.chunk = chunk;
            this.key = key;
        }

        private void merge(PendingChunkWrite other) {
            endpoints.addAll(other.endpoints);
        }
    }

    private static final class PendingEndpoint {
        private final TileEntity tile;
        @Nullable
        private final QIOCraftingProcessor processor;
        @Nullable
        private final QIOAutomationHost host;
        private final Set<UUID> operationIds = new LinkedHashSet<>();
        private final Map<UUID, Set<UUID>> receipts = new LinkedHashMap<>();

        private PendingEndpoint(TileEntity tile, @Nullable QIOCraftingProcessor processor,
              @Nullable QIOAutomationHost host) {
            this.tile = tile;
            this.processor = processor;
            this.host = host;
        }

        private static PendingEndpoint processor(QIOCraftingProcessor processor) {
            return new PendingEndpoint(processor, processor, null);
        }

        private static PendingEndpoint host(TileEntity tile, QIOAutomationHost host) {
            return new PendingEndpoint(tile, null, host);
        }

        private void acknowledge() {
            if (processor != null) {
                boolean changed = false;
                for (UUID operationId : operationIds) {
                    changed |= processor.getProcessorState().confirmSettledLanePersisted(operationId);
                }
                if (changed && processor.getFrequencyReference() != null) {
                    QIOProcessingExecutionService.INSTANCE.wakeDevice(
                          processor.getFrequencyReference().getFrequencyUUID(),
                          processor.getProcessorState().getProcessorUUID());
                }
            } else if (host != null) {
                operationIds.forEach(host::confirmCompletedOperationPersisted);
                receipts.forEach((operationId, values) -> values.forEach(receiptId ->
                      host.confirmTransferReceiptPersisted(operationId, receiptId)));
            }
        }

        private boolean hasReceipt(UUID operationId, UUID receiptId) {
            Set<UUID> values = receipts.get(operationId);
            return values != null && values.contains(receiptId);
        }
    }
}
