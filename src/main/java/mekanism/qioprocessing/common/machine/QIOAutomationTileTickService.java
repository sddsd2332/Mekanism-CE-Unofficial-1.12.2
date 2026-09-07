package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.recipe.cache.AsyncMachinePlanSupport;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import net.minecraft.tileentity.TileEntity;
import mekanism.common.Mekanism;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Routes QIO machine work through the machine's own stable, pre-component tick. */
public final class QIOAutomationTileTickService {

    public static final QIOAutomationTileTickService INSTANCE =
          new QIOAutomationTileTickService();

    /**
     * Capability lookup is deliberately not used as the per-tick discovery mechanism. Forge
     * dispatches a capability query through every resolver owned by a container tile before it
     * reaches an attached provider, which made the optional QIO hook expensive for every machine.
     * The value is weak as an extra guard for unusual unload paths; normal lifecycle events remove
     * entries explicitly.
     */
    private final Map<TileEntity, WeakReference<DefaultQIOAutomationHost>> hosts = new WeakHashMap<>();

    private QIOAutomationTileTickService() {
    }

    void registerHost(@Nonnull TileEntity tile, @Nonnull DefaultQIOAutomationHost host) {
        if (host.tile() == tile) {
            hosts.put(tile, new WeakReference<>(host));
        }
    }

    void unregisterHost(@Nullable TileEntity tile) {
        if (tile != null && hosts.remove(tile) != null && tile instanceof TileEntityBasicBlock) {
            TileEntityBasicBlock machine = (TileEntityBasicBlock) tile;
            machine.invalidateProcessingState();
            machine.cancelPendingAsyncPlan(null);
            AsyncMachinePlanSupport.invalidateCompiledSource(machine);
        }
    }

    /** Only the departing World instance is removed, even if a dimension ID has been reused. */
    void unregisterWorld(@Nonnull World world) {
        List<TileEntity> removed = new ArrayList<>();
        for (TileEntity tile : hosts.keySet()) {
            if (tile.getWorld() == world) removed.add(tile);
        }
        removed.forEach(this::unregisterHost);
    }

    void unregisterChunk(@Nonnull Chunk chunk) {
        new ArrayList<>(chunk.getTileEntityMap().values()).forEach(this::unregisterHost);
    }

    /** Restores reused Tile instances at a lifecycle boundary, never by probing capabilities each tick. */
    void registerChunk(@Nonnull Chunk chunk) {
        for (TileEntity tile : new ArrayList<>(chunk.getTileEntityMap().values())) {
            if (tile.isInvalid() || tile.getWorld() != chunk.getWorld() || chunk.getWorld().isRemote ||
                getRegisteredHost(tile) != null || QIOAutomationCapabilities.AUTOMATION_HOST == null) continue;
            try {
                if (!tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) continue;
                QIOAutomationHost exposed = tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
                if (exposed instanceof DefaultQIOAutomationHost && ((DefaultQIOAutomationHost) exposed).tile() == tile) {
                    DefaultQIOAutomationHost host = (DefaultQIOAutomationHost) exposed;
                    registerHost(tile, host);
                    QIOAutomationDeviceRegistry.INSTANCE.trackPending(host);
                }
            } catch (RuntimeException | LinkageError error) {
                Mekanism.logger.warn("Unable to restore QIO machine tick registration for {} at {}",
                      tile.getClass().getName(), tile.getPos(), error);
            }
        }
    }

    /** Full-server shutdown only; world and chunk events use scoped removal. */
    void clearHosts() {
        new ArrayList<>(hosts.keySet()).forEach(this::unregisterHost);
    }

    @Nullable
    public DefaultQIOAutomationHost getRegisteredHost(@Nonnull TileEntity tile) {
        WeakReference<DefaultQIOAutomationHost> reference = hosts.get(tile);
        if (reference == null) {
            return null;
        }
        DefaultQIOAutomationHost host = reference.get();
        if (host == null || host.tile() != tile) {
            hosts.remove(tile);
            return null;
        }
        return host;
    }

    public void tick(TileEntityBasicBlock tile) {
        if (tile == null || tile.isInvalid() || tile.getWorld() == null || tile.getWorld().isRemote) {
            return;
        }
        DefaultQIOAutomationHost host = getRegisteredHost(tile);
        if (host == null) {
            return;
        }
        long gameTick = Math.max(0, tile.getWorld().getTotalWorldTime());
        // QIO route selection and cross-machine transfers are main-thread work,
        // but they are not a local container transaction. Holding the tile lock
        // across this call made external two-phase simulations observe a false
        // busy result and could deadlock two machines transferring to each other.
        tick(host, tile, gameTick);
    }

    void tick(DefaultQIOAutomationHost host, TileEntity tile, long gameTick) {
        if (host == null || tile == null || host.tile() != tile) {
            return;
        }
        QIOAutomationMode mode = host.getEnabledMode();
        if (mode == null) {
            return;
        }
        try {
            if (mode == QIOAutomationMode.OUTPUT_ONLY) {
                QIOAutomaticOutputService.INSTANCE.tickDevice(host, tile, gameTick);
            } else {
                // New order allocation remains frequency-wide. An operation which already owns
                // this machine must collect its output here, before TileComponentEjector runs.
                // The ordinary ejector is deliberately left active: the optional QIO guard
                // filters only the physical output containers covered by the active lease.
                QIOProcessingExecutionService.INSTANCE.onMachinePreComponentTick(host);
            }
        } catch (RuntimeException e) {
            if (mode == QIOAutomationMode.SCHEDULED || mode == QIOAutomationMode.PASSIVE) {
                String message = e.getMessage();
                host.pauseProcessingOutputCollection("pre-ejection hook failed: " +
                      (message == null || message.isEmpty() ? e.getClass().getSimpleName() :
                            message.substring(0, Math.min(384, message.length()))));
            }
            Mekanism.logger.error("QIO machine tick failed for device {}; processing collection " +
                  "was paused when applicable", host.getPersistentDeviceUUID(), e);
        }
    }
}
