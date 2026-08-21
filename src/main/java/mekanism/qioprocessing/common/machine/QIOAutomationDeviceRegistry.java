package mekanism.qioprocessing.common.machine;

import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachinePresentationDescriptor;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.ProviderConformanceReport;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.config.MekanismConfig;
import mekanism.qioprocessing.common.content.QIOAutomationUpgradeSupport;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager.IsolatedNetworkException;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceDirectoryCleanupService;
import mekanism.qioprocessing.common.content.device.QIOAutomationDeviceSnapshot;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import mekanism.qioprocessing.api.machine.QIOAutomationDeviceLocation;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.common.Mekanism;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side online directory for QIO automation hosts. It never chooses one endpoint when a UUID is duplicated.
 */
/**
 * QIO 处理模块中的 QIOAutomationDeviceRegistry 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAutomationDeviceRegistry {

    public static final QIOAutomationDeviceRegistry INSTANCE = new QIOAutomationDeviceRegistry();
    private static final int MAX_PENDING_PER_TICK = 512;
    private static final int MAX_PRUNE_PER_TICK = 512;
    private static final int MAX_ACCESS_CHECKS_PER_TICK = 128;

    private final Map<UUID, LinkedHashMap<QIOAutomationDeviceLocation, Endpoint>> byUUID = new LinkedHashMap<>();
    private final Set<DefaultQIOAutomationHost> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<UUID> quarantinedUUIDs = new LinkedHashSet<>();
    private final Set<UUID> directoryRejectedDevices = new LinkedHashSet<>();
    private int pendingCursor;
    private int pruneCursor;
    private int accessCursor;

    private QIOAutomationDeviceRegistry() {
    }

    public synchronized void trackPending(@Nonnull DefaultQIOAutomationHost host) {
        pending.add(host);
    }

    /** Bridges ordinary Mekanism slot/tank notifications into the QIO execution wake queue. */
    public void notifyContentsChanged(@Nonnull TileEntity tile) {
        Objects.requireNonNull(tile, "tile");
        if (tile.getWorld() == null || tile.getWorld().isRemote ||
              QIOAutomationCapabilities.AUTOMATION_HOST == null ||
              !tile.hasCapability(QIOAutomationCapabilities.AUTOMATION_HOST, null)) {
            return;
        }
        QIOAutomationHost host = tile.getCapability(
              QIOAutomationCapabilities.AUTOMATION_HOST, null);
        if (host instanceof DefaultQIOAutomationHost mutable &&
              mutable.hasDeferredOutputRecovery()) {
            trackPending(mutable);
        }
        QIOFrequencyReference reference = host == null ? null : host.getFrequencyReference();
        if (host != null && reference != null) {
            QIOProcessingExecutionService.INSTANCE.wakeDeviceContents(
                  reference.getFrequencyUUID(), host.getPersistentDeviceUUID());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient()) {
            return;
        }
        processPending();
        QIOAutomationDeviceDirectoryCleanupService.processPendingChunks();
        pruneStaleEndpoints();
        refreshFrequencyAccess();
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getWorld().isRemote) {
            return;
        }
        int chunkX = event.getChunk().x;
        int chunkZ = event.getChunk().z;
        synchronized (this) {
            List<Endpoint> removed = new ArrayList<>();
            for (LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints : byUUID.values()) {
                for (Endpoint endpoint : endpoints.values()) {
                    QIOAutomationDeviceLocation location = endpoint.location;
                    if (location.dimension() == event.getWorld().provider.getDimension() &&
                        location.position().getX() >> 4 == chunkX && location.position().getZ() >> 4 == chunkZ) {
                        removed.add(endpoint);
                    }
                }
            }
            removed.forEach(endpoint -> unregisterInternal(endpoint.host, endpoint.location));
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            return;
        }
        int dimension = event.getWorld().provider.getDimension();
        synchronized (this) {
            List<Endpoint> removed = new ArrayList<>();
            for (LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints : byUUID.values()) {
                for (Endpoint endpoint : endpoints.values()) {
                    if (endpoint.location.dimension() == dimension) {
                        removed.add(endpoint);
                    }
                }
            }
            removed.forEach(endpoint -> unregisterInternal(endpoint.host, endpoint.location));
            pending.removeIf(host -> host.tile() != null && host.tile().getWorld() == event.getWorld());
        }
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (event.getWorld().isRemote) {
            return;
        }
        int dimension = event.getWorld().provider.getDimension();
        synchronized (this) {
            for (LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints : byUUID.values()) {
                for (Endpoint endpoint : endpoints.values()) {
                    if (endpoint.location.dimension() == dimension) {
                        endpoint.host.confirmCompletedOperationsPersisted();
                    }
                }
            }
        }
    }

    @Nullable
    public synchronized QIOAutomationHost findUsable(@Nonnull UUID deviceUUID) {
        LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints = byUUID.get(deviceUUID);
        if (endpoints == null || endpoints.size() != 1 || quarantinedUUIDs.contains(deviceUUID)) {
            return null;
        }
        Endpoint endpoint = endpoints.values().iterator().next();
        TileEntity tile = endpoint.host.tile();
        if (tile != null) {
            QIOAutomationUpgradeSupport.reconcile(tile, endpoint.host);
        }
        return endpoint.host.getState() == QIOAutomationHost.State.ACTIVE &&
              !endpoint.host.isManagementPaused() &&
              QIOAutomationUpgradeSupport.isModeInstalled(tile, endpoint.host.getEnabledMode()) &&
              validateEndpointForRegistration(endpoint.host, tile) ? endpoint.host : null;
    }

    @Nullable
    public synchronized LoadedDevice findLoadedDevice(@Nonnull UUID deviceUUID) {
        LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints = byUUID.get(deviceUUID);
        if (endpoints == null || endpoints.size() != 1 || quarantinedUUIDs.contains(deviceUUID)) {
            return null;
        }
        Endpoint endpoint = endpoints.values().iterator().next();
        TileEntity tile = endpoint.host.tile();
        if (tile != null) {
            QIOAutomationUpgradeSupport.reconcile(tile, endpoint.host);
        }
        boolean draining = endpoint.host.getState() == QIOAutomationHost.State.DRAINING_CHANGE;
        return isOperational(endpoint.host) && tile != null &&
              (draining || QIOAutomationUpgradeSupport.isModeInstalled(tile, endpoint.host.getEnabledMode())) &&
              validateEndpointForRegistration(endpoint.host, tile) &&
              !tile.isInvalid() && tile.getWorld() != null && !tile.getWorld().isRemote ?
              new LoadedDevice(endpoint.host, tile, endpoint.location) : null;
    }

    /**
     * Resolves a unique loaded automation machine for an explicit recovery command.
     * Unlike the operational lookup, this intentionally accepts quarantined hosts,
     * but still rejects duplicate identities and unloaded or replaced tiles.
     */
    @Nullable
    public synchronized LoadedDevice findLoadedDeviceForRecovery(@Nonnull UUID deviceUUID) {
        LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints = byUUID.get(deviceUUID);
        if (endpoints == null || endpoints.size() != 1 || quarantinedUUIDs.contains(deviceUUID)) {
            return null;
        }
        Endpoint endpoint = endpoints.values().iterator().next();
        TileEntity tile = endpoint.host.tile();
        return tile != null && !tile.isInvalid() && tile.getWorld() != null &&
              !tile.getWorld().isRemote &&
              tile.getWorld().isBlockLoaded(endpoint.location.position(), false) &&
              tile.getWorld().getTileEntity(endpoint.location.position()) == tile ?
              new LoadedDevice(endpoint.host, tile, endpoint.location) : null;
    }

    /** Publishes an already registered host immediately after an explicit recovery. */
    public synchronized void refreshHost(@Nonnull DefaultQIOAutomationHost host) {
        for (LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints : byUUID.values()) {
            for (Endpoint endpoint : endpoints.values()) {
                if (endpoint.host == host) {
                    publishSnapshot(endpoint);
                    return;
                }
            }
        }
    }

    @Nonnull
    public synchronized Map<UUID, List<QIOAutomationDeviceLocation>> snapshotLocations() {
        Map<UUID, List<QIOAutomationDeviceLocation>> snapshot = new LinkedHashMap<>();
        byUUID.forEach((uuid, endpoints) -> {
            List<QIOAutomationDeviceLocation> locations = new ArrayList<>(endpoints.keySet());
            Collections.sort(locations);
            snapshot.put(uuid, Collections.unmodifiableList(locations));
        });
        return Collections.unmodifiableMap(snapshot);
    }

    public synchronized boolean isQuarantined(@Nonnull UUID deviceUUID) {
        return quarantinedUUIDs.contains(deviceUUID);
    }

    /**
     * Checks whether a recovery candidate identifies no loaded endpoint other than the host
     * performing the recovery. Deferred legacy state must never be installed while the same
     * persistent UUID belongs to another loaded machine.
     */
    synchronized boolean isRecoveryIdentityUnique(@Nonnull UUID deviceUUID,
          @Nonnull DefaultQIOAutomationHost host) {
        LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints = byUUID.get(
              Objects.requireNonNull(deviceUUID, "deviceUUID"));
        if (endpoints == null || endpoints.isEmpty()) {
            return true;
        }
        return endpoints.size() == 1 && endpoints.values().iterator().next().host ==
              Objects.requireNonNull(host, "host");
    }

    @Nonnull
    public synchronized List<LoadedDevice> getUsableDevices(@Nonnull QIOAutomationMode mode) {
        return getLoadedDevices(mode, false);
    }

    /** Includes draining devices so their already-owned operations can settle. */
    @Nonnull
    public synchronized List<LoadedDevice> getOperationalDevices(
          @Nonnull QIOAutomationMode mode) {
        return getLoadedDevices(mode, true);
    }

    private List<LoadedDevice> getLoadedDevices(QIOAutomationMode mode,
          boolean includeDraining) {
        List<LoadedDevice> devices = new ArrayList<>();
        for (Map.Entry<UUID, LinkedHashMap<QIOAutomationDeviceLocation, Endpoint>> entry : byUUID.entrySet()) {
            if (quarantinedUUIDs.contains(entry.getKey()) || entry.getValue().size() != 1) {
                continue;
            }
            Endpoint endpoint = entry.getValue().values().iterator().next();
            TileEntity endpointTile = endpoint.host.tile();
            if (endpointTile != null) {
                QIOAutomationUpgradeSupport.reconcile(endpointTile, endpoint.host);
            }
            TileEntity tile = endpointTile;
            QIOAutomationHost.State hostState = endpoint.host.getState();
            boolean draining = includeDraining && hostState == QIOAutomationHost.State.DRAINING_CHANGE;
            boolean endpointValid = tile != null &&
                  validateEndpointForRegistration(endpoint.host, tile);
            if ((hostState == QIOAutomationHost.State.ACTIVE || draining) &&
                !endpoint.host.isManagementPaused() && endpoint.host.getEnabledMode() == mode &&
                (draining || QIOAutomationUpgradeSupport.isModeInstalled(tile, mode)) &&
                endpointValid &&
                tile != null && !tile.isInvalid() && tile.getWorld() != null && !tile.getWorld().isRemote) {
                devices.add(new LoadedDevice(endpoint.host, tile, endpoint.location));
            }
        }
        devices.sort((first, second) -> first.location.compareTo(second.location));
        return Collections.unmodifiableList(devices);
    }

    private static boolean isOperational(DefaultQIOAutomationHost host) {
        return host.getState() == QIOAutomationHost.State.ACTIVE ||
              host.getState() == QIOAutomationHost.State.DRAINING_CHANGE;
    }

    private static boolean isLoadedInvalidation(@Nullable TileEntity tile) {
        return tile != null && tile.getWorld() != null && !tile.getWorld().isRemote &&
              tile.getWorld().isBlockLoaded(tile.getPos(), false) &&
              (tile.isInvalid() || tile.getWorld().getTileEntity(tile.getPos()) != tile);
    }

    public synchronized void unregister(@Nonnull DefaultQIOAutomationHost host) {
        pending.remove(host);
        List<Endpoint> matches = new ArrayList<>();
        for (LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints : byUUID.values()) {
            for (Endpoint endpoint : endpoints.values()) {
                if (endpoint.host == host) {
                    matches.add(endpoint);
                }
            }
        }
        boolean removed = isLoadedInvalidation(host.tile());
        matches.forEach(endpoint -> {
            if (removed) {
                unregisterRemovedInternal(endpoint.host, endpoint.location);
            } else {
                unregisterInternal(endpoint.host, endpoint.location);
            }
        });
    }

    public synchronized void shutdown() {
        byUUID.clear();
        pending.clear();
        quarantinedUUIDs.clear();
        directoryRejectedDevices.clear();
        pendingCursor = 0;
        pruneCursor = 0;
        accessCursor = 0;
    }

    /** Explicitly clears a quarantine only after every endpoint has a distinct identity. */
    public synchronized boolean acknowledgeIdentityRecovery(@Nonnull UUID oldUUID, @Nonnull DefaultQIOAutomationHost host) {
        if (!quarantinedUUIDs.contains(oldUUID)) {
            return false;
        }
        unregister(host);
        if (!host.regenerateDeviceIdentity()) {
            pending.add(host);
            return false;
        }
        LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> remaining = byUUID.get(oldUUID);
        if (remaining == null || remaining.isEmpty()) {
            quarantinedUUIDs.remove(oldUUID);
        }
        pending.add(host);
        return true;
    }

    private void processPending() {
        List<DefaultQIOAutomationHost> candidates;
        synchronized (this) {
            if (pending.isEmpty()) {
                return;
            }
            candidates = new ArrayList<>(pending);
        }
        int processed = 0;
        for (DefaultQIOAutomationHost host : candidates) {
            if (processed++ >= MAX_PENDING_PER_TICK) {
                break;
            }
            TileEntity tile = host.tile();
            if (tile == null || tile.isInvalid() || tile.getWorld() == null || tile.getWorld().isRemote) {
                synchronized (this) {
                    pending.remove(host);
                }
                continue;
            }
            DefaultQIOAutomationHost.DeferredOutputRecoveryResult recovery;
            try {
                recovery = host.attemptDeferredOutputRecovery();
            } catch (RuntimeException e) {
                // Provider ports are addon-owned and may be unavailable while a tile is
                // restoring its custom inventory. Recovery must never escape into the
                // server tick; keep the host pending and retry against the next stable view.
                host.markRetryPending();
                Mekanism.logger.warn("Unable to inspect deferred QIO output recovery for {}",
                      host.getPersistentDeviceUUID(), e);
                continue;
            }
            if (recovery == DefaultQIOAutomationHost.DeferredOutputRecoveryResult.RETRY_LATER) {
                continue;
            }
            // A blocked deferred handoff is a quarantined, ownership-bearing record. Keep it
            // in the bounded retry queue so a later inventory/tank update can make the exact
            // baseline recoverable; removing it here used to make a valid post-load recovery
            // depend on an unrelated output-service tick.
            boolean retryRecovery = recovery == DefaultQIOAutomationHost.DeferredOutputRecoveryResult.BLOCKED &&
                  host.hasDeferredOutputRecovery();
            try {
                register(host, tile.getWorld(), tile.getPos());
            } catch (RuntimeException e) {
                // Provider/configuration code is addon-owned. One malformed endpoint must not
                // abort the server tick or lose the pending recovery record.
                Mekanism.logger.warn("Unable to register QIO automation device {}", 
                      host.getPersistentDeviceUUID(), e);
                retryRecovery = true;
            }
            synchronized (this) {
                if (!retryRecovery) {
                    pending.remove(host);
                }
            }
        }
    }

    private void register(@Nonnull DefaultQIOAutomationHost host, @Nonnull World world, @Nonnull BlockPos position) {
        QIOAutomationDeviceLocation location = QIOAutomationDeviceLocation.of(world, position);
        synchronized (this) {
            LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints =
                  byUUID.computeIfAbsent(host.getPersistentDeviceUUID(), ignored -> new LinkedHashMap<>());
            Endpoint atLocation = endpoints.get(location);
            if (atLocation != null && atLocation.host != host) {
                TileEntity current = world.getTileEntity(position);
                if (current != atLocation.host.tile()) {
                    endpoints.remove(location);
                } else {
                    atLocation.host.quarantineIdentityConflict();
                    host.quarantineIdentityConflict();
                    quarantinedUUIDs.add(host.getPersistentDeviceUUID());
                    return;
                }
            }
            Endpoint endpoint = atLocation != null && atLocation.host == host ? atLocation :
                  new Endpoint(host, location);
            endpoints.put(location, endpoint);
            QIOAutomationUpgradeSupport.reconcile(host.tile(), host);
            if (!validateEndpointForRegistration(host, host.tile())) {
                // Keep the endpoint visible for diagnostics/offline management, but do not let
                // an invalid dynamic provider be mistaken for an executable route publisher.
                directoryRejectedDevices.add(host.getPersistentDeviceUUID());
            }
            if (endpoints.size() > 1 || quarantinedUUIDs.contains(host.getPersistentDeviceUUID())) {
                quarantinedUUIDs.add(host.getPersistentDeviceUUID());
                for (Endpoint conflictingEndpoint : endpoints.values()) {
                    conflictingEndpoint.host.quarantineIdentityConflict();
                }
            }
            for (Endpoint registered : endpoints.values()) {
                publishSnapshot(registered);
            }
        }
    }

    static boolean validateEndpointForRegistration(@Nonnull DefaultQIOAutomationHost host,
          @Nullable TileEntity tile) {
        if (host.getEnabledMode() == null) {
            return true;
        }
        MachineRecipeProviderRegistry.BoundProvider provider =
              MachineRecipeProviderRegistry.find(tile);
        if (provider == null) {
            return false;
        }
        try {
            ProviderConformanceReport report = provider.validateQIOEndpointConformance(
                  host.getEnabledMode());
            if (!report.isConformant()) {
                return false;
            }
        } catch (RuntimeException ignored) {
            // Dynamic providers may transiently fail while their tile configuration is being
            // restored. Registration must not leak that exception into the server tick.
            return false;
        }
        return true;
    }

    private void pruneStaleEndpoints() {
        List<Endpoint> candidates = new ArrayList<>();
        synchronized (this) {
            for (LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints : byUUID.values()) {
                candidates.addAll(endpoints.values());
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        int start = pruneCursor % candidates.size();
        int checked = 0;
        for (int offset = 0; offset < candidates.size() && checked++ < MAX_PRUNE_PER_TICK; offset++) {
            Endpoint endpoint = candidates.get((start + offset) % candidates.size());
            TileEntity tile = endpoint.host.tile();
            if (tile == null || tile.getWorld() == null ||
                tile.getWorld().provider.getDimension() != endpoint.location.dimension() ||
                !endpoint.location.position().equals(tile.getPos()) ||
                !tile.getWorld().isBlockLoaded(tile.getPos(), false)) {
                synchronized (this) {
                    unregisterInternal(endpoint.host, endpoint.location);
                }
            } else if (tile.getWorld().getTileEntity(tile.getPos()) != tile) {
                synchronized (this) {
                    unregisterRemovedInternal(endpoint.host, endpoint.location);
                }
            } else if (tile.isInvalid()) {
                synchronized (this) {
                    // A loaded invalid tile is the normal 1.12 signal for block replacement;
                    // chunk unloads are handled by onChunkUnload and retain an offline snapshot.
                    unregisterRemovedInternal(endpoint.host, endpoint.location);
                }
            }
        }
        pruneCursor = start + checked;
    }

    private void refreshFrequencyAccess() {
        List<Endpoint> candidates = new ArrayList<>();
        synchronized (this) {
            for (LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints : byUUID.values()) {
                if (endpoints.size() == 1) {
                    candidates.addAll(endpoints.values());
                }
            }
        }
        if (candidates.isEmpty()) {
            accessCursor = 0;
            return;
        }
        candidates.sort((first, second) -> first.location.compareTo(second.location));
        int start = Math.floorMod(accessCursor, candidates.size());
        int checked = 0;
        for (int offset = 0; offset < candidates.size() && checked < MAX_ACCESS_CHECKS_PER_TICK;
             offset++, checked++) {
            Endpoint endpoint = candidates.get((start + offset) % candidates.size());
            if (!quarantinedUUIDs.contains(endpoint.host.getPersistentDeviceUUID()) &&
                endpoint.host.getState() != QIOAutomationHost.State.IDENTITY_CONFLICT) {
                QIOAutomationUpgradeSupport.reconcile(endpoint.host.tile(), endpoint.host);
                QIOAutomationBindingService.refreshAccess(endpoint.host);
                publishSnapshot(endpoint);
            }
        }
        accessCursor = start + checked;
    }

    private void publishSnapshot(Endpoint endpoint) {
        TileEntity tile = endpoint.host.tile();
        if (tile == null || tile.isInvalid() || tile.getWorld() == null ||
            tile.getWorld().isRemote ||
            !tile.getWorld().isBlockLoaded(endpoint.location.position(), false) ||
            tile.getWorld().getTileEntity(endpoint.location.position()) != tile) {
            publishOffline(endpoint);
            return;
        }
        if (!QIOAutomationUpgradeSupport.isModeInstalled(tile,
              endpoint.host.getEnabledMode())) {
            forgetPublished(endpoint);
            return;
        }
        QIOFrequencyReference reference = endpoint.host.getFrequencyReference();
        UUID frequencyUUID = reference == null || endpoint.host.getEnabledMode() == null ? null :
              reference.getFrequencyUUID();
        if (!java.util.Objects.equals(endpoint.publishedFrequencyUUID, frequencyUUID)) {
            forgetPublished(endpoint);
            endpoint.publishedFrequencyUUID = frequencyUUID;
        }
        if (frequencyUUID == null || !QIOProcessingNetworkManager.INSTANCE.isLoaded()) {
            return;
        }
        MachineRecipeProviderRegistry.BoundProvider provider = MachineRecipeProviderRegistry.find(tile);
        Block block = tile.getBlockType();
        ResourceLocation blockId = block == null ? null : block.getRegistryName();
        if (QIOProcessingNetworkManager.INSTANCE.getIsolationStatus(frequencyUUID) != null) {
            directoryRejectedDevices.add(endpoint.host.getPersistentDeviceUUID());
            return;
        }
        try {
            QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.getOrCreate(
                  frequencyUUID, new QIOFrequencyIdentitySnapshot(reference.getFrequencyName(),
                        reference.getOwnerUUID(), reference.getSecurityMode()));
            String providerId = provider == null ? "unknown:provider" :
                  provider.id().toString();
            String profileScopeId = provider == null ? providerId :
                  QIOAutomationRecipeProfileScope.resolve(provider);
            int providerRevision = provider == null ? 0 :
                  Math.max(0, provider.getConfigurationRevision());
            List<MachineRecipeRoute> providerRoutes = provider == null ?
                  Collections.emptyList() : provider.getRecipeRoutes();
            MachinePresentationDescriptor presentation = provider == null ?
                  MachinePresentationDescriptor.fallback(tile) : provider.getPresentation();
            QIOAutomationMode mode = endpoint.host.getEnabledMode();
            boolean publishableRoutes = !providerRoutes.isEmpty() &&
                  hasPublishableRoutes(provider, mode);
            QIOAutomationDeviceSnapshot snapshot = new QIOAutomationDeviceSnapshot(
                   endpoint.host.getPersistentDeviceUUID(), endpoint.location,
                  QIOAutomationDeviceSnapshot.Kind.AUTOMATION_MACHINE,
                  blockId == null ? "minecraft:air" : blockId.toString(),
                  Math.max(0, tile.getBlockMetadata()),
                  presentation, providerId, profileScopeId,
                  mode.name(), endpoint.host.getState().name(), true,
                  Math.max(0, tile.getWorld().getTotalWorldTime()),
                  endpoint.host.getConfigurationRevision(),
                  providerRevision, providerRoutes.size(),
                   endpoint.host.getActivitySnapshots().size(),
                    endpoint.host.isManagementPaused(), endpoint.host.getRecoveryDiagnostic())
                  .withRecoveryState(endpoint.host.getRecoveryState());
            network.observeAutomationDevice(snapshot,
                  MekanismConfig.current().qioProcessing.deviceRecordsPerFrequency.val());
            RoutePublicationAction routeAction = routePublicationAction(publishableRoutes,
                  endpoint.shouldPublishRoutes(frequencyUUID, mode, profileScopeId,
                        providerRevision, providerRoutes.size()));
            if (routeAction == RoutePublicationAction.PUBLISH) {
                network.observeAutomationDeviceRoutes(endpoint.host.getPersistentDeviceUUID(),
                      mode, profileScopeId, provider.id(), providerRoutes,
                      MekanismConfig.current().qioProcessing.providerRoutesPerFrequency.val());
                endpoint.markRoutesPublished(frequencyUUID, mode, profileScopeId,
                      providerRevision, providerRoutes.size());
            } else if (routeAction == RoutePublicationAction.FORGET) {
                network.forgetAutomationDeviceRoutes(endpoint.host.getPersistentDeviceUUID());
                endpoint.clearPublishedRoutes();
            }
            directoryRejectedDevices.remove(endpoint.host.getPersistentDeviceUUID());
        } catch (IsolatedNetworkException e) {
            directoryRejectedDevices.add(endpoint.host.getPersistentDeviceUUID());
        } catch (RuntimeException e) {
            if (directoryRejectedDevices.add(endpoint.host.getPersistentDeviceUUID())) {
                Mekanism.logger.warn("Unable to publish QIO automation device {} to frequency {}",
                      endpoint.host.getPersistentDeviceUUID(), frequencyUUID, e);
            }
        }
    }

    static boolean hasPublishableRoutes(
          @Nullable MachineRecipeProviderRegistry.BoundProvider provider,
          @Nullable QIOAutomationMode mode) {
        return provider != null && mode != null && mode != QIOAutomationMode.OUTPUT_ONLY &&
              provider.validateQIOConformance(mode).isConformant();
    }

    static RoutePublicationAction routePublicationAction(boolean publishableRoutes,
          boolean publicationChanged) {
        if (!publishableRoutes) {
            return RoutePublicationAction.FORGET;
        }
        return publicationChanged ? RoutePublicationAction.PUBLISH :
              RoutePublicationAction.RETAIN;
    }

    enum RoutePublicationAction {
        PUBLISH,
        RETAIN,
        FORGET
    }

    private void publishOffline(Endpoint endpoint) {
        UUID frequencyUUID = endpoint.publishedFrequencyUUID;
        if (frequencyUUID == null) {
            return;
        }
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(frequencyUUID);
        if (network != null) {
            TileEntity tile = endpoint.host.tile();
            long currentTick = tile != null && tile.getWorld() != null ?
                  Math.max(0, tile.getWorld().getTotalWorldTime()) : 0;
            network.markAutomationDeviceOffline(endpoint.host.getPersistentDeviceUUID(),
                  endpoint.location, currentTick);
        }
        endpoint.publishedFrequencyUUID = null;
        endpoint.clearPublishedRoutes();
    }

    private void forgetPublished(Endpoint endpoint) {
        UUID frequencyUUID = endpoint.publishedFrequencyUUID;
        if (frequencyUUID != null) {
            QIOProcessingNetworkData network =
                  QIOProcessingNetworkManager.INSTANCE.get(frequencyUUID);
            if (network != null) {
                network.forgetAutomationDeviceMembership(
                      endpoint.host.getPersistentDeviceUUID(), endpoint.location);
            }
        }
        endpoint.publishedFrequencyUUID = null;
        endpoint.clearPublishedRoutes();
    }

    private void unregisterInternal(DefaultQIOAutomationHost host, QIOAutomationDeviceLocation location) {
        LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints = byUUID.get(host.getPersistentDeviceUUID());
        if (endpoints != null) {
            Endpoint endpoint = endpoints.get(location);
            if (endpoint != null && endpoint.host == host) {
                publishOffline(endpoint);
                endpoints.remove(location);
            }
            if (endpoints.isEmpty()) {
                byUUID.remove(host.getPersistentDeviceUUID());
            }
        }
    }

    private void unregisterRemovedInternal(DefaultQIOAutomationHost host,
          QIOAutomationDeviceLocation location) {
        LinkedHashMap<QIOAutomationDeviceLocation, Endpoint> endpoints =
              byUUID.get(host.getPersistentDeviceUUID());
        if (endpoints != null) {
            Endpoint endpoint = endpoints.get(location);
            if (endpoint != null && endpoint.host == host) {
                forgetPublished(endpoint);
                endpoints.remove(location);
            }
            if (endpoints.isEmpty()) {
                byUUID.remove(host.getPersistentDeviceUUID());
            }
        }
    }

    private static final class Endpoint {

        private final DefaultQIOAutomationHost host;
        private final QIOAutomationDeviceLocation location;
        @Nullable
        private UUID publishedFrequencyUUID;
        @Nullable
        private UUID routeFrequencyUUID;
        @Nullable
        private QIOAutomationMode routeMode;
        @Nullable
        private String routeProfileScopeId;
        private int routeProviderRevision = -1;
        private int routeCount = -1;

        private Endpoint(DefaultQIOAutomationHost host, QIOAutomationDeviceLocation location) {
            this.host = host;
            this.location = location;
        }

        private boolean shouldPublishRoutes(UUID frequencyUUID, QIOAutomationMode mode,
              String profileScopeId, int providerRevision, int currentRouteCount) {
            return !frequencyUUID.equals(routeFrequencyUUID) || mode != routeMode ||
                  !profileScopeId.equals(routeProfileScopeId) ||
                  providerRevision != routeProviderRevision || currentRouteCount != routeCount;
        }

        private void markRoutesPublished(UUID frequencyUUID, QIOAutomationMode mode,
              String profileScopeId, int providerRevision, int currentRouteCount) {
            routeFrequencyUUID = frequencyUUID;
            routeMode = mode;
            routeProfileScopeId = profileScopeId;
            routeProviderRevision = providerRevision;
            routeCount = currentRouteCount;
        }

        private void clearPublishedRoutes() {
            routeFrequencyUUID = null;
            routeMode = null;
            routeProfileScopeId = null;
            routeProviderRevision = -1;
            routeCount = -1;
        }
    }

    public static final class LoadedDevice {

        private final DefaultQIOAutomationHost host;
        private final TileEntity tile;
        private final QIOAutomationDeviceLocation location;

        private LoadedDevice(DefaultQIOAutomationHost host, TileEntity tile, QIOAutomationDeviceLocation location) {
            this.host = host;
            this.tile = tile;
            this.location = location;
        }

        @Nonnull
        public QIOAutomationHost host() {
            return host;
        }

        @Nonnull
        public DefaultQIOAutomationHost mutableHost() {
            return host;
        }

        @Nonnull
        public TileEntity tile() {
            return tile;
        }

        @Nonnull
        public QIOAutomationDeviceLocation location() {
            return location;
        }
    }
}
