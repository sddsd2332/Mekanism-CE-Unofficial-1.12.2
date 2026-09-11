package mekanism.qioprocessing.common.machine;

import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.api.IAsyncMachinePlanner;
import mekanism.api.IAsyncPlanCalculator;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import net.minecraft.init.Bootstrap;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.GameType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveHandlerMP;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

class QIOAutomationTileTickServiceTest {

    private final QIOAutomationTileTickService service = QIOAutomationTileTickService.INSTANCE;
    private Capability<QIOAutomationHost> previousCapability;

    @BeforeAll
    static void bootstrap() { Bootstrap.register(); }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void exposeTestCapability() throws Exception {
        previousCapability = QIOAutomationCapabilities.AUTOMATION_HOST;
        Constructor<Capability> constructor = Capability.class.getDeclaredConstructor(
              String.class, Capability.IStorage.class, Callable.class);
        constructor.setAccessible(true);
        QIOAutomationCapabilities.AUTOMATION_HOST = constructor.newInstance("test:qio_lifecycle", null,
              (Callable<QIOAutomationHost>) DefaultQIOAutomationHost::new);
    }

    @AfterEach
    void clearIndex() {
        service.clearHosts();
        QIOAutomationDeviceRegistry.INSTANCE.shutdown();
        QIOAutomationCapabilities.AUTOMATION_HOST = previousCapability;
    }

    @Test
    void registeredHostIsResolvedWithoutCapabilityDispatch() {
        TileEntityBasicBlock tile = new TestTile();
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(tile);

        service.registerHost(tile, host);

        assertSame(host, service.getRegisteredHost(tile));
    }

    @Test
    void hostForAnotherTileIsRejected() {
        TileEntityBasicBlock tile = new TestTile();
        DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(new TestTile());

        service.registerHost(tile, host);

        assertNull(service.getRegisteredHost(tile));
    }

    @Test
    void unregisterAndClearRemoveHosts() {
        TileEntityBasicBlock first = new TestTile();
        TileEntityBasicBlock second = new TestTile();
        service.registerHost(first, new DefaultQIOAutomationHost(first));
        service.registerHost(second, new DefaultQIOAutomationHost(second));

        service.unregisterHost(first);
        assertNull(service.getRegisteredHost(first));

        service.clearHosts();
        assertNull(service.getRegisteredHost(second));
    }

    @Test
    void worldUnloadPreservesOtherWorldsEvenWithTheSameDimensionId() throws Exception {
        TestWorld unloading = new TestWorld(false, 7);
        TestWorld surviving = new TestWorld(false, 7);
        TestTile removed = tile(unloading, new BlockPos(0, 64, 0));
        TestTile kept = tile(surviving, removed.getPos());
        removed.host.setManagementPaused(true);
        kept.host.setManagementPaused(true);
        reserve(removed);
        reserve(kept);
        long removedVersion = removed.getProcessingStateVersion();
        long keptVersion = kept.getProcessingStateVersion();
        long keptOwnership = kept.host.getOwnershipRevision();

        QIOAutomationEventHandler.INSTANCE.onWorldUnload(new WorldEvent.Unload(unloading));

        assertNull(service.getRegisteredHost(removed));
        assertFalse(removed.hasPendingAsyncPlan());
        assertEquals(1, removed.discards);
        assertTrue(removed.getProcessingStateVersion() > removedVersion);
        assertSame(kept.host, service.getRegisteredHost(kept));
        assertTrue(kept.hasPendingAsyncPlan());
        assertEquals(keptVersion, kept.getProcessingStateVersion());
        assertEquals(keptOwnership, service.getRegisteredHost(kept).getLeaseRevision());
        assertEquals(keptOwnership, service.getRegisteredHost(kept).getPortOwnershipRevision());
    }

    @Test
    void clientWorldUnloadDoesNotTouchServerHosts() throws Exception {
        TestTile server = tile(new TestWorld(false, 0), BlockPos.ORIGIN);
        reserve(server);
        QIOAutomationEventHandler.INSTANCE.onWorldUnload(new WorldEvent.Unload(new TestWorld(true, 0)));
        assertSame(server.host, service.getRegisteredHost(server));
        assertTrue(server.hasPendingAsyncPlan());
    }

    @Test
    void chunkUnloadAndReloadRestoreTheSameHostWithoutPerTickCapabilityQueries() throws Exception {
        TestWorld world = new TestWorld(false, 0);
        TestTile unloaded = tile(world, new BlockPos(1, 64, 1));
        TestTile unaffected = tile(world, new BlockPos(32, 64, 1));
        Chunk chunk = new Chunk(world, 0, 0);
        chunk.getTileEntityMap().put(unloaded.getPos(), unloaded);
        reserve(unloaded);
        unloaded.host.setManagementPaused(true);
        long ownership = unloaded.host.getOwnershipRevision();
        QIOAutomationEventHandler.INSTANCE.onChunkUnload(new ChunkEvent.Unload(chunk));
        assertNull(service.getRegisteredHost(unloaded));
        assertFalse(unloaded.hasPendingAsyncPlan());
        assertSame(unaffected.host, service.getRegisteredHost(unaffected));

        QIOAutomationEventHandler.INSTANCE.onChunkLoad(new ChunkEvent.Load(chunk));
        assertSame(unloaded.host, service.getRegisteredHost(unloaded));
        assertEquals(ownership, service.getRegisteredHost(unloaded).getLeaseRevision());
        int queries = unloaded.capabilityQueries;
        assertTrue(queries > 0);
        for (int tick = 0; tick < 5; tick++) service.tick(unloaded);
        QIOAutomationEventHandler.INSTANCE.onChunkLoad(new ChunkEvent.Load(chunk));
        assertEquals(queries, unloaded.capabilityQueries);
    }

    @Test
    void removingOldTileDoesNotRemoveItsReplacementAtTheSamePosition() {
        TestWorld world = new TestWorld(false, 0);
        TestTile old = tile(world, BlockPos.ORIGIN);
        TestTile replacement = tile(world, BlockPos.ORIGIN);
        service.unregisterHost(old);
        assertNull(service.getRegisteredHost(old));
        assertSame(replacement.host, service.getRegisteredHost(replacement));
    }

    @Test
    void shutdownClearsHostsAndPendingPlansAcrossAllWorlds() throws Exception {
        TestTile first = tile(new TestWorld(false, 0), BlockPos.ORIGIN);
        TestTile second = tile(new TestWorld(false, -1), BlockPos.ORIGIN);
        reserve(first);
        reserve(second);
        QIOAutomationDeviceRegistry.INSTANCE.shutdown();
        assertNull(service.getRegisteredHost(first));
        assertNull(service.getRegisteredHost(second));
        assertFalse(first.hasPendingAsyncPlan());
        assertFalse(second.hasPendingAsyncPlan());
        assertEquals(1, first.discards);
        assertEquals(1, second.discards);
    }

    private TestTile tile(TestWorld world, BlockPos pos) {
        TestTile tile = new TestTile();
        tile.setWorld(world);
        tile.setPos(pos);
        service.registerHost(tile, tile.host);
        return tile;
    }

    private static void reserve(TestTile tile) throws Exception {
        Class<?> pendingType = Class.forName(TileEntityBasicBlock.class.getName() + "$PendingAsyncPlan");
        Constructor<?> constructor = pendingType.getDeclaredConstructor(Object.class, long.class, long.class, long.class);
        constructor.setAccessible(true);
        Field field = TileEntityBasicBlock.class.getDeclaredField("pendingAsyncPlan");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Object> pending = (AtomicReference<Object>) field.get(tile);
        pending.set(constructor.newInstance(1, tile.getProcessingStateVersion(), 0L, 0L));
    }

    private static final class TestTile extends TileEntityBasicBlock implements IAsyncMachinePlanner<Integer, Integer> {
        private final DefaultQIOAutomationHost host = new DefaultQIOAutomationHost(this);
        private int capabilityQueries;
        private int discards;
        public Integer captureSnapshot() { return 1; }
        public Integer calculatePlan(Integer snapshot) { return snapshot; }
        public IAsyncPlanCalculator<Integer, Integer> getAsyncPlanCalculator() { return value -> value; }
        public void commitPlan(Integer snapshot, Integer plan) { fail("Unloaded plans must never commit"); }
        public void onPlanDiscarded(Integer snapshot, Integer plan, Throwable cause) { discards++; }
        @Override public void markDirty() { }
        @Override public boolean hasCapability(Capability<?> capability, EnumFacing side) {
            capabilityQueries++;
            return capability == QIOAutomationCapabilities.AUTOMATION_HOST;
        }
        @Override public <T> T getCapability(Capability<T> capability, EnumFacing side) {
            capabilityQueries++;
            return capability == QIOAutomationCapabilities.AUTOMATION_HOST ?
                  QIOAutomationCapabilities.AUTOMATION_HOST.cast(host) : null;
        }
    }

    private static final class TestWorld extends World {
        private TestWorld(boolean client, int dimension) {
            super(new SaveHandlerMP(), new WorldInfo(new WorldSettings(0, GameType.SURVIVAL, false, false,
                  WorldType.FLAT), "qio-lifecycle"), new WorldProviderSurface(), new Profiler(), client);
            provider.setDimension(dimension);
        }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return false; }
    }
}
