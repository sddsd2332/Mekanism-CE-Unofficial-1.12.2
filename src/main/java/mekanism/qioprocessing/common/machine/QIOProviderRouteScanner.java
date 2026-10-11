package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.planning.QIOProviderRouteCatalog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Bounded server-thread discovery of loaded machine Provider endpoints.
 */
public final class QIOProviderRouteScanner {
    public static final int MAX_TILES_PER_STEP = 32;
    private final Supplier<World[]> worldSupplier;
    private final QIOProviderRouteCatalog catalog;
    private World[] worlds;
    private List<TileEntity> tiles;
    private int worldIndex;
    private int tileIndex;
    private Set<String> activeSources = new HashSet<>();
    private Set<String> seenSources;

    public QIOProviderRouteScanner(MinecraftServer server, QIOProviderRouteCatalog catalog) {
        this(() -> server.worlds == null ? new World[0] : server.worlds.clone(), catalog);
    }

    public QIOProviderRouteScanner(Supplier<World[]> worldSupplier, QIOProviderRouteCatalog catalog) {
        this.worldSupplier = worldSupplier;
        this.catalog = catalog;
    }

    /**
     * Processes a bounded batch of loaded TileEntities and returns true only at pass completion.
     */
    public boolean step() {
        if (worlds == null) {
            worlds = worldSupplier.get();
            if (worlds == null) worlds = new World[0];
            worldIndex = 0;
            tileIndex = 0;
            tiles = null;
            seenSources = new HashSet<>();
        }
        int processed = 0;
        while (worldIndex < worlds.length && processed < MAX_TILES_PER_STEP) {
            World world = worlds[worldIndex];
            if (world == null) {
                worldIndex++;
                tiles = null;
                tileIndex = 0;
                continue;
            }
            if (tiles == null) {
                tiles = new ArrayList<>(world.loadedTileEntityList);
                tileIndex = 0;
            }
            if (tileIndex < tiles.size()) {
                TileEntity tile = tiles.get(tileIndex++);
                observe(world, tile);
                processed++;
                continue;
            }
            worldIndex++;
            tiles = null;
            tileIndex = 0;
        }
        if (worldIndex < worlds.length) {
            return false;
        }
        for (String sourceId : activeSources) {
            if (!seenSources.contains(sourceId)) {
                catalog.forget(sourceId);
            }
        }
        activeSources = seenSources;
        seenSources = null;
        worlds = null;
        tiles = null;
        return true;
    }

    public void reset() {
        worlds = null;
        tiles = null;
        worldIndex = 0;
        tileIndex = 0;
        if (seenSources != null) {
            seenSources.clear();
            seenSources = null;
        }
    }

    private void observe(World world, TileEntity tile) {
        if (tile == null || !world.isBlockLoaded(tile.getPos()) || world.getTileEntity(tile.getPos()) != tile) {
            return;
        }
        MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(tile);
        if (provider == null) {
            return;
        }
        if (QIOAutomationCapabilities.AUTOMATION_HOST == null ||
                !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) {
            return;
        }
        QIOAutomationHost host = tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
        QIOFrequencyReference reference = host == null ? null : host.getFrequencyReference();
        if (host == null || host.getEnabledMode() != QIOAutomationMode.SCHEDULED ||
                host.getState() != QIOAutomationHost.State.ACTIVE || reference == null ||
                reference.getFrequencyUUID() == null) {
            return;
        }
        if (!provider.isAvailable() || !provider.validateQIOConformance(
                QIOAutomationMode.SCHEDULED).isConformant()) {
            return;
        }
        String sourceId = sourceId(world, tile);
        seenSources.add(sourceId);
        catalog.observe(sourceId, reference.getFrequencyUUID(), provider.id(),
                provider.getRecipeSourceKey(), provider.getConfigurationRevision(),
                provider.getRecipeProfileScopeDiscriminator(), provider.getRecipeRoutes(),
                provider.getPorts());
    }

    private static String sourceId(World world, TileEntity tile) {
        return world.provider.getDimension() + ":" + tile.getPos().toLong();
    }
}
