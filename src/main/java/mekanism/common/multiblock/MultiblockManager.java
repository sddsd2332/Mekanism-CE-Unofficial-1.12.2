package mekanism.common.multiblock;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Coord4D;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import mekanism.common.multiblock.persistence.LegacyReplicaSelection;
import mekanism.common.multiblock.persistence.ManagedCacheIo;
import mekanism.common.multiblock.persistence.MultiblockBackpressureException;
import mekanism.common.multiblock.persistence.ManagedCacheNbt;
import mekanism.common.multiblock.persistence.ManagedCacheStore;
import mekanism.common.multiblock.persistence.LegacyIndexLoader;
import mekanism.common.multiblock.persistence.MultiblockPersistenceSession;
import mekanism.common.multiblock.persistence.MultiblockStoragePaths;
import mekanism.common.Mekanism;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.*;

public class MultiblockManager<T extends SynchronizedData<T>> {

    private static final Set<MultiblockManager<?>> MANAGERS = new ObjectOpenHashSet<>();
    private static ManagedCacheIo storageIo;
    private static LegacyIndexLoader legacyLoader;
    private static final List<ManagedCacheIo> retiringStores = new ArrayList<>();
    private static final List<ManagedCacheIo.Ticket<Void>> closingSessions = new ArrayList<>();

    public String name;

    /** World identity, not a dimension number: multiple saves can reuse the same dimension/UUID. */
    private final Map<World, WorldCacheState<T>> worlds = new IdentityHashMap<>();

    private static final class WorldCacheState<T extends SynchronizedData<T>> {
        final Map<String, MultiblockCache<T>> inventories = new Object2ObjectOpenHashMap<>();
        final Map<String, Long> timestamps = new Object2ObjectOpenHashMap<>();
        final Map<String, Long> serverTickClaims = new HashMap<>();
        final Map<String, Long> cacheSyncClaims = new HashMap<>();
        final Set<String> runtimeAuthorities = new HashSet<>();
        final Set<String> managed = new HashSet<>();
        final Map<String, T> bindings = new HashMap<>();
        MultiblockPersistenceSession persistence;
        IOException persistenceFailure;
        IOException recoverySnapshotFailure;
        boolean recovering;
        PendingFormation<T> pending;
        long nextSaveTick;
        long nextRetryTick;
    }

    private static final class PendingFormation<T extends SynchronizedData<T>> {
        final MultiblockPersistenceSession.Transfer transfer;
        final Collection<ManagedCacheStore.SourceKey> sources;
        final List<TileEntityMultiblock<T>> casings;
        PendingFormation(MultiblockPersistenceSession.Transfer transfer, Collection<ManagedCacheStore.SourceKey> sources,
              Collection<TileEntityMultiblock<T>> casings) {
            this.transfer = transfer;
            this.sources = new ArrayList<>(sources);
            this.casings = new ArrayList<>(casings);
        }
    }

    private WorldCacheState<T> state(World world) {
        return worlds.computeIfAbsent(Objects.requireNonNull(world, "world"), ignored -> new WorldCacheState<>());
    }

    public MultiblockCache<T> getInventory(World world, String id) {
        WorldCacheState<T> state = worlds.get(world);
        return state == null ? null : state.inventories.get(id);
    }

    /** Select a single detached legacy replica before consuming an ID during formation. */
    public MultiblockCache<T> selectFormationCache(World world, String id, Collection<TileEntityMultiblock<T>> loaded, T target) throws IOException {
        WorldCacheState<T> state = worlds.get(world);
        MultiblockCache<T> current = state == null ? null : state.inventories.get(id);
        if (current == null) throw new IOException("Referenced cache is not available: " + id);
        // A live authority includes post-tick changes and must not be replaced by a late casing.
        if (state.runtimeAuthorities.contains(id)) return current;
        List<LegacyReplicaSelection.Replica> candidates = new ArrayList<>();
        // Keep the best replica already observed in this world session, even if the casing
        // carrying it lies outside the newly scanned shape or its chunk has since unloaded.
        current.validateCapacity(target);
        NBTTagCompound knownSnapshot = new NBTTagCompound();
        current.save(knownSnapshot);
        candidates.add(new LegacyReplicaSelection.Replica(state.timestamps.getOrDefault(id, Long.MIN_VALUE), knownSnapshot));
        TileEntityMultiblock<T> factory = null;
        for (TileEntityMultiblock<T> tile : loaded) {
            if (tile.getWorld() != world || tile.getManager() != this || !Objects.equals(id, tile.cachedID)) continue;
            tile.cachedData.validateCapacity(target);
            NBTTagCompound snapshot = new NBTTagCompound();
            tile.cachedData.save(snapshot);
            candidates.add(new LegacyReplicaSelection.Replica(tile.cachedDataTimestamp, snapshot));
            factory = tile;
        }
        LegacyReplicaSelection.Replica chosen = LegacyReplicaSelection.select(candidates, world.getTotalWorldTime(), null);
        if (factory == null) throw new IOException("Legacy cache has no verified loaded casing: " + id);
        MultiblockCache<T> result = factory.getNewCache();
        NBTTagCompound snapshot = chosen.snapshot();
        result.load(snapshot);
        NBTTagCompound restored = new NBTTagCompound();
        result.save(restored);
        if (!snapshot.equals(restored)) throw new IOException("Selected legacy cache cannot round-trip without changing stored state");
        result.locations.addAll(current.locations);
        return result;
    }

    public MultiblockManager(String s) {
        name = s;
        MANAGERS.add(this);
    }

    public static void tick(World world) {
        pollClosedSessions();
        MANAGERS.forEach(manager -> manager.tickSelf(world));
    }

    public static String getStructureId(TileEntityMultiblock<?> tile) {
        return tile.structure != null ? tile.getSynchronizedData().inventoryID : null;
    }

    public static boolean areEqual(TileEntity tile1, TileEntity tile2) {
        if (tile1 instanceof TileEntityMultiblock<?> multiblock1 && tile2 instanceof TileEntityMultiblock<?> multiblock2) {
            return multiblock1.getManager() == multiblock2.getManager();
        }
        return false;
    }

    public static void reset() {
        for (MultiblockManager<?> manager : MANAGERS) {
            for (World world : new ArrayList<>(manager.worlds.keySet())) manager.closeWorld(world);
        }
        for (MultiblockManager<?> manager : MANAGERS) {
            if (!manager.worlds.isEmpty()) {
                Mekanism.logger.error("Multiblock shutdown has unresolved runtime snapshots for {}; recover failed stores before stopping the server", manager.name);
            }
        }
        if (legacyLoader != null) { legacyLoader.close(); legacyLoader = null; }
        if (storageIo != null) {
            storageIo.close();
            retiringStores.add(storageIo);
            storageIo = null;
        }
        pollClosedSessions();
    }

    /** Only after server ticks have stopped: the owner thread must capture any budget-deferred state. */
    public static void shutdown() {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
        boolean interrupted = false;
        try {
            while (true) {
                for (MultiblockManager<?> manager : MANAGERS) {
                    for (World world : new ArrayList<>(manager.worlds.keySet())) manager.closeWorld(world);
                }
                if (MANAGERS.stream().allMatch(manager -> manager.worlds.isEmpty())) break;
                if (System.nanoTime() >= deadline) break;
                try { Thread.sleep(10); }
                catch (InterruptedException error) { interrupted = true; break; }
            }
            reset();
            while (!isPersistenceStopped() && System.nanoTime() < deadline && !interrupted) {
                try { Thread.sleep(10); }
                catch (InterruptedException error) { interrupted = true; }
            }
            if (!isPersistenceStopped()) {
                Mekanism.logger.error("Multiblock shutdown did not finish within its drain deadline; latest runtime snapshots may not be durable. Preserve the world and emergency journals.");
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void pollClosedSessions() {
        Iterator<ManagedCacheIo.Ticket<Void>> iterator = closingSessions.iterator();
        while (iterator.hasNext()) {
            ManagedCacheIo.Ticket<Void> ticket = iterator.next();
            if (ticket.isDone()) {
                try { ticket.result(); }
                catch (IOException error) { Mekanism.logger.error("Multiblock final save/close failed", error); }
                iterator.remove();
            }
        }
    }

    /** Nonblocking shutdown status for lifecycle diagnostics and standalone server harnesses. */
    public static boolean isPersistenceStopped() {
        pollClosedSessions();
        retiringStores.removeIf(ManagedCacheIo::isTerminated);
        return storageIo == null && retiringStores.isEmpty() && closingSessions.isEmpty() &&
              MANAGERS.stream().allMatch(manager -> manager.worlds.isEmpty());
    }

    public static void unload(World world) {
        MANAGERS.forEach(manager -> manager.closeWorld(world));
    }

    public static void save(World world) {
        MANAGERS.forEach(manager -> manager.saveWorld(world));
    }

    public static void saveAll() {
        for (MultiblockManager<?> manager : MANAGERS) for (World world : new ArrayList<>(manager.worlds.keySet())) manager.saveWorld(world);
    }

    /** Explicit retry of a failed read-only history load; never clears a durable writer failure. */
    public static int retryLegacyHistory(World world) {
        int count = 0;
        for (MultiblockManager<?> manager : MANAGERS) {
            WorldCacheState<?> state = manager.worlds.get(world);
            if (state != null && state.persistence != null && state.persistence.state() == MultiblockPersistenceSession.State.READY &&
                  state.persistence.legacyFailure() != null) {
                state.persistence.retryLegacyIndex();
                count++;
                for (TileEntity tile : world.loadedTileEntityList) {
                    if (tile instanceof TileEntityMultiblock<?> casing && casing.getManager() == manager) casing.requestCacheRetry();
                }
            }
        }
        return count;
    }

    public static java.util.List<String> persistenceStatus(World world) {
        java.util.List<String> result = new ArrayList<>();
        for (MultiblockManager<?> manager : MANAGERS) {
            WorldCacheState<?> state = manager.worlds.get(world);
            if (state == null) continue;
            long observedLegacy = state.inventories.keySet().stream().filter(id -> !state.managed.contains(id)).count();
            result.add(manager.name + " observedLegacyCaches=" + observedLegacy + " " +
                  (state.persistence == null ? "state=NOT_OPENED" : state.persistence.diagnostics().toString()));
            if (state.persistenceFailure != null) result.add(manager.name + " managerFailure=" + state.persistenceFailure);
        }
        if (result.isEmpty()) result.add("No observed multiblock sessions in this world");
        return result;
    }

    public static int recoverStores(World world) throws IOException {
        int count = 0;
        for (MultiblockManager<?> manager : MANAGERS) {
            WorldCacheState<?> state = manager.worlds.get(world);
            if (state == null || state.persistenceFailure == null || state.recovering) continue;
            if (state.recoverySnapshotFailure != null) throw new IOException("Cannot recover " + manager.name + ": runtime snapshot was not retained", state.recoverySnapshotFailure);
            if (state.persistence == null || state.persistence.state() != MultiblockPersistenceSession.State.FAILED) {
                throw new IOException("Cannot recover " + manager.name + ": failure requires a world reload");
            }
            state.persistence.recover();
            state.recovering = true;
            count++;
        }
        return count;
    }

    /** null means preparation/close-drain is still in progress; no server-thread waits. */
    public MultiblockPersistenceSession persistence(World world) throws IOException {
        WorldCacheState<T> state = state(world);
        if (state.recovering) return null;
        if (state.persistenceFailure != null) throw state.persistenceFailure;
        // An earlier close may still own this exact World during a failed unload retry.
        if (state.persistence != null && (state.persistence.state() == MultiblockPersistenceSession.State.CLOSING ||
              state.persistence.state() == MultiblockPersistenceSession.State.CLOSED)) return null;
        if (state.persistence == null) {
            if (world.getTotalWorldTime() < state.nextRetryTick) return null;
            try {
                MultiblockStoragePaths paths = MultiblockStoragePaths.resolve(world, name);
                retiringStores.removeIf(ManagedCacheIo::isTerminated);
                for (ManagedCacheIo retiring : retiringStores) if (!retiring.isReleased(paths.managedDirectory)) return null;
                if (storageIo == null) storageIo = new ManagedCacheIo();
                if (legacyLoader == null) legacyLoader = new LegacyIndexLoader();
                if (!storageIo.isReleased(paths.managedDirectory)) return null;
                state.persistence = new MultiblockPersistenceSession(storageIo, legacyLoader, paths);
            } catch (MultiblockBackpressureException retryLater) {
                state.nextRetryTick = world.getTotalWorldTime() + 20;
                return null;
            } catch (IOException error) {
                state.persistenceFailure = error;
                throw error;
            }
        }
        state.persistence.poll();
        if (state.persistence.state() == MultiblockPersistenceSession.State.FAILED) {
            failPersistence(world, state, state.persistence.failure());
            throw state.persistence.failure();
        }
        return state.persistence.state() == MultiblockPersistenceSession.State.READY ? state.persistence : null;
    }

    public boolean isManaged(World world, String id) {
        WorldCacheState<T> state = worlds.get(world);
        return state != null && state.managed.contains(id);
    }

    public T boundStructure(World world, String id) {
        WorldCacheState<T> state = worlds.get(world);
        return state == null ? null : state.bindings.get(id);
    }

    public MultiblockCache<T> managedCache(World world, String id, TileEntityMultiblock<T> factory) throws IOException {
        MultiblockPersistenceSession session = persistence(world);
        if (session == null) throw new IOException("Managed cache is still loading");
        MultiblockCache<T> cache = factory.getNewCache();
        NBTTagCompound snapshot = ManagedCacheNbt.decode(session.latestSnapshot(UUID.fromString(id)));
        cache.load(snapshot);
        NBTTagCompound restored = new NBTTagCompound();
        cache.save(restored);
        if (!snapshot.equals(restored)) throw new IOException("Managed cache cannot round-trip without changing stored state: " + id);
        return cache;
    }

    public boolean bindManaged(World world, String id, long[] positions, T structure, MultiblockCache<T> cache) throws IOException {
        MultiblockPersistenceSession session = persistence(world);
        if (session == null || !session.bind(UUID.fromString(id), positions, structure)) return false;
        WorldCacheState<T> state = state(world);
        state.managed.add(id);
        state.inventories.put(id, cache);
        state.bindings.put(id, structure);
        return true;
    }

    public void detached(World world, T structure) {
        WorldCacheState<T> state = worlds.get(world);
        if (state == null || structure.inventoryID == null) return;
        String id = structure.inventoryID;
        if (state.bindings.get(id) == structure) {
            try { snapshot(state, id, structure); }
            catch (IOException error) { failPersistence(world, state, error); return; }
            state.persistence.unbind(UUID.fromString(id), structure);
            state.bindings.remove(id);
        }
    }

    public boolean beginTransfer(World world, Collection<ManagedCacheStore.SourceKey> sources, long[] positions,
          MultiblockCache<T> cache, Collection<TileEntityMultiblock<T>> casings) throws IOException {
        MultiblockPersistenceSession session = persistence(world);
        if (session == null || state(world).pending != null) return false;
        NBTTagCompound tag = new NBTTagCompound();
        cache.save(tag);
        MultiblockPersistenceSession.Transfer transfer = session.beginTransfer(sources, positions, ManagedCacheNbt.encode(tag));
        if (transfer == null) return false;
        state(world).pending = new PendingFormation<>(transfer, sources, casings);
        return true;
    }

    private void pollTransfer(World world, WorldCacheState<T> state) throws IOException {
        state.persistence.poll();
        if (state.persistence.state() == MultiblockPersistenceSession.State.FAILED) throw state.persistence.failure();
        PendingFormation<T> pending = state.pending;
        if (pending != null && pending.transfer.aborted()) {
            for (TileEntityMultiblock<T> tile : pending.casings) {
                if (!tile.isInvalid() && tile.getWorld() == world && world.isBlockLoaded(tile.getPos()) && world.getTileEntity(tile.getPos()) == tile) tile.requestCacheRetry();
            }
            state.pending = null;
            return;
        }
        if (pending == null || !pending.transfer.committed()) return;
        List<String> consumed = new ArrayList<>();
        for (ManagedCacheStore.SourceKey source : pending.sources) {
            String id = source.id.toString();
            consumed.add(id);
            state.inventories.remove(id);
            state.timestamps.remove(id);
            state.runtimeAuthorities.remove(id);
            state.managed.remove(id);
            state.bindings.remove(id);
        }
        String target = pending.transfer.target.toString();
        inheritServerTickClaim(world, consumed, target, world.getTotalWorldTime());
        boolean retryRequested = false;
        for (TileEntityMultiblock<T> tile : pending.casings) {
            // Do not attach authority to a replacement block built while IO was pending.
            if (!tile.isInvalid() && world.isBlockLoaded(tile.getPos()) && world.getTileEntity(tile.getPos()) == tile) {
                tile.cachedID = target;
                tile.cacheFormat = 1;
                tile.cachedData = tile.getNewCache();
                tile.cachedDataTimestamp = Long.MIN_VALUE;
                tile.markDirty();
                if (!retryRequested) { tile.requestCacheRetry(); retryRequested = true; }
            }
        }
        state.pending = null;
    }

    private void snapshot(WorldCacheState<T> state, String id, T structure) throws IOException {
        if (state.persistence == null || state.persistence.state() != MultiblockPersistenceSession.State.READY ||
              state.persistence.reserved(ManagedCacheStore.SourceKey.managed(UUID.fromString(id)))) return;
        MultiblockCache<T> cache = state.inventories.get(id);
        if (cache == null) throw new IOException("Missing runtime authority: " + id);
        cache.sync(structure);
        NBTTagCompound tag = new NBTTagCompound();
        cache.save(tag);
        state.persistence.changed(UUID.fromString(id), ManagedCacheNbt.encode(tag));
    }

    private void saveWorld(World world) { saveWorld(world, true); }

    private void saveWorld(World world, boolean force) {
        WorldCacheState<T> state = worlds.get(world);
        if (state == null || state.persistence == null) return;
        if (state.recovering) {
            pollRecovery(world, state);
            return;
        }
        if (state.persistenceFailure != null) {
            if (state.persistenceFailure instanceof MultiblockBackpressureException) retryBackpressure(world, state);
            return;
        }
        try {
            pollTransfer(world, state);
            if (state.persistence.state() != MultiblockPersistenceSession.State.READY) return;
            for (Map.Entry<String, T> bound : state.bindings.entrySet()) snapshot(state, bound.getKey(), bound.getValue());
            long tick = world.getTotalWorldTime();
            if (force || tick >= state.nextSaveTick) {
                if (state.persistence.flush()) state.nextSaveTick = tick + 100;
            }
        } catch (IOException error) {
            failPersistence(world, state, error);
        }
    }

    private void failPersistence(World world, WorldCacheState<T> state, IOException error) {
        boolean firstFailure = state.persistenceFailure == null;
        if (state.persistenceFailure == null) Mekanism.logger.error("Multiblock {} in dimension {} is paused: {}", name, world.provider.getDimension(), error.toString());
        state.persistenceFailure = error;
        if (firstFailure) state.nextRetryTick = world.getTotalWorldTime() + 20;
        if (firstFailure) retainPausedSnapshots(state);
        for (T structure : new ArrayList<>(state.bindings.values())) {
            structure.setFormed(false);
            structure.destroyed = true;
            for (Coord4D coordinate : structure.locations) {
                TileEntity tile = coordinate.getTileEntity(world);
                if (tile instanceof TileEntityMultiblock<?> multiblock && multiblock.structure == structure) multiblock.structure = null;
            }
        }
    }

    private void retainPausedSnapshots(WorldCacheState<T> state) {
        if (state.persistence == null) return;
        MultiblockPersistenceSession.State phase = state.persistence.state();
        if (phase != MultiblockPersistenceSession.State.READY && phase != MultiblockPersistenceSession.State.FAILED) return;
        state.recoverySnapshotFailure = null;
        for (T structure : state.bindings.values()) {
            UUID id = UUID.fromString(structure.inventoryID);
            if (state.persistence.reserved(ManagedCacheStore.SourceKey.managed(id))) continue;
            try {
                MultiblockCache<T> cache = state.inventories.get(structure.inventoryID);
                if (cache == null) throw new IOException("Missing paused runtime cache: " + structure.inventoryID);
                cache.sync(structure);
                NBTTagCompound tag = new NBTTagCompound();
                cache.save(tag);
                byte[] snapshot = ManagedCacheNbt.encode(tag);
                if (phase == MultiblockPersistenceSession.State.READY) state.persistence.changed(id, snapshot);
                else state.persistence.retainFailedSnapshot(id, snapshot);
            } catch (IOException captureError) {
                if (state.recoverySnapshotFailure == null || !(captureError instanceof MultiblockBackpressureException)) {
                    state.recoverySnapshotFailure = captureError;
                }
                if (!(captureError instanceof MultiblockBackpressureException)) {
                    Mekanism.logger.error("Could not retain paused multiblock snapshot", captureError);
                }
            }
        }
    }

    private void retryBackpressure(World world, WorldCacheState<T> state) {
        long tick = world.getTotalWorldTime();
        if (tick < state.nextRetryTick) return;
        state.nextRetryTick = tick + 20;
        state.persistence.poll();
        if (state.persistence.state() == MultiblockPersistenceSession.State.FAILED) {
            retainPausedSnapshots(state);
            failPersistence(world, state, state.persistence.failure());
            return;
        }
        if (state.persistence.state() != MultiblockPersistenceSession.State.READY) return;
        try {
            pollTransfer(world, state);
            retainPausedSnapshots(state);
            if (state.recoverySnapshotFailure != null && !(state.recoverySnapshotFailure instanceof MultiblockBackpressureException)) {
                failPersistence(world, state, state.recoverySnapshotFailure);
                return;
            }
            if (state.persistence.busy() || state.persistence.flush() || state.recoverySnapshotFailure != null) return;
            for (Map.Entry<String, T> bound : state.bindings.entrySet()) {
                state.persistence.unbind(UUID.fromString(bound.getKey()), bound.getValue());
                for (Coord4D coordinate : bound.getValue().locations) {
                    if (!coordinate.exists(world)) continue;
                    TileEntity tile = coordinate.getTileEntity(world);
                    if (tile instanceof TileEntityMultiblock<?> casing && casing.getManager() == this) casing.requestCacheRetry();
                }
            }
            state.bindings.clear();
            state.persistenceFailure = null;
            state.nextSaveTick = tick + 100;
        } catch (MultiblockBackpressureException retryLater) {
            // The source remains frozen and the immutable dirty snapshots remain owned here.
        } catch (IOException error) {
            failPersistence(world, state, error);
        }
    }

    private void pollRecovery(World world, WorldCacheState<T> state) {
        state.persistence.poll();
        if (state.persistence.state() == MultiblockPersistenceSession.State.FAILED) {
            state.recovering = false;
            failPersistence(world, state, state.persistence.failure());
            return;
        }
        if (state.persistence.state() != MultiblockPersistenceSession.State.READY) return;
        try {
            // Recovery is not published until retained post-save changes have also been committed.
            if (state.persistence.busy() || state.persistence.flush()) return;
            state.bindings.clear();
            pollTransfer(world, state);
            state.persistenceFailure = null;
            state.recovering = false;
            for (TileEntity tile : world.loadedTileEntityList) {
                if (tile instanceof TileEntityMultiblock<?> casing && casing.getManager() == this) casing.requestCacheRetry();
            }
        } catch (IOException error) {
            state.recovering = false;
            failPersistence(world, state, error);
        }
    }

    private void closeWorld(World world) {
        WorldCacheState<T> state = worlds.get(world);
        if (state == null) return;
        saveWorld(world);
        if (state.persistence != null) {
            if (state.recoverySnapshotFailure != null) {
                state.persistence.poll();
                retainPausedSnapshots(state);
                if (state.recoverySnapshotFailure != null) {
                    // Keep the original frozen structures until every latest snapshot is accepted.
                    // Already accepted snapshots may drain to make room for an unload retry.
                    if (state.persistence.state() == MultiblockPersistenceSession.State.READY) {
                        try { state.persistence.flush(); }
                        catch (IOException error) { Mekanism.logger.error("Paused multiblock save is still pending", error); }
                    }
                    return;
                }
            }
            try {
                state.persistence.close();
                closingSessions.add(state.persistence.closeTicket());
            }
            catch (IOException error) { failPersistence(world, state, error); return; }
        }
        worlds.remove(world);
    }

    /** Claims structure-wide processing for a persistent multiblock inventory in a dimension. */
    public boolean tryClaimServerTick(World world, String inventoryID, long gameTime) {
        Map<String, Long> dimensionClaims = state(world).serverTickClaims;
        Long previousTick = dimensionClaims.put(inventoryID, gameTime);
        return previousTick == null || previousTick != gameTime;
    }

    /** Claims the manager's canonical cache synchronization for one persistent structure and tick. */
    public boolean tryClaimCacheSync(World world, String inventoryID, long gameTime) {
        Map<String, Long> dimensionClaims = state(world).cacheSyncClaims;
        Long previousTick = dimensionClaims.put(inventoryID, gameTime);
        return previousTick == null || previousTick != gameTime;
    }

    /** Carries a processing claim across the cache ID replacement performed while reforming. */
    public void inheritServerTickClaim(World world, Collection<String> staleIds, String replacementId, long gameTime) {
        WorldCacheState<T> state = worlds.get(world);
        if (state == null || replacementId == null) {
            return;
        }
        Map<String, Long> dimensionClaims = state.serverTickClaims;
        for (String staleId : staleIds) {
            if (Objects.equals(dimensionClaims.get(staleId), gameTime)) {
                dimensionClaims.put(replacementId, gameTime);
                return;
            }
        }
    }

    /** Old immediate consumption cannot satisfy durable ownership; callers must use beginTransfer. */
    @Deprecated
    public MultiblockCache<T> pullInventory(World world, String id) {
        throw new UnsupportedOperationException("Use the durable multiblock transfer protocol");
    }

    @Deprecated
    public void invalidateInventory(World world, String id) {
        throw new UnsupportedOperationException("Multiblock consumption must commit with its target cache");
    }

    public boolean isInventoryInvalidated(World world, String id) {
        if (id == null) return false;
        try {
            MultiblockPersistenceSession session = persistence(world);
            if (session == null) throw new IllegalStateException("Multiblock history is still loading");
            MultiblockPersistenceSession.LegacyStatus status = session.legacyStatus(UUID.fromString(id));
            if (status == MultiblockPersistenceSession.LegacyStatus.AVAILABLE) return false;
            if (status == MultiblockPersistenceSession.LegacyStatus.INVALIDATED || status == MultiblockPersistenceSession.LegacyStatus.CONSUMED) return true;
            throw new IllegalStateException("Multiblock legacy status is " + status);
        } catch (IOException error) {
            throw new IllegalStateException("Multiblock history could not be read", error);
        }
    }
    /**
     * Grabs a unique inventory ID for a multiblock.
     *
     * @return unique inventory ID
     */
    public static String getUniqueInventoryID() {
        return UUID.randomUUID().toString();
    }

    public void tickSelf(World world) {
        WorldCacheState<T> state = worlds.get(world);
        if (state == null) return;
        saveWorld(world, false);
        Map<String, MultiblockCache<T>> inventories = state.inventories;
        Map<String, Long> inventoryTimestamps = state.timestamps;
        ArrayList<String> idsToKill = new ArrayList<>();
        for (Map.Entry<String, MultiblockCache<T>> entry : inventories.entrySet()) {
            String inventoryID = entry.getKey();
            Set<Coord4D> tilesToKill = new ObjectOpenHashSet<>();
            for (Coord4D obj : entry.getValue().locations) {
                if (obj.dimensionId != world.provider.getDimension() || !obj.exists(world)) {
                    continue;
                }
                TileEntity tileEntity = obj.getTileEntity(world);
                if (!(tileEntity instanceof TileEntityMultiblock<?> multiblock) || multiblock.getManager() != this) {
                    tilesToKill.add(obj);
                } else {
                    String structureId = getStructureId(multiblock);
                    String referencedId = structureId == null ? multiblock.cachedID : structureId;
                    if (!Objects.equals(referencedId, inventoryID)) {
                        tilesToKill.add(obj);
                    }
                }
            }
            if (!tilesToKill.isEmpty()) {
                entry.getValue().locations.removeAll(tilesToKill);
            }
            if (entry.getValue().locations.isEmpty() && !state.managed.contains(inventoryID) && state.pending == null) {
                idsToKill.add(inventoryID);
            }
        }
        idsToKill.forEach(id -> {
            inventories.remove(id);
            inventoryTimestamps.remove(id);
            state.runtimeAuthorities.remove(id);
        });
        long gameTime = world.getTotalWorldTime();
        //Keep this tick's claims until the following tick so late same-tick callbacks remain guarded.
        state.serverTickClaims.entrySet().removeIf(entry -> entry.getValue() != gameTime);
        state.cacheSyncClaims.entrySet().removeIf(entry -> entry.getValue() != gameTime);

    }

    public void updateCache(TileEntityMultiblock<T> tile) {
        boolean syncCanonical = tile.cachedID != null && tile.structure != null && Objects.equals(tile.cachedID, tile.structure.inventoryID) &&
              tile.getWorld() != null && tryClaimCacheSync(tile.getWorld(), tile.cachedID, tile.getWorld().getTotalWorldTime());
        updateCache(tile, syncCanonical);
    }

    public void updateCache(TileEntityMultiblock<T> tile, boolean syncCanonical) {
        if (tile.cachedID == null || tile.cacheFormat < 0) {
            return;
        }
        WorldCacheState<T> state = state(tile.getWorld());
        if (state.persistenceFailure != null) return;
        Map<String, MultiblockCache<T>> inventories = state.inventories;
        Map<String, Long> inventoryTimestamps = state.timestamps;
        if (tile.cacheFormat == 1 || state.managed.contains(tile.cachedID)) {
            // Reference-only casings must never import a second inventory from cachedData.
            MultiblockCache<T> authority = inventories.get(tile.cachedID);
            if (authority != null) authority.locations.add(Coord4D.get(tile));
            if (syncCanonical && tile.structure != null && state.bindings.get(tile.cachedID) == tile.structure) {
                try { snapshot(state, tile.cachedID, tile.structure); }
                catch (IOException error) { failPersistence(tile.getWorld(), state, error); }
            }
            return;
        }
        // Legacy replicas are provisional until the formation path has checked the read-only
        // history and durable consumption ledger. No MapStorage read is allowed in tile ticks.
        tile.cachedData.locations.add(Coord4D.get(tile));
        MultiblockCache<T> current = inventories.get(tile.cachedID);
        if (current == null) {
            inventories.put(tile.cachedID, tile.cachedData);
            inventoryTimestamps.put(tile.cachedID, tile.cachedDataTimestamp);
        } else {
            long currentTimestamp = inventoryTimestamps.getOrDefault(tile.cachedID, Long.MIN_VALUE);
            if (tile.structure == null && !state.runtimeAuthorities.contains(tile.cachedID) && tile.cachedDataTimestamp > currentTimestamp) {
                //During chunk loading prefer the newest persisted replica, independent of load order.
                tile.cachedData.locations.addAll(current.locations);
                inventories.put(tile.cachedID, tile.cachedData);
                inventoryTimestamps.put(tile.cachedID, tile.cachedDataTimestamp);
            } else {
                current.locations.add(Coord4D.get(tile));
                if (syncCanonical) {
                    //Keep the canonical runtime cache at the latest immediately visible shared state.
                    if (current != tile.cachedData) {
                        current.sync(tile.structure);
                    }
                    inventoryTimestamps.put(tile.cachedID, tile.getWorld() == null ? Math.max(currentTimestamp, tile.cachedDataTimestamp) :
                          tile.getWorld().getTotalWorldTime());
                }
            }
        }
        if (syncCanonical) state.runtimeAuthorities.add(tile.cachedID);
    }

}
