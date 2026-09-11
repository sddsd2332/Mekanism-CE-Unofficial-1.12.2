package mekanism.qioprocessing.common.machine;

import mekanism.common.Mekanism;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.cache.MachineStressFixtures;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.stress.MachineStressLifecycle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real Forge dimension/chunk lifecycle checks in the isolated machine smoke world. */
public final class QIOHostLifecycleSmoke implements MachineStressLifecycle {
    private final QIOAutomationTileTickService service = QIOAutomationTileTickService.INSTANCE;
    private final Map<TileEntityBasicBlock, DefaultQIOAutomationHost> surviving = new IdentityHashMap<>();
    private final Map<DefaultQIOAutomationHost, Boolean> originalPause = new IdentityHashMap<>();
    private final List<MachineStressFixtures.Entry> entries;
    private TileEntityBasicBlock departingTile;
    private TileEntityBasicBlock chunkTile;
    private Chunk departingChunk;
    private long departingState;
    private int stage;
    private boolean dimensionPassed;
    private boolean chunkPassed;

    public QIOHostLifecycleSmoke(List<MachineStressFixtures.Entry> entries) {
        this.entries = entries;
    }

    public boolean advance(MinecraftServer server) {
        WorldServer overworld = server.getWorld(0);
        if (stage == 0) {
            for (MachineStressFixtures.Entry entry : entries) {
                DefaultQIOAutomationHost host = service.getRegisteredHost(entry.tile);
                if (host != null) {
                    surviving.put(entry.tile, host);
                    originalPause.put(host, host.isManagementPaused());
                    host.setManagementPaused(!host.isManagementPaused());
                }
            }
            check(!surviving.isEmpty(), "No real QIO hosts were attached to fixture machines");
            DimensionManager.initDimension(-1);
            WorldServer departing = DimensionManager.getWorld(-1);
            check(departing != null && departing != overworld, "Nether did not load");
            departingTile = placeMachine(departing, new BlockPos(0, 100, 0));
            departingState = departingTile.getProcessingStateVersion();
            check(service.getRegisteredHost(departingTile) != null, "Nether machine has no QIO index entry");
            DimensionManager.unloadWorld(-1);
            check(DimensionManager.isWorldQueuedToUnload(-1), "Forge refused isolated Nether unload");
            Mekanism.logger.info("QIO_LIFECYCLE_SMOKE dimension unload queued; survivingHosts={}", surviving.size());
            stage = 1;
            return false;
        }
        if (stage == 1) {
            if (DimensionManager.getWorld(-1) != null) return false;
            check(service.getRegisteredHost(departingTile) == null, "Unloaded dimension kept its QIO host");
            check(!departingTile.hasPendingAsyncPlan() && departingTile.getProcessingStateVersion() > departingState,
                  "Unloaded dimension did not invalidate machine plans");
            verifySurvivors();
            dimensionPassed = true;
            chunkTile = placeMachine(overworld, new BlockPos(1024, 100, 1024));
            check(service.getRegisteredHost(chunkTile) != null, "Chunk fixture has no QIO index entry");
            departingChunk = overworld.getChunk(chunkTile.getPos());
            overworld.getChunkProvider().queueUnload(departingChunk);
            check(departingChunk.unloadQueued, "Forge refused isolated chunk unload");
            Mekanism.logger.info("QIO_LIFECYCLE_SMOKE dimension isolation passed; chunk unload queued");
            stage = 2;
            return false;
        }
        if (stage == 2) {
            // getLoadedChunk() cancels a queued unload; inspect the collection without requesting the chunk.
            if (overworld.getChunkProvider().getLoadedChunks().contains(departingChunk)) return false;
            check(service.getRegisteredHost(chunkTile) == null, "Unloaded chunk kept its QIO host");
            check(!chunkTile.hasPendingAsyncPlan(), "Unloaded chunk kept a pending machine plan");
            overworld.getChunk(departingChunk.x, departingChunk.z);
            TileEntityBasicBlock reloaded = (TileEntityBasicBlock) overworld.getTileEntity(chunkTile.getPos());
            check(reloaded != null && reloaded != chunkTile, "Chunk did not restore its saved machine");
            DefaultQIOAutomationHost host = service.getRegisteredHost(reloaded);
            check(host != null && host.tile() == reloaded && host == reloaded.getCapability(
                  QIOAutomationCapabilities.AUTOMATION_HOST, null), "Reloaded machine did not register its actual capability host");
            verifySurvivors();
            originalPause.forEach(DefaultQIOAutomationHost::setManagementPaused);
            chunkPassed = true;
            stage = 3;
            Mekanism.logger.info("QIO_LIFECYCLE_SMOKE chunk reload passed; survivingHosts={}", surviving.size());
        }
        return stage == 3;
    }

    private void verifySurvivors() {
        surviving.forEach((tile, host) -> {
            check(service.getRegisteredHost(tile) == host, "Other dimension lost a live QIO host");
            check(host.getOwnershipRevision() > 0 && tile.getAsyncLeaseVersion() == host.getLeaseRevision() &&
                  tile.getAsyncPortOwnershipVersion() == host.getPortOwnershipRevision(),
                  "Other dimension lost its live lease/port revisions");
        });
    }

    private static TileEntityBasicBlock placeMachine(WorldServer world, BlockPos pos) {
        MachineType type = MachineType.ENRICHMENT_CHAMBER;
        world.setBlockState(pos, type.typeBlock.getBlock().getStateFromMeta(type.meta), 2);
        TileEntityBasicBlock tile = (TileEntityBasicBlock) world.getTileEntity(pos);
        check(tile != null, "Registered block did not create a lifecycle fixture machine");
        return tile;
    }

    public Map<String, Object> report() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("otherDimensionHostsPreserved", dimensionPassed);
        report.put("chunkUnloadReloadPassed", chunkPassed);
        report.put("survivingHosts", surviving.size());
        report.put("unloadedDimension", -1);
        report.put("coverage", "Real lifecycle index and version checks; not end-to-end QIO order settlement");
        return report;
    }

    private static void check(boolean condition, String message) {
        MachineStressFixtures.check(condition, message);
    }
}
