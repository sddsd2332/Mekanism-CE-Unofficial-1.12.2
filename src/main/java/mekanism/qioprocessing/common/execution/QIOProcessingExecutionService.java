package mekanism.qioprocessing.common.execution;

import mekanism.api.Action;
import mekanism.api.processing.MachinePort;
import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.MachineResourceStack;
import mekanism.api.processing.MachineTransferPlan;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.api.qio.external.QIOTransferResult;
import mekanism.api.qio.external.QIOClaimRequest;
import mekanism.api.qio.external.QIOClaimResult;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.QIOProcessingConfig;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.buffer.QIOJobBuffer;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobState;
import mekanism.qioprocessing.common.content.job.QIOOperationAssignment;
import mekanism.qioprocessing.common.content.job.QIOStepRuntime;
import mekanism.qioprocessing.common.content.material.QIOMaterialClaimCoordinator;
import mekanism.qioprocessing.common.content.material.QIOPlanReassignmentCoordinator;
import mekanism.qioprocessing.common.content.material.QIOMaterialCommitment;
import mekanism.qioprocessing.common.content.passive.QIOPassiveOperation;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfile;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileCatalog;
import mekanism.qioprocessing.common.content.profile.QIOAutomationRecipeProfileLayout;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import mekanism.qioprocessing.common.content.processor.QIOProcessorLaneRuntime;
import mekanism.qioprocessing.common.content.processor.QIOCraftingProcessorState;
import mekanism.qioprocessing.common.content.scheduling.QIOExecutionSlotScheduler;
import mekanism.qioprocessing.common.content.transfer.QIODurableTransferRecord;
import mekanism.qioprocessing.common.content.transfer.QIOConfigurationExchangeRecord;
import mekanism.qioprocessing.common.content.transfer.QIOReservationCoordinator;
import mekanism.qioprocessing.api.machine.MachineActivitySnapshot;
import mekanism.qioprocessing.api.machine.MachineOperationLease;
import mekanism.qioprocessing.api.machine.MachineOperationToken;
import mekanism.qioprocessing.api.machine.MachinePortBaseline;
import mekanism.qioprocessing.api.machine.QIOAutomationHost;
import mekanism.qioprocessing.common.planning.QIORecipeCatalogService;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipePattern;
import mekanism.qioprocessing.common.planning.QIOPlanningRoute;
import mekanism.qioprocessing.common.processor.QIOCraftingProcessorDeviceRegistry;
import mekanism.qioprocessing.common.processor.QIOCraftingProcessorDeviceRegistry.LoadedProcessor;
import mekanism.qioprocessing.common.machine.QIOAutomationDeviceRegistry;
import mekanism.qioprocessing.common.machine.QIOAutomationDeviceRegistry.LoadedDevice;
import mekanism.qioprocessing.common.machine.QIOAutomationRecipeProfileScope;
import mekanism.qioprocessing.common.machine.DefaultQIOAutomationHost;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import mekanism.api.gas.GasStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/** Bounded main-thread scheduler and transfer driver for persisted QIO jobs. */
public final class QIOProcessingExecutionService {

    public static final QIOProcessingExecutionService INSTANCE =
          new QIOProcessingExecutionService();
    private static final long AGING_INTERVAL = 200;
    private static final long AGING_CAP = 1_000;
    private static final long PROVIDER_FALLBACK_SCAN_TICKS =
          QIOAdaptiveRetryTracker.MAX_RETRY_TICKS;

    private int networkCursor;
    private final Map<UUID, ProviderWakeStamp> providerWakeStamps = new LinkedHashMap<>();
    private final Map<ProviderCursorKey, UUID> providerCursors = new LinkedHashMap<>();
    private final QIOAdaptiveRetryTracker retryTracker = new QIOAdaptiveRetryTracker();
    private final Set<UUID> providerWakeFrequencies = ConcurrentHashMap.newKeySet();

    private QIOProcessingExecutionService() {
    }

    public void shutdown() {
        networkCursor = 0;
        providerWakeStamps.clear();
        providerCursors.clear();
        retryTracker.clear();
        providerWakeFrequencies.clear();
    }

    /** Wakes blocked jobs and provider waiters after a device lane was released. */
    public void wakeDevice(@Nonnull UUID frequencyUUID, @Nonnull UUID deviceUUID) {
        providerWakeFrequencies.add(Objects.requireNonNull(frequencyUUID, "frequencyUUID"));
        retryTracker.wakeDevice(Objects.requireNonNull(deviceUUID, "deviceUUID"));
    }

    /** Wakes active jobs and requeues provider waiters after observable machine contents change. */
    public void wakeDeviceContents(@Nonnull UUID frequencyUUID, @Nonnull UUID deviceUUID) {
        providerWakeFrequencies.add(Objects.requireNonNull(frequencyUUID, "frequencyUUID"));
        retryTracker.wakeDevice(Objects.requireNonNull(deviceUUID, "deviceUUID"));
    }

    boolean hasPendingProviderWake(@Nonnull UUID frequencyUUID) {
        return providerWakeFrequencies.contains(Objects.requireNonNull(frequencyUUID,
              "frequencyUUID"));
    }

    /** Wakes delivery jobs when QIO contents, capacity, claims, or access changed. */
    public void wakeStorage(@Nonnull UUID frequencyUUID) {
        retryTracker.wakeFrequency(Objects.requireNonNull(frequencyUUID, "frequencyUUID"));
    }

    /** Wakes one job after a direct user mutation such as cancellation. */
    public void wakeJob(@Nonnull UUID jobId) {
        retryTracker.wakeJob(Objects.requireNonNull(jobId, "jobId"));
    }

    public void tick(@Nonnull World world) {
        if (world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        QIOProcessingConfig config = MekanismConfig.current().qioProcessing;
        List<QIOProcessingNetworkData> networks = new ArrayList<>(
              QIOProcessingNetworkManager.INSTANCE.getNetworks());
        if (networks.isEmpty()) {
            networkCursor = 0;
            providerWakeStamps.clear();
            providerCursors.clear();
            retryTracker.clear();
            providerWakeFrequencies.clear();
            return;
        }
        networks.sort(Comparator.comparing(network -> network.getFrequencyUUID().toString()));
        Map<UUID, QIOProcessingNetworkData> networksById = new LinkedHashMap<>();
        Set<UUID> loadedNetworks = new LinkedHashSet<>();
        for (QIOProcessingNetworkData network : networks) {
            loadedNetworks.add(network.getFrequencyUUID());
            networksById.put(network.getFrequencyUUID(), network);
            ProviderWakeStamp next = ProviderWakeStamp.capture(network);
            ProviderWakeStamp previous = providerWakeStamps.put(network.getFrequencyUUID(), next);
            boolean explicitlyWoken = providerWakeFrequencies.remove(network.getFrequencyUUID());
            boolean fallbackScan = Math.floorMod(world.getTotalWorldTime() +
                  network.getFrequencyUUID().hashCode(), PROVIDER_FALLBACK_SCAN_TICKS) == 0;
            if (explicitlyWoken || fallbackScan || !next.equals(previous)) {
                network.wakeWaitingProviderJobs();
            }
        }
        providerWakeStamps.keySet().retainAll(loadedNetworks);
        providerCursors.keySet().removeIf(key -> !loadedNetworks.contains(key.frequencyUUID));
        providerWakeFrequencies.retainAll(loadedNetworks);
        retryTracker.removeIf((frequencyUUID, jobId) -> {
            QIOProcessingNetworkData network = networksById.get(frequencyUUID);
            QIOCraftingJob job = network == null ? null : network.getJob(jobId);
            return job == null || job.getState().isTerminal();
        });
        int remaining = config.executionActionsPerTick.val();
        int start = Math.floorMod(networkCursor, networks.size());
        int visited = 0;
        while (visited < networks.size() && remaining > 0) {
            QIOProcessingNetworkData network = networks.get((start + visited) % networks.size());
            remaining -= Math.max(1, tickNetwork(network, world.getTotalWorldTime(), remaining,
                  config));
            visited++;
        }
        networkCursor = start + Math.max(1, visited);
    }

    private int tickNetwork(QIOProcessingNetworkData network, long gameTick, int budget,
          QIOProcessingConfig config) {
        int actions = 0;
        try {
            actions += pruneSettledHistory(network, budget - actions);
            if (actions >= budget) {
                return actions;
            }
            actions += tickUnreservedCancellations(network, budget - actions);
            if (actions >= budget) {
                return actions;
            }
            actions += tickReplans(network, budget - actions);
            if (actions >= budget) {
                return actions;
            }
            int granted = QIOExecutionSlotScheduler.grantAvailableSlots(network,
                  config.executionSlotsPerFrequency.val(), config.slotGrantsPerTick.val(),
                  AGING_INTERVAL, AGING_CAP);
            actions += granted;
            if (actions >= budget) {
                return actions;
            }
            for (QIOCraftingJob job : network.pollReservingJobs(budget - actions)) {
                actions += Math.max(1, reserve(network, job));
                if (actions >= budget) {
                    break;
                }
            }
            for (QIOCraftingJob job : network.getDispatchCandidates(budget - actions)) {
                if (actions >= budget) {
                    break;
                }
                // Physical operations must be observed every tick so a completed machine can be
                // settled and receive its next batch without waiting for adaptive retry.
                if (!job.hasActiveOperations() &&
                      !retryTracker.shouldAttempt(job.getJobId(), gameTick)) {
                    network.rotateDispatchJob(job.getJobId());
                    continue;
                }
                int used = driveJob(network, job, gameTick, budget - actions, config);
                actions += Math.max(1, used);
                if (job.getState() == QIOCraftingJobState.WAITING_PROVIDER &&
                      !job.hasActiveOperations()) {
                    retryTracker.recordProgress(job.getJobId());
                    network.deferWaitingProviderJob(job.getJobId());
                } else {
                    if (used > 0 || job.getState().isTerminal()) {
                        retryTracker.recordProgress(job.getJobId());
                    } else {
                        retryTracker.recordBlocked(job.getJobId(), network.getFrequencyUUID(),
                              activeDeviceUUIDs(job), gameTick);
                    }
                    network.rotateDispatchJob(job.getJobId());
                }
            }
            // Scheduled jobs are latency-sensitive: process passive automation only after the
            // active reservation/dispatch pass has had a chance to advance them.
            if (actions < budget) {
                actions += tickPassive(network, gameTick, budget - actions);
            }
        } catch (IOException e) {
            Mekanism.logger.error("Unable to persist QIO processing execution for frequency {}",
                  network.getFrequencyUUID(), e);
        } catch (RuntimeException e) {
            Mekanism.logger.error("Unable to advance QIO processing execution for frequency {}",
                  network.getFrequencyUUID(), e);
        }
        return actions;
    }

    private static long activeOperationCount(QIOCraftingJob job, ProviderKind providerKind) {
        long active = 0;
        for (QIOStepRuntime runtime : job.getStepRuntimes().values()) {
            for (QIOOperationAssignment assignment : runtime.getActiveOperations().values()) {
                if (assignment.getProviderKind() == providerKind) {
                    active++;
                }
            }
        }
        return active;
    }

    private static long workbenchConcurrencyLimit(QIOProcessingNetworkData network,
          QIOPlanStep step, QIOProcessingConfig config) {
        long onlineCapacity = 0;
        for (LoadedProcessor loaded :
              QIOCraftingProcessorDeviceRegistry.INSTANCE.getAvailable(network.getFrequencyUUID())) {
            if (network.getPolicies().isRouteEnabled(loaded.processorUUID(), step.getProviderId(),
                  step.getRouteId(), step.getRecipeKey())) {
                onlineCapacity = saturatedAdd(onlineCapacity,
                      Math.max(0, loaded.processor().getLaneCount()));
            }
        }
        long limit = effectiveConcurrencyLimit(config.limitWorkbenchThreadsPerJob.val(),
              config.maxWorkbenchThreadsPerJob.val(), onlineCapacity);
        // A zero-capacity route is attempted once so the job can enter WAITING_PROVIDER.
        return Math.max(1, limit);
    }

    private static long machineConcurrencyLimit(QIOProcessingNetworkData network,
          QIOPlanStep step, QIOProcessingConfig config) {
        long onlineCapacity = machineOnlineCapacity(network, step);
        long limit = effectiveConcurrencyLimit(config.limitMachineThreadsPerJob.val(),
              config.maxMachineThreadsPerJob.val(), onlineCapacity);
        return Math.max(1, limit);
    }

    static long effectiveConcurrencyLimit(boolean limited, int configuredMaximum,
          long onlineCapacity) {
        if (configuredMaximum <= 0) {
            throw new IllegalArgumentException("Configured QIO concurrency must be positive");
        }
        long checkedCapacity = Math.max(0, onlineCapacity);
        return limited ? Math.min(configuredMaximum, checkedCapacity) : checkedCapacity;
    }

    private static long machineOnlineCapacity(QIOProcessingNetworkData network,
          QIOPlanStep step) {
        long capacity = 0;
        for (LoadedDevice device : QIOAutomationDeviceRegistry.INSTANCE.getUsableDevices(
              QIOAutomationMode.SCHEDULED)) {
            UUID deviceUUID = device.host().getPersistentDeviceUUID();
            if (!matchesFrequencyOrOwnedDrain(network, device.host(),
                  QIOAutomationMode.SCHEDULED, null) ||
                !network.getPolicies().isRouteEnabled(deviceUUID, step.getProviderId(),
                      step.getRouteId(), step.getRecipeKey())) {
                continue;
            }
            MachineRecipeProviderRegistry.BoundProvider provider =
                  MachineRecipeProviderRegistry.find(device.tile());
            if (provider == null || !provider.id().toString().equals(step.getProviderId()) ||
                !network.getAutomationRecipeProfiles().getActiveProfile(deviceUUID,
                      QIOAutomationMode.SCHEDULED,
                      QIOAutomationRecipeProfileScope.resolve(provider)).isRouteEnabled(
                            QIOAutomationRecipeProfileLayout.routeKey(step.getRouteId(),
                                  step.getRecipeKey()))) {
                continue;
            }
            Map<String, MachinePort> ports = ports(provider);
            for (MachineRecipeRoute route : provider.getRecipeRoutes()) {
                if (!route.routeId().equals(step.getRouteId()) ||
                    !route.logicalRecipeKey().equals(step.getRecipeKey())) {
                    continue;
                }
                QIOPlanningRoute projection = QIOPlanningRoute.fromMachineRoute(provider.id(),
                      route, 0);
                if (projection.getSignature().equals(step.getRouteSignature()) &&
                    endpoint(device, provider, route, ports) != null) {
                    capacity = saturatedAdd(capacity, 1);
                }
            }
        }
        return capacity;
    }

    private int pruneSettledHistory(QIOProcessingNetworkData network, int budget)
          throws IOException {
        if (budget <= 0) {
            return 0;
        }
        int actions = 0;
        boolean jobsChanged = false;
        for (QIOCraftingJob job : network.pollTerminalJobHistory(budget)) {
            int removed = network.removeCommittedJobTransfers(job.getJobId());
            boolean pruned = network.removeSettledTerminalJob(job.getJobId());
            jobsChanged |= removed > 0 || pruned;
            if (removed > 0 || pruned) {
                actions++;
            }
        }
        if (jobsChanged) {
            persist(network);
        }
        if (actions >= budget) {
            return actions;
        }
        for (QIOPassiveOperation operation : network.pollTerminalPassiveHistory(budget - actions)) {
            if (network.hasUnsettledConfigurationExchange(operation.getOperationId())) {
                network.acknowledgeTerminalPassiveHistory(operation.getOperationId());
                continue;
            }
            int removed = network.removeCommittedOperationTransfers(operation.getOperationId());
            network.acknowledgeTerminalPassiveHistory(operation.getOperationId());
            if (removed > 0) {
                persist(network);
                return actions + 1;
            }
        }
        for (LoadedProcessor loaded : QIOCraftingProcessorDeviceRegistry.INSTANCE.getAvailable(
              network.getFrequencyUUID())) {
            QIOCraftingProcessorState state = loaded.processor().getProcessorState();
            for (QIOProcessorLaneRuntime lane : state.getActiveLanes().values()) {
                if (!lane.isSettled() || hasActiveJobOperation(network,
                      lane.getJobId(), lane.getOperationId())) {
                    continue;
                }
                if (!state.isPersistedSettledOperation(lane.getOperationId())) {
                    QIOEndpointPersistenceService.INSTANCE.requestProcessor(loaded.processor(),
                          lane.getOperationId());
                    continue;
                }
                network.removeCommittedJobOperationTransfers(lane.getJobId(),
                      lane.getOperationId());
                persist(network);
                state.removeSettledLane(lane.getLaneId(), lane.getOperationId());
                return actions + 1;
            }
        }
        for (LoadedDevice loaded : QIOAutomationDeviceRegistry.INSTANCE.getOperationalDevices(
              QIOAutomationMode.SCHEDULED)) {
            QIOAutomationHost host = loaded.host();
            MachineOperationToken[] tokens = host.getOperationTokens().values().toArray(
                  new MachineOperationToken[0]);
            for (MachineOperationToken token : tokens) {
                UUID jobId = token.jobId();
                if (token.kind() != MachineOperationToken.Kind.JOB || jobId == null ||
                      network.getJob(jobId) == null || hasActiveJobOperation(network, jobId,
                            token.operationId()) || hasActiveJobOperationTransfer(network,
                                  jobId, token.operationId())) {
                    continue;
                }
                if (token.state() == MachineOperationToken.State.COLLECTING) {
                    if (!host.transitionOperation(token.operationId(),
                          MachineOperationToken.State.COMPLETED,
                          MachineOperationLease.State.COMPLETED) ||
                          !host.transitionOperation(token.operationId(),
                                MachineOperationToken.State.COMPLETED,
                                MachineOperationLease.State.RELEASED)) {
                        continue;
                    }
                } else if (token.state() == MachineOperationToken.State.COMPLETED) {
                    MachineOperationLease lease = host.getLeases().get(token.leaseId());
                    if (lease != null && lease.state() == MachineOperationLease.State.COMPLETED &&
                          !host.transitionOperation(token.operationId(),
                                MachineOperationToken.State.COMPLETED,
                                MachineOperationLease.State.RELEASED)) {
                        continue;
                    }
                } else if (!host.abandonJobOperation(token.operationId())) {
                    continue;
                }
                int removed = network.removeCommittedJobOperationTransfers(jobId,
                      token.operationId());
                if (removed > 0) {
                    persist(network);
                }
                if (host.forgetSettledOperation(token.operationId())) {
                    return actions + 1;
                }
            }
        }
        return actions;
    }

    private int tickReplans(QIOProcessingNetworkData network, int budget) throws IOException {
        int actions = 0;
        for (QIOCraftingJob job : network.pollReplanCandidates(budget)) {
            if (actions >= budget) break;
            if (job.getState() == QIOCraftingJobState.REPLAN_DRAINING) {
                if (!job.hasActiveOperations()) {
                    actions += Math.max(1, deliver(network, job, budget - actions));
                } else {
                    actions++;
                }
                continue;
            }
            IQIOStorageView view = open(network, job);
            if (view == null) {
                actions++;
                continue;
            }
            try {
                QIOPlanReassignmentCoordinator.advance(network, job.getJobId(), view,
                      QIOProcessingNetworkManager.INSTANCE.persistenceBarrier());
                actions++;
            } finally {
                view.close();
            }
        }
        return actions;
    }

    private int tickUnreservedCancellations(QIOProcessingNetworkData network, int budget)
          throws IOException {
        int actions = 0;
        for (QIOCraftingJob job : network.pollCancellationCandidates(budget)) {
            if (actions >= budget) {
                break;
            }
            actions++;
            QIOJobBuffer buffer = network.getJobBuffer(job.getJobId());
            QIOMaterialCommitment commitment = network.getCommitment(job.getJobId());
            if (job.getExecutionSlotToken() != null || buffer == null || !buffer.isEmpty() ||
                  commitment == null || commitment.getState() == QIOMaterialCommitment.State.CONSUMED ||
                  commitment.getState() == QIOMaterialCommitment.State.RELEASED) {
                continue;
            }
            IQIOStorageView view = open(network, job);
            if (view == null) {
                if (job.getState() != QIOCraftingJobState.WAITING_ACCESS) {
                    network.transitionJobState(job.getJobId(), QIOCraftingJobState.WAITING_ACCESS);
                    persist(network);
                }
                continue;
            }
            try {
                QIOMaterialClaimCoordinator.cancelUnreserved(network, job.getJobId(), view,
                      QIOProcessingNetworkManager.INSTANCE.persistenceBarrier());
            } finally {
                view.close();
            }
        }
        return actions;
    }

    private int reserve(QIOProcessingNetworkData network, QIOCraftingJob job) throws IOException {
        IQIOStorageView view = open(network, job);
        if (view == null) {
            network.releaseExecutionSlot(job.getJobId(), QIOCraftingJobState.WAITING_ACCESS);
            persist(network);
            return 1;
        }
        try {
            QIOReservationCoordinator.reserve(network, job.getJobId(), view,
                  QIOProcessingNetworkManager.INSTANCE.persistenceBarrier());
            return 1;
        } finally {
            view.close();
        }
    }

    private int tickPassive(QIOProcessingNetworkData network, long gameTick, int budget)
          throws IOException {
        if (budget <= 0) {
            return 0;
        }
        int actions = 0;
        int operationBudget = budget > 1 ? budget - 1 : budget;
        for (QIOPassiveOperation operation : network.pollActivePassiveOperations(
              operationBudget)) {
            if (actions >= budget) {
                return actions;
            }
            actions += Math.max(1, drivePassiveOperation(network, operation));
        }
        if (actions < budget) {
            actions += startPassiveOperation(network, gameTick);
        }
        return actions;
    }

    private int startPassiveOperation(QIOProcessingNetworkData network, long gameTick)
          throws IOException {
        List<LoadedDevice> devices = new ArrayList<>(
              QIOAutomationDeviceRegistry.INSTANCE.getUsableDevices(QIOAutomationMode.PASSIVE));
        devices.removeIf(device -> device.host().getFrequencyReference() == null ||
              !network.getFrequencyUUID().equals(
                    device.host().getFrequencyReference().getFrequencyUUID()) ||
              network.hasActivePassiveOperation(device.host().getPersistentDeviceUUID()));
        devices.sort(Comparator.comparingLong((LoadedDevice device) ->
              network.getPolicies().machinePriority(device.host().getPersistentDeviceUUID()))
              .reversed().thenComparing(device ->
                    device.host().getPersistentDeviceUUID().toString()));
        for (LoadedDevice device : devices) {
            MachineRecipeProviderRegistry.BoundProvider provider =
                  MachineRecipeProviderRegistry.find(device.tile());
            if (!acceptsCurrentRoutes(provider, QIOAutomationMode.PASSIVE)) {
                continue;
            }
            List<MachineRecipeRoute> routes = new ArrayList<>(provider.getRecipeRoutes());
            UUID deviceUUID = device.host().getPersistentDeviceUUID();
            routes.removeIf(route -> !network.getPolicies().isRouteEnabled(deviceUUID,
                  provider.id().toString(), route.routeId(), route.logicalRecipeKey()));
            QIOAutomationRecipeProfileCatalog profiles = network.getAutomationRecipeProfiles();
            QIOAutomationRecipeProfile profile = profiles.getActiveProfile(deviceUUID,
                  QIOAutomationMode.PASSIVE,
                  QIOAutomationRecipeProfileScope.resolve(provider));
            QIOAutomationRecipeProfileLayout profileLayout =
                  new QIOAutomationRecipeProfileLayout(routes);
            routes = new ArrayList<>();
            for (QIOAutomationRecipeProfileLayout.Route route :
                  profileLayout.ordered(profile, true)) {
                routes.add(route.getMachineRoute());
            }
            if (!profile.hasCustomOrder()) {
                routes.sort(Comparator.comparingLong((MachineRecipeRoute route) ->
                      network.getPolicies().passiveRoutePriority(deviceUUID,
                            provider.id().toString(), route.routeId(),
                            route.logicalRecipeKey())).reversed()
                      .thenComparing(Comparator.comparingLong((MachineRecipeRoute route) ->
                            network.getPolicies().routePriority(deviceUUID,
                                  provider.id().toString(), route.routeId(),
                                  route.logicalRecipeKey())).reversed())
                      .thenComparing(MachineRecipeRoute::routeId)
                      .thenComparing(MachineRecipeRoute::recipeKey));
            }
            Map<String, MachinePort> currentPorts = ports(provider);
            routes.sort(Comparator.comparingInt(route ->
                  configurationAffinity(route, currentPorts)));
            IQIOStorageView view = openPassive(network);
            if (view == null) {
                return 0;
            }
            try {
                QIOStorageSnapshot snapshot = view.getSnapshot();
                for (MachineRecipeRoute route : routes) {
                    Map<String, MachinePort> ports = ports(provider);
                    MachineEndpoint endpoint = endpoint(device, provider, route, ports);
                    if (endpoint == null || !machineReadyForInput(endpoint)) {
                        continue;
                    }
                    if (!configurationPreflight(endpoint, snapshot, view)) {
                        continue;
                    }
                    String profileRouteKey = QIOAutomationRecipeProfileLayout.routeKey(
                          route.routeId(), route.logicalRecipeKey());
                    long operationCount = profile.getEffectiveCraftAmount(profileRouteKey,
                          profileLayout.getRoute(profileRouteKey) == null ? 1 :
                                profileLayout.getRoute(profileRouteKey).getMaximumCraftAmount());
                    operationCount = maxMachineOperations(endpoint, operationCount);
                    Map<PortableResourceDescriptor, Long> baseInputs = aggregate(route.inputs());
                    operationCount = maxAvailableOperations(snapshot, baseInputs,
                          operationCount);
                    if (operationCount <= 0) {
                        continue;
                    }
                    Map<PortableResourceDescriptor, Long> inputs = scaleAmounts(
                          baseInputs, operationCount);
                    Map<PortableResourceDescriptor, UUID> bindings = resolveAvailableBindings(
                          snapshot, inputs);
                    if (bindings == null) {
                        continue;
                    }
                    QIOPassiveOperation operation = new QIOPassiveOperation(UUID.randomUUID(),
                          device.host().getPersistentDeviceUUID(), provider.id().toString(),
                          route.routeId(), route.recipeKey(), operationCount, gameTick,
                          snapshot.getContentsRevision(), snapshot.getClaimRevision(), bindings,
                          inputs);
                    network.addPassiveOperation(operation);
                    persist(network);
                    return drivePassiveClaim(network, operation, view);
                }
            } finally {
                view.close();
            }
        }
        return 0;
    }

    static boolean acceptsCurrentRoutes(
          @Nullable MachineRecipeProviderRegistry.BoundProvider provider,
          @Nonnull QIOAutomationMode mode) {
        return provider != null && provider.validateQIOConformance(mode).isConformant();
    }

    private int drivePassiveOperation(QIOProcessingNetworkData network,
          QIOPassiveOperation operation) throws IOException {
        if (operation.getState() == QIOPassiveOperation.State.CLAIM_PREPARED ||
              operation.getState() == QIOPassiveOperation.State.CONSUME_PREPARED ||
              operation.getState() == QIOPassiveOperation.State.RELEASE_PREPARED) {
            IQIOStorageView view = openPassive(network);
            if (view == null) {
                return 0;
            }
            try {
                return drivePassiveClaim(network, operation, view);
            } finally {
                view.close();
            }
        }
        if (operation.getState() == QIOPassiveOperation.State.RETURNING) {
            IQIOStorageView view = openPassive(network);
            if (view == null) {
                return 0;
            }
            try {
                return drivePassiveReturn(network, operation, view);
            } finally {
                view.close();
            }
        }
        if (operation.getState() == QIOPassiveOperation.State.DELIVERING) {
            if (!reconcilePassiveMachineSettlement(network, operation)) {
                return 0;
            }
            IQIOStorageView view = openPassive(network);
            if (view == null) {
                return 0;
            }
            try {
                return drivePassiveDelivery(network, operation, view);
            } finally {
                view.close();
            }
        }
        MachineEndpoint endpoint = passiveEndpoint(network, operation,
              operation.getState() == QIOPassiveOperation.State.RESERVED);
        if (endpoint == null) {
            LoadedDevice loaded = QIOAutomationDeviceRegistry.INSTANCE.findLoadedDevice(
                  operation.getDeviceUUID());
            if (loaded != null) {
                if (operation.getState() == QIOPassiveOperation.State.RESERVED) {
                    operation.fail("Passive provider changed before machine input was transferred");
                    network.markPassiveOperationChanged(operation.getOperationId());
                    persist(network);
                    return 1;
                }
                if (operation.getState() == QIOPassiveOperation.State.LOADING) {
                    QIODurableTransferRecord transfer = firstOperationTransferAny(network, operation,
                          QIODurableTransferRecord.Type.JOB_TO_MACHINE);
                    if (transfer != null &&
                          transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED &&
                          loaded.host().getOperationTokens().get(operation.getOperationId()) == null) {
                        if (transfer.getLeaseId() != null) {
                            loaded.host().releaseUnattachedLease(transfer.getLeaseId());
                        }
                        network.discardPreparedGenericTransfer(transfer.getTransferId());
                        operation.abandonLoading(
                              "Passive provider changed before machine input was transferred");
                        network.markPassiveOperationChanged(operation.getOperationId());
                        persist(network);
                        return 1;
                    }
                }
            }
            return 0;
        }
        QIOAutomationHost host = endpoint.device.host();
        MachineOperationToken restoredToken = host.getOperationTokens().get(
              operation.getOperationId());
        if (restoredToken != null &&
              restoredToken.state() == MachineOperationToken.State.CONTAMINATED) {
            MachineOperationLease contaminated = host.getLeases().get(restoredToken.leaseId());
            String reason = contaminated != null && contaminated.contaminationReason() != null ?
                  contaminated.contaminationReason() : "Passive machine operation was contaminated";
            IQIOStorageView view = openPassive(network);
            if (view == null || !releaseConfigurationClaims(network,
                  operation.getOperationId(), view)) {
                if (view != null) view.close();
                return 0;
            }
            view.close();
            operation.fail(reason);
            network.markPassiveOperationChanged(operation.getOperationId());
            persist(network);
            return 1;
        }
        if (restoredToken != null) {
            MachineOperationLease restoredLease = host.getLeases().get(restoredToken.leaseId());
            if (restoredLease != null) {
                endpoint = withBaselines(endpoint, restoredLease.baselines());
            }
        } else if (operation.getState() == QIOPassiveOperation.State.CONFIGURING ||
              operation.getState() == QIOPassiveOperation.State.LOADING ||
              operation.getState() == QIOPassiveOperation.State.ACTIVE) {
            QIODurableTransferRecord inputTransfer = firstOperationTransferAny(network, operation,
                  QIODurableTransferRecord.Type.JOB_TO_MACHINE);
            if (inputTransfer != null && !inputTransfer.getMachineBaselines().isEmpty()) {
                endpoint = withBaselines(endpoint, inputTransfer.getMachineBaselines());
            }
        }
        if (operation.getState() == QIOPassiveOperation.State.CLAIMED) {
            IQIOStorageView view = openPassive(network);
            if (view == null) return 0;
            try {
                if (!configurationPreflight(endpoint, view.getSnapshot(), view) ||
                      !machineReadyForInput(endpoint)) return 0;
            } finally {
                view.close();
            }
            UUID leaseId = UUID.randomUUID();
            operation.markConfiguring(leaseId, endpoint.laneId);
            network.markPassiveOperationChanged(operation.getOperationId());
            QIODurableTransferRecord transfer = new QIODurableTransferRecord(UUID.randomUUID(),
                  UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_MACHINE, null,
                  operation.getOperationId(), 0, "passive-input", leaseId,
                  "passive/" + operation.getOperationId() + "/input",
                  "machine/" + operation.getDeviceUUID() + "/lane/" + endpoint.laneId,
                  operation.getRequiredInputs(), endpoint.baselines);
            network.addDurableTransfer(transfer);
            persist(network);
        }
        if (operation.getState() == QIOPassiveOperation.State.CONFIGURING) {
            QIODurableTransferRecord transfer = firstOperationTransferAny(network, operation,
                  QIODurableTransferRecord.Type.JOB_TO_MACHINE);
            if (transfer == null || !ensurePassiveMachineOperation(operation, endpoint,
                  transfer)) return 0;
            IQIOStorageView view = openPassive(network);
            if (view == null) return 0;
            try {
                if (!driveConfigurationExchanges(network, endpoint,
                      operation.getOperationId(), null, view)) return 0;
                Map<PortableResourceDescriptor, BigInteger> baselines =
                      capturePassiveBaselines(operation, view);
                if (baselines == null) return 0;
                operation.prepareConsume(baselines);
                network.markPassiveOperationChanged(operation.getOperationId());
                persist(network);
                return 1;
            } finally {
                view.close();
            }
        }
        if (operation.getState() == QIOPassiveOperation.State.RESERVED) {
            UUID leaseId = UUID.randomUUID();
            operation.markLoading(leaseId, endpoint.laneId);
            network.markPassiveOperationChanged(operation.getOperationId());
            QIODurableTransferRecord transfer = new QIODurableTransferRecord(UUID.randomUUID(),
                  UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_MACHINE, null,
                  operation.getOperationId(), 0, "passive-input", leaseId,
                  "passive/" + operation.getOperationId() + "/input",
                  "machine/" + operation.getDeviceUUID() + "/lane/" + endpoint.laneId,
                  operation.getInputBuffer(), endpoint.baselines);
            network.addDurableTransfer(transfer);
            persist(network);
        }
        if (operation.getState() == QIOPassiveOperation.State.LOADING) {
            QIODurableTransferRecord transfer = firstOperationTransferAny(network, operation,
                  QIODurableTransferRecord.Type.JOB_TO_MACHINE);
            if (transfer != null && ensurePassiveMachineOperation(operation, endpoint, transfer)) {
                IQIOStorageView view = openPassive(network);
                if (view != null) {
                    try {
                        if (driveConfigurationExchanges(network, endpoint,
                              operation.getOperationId(), null, view)) {
                            drivePassiveInputTransfer(network, operation, endpoint, transfer);
                        }
                    } finally {
                        view.close();
                    }
                }
                return 1;
            }
        }
        if (operation.getState() == QIOPassiveOperation.State.ACTIVE) {
            if (host.getOperationTokens().get(operation.getOperationId()) == null) {
                QIODurableTransferRecord transfer = firstOperationTransferAny(network, operation,
                      QIODurableTransferRecord.Type.JOB_TO_MACHINE);
                if (transfer != null && ensurePassiveMachineOperation(operation, endpoint,
                  transfer)) {
                    IQIOStorageView view = openPassive(network);
                    if (view != null) {
                        try {
                            if (driveConfigurationExchanges(network, endpoint,
                                  operation.getOperationId(), null, view)) {
                                drivePassiveInputTransfer(network, operation, endpoint, transfer);
                            }
                        } finally {
                            view.close();
                        }
                    }
                    return 1;
                }
                return 0;
            }
            if (!configurationStillMatches(endpoint)) {
                contaminateConfigurationOperation(network, endpoint,
                      operation.getOperationId(),
                      "Passive configuration changed while the lease was active");
                return 1;
            }
            if (!machineOutputsReady(endpoint)) {
                return 0;
            }
            if (!host.transitionOperation(operation.getOperationId(),
                  MachineOperationToken.State.COLLECTING,
                  MachineOperationLease.State.COLLECTING)) {
                return 0;
            }
            Map<PortableResourceDescriptor, Long> outputs = machineOutputAmounts(endpoint);
            QIODurableTransferRecord transfer = new QIODurableTransferRecord(UUID.randomUUID(),
                  UUID.randomUUID(), QIODurableTransferRecord.Type.MACHINE_TO_JOB, null,
                  operation.getOperationId(), 0, "passive-output", operation.getLeaseId(),
                  "machine/" + operation.getDeviceUUID() + "/lane/" + endpoint.laneId,
                  "passive/" + operation.getOperationId() + "/output", outputs);
            network.addDurableTransfer(transfer);
            persist(network);
            return drivePassiveMachineOutput(network, operation, endpoint, transfer);
        }
        if (operation.getState() == QIOPassiveOperation.State.COLLECTING) {
            if (!configurationStillMatches(endpoint)) {
                contaminateConfigurationOperation(network, endpoint,
                      operation.getOperationId(),
                      "Passive configuration changed before output collection");
                return 1;
            }
            QIODurableTransferRecord transfer = firstOperationTransferAny(network, operation,
                  QIODurableTransferRecord.Type.MACHINE_TO_JOB);
            return transfer == null ? 0 : drivePassiveMachineOutput(network, operation, endpoint,
                  transfer);
        }
        return 0;
    }

    private int drivePassiveClaim(QIOProcessingNetworkData network,
          QIOPassiveOperation operation, IQIOStorageView view) throws IOException {
        Map<UUID, Long> amounts = boundAmounts(operation);
        if (operation.getState() == QIOPassiveOperation.State.CLAIM_PREPARED) {
            QIOClaimResult result = view.submitClaim(QIOClaimRequest.createOrAdjust(
                  operation.getClaimRequestId(), operation.getClaimId(),
                  MekanismQIOProcessing.MODID, passiveOwner(operation), 0,
                  operation.getCreatedAtTick(), operation.getExpectedContentsRevision(),
                  operation.getExpectedClaimRevision(), amounts));
            if (!result.isSuccess() || !result.getCommittedAmounts().equals(amounts)) {
                operation.markPartialClaim(unbindAmounts(operation,
                            result.getCommittedAmounts()), result.getClaimRevision(),
                      "Passive input claim was not fully committed: " + result.getStatus());
                network.markPassiveOperationChanged(operation.getOperationId());
                persist(network);
                return 1;
            }
            operation.markClaimed(result.getClaimRevision());
            network.markPassiveOperationChanged(operation.getOperationId());
            persist(network);
        }
        if (operation.getState() == QIOPassiveOperation.State.CONSUME_PREPARED) {
            Map<UUID, BigInteger> baselines = boundBaselines(operation);
            QIOClaimRequest consume = QIOClaimRequest.consumeReconciled(
                  operation.getConsumeRequestId(), operation.getConsumeTransferId(),
                  operation.getClaimId(), MekanismQIOProcessing.MODID,
                  passiveOwner(operation), QIOClaimRequest.ANY_REVISION,
                  QIOClaimRequest.ANY_REVISION, amounts, baselines);
            QIOClaimResult result = view.submitClaim(consume);
            if (!result.isSuccess() || !result.getConsumedAmounts().equals(amounts)) {
                operation.fail("Passive input claim could not be consumed: " +
                      result.getStatus());
                network.markPassiveOperationChanged(operation.getOperationId());
                persist(network);
                return 1;
            }
            operation.markReserved(result.getClaimRevision());
            network.markPassiveOperationChanged(operation.getOperationId());
            persist(network);
        }
        if (operation.getState() == QIOPassiveOperation.State.RELEASE_PREPARED) {
            QIOClaimResult result = view.submitClaim(QIOClaimRequest.release(
                  operation.getReleaseRequestId(), operation.getClaimId(),
                  MekanismQIOProcessing.MODID, passiveOwner(operation),
                  QIOClaimRequest.ANY_REVISION, Collections.emptyMap()));
            if (!result.isSuccess() && result.getStatus() != QIOClaimResult.Status.NOT_FOUND) {
                return 0;
            }
            operation.markClaimReleased();
            network.markPassiveOperationChanged(operation.getOperationId());
            persist(network);
        }
        return 1;
    }

    private void drivePassiveInputTransfer(QIOProcessingNetworkData network,
          QIOPassiveOperation operation, MachineEndpoint endpoint,
          QIODurableTransferRecord transfer) throws IOException {
        QIOAutomationHost host = endpoint.device.host();
        MachineOperationToken token = host.getOperationTokens().get(operation.getOperationId());
        if (token == null) {
            return;
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            network.markGenericTransferSourceDebited(transfer.getTransferId(),
                  "passive-input-buffer", operation.getRuntimeRevision());
            persist(network);
        }
        if (transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED &&
              !token.hasTransferReceipt(transfer.getTransferId())) {
            MachineTransferPlan insertion = MachineTransferPlan.create(endpoint.device.tile());
            for (MachineResourceStack input : endpoint.route.inputs()) {
                insertion.addInsert(endpoint.ports.get(input.portId()), input);
            }
            boolean inserted = insertion.execute();
            if ((!inserted && !machineMatchesAfterInput(endpoint) &&
                  !machineMatchesCompletedOperation(endpoint)) ||
                  !host.recordTransferReceipt(operation.getOperationId(),
                        transfer.getTransferId())) {
                return;
            }
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            // Destination credit and transfer completion are checkpointed together.
            network.markGenericTransferDestinationCredited(transfer.getTransferId(),
                  "machine-input-ports");
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            network.commitGenericTransfer(transfer.getTransferId());
            if (operation.getState() == QIOPassiveOperation.State.LOADING) {
                operation.markActive();
                network.markPassiveOperationChanged(operation.getOperationId());
            }
            persist(network);
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.COMMITTED &&
              operation.getState() == QIOPassiveOperation.State.LOADING) {
            operation.markActive();
            network.markPassiveOperationChanged(operation.getOperationId());
            persist(network);
        }
        token = host.getOperationTokens().get(operation.getOperationId());
        if (token != null && token.state() == MachineOperationToken.State.LOADING) {
            host.transitionOperation(operation.getOperationId(),
                  MachineOperationToken.State.ACTIVE, MachineOperationLease.State.ACTIVE);
        }
    }

    private int drivePassiveMachineOutput(QIOProcessingNetworkData network,
          QIOPassiveOperation operation, MachineEndpoint endpoint,
          QIODurableTransferRecord transfer) throws IOException {
        QIOAutomationHost host = endpoint.device.host();
        MachineOperationToken token = host.getOperationTokens().get(operation.getOperationId());
        if (token == null) {
            return 0;
        }
        if (!token.hasTransferReceipt(transfer.getTransferId())) {
            MachineTransferPlan extraction = outputExtraction(endpoint);
            boolean extracted = extraction.execute();
            boolean exact = extracted ? aggregate(extraction.getExtracted()).equals(
                  transfer.getResources()) : machineMatchesLeaseBaseline(endpoint);
            if (!exact || !host.recordTransferReceipt(operation.getOperationId(),
                  transfer.getTransferId())) {
                return 0;
            }
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            network.markGenericTransferSourceDebited(transfer.getTransferId(),
                  "machine-output-ports", host.getConfigurationRevision());
            persist(network);
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            operation.markCollecting(transfer.getResources());
            network.markPassiveOperationChanged(operation.getOperationId());
            network.markGenericTransferDestinationCredited(transfer.getTransferId(),
                  "passive-output-buffer");
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            network.commitGenericTransfer(transfer.getTransferId());
            operation.markDelivering();
            network.markPassiveOperationChanged(operation.getOperationId());
            host.transitionOperation(operation.getOperationId(),
                  MachineOperationToken.State.COMPLETED, MachineOperationLease.State.COMPLETED);
            host.transitionOperation(operation.getOperationId(),
                  MachineOperationToken.State.COMPLETED, MachineOperationLease.State.RELEASED);
            persist(network);
        }
        token = host.getOperationTokens().get(operation.getOperationId());
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.COMMITTED && token != null &&
              token.state() == MachineOperationToken.State.COLLECTING) {
            if (!host.transitionOperation(operation.getOperationId(),
                  MachineOperationToken.State.COMPLETED,
                  MachineOperationLease.State.COMPLETED) ||
                  !host.transitionOperation(operation.getOperationId(),
                        MachineOperationToken.State.COMPLETED,
                        MachineOperationLease.State.RELEASED)) {
                return 0;
            }
        }
        return 1;
    }

    private boolean reconcilePassiveMachineSettlement(QIOProcessingNetworkData network,
          QIOPassiveOperation operation) throws IOException {
        LoadedDevice loaded = QIOAutomationDeviceRegistry.INSTANCE.findLoadedDevice(
              operation.getDeviceUUID());
        MachineEndpoint endpoint = passiveEndpoint(network, operation, false);
        if (loaded == null || endpoint == null) {
            return false;
        }
        QIODurableTransferRecord input = firstOperationTransferAny(network, operation,
              QIODurableTransferRecord.Type.JOB_TO_MACHINE);
        QIODurableTransferRecord output = firstOperationTransferAny(network, operation,
              QIODurableTransferRecord.Type.MACHINE_TO_JOB);
        if (input == null || output == null ||
              output.getPhase() != QIODurableTransferRecord.Phase.COMMITTED ||
              input.getMachineBaselines().isEmpty()) {
            return false;
        }
        endpoint = withBaselines(endpoint, input.getMachineBaselines());
        QIOAutomationHost host = loaded.host();
        MachineOperationToken token = host.getOperationTokens().get(operation.getOperationId());
        if (token != null) {
            if (token.state() == MachineOperationToken.State.ALLOCATED ||
                  token.state() == MachineOperationToken.State.LOADING) {
                if (ensurePassiveMachineOperation(operation, endpoint, input)) {
                    drivePassiveInputTransfer(network, operation, endpoint, input);
                }
            } else if (token.state() == MachineOperationToken.State.ACTIVE &&
                  (machineOutputsReady(endpoint) || machineMatchesLeaseBaseline(endpoint))) {
                if (host.transitionOperation(operation.getOperationId(),
                      MachineOperationToken.State.COLLECTING,
                      MachineOperationLease.State.COLLECTING)) {
                    drivePassiveMachineOutput(network, operation, endpoint, output);
                }
            } else if (token.state() == MachineOperationToken.State.COLLECTING) {
                drivePassiveMachineOutput(network, operation, endpoint, output);
            } else if (token.state() == MachineOperationToken.State.COMPLETED) {
                if (host instanceof DefaultQIOAutomationHost mutable &&
                      mutable.isPersistedCompletedOperation(operation.getOperationId())) {
                    host.forgetSettledOperation(operation.getOperationId());
                }
            }
            return host.getOperationTokens().get(operation.getOperationId()) == null;
        }
        if (machineMatchesLeaseBaseline(endpoint)) {
            return true;
        }
        if (machineMatchesCompletedOperation(endpoint)) {
            MachineTransferPlan extraction = outputExtraction(endpoint);
            return extraction.execute() && aggregate(extraction.getExtracted()).equals(
                  output.getResources());
        }
        if (machineMatchesAfterInput(endpoint) &&
              ensurePassiveMachineOperation(operation, endpoint, input)) {
            drivePassiveInputTransfer(network, operation, endpoint, input);
        }
        return false;
    }

    private int drivePassiveDelivery(QIOProcessingNetworkData network,
          QIOPassiveOperation operation, IQIOStorageView view) throws IOException {
        QIODurableTransferRecord active = firstOperationTransfer(network, operation,
              QIODurableTransferRecord.Type.JOB_TO_QIO);
        if (active == null) {
            if (operation.getOutputBuffer().isEmpty()) {
                return 0;
            }
            Map.Entry<PortableResourceDescriptor, Long> resource =
                  operation.getOutputBuffer().entrySet().iterator().next();
            long accepted = simulateInsert(view, resource.getKey(), resource.getValue());
            if (accepted <= 0) {
                return 0;
            }
            active = new QIODurableTransferRecord(UUID.randomUUID(), UUID.randomUUID(),
                  QIODurableTransferRecord.Type.JOB_TO_QIO, null, operation.getOperationId(), 0,
                  "passive-delivery", null, "passive/" + operation.getOperationId() + "/output",
                  "qio/" + network.getFrequencyUUID(), Collections.singletonMap(resource.getKey(),
                  accepted), Collections.singletonMap(resource.getKey(),
                  storedAmount(view, resource.getKey())));
            network.addDurableTransfer(active);
            persist(network);
        }
        if (active.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            network.markGenericTransferSourceDebited(active.getTransferId(),
                  "passive-output-buffer", operation.getRuntimeRevision());
            persist(network);
        }
        if (active.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            Map.Entry<PortableResourceDescriptor, Long> resource =
                  active.getResources().entrySet().iterator().next();
            QIOTransferResult result = insert(view, active.getTransferId(), resource.getKey(),
                  resource.getValue(), active.getQIOBaselines().get(resource.getKey()));
            if (!result.isSuccess() || result.getTransferredAmount() != resource.getValue()) {
                return 0;
            }
            network.markGenericTransferDestinationCredited(active.getTransferId(),
                  result.getStatus().name());
        }
        if (active.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            Map.Entry<PortableResourceDescriptor, Long> resource =
                  active.getResources().entrySet().iterator().next();
            operation.debitDelivered(resource.getKey(), resource.getValue());
            network.markPassiveOperationChanged(operation.getOperationId());
            network.commitGenericTransfer(active.getTransferId());
            persist(network);
        }
        return 1;
    }

    private int drivePassiveReturn(QIOProcessingNetworkData network,
          QIOPassiveOperation operation, IQIOStorageView view) throws IOException {
        QIODurableTransferRecord active = firstOperationTransfer(network, operation,
              QIODurableTransferRecord.Type.JOB_TO_QIO);
        if (active == null) {
            Map<PortableResourceDescriptor, Long> source = operation.getOutputBuffer().isEmpty() ?
                  operation.getInputBuffer() : operation.getOutputBuffer();
            if (source.isEmpty()) {
                return 0;
            }
            Map.Entry<PortableResourceDescriptor, Long> resource =
                  source.entrySet().iterator().next();
            long accepted = simulateInsert(view, resource.getKey(), resource.getValue());
            if (accepted <= 0) {
                return 0;
            }
            active = new QIODurableTransferRecord(UUID.randomUUID(), UUID.randomUUID(),
                  QIODurableTransferRecord.Type.JOB_TO_QIO, null, operation.getOperationId(), 0,
                  "passive-return", null, "passive/" + operation.getOperationId() + "/return",
                  "qio/" + network.getFrequencyUUID(), Collections.singletonMap(resource.getKey(),
                  accepted), Collections.singletonMap(resource.getKey(),
                  storedAmount(view, resource.getKey())));
            network.addDurableTransfer(active);
            persist(network);
        }
        if (active.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            network.markGenericTransferSourceDebited(active.getTransferId(),
                  "passive-return-buffer", operation.getRuntimeRevision());
            persist(network);
        }
        if (active.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            Map.Entry<PortableResourceDescriptor, Long> resource =
                  active.getResources().entrySet().iterator().next();
            QIOTransferResult result = insert(view, active.getTransferId(), resource.getKey(),
                  resource.getValue(), active.getQIOBaselines().get(resource.getKey()));
            if (!result.isSuccess() || result.getTransferredAmount() != resource.getValue()) {
                return 0;
            }
            network.markGenericTransferDestinationCredited(active.getTransferId(),
                  result.getStatus().name());
        }
        if (active.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            Map.Entry<PortableResourceDescriptor, Long> resource =
                  active.getResources().entrySet().iterator().next();
            operation.debitReturned(resource.getKey(), resource.getValue());
            network.markPassiveOperationChanged(operation.getOperationId());
            network.commitGenericTransfer(active.getTransferId());
            persist(network);
        }
        return 1;
    }

    private int driveJob(QIOProcessingNetworkData network, QIOCraftingJob job, long gameTick,
          int budget, QIOProcessingConfig config) throws IOException {
        int actions = 0;
        boolean mayDispatch = mayDispatchNewOperations(job);
        int activeBudget = activeOperationBudget(budget, job.hasActiveOperations(), mayDispatch);
        for (QIOStepRuntime runtime : job.getStepRuntimes().values()) {
            if (actions >= activeBudget) {
                break;
            }
            for (QIOOperationAssignment assignment : runtime.getActiveOperations(
                  activeBudget - actions)) {
                if (actions >= activeBudget) {
                    break;
                }
                actions += Math.max(1, driveOperation(network, job, runtime, assignment));
            }
        }
        if (actions >= budget) {
            return actions;
        }
        if (job.getState() == QIOCraftingJobState.PLANNING ||
              job.getState() == QIOCraftingJobState.REPLAN_DRAINING ||
              job.getState() == QIOCraftingJobState.REPLAN_REQUIRED) {
            if (!job.hasActiveOperations()) {
                return actions + deliver(network, job, budget - actions);
            }
            return actions;
        }
        if (job.isCancellationRequested()) {
            long active = job.getStepRuntimes().values().stream()
                  .mapToLong(QIOStepRuntime::getActiveAssignmentCount).sum();
            if (active > 0) {
                return actions;
            }
            if (job.getState() != QIOCraftingJobState.RETURNING) {
                network.transitionJobState(job.getJobId(), QIOCraftingJobState.RETURNING);
                persist(network);
                actions++;
                if (actions >= budget) {
                    return actions;
                }
            }
            return actions + deliver(network, job, budget - actions);
        }
        if (job.getState() == QIOCraftingJobState.DELIVERING ||
              job.getState() == QIOCraftingJobState.RETURNING ||
              job.areAllStepsComplete()) {
            return actions + deliver(network, job, budget - actions);
        }
        int delivered = deliverProgressiveRoot(network, job, budget - actions);
        actions += delivered;
        if (actions >= budget) {
            return actions;
        }
        long workbenchInFlight = activeOperationCount(job, ProviderKind.WORKBENCH);
        long machineInFlight = activeOperationCount(job, ProviderKind.MEKANISM);
        Map<Long, Long> workbenchCapByStep = new LinkedHashMap<>();
        Map<Long, Long> machineCapByStep = new LinkedHashMap<>();
        while (actions < budget) {
            final long currentWorkbenchInFlight = workbenchInFlight;
            final long currentMachineInFlight = machineInFlight;
            QIOPlanStep step = nextRunnableStep(job, network.getJobBuffer(job.getJobId()),
                  candidate -> candidate.getProviderKind() == ProviderKind.WORKBENCH ?
                        currentWorkbenchInFlight < workbenchCapByStep.computeIfAbsent(
                              candidate.getNodeId(), ignored -> workbenchConcurrencyLimit(
                                    network, candidate, config)) :
                        currentMachineInFlight < machineCapByStep.computeIfAbsent(
                              candidate.getNodeId(), ignored -> machineConcurrencyLimit(
                                    network, candidate, config)));
            if (step == null) {
                break;
            }
            int dispatched = step.getProviderKind() == ProviderKind.WORKBENCH ?
                  dispatchWorkbench(network, job, step, gameTick) :
                  dispatchMachine(network, job, step, gameTick);
            if (dispatched == 0) {
                break;
            }
            actions += dispatched;
            if (step.getProviderKind() == ProviderKind.WORKBENCH) {
                workbenchInFlight++;
            } else {
                machineInFlight++;
            }
        }
        return actions;
    }

    private static boolean mayDispatchNewOperations(QIOCraftingJob job) {
        QIOCraftingJobState state = job.getState();
        return !state.isTerminal() && !job.isCancellationRequested() &&
              state != QIOCraftingJobState.PLANNING &&
              state != QIOCraftingJobState.REPLAN_DRAINING &&
              state != QIOCraftingJobState.REPLAN_REQUIRED &&
              state != QIOCraftingJobState.DELIVERING &&
              state != QIOCraftingJobState.RETURNING && !job.areAllStepsComplete();
    }

    static int activeOperationBudget(int totalBudget, boolean hasActiveOperations,
          boolean mayDispatchNewOperations) {
        if (totalBudget < 0) {
            throw new IllegalArgumentException("QIO execution budget cannot be negative");
        }
        if (!hasActiveOperations || !mayDispatchNewOperations || totalBudget <= 1) {
            return totalBudget;
        }
        return Math.max(1, totalBudget / 2);
    }

    private int deliverProgressiveRoot(QIOProcessingNetworkData network, QIOCraftingJob job,
          int budget) throws IOException {
        if (budget <= 0) {
            return 0;
        }
        List<QIODurableTransferRecord> active = network.getActiveJobTransfers(job.getJobId(),
              QIODurableTransferRecord.Type.JOB_TO_QIO);
        IQIOStorageView view = open(network, job);
        if (view == null) {
            return 0;
        }
        try {
            if (!active.isEmpty()) {
                return driveReturnTransfer(network, active.get(0), view);
            }
            QIOJobBuffer buffer = network.getJobBuffer(job.getJobId());
            PortableResourceDescriptor root = job.getActivePlan().getRootResource();
            long amount = buffer == null ? 0 : buffer.get(
                  QIOJobBuffer.Compartment.SETTLED_OUTPUT, root);
            if (amount <= 0) {
                return 0;
            }
            long accepted = simulateInsert(view, root, amount);
            if (accepted <= 0) {
                return 0;
            }
            QIODurableTransferRecord transfer = new QIODurableTransferRecord(UUID.randomUUID(),
                  UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_QIO,
                  job.getJobId(), null, job.getActivePlan().getRevision(),
                  QIOProcessingNetworkData.PROGRESSIVE_ROOT_DELIVERY_NODE_ID, null,
                  "job/" + job.getJobId() + "/settled-output",
                  "qio/" + network.getFrequencyUUID(),
                  Collections.singletonMap(root, accepted),
                  Collections.singletonMap(root, storedAmount(view, root)));
            network.addDurableTransfer(transfer);
            persist(network);
            return driveReturnTransfer(network, transfer, view);
        } finally {
            view.close();
        }
    }

    private int dispatchWorkbench(QIOProcessingNetworkData network, QIOCraftingJob job,
          QIOPlanStep step, long gameTick) throws IOException {
        if (step.getProviderKind() != ProviderKind.WORKBENCH ||
              !QIORecipeCatalogService.INSTANCE.isInitialized()) {
            if (job.getState() != QIOCraftingJobState.WAITING_PROVIDER) {
                network.transitionJobState(job.getJobId(), QIOCraftingJobState.WAITING_PROVIDER);
                persist(network);
                return 1;
            }
            return 0;
        }
        QIOJobBuffer buffer = network.getJobBuffer(job.getJobId());
        QIOWorkbenchRecipeCatalog.Snapshot catalog =
              QIORecipeCatalogService.INSTANCE.getSnapshot(network.getWorkbenchConfiguration());
        QIOWorkbenchRecipePattern pattern = step.getCandidateInputs().isEmpty() ?
              catalog.getPattern(step.getStableRouteId()) : buffer == null ? null :
              catalog.resolveExecutablePattern(step.getRouteId(),
                    buffer.getConsumableAmounts(), step.getGuaranteedOutputs(),
                    network.getWorkbenchConfiguration());
        if (pattern == null || step.getCandidateInputs().isEmpty() &&
              !pattern.getRouteSignature().equals(step.getRouteSignature()) ||
              !pattern.getExpectedOutputs().equals(step.getGuaranteedOutputs())) {
            network.transitionJobState(job.getJobId(), QIOCraftingJobState.RETURNING);
            persist(network);
            return 1;
        }
        String routeKey = pattern.getStableRouteId();
        List<LoadedProcessor> processors = new ArrayList<>(
              QIOCraftingProcessorDeviceRegistry.INSTANCE.getAvailable(network.getFrequencyUUID()));
        processors.removeIf(loaded -> !network.getPolicies().isRouteEnabled(
              loaded.processorUUID(), step.getProviderId(), step.getRouteId(),
              step.getRecipeKey()));
        processors = orderProvidersRoundRobin(processors, LoadedProcessor::processorUUID,
              loaded -> network.getPolicies().machinePriority(loaded.processorUUID()),
              priority -> providerCursors.get(new ProviderCursorKey(network.getFrequencyUUID(),
                    ProviderKind.WORKBENCH, routeKey, priority)));
        for (LoadedProcessor loaded : processors) {
            Long laneId = loaded.processor().getProcessorState().findAvailableLaneId();
            if (laneId == null) {
                continue;
            }
            QIOStepRuntime runtime = job.getStepRuntime(step.getNodeId());
            if (runtime == null || runtime.getRemainingOperations() <= 0) {
                return 0;
            }
            long operationCount = Math.min(runtime.getRemainingOperations(),
                  loaded.processor().getOperationsPerBatch());
            operationCount = Math.min(operationCount, job.getCycleDispatchAllowance(step.getNodeId()));
            operationCount = maxBufferOperations(buffer,
                  pattern.getExactInputs(), operationCount);
            if (operationCount <= 0) {
                return 0;
            }
            UUID operationId = UUID.randomUUID();
            QIOOperationAssignment assignment = new QIOOperationAssignment(operationId,
                  ProviderKind.WORKBENCH, loaded.processorUUID(), laneId, operationCount,
                  gameTick, routeKey);
            QIODurableTransferRecord transfer = new QIODurableTransferRecord(UUID.randomUUID(),
                  UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_MACHINE,
                  job.getJobId(), null, job.getActivePlan().getRevision(),
                  operationKey(step.getNodeId(), operationId), null,
                  "job/" + job.getJobId() + "/buffer",
                  "processor/" + loaded.processorUUID() + "/lane/" + laneId,
                  scaleAmounts(pattern.getExactInputs(), operationCount));
            network.startStepOperation(job.getJobId(), step.getNodeId(), assignment);
            network.addDurableTransfer(transfer);
            persist(network);
            QIOProcessorLaneRuntime lane = loaded.processor().getProcessorState().acquireLane(
                  laneId, operationId, job.getJobId(), job.getActivePlan().getRevision(), routeKey,
                  operationCount);
            if (lane == null) {
                network.discardPreparedGenericTransfer(transfer.getTransferId());
                network.failStepOperation(job.getJobId(), step.getNodeId(), operationId,
                      "Processor lane became unavailable before assignment");
                persist(network);
                return 1;
            }
            advanceProviderCursor(network.getFrequencyUUID(), ProviderKind.WORKBENCH, routeKey,
                  network.getPolicies().machinePriority(loaded.processorUUID()),
                  loaded.processorUUID());
            driveJobToMachineTransfer(network, transfer, lane);
            return 1;
        }
        if (job.getState() != QIOCraftingJobState.WAITING_PROVIDER) {
            network.transitionJobState(job.getJobId(), QIOCraftingJobState.WAITING_PROVIDER);
            persist(network);
            return 1;
        }
        return 0;
    }

    private int driveOperation(QIOProcessingNetworkData network, QIOCraftingJob job,
          QIOStepRuntime runtime, QIOOperationAssignment assignment) throws IOException {
        if (assignment.getProviderKind() == ProviderKind.MEKANISM) {
            return driveMachineOperation(network, job, runtime, assignment);
        }
        LoadedProcessor loaded = QIOCraftingProcessorDeviceRegistry.INSTANCE.findAvailable(
              assignment.getDeviceUUID(), network.getFrequencyUUID());
        if (loaded == null) {
            if (job.getState() != QIOCraftingJobState.WAITING_PROVIDER) {
                network.transitionJobState(job.getJobId(), QIOCraftingJobState.WAITING_PROVIDER);
                persist(network);
                return 1;
            }
            return 0;
        }
        QIOPlanStep step = planStep(job, runtime.getNodeId());
        if (step == null) {
            throw new IllegalStateException("QIO operation references a missing plan step");
        }
        QIOProcessorLaneRuntime lane = loaded.processor().getProcessorState().getLane(
              assignment.getLaneId());
        QIODurableTransferRecord settledProcessorTransfer = lane == null ?
              committedMachineOutputTransfer(network, job.getJobId(), runtime.getNodeId(),
                    assignment.getOperationId()) : null;
        if (settledProcessorTransfer != null) {
            return settledProcessorTransfer.getDestination().endsWith("/returned") ?
                  settleReturnedJobOperation(network, job, runtime, assignment) :
                  settleCompletedJobOperation(network, job, runtime, assignment);
        }
        if (lane == null) {
            lane = loaded.processor().getProcessorState().acquireLane(assignment.getLaneId(),
                  assignment.getOperationId(), job.getJobId(), job.getActivePlan().getRevision(),
                  assignment.getExecutionRouteKey().isEmpty() ? step.getStableRouteId() :
                        assignment.getExecutionRouteKey(), assignment.getOperationCount());
        }
        if (lane == null || !lane.getOperationId().equals(assignment.getOperationId())) {
            return 0;
        }
        if (job.isCancellationRequested() &&
              (lane.getState() == QIOProcessorLaneRuntime.State.LOADING ||
                    lane.getState() == QIOProcessorLaneRuntime.State.READY ||
                    lane.getState() == QIOProcessorLaneRuntime.State.PROCESSING)) {
            if (lane.getState() == QIOProcessorLaneRuntime.State.LOADING) {
                QIODurableTransferRecord transfer = findOperationTransferAny(network, job.getJobId(),
                      QIODurableTransferRecord.Type.JOB_TO_MACHINE, runtime.getNodeId(),
                      assignment.getOperationId());
                if (transfer != null) {
                    driveJobToMachineTransfer(network, transfer, lane);
                }
            }
            if (lane.getState() == QIOProcessorLaneRuntime.State.LOADING ||
                  lane.getState() == QIOProcessorLaneRuntime.State.READY) {
                lane.beginReturn();
            } else if (lane.getState() == QIOProcessorLaneRuntime.State.PROCESSING) {
                lane.abortProcessing();
            }
        }
        if (lane.getState() == QIOProcessorLaneRuntime.State.LOADING) {
            QIODurableTransferRecord transfer = findOperationTransferAny(network, job.getJobId(),
                  QIODurableTransferRecord.Type.JOB_TO_MACHINE, runtime.getNodeId(),
                  assignment.getOperationId());
            if (transfer == null) {
                if (!lane.getInput().isEmpty()) {
                    lane.markReady();
                }
            } else {
                driveJobToMachineTransfer(network, transfer, lane);
            }
        }
        if (lane.getState() == QIOProcessorLaneRuntime.State.READY ||
              lane.getState() == QIOProcessorLaneRuntime.State.PROCESSING) {
            return network.updateStepOperation(job.getJobId(), runtime.getNodeId(),
                  assignment.getOperationId(), QIOOperationAssignment.State.PROCESSING,
                  lane.getCurrentTick(), lane.getTotalTicks(), null) ? 1 : 0;
        }
        if (lane.getState() == QIOProcessorLaneRuntime.State.OUTPUT_BLOCKED) {
            return collectOutput(network, job, runtime, assignment, loaded, lane);
        }
        if (lane.getState() == QIOProcessorLaneRuntime.State.RETURNING) {
            return collectReturn(network, job, runtime, assignment, loaded, lane);
        }
        if (lane.getState() == QIOProcessorLaneRuntime.State.SETTLED) {
            return settleProcessorLane(network, job, runtime, assignment, loaded, lane);
        }
        return 0;
    }

    private int dispatchMachine(QIOProcessingNetworkData network, QIOCraftingJob job,
          QIOPlanStep step, long gameTick) throws IOException {
        MachineEndpoint endpoint = findMachineEndpoint(network, step, null,
              null, null, true);
        if (endpoint == null) {
            if (job.getState() != QIOCraftingJobState.WAITING_PROVIDER) {
                network.transitionJobState(job.getJobId(), QIOCraftingJobState.WAITING_PROVIDER);
                persist(network);
                return 1;
            }
            return 0;
        }
        IQIOStorageView configurationView = open(network, job);
        if (configurationView == null) return 0;
        try {
            if (!configurationPreflight(endpoint, configurationView.getSnapshot(),
                  configurationView)) return 0;
        } finally {
            configurationView.close();
        }
        QIOStepRuntime runtime = job.getStepRuntime(step.getNodeId());
        if (runtime == null || runtime.getRemainingOperations() <= 0 ||
            job.getCycleDispatchAllowance(step.getNodeId()) <= 0) {
            return 0;
        }
        UUID deviceUUID = endpoint.device.host().getPersistentDeviceUUID();
        QIOAutomationRecipeProfile profile = network.getAutomationRecipeProfiles()
              .getActiveProfile(deviceUUID, QIOAutomationMode.SCHEDULED,
                    QIOAutomationRecipeProfileScope.resolve(endpoint.provider));
        String profileRouteKey = QIOAutomationRecipeProfileLayout.routeKey(
              endpoint.route.routeId(), endpoint.route.logicalRecipeKey());
        long operationCount = Math.min(runtime.getRemainingOperations(),
              job.getCycleDispatchAllowance(step.getNodeId()));
        operationCount = Math.min(operationCount, profile.getEffectiveCraftAmount(
              profileRouteKey, endpoint.route.optionalOutputs().isEmpty() ?
                    endpoint.route.getMaxOperations() : 1));
        operationCount = maxBufferOperations(network.getJobBuffer(job.getJobId()),
              step.getExactInputs(), operationCount);
        operationCount = maxMachineOperations(endpoint, operationCount);
        if (operationCount <= 0) {
            return 0;
        }
        endpoint = scaleEndpoint(endpoint, operationCount);
        UUID operationId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        QIOOperationAssignment assignment = new QIOOperationAssignment(operationId,
              ProviderKind.MEKANISM, deviceUUID, endpoint.laneId, operationCount, gameTick);
        QIODurableTransferRecord transfer = new QIODurableTransferRecord(UUID.randomUUID(),
              UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_MACHINE,
              job.getJobId(), null, job.getActivePlan().getRevision(),
              operationKey(step.getNodeId(), operationId), leaseId,
              "job/" + job.getJobId() + "/buffer",
              "machine/" + assignment.getDeviceUUID() + "/lane/" + endpoint.laneId,
              scaleAmounts(step.getExactInputs(), operationCount), endpoint.baselines);
        network.startStepOperation(job.getJobId(), step.getNodeId(), assignment);
        network.addDurableTransfer(transfer);
        persist(network);
        if (ensureScheduledMachineOperation(job, assignment, endpoint, transfer)) {
            advanceProviderCursor(network.getFrequencyUUID(), ProviderKind.MEKANISM,
                  step.getStableRouteId(), network.getPolicies().machinePriority(deviceUUID),
                  deviceUUID);
            IQIOStorageView view = open(network, job);
            if (view != null) {
                try {
                    if (driveConfigurationExchanges(network, endpoint, operationId,
                          job.getJobId(), view)) {
                        driveJobToMachineTransfer(network, transfer, endpoint);
                    }
                } finally {
                    view.close();
                }
            }
        }
        return 1;
    }

    private int driveMachineOperation(QIOProcessingNetworkData network, QIOCraftingJob job,
          QIOStepRuntime runtime, QIOOperationAssignment assignment) throws IOException {
        LoadedDevice device = QIOAutomationDeviceRegistry.INSTANCE.findLoadedDevice(
              assignment.getDeviceUUID());
        if (device == null || !matchesFrequencyOrOwnedDrain(network, device.host(),
              QIOAutomationMode.SCHEDULED, assignment.getOperationId())) {
            if (job.isCancellationRequested()) {
                QIOAutomationHost host = device == null ? null : device.host();
                MachineOperationToken token = host == null ? null :
                      host.getOperationTokens().get(assignment.getOperationId());
                return cancelScheduledMachineOperation(network, job, runtime, assignment,
                      host, token, null);
            }
            if (job.getState() != QIOCraftingJobState.WAITING_PROVIDER) {
                network.transitionJobState(job.getJobId(), QIOCraftingJobState.WAITING_PROVIDER);
                persist(network);
                return 1;
            }
            return 0;
        }
        QIOPlanStep step = planStep(job, runtime.getNodeId());
        MachineOperationToken token = device.host().getOperationTokens().get(
              assignment.getOperationId());
        if (token == null && hasCommittedMachineOutput(network, job.getJobId(),
              runtime.getNodeId(), assignment.getOperationId()) && endpointAtBaseline(
                    network, job, runtime, assignment)) {
            return settleCompletedJobOperation(network, job, runtime, assignment);
        }
        if (job.isCancellationRequested() && token == null) {
            return cancelScheduledMachineOperation(network, job, runtime, assignment,
                  device.host(), null, null);
        }
        MachineEndpoint endpoint = step == null ? null : findMachineEndpoint(
              network, step, token, assignment.getDeviceUUID(),
              assignment.getOperationId(), false);
        if (endpoint != null) {
            endpoint = scaleEndpoint(endpoint, assignment.getOperationCount());
        }
        if (token == null && endpoint != null && endpoint.laneId == assignment.getLaneId()) {
            QIODurableTransferRecord transfer = findOperationTransferAny(network, job.getJobId(),
                  QIODurableTransferRecord.Type.JOB_TO_MACHINE, runtime.getNodeId(),
                  assignment.getOperationId());
            if (transfer != null) {
                if (!transfer.getMachineBaselines().isEmpty()) {
                    endpoint = withBaselines(endpoint, transfer.getMachineBaselines());
                }
            }
            if (transfer != null && ensureScheduledMachineOperation(job, assignment, endpoint, transfer)) {
                token = device.host().getOperationTokens().get(assignment.getOperationId());
            }
        }
        if (token == null && endpoint == null) {
            QIODurableTransferRecord transfer = findOperationTransferAny(network, job.getJobId(),
                  QIODurableTransferRecord.Type.JOB_TO_MACHINE, runtime.getNodeId(),
                  assignment.getOperationId());
            if (transfer != null && transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
                if (transfer.getLeaseId() != null) {
                    device.host().releaseUnattachedLease(transfer.getLeaseId());
                }
                network.discardPreparedGenericTransfer(transfer.getTransferId());
                network.failStepOperation(job.getJobId(), runtime.getNodeId(),
                      assignment.getOperationId(), "Scheduled provider changed before input transfer");
                network.transitionJobState(job.getJobId(), QIOCraftingJobState.RETURNING);
                persist(network);
                return 1;
            }
        }
        if (token == null) {
            return 0;
        }
        endpoint = step == null ? null : findMachineEndpoint(network, step, token,
              assignment.getDeviceUUID(), assignment.getOperationId(), false);
        if (endpoint != null) {
            endpoint = scaleEndpoint(endpoint, assignment.getOperationCount());
        }
        MachineOperationLease activeLease = device.host().getLeases().get(token.leaseId());
        if (token.state() == MachineOperationToken.State.CONTAMINATED) {
            IQIOStorageView view = open(network, job);
            if (view == null || !releaseConfigurationClaims(network,
                  assignment.getOperationId(), view)) {
                if (view != null) view.close();
                return 0;
            }
            view.close();
            network.contaminateStepOperation(job.getJobId(), runtime.getNodeId(),
                  assignment.getOperationId(), activeLease != null &&
                        activeLease.contaminationReason() != null ?
                        activeLease.contaminationReason() : "Machine operation was contaminated");
            persist(network);
            return 1;
        }
        if (job.isCancellationRequested() &&
              token.state() != MachineOperationToken.State.COLLECTING &&
              token.state() != MachineOperationToken.State.COMPLETED) {
            if (endpoint != null && endpoint.device.host() == device.host() &&
                  activeLease != null) {
                endpoint = withBaselines(endpoint, activeLease.baselines());
            } else {
                endpoint = null;
            }
            if (endpoint != null && token.state() == MachineOperationToken.State.LOADING &&
                  !network.getOperationConfigurationExchanges(
                        assignment.getOperationId()).isEmpty()) {
                IQIOStorageView view = open(network, job);
                if (view == null) return 0;
                try {
                    if (!settleConfigurationCancellation(network, endpoint,
                          assignment.getOperationId(), view)) return 0;
                } finally {
                    view.close();
                }
            }
            return cancelScheduledMachineOperation(network, job, runtime, assignment,
                  device.host(), token, endpoint);
        }
        if (endpoint == null || endpoint.device.host() != device.host()) {
            return 0;
        }
        if (activeLease == null) {
            return 0;
        }
        endpoint = withBaselines(endpoint, activeLease.baselines());
        if (token.state() == MachineOperationToken.State.LOADING) {
            QIODurableTransferRecord transfer = findOperationTransferAny(network, job.getJobId(),
                  QIODurableTransferRecord.Type.JOB_TO_MACHINE, runtime.getNodeId(),
                  assignment.getOperationId());
            if (transfer != null) {
                IQIOStorageView view = open(network, job);
                if (view != null) {
                    try {
                        if (driveConfigurationExchanges(network, endpoint,
                              assignment.getOperationId(), job.getJobId(), view)) {
                            driveJobToMachineTransfer(network, transfer, endpoint);
                        }
                    } finally {
                        view.close();
                    }
                }
                token = device.host().getOperationTokens().get(assignment.getOperationId());
            }
        }
        if (token != null && token.state() == MachineOperationToken.State.ACTIVE) {
            if (!configurationStillMatches(endpoint)) {
                contaminateConfigurationOperation(network, endpoint,
                      assignment.getOperationId(), "Configuration changed while the lease was active");
                return 1;
            }
            if (machineOutputsReady(endpoint)) {
                if (!device.host().transitionOperation(assignment.getOperationId(),
                      MachineOperationToken.State.COLLECTING,
                      MachineOperationLease.State.COLLECTING)) {
                    return 0;
                }
                return collectMachineOutput(network, job, runtime, assignment, endpoint);
            }
            MachineActivitySnapshot activity = device.host().getActivitySnapshots().get(
                  assignment.getLaneId());
            long current = activity != null && assignment.getOperationId().equals(
                  activity.operationId()) ? activity.currentTick() : 0;
            long total = activity != null && assignment.getOperationId().equals(
                  activity.operationId()) ? activity.totalTicks() : 0;
            return network.updateStepOperation(job.getJobId(), runtime.getNodeId(),
                  assignment.getOperationId(), QIOOperationAssignment.State.PROCESSING,
                  current, total, null) ? 1 : 0;
        }
        if (token != null && token.state() == MachineOperationToken.State.COLLECTING) {
            if (!configurationStillMatches(endpoint)) {
                contaminateConfigurationOperation(network, endpoint,
                      assignment.getOperationId(), "Configuration changed before output collection");
                return 1;
            }
            return collectMachineOutput(network, job, runtime, assignment, endpoint);
        }
        if (token != null && token.state() == MachineOperationToken.State.COMPLETED) {
            if (!(device.host() instanceof DefaultQIOAutomationHost mutable) ||
                  !mutable.isPersistedCompletedOperation(assignment.getOperationId())) {
                return 0;
            }
            int settled = settleCompletedJobOperation(network, job, runtime, assignment);
            device.host().forgetSettledOperation(assignment.getOperationId());
            return settled;
        }
        return 0;
    }

    private int cancelScheduledMachineOperation(QIOProcessingNetworkData network,
          QIOCraftingJob job, QIOStepRuntime runtime, QIOOperationAssignment assignment,
          @Nullable QIOAutomationHost host, @Nullable MachineOperationToken token,
          @Nullable MachineEndpoint endpoint) throws IOException {
        QIODurableTransferRecord output = findOperationTransferAny(network, job.getJobId(),
              QIODurableTransferRecord.Type.MACHINE_TO_JOB, runtime.getNodeId(),
              assignment.getOperationId());
        if (output != null) {
            if (output.getPhase() == QIODurableTransferRecord.Phase.COMMITTED &&
                  output.getResolution() == QIODurableTransferRecord.Resolution.FORWARD_COMMITTED) {
                return settleCompletedJobOperation(network, job, runtime, assignment);
            }
            // A machine output handoff already owns physical output. Finish that handoff before
            // cancellation so neither side can receive a duplicate or lose a receipted result.
            return 0;
        }

        QIODurableTransferRecord input = findOperationTransferAny(network, job.getJobId(),
              QIODurableTransferRecord.Type.JOB_TO_MACHINE, runtime.getNodeId(),
              assignment.getOperationId());
        boolean restoreStagedInput = input != null &&
              input.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED &&
              endpoint != null && token != null &&
              !token.hasTransferReceipt(input.getTransferId()) &&
              machineMatchesLeaseBaseline(endpoint);

        if (host != null && token != null &&
              !host.abandonJobOperation(assignment.getOperationId())) {
            return 0;
        }
        if (host != null && token == null && input != null && input.getLeaseId() != null) {
            host.releaseUnattachedLease(input.getLeaseId());
        }

        if (input != null) {
            switch (input.getPhase()) {
                case PREPARED -> network.discardPreparedGenericTransfer(input.getTransferId());
                case SOURCE_DEBITED -> {
                    if (restoreStagedInput) {
                        network.rollbackUncreditedJobMachineTransfer(input.getTransferId());
                    } else {
                        // Missing receipts or an unavailable endpoint are ambiguous. Treat the
                        // input as consumed; refunding it could duplicate material already inside
                        // the machine.
                        network.markGenericTransferDestinationCredited(input.getTransferId(),
                              "cancelled-machine-input-abandoned");
                        network.completeJobMachineTransfer(input.getTransferId());
                    }
                }
                case DESTINATION_CREDITED ->
                      network.completeJobMachineTransfer(input.getTransferId());
                case COMMITTED -> {
                }
                case ROLLBACK_REQUIRED -> {
                    return 0;
                }
            }
        }

        network.failStepOperation(job.getJobId(), runtime.getNodeId(),
              assignment.getOperationId(),
              "Scheduled machine operation abandoned after job cancellation");
        network.removeCommittedJobOperationTransfers(job.getJobId(),
              assignment.getOperationId());
        persist(network);
        if (host != null && token != null) {
            host.forgetSettledOperation(assignment.getOperationId());
        }
        return 1;
    }

    private void driveJobToMachineTransfer(QIOProcessingNetworkData network,
          QIODurableTransferRecord transfer, MachineEndpoint endpoint) throws IOException {
        QIOAutomationHost host = endpoint.device.host();
        MachineOperationToken token = host.getOperationTokens().get(
              endpoint.operationId(transfer));
        if (token == null) {
            return;
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            network.debitJobMachineTransfer(transfer.getTransferId());
            persist(network);
        }
        if (transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED &&
              !token.hasTransferReceipt(transfer.getTransferId())) {
            MachineTransferPlan insertion = MachineTransferPlan.create(endpoint.device.tile());
            for (MachineResourceStack input : endpoint.route.inputs()) {
                insertion.addInsert(endpoint.ports.get(input.portId()), input);
            }
            boolean inserted = insertion.execute();
            if ((!inserted && !machineMatchesAfterInput(endpoint) &&
                  !machineMatchesCompletedOperation(endpoint)) ||
                  !host.recordTransferReceipt(token.operationId(), transfer.getTransferId())) {
                return;
            }
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            // Destination credit and transfer completion are checkpointed together.
            network.markGenericTransferDestinationCredited(transfer.getTransferId(),
                  "machine-input-ports");
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            network.completeJobMachineTransfer(transfer.getTransferId());
            persist(network);
        }
        token = host.getOperationTokens().get(token.operationId());
        if (token != null && token.state() == MachineOperationToken.State.LOADING) {
            host.transitionOperation(token.operationId(), MachineOperationToken.State.ACTIVE,
                  MachineOperationLease.State.ACTIVE);
        }
    }

    private int collectMachineOutput(QIOProcessingNetworkData network, QIOCraftingJob job,
          QIOStepRuntime runtime, QIOOperationAssignment assignment, MachineEndpoint endpoint)
          throws IOException {
        QIODurableTransferRecord transfer = findOperationTransferAny(network, job.getJobId(),
              QIODurableTransferRecord.Type.MACHINE_TO_JOB, runtime.getNodeId(),
              assignment.getOperationId());
        if (transfer == null) {
            Map<PortableResourceDescriptor, Long> outputs = machineOutputAmounts(endpoint);
            if (outputs.isEmpty()) {
                return 0;
            }
            MachineOperationToken token = endpoint.device.host().getOperationTokens().get(
                  assignment.getOperationId());
            transfer = new QIODurableTransferRecord(UUID.randomUUID(), UUID.randomUUID(),
                  QIODurableTransferRecord.Type.MACHINE_TO_JOB, job.getJobId(), null,
                  job.getActivePlan().getRevision(), operationKey(runtime.getNodeId(),
                  assignment.getOperationId()), token == null ? null : token.leaseId(),
                  "machine/" + assignment.getDeviceUUID() + "/lane/" + assignment.getLaneId(),
                  "job/" + job.getJobId() + "/produced", outputs);
            network.addDurableTransfer(transfer);
            persist(network);
        }
        MachineOperationToken token = endpoint.device.host().getOperationTokens().get(
              assignment.getOperationId());
        if (token == null) {
            return 0;
        }
        if (!token.hasTransferReceipt(transfer.getTransferId())) {
            MachineTransferPlan extraction = outputExtraction(endpoint);
            boolean extracted = extraction.execute();
            boolean exact = extracted ? aggregate(extraction.getExtracted()).equals(
                  transfer.getResources()) : machineMatchesLeaseBaseline(endpoint);
            if (!exact || !endpoint.device.host().recordTransferReceipt(
                  assignment.getOperationId(), transfer.getTransferId())) {
                return 0;
            }
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            network.markGenericTransferSourceDebited(transfer.getTransferId(),
                  "machine-output-ports", endpoint.device.host().getConfigurationRevision());
            persist(network);
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            // Buffer credit and transfer completion are checkpointed together.
            network.creditMachineOutputTransfer(transfer.getTransferId());
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            network.commitGenericTransfer(transfer.getTransferId());
            persist(network);
        }
        token = endpoint.device.host().getOperationTokens().get(assignment.getOperationId());
        if (token != null && token.state() == MachineOperationToken.State.COLLECTING) {
            if (!endpoint.device.host().transitionOperation(assignment.getOperationId(),
                  MachineOperationToken.State.COMPLETED, MachineOperationLease.State.COMPLETED) ||
                  !endpoint.device.host().transitionOperation(assignment.getOperationId(),
                        MachineOperationToken.State.COMPLETED,
                        MachineOperationLease.State.RELEASED)) {
                return 0;
            }
        }
        return 1;
    }

    private void driveJobToMachineTransfer(QIOProcessingNetworkData network,
          QIODurableTransferRecord transfer, QIOProcessorLaneRuntime lane) throws IOException {
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            network.debitJobMachineTransfer(transfer.getTransferId());
            persist(network);
        }
        if (transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED &&
              !lane.hasTransferReceipt(transfer.getTransferId())) {
            lane.creditInput(transfer.getTransferId(), transfer.getResources());
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            network.markGenericTransferDestinationCredited(transfer.getTransferId(),
                  "processor-lane-input");
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            network.completeJobMachineTransfer(transfer.getTransferId());
            persist(network);
        }
        if (lane.getState() == QIOProcessorLaneRuntime.State.LOADING &&
              lane.getInput().equals(transfer.getResources())) {
            lane.markReady();
        }
    }

    private int collectOutput(QIOProcessingNetworkData network, QIOCraftingJob job,
          QIOStepRuntime runtime, QIOOperationAssignment assignment, LoadedProcessor loaded,
          QIOProcessorLaneRuntime lane) throws IOException {
        QIODurableTransferRecord transfer = findOperationTransferAny(network, job.getJobId(),
              QIODurableTransferRecord.Type.MACHINE_TO_JOB, runtime.getNodeId(),
              assignment.getOperationId());
        if (transfer == null) {
            transfer = new QIODurableTransferRecord(UUID.randomUUID(), UUID.randomUUID(),
                  QIODurableTransferRecord.Type.MACHINE_TO_JOB, job.getJobId(), null,
                  job.getActivePlan().getRevision(), operationKey(runtime.getNodeId(),
                  assignment.getOperationId()), null,
                  "processor/" + loaded.processorUUID() + "/lane/" + lane.getLaneId(),
                  "job/" + job.getJobId() + "/produced", lane.getOutput());
            network.addDurableTransfer(transfer);
            persist(network);
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            lane.debitOutput(transfer.getTransferId(), transfer.getResources());
            network.markGenericTransferSourceDebited(transfer.getTransferId(),
                  "processor-lane-output", lane.getRuntimeRevision());
            persist(network);
        }
        if (transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED &&
              !lane.hasTransferReceipt(transfer.getTransferId())) {
            lane.debitOutput(transfer.getTransferId(), transfer.getResources());
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            network.creditMachineOutputTransfer(transfer.getTransferId());
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            network.commitGenericTransfer(transfer.getTransferId());
            persist(network);
        }
        if (lane.isSettled()) {
            return settleProcessorLane(network, job, runtime, assignment, loaded, lane);
        }
        return 1;
    }

    private int collectReturn(QIOProcessingNetworkData network, QIOCraftingJob job,
          QIOStepRuntime runtime, QIOOperationAssignment assignment, LoadedProcessor loaded,
          QIOProcessorLaneRuntime lane) throws IOException {
        QIODurableTransferRecord transfer = findOperationTransferAny(network, job.getJobId(),
              QIODurableTransferRecord.Type.MACHINE_TO_JOB, runtime.getNodeId(),
              assignment.getOperationId());
        if (transfer == null) {
            transfer = new QIODurableTransferRecord(UUID.randomUUID(), UUID.randomUUID(),
                  QIODurableTransferRecord.Type.MACHINE_TO_JOB, job.getJobId(), null,
                  job.getActivePlan().getRevision(), operationKey(runtime.getNodeId(),
                  assignment.getOperationId()), null,
                  "processor/" + loaded.processorUUID() + "/lane/" + lane.getLaneId(),
                  "job/" + job.getJobId() + "/returned", lane.getInput());
            network.addDurableTransfer(transfer);
            persist(network);
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            lane.debitReturn(transfer.getTransferId(), transfer.getResources());
            network.markGenericTransferSourceDebited(transfer.getTransferId(),
                  "processor-lane-return", lane.getRuntimeRevision());
            persist(network);
        }
        if (transfer.getPhase() != QIODurableTransferRecord.Phase.PREPARED &&
              !lane.hasTransferReceipt(transfer.getTransferId())) {
            lane.debitReturn(transfer.getTransferId(), transfer.getResources());
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            network.creditMachineOutputTransfer(transfer.getTransferId());
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            network.commitGenericTransfer(transfer.getTransferId());
            persist(network);
        }
        if (lane.isSettled()) {
            return settleProcessorLane(network, job, runtime, assignment, loaded, lane);
        }
        return 1;
    }

    private int deliver(QIOProcessingNetworkData network, QIOCraftingJob job, int budget)
          throws IOException {
        if (budget <= 0) {
            return 0;
        }
        IQIOStorageView view = open(network, job);
        if (view == null) {
            if (job.getState() == QIOCraftingJobState.RETURNING ||
                  job.getState() == QIOCraftingJobState.PLANNING ||
                  job.getState() == QIOCraftingJobState.REPLAN_DRAINING) {
                return 0;
            }
            if (job.getState() != QIOCraftingJobState.RETURN_BLOCKED) {
                network.transitionJobState(job.getJobId(), QIOCraftingJobState.RETURN_BLOCKED);
                persist(network);
                return 1;
            }
            return 0;
        }
        try {
            int actions = 0;
            while (actions < budget) {
                int used = deliverOne(network, job, view);
                if (used <= 0) {
                    break;
                }
                actions += used;
                if (job.getExecutionSlotToken() == null || job.getState().isTerminal() ||
                      job.getState() == QIOCraftingJobState.RETURN_BLOCKED) {
                    break;
                }
            }
            return actions;
        } finally {
            view.close();
        }
    }

    private int deliverOne(QIOProcessingNetworkData network, QIOCraftingJob job,
          IQIOStorageView view) throws IOException {
        List<QIODurableTransferRecord> active = network.getActiveJobTransfers(job.getJobId(),
              QIODurableTransferRecord.Type.JOB_TO_QIO);
        if (!active.isEmpty()) {
            return driveReturnTransfer(network, active.get(0), view);
        }
        QIOJobBuffer buffer = network.getJobBuffer(job.getJobId());
        if (buffer == null) {
            throw new IllegalStateException("QIO job lost its resource buffer");
        }
        Map<PortableResourceDescriptor, Long> returnable = buffer.getReturnableResources();
        if (returnable.isEmpty()) {
            if (buffer.isEmpty()) {
                if (job.getState() == QIOCraftingJobState.PLANNING) {
                    network.finishPlanningDrain(job.getJobId());
                    persist(network);
                    return 1;
                }
                if (job.getState() == QIOCraftingJobState.REPLAN_DRAINING) {
                    network.finishReplanDrain(job.getJobId());
                    persist(network);
                    return 1;
                }
                network.releaseExecutionSlot(job.getJobId(),
                      job.isCancellationRequested() ? QIOCraftingJobState.CANCELLED :
                            job.getState() == QIOCraftingJobState.RETURNING ?
                                  QIOCraftingJobState.FAILED : QIOCraftingJobState.COMPLETED);
                persist(network);
                return 1;
            }
            if (job.getState() != QIOCraftingJobState.RETURN_BLOCKED) {
                network.transitionJobState(job.getJobId(), QIOCraftingJobState.RETURN_BLOCKED);
                persist(network);
                return 1;
            }
            return 0;
        }
        Map.Entry<PortableResourceDescriptor, Long> resource =
              returnable.entrySet().iterator().next();
        long accepted = simulateInsert(view, resource.getKey(), resource.getValue());
        if (accepted <= 0) {
            if (job.getState() == QIOCraftingJobState.RETURNING ||
                  job.getState() == QIOCraftingJobState.PLANNING ||
                  job.getState() == QIOCraftingJobState.REPLAN_DRAINING) {
                return 0;
            }
            if (job.getState() != QIOCraftingJobState.RETURN_BLOCKED) {
                network.transitionJobState(job.getJobId(), QIOCraftingJobState.RETURN_BLOCKED);
                persist(network);
                return 1;
            }
            return 0;
        }
        Map<PortableResourceDescriptor, Long> resources = Collections.singletonMap(
              resource.getKey(), accepted);
        QIODurableTransferRecord transfer = new QIODurableTransferRecord(UUID.randomUUID(),
              UUID.randomUUID(), QIODurableTransferRecord.Type.JOB_TO_QIO,
              job.getJobId(), null, job.getActivePlan().getRevision(), "delivery", null,
              "job/" + job.getJobId() + "/buffer", "qio/" + network.getFrequencyUUID(),
              resources, Collections.singletonMap(resource.getKey(),
              storedAmount(view, resource.getKey())));
        network.addDurableTransfer(transfer);
        if (job.getState() != QIOCraftingJobState.RETURNING &&
              job.getState() != QIOCraftingJobState.PLANNING &&
              job.getState() != QIOCraftingJobState.REPLAN_DRAINING) {
            network.transitionJobState(job.getJobId(), QIOCraftingJobState.DELIVERING);
        }
        persist(network);
        return driveReturnTransfer(network, transfer, view);
    }

    private int driveReturnTransfer(QIOProcessingNetworkData network,
          QIODurableTransferRecord transfer, IQIOStorageView view) throws IOException {
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED) {
            network.stageJobReturnTransfer(transfer.getTransferId());
            persist(network);
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.SOURCE_DEBITED) {
            // QIO credit and job-buffer completion are checkpointed together.
            Map.Entry<PortableResourceDescriptor, Long> resource =
                  transfer.getResources().entrySet().iterator().next();
            QIOTransferResult result = insert(view, transfer.getTransferId(), resource.getKey(),
                  resource.getValue(), transfer.getQIOBaselines().get(resource.getKey()));
            if (!result.isSuccess() || result.getTransferredAmount() != resource.getValue()) {
                return 0;
            }
            network.markGenericTransferDestinationCredited(transfer.getTransferId(),
                  result.getStatus().name());
        }
        if (transfer.getPhase() == QIODurableTransferRecord.Phase.DESTINATION_CREDITED) {
            network.completeJobReturnTransfer(transfer.getTransferId());
            persist(network);
        }
        return 1;
    }

    @Nullable
    private static MachineEndpoint findMachineEndpoint(QIOProcessingNetworkData network,
          QIOPlanStep step,
          @Nullable MachineOperationToken token, @Nullable UUID requiredDeviceUUID,
          @Nullable UUID operationId, boolean requireReadyWithoutToken) {
        List<LoadedDevice> devices = new ArrayList<>(
              operationId == null ?
                    QIOAutomationDeviceRegistry.INSTANCE.getUsableDevices(QIOAutomationMode.SCHEDULED) :
                    QIOAutomationDeviceRegistry.INSTANCE.getOperationalDevices(QIOAutomationMode.SCHEDULED));
        if (token == null && requiredDeviceUUID == null) {
            devices.removeIf(device -> {
                UUID deviceUUID = device.host().getPersistentDeviceUUID();
                return !network.getPolicies().isRouteEnabled(deviceUUID,
                      step.getProviderId(), step.getRouteId(), step.getRecipeKey());
            });
        }
        if (token == null && requiredDeviceUUID == null) {
            devices = orderProvidersRoundRobin(devices,
                  device -> device.host().getPersistentDeviceUUID(),
                  device -> network.getPolicies().machinePriority(
                        device.host().getPersistentDeviceUUID()),
                  priority -> INSTANCE.providerCursors.get(new ProviderCursorKey(
                        network.getFrequencyUUID(), ProviderKind.MEKANISM,
                        step.getStableRouteId(), priority)));
        } else {
            devices.sort(Comparator.comparingLong((LoadedDevice device) ->
                  network.getPolicies().machinePriority(
                        device.host().getPersistentDeviceUUID())).reversed()
                  .thenComparing(device ->
                        device.host().getPersistentDeviceUUID().toString()));
        }
        int affinityPasses = token == null && requiredDeviceUUID == null ? 3 : 1;
        for (int affinity = 0; affinity < affinityPasses; affinity++) {
        for (LoadedDevice device : devices) {
            if (requiredDeviceUUID != null && !requiredDeviceUUID.equals(
                  device.host().getPersistentDeviceUUID())) {
                continue;
            }
            if (!matchesFrequencyOrOwnedDrain(network, device.host(),
                  QIOAutomationMode.SCHEDULED, operationId)) {
                continue;
            }
            MachineRecipeProviderRegistry.BoundProvider provider =
                  MachineRecipeProviderRegistry.find(device.tile());
            if (provider == null || !provider.id().toString().equals(step.getProviderId())) {
                continue;
            }
            if (token == null && requiredDeviceUUID == null &&
                !network.getAutomationRecipeProfiles().getActiveProfile(
                      device.host().getPersistentDeviceUUID(), QIOAutomationMode.SCHEDULED,
                      QIOAutomationRecipeProfileScope.resolve(provider)).isRouteEnabled(
                      QIOAutomationRecipeProfileLayout.routeKey(step.getRouteId(),
                            step.getRecipeKey()))) {
                continue;
            }
            Map<String, MachinePort> ports = new LinkedHashMap<>();
            for (MachinePort port : provider.getPorts()) {
                if (port != null) {
                    ports.put(port.portId(), port);
                }
            }
            for (MachineRecipeRoute route : provider.getRecipeRoutes()) {
                if (!route.routeId().equals(step.getRouteId()) ||
                      (token == null ? !route.logicalRecipeKey().equals(step.getRecipeKey()) :
                            !route.recipeKey().equals(token.recipeKey()))) {
                    continue;
                }
                QIOPlanningRoute projection = QIOPlanningRoute.fromMachineRoute(provider.id(),
                      route, 0);
                if (!projection.getSignature().equals(step.getRouteSignature())) {
                    continue;
                }
                MachineEndpoint endpoint = endpoint(device, provider, route, ports);
                if (endpoint == null || token != null && endpoint.laneId != token.laneId()) {
                    continue;
                }
                if (token == null && requireReadyWithoutToken && !machineReadyForInput(endpoint)) {
                    continue;
                }
                if (affinityPasses > 1 && configurationAffinity(route, ports) != affinity) {
                    continue;
                }
                return endpoint;
            }
        }
        }
        return null;
    }

    @Nullable
    private static MachineEndpoint endpoint(LoadedDevice device,
          MachineRecipeProviderRegistry.BoundProvider provider, MachineRecipeRoute route,
          Map<String, MachinePort> ports) {
        Set<String> referenced = new LinkedHashSet<>();
        long laneId = -1;
        List<MachinePortBaseline> baselines = new ArrayList<>();
        List<MachineResourceStack> stacks = new ArrayList<>();
        stacks.addAll(route.configurationInputs());
        stacks.addAll(route.inputs());
        stacks.addAll(route.guaranteedOutputs());
        stacks.addAll(route.optionalOutputs());
        for (MachineResourceStack stack : stacks) {
            MachinePort port = ports.get(stack.portId());
            if (port == null) {
                return null;
            }
            if (port.laneId() != MachinePort.SHARED_LANE) {
                if (laneId >= 0 && laneId != port.laneId()) {
                    return null;
                }
                laneId = port.laneId();
            }
            if (referenced.add(port.portId())) {
                baselines.add(MachinePortBaseline.capture(port));
            }
        }
        addDistributedOutputBaselines(route, ports, referenced, baselines);
        return new MachineEndpoint(device, provider, route, ports, laneId < 0 ? 0 : laneId,
              baselines);
    }

    private static boolean machineReadyForInput(MachineEndpoint endpoint) {
        return machineReadyForInput(endpoint.device.tile(), endpoint.route, endpoint.ports);
    }

    static boolean machineReadyForInput(TileEntity tile, MachineRecipeRoute route,
          Map<String, MachinePort> ports) {
        MachineTransferPlan insertion = MachineTransferPlan.create(tile);
        for (MachineResourceStack configuration : route.configurationInputs()) {
            MachinePort port = ports.get(configuration.portId());
            if (port == null || !port.isConfiguration() || port.kind() != configuration.kind()) {
                return false;
            }
            MachineResourceStack current = port.peek();
            if (current != null && current.sameResource(configuration)) {
                continue;
            }
            if (current == null ? !port.canInsert(configuration) : !port.canExtract(current)) {
                return false;
            }
        }
        for (MachineResourceStack input : route.inputs()) {
            MachinePort port = ports.get(input.portId());
            if (port == null) {
                return false;
            }
            if (port.peek() != null) {
                return false;
            }
            insertion.addInsert(port, input);
        }
        for (MachineResourceStack output : route.guaranteedOutputs()) {
            MachinePort port = ports.get(output.portId());
            if (port == null || port.peek() != null) {
                return false;
            }
        }
        for (MachineResourceStack output : route.optionalOutputs()) {
            MachinePort port = ports.get(output.portId());
            if (port == null || port.peek() != null) {
                return false;
            }
        }
        return insertion.canExecute();
    }

    /** Computes the largest safe batch that fits every referenced input/output container. */
    private static long maxMachineOperations(MachineEndpoint endpoint, long requested) {
        return maxMachineOperations(endpoint.device.tile(), endpoint.route, endpoint.ports,
              requested);
    }

    static long maxMachineOperations(TileEntity tile, MachineRecipeRoute route,
          Map<String, MachinePort> ports, long requested) {
        if (requested <= 0) {
            return 0;
        }
        long maximum = requested;
        List<MachineResourceStack> stacks = new ArrayList<>(route.inputs());
        stacks.addAll(route.guaranteedOutputs());
        stacks.addAll(route.optionalOutputs());
        for (MachineResourceStack stack : stacks) {
            MachinePort port = ports.get(stack.portId());
            if (port == null) {
                return 0;
            }
            long capacity = availableCapacity(port, stack);
            maximum = Math.min(maximum, capacity / stack.amount());
        }
        if (maximum <= 1) {
            return maximum == 1 && machineReadyForInput(tile, route, ports) ? 1 : 0;
        }
        long low = 1;
        long high = maximum;
        long accepted = 0;
        while (low <= high) {
            long middle = low + (high - low) / 2;
            if (machineReadyForInput(tile, scaleRoute(route, middle), ports)) {
                accepted = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return accepted;
    }

    private static MachineEndpoint scaleEndpoint(MachineEndpoint endpoint, long operations) {
        if (operations == 1) {
            return endpoint;
        }
        return new MachineEndpoint(endpoint.device, endpoint.provider,
              scaleRoute(endpoint.route, operations),
              endpoint.ports, endpoint.laneId, endpoint.baselines);
    }

    static MachineRecipeRoute scaleRoute(MachineRecipeRoute route, long operations) {
        if (operations <= 0) {
            throw new IllegalArgumentException("Machine route operation count must be positive");
        }
        if (operations == 1) {
            return route;
        }
        MachineRecipeRoute.Builder builder = MachineRecipeRoute.builder(route.routeId())
              .recipeKey(route.recipeKey()).logicalRecipeKey(route.logicalRecipeKey());
        route.configurationInputs().forEach(builder::configurationInput);
        route.inputs().forEach(stack -> builder.input(stack.scale(operations)));
        route.guaranteedOutputs().forEach(stack ->
              builder.output(stack.scale(operations)));
        route.optionalOutputs().forEach(stack ->
              builder.optionalOutput(stack.scale(operations)));
        return builder.build();
    }

    private static long availableCapacity(MachinePort port, MachineResourceStack stack) {
        return port.getAvailableCapacity(stack);
    }

    private static boolean machineOutputsReady(MachineEndpoint endpoint) {
        return machineOutputsReady(endpoint.route, endpoint.ports, endpoint.baselines);
    }

    static boolean machineOutputsReady(MachineRecipeRoute route,
          Map<String, MachinePort> ports, List<MachinePortBaseline> baselines) {
        return collectMachineOutputs(route, ports, baselines) != null;
    }

    static boolean machineOutputsReady(MachineRecipeRoute route,
          Map<String, MachinePort> ports) {
        for (MachineResourceStack output : route.guaranteedOutputs()) {
            MachinePort port = ports.get(output.portId());
            if (port == null || !port.canExtract(output)) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    private static Map<PortableResourceDescriptor, Long> machineOutputAmounts(
          MachineEndpoint endpoint) {
        return machineOutputAmounts(endpoint.route, endpoint.ports, endpoint.baselines);
    }

    @Nonnull
    static Map<PortableResourceDescriptor, Long> machineOutputAmounts(
          MachineRecipeRoute route, Map<String, MachinePort> ports,
          List<MachinePortBaseline> baselines) {
        List<MachineResourceStack> outputs = collectMachineOutputs(route, ports, baselines);
        return outputs == null ? Collections.emptyMap() : aggregate(outputs);
    }

    @Nonnull
    static Map<PortableResourceDescriptor, Long> machineOutputAmounts(
          MachineRecipeRoute route, Map<String, MachinePort> ports) {
        List<MachineResourceStack> outputs = new ArrayList<>(route.guaranteedOutputs());
        for (MachineResourceStack optional : route.optionalOutputs()) {
            MachinePort port = ports.get(optional.portId());
            if (port != null && port.canExtract(optional)) {
                outputs.add(optional);
            }
        }
        return aggregate(outputs);
    }

    @Nonnull
    private static MachineTransferPlan outputExtraction(MachineEndpoint endpoint) {
        return outputExtraction(endpoint.device.tile(), endpoint.route, endpoint.ports,
              endpoint.baselines);
    }

    @Nonnull
    static MachineTransferPlan outputExtraction(TileEntity tile, MachineRecipeRoute route,
          Map<String, MachinePort> ports, List<MachinePortBaseline> baselines) {
        MachineTransferPlan extraction = MachineTransferPlan.create(tile);
        List<MachineResourceStack> outputs = collectMachineOutputs(route, ports, baselines);
        if (outputs != null) {
            outputs.forEach(output -> extraction.addExtract(
                  ports.get(output.portId()), output));
        }
        return extraction;
    }

    @Nonnull
    static MachineTransferPlan outputExtraction(TileEntity tile, MachineRecipeRoute route,
          Map<String, MachinePort> ports) {
        MachineTransferPlan extraction = MachineTransferPlan.create(tile);
        for (MachineResourceStack output : route.guaranteedOutputs()) {
            extraction.addExtract(ports.get(output.portId()), output);
        }
        for (MachineResourceStack optional : route.optionalOutputs()) {
            MachinePort port = ports.get(optional.portId());
            if (port != null && port.canExtract(optional)) {
                extraction.addExtract(port, optional);
            }
        }
        return extraction;
    }

    private static boolean machineMatchesAfterInput(MachineEndpoint endpoint) {
        return machineMatchesAfterInput(endpoint.route, endpoint.ports, endpoint.baselines);
    }

    static boolean machineMatchesAfterInput(MachineRecipeRoute route,
          Map<String, MachinePort> ports, List<MachinePortBaseline> baselines) {
        Map<String, MachineResourceStack> inputs = new LinkedHashMap<>();
        for (MachineResourceStack input : route.inputs()) {
            if (inputs.put(input.portId(), input) != null) {
                return false;
            }
        }
        Map<String, MachineResourceStack> configuration = configurationByPort(route);
        for (MachinePortBaseline baseline : baselines) {
            MachinePort port = ports.get(baseline.portId());
            MachineResourceStack input = inputs.get(baseline.portId());
            MachineResourceStack retained = configuration.get(baseline.portId());
            if (port == null || retained != null &&
                  !portHoldsConfiguration(port, retained, baseline) ||
                  retained == null && (input == null ? !baseline.matches(port) :
                  !baseline.matchesAfterInsertion(port, input))) {
                return false;
            }
        }
        return true;
    }

    private static boolean machineMatchesLeaseBaseline(MachineEndpoint endpoint) {
        return machineMatchesLeaseBaseline(endpoint.route, endpoint.ports, endpoint.baselines);
    }

    static boolean machineMatchesLeaseBaseline(MachineRecipeRoute route,
          Map<String, MachinePort> ports, List<MachinePortBaseline> baselines) {
        Map<String, MachineResourceStack> configuration = configurationByPort(route);
        for (MachinePortBaseline baseline : baselines) {
            MachinePort port = ports.get(baseline.portId());
            MachineResourceStack retained = configuration.get(baseline.portId());
            if (port == null || retained == null ? port == null || !baseline.matches(port) :
                  !portHoldsConfiguration(port, retained, baseline)) {
                return false;
            }
        }
        return true;
    }

    static boolean machineMatchesLeaseBaseline(Map<String, MachinePort> ports,
          List<MachinePortBaseline> baselines) {
        for (MachinePortBaseline baseline : baselines) {
            MachinePort port = ports.get(baseline.portId());
            if (port == null || !baseline.matches(port)) {
                return false;
            }
        }
        return true;
    }

    private static boolean machineMatchesCompletedOperation(MachineEndpoint endpoint) {
        return machineMatchesCompletedOperation(endpoint.route, endpoint.ports,
              endpoint.baselines);
    }

    static boolean machineMatchesCompletedOperation(MachineRecipeRoute route,
          Map<String, MachinePort> ports, List<MachinePortBaseline> baselines) {
        List<MachineResourceStack> collected = collectMachineOutputs(route, ports, baselines);
        if (collected == null) {
            return false;
        }
        Map<String, MachineResourceStack> inputs = new LinkedHashMap<>();
        route.inputs().forEach(input -> inputs.put(input.portId(), input));
        Map<String, MachineResourceStack> configuration = configurationByPort(route);
        Map<String, MachineResourceStack> produced = mergeByPort(collected);
        for (MachinePortBaseline baseline : baselines) {
            MachinePort port = ports.get(baseline.portId());
            if (port == null) {
                return false;
            }
            MachineResourceStack retained = configuration.get(baseline.portId());
            if (retained != null) {
                if (!portHoldsConfiguration(port, retained, baseline)) {
                    return false;
                }
                continue;
            }
            if (inputs.containsKey(baseline.portId())) {
                if (!baseline.matches(port)) {
                    return false;
                }
                continue;
            }
            MachineResourceStack output = produced.get(baseline.portId());
            if (output != null) {
                if (!baseline.matchesAfterInsertion(port, output)) {
                    return false;
                }
                continue;
            }
            if (!baseline.matches(port)) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    private static Map<String, MachineResourceStack> configurationByPort(
          MachineRecipeRoute route) {
        Map<String, MachineResourceStack> values = new LinkedHashMap<>();
        route.configurationInputs().forEach(stack -> values.put(stack.portId(), stack));
        return values;
    }

    private static boolean portHoldsConfiguration(MachinePort port,
          MachineResourceStack expected, @Nullable MachinePortBaseline baseline) {
        if (port == null) return false;
        MachineResourceStack current = port.peek();
        long expectedAmount = expected.amount();
        if (baseline != null && baseline.contents() != null &&
              baseline.contents().sameResource(expected)) {
            expectedAmount = baseline.contents().amount();
        }
        return port.isConfiguration() && current != null &&
              current.amount() == expectedAmount && current.sameResource(expected);
    }

    private static MachineEndpoint withBaselines(MachineEndpoint endpoint,
          List<MachinePortBaseline> baselines) {
        if (baselines.isEmpty()) {
            return endpoint;
        }
        Set<String> expected = new LinkedHashSet<>();
        for (MachinePortBaseline current : endpoint.baselines) {
            expected.add(current.portId());
        }
        Map<String, MachinePortBaseline> restored = new LinkedHashMap<>();
        for (MachinePortBaseline baseline : baselines) {
            MachinePort port = endpoint.ports.get(baseline.portId());
            if (port == null || port.kind() != baseline.kind() ||
                  !port.portGroupId().equals(baseline.portGroupId()) ||
                  restored.put(baseline.portId(), baseline) != null) {
                throw new IllegalStateException("Persisted machine baseline no longer matches its port");
            }
        }
        if (!expected.containsAll(restored.keySet())) {
            throw new IllegalStateException("Persisted machine baseline set changed");
        }
        List<MachinePortBaseline> merged = new ArrayList<>(endpoint.baselines.size());
        for (MachinePortBaseline current : endpoint.baselines) {
            MachinePortBaseline restoredBaseline = restored.get(current.portId());
            if (restoredBaseline != null) {
                merged.add(restoredBaseline);
            } else if (isDistributedOutputPort(endpoint.route, endpoint.ports,
                  current.portId())) {
                // Legacy operations only captured their selected factory lane. Factory sorting
                // can move that owned batch into sibling outputs, whose pre-operation state was
                // necessarily empty for a schedulable route.
                merged.add(new MachinePortBaseline(current.portId(), current.portGroupId(),
                      current.kind(), null));
            } else {
                throw new IllegalStateException("Persisted machine baseline set changed");
            }
        }
        return new MachineEndpoint(endpoint.device, endpoint.provider, endpoint.route,
              endpoint.ports, endpoint.laneId, merged);
    }

    private static void addDistributedOutputBaselines(MachineRecipeRoute route,
          Map<String, MachinePort> ports, Set<String> referenced,
          List<MachinePortBaseline> baselines) {
        List<MachineResourceStack> outputs = new ArrayList<>(route.guaranteedOutputs());
        outputs.addAll(route.optionalOutputs());
        for (MachineResourceStack output : outputs) {
            MachinePort selected = ports.get(output.portId());
            String family = lanePortFamily(selected);
            if (family == null) continue;
            for (MachinePort candidate : ports.values()) {
                if (candidate != null && !candidate.isConfiguration() &&
                    candidate.role().allowsOutput() &&
                    candidate.kind() == output.kind() &&
                    family.equals(lanePortFamily(candidate)) &&
                    referenced.add(candidate.portId())) {
                    baselines.add(MachinePortBaseline.capture(candidate));
                }
            }
        }
    }

    @Nullable
    static List<MachineResourceStack> collectMachineOutputs(MachineRecipeRoute route,
          Map<String, MachinePort> ports, List<MachinePortBaseline> baselines) {
        Map<String, MachinePortBaseline> baselineByPort = new LinkedHashMap<>();
        for (MachinePortBaseline baseline : baselines) {
            baselineByPort.put(baseline.portId(), baseline);
        }
        Map<String, Long> allocatedByPort = new LinkedHashMap<>();
        List<MachineResourceStack> collected = new ArrayList<>();
        for (MachineResourceStack output : route.guaranteedOutputs()) {
            List<MachineResourceStack> allocations = allocateMachineOutput(output, ports,
                  baselineByPort, allocatedByPort, true);
            if (allocations == null) return null;
            collected.addAll(allocations);
        }
        for (MachineResourceStack output : route.optionalOutputs()) {
            List<MachineResourceStack> allocations = allocateMachineOutput(output, ports,
                  baselineByPort, allocatedByPort, false);
            if (allocations != null) collected.addAll(allocations);
        }
        return collected;
    }

    @Nullable
    private static List<MachineResourceStack> allocateMachineOutput(
          MachineResourceStack expected, Map<String, MachinePort> ports,
          Map<String, MachinePortBaseline> baselines, Map<String, Long> allocatedByPort,
          boolean required) {
        MachinePort selected = ports.get(expected.portId());
        if (selected == null || selected.isConfiguration()) {
            return required ? null : Collections.emptyList();
        }
        String family = lanePortFamily(selected);
        List<MachinePort> candidates = new ArrayList<>();
        if (family == null) {
            candidates.add(selected);
        } else {
            for (MachinePort candidate : ports.values()) {
                if (candidate != null && !candidate.isConfiguration() &&
                    candidate.role().allowsOutput() &&
                    candidate.kind() == expected.kind() &&
                    family.equals(lanePortFamily(candidate))) {
                    candidates.add(candidate);
                }
            }
            candidates.sort(Comparator.comparingLong(MachinePort::laneId)
                  .thenComparing(MachinePort::portId));
        }
        long remaining = expected.amount();
        List<MachineResourceStack> allocations = new ArrayList<>();
        for (MachinePort candidate : candidates) {
            long alreadyAllocated = allocatedByPort.getOrDefault(candidate.portId(), 0L);
            long available = producedSinceBaseline(candidate, expected,
                  baselines.get(candidate.portId())) - alreadyAllocated;
            if (available <= 0) continue;
            long take = Math.min(remaining, available);
            allocations.add(expected.withPort(candidate.portId()).withAmount(take));
            allocatedByPort.put(candidate.portId(), alreadyAllocated + take);
            remaining -= take;
            if (remaining == 0) break;
        }
        if (required && remaining != 0) return null;
        return allocations;
    }

    private static long producedSinceBaseline(MachinePort port,
          MachineResourceStack expected, @Nullable MachinePortBaseline baseline) {
        MachineResourceStack current = port.peek();
        if (current == null || !current.sameResource(expected)) return 0;
        if (baseline == null || baseline.contents() == null) return current.amount();
        MachineResourceStack previous = baseline.contents();
        if (!previous.sameResource(current) || previous.amount() > current.amount()) return 0;
        return current.amount() - previous.amount();
    }

    private static Map<String, MachineResourceStack> mergeByPort(
          List<MachineResourceStack> stacks) {
        Map<String, MachineResourceStack> merged = new LinkedHashMap<>();
        for (MachineResourceStack stack : stacks) {
            MachineResourceStack previous = merged.get(stack.portId());
            if (previous == null) {
                merged.put(stack.portId(), stack);
            } else if (!previous.sameResource(stack) ||
                  previous.amount() > Long.MAX_VALUE - stack.amount()) {
                throw new IllegalStateException("Machine output allocations conflict");
            } else {
                merged.put(stack.portId(), previous.withAmount(
                      previous.amount() + stack.amount()));
            }
        }
        return merged;
    }

    private static boolean isDistributedOutputPort(MachineRecipeRoute route,
          Map<String, MachinePort> ports, String portId) {
        MachinePort candidate = ports.get(portId);
        if (candidate == null || candidate.isConfiguration()) return false;
        String candidateFamily = lanePortFamily(candidate);
        if (candidateFamily == null) return false;
        List<MachineResourceStack> outputs = new ArrayList<>(route.guaranteedOutputs());
        outputs.addAll(route.optionalOutputs());
        for (MachineResourceStack output : outputs) {
            MachinePort selected = ports.get(output.portId());
            if (selected != null && selected.kind() == candidate.kind() &&
                  candidateFamily.equals(lanePortFamily(selected))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String lanePortFamily(@Nullable MachinePort port) {
        if (port == null || port.laneId() < 0) return null;
        String suffix = "_" + port.laneId();
        String portId = port.portId();
        return portId.endsWith(suffix) && portId.length() > suffix.length() ?
              portId.substring(0, portId.length() - suffix.length()) : null;
    }

    @Nonnull
    private static Map<PortableResourceDescriptor, Long> aggregate(
          List<MachineResourceStack> stacks) {
        Map<PortableResourceDescriptor, Long> amounts = new LinkedHashMap<>();
        for (MachineResourceStack stack : stacks) {
            amounts.merge(describe(stack), stack.amount(), Math::addExact);
        }
        return Collections.unmodifiableMap(amounts);
    }

    @Nonnull
    private static Map<PortableResourceDescriptor, Long> scaleAmounts(
          Map<PortableResourceDescriptor, Long> amounts, long multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("QIO amount multiplier must be positive");
        }
        Map<PortableResourceDescriptor, Long> scaled = new LinkedHashMap<>();
        amounts.forEach((resource, amount) ->
              scaled.put(resource, Math.multiplyExact(amount, multiplier)));
        return Collections.unmodifiableMap(scaled);
    }

    private static boolean hasActiveJobOperation(QIOProcessingNetworkData network,
          UUID jobId, UUID operationId) {
        QIOCraftingJob job = network.getJob(jobId);
        if (job == null) {
            return false;
        }
        for (QIOStepRuntime runtime : job.getStepRuntimes().values()) {
            if (runtime.getOperation(operationId) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActiveJobOperationTransfer(QIOProcessingNetworkData network,
          UUID jobId, UUID operationId) {
        String suffix = "/" + operationId;
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            if (jobId.equals(transfer.getOwnerJobId()) &&
                  transfer.getNodeId().endsWith(suffix) &&
                  transfer.getResolution() == QIODurableTransferRecord.Resolution.NONE) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCommittedMachineOutput(QIOProcessingNetworkData network,
          UUID jobId, long nodeId, UUID operationId) {
        return committedMachineOutputTransfer(network, jobId, nodeId, operationId) != null;
    }

    @Nullable
    private static QIODurableTransferRecord committedMachineOutputTransfer(
          QIOProcessingNetworkData network, UUID jobId, long nodeId, UUID operationId) {
        QIODurableTransferRecord transfer = findOperationTransferAny(network, jobId,
              QIODurableTransferRecord.Type.MACHINE_TO_JOB, nodeId, operationId);
        return transfer != null && transfer.getPhase() ==
              QIODurableTransferRecord.Phase.COMMITTED && transfer.getResolution() ==
              QIODurableTransferRecord.Resolution.FORWARD_COMMITTED ? transfer : null;
    }

    private static boolean endpointAtBaseline(QIOProcessingNetworkData network,
          QIOCraftingJob job, QIOStepRuntime runtime,
          QIOOperationAssignment assignment) {
        QIOPlanStep step = planStep(job, runtime.getNodeId());
        if (step == null) {
            return false;
        }
        MachineEndpoint endpoint = findMachineEndpoint(network, step, null,
              assignment.getDeviceUUID(), assignment.getOperationId(), false);
        QIODurableTransferRecord input = findOperationTransferAny(network, job.getJobId(),
              QIODurableTransferRecord.Type.JOB_TO_MACHINE, runtime.getNodeId(),
              assignment.getOperationId());
        if (endpoint == null || input == null || input.getMachineBaselines().isEmpty()) {
            return false;
        }
        return machineMatchesLeaseBaseline(withBaselines(endpoint,
              input.getMachineBaselines()));
    }

    private static int settleCompletedJobOperation(QIOProcessingNetworkData network,
          QIOCraftingJob job, QIOStepRuntime runtime,
          QIOOperationAssignment assignment) throws IOException {
        network.completeStepOperation(job.getJobId(), runtime.getNodeId(),
              assignment.getOperationId());
        network.removeCommittedJobOperationTransfers(job.getJobId(),
              assignment.getOperationId());
        persist(network);
        INSTANCE.wakeDevice(network.getFrequencyUUID(), assignment.getDeviceUUID());
        return 1;
    }

    private static int settleReturnedJobOperation(QIOProcessingNetworkData network,
          QIOCraftingJob job, QIOStepRuntime runtime,
          QIOOperationAssignment assignment) throws IOException {
        network.failStepOperation(job.getJobId(), runtime.getNodeId(),
              assignment.getOperationId(),
              "Workbench operation returned without producing its output");
        network.transitionJobState(job.getJobId(), QIOCraftingJobState.RETURNING);
        network.removeCommittedJobOperationTransfers(job.getJobId(),
              operationKey(runtime.getNodeId(), assignment.getOperationId()));
        persist(network);
        return 1;
    }

    private static int settleProcessorLane(QIOProcessingNetworkData network,
          QIOCraftingJob job, QIOStepRuntime runtime,
          QIOOperationAssignment assignment, LoadedProcessor loaded,
          QIOProcessorLaneRuntime lane) throws IOException {
        QIOCraftingProcessorState state = loaded.processor().getProcessorState();
        if (!lane.isSettled()) {
            return 0;
        }
        if (!state.isPersistedSettledOperation(lane.getOperationId())) {
            QIOEndpointPersistenceService.INSTANCE.requestProcessor(loaded.processor(),
                  lane.getOperationId());
            return 0;
        }
        if (lane.getSettlement() == QIOProcessorLaneRuntime.Settlement.OUTPUT) {
            network.completeStepOperation(job.getJobId(), runtime.getNodeId(),
                  assignment.getOperationId());
        } else {
            network.failStepOperation(job.getJobId(), runtime.getNodeId(),
                  assignment.getOperationId(),
                  "Workbench operation returned without producing its output");
            network.transitionJobState(job.getJobId(), QIOCraftingJobState.RETURNING);
        }
        network.removeCommittedJobOperationTransfers(job.getJobId(),
              operationKey(runtime.getNodeId(), assignment.getOperationId()));
        persist(network);
        state.removeSettledLane(lane.getLaneId(), lane.getOperationId());
        INSTANCE.wakeDevice(network.getFrequencyUUID(), loaded.processorUUID());
        return 1;
    }

    private static boolean ensurePassiveMachineOperation(QIOPassiveOperation operation,
          MachineEndpoint endpoint, QIODurableTransferRecord transfer) {
        UUID leaseId = operation.getLeaseId();
        if (leaseId == null || !leaseId.equals(transfer.getLeaseId()) ||
              endpoint.laneId != operation.getLaneId()) {
            return false;
        }
        QIOAutomationHost host = endpoint.device.host();
        MachineOperationToken token = host.getOperationTokens().get(operation.getOperationId());
        if (token != null) {
            boolean matches = token.kind() == MachineOperationToken.Kind.PASSIVE &&
                  token.leaseId().equals(leaseId) && token.laneId() == endpoint.laneId &&
                  token.routeId().equals(endpoint.route.routeId()) &&
                  token.recipeKey().equals(endpoint.route.recipeKey());
            if (!matches) {
                return false;
            }
            return prepareRecoveredLoading(host, operation.getOperationId(), endpoint,
                  transfer, token);
        }
        if (!machineReadyForInput(endpoint) && !machineMatchesAfterInput(endpoint) &&
              !machineMatchesCompletedOperation(endpoint) &&
              !machineMatchesLeaseBaseline(endpoint)) {
            return false;
        }
        MachineOperationLease lease = host.getLeases().get(leaseId);
        if (lease == null) {
            lease = host.tryAcquireLease(leaseId, operation.getOperationId(),
                  MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, endpoint.laneId,
                  operation.getCreatedAtTick(), endpoint.baselines);
        }
        if (lease == null || lease.state() != MachineOperationLease.State.ACQUIRED ||
              !lease.ownerOperationId().equals(operation.getOperationId()) ||
              lease.laneId() != endpoint.laneId) {
            return false;
        }
        token = MachineOperationToken.passive(operation.getOperationId(), leaseId,
              endpoint.route.routeId(), endpoint.route.recipeKey(), endpoint.laneId);
        if (!host.attachOperationToken(token)) {
            return false;
        }
        return prepareRecoveredLoading(host, operation.getOperationId(), endpoint,
              transfer, token);
    }

    private static boolean ensureScheduledMachineOperation(QIOCraftingJob job,
          QIOOperationAssignment assignment, MachineEndpoint endpoint,
          QIODurableTransferRecord transfer) {
        UUID leaseId = transfer.getLeaseId();
        if (leaseId == null || endpoint.laneId != assignment.getLaneId()) {
            return false;
        }
        QIOAutomationHost host = endpoint.device.host();
        MachineOperationToken token = host.getOperationTokens().get(assignment.getOperationId());
        if (token != null) {
            boolean matches = token.kind() == MachineOperationToken.Kind.JOB &&
                  job.getJobId().equals(token.jobId()) &&
                  token.planRevision() == job.getActivePlan().getRevision() &&
                  token.leaseId().equals(leaseId) && token.laneId() == endpoint.laneId &&
                  token.routeId().equals(endpoint.route.routeId()) &&
                  token.recipeKey().equals(endpoint.route.recipeKey());
            if (!matches) {
                return false;
            }
            return prepareRecoveredLoading(host, assignment.getOperationId(), endpoint,
                  transfer, token);
        }
        if (!machineReadyForInput(endpoint) && !machineMatchesAfterInput(endpoint) &&
              !machineMatchesCompletedOperation(endpoint) &&
              !machineMatchesLeaseBaseline(endpoint)) {
            return false;
        }
        MachineOperationLease lease = host.getLeases().get(leaseId);
        if (lease == null) {
            lease = host.tryAcquireLease(leaseId, assignment.getOperationId(),
                  MachineOperationLease.Mode.PROCESSING_EXCLUSIVE, endpoint.laneId,
                  assignment.getDispatchedAtTick(), endpoint.baselines);
        }
        if (lease == null || lease.state() != MachineOperationLease.State.ACQUIRED ||
              !lease.ownerOperationId().equals(assignment.getOperationId()) ||
              lease.laneId() != endpoint.laneId) {
            return false;
        }
        token = MachineOperationToken.job(assignment.getOperationId(), leaseId, job.getJobId(),
              job.getActivePlan().getRevision(), endpoint.route.routeId(),
              endpoint.route.recipeKey(), endpoint.laneId);
        if (!host.attachOperationToken(token)) {
            return false;
        }
        return prepareRecoveredLoading(host, assignment.getOperationId(), endpoint,
              transfer, token);
    }

    private static boolean prepareRecoveredLoading(QIOAutomationHost host,
          UUID operationId, MachineEndpoint endpoint, QIODurableTransferRecord transfer,
          MachineOperationToken token) {
        boolean loading = token.state() == MachineOperationToken.State.ALLOCATED ?
              host.transitionOperation(operationId, MachineOperationToken.State.LOADING,
                    MachineOperationLease.State.LOADING) :
              token.state() == MachineOperationToken.State.LOADING;
        if (!loading) {
            return false;
        }
        MachineOperationToken current = host.getOperationTokens().get(operationId);
        return transfer.getPhase() == QIODurableTransferRecord.Phase.PREPARED ||
              machineMatchesLeaseBaseline(endpoint) || current != null &&
                    (current.hasTransferReceipt(transfer.getTransferId()) ||
                          host.recordTransferReceipt(operationId, transfer.getTransferId()));
    }

    @Nullable
    private static MachineEndpoint passiveEndpoint(QIOProcessingNetworkData network,
          QIOPassiveOperation operation, boolean requireReady) {
        LoadedDevice device = QIOAutomationDeviceRegistry.INSTANCE.findLoadedDevice(
              operation.getDeviceUUID());
        if (device == null || device.host().getEnabledMode() != QIOAutomationMode.PASSIVE ||
              !matchesFrequencyOrOwnedDrain(network, device.host(), QIOAutomationMode.PASSIVE,
                    operation.getOperationId())) {
            return null;
        }
        MachineRecipeProviderRegistry.BoundProvider provider =
              MachineRecipeProviderRegistry.find(device.tile());
        if (provider == null || !provider.id().toString().equals(operation.getProviderId())) {
            return null;
        }
        Map<String, MachinePort> ports = ports(provider);
        for (MachineRecipeRoute route : provider.getRecipeRoutes()) {
            if (route.routeId().equals(operation.getRouteId()) &&
                  route.recipeKey().equals(operation.getRecipeKey())) {
                MachineEndpoint endpoint = endpoint(device, provider, route, ports);
                if (endpoint == null) {
                    return null;
                }
                endpoint = scaleEndpoint(endpoint, operation.getOperationCount());
                return !requireReady || machineReadyForInput(endpoint) ? endpoint : null;
            }
        }
        return null;
    }

    private static boolean matchesFrequencyOrOwnedDrain(QIOProcessingNetworkData network,
          QIOAutomationHost host, QIOAutomationMode mode, @Nullable UUID operationId) {
        QIOFrequencyReference reference = host.getFrequencyReference();
        if (reference != null && network.getFrequencyUUID().equals(reference.getFrequencyUUID())) {
            return true;
        }
        if (operationId == null || host.getState() != QIOAutomationHost.State.DRAINING_CHANGE ||
            host.getEnabledMode() != mode) {
            return false;
        }
        if (host.getOperationTokens().containsKey(operationId)) {
            return true;
        }
        return host.getLeases().values().stream().anyMatch(lease ->
              operationId.equals(lease.ownerOperationId()));
    }

    @Nonnull
    private static Map<String, MachinePort> ports(
          MachineRecipeProviderRegistry.BoundProvider provider) {
        Map<String, MachinePort> ports = new LinkedHashMap<>();
        for (MachinePort port : provider.getPorts()) {
            if (port != null) {
                ports.put(port.portId(), port);
            }
        }
        return ports;
    }

    @Nullable
    private static QIODurableTransferRecord firstOperationTransfer(
          QIOProcessingNetworkData network, QIOPassiveOperation operation,
          QIODurableTransferRecord.Type type) {
        List<QIODurableTransferRecord> transfers = network.getActiveOperationTransfers(
              operation.getOperationId(), type);
        return transfers.isEmpty() ? null : transfers.get(0);
    }

    @Nullable
    private static QIODurableTransferRecord firstOperationTransferAny(
          QIOProcessingNetworkData network, QIOPassiveOperation operation,
          QIODurableTransferRecord.Type type) {
        QIODurableTransferRecord found = null;
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            if (operation.getOperationId().equals(transfer.getOwnerOperationId()) &&
                  transfer.getType() == type) {
                if (found != null) {
                    throw new IllegalStateException(
                          "Passive operation has multiple transfers of the same type");
                }
                found = transfer;
            }
        }
        return found;
    }

    @Nonnull
    private static Map<UUID, Long> boundAmounts(QIOPassiveOperation operation) {
        Map<UUID, Long> amounts = new LinkedHashMap<>();
        operation.getRequiredInputs().forEach((resource, amount) -> {
            UUID uuid = operation.getResourceBindings().get(resource);
            if (uuid == null || amounts.put(uuid, amount) != null) {
                throw new IllegalStateException("Passive operation has invalid QIO bindings");
            }
        });
        return amounts;
    }

    @Nonnull
    private static Map<PortableResourceDescriptor, Long> unbindAmounts(
          QIOPassiveOperation operation, Map<UUID, Long> amounts) {
        Map<UUID, PortableResourceDescriptor> resources = new LinkedHashMap<>();
        operation.getResourceBindings().forEach((resource, uuid) -> resources.put(uuid, resource));
        Map<PortableResourceDescriptor, Long> unbound = new LinkedHashMap<>();
        amounts.forEach((uuid, amount) -> {
            PortableResourceDescriptor resource = resources.get(uuid);
            if (resource == null || unbound.put(resource, amount) != null) {
                throw new IllegalStateException("Passive claim returned an unknown resource binding");
            }
        });
        return unbound;
    }

    @Nullable
    private static Map<PortableResourceDescriptor, UUID> resolveAvailableBindings(
          QIOStorageSnapshot snapshot, Map<PortableResourceDescriptor, Long> inputs) {
        Map<PortableResourceDescriptor, QIOStorageEntry> entries = new LinkedHashMap<>();
        for (QIOStorageEntry entry : snapshot.getEntries()) {
            PortableResourceDescriptor resource = PortableResourceDescriptor.fromStorageEntry(entry);
            if (entries.put(resource, entry) != null) {
                return null;
            }
        }
        Map<PortableResourceDescriptor, UUID> bindings = new LinkedHashMap<>();
        for (Map.Entry<PortableResourceDescriptor, Long> input : inputs.entrySet()) {
            QIOStorageEntry entry = entries.get(input.getKey());
            if (entry == null || entry.getAvailableAmountClamped() < input.getValue()) {
                return null;
            }
            bindings.put(input.getKey(), entry.getResourceUUID());
        }
        return bindings;
    }

    private static long maxAvailableOperations(QIOStorageSnapshot snapshot,
          Map<PortableResourceDescriptor, Long> inputs, long requested) {
        if (requested <= 0) {
            return 0;
        }
        Map<PortableResourceDescriptor, Long> available = new LinkedHashMap<>();
        for (QIOStorageEntry entry : snapshot.getEntries()) {
            PortableResourceDescriptor resource = PortableResourceDescriptor.fromStorageEntry(entry);
            available.merge(resource, entry.getAvailableAmountClamped(),
                  QIOProcessingExecutionService::saturatedAdd);
        }
        long operations = requested;
        for (Map.Entry<PortableResourceDescriptor, Long> input : inputs.entrySet()) {
            operations = Math.min(operations,
                  available.getOrDefault(input.getKey(), 0L) / input.getValue());
        }
        return operations;
    }

    private static long maxBufferOperations(@Nullable QIOJobBuffer buffer,
          Map<PortableResourceDescriptor, Long> inputs, long requested) {
        if (buffer == null || requested <= 0) {
            return 0;
        }
        long operations = requested;
        for (Map.Entry<PortableResourceDescriptor, Long> input : inputs.entrySet()) {
            long available = saturatedAdd(buffer.get(QIOJobBuffer.Compartment.PRODUCED,
                  input.getKey()), buffer.get(QIOJobBuffer.Compartment.RESERVED,
                  input.getKey()));
            operations = Math.min(operations, available / input.getValue());
        }
        return operations;
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static Set<UUID> activeDeviceUUIDs(QIOCraftingJob job) {
        Set<UUID> devices = new LinkedHashSet<>();
        for (QIOStepRuntime runtime : job.getStepRuntimes().values()) {
            for (QIOOperationAssignment assignment : runtime.getActiveOperations().values()) {
                devices.add(assignment.getDeviceUUID());
            }
        }
        return devices;
    }

    @Nullable
    private static IQIOStorageView openPassive(QIOProcessingNetworkData network) {
        QIOFrequencyIdentitySnapshot identity = network.getLastKnownFrequencyIdentity();
        UUID requester = identity.getOwnerUUID();
        return QIOFrequencyStorageAccess.INSTANCE.open(new QIOFrequencyReference(
              network.getFrequencyUUID(), identity.getName(), identity.getOwnerUUID(),
              identity.getSecurityMode(), requester), requester);
    }

    private static String passiveOwner(QIOPassiveOperation operation) {
        return "passive/" + operation.getOperationId();
    }

    @Nonnull
    private static PortableResourceDescriptor describe(MachineResourceStack stack) {
        return switch (stack.kind()) {
            case ITEM -> PortableResourceDescriptor.item(stack.itemStack());
            case FLUID -> PortableResourceDescriptor.fluid(
                  Objects.requireNonNull(stack.fluidStack(), "machine fluid"));
            case GAS -> PortableResourceDescriptor.gas(
                  Objects.requireNonNull(stack.gasStack(), "machine gas"));
        };
    }

    @Nullable
    static QIOPlanStep nextRunnableStep(QIOCraftingJob job, QIOJobBuffer buffer) {
        return nextRunnableStep(job, buffer, step -> true);
    }

    @Nullable
    static QIOPlanStep nextRunnableStep(QIOCraftingJob job, QIOJobBuffer buffer,
          Predicate<QIOPlanStep> dispatchable) {
        for (QIOPlanStep step : job.getActivePlan().getSteps()) {
            QIOStepRuntime runtime = job.getStepRuntime(step.getNodeId());
            if (runtime != null && runtime.getRemainingOperations() > 0 &&
                  job.getCycleDispatchAllowance(step.getNodeId()) > 0 &&
                  buffer != null && (step.getCandidateInputs().isEmpty() ?
                        buffer.canSupply(step.getExactInputs()) :
                        buffer.canSupply(step.getFixedInputs(), step.getCandidateInputs())) &&
                  dispatchable.test(step)) {
                return step;
            }
        }
        return null;
    }

    @Nonnull
    static <T> List<T> orderProvidersRoundRobin(@Nonnull List<T> candidates,
          @Nonnull Function<T, UUID> identity, @Nonnull ToLongFunction<T> priority,
          @Nonnull Function<Long, UUID> lastProviderByPriority) {
        List<T> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingLong(priority).reversed()
              .thenComparing(candidate -> identity.apply(candidate).toString()));
        if (sorted.size() < 2) {
            return sorted;
        }
        List<T> ordered = new ArrayList<>(sorted.size());
        int groupStart = 0;
        while (groupStart < sorted.size()) {
            long groupPriority = priority.applyAsLong(sorted.get(groupStart));
            int groupEnd = groupStart + 1;
            while (groupEnd < sorted.size() &&
                  priority.applyAsLong(sorted.get(groupEnd)) == groupPriority) {
                groupEnd++;
            }
            int groupSize = groupEnd - groupStart;
            int rotatedStart = 0;
            UUID lastProvider = lastProviderByPriority.apply(groupPriority);
            if (lastProvider != null) {
                for (int offset = 0; offset < groupSize; offset++) {
                    if (lastProvider.equals(identity.apply(sorted.get(groupStart + offset)))) {
                        rotatedStart = (offset + 1) % groupSize;
                        break;
                    }
                }
            }
            for (int offset = 0; offset < groupSize; offset++) {
                ordered.add(sorted.get(groupStart + (rotatedStart + offset) % groupSize));
            }
            groupStart = groupEnd;
        }
        return ordered;
    }

    private void advanceProviderCursor(UUID frequencyUUID, ProviderKind providerKind,
          String routeKey, long priority, UUID deviceUUID) {
        providerCursors.put(new ProviderCursorKey(frequencyUUID, providerKind, routeKey,
              priority), deviceUUID);
    }

    @Nullable
    private static QIOPlanStep planStep(QIOCraftingJob job, long nodeId) {
        for (QIOPlanStep step : job.getActivePlan().getSteps()) {
            if (step.getNodeId() == nodeId) {
                return step;
            }
        }
        return null;
    }

    @Nullable
    private static QIODurableTransferRecord findOperationTransfer(QIOProcessingNetworkData network,
          UUID jobId, QIODurableTransferRecord.Type type, long nodeId, UUID operationId) {
        String key = operationKey(nodeId, operationId);
        for (QIODurableTransferRecord transfer : network.getActiveJobTransfers(jobId, type)) {
            if (key.equals(transfer.getNodeId())) {
                return transfer;
            }
        }
        return null;
    }

    @Nullable
    private static QIODurableTransferRecord findOperationTransferAny(
          QIOProcessingNetworkData network, UUID jobId, QIODurableTransferRecord.Type type,
          long nodeId, UUID operationId) {
        String key = operationKey(nodeId, operationId);
        QIODurableTransferRecord found = null;
        for (QIODurableTransferRecord transfer : network.getDurableTransfers()) {
            if (jobId.equals(transfer.getOwnerJobId()) && transfer.getType() == type &&
                  key.equals(transfer.getNodeId())) {
                if (found != null) {
                    throw new IllegalStateException(
                          "QIO operation has multiple transfers of the same type");
                }
                found = transfer;
            }
        }
        return found;
    }

    private static String operationKey(long nodeId, UUID operationId) {
        return nodeId + "/" + operationId;
    }

    private static int configurationAffinity(MachineRecipeRoute route,
          Map<String, MachinePort> ports) {
        if (route.configurationInputs().isEmpty()) return 0;
        boolean empty = false;
        for (MachineResourceStack target : route.configurationInputs()) {
            MachinePort port = ports.get(target.portId());
            MachineResourceStack current = port == null ? null : port.peek();
            if (current == null) {
                empty = true;
            } else if (!current.sameResource(target)) {
                return 2;
            }
        }
        return empty ? 1 : 0;
    }

    private static boolean configurationPreflight(MachineEndpoint endpoint,
          QIOStorageSnapshot snapshot, IQIOStorageView view) {
        for (MachineResourceStack target : endpoint.route.configurationInputs()) {
            MachinePort port = endpoint.ports.get(target.portId());
            if (port == null || !port.isConfiguration() || target.amount() != 1) return false;
            MachineResourceStack current = port.peek();
            if (current != null && current.sameResource(target)) continue;
            QIOStorageEntry targetEntry = findStorageEntry(snapshot, describe(target));
            if (targetEntry == null || targetEntry.getExactAvailableAmount().signum() <= 0) {
                return false;
            }
            if (current != null && !port.canExtract(current)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static QIOStorageEntry findStorageEntry(QIOStorageSnapshot snapshot,
          PortableResourceDescriptor resource) {
        QIOStorageEntry found = null;
        for (QIOStorageEntry entry : snapshot.getEntries()) {
            if (!resource.equals(PortableResourceDescriptor.fromStorageEntry(entry))) continue;
            if (found != null && !found.getResourceUUID().equals(entry.getResourceUUID())) {
                return null;
            }
            found = entry;
        }
        return found;
    }

    private static boolean driveConfigurationExchanges(QIOProcessingNetworkData network,
          MachineEndpoint endpoint, UUID operationId, @Nullable UUID ownerJobId,
          IQIOStorageView view) throws IOException {
        if (endpoint.route.configurationInputs().isEmpty()) return true;
        QIOAutomationHost host = endpoint.device.host();
        MachineOperationToken token = host.getOperationTokens().get(operationId);
        MachineOperationLease lease = token == null ? null : host.getLeases().get(token.leaseId());
        if (token == null || lease == null || lease.state() != MachineOperationLease.State.LOADING) {
            return false;
        }
        List<QIOConfigurationExchangeRecord> exchanges =
              network.getOperationConfigurationExchanges(operationId);
        if (exchanges.isEmpty()) {
            if (!configurationPreflight(endpoint, view.getSnapshot(), view)) return false;
            for (MachineResourceStack target : endpoint.route.configurationInputs()) {
                MachinePort port = endpoint.ports.get(target.portId());
                MachineResourceStack current = port.peek();
                QIOConfigurationExchangeRecord exchange;
                if (current != null && current.sameResource(target)) {
                    exchange = QIOConfigurationExchangeRecord.reused(UUID.randomUUID(),
                          operationId, ownerJobId, host.getPersistentDeviceUUID(), token.leaseId(),
                          port.portId(), port.portGroupId(), endpoint.laneId, target, current);
                } else {
                    QIOStorageEntry targetEntry = findStorageEntry(view.getSnapshot(),
                          describe(target));
                    if (targetEntry == null ||
                          targetEntry.getExactAvailableAmount().signum() <= 0) return false;
                    exchange = new QIOConfigurationExchangeRecord(UUID.randomUUID(), operationId,
                          ownerJobId, host.getPersistentDeviceUUID(), token.leaseId(),
                          port.portId(), port.portGroupId(), endpoint.laneId, target, current,
                          targetEntry.getResourceUUID());
                }
                network.addConfigurationExchange(exchange);
            }
            persist(network);
            exchanges = network.getOperationConfigurationExchanges(operationId);
        }
        if (!configurationRecordsMatch(exchanges, endpoint, operationId, ownerJobId, token)) {
            contaminateConfigurationOperation(network, endpoint, operationId,
                  "Configuration exchange identity no longer matches its route");
            return false;
        }
        boolean allClaimsReady = true;
        for (QIOConfigurationExchangeRecord exchange : exchanges) {
            if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.CONTAMINATED) {
                return false;
            }
            if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.CLAIM_PREPARED &&
                  !prepareConfigurationClaim(network, exchange, view)) {
                allClaimsReady = false;
            }
        }
        if (!allClaimsReady || exchanges.stream().anyMatch(exchange ->
              exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.CLAIM_PREPARED)) {
            return false;
        }
        for (QIOConfigurationExchangeRecord exchange : exchanges) {
            if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.CONTAMINATED) {
                return false;
            }
            if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.COMMITTED) {
                if (!portHoldsConfiguration(endpoint.ports.get(exchange.getPortId()),
                      exchange.getTarget(), baseline(endpoint.baselines,
                            exchange.getPortId()))) {
                    contaminateConfigurationOperation(network, endpoint, operationId,
                          "Committed configuration changed before processing input");
                    return false;
                }
                continue;
            }
            if (!driveConfigurationExchange(network, endpoint, exchange, view)) return false;
        }
        return configurationStillMatches(endpoint) &&
              network.getOperationConfigurationExchanges(operationId).stream().allMatch(exchange ->
                    exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.COMMITTED);
    }

    private static boolean driveConfigurationExchange(QIOProcessingNetworkData network,
          MachineEndpoint endpoint, QIOConfigurationExchangeRecord exchange,
          IQIOStorageView view) throws IOException {
        QIOAutomationHost host = endpoint.device.host();
        MachinePort port = endpoint.ports.get(exchange.getPortId());
        UUID operationId = exchange.getOperationId();
        MachineOperationToken token = host.getOperationTokens().get(operationId);
        if (port == null || token == null) return false;
        String owner = "configuration/" + operationId + "/" + exchange.getPortId();
        Map<UUID, Long> amount = Collections.singletonMap(exchange.getTargetResourceUUID(), 1L);
        if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.CLAIMED) {
            QIOStorageEntry entry = view.getResource(exchange.getTargetResourceUUID());
            if (entry == null || !exchange.getTargetResource().equals(
                  PortableResourceDescriptor.fromStorageEntry(entry)) ||
                  entry.getExactStoredAmount().signum() <= 0) return false;
            exchange.prepareTargetDebit(entry.getExactStoredAmount());
            network.markConfigurationExchangeChanged(exchange.getExchangeId());
            persist(network);
        }
        if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.TARGET_DEBIT_PREPARED) {
            QIOClaimResult result = view.submitClaim(QIOClaimRequest.consumeReconciled(
                  exchange.getConsumeRequestId(), exchange.getTargetDebitTransferId(),
                  exchange.getClaimId(), MekanismQIOProcessing.MODID, owner,
                  QIOClaimRequest.ANY_REVISION, QIOClaimRequest.ANY_REVISION, amount,
                  Collections.singletonMap(exchange.getTargetResourceUUID(),
                        Objects.requireNonNull(exchange.getTargetQioBaseline()))));
            if (!result.isSuccess() || !result.getConsumedAmounts().equals(amount)) {
                QIOStorageEntry entry = view.getResource(exchange.getTargetResourceUUID());
                if (entry != null && exchange.getTargetResource().equals(
                      PortableResourceDescriptor.fromStorageEntry(entry)) &&
                      entry.getExactStoredAmount().equals(exchange.getTargetQioBaseline())) {
                    if (exchange.retryTargetDebit(entry.getExactStoredAmount(),
                          view.getContentsRevision(), view.getClaimRevision())) {
                        network.markConfigurationExchangeChanged(exchange.getExchangeId());
                        persist(network);
                    }
                }
                return false;
            }
            exchange.markTargetDebited();
            network.markConfigurationExchangeChanged(exchange.getExchangeId());
            persist(network);
        }
        if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.TARGET_DEBITED &&
              exchange.getOriginal() != null) {
            MachineResourceStack current = port.peek();
            if (!exactMachineStack(current, exchange.getOriginal())) {
                contaminateConfigurationOperation(network, endpoint, operationId,
                      "Configuration changed before old template extraction");
                return false;
            }
            if (simulateInsert(view, exchange.getOriginalResource(),
                  exchange.getOriginal().amount()) != exchange.getOriginal().amount()) return false;
            exchange.prepareOldReturn(storedAmount(view, exchange.getOriginalResource()));
            network.markConfigurationExchangeChanged(exchange.getExchangeId());
            persist(network);
        }
        if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.OLD_RETURN_PREPARED) {
            UUID receipt = exchange.getOldExtractionReceiptId();
            if (!token.hasTransferReceipt(receipt)) {
                MachineResourceStack current = port.peek();
                if (!exactMachineStack(current, exchange.getOriginal())) {
                    contaminateConfigurationOperation(network, endpoint, operationId,
                          "Configuration changed before the exact old template extraction");
                    return false;
                }
                if (token.transferReceipts().size() >= MachineOperationToken.MAX_TRANSFER_RECEIPTS ||
                      port.extract(exchange.getOriginal()) == null) return false;
                if (!host.recordTransferReceipt(operationId, receipt)) {
                    throw new IllegalStateException(
                          "Configuration extraction could not persist its reserved receipt");
                }
                QIOEndpointPersistenceService.INSTANCE.requestAutomationHostReceipt(
                      endpoint.device.tile(), host, operationId, receipt);
                return false;
            }
            if (port.peek() != null) {
                contaminateConfigurationOperation(network, endpoint, operationId,
                      "Configuration port changed after old template extraction");
                return false;
            }
            if (!host.isTransferReceiptPersisted(operationId, receipt)) {
                QIOEndpointPersistenceService.INSTANCE.requestAutomationHostReceipt(
                      endpoint.device.tile(), host, operationId, receipt);
                return false;
            }
            exchange.markOldExtracted();
            network.markConfigurationExchangeChanged(exchange.getExchangeId());
            persist(network);
        }
        if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.OLD_EXTRACTED) {
            QIOTransferResult result = insert(view, exchange.getOldQioTransferId(),
                  exchange.getOriginalResource(), exchange.getOriginal().amount(),
                  Objects.requireNonNull(exchange.getOldQioBaseline()));
            if (!result.isSuccess() || result.getTransferredAmount() !=
                  exchange.getOriginal().amount()) return false;
            exchange.markOldQioCredited();
            network.markConfigurationExchangeChanged(exchange.getExchangeId());
            persist(network);
        }
        if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.TARGET_DEBITED ||
              exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.OLD_QIO_CREDITED) {
            UUID receipt = exchange.getNewInstallationReceiptId();
            if (!token.hasTransferReceipt(receipt)) {
                if (port.peek() != null) {
                    contaminateConfigurationOperation(network, endpoint, operationId,
                          "Configuration port was no longer empty before target installation");
                    return false;
                }
                if (token.transferReceipts().size() >= MachineOperationToken.MAX_TRANSFER_RECEIPTS ||
                      !port.insert(exchange.getTarget())) return false;
                if (!host.recordTransferReceipt(operationId, receipt)) {
                    throw new IllegalStateException(
                          "Configuration installation could not persist its reserved receipt");
                }
                QIOEndpointPersistenceService.INSTANCE.requestAutomationHostReceipt(
                      endpoint.device.tile(), host, operationId, receipt);
                return false;
            }
            if (!exactMachineStack(port.peek(), exchange.getTarget())) {
                contaminateConfigurationOperation(network, endpoint, operationId,
                      "Configuration port changed after target installation");
                return false;
            }
            if (!host.isTransferReceiptPersisted(operationId, receipt)) {
                QIOEndpointPersistenceService.INSTANCE.requestAutomationHostReceipt(
                      endpoint.device.tile(), host, operationId, receipt);
                return false;
            }
            exchange.markNewInstalled();
            network.markConfigurationExchangeChanged(exchange.getExchangeId());
            persist(network);
        }
        if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.NEW_INSTALLED) {
            if (!exactMachineStack(port.peek(), exchange.getTarget())) {
                contaminateConfigurationOperation(network, endpoint, operationId,
                      "Target configuration changed before exchange commit");
                return false;
            }
            exchange.commit();
            network.markConfigurationExchangeChanged(exchange.getExchangeId());
            persist(network);
        }
        return exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.COMMITTED;
    }

    private static boolean prepareConfigurationClaim(QIOProcessingNetworkData network,
          QIOConfigurationExchangeRecord exchange, IQIOStorageView view) throws IOException {
        Map<UUID, Long> amount = Collections.singletonMap(exchange.getTargetResourceUUID(), 1L);
        String owner = "configuration/" + exchange.getOperationId() + "/" +
              exchange.getPortId();
        QIOClaimResult result = view.submitClaim(QIOClaimRequest.createOrAdjust(
              exchange.getClaimRequestId(), exchange.getClaimId(),
              MekanismQIOProcessing.MODID, owner, 0, 0, QIOClaimRequest.ANY_REVISION,
              QIOClaimRequest.ANY_REVISION, amount));
        if (!result.isSuccess() || !result.getCommittedAmounts().equals(amount)) {
            if (exchange.retryClaim(view.getContentsRevision(), view.getClaimRevision())) {
                network.markConfigurationExchangeChanged(exchange.getExchangeId());
                persist(network);
            }
            return false;
        }
        exchange.markClaimed();
        network.markConfigurationExchangeChanged(exchange.getExchangeId());
        persist(network);
        return true;
    }

    private static boolean configurationRecordsMatch(
          List<QIOConfigurationExchangeRecord> exchanges, MachineEndpoint endpoint,
          UUID operationId, @Nullable UUID ownerJobId, MachineOperationToken token) {
        if (exchanges.size() != endpoint.route.configurationInputs().size()) return false;
        Map<String, MachineResourceStack> expected = configurationByPort(endpoint.route);
        for (QIOConfigurationExchangeRecord exchange : exchanges) {
            MachinePort port = endpoint.ports.get(exchange.getPortId());
            MachineResourceStack target = expected.remove(exchange.getPortId());
            if (!exchange.getOperationId().equals(operationId) ||
                  !Objects.equals(exchange.getOwnerJobId(), ownerJobId) ||
                  !exchange.getDeviceUUID().equals(token == null ? null :
                        endpoint.device.host().getPersistentDeviceUUID()) ||
                  !exchange.getLeaseId().equals(token.leaseId()) || port == null ||
                  !port.isConfiguration() ||
                  !exchange.getPortGroupId().equals(port.portGroupId()) ||
                  target == null || !target.equals(exchange.getTarget())) return false;
        }
        return expected.isEmpty();
    }

    private static boolean exactMachineStack(@Nullable MachineResourceStack current,
          @Nullable MachineResourceStack expected) {
        return current == null || expected == null ? current == null && expected == null :
              current.amount() == expected.amount() && current.sameResource(expected);
    }

    private static boolean configurationStillMatches(MachineEndpoint endpoint) {
        for (MachineResourceStack target : endpoint.route.configurationInputs()) {
            if (!portHoldsConfiguration(endpoint.ports.get(target.portId()), target,
                  baseline(endpoint.baselines, target.portId()))) return false;
        }
        return true;
    }

    @Nullable
    private static MachinePortBaseline baseline(List<MachinePortBaseline> baselines,
          String portId) {
        for (MachinePortBaseline baseline : baselines) {
            if (baseline.portId().equals(portId)) return baseline;
        }
        return null;
    }

    private static void contaminateConfigurationOperation(QIOProcessingNetworkData network,
          MachineEndpoint endpoint, UUID operationId, String reason) throws IOException {
        MachineOperationToken token = endpoint.device.host().getOperationTokens().get(operationId);
        if (token != null) endpoint.device.host().contaminateLease(token.leaseId(), reason);
        for (QIOConfigurationExchangeRecord exchange :
              network.getOperationConfigurationExchanges(operationId)) {
            if (!exchange.getPhase().isTerminal()) {
                exchange.contaminate(reason);
                network.markConfigurationExchangeChanged(exchange.getExchangeId());
            }
        }
        persist(network);
    }

    private static boolean releaseConfigurationClaims(QIOProcessingNetworkData network,
          UUID operationId, IQIOStorageView view) throws IOException {
        for (QIOConfigurationExchangeRecord exchange :
              network.getOperationConfigurationExchanges(operationId)) {
            if (!exchange.hasOutstandingClaim()) continue;
            QIOClaimResult result = view.submitClaim(QIOClaimRequest.release(
                  exchange.getReleaseRequestId(), exchange.getClaimId(),
                  MekanismQIOProcessing.MODID,
                  "configuration/" + operationId + "/" + exchange.getPortId(),
                  QIOClaimRequest.ANY_REVISION, Collections.emptyMap()));
            if (!result.isSuccess() && result.getStatus() != QIOClaimResult.Status.NOT_FOUND) {
                return false;
            }
            exchange.markClaimReleased();
            network.markConfigurationExchangeChanged(exchange.getExchangeId());
            persist(network);
        }
        return true;
    }

    private static boolean settleConfigurationCancellation(QIOProcessingNetworkData network,
          MachineEndpoint endpoint, UUID operationId, IQIOStorageView view) throws IOException {
        for (QIOConfigurationExchangeRecord exchange :
              network.getOperationConfigurationExchanges(operationId)) {
            if (exchange.getPhase().isSettled()) continue;
            if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.CONTAMINATED) {
                return false;
            }
            if (exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.CLAIM_PREPARED ||
                  exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.CLAIMED ||
                  exchange.getPhase() == QIOConfigurationExchangeRecord.Phase.TARGET_DEBIT_PREPARED) {
                if (exchange.hasOutstandingClaim()) {
                    QIOClaimResult result = view.submitClaim(QIOClaimRequest.release(
                          exchange.getReleaseRequestId(), exchange.getClaimId(),
                          MekanismQIOProcessing.MODID,
                          "configuration/" + operationId + "/" + exchange.getPortId(),
                          QIOClaimRequest.ANY_REVISION, Collections.emptyMap()));
                    if (!result.isSuccess() &&
                          result.getStatus() != QIOClaimResult.Status.NOT_FOUND) return false;
                    exchange.markClaimReleased();
                }
                exchange.cancelBeforeTargetDebit();
                network.markConfigurationExchangeChanged(exchange.getExchangeId());
                persist(network);
                continue;
            }
            if (!driveConfigurationExchange(network, endpoint, exchange, view)) return false;
        }
        return network.getOperationConfigurationExchanges(operationId).stream().allMatch(
              exchange -> exchange.getPhase().isSettled());
    }

    @Nullable
    private static IQIOStorageView open(QIOProcessingNetworkData network, QIOCraftingJob job) {
        QIOFrequencyIdentitySnapshot identity = network.getLastKnownFrequencyIdentity();
        UUID requester = job.getRequester() == null ? identity.getOwnerUUID() : job.getRequester();
        return QIOFrequencyStorageAccess.INSTANCE.open(new QIOFrequencyReference(
              network.getFrequencyUUID(), identity.getName(), identity.getOwnerUUID(),
              identity.getSecurityMode(), requester), requester);
    }

    private static long simulateInsert(IQIOStorageView view,
          PortableResourceDescriptor resource, long amount) {
        return switch (resource.getKind()) {
            case ITEM -> view.insert(resource.resolveItem(), amount, Action.SIMULATE);
            case FLUID -> {
                FluidStack stack = resource.resolveFluid();
                yield stack == null ? 0 : view.insert(stack, amount, Action.SIMULATE);
            }
            case GAS -> {
                GasStack stack = resource.resolveGas();
                yield stack == null ? 0 : view.insert(stack, amount, Action.SIMULATE);
            }
        };
    }

    private static QIOTransferResult insert(IQIOStorageView view, UUID transferId,
          PortableResourceDescriptor resource, long amount, @Nonnull BigInteger baseline) {
        Objects.requireNonNull(baseline, "missing QIO transfer baseline");
        return switch (resource.getKind()) {
            case ITEM -> view.insertIdempotent(transferId, resource.resolveItem(), amount, baseline);
            case FLUID -> view.insertIdempotent(transferId,
                  Objects.requireNonNull(resource.resolveFluid(), "missing fluid"), amount, baseline);
            case GAS -> view.insertIdempotent(transferId,
                  Objects.requireNonNull(resource.resolveGas(), "missing gas"), amount, baseline);
        };
    }

    @Nullable
    private static Map<PortableResourceDescriptor, BigInteger> capturePassiveBaselines(
          QIOPassiveOperation operation, IQIOStorageView view) {
        Map<PortableResourceDescriptor, BigInteger> baselines = new LinkedHashMap<>();
        for (Map.Entry<PortableResourceDescriptor, Long> input :
              operation.getRequiredInputs().entrySet()) {
            UUID uuid = operation.getResourceBindings().get(input.getKey());
            QIOStorageEntry entry = uuid == null ? null : view.getResource(uuid);
            if (entry == null || !input.getKey().equals(
                  PortableResourceDescriptor.fromStorageEntry(entry)) ||
                  entry.getExactStoredAmount().compareTo(BigInteger.valueOf(input.getValue())) < 0) {
                return null;
            }
            baselines.put(input.getKey(), entry.getExactStoredAmount());
        }
        return baselines;
    }

    @Nonnull
    private static Map<UUID, BigInteger> boundBaselines(QIOPassiveOperation operation) {
        Map<UUID, BigInteger> baselines = new LinkedHashMap<>();
        for (Map.Entry<PortableResourceDescriptor, BigInteger> baseline :
              operation.getConsumeBaselines().entrySet()) {
            UUID uuid = operation.getResourceBindings().get(baseline.getKey());
            if (uuid == null || baselines.put(uuid, baseline.getValue()) != null) {
                throw new IllegalStateException("Passive consume baseline binding is invalid");
            }
        }
        if (baselines.size() != operation.getRequiredInputs().size()) {
            throw new IllegalStateException("Passive consume baselines are incomplete");
        }
        return baselines;
    }

    @Nonnull
    private static BigInteger storedAmount(IQIOStorageView view,
          PortableResourceDescriptor resource) {
        BigInteger amount = BigInteger.ZERO;
        UUID matched = null;
        for (QIOStorageEntry entry : view.getSnapshot().getEntries()) {
            if (!resource.equals(PortableResourceDescriptor.fromStorageEntry(entry))) {
                continue;
            }
            if (matched != null && !matched.equals(entry.getResourceUUID())) {
                throw new IllegalStateException(
                      "QIO snapshot maps one portable resource to multiple UUIDs");
            }
            matched = entry.getResourceUUID();
            amount = entry.getExactStoredAmount();
        }
        return amount;
    }

    private static void persist(QIOProcessingNetworkData network) throws IOException {
        QIOProcessingNetworkManager.INSTANCE.checkpointNetwork(network.getFrequencyUUID());
    }

    private static final class ProviderWakeStamp {

        private final boolean recipeCatalogInitialized;
        private final long recipeCatalogRevision;
        private final long deviceRevision;
        private final long providerRevision;
        private final long providerAvailabilityRevision;
        private final long policyRevision;
        private final long profileRevision;
        private final long workbenchRevision;

        private ProviderWakeStamp(boolean recipeCatalogInitialized, long recipeCatalogRevision,
              long deviceRevision, long providerRevision, long providerAvailabilityRevision,
              long policyRevision, long profileRevision, long workbenchRevision) {
            this.recipeCatalogInitialized = recipeCatalogInitialized;
            this.recipeCatalogRevision = recipeCatalogRevision;
            this.deviceRevision = deviceRevision;
            this.providerRevision = providerRevision;
            this.providerAvailabilityRevision = providerAvailabilityRevision;
            this.policyRevision = policyRevision;
            this.profileRevision = profileRevision;
            this.workbenchRevision = workbenchRevision;
        }

        private static ProviderWakeStamp capture(QIOProcessingNetworkData network) {
            return new ProviderWakeStamp(QIORecipeCatalogService.INSTANCE.isInitialized(),
                  QIORecipeCatalogService.INSTANCE.getRevision(
                        network.getWorkbenchConfiguration()),
                  network.getAutomationDevices().getRevision(),
                  network.getProviderCatalog().getRevision(),
                  network.getProviderCatalog().getAvailabilityRevision(),
                  network.getPolicies().getRevision(),
                  network.getAutomationRecipeProfiles().getRevision(),
                  network.getWorkbenchConfiguration().getRevision());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProviderWakeStamp stamp)) {
                return false;
            }
            return recipeCatalogInitialized == stamp.recipeCatalogInitialized &&
                  recipeCatalogRevision == stamp.recipeCatalogRevision &&
                  deviceRevision == stamp.deviceRevision &&
                  providerRevision == stamp.providerRevision &&
                  providerAvailabilityRevision == stamp.providerAvailabilityRevision &&
                  policyRevision == stamp.policyRevision &&
                  profileRevision == stamp.profileRevision &&
                  workbenchRevision == stamp.workbenchRevision;
        }

        @Override
        public int hashCode() {
            return Objects.hash(recipeCatalogInitialized, recipeCatalogRevision, deviceRevision,
                  providerRevision, providerAvailabilityRevision, policyRevision,
                  profileRevision, workbenchRevision);
        }
    }

    private static final class ProviderCursorKey {

        private final UUID frequencyUUID;
        private final ProviderKind providerKind;
        private final String routeKey;
        private final long priority;

        private ProviderCursorKey(UUID frequencyUUID, ProviderKind providerKind,
              String routeKey, long priority) {
            this.frequencyUUID = Objects.requireNonNull(frequencyUUID, "frequencyUUID");
            this.providerKind = Objects.requireNonNull(providerKind, "providerKind");
            this.routeKey = Objects.requireNonNull(routeKey, "routeKey");
            this.priority = priority;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProviderCursorKey)) {
                return false;
            }
            ProviderCursorKey key = (ProviderCursorKey) other;
            return priority == key.priority && frequencyUUID.equals(key.frequencyUUID) &&
                  providerKind == key.providerKind && routeKey.equals(key.routeKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(frequencyUUID, providerKind, routeKey, priority);
        }
    }

    private static final class MachineEndpoint {

        private final LoadedDevice device;
        private final MachineRecipeProviderRegistry.BoundProvider provider;
        private final MachineRecipeRoute route;
        private final Map<String, MachinePort> ports;
        private final long laneId;
        private final List<MachinePortBaseline> baselines;

        private MachineEndpoint(LoadedDevice device,
              MachineRecipeProviderRegistry.BoundProvider provider, MachineRecipeRoute route,
              Map<String, MachinePort> ports, long laneId,
              List<MachinePortBaseline> baselines) {
            this.device = device;
            this.provider = provider;
            this.route = route;
            this.ports = ports;
            this.laneId = laneId;
            this.baselines = baselines;
        }

        private UUID operationId(QIODurableTransferRecord transfer) {
            String node = transfer.getNodeId();
            int separator = node.indexOf('/');
            if (separator < 0 || separator == node.length() - 1) {
                throw new IllegalArgumentException("Machine transfer has no operation identity");
            }
            return UUID.fromString(node.substring(separator + 1));
        }
    }
}
