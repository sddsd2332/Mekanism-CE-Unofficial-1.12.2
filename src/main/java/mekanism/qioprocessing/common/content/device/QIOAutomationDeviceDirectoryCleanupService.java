package mekanism.qioprocessing.common.content.device;

import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.content.QIOAutomationUpgradeSupport;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.machine.QIOAutomationCapabilities;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import mekanism.qioprocessing.common.tile.QIOProcessingTerminal;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Distinguishes a temporarily unloaded endpoint from a device which left a frequency. */
/**
 * QIO 处理模块中的 QIOAutomationDeviceDirectoryCleanupService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationDeviceDirectoryCleanupService {

    private static final int MAX_CHUNKS_PER_TICK = 8;
    private static final int MAX_FALLBACK_RECORDS = 512;
    private static final Set<ChunkKey> pendingChunks = new LinkedHashSet<>();

    private QIOAutomationDeviceDirectoryCleanupService() {
    }

    public static synchronized void queueChunk(@Nonnull World world, int chunkX, int chunkZ) {
        if (!world.isRemote) {
            pendingChunks.add(new ChunkKey(world.provider.getDimension(), chunkX, chunkZ));
        }
    }

    public static synchronized void discardDimension(int dimension) {
        pendingChunks.removeIf(key -> key.dimension == dimension);
    }

    public static synchronized void discardChunk(@Nonnull World world, int chunkX, int chunkZ) {
        pendingChunks.remove(new ChunkKey(world.provider.getDimension(), chunkX, chunkZ));
    }

    public static synchronized void clear() {
        pendingChunks.clear();
    }

    /** Runs a bounded amount of loaded-chunk cleanup on the server thread. */
    public static void processPendingChunks() {
        if (!QIOProcessingNetworkManager.INSTANCE.isLoaded()) {
            return;
        }
        List<ChunkKey> batch = new ArrayList<>(MAX_CHUNKS_PER_TICK);
        synchronized (QIOAutomationDeviceDirectoryCleanupService.class) {
            Iterator<ChunkKey> iterator = pendingChunks.iterator();
            while (iterator.hasNext() && batch.size() < MAX_CHUNKS_PER_TICK) {
                batch.add(iterator.next());
                iterator.remove();
            }
        }
        for (ChunkKey key : batch) {
            World world = DimensionManager.getWorld(key.dimension);
            if (world == null || world.isRemote ||
                !world.isBlockLoaded(new BlockPos(key.chunkX << 4, 0,
                      key.chunkZ << 4), false)) {
                continue;
            }
            for (QIOProcessingNetworkData network :
                  QIOProcessingNetworkManager.INSTANCE.getNetworks()) {
                pruneLoadedStaleRecordsInChunk(network, world, key.chunkX, key.chunkZ);
            }
        }
    }

    /**
     * Repairs persisted directory entries only when their coordinate is already loaded. An
     * unloaded coordinate is deliberately treated as an intact offline device.
     */
    public static int pruneLoadedStaleRecords(@Nonnull QIOProcessingNetworkData network) {
        int removed = 0;
        int checked = 0;
        for (QIOAutomationDeviceSnapshot snapshot :
              network.getAutomationDevices().getDevices()) {
            if (checked++ >= MAX_FALLBACK_RECORDS) {
                break;
            }
            QIOAutomationDeviceLocation location = snapshot.getLocation();
            World world = DimensionManager.getWorld(location.dimension());
            if (world == null || world.isRemote ||
                !world.isBlockLoaded(location.position(), false)) {
                continue;
            }
            TileEntity tile = world.getTileEntity(location.position());
            try {
                if (matchesMembership(snapshot, tile, network.getFrequencyUUID())) {
                    continue;
                }
            } catch (RuntimeException ignored) {
                // A foreign capability failure cannot prove that the physical device is gone.
                continue;
            }
            if (network.forgetAutomationDeviceMembership(snapshot.getDeviceUUID(), location)) {
                removed++;
            }
        }
        return removed;
    }

    private static int pruneLoadedStaleRecordsInChunk(@Nonnull QIOProcessingNetworkData network,
          @Nonnull World world, int chunkX, int chunkZ) {
        int removed = 0;
        for (QIOAutomationDeviceSnapshot snapshot : network.getAutomationDevices()
              .getDevicesInChunk(world.provider.getDimension(), chunkX, chunkZ)) {
            QIOAutomationDeviceLocation location = snapshot.getLocation();
            if (!world.isBlockLoaded(location.position(), false)) {
                continue;
            }
            TileEntity tile = world.getTileEntity(location.position());
            try {
                if (matchesMembership(snapshot, tile, network.getFrequencyUUID())) {
                    continue;
                }
            } catch (RuntimeException ignored) {
                continue;
            }
            if (network.forgetAutomationDeviceMembership(snapshot.getDeviceUUID(), location)) {
                removed++;
            }
        }
        return removed;
    }

    /** Removes the currently loaded tile from its processing-frequency directory. */
    public static boolean forgetLoadedTile(@Nullable TileEntity tile) {
        if (tile == null || tile.getWorld() == null || tile.getWorld().isRemote) {
            return false;
        }
        UUID deviceUUID;
        UUID frequencyUUID;
        if (tile instanceof QIOProcessingTerminal terminal) {
            QIOFrequencyReference reference = terminal.getFrequencyReference();
            if (reference == null) {
                return false;
            }
            deviceUUID = terminal.getPersistentTerminalUUID();
            frequencyUUID = reference.getFrequencyUUID();
        } else if (tile instanceof QIOCraftingProcessor processor) {
            QIOFrequency frequency = processor.getQIOFrequency();
            if (frequency == null) {
                return false;
            }
            deviceUUID = processor.getProcessorState().getProcessorUUID();
            frequencyUUID = frequency.getFrequencyUUID();
        } else {
            QIOAutomationHost host = automationHost(tile);
            QIOFrequencyReference reference = host == null ? null :
                  host.getFrequencyReference();
            if (host == null || reference == null) {
                return false;
            }
            deviceUUID = host.getPersistentDeviceUUID();
            frequencyUUID = reference.getFrequencyUUID();
        }
        QIOProcessingNetworkData network =
              QIOProcessingNetworkManager.INSTANCE.get(frequencyUUID);
        return network != null && network.forgetAutomationDeviceMembership(deviceUUID,
              QIOAutomationDeviceLocation.of(tile.getWorld(), tile.getPos()));
    }

    private static boolean matchesMembership(QIOAutomationDeviceSnapshot snapshot,
          @Nullable TileEntity tile, UUID frequencyUUID) {
        if (tile == null || tile.isInvalid()) {
            return false;
        }
        return switch (snapshot.getKind()) {
            case AUTOMATION_MACHINE -> matchesAutomationMachine(snapshot, tile,
                  frequencyUUID);
            case CRAFTING_PROCESSOR -> matchesCraftingProcessor(snapshot, tile,
                  frequencyUUID);
            case TERMINAL -> matchesTerminal(snapshot, tile, frequencyUUID);
        };
    }

    private static boolean matchesAutomationMachine(QIOAutomationDeviceSnapshot snapshot,
          TileEntity tile, UUID frequencyUUID) {
        QIOAutomationHost host = automationHost(tile);
        if (host == null || !snapshot.getDeviceUUID().equals(
              host.getPersistentDeviceUUID())) {
            return false;
        }
        QIOFrequencyReference reference = host.getFrequencyReference();
        if (reference == null || !frequencyUUID.equals(reference.getFrequencyUUID())) {
            return false;
        }
        for (QIOAutomationMode mode : QIOAutomationMode.values()) {
            if (QIOAutomationUpgradeSupport.isModeInstalled(tile, mode)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCraftingProcessor(
          QIOAutomationDeviceSnapshot snapshot, TileEntity tile, UUID frequencyUUID) {
        if (!(tile instanceof QIOCraftingProcessor processor) ||
            !snapshot.getDeviceUUID().equals(
                  processor.getProcessorState().getProcessorUUID())) {
            return false;
        }
        QIOFrequency frequency = processor.getQIOFrequency();
        return frequency != null && frequencyUUID.equals(frequency.getFrequencyUUID());
    }

    private static boolean matchesTerminal(QIOAutomationDeviceSnapshot snapshot,
          TileEntity tile, UUID frequencyUUID) {
        if (!(tile instanceof QIOProcessingTerminal terminal) ||
            !snapshot.getDeviceUUID().equals(terminal.getPersistentTerminalUUID())) {
            return false;
        }
        QIOFrequencyReference reference = terminal.getFrequencyReference();
        return reference != null && frequencyUUID.equals(reference.getFrequencyUUID());
    }

    @Nullable
    private static QIOAutomationHost automationHost(TileEntity tile) {
        return QIOAutomationCapabilities.AUTOMATION_HOST == null ||
              !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null) ? null :
              tile.getCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null);
    }

    private static final class ChunkKey {

        private final int dimension;
        private final int chunkX;
        private final int chunkZ;

        private ChunkKey(int dimension, int chunkX, int chunkZ) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof ChunkKey other &&
                  dimension == other.dimension && chunkX == other.chunkX &&
                  chunkZ == other.chunkZ;
        }

        @Override
        public int hashCode() {
            int result = dimension;
            result = 31 * result + chunkX;
            return 31 * result + chunkZ;
        }
    }
}
