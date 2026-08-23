package mekanism.qioprocessing.common.processor;

import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager.IsolatedNetworkException;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.content.processor.QIOCraftingProcessorState;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Online directory for workbench processors; duplicate persistent UUIDs are fail-closed. */
/**
 * QIO 处理模块中的 QIOCraftingProcessorDeviceRegistry 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOCraftingProcessorDeviceRegistry {

    public static final QIOCraftingProcessorDeviceRegistry INSTANCE =
          new QIOCraftingProcessorDeviceRegistry();

    private final Map<UUID, Set<QIOCraftingProcessor>> processors = new LinkedHashMap<>();
    private final Map<QIOCraftingProcessor, UUID> identities = new IdentityHashMap<>();
    private final Map<QIOCraftingProcessor, PublishedEndpoint> published = new IdentityHashMap<>();
    private final Set<UUID> directoryRejectedDevices = new java.util.LinkedHashSet<>();

    private QIOCraftingProcessorDeviceRegistry() {
    }

    public synchronized void register(@Nonnull QIOCraftingProcessor processor) {
        UUID uuid = processor.getProcessorState().getProcessorUUID();
        UUID previous = identities.get(processor);
        if (previous != null && !previous.equals(uuid)) {
            forgetPublished(processor);
            remove(previous, processor);
        }
        identities.put(processor, uuid);
        processors.computeIfAbsent(uuid,
              ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(processor);
        refreshIdentityGroup(uuid);
    }

    public synchronized void unregister(@Nonnull QIOCraftingProcessor processor) {
        UUID uuid = identities.remove(processor);
        if (uuid != null) {
            publishOffline(processor);
            remove(uuid, processor);
            refreshIdentityGroup(uuid);
        }
    }

    /** Called from TileEntity.invalidate; a loaded invalid tile has been physically replaced. */
    public synchronized void unregisterRemoved(@Nonnull QIOCraftingProcessor processor) {
        UUID uuid = identities.remove(processor);
        if (uuid != null) {
            if (isLoadedInvalidation(processor)) {
                forgetPublished(processor);
            } else {
                publishOffline(processor);
            }
            remove(uuid, processor);
            refreshIdentityGroup(uuid);
        }
    }

    public synchronized void refresh(@Nonnull QIOCraftingProcessor processor) {
        UUID uuid = identities.get(processor);
        if (uuid != null) {
            publish(processor, isIdentityConflicted(uuid));
        }
    }

    @Nonnull
    public synchronized List<LoadedProcessor> getAvailable(@Nonnull UUID frequencyUUID) {
        List<LoadedProcessor> result = new ArrayList<>();
        for (Map.Entry<UUID, Set<QIOCraftingProcessor>> entry : processors.entrySet()) {
            if (entry.getValue().size() != 1) {
                continue;
            }
            QIOCraftingProcessor processor = entry.getValue().iterator().next();
            if (!isAvailable(processor, frequencyUUID)) {
                continue;
            }
            result.add(new LoadedProcessor(entry.getKey(), processor));
        }
        result.sort(Comparator.comparingInt(LoadedProcessor::dimension)
              .thenComparing(LoadedProcessor::position)
              .thenComparing(loaded -> loaded.processorUUID().toString()));
        return Collections.unmodifiableList(result);
    }

    @Nullable
    public synchronized LoadedProcessor findAvailable(@Nonnull UUID processorUUID,
          @Nonnull UUID frequencyUUID) {
        Set<QIOCraftingProcessor> matches = processors.get(processorUUID);
        if (matches == null || matches.size() != 1) {
            return null;
        }
        QIOCraftingProcessor processor = matches.iterator().next();
        return isAvailable(processor, frequencyUUID) ?
              new LoadedProcessor(processorUUID, processor) : null;
    }

    @Nullable
    public synchronized LoadedProcessor findLoaded(@Nonnull UUID processorUUID,
          @Nonnull UUID frequencyUUID) {
        Set<QIOCraftingProcessor> matches = processors.get(processorUUID);
        if (matches == null || matches.size() != 1) return null;
        QIOCraftingProcessor processor = matches.iterator().next();
        return isLoaded(processor, frequencyUUID) ?
              new LoadedProcessor(processorUUID, processor) : null;
    }

    public synchronized boolean isIdentityConflicted(@Nonnull UUID processorUUID) {
        Set<QIOCraftingProcessor> matches = processors.get(processorUUID);
        return matches != null && matches.size() > 1;
    }

    public synchronized void clear() {
        processors.clear();
        identities.clear();
        published.clear();
        directoryRejectedDevices.clear();
    }

    @SubscribeEvent
    public synchronized void onWorldSave(WorldEvent.Save event) {
        if (event.getWorld().isRemote) {
            return;
        }
        int dimension = event.getWorld().provider.getDimension();
        for (Set<QIOCraftingProcessor> matches : processors.values()) {
            for (QIOCraftingProcessor processor : matches) {
                if (processor.getWorld() == event.getWorld() &&
                      processor.getWorld().provider.getDimension() == dimension) {
                    if (processor.getProcessorState().confirmSettledLanesPersisted() &&
                          processor.getFrequencyReference() != null) {
                        QIOProcessingExecutionService.INSTANCE.wakeDevice(
                              processor.getFrequencyReference().getFrequencyUUID(),
                              processor.getProcessorState().getProcessorUUID());
                    }
                }
            }
        }
    }

    private void remove(UUID uuid, QIOCraftingProcessor processor) {
        Set<QIOCraftingProcessor> matches = processors.get(uuid);
        if (matches != null) {
            matches.remove(processor);
            if (matches.isEmpty()) {
                processors.remove(uuid);
            }
        }
    }

    private void refreshIdentityGroup(UUID uuid) {
        Set<QIOCraftingProcessor> matches = processors.get(uuid);
        if (matches == null) {
            return;
        }
        boolean conflicted = matches.size() > 1;
        for (QIOCraftingProcessor processor : matches) {
            publish(processor, conflicted);
        }
    }

    private void publish(QIOCraftingProcessor processor, boolean identityConflict) {
        if (processor == null || processor.isInvalid() || processor.getWorld() == null ||
            processor.getWorld().isRemote ||
            !processor.getWorld().isBlockLoaded(processor.getPos(), false) ||
            processor.getWorld().getTileEntity(processor.getPos()) != processor) {
            publishOffline(processor);
            return;
        }
        QIOFrequency frequency = processor.getQIOFrequency();
        if (frequency == null || !frequency.isValid() || frequency.isRemoved()) {
            forgetPublished(processor);
            return;
        }
        if (!QIOProcessingNetworkManager.INSTANCE.isLoaded()) {
            publishOffline(processor);
            return;
        }
        UUID processorUUID = processor.getProcessorState().getProcessorUUID();
        QIOAutomationDeviceLocation location = QIOAutomationDeviceLocation.of(
              processor.getWorld(), processor.getPos());
        PublishedEndpoint previous = published.get(processor);
        if (previous != null && (!previous.frequencyUUID.equals(frequency.getFrequencyUUID()) ||
            !previous.location.equals(location) || !previous.deviceUUID.equals(processorUUID))) {
            forgetPublished(previous);
        }
        PublishedEndpoint endpoint = new PublishedEndpoint(processorUUID,
              frequency.getFrequencyUUID(), location);
        published.put(processor, endpoint);
        Block block = processor.getBlockType();
        ResourceLocation blockId = block == null ? null : block.getRegistryName();
        QIOCraftingProcessorState state = processor.getProcessorState();
        boolean recoveryPending = state.hasRecoveryPending();
        String stateName = identityConflict ? "IDENTITY_CONFLICT" :
              recoveryPending ? "DATA_ERROR" : state.getState().name();
        if (QIOProcessingNetworkManager.INSTANCE.getIsolationStatus(
              frequency.getFrequencyUUID()) != null) {
            directoryRejectedDevices.add(processorUUID);
            return;
        }
        try {
            QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.getOrCreate(
                  frequency.getFrequencyUUID(), new QIOFrequencyIdentitySnapshot(
                        frequency.getName(), frequency.getOwner(), frequency.getSecurity()));
            QIOAutomationDeviceSnapshot snapshot = new QIOAutomationDeviceSnapshot(
                  processorUUID, location,
                  QIOAutomationDeviceSnapshot.Kind.CRAFTING_PROCESSOR,
                  blockId == null ? "minecraft:air" : blockId.toString(),
                  Math.max(0, processor.getBlockMetadata()),
                  state.getDefinitionId().toString(), state.getHostId().toString(),
                  stateName, true, Math.max(0, processor.getWorld().getTotalWorldTime()),
                  processor.getManagementConfigurationRevision(), 0, processor.getLaneCount(),
                  Math.min(Integer.MAX_VALUE, state.getActiveLanes().size()),
                  processor.isManagementPaused(),
                  identityConflict ? "Duplicate QIO crafting processor UUID" :
                   recoveryPending && state.getDiagnostic() == null ?
                         "QIO crafting processor recovery is pending" : state.getDiagnostic());
            network.observeAutomationDevice(snapshot,
                  MekanismConfig.current().qioProcessing.deviceRecordsPerFrequency.val());
            directoryRejectedDevices.remove(processorUUID);
        } catch (IsolatedNetworkException e) {
            directoryRejectedDevices.add(processorUUID);
        } catch (RuntimeException e) {
            if (directoryRejectedDevices.add(processorUUID)) {
                Mekanism.logger.warn("Unable to publish QIO crafting processor {} to frequency {}",
                      processorUUID, frequency.getFrequencyUUID(), e);
            }
        }
    }

    private void publishOffline(QIOCraftingProcessor processor) {
        PublishedEndpoint endpoint = published.remove(processor);
        if (endpoint == null) {
            return;
        }
        long currentTick = processor != null && processor.getWorld() != null ?
              Math.max(0, processor.getWorld().getTotalWorldTime()) : 0;
        publishOffline(endpoint, currentTick);
    }

    private static void publishOffline(PublishedEndpoint endpoint, long currentTick) {
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              endpoint.frequencyUUID);
        if (network != null) {
            network.markAutomationDeviceOffline(endpoint.deviceUUID, endpoint.location,
                  currentTick);
        }
    }

    private void forgetPublished(QIOCraftingProcessor processor) {
        PublishedEndpoint endpoint = published.remove(processor);
        if (endpoint != null) {
            forgetPublished(endpoint);
        }
    }

    private static void forgetPublished(PublishedEndpoint endpoint) {
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              endpoint.frequencyUUID);
        if (network != null) {
            network.forgetAutomationDeviceMembership(endpoint.deviceUUID,
                  endpoint.location);
        }
    }

    private static boolean isLoadedInvalidation(QIOCraftingProcessor processor) {
        return processor.getWorld() != null && !processor.getWorld().isRemote &&
              processor.getWorld().isBlockLoaded(processor.getPos(), false) &&
              (processor.isInvalid() || processor.getWorld().getTileEntity(
                    processor.getPos()) != processor);
    }

    private static boolean isAvailable(QIOCraftingProcessor processor, UUID frequencyUUID) {
        if (!isLoaded(processor, frequencyUUID) ||
              processor.getProcessorState().getState() != QIOCraftingProcessorState.State.ACTIVE ||
              processor.isManagementPaused()) return false;
        return true;
    }

    private static boolean isLoaded(QIOCraftingProcessor processor, UUID frequencyUUID) {
        if (processor == null || processor.isInvalid() || processor.getWorld() == null ||
            processor.getWorld().isRemote ||
            !processor.getWorld().isBlockLoaded(processor.getPos(), false) ||
            processor.getWorld().getTileEntity(processor.getPos()) != processor) return false;
        QIOFrequency frequency = processor.getQIOFrequency();
        return frequency != null && frequency.isValid() && !frequency.isRemoved() &&
              frequencyUUID.equals(frequency.getFrequencyUUID());
    }

    private static final class PublishedEndpoint {

        private final UUID deviceUUID;
        private final UUID frequencyUUID;
        private final QIOAutomationDeviceLocation location;

        private PublishedEndpoint(UUID deviceUUID, UUID frequencyUUID,
              QIOAutomationDeviceLocation location) {
            this.deviceUUID = deviceUUID;
            this.frequencyUUID = frequencyUUID;
            this.location = location;
        }
    }

    public static final class LoadedProcessor {

        private final UUID processorUUID;
        private final QIOCraftingProcessor processor;

        private LoadedProcessor(UUID processorUUID, QIOCraftingProcessor processor) {
            this.processorUUID = processorUUID;
            this.processor = processor;
        }

        @Nonnull
        public UUID processorUUID() {
            return processorUUID;
        }

        @Nonnull
        public QIOCraftingProcessor processor() {
            return processor;
        }

        public int dimension() {
            return processor.getWorld().provider.getDimension();
        }

        @Nonnull
        public BlockPos position() {
            return processor.getPos();
        }
    }
}
