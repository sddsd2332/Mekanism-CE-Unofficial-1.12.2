package mekanism.qioprocessing.common.order;

import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.QIOProcessingConfig;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.material.QIOMaterialClaimCoordinator;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import mekanism.qioprocessing.common.planning.QIOPlanningExecutor;
import mekanism.qioprocessing.common.planning.QIOPlanner;
import mekanism.qioprocessing.common.planning.QIOPlanningRequest;
import mekanism.qioprocessing.common.planning.QIOPlanningResult;
import mekanism.qioprocessing.common.planning.QIOPlanningService;
import mekanism.qioprocessing.common.planning.QIOPlanningSnapshot;
import mekanism.qioprocessing.common.planning.QIOPlanningSnapshotFactory;
import mekanism.qioprocessing.common.planning.QIOPlanningRoute;
import mekanism.qioprocessing.common.planning.QIOPlanningRouteClosure;
import mekanism.qioprocessing.common.planning.QIOProviderCatalog;
import mekanism.qioprocessing.common.planning.QIOProviderRouteCatalog;
import mekanism.qioprocessing.common.planning.QIORecipeCatalogService;
import mekanism.qioprocessing.common.planning.QIOWorkbenchRecipeCatalog;
import mekanism.qioprocessing.common.processor.QIOCraftingProcessorDeviceRegistry;
import mekanism.qioprocessing.common.processor.QIOCraftingProcessorDeviceRegistry.LoadedProcessor;
import mekanism.qioprocessing.common.machine.QIOAutomationDeviceRegistry;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Main-thread preview and formal order submission coordinator. */
public final class QIOOrderService {

    public static final QIOOrderService INSTANCE = new QIOOrderService();
    // Persisted plans contain exact workbench candidates. Bump this whenever candidate
    // selection semantics change so waiting jobs are safely replanned after an update.
    private static final long PLANNING_CONFIGURATION_ALGORITHM_REVISION = 3;
    private static final int MAX_PREVIEW_PLANNING_ATTEMPTS = 3;
    private static final int MAX_CACHED_ROUTE_CLOSURES = 16;
    private static final int MAX_CACHED_ROUTES_PER_CLOSURE = 16_384;
    private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();

    public enum RequestStatus {
        ACCEPTED,
        ACCESS_DENIED,
        NO_PROCESSOR_OR_PROVIDER,
        TOO_MANY_PREVIEWS,
        SERVICE_STOPPED
    }

    public enum ConfirmStatus {
        CREATED,
        NOT_FOUND,
        NOT_OWNER,
        NOT_READY,
        EXPIRED,
        STALE,
        ACCESS_DENIED,
        JOB_LIMIT,
        PERSISTENCE_ERROR
    }

    public enum CancelStatus {
        ACCEPTED,
        ALREADY_REQUESTED,
        NOT_FOUND,
        NOT_OWNER,
        ACCESS_DENIED,
        ALREADY_TERMINAL,
        PERSISTENCE_ERROR
    }

    public static final class PreviewRequestResult {

        private final RequestStatus status;
        @Nullable
        private final QIOOrderPreview preview;

        private PreviewRequestResult(RequestStatus status, @Nullable QIOOrderPreview preview) {
            this.status = status;
            this.preview = preview;
        }

        @Nonnull
        public RequestStatus getStatus() {
            return status;
        }

        @Nullable
        public QIOOrderPreview getPreview() {
            return preview;
        }
    }

    public static final class ConfirmResult {

        private final ConfirmStatus status;
        @Nullable
        private final QIOCraftingJob job;

        private ConfirmResult(ConfirmStatus status, @Nullable QIOCraftingJob job) {
            this.status = status;
            this.job = job;
        }

        @Nonnull
        public ConfirmStatus getStatus() {
            return status;
        }

        @Nullable
        public QIOCraftingJob getJob() {
            return job;
        }
    }

    /** Immutable main-thread capture ready to be handed to the bounded planning pool. */
    public static final class PlanningPreparation {

        private final RequestStatus status;
        @Nullable
        private final QIOPlanningSnapshot snapshot;

        private PlanningPreparation(RequestStatus status, @Nullable QIOPlanningSnapshot snapshot) {
            this.status = Objects.requireNonNull(status, "status");
            this.snapshot = snapshot;
        }

        @Nonnull
        public RequestStatus getStatus() {
            return status;
        }

        @Nullable
        public QIOPlanningSnapshot getSnapshot() {
            return snapshot;
        }
    }

    /** Main-thread-only capture result; accepted captures contain worker-safe immutable data. */
    public static final class PlanningCaptureResult {

        private final RequestStatus status;
        @Nullable private final PlanningCapture capture;

        private PlanningCaptureResult(RequestStatus status, @Nullable PlanningCapture capture) {
            this.status = Objects.requireNonNull(status, "status");
            this.capture = capture;
        }

        @Nonnull public RequestStatus getStatus() { return status; }
        @Nullable public PlanningCapture getCapture() { return capture; }
    }

    /** Pure-data source snapshot from which dependency closure and planning can run off-thread. */
    public static final class PlanningCapture {

        private final int dimensionId;
        private final UUID frequencyUUID;
        private final PortableResourceDescriptor target;
        private final Map<PortableResourceDescriptor, Long> available;
        private final QIOWorkbenchRecipeCatalog.Snapshot workbench;
        private final QIOPlanningRouteClosure.ProviderIndex providerIndex;
        private final Map<ResourceLocation, Long> workbenchPriorities;
        private final Set<PortableResourceDescriptor> schedulableOutputs;
        private final QIOWorkbenchConfiguration workbenchConfiguration;
        private final QIOPlanSourceRevisions sourceRevisions;

        private PlanningCapture(int dimensionId, UUID frequencyUUID,
              PortableResourceDescriptor target,
              Map<PortableResourceDescriptor, Long> available,
              QIOWorkbenchRecipeCatalog.Snapshot workbench,
              QIOPlanningRouteClosure.ProviderIndex providerIndex,
              Map<ResourceLocation, Long> workbenchPriorities,
              Set<PortableResourceDescriptor> schedulableOutputs,
              QIOWorkbenchConfiguration workbenchConfiguration,
              QIOPlanSourceRevisions sourceRevisions) {
            this.dimensionId = dimensionId;
            this.frequencyUUID = Objects.requireNonNull(frequencyUUID, "frequencyUUID");
            this.target = Objects.requireNonNull(target, "target");
            this.available = Collections.unmodifiableMap(new LinkedHashMap<>(available));
            this.workbench = Objects.requireNonNull(workbench, "workbench");
            this.providerIndex = Objects.requireNonNull(providerIndex, "providerIndex");
            this.workbenchPriorities = Collections.unmodifiableMap(new LinkedHashMap<>(
                  workbenchPriorities));
            this.schedulableOutputs = Collections.unmodifiableSet(new LinkedHashSet<>(
                  schedulableOutputs));
            this.workbenchConfiguration = Objects.requireNonNull(workbenchConfiguration,
                  "workbenchConfiguration");
            this.sourceRevisions = Objects.requireNonNull(sourceRevisions, "sourceRevisions");
        }

        public int getDimensionId() { return dimensionId; }
        @Nonnull public PortableResourceDescriptor getTarget() { return target; }
        @Nonnull public QIOPlanSourceRevisions getSourceRevisions() { return sourceRevisions; }
    }

    private static final class PreviewPlanningOutcome {

        private final QIOPlanningResult result;
        private final long routePreparationNanos;
        private final long planningNanos;

        private PreviewPlanningOutcome(QIOPlanningResult result,
              long routePreparationNanos, long planningNanos) {
            this.result = Objects.requireNonNull(result, "result");
            this.routePreparationNanos = Math.max(0, routePreparationNanos);
            this.planningNanos = Math.max(0, planningNanos);
        }
    }

    /** Cached lightweight projection used by the smart-processing resource directory. */
    public static final class SchedulableResources {

        private final Set<PortableResourceDescriptor> resources;
        private final long revision;

        private SchedulableResources(Set<PortableResourceDescriptor> resources, long revision) {
            this.resources = resources;
            this.revision = revision;
        }

        @Nonnull
        public Set<PortableResourceDescriptor> getResources() {
            return resources;
        }

        public long getRevision() {
            return revision;
        }
    }

    private final Map<UUID, QIOOrderPreview> previews = new LinkedHashMap<>();
    private final Map<UUID, EffectiveRouteView> effectiveRouteViews = new LinkedHashMap<>();
    private final Map<UUID, Long> observedProviderDeviceRevisions = new LinkedHashMap<>();
    private final Map<RouteClosureKey, List<QIOPlanningRoute>> routeClosures =
          new LinkedHashMap<>(32, 0.75F, true) {
              @Override
              protected boolean removeEldestEntry(
                    Map.Entry<RouteClosureKey, List<QIOPlanningRoute>> eldest) {
                  return size() > MAX_CACHED_ROUTE_CLOSURES;
              }
          };

    private QIOOrderService() {
    }

    @Nonnull
    public PreviewRequestResult requestPreview(@Nonnull World world,
          @Nonnull QIOFrequencyReference frequency, @Nonnull UUID requester,
          @Nonnull PortableResourceDescriptor target, long amount, long priority,
          long currentTick) {
        return requestPreview(world, frequency, requester, target, amount, priority,
              false, currentTick);
    }

    @Nonnull
    public PreviewRequestResult requestPreview(@Nonnull World world,
          @Nonnull QIOFrequencyReference frequency, @Nonnull UUID requester,
          @Nonnull PortableResourceDescriptor target, long amount, long priority,
          boolean mergeRequested, long currentTick) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
        if (world.isRemote || amount <= 0 || currentTick < 0) {
            throw new IllegalArgumentException("Invalid QIO preview request");
        }
        long requestStartedNanos = System.nanoTime();
        QIOProcessingConfig config = MekanismConfig.current().qioProcessing;
        synchronized (this) {
            expire(currentTick);
            if (ownedPreviewCount(requester) >= config.previewsPerPlayer.val()) {
                return new PreviewRequestResult(RequestStatus.TOO_MANY_PREVIEWS, null);
            }
        }

        IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(frequency, requester);
        if (view == null) {
            return new PreviewRequestResult(RequestStatus.ACCESS_DENIED, null);
        }
        try {
            QIOProcessingNetworkData network = network(frequency);
            PlanningCaptureResult captured = capturePlanning(world, network,
                  view.getSnapshot(), target);
            if (captured.getStatus() != RequestStatus.ACCEPTED ||
                captured.getCapture() == null) {
                return new PreviewRequestResult(captured.getStatus(), null);
            }
            UUID previewId = UUID.randomUUID();
            long expiresAt = saturatedAdd(currentTick, config.previewTimeoutTicks.val());
            MergeResolution merge = resolveMerge(network, requester, target, priority,
                  mergeRequested);
            long mainThreadPreparationNanos = Math.max(0,
                  System.nanoTime() - requestStartedNanos);
            QIOOrderPreview preview = new QIOOrderPreview(previewId, requester, frequency,
                  target, amount, merge.priority, currentTick, expiresAt, merge.mergeOrder,
                  requestStartedNanos, mainThreadPreparationNanos);
            synchronized (this) {
                if (ownedPreviewCount(requester) >= config.previewsPerPlayer.val()) {
                    return new PreviewRequestResult(RequestStatus.TOO_MANY_PREVIEWS, null);
                }
                previews.put(previewId, preview);
            }
            if (!submitPreview(preview, captured.getCapture())) {
                synchronized (this) {
                    previews.remove(previewId);
                }
                preview.cancel();
                return new PreviewRequestResult(RequestStatus.SERVICE_STOPPED, null);
            }
            return new PreviewRequestResult(RequestStatus.ACCEPTED, preview);
        } finally {
            view.close();
        }
    }

    private long ownedPreviewCount(UUID requester) {
        return previews.values().stream().filter(preview ->
              requester.equals(preview.getRequester()) && isLive(preview.getState())).count();
    }

    private boolean submitPreview(QIOOrderPreview preview, PlanningCapture capture) {
        QIOPlanningExecutor.Submission submission;
        try {
            submission = QIOPlanningService.INSTANCE.submitTask(capture,
                  (input, cancellation) -> {
                      long routeStarted = workerCpuTime();
                      PlanningPreparation preparation = prepareCaptured(input,
                            cancellation::isCancelled);
                      long routeNanos = Math.max(0, workerCpuTime() - routeStarted);
                      preview.beginPlanning();
                      if (preparation.getSnapshot() == null) {
                          return new PreviewPlanningOutcome(QIOPlanningResult.failure(
                                QIOPlanningResult.Status.NO_ROUTE,
                                preparation.getStatus().name(), 0, 0), routeNanos, 0);
                      }
                      QIOPlanningRequest request = new QIOPlanningRequest(
                            preview.getPreviewId(), 1, preparation.getSnapshot(),
                            preview.getTarget(), preview.getAmount());
                      long planningStarted = workerCpuTime();
                      QIOPlanningResult result = QIOPlanner.INSTANCE.plan(request, cancellation);
                      long planningNanos = Math.max(0,
                            workerCpuTime() - planningStarted);
                      return new PreviewPlanningOutcome(result, routeNanos, planningNanos);
                  }, completed -> complete(preview.getPreviewId(), capture, completed));
        } catch (IllegalStateException e) {
            return false;
        }
        if (!submission.isAccepted()) {
            return false;
        }
        preview.bindTask(submission.getTaskId());
        return true;
    }

    static MergeResolution resolveMerge(@Nonnull QIOProcessingNetworkData network,
          @Nonnull UUID requester, @Nonnull PortableResourceDescriptor target,
          long requestedPriority, boolean mergeRequested) {
        if (!mergeRequested) return new MergeResolution(requestedPriority, false);
        boolean found = false;
        long inheritedPriority = Long.MIN_VALUE;
        for (QIOCraftingJob job : network.getJobs()) {
            if (job.getState().isTerminal() || job.getSource() != QIOCraftingJobSource.MANUAL ||
                !requester.equals(job.getRequester()) ||
                !target.equals(job.getActivePlan().getRootResource())) {
                continue;
            }
            found = true;
            inheritedPriority = Math.max(inheritedPriority, job.getBasePriority());
        }
        return new MergeResolution(found ? inheritedPriority : requestedPriority, found);
    }

    static final class MergeResolution {
        private final long priority;
        private final boolean mergeOrder;

        private MergeResolution(long priority, boolean mergeOrder) {
            this.priority = priority;
            this.mergeOrder = mergeOrder;
        }

        long priority() {
            return priority;
        }

        boolean mergeOrder() {
            return mergeOrder;
        }
    }

    @Nonnull
    public synchronized ConfirmResult confirm(@Nonnull UUID previewId,
          @Nonnull UUID requester, long currentTick) {
        QIOOrderPreview preview = previews.get(Objects.requireNonNull(previewId, "previewId"));
        if (preview == null) {
            return new ConfirmResult(ConfirmStatus.NOT_FOUND, null);
        }
        if (!preview.getRequester().equals(Objects.requireNonNull(requester, "requester"))) {
            return new ConfirmResult(ConfirmStatus.NOT_OWNER, null);
        }
        if (preview.expire(currentTick)) {
            cancelWorker(preview);
            return new ConfirmResult(ConfirmStatus.EXPIRED, null);
        }
        if (preview.getState() != QIOOrderPreview.State.READY || preview.getResult() == null ||
              preview.getResult().getPlan() == null) {
            return new ConfirmResult(ConfirmStatus.NOT_READY, null);
        }
        IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(preview.getFrequency(),
              requester);
        if (view == null) {
            return new ConfirmResult(ConfirmStatus.ACCESS_DENIED, null);
        }
        try {
            QIOProcessingNetworkData network = network(preview.getFrequency());
            QIOCraftPlan plan = preview.getResult().getPlan();
            QIOCraftingJob job = network.getJob(previewId);
            if (job != null) {
                if (job.getSource() != QIOCraftingJobSource.MANUAL ||
                      !requester.equals(job.getRequester()) ||
                      !job.getActivePlan().getPlanId().equals(plan.getPlanId())) {
                    return new ConfirmResult(ConfirmStatus.PERSISTENCE_ERROR, null);
                }
                try {
                    QIOProcessingNetworkManager.INSTANCE.persistenceBarrier().persist(network);
                    if (!job.getState().isTerminal()) {
                        QIOMaterialClaimCoordinator.refresh(network, job.getJobId(), view,
                              QIOProcessingNetworkManager.INSTANCE.persistenceBarrier());
                    }
                } catch (IOException | RuntimeException e) {
                    return new ConfirmResult(ConfirmStatus.PERSISTENCE_ERROR, job);
                }
                preview.confirm();
                previews.remove(previewId);
                return new ConfirmResult(ConfirmStatus.CREATED, job);
            }
            QIOProviderCatalog providers = network.getProviderCatalog();
            if (!matchesCurrentSources(plan.getSourceRevisions(), view,
                  providerSourceRevision(providers),
                  network.getTaskCommitmentRevision())) {
                previews.remove(previewId);
                preview.cancel();
                return new ConfirmResult(ConfirmStatus.STALE, null);
            }
            try {
                job = network.createJob(previewId, QIOCraftingJobSource.MANUAL, requester,
                      preview.getPriority(), currentTick, plan,
                      MekanismConfig.current().qioProcessing.nonTerminalJobsPerFrequency.val());
            } catch (IllegalStateException e) {
                return new ConfirmResult(ConfirmStatus.JOB_LIMIT, null);
            }
            try {
                QIOProcessingNetworkManager.INSTANCE.persistenceBarrier().persist(network);
                QIOMaterialClaimCoordinator.refresh(network, job.getJobId(), view,
                      QIOProcessingNetworkManager.INSTANCE.persistenceBarrier());
            } catch (IOException | RuntimeException e) {
                return new ConfirmResult(ConfirmStatus.PERSISTENCE_ERROR, job);
            }
            preview.confirm();
            previews.remove(previewId);
            return new ConfirmResult(ConfirmStatus.CREATED, job);
        } finally {
            view.close();
        }
    }

    @Nullable
    public synchronized QIOOrderPreview getPreview(@Nonnull UUID previewId,
          @Nonnull UUID requester) {
        QIOOrderPreview preview = previews.get(Objects.requireNonNull(previewId, "previewId"));
        return preview != null && preview.getRequester().equals(requester) ? preview : null;
    }

    @Nonnull
    public synchronized Collection<QIOOrderPreview> getPreviews(@Nonnull UUID requester) {
        List<QIOOrderPreview> result = new ArrayList<>();
        for (QIOOrderPreview preview : previews.values()) {
            if (preview.getRequester().equals(requester)) {
                result.add(preview);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized boolean cancelPreview(@Nonnull UUID previewId,
          @Nonnull UUID requester) {
        QIOOrderPreview preview = previews.get(Objects.requireNonNull(previewId, "previewId"));
        if (preview == null || !preview.getRequester().equals(requester) || !preview.cancel()) {
            return false;
        }
        cancelWorker(preview);
        previews.remove(previewId);
        return true;
    }

    @Nonnull
    public synchronized CancelStatus cancelOrder(@Nonnull QIOFrequencyReference frequency,
          @Nonnull UUID jobId, @Nonnull UUID requester) {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(requester, "requester");
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              frequency.getFrequencyUUID());
        QIOCraftingJob job = network == null ? null : network.getJob(jobId);
        if (job == null) {
            return CancelStatus.NOT_FOUND;
        }
        if (!requester.equals(job.getRequester())) {
            return CancelStatus.NOT_OWNER;
        }
        if (job.getState().isTerminal()) {
            return CancelStatus.ALREADY_TERMINAL;
        }
        if (job.isCancellationRequested()) {
            return CancelStatus.ALREADY_REQUESTED;
        }
        IQIOStorageView view = QIOFrequencyStorageAccess.INSTANCE.open(frequency, requester);
        if (view == null) {
            return CancelStatus.ACCESS_DENIED;
        }
        view.close();
        network.requestJobCancellation(jobId);
        QIOProcessingExecutionService.INSTANCE.wakeJob(jobId);
        try {
            QIOProcessingNetworkManager.INSTANCE.persistenceBarrier().persist(network);
            return CancelStatus.ACCEPTED;
        } catch (IOException e) {
            return CancelStatus.PERSISTENCE_ERROR;
        }
    }

    public synchronized void tick(long currentTick) {
        expire(currentTick);
    }

    public synchronized void clear() {
        for (QIOOrderPreview preview : previews.values()) {
            cancelWorker(preview);
            preview.cancel();
        }
        previews.clear();
        effectiveRouteViews.clear();
        observedProviderDeviceRevisions.clear();
        routeClosures.clear();
    }

    /** Builds the same route and storage snapshot used by manual previews without creating one. */
    @Nonnull
    public PlanningPreparation preparePlanning(@Nonnull World world,
          @Nonnull QIOProcessingNetworkData network, @Nonnull QIOStorageSnapshot storage,
          @Nonnull PortableResourceDescriptor target) {
        PlanningCaptureResult captured = capturePlanning(world, network, storage, target);
        return captured.getCapture() == null ? new PlanningPreparation(captured.getStatus(), null) :
              prepareCaptured(captured.getCapture(), () -> false);
    }

    /** Captures live server state without traversing the target dependency graph. */
    @Nonnull
    public synchronized PlanningCaptureResult capturePlanning(@Nonnull World world,
          @Nonnull QIOProcessingNetworkData network, @Nonnull QIOStorageSnapshot storage,
          @Nonnull PortableResourceDescriptor target) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(target, "target");
        if (world.isRemote || !network.getFrequencyUUID().equals(storage.getFrequencyUUID())) {
            throw new IllegalArgumentException("Invalid QIO planning capture");
        }
        if (!QIORecipeCatalogService.INSTANCE.isInitialized()) {
            QIORecipeCatalogService.INSTANCE.refresh(world);
        }
        QIOWorkbenchConfiguration workbenchConfiguration =
              network.getWorkbenchConfiguration().copy();
        QIORecipeCatalogService.State recipeState = QIORecipeCatalogService.INSTANCE.getState(
              workbenchConfiguration);
        QIOProviderCatalog providerState = observeProviders(network);
        List<LoadedProcessor> processors = QIOCraftingProcessorDeviceRegistry.INSTANCE
              .getAvailable(network.getFrequencyUUID());
        EffectiveRouteView routeView = effectiveRouteView(network, providerState,
              recipeState, processors);
        Map<PortableResourceDescriptor, Long> available =
              QIOPlanningSnapshotFactory.captureAvailableResources(storage);
        if (!routeView.schedulableOutputs.contains(target)) {
            return new PlanningCaptureResult(RequestStatus.NO_PROCESSOR_OR_PROVIDER, null);
        }
        QIOPlanSourceRevisions revisions = new QIOPlanSourceRevisions(
              storage.getContentsRevision(), storage.getCapacityRevision(),
              storage.getClaimRevision(), storage.getAccessRevision(),
              recipeState.getRevision(), providerSourceRevision(providerState),
              configurationRevision(network), network.getTaskCommitmentRevision());
        PlanningCapture capture = new PlanningCapture(world.provider.getDimension(),
              network.getFrequencyUUID(), target, available, recipeState.getSnapshot(),
              routeView.providerIndex,
              routeView.workbenchPriorities, routeView.schedulableOutputs,
              workbenchConfiguration, revisions);
        return new PlanningCaptureResult(RequestStatus.ACCEPTED, capture);
    }

    /** Performs route closure using only immutable data captured on the server thread. */
    @Nonnull
    public PlanningPreparation prepareCaptured(@Nonnull PlanningCapture capture,
          @Nonnull BooleanSupplier cancelled) {
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(cancelled, "cancelled");
        RouteClosureKey cacheKey = new RouteClosureKey(capture.frequencyUUID,
              capture.target, capture.sourceRevisions);
        List<QIOPlanningRoute> routes;
        synchronized (this) {
            routes = routeClosures.get(cacheKey);
        }
        if (routes == null) {
            routes = QIOPlanningRouteClosure.collect(capture.target, capture.available,
                  capture.providerIndex, capture.workbench, capture.workbenchPriorities,
                  capture.schedulableOutputs, Integer.MAX_VALUE,
                  capture.workbenchConfiguration, cancelled);
            if (routes.size() <= MAX_CACHED_ROUTES_PER_CLOSURE) {
                synchronized (this) {
                    routeClosures.put(cacheKey, routes);
                }
            }
        } else if (cancelled.getAsBoolean()) {
            throw new java.util.concurrent.CancellationException(
                  "QIO route closure cancelled");
        }
        if (routes.isEmpty()) {
            return new PlanningPreparation(RequestStatus.NO_PROCESSOR_OR_PROVIDER, null);
        }
        QIOPlanningSnapshot snapshot = QIOPlanningSnapshotFactory.create(
              capture.sourceRevisions, capture.available, routes);
        return new PlanningPreparation(RequestStatus.ACCEPTED, snapshot);
    }

    @Nonnull
    public synchronized SchedulableResources getSchedulableResources(@Nonnull World world,
          @Nonnull QIOProcessingNetworkData network) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(network, "network");
        if (world.isRemote) {
            throw new IllegalArgumentException("QIO schedulable resources require a server world");
        }
        if (!QIORecipeCatalogService.INSTANCE.isInitialized()) {
            QIORecipeCatalogService.INSTANCE.refresh(world);
        }
        QIORecipeCatalogService.State recipeState = QIORecipeCatalogService.INSTANCE.getState(
              network.getWorkbenchConfiguration());
        QIOProviderCatalog providerState = observeProviders(network);
        List<LoadedProcessor> processors = QIOCraftingProcessorDeviceRegistry.INSTANCE
              .getAvailable(network.getFrequencyUUID());
        EffectiveRouteView view = effectiveRouteView(network, providerState, recipeState,
              processors);
        return new SchedulableResources(view.schedulableOutputs, view.revision);
    }

    /** Revalidates route/security inputs while allowing storage quantities to be claim-resolved. */
    public synchronized boolean isAutomatedPlanCurrent(@Nonnull World world,
          @Nonnull QIOProcessingNetworkData network, @Nonnull QIOStorageSnapshot storage,
          @Nonnull QIOPlanSourceRevisions revisions) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(revisions, "revisions");
        if (world.isRemote || !network.getFrequencyUUID().equals(storage.getFrequencyUUID())) {
            return false;
        }
        if (!QIORecipeCatalogService.INSTANCE.isInitialized()) {
            QIORecipeCatalogService.INSTANCE.refresh(world);
        }
        QIOProviderCatalog providers = observeProviders(network);
        return revisions.getAccessRevision() == storage.getAccessRevision() &&
              revisions.getRecipeCatalogRevision() == QIORecipeCatalogService.INSTANCE
                    .getRevision(network.getWorkbenchConfiguration()) &&
              revisions.getProviderCatalogRevision() == providerSourceRevision(providers) &&
              revisions.getPolicyRevision() == configurationRevision(network);
    }

    private synchronized void complete(UUID previewId, PlanningCapture capture,
          QIOPlanningExecutor.PlanningResult<PreviewPlanningOutcome> completed) {
        QIOOrderPreview preview = previews.get(previewId);
        if (preview == null || (preview.getState() != QIOOrderPreview.State.PREPARING &&
              preview.getState() != QIOOrderPreview.State.PLANNING) ||
              !completed.getTaskId().equals(preview.getPlanningTaskId())) {
            return;
        }
        PreviewPlanningOutcome outcome = completed.getStatus() ==
              QIOPlanningExecutor.ResultStatus.SUCCESS ? completed.getValue() : null;
        long routePreparationNanos = outcome == null ? 0 : outcome.routePreparationNanos;
        long planningNanos = outcome == null ? 0 : outcome.planningNanos;

        IQIOStorageView currentView = QIOFrequencyStorageAccess.INSTANCE.open(
              preview.getFrequency(), preview.getRequester());
        QIOProcessingNetworkData currentNetwork = currentView == null ? null :
              network(preview.getFrequency());
        boolean currentSources = false;
        try {
            if (currentView != null && currentView.isValid() && currentNetwork != null) {
                QIOProviderCatalog providers = observeProviders(currentNetwork);
                currentSources = matchesCurrentSources(capture.sourceRevisions, currentView,
                      providerSourceRevision(providers),
                      currentNetwork.getTaskCommitmentRevision());
            }
            if (!currentSources && currentView != null && currentView.isValid() &&
                currentNetwork != null && preview.getPlanningAttempt() <
                      MAX_PREVIEW_PLANNING_ATTEMPTS) {
                World world = DimensionManager.getWorld(capture.dimensionId);
                if (world != null && !world.isRemote) {
                    long recaptureStarted = System.nanoTime();
                    PlanningCaptureResult refreshed = capturePlanning(world, currentNetwork,
                          currentView.getSnapshot(), preview.getTarget());
                    long recaptureNanos = Math.max(0,
                          System.nanoTime() - recaptureStarted);
                    if (refreshed.getCapture() != null && preview.retry(recaptureNanos,
                          routePreparationNanos, planningNanos,
                          completed.getElapsedNanos())) {
                        if (submitPreview(preview, refreshed.getCapture())) {
                            return;
                        }
                        preview.complete(QIOPlanningResult.failure(
                              QIOPlanningResult.Status.INTERNAL_ERROR,
                              "QIO planning service stopped during retry", 0, 0), 0, 0, 0);
                        return;
                    }
                }
            }
        } finally {
            if (currentView != null) currentView.close();
        }

        QIOPlanningResult result;
        if (!currentSources) {
            result = QIOPlanningResult.failure(QIOPlanningResult.Status.INTERNAL_ERROR,
                  "QIO planning inputs changed repeatedly before publication", 0, 0);
        } else if (outcome != null) {
            result = outcome.result;
        } else {
            QIOPlanningResult.Status status = switch (completed.getStatus()) {
                case CANCELLED -> QIOPlanningResult.Status.CANCELLED;
                case TIMED_OUT -> QIOPlanningResult.Status.TOO_COMPLEX;
                default -> QIOPlanningResult.Status.INTERNAL_ERROR;
            };
            String diagnostic = completed.getError() == null ? completed.getStatus().name() :
                  completed.getError().getMessage();
            result = QIOPlanningResult.failure(status, diagnostic, 0, 0);
        }
        preview.complete(result, routePreparationNanos, planningNanos,
              completed.getElapsedNanos());
    }

    private void expire(long currentTick) {
        Iterator<Map.Entry<UUID, QIOOrderPreview>> iterator = previews.entrySet().iterator();
        while (iterator.hasNext()) {
            QIOOrderPreview preview = iterator.next().getValue();
            if (preview.expire(currentTick)) {
                cancelWorker(preview);
                iterator.remove();
            }
        }
    }

    private static boolean matchesCurrentSources(QIOPlanSourceRevisions revisions,
          IQIOStorageView view, long providerRevision, long taskRevision) {
        return revisions.getContentRevision() == view.getContentsRevision() &&
              revisions.getCapacityRevision() == view.getCapacityRevision() &&
              revisions.getClaimRevision() == view.getClaimRevision() &&
              revisions.getAccessRevision() == view.getAccessRevision() &&
              revisions.getRecipeCatalogRevision() == networkRecipeRevision(
                    view.getFrequencyUUID()) &&
              revisions.getProviderCatalogRevision() == providerRevision &&
              revisions.getPolicyRevision() == networkPolicyRevision(view.getFrequencyUUID()) &&
              revisions.getTaskCommitmentRevision() == taskRevision;
    }

    private static QIOProcessingNetworkData network(QIOFrequencyReference frequency) {
        return QIOProcessingNetworkManager.INSTANCE.getOrCreate(frequency.getFrequencyUUID(),
              new QIOFrequencyIdentitySnapshot(frequency.getFrequencyName(),
                    frequency.getOwnerUUID(), frequency.getSecurityMode()));
    }

    private static boolean isLive(QIOOrderPreview.State state) {
        return state == QIOOrderPreview.State.PREPARING ||
              state == QIOOrderPreview.State.PLANNING || state == QIOOrderPreview.State.READY ||
              state == QIOOrderPreview.State.FAILED;
    }

    private static void cancelWorker(QIOOrderPreview preview) {
        UUID taskId = preview.getPlanningTaskId();
        QIOPlanningExecutor executor = QIOPlanningService.INSTANCE.getExecutor();
        if (taskId != null && executor != null) {
            executor.cancel(taskId);
        }
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static long workerCpuTime() {
        if (THREAD_MX_BEAN.isCurrentThreadCpuTimeSupported()) {
            try {
                long value = THREAD_MX_BEAN.getCurrentThreadCpuTime();
                if (value >= 0) return value;
            } catch (UnsupportedOperationException | SecurityException ignored) {
            }
        }
        return System.nanoTime();
    }

    private static long networkPolicyRevision(UUID frequencyUUID) {
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(frequencyUUID);
        return network == null ? -1 : configurationRevision(network);
    }

    private static long networkRecipeRevision(UUID frequencyUUID) {
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(frequencyUUID);
        return network == null ? -1 : QIORecipeCatalogService.INSTANCE.getRevision(
              network.getWorkbenchConfiguration());
    }

    private static long configurationRevision(QIOProcessingNetworkData network) {
        long configured = mixRevision(mixRevision(network.getPolicies().getRevision(),
              network.getAutomationRecipeProfiles().getRevision()),
              network.getWorkbenchConfiguration().getRevision());
        return mixRevision(configured, PLANNING_CONFIGURATION_ALGORITHM_REVISION) &
              Long.MAX_VALUE;
    }

    @Nonnull
    private QIOProviderCatalog observeProviders(QIOProcessingNetworkData network) {
        long deviceRevision = network.getAutomationDevices().getRevision();
        Long observedRevision = observedProviderDeviceRevisions.get(network.getFrequencyUUID());
        if (observedRevision != null && observedRevision == deviceRevision) {
            return network.getProviderCatalog();
        }
        QIOProviderRouteCatalog.Snapshot onlineProviders = QIOProviderRouteCatalog.capture(
              network.getFrequencyUUID(), QIOAutomationDeviceRegistry.INSTANCE,
              (provider, route, recipe) -> 0);
        network.observeProviderCatalog(onlineProviders,
              MekanismConfig.current().qioProcessing.providerRoutesPerFrequency.val());
        observedProviderDeviceRevisions.put(network.getFrequencyUUID(),
              network.getAutomationDevices().getRevision());
        return network.getProviderCatalog();
    }

    @Nonnull
    private EffectiveRouteView effectiveRouteView(QIOProcessingNetworkData network,
          QIOProviderCatalog providerState, QIORecipeCatalogService.State recipeState,
          List<LoadedProcessor> processors) {
        List<UUID> processorIds = new ArrayList<>(processors.size());
        for (LoadedProcessor processor : processors) {
            processorIds.add(processor.processorUUID());
        }
        EffectiveRouteView current = effectiveRouteViews.get(network.getFrequencyUUID());
        long policyRevision = configurationRevision(network);
        long deviceRevision = network.getAutomationDevices().getRevision();
        if (current != null && current.matches(recipeState.getRevision(),
              providerState.getRevision(), providerState.getAvailabilityRevision(),
              policyRevision, deviceRevision, processorIds)) {
            return current;
        }

        List<QIOPlanningRoute> providerRoutes = providerState.getRoutes(network.getPolicies(),
              network.getAutomationRecipeProfiles());
        QIOPlanningRouteClosure.ProviderIndex providerIndex =
              QIOPlanningRouteClosure.indexProviders(providerRoutes);
        Map<ResourceLocation, Long> workbenchPriorities = new LinkedHashMap<>();
        QIOWorkbenchRecipeCatalog.Snapshot workbench = recipeState.getSnapshot();
        for (PortableResourceDescriptor output : workbench.getOrderedOutputs()) {
            List<ResourceLocation> recipeIds = workbench.getRecipeIds(output);
            List<String> defaultOrder = new ArrayList<>(recipeIds.size());
            recipeIds.forEach(recipeId -> defaultOrder.add(recipeId.toString()));
            String productKey = QIOWorkbenchRecipeCatalog.productKey(output);
            List<String> ordered = network.getWorkbenchConfiguration().orderedRecipes(
                  productKey, defaultOrder);
            for (int index = 0; index < ordered.size(); index++) {
                ResourceLocation recipeId;
                try {
                    recipeId = new ResourceLocation(ordered.get(index));
                } catch (RuntimeException ignored) {
                    continue;
                }
                QIOWorkbenchRecipeCatalog.RecipeDefinition definition =
                      workbench.getRecipeDefinition(recipeId);
                if (definition != null && network.getWorkbenchConfiguration()
                      .isRecipeEnabled(recipeId.toString(), definition.getSignature()) &&
                    workbenchRecipeAvailableOnAnyProcessor(network, processors, recipeId)) {
                    workbenchPriorities.put(recipeId, Long.MAX_VALUE - index);
                }
            }
        }
        Set<PortableResourceDescriptor> schedulable = new LinkedHashSet<>(
              providerIndex.getOutputs());
        schedulable.addAll(recipeState.getSnapshot().getDeclaredOutputs(
              workbenchPriorities.keySet()));
        List<PortableResourceDescriptor> orderedOutputs = new ArrayList<>(schedulable);
        Collections.sort(orderedOutputs);
        Set<PortableResourceDescriptor> immutableOutputs = Collections.unmodifiableSet(
              new LinkedHashSet<>(orderedOutputs));
        long revision = routeViewRevision(recipeState.getRevision(), providerState,
              policyRevision, deviceRevision, processorIds, immutableOutputs);
        EffectiveRouteView next = new EffectiveRouteView(recipeState.getRevision(),
              providerState.getRevision(), providerState.getAvailabilityRevision(),
              policyRevision, deviceRevision, processorIds, providerIndex,
              workbenchPriorities, immutableOutputs, revision);
        effectiveRouteViews.put(network.getFrequencyUUID(), next);
        return next;
    }

    @Nullable
    private static boolean workbenchRecipeAvailableOnAnyProcessor(
          QIOProcessingNetworkData network,
          List<LoadedProcessor> processors, ResourceLocation recipeId) {
        String id = recipeId.toString();
        for (LoadedProcessor processor : processors) {
            if (network.getPolicies().isRouteEnabled(processor.processorUUID(),
                  QIOWorkbenchRecipeCatalog.PROVIDER_ID.toString(), id, id)) {
                return true;
            }
        }
        return false;
    }

    private static long providerSourceRevision(QIOProviderCatalog providers) {
        return mixRevision(providers.getRevision(), providers.getAvailabilityRevision()) &
              Long.MAX_VALUE;
    }

    private static long routeViewRevision(long recipeRevision, QIOProviderCatalog providers,
          long policyRevision, long deviceRevision, List<UUID> processorIds,
          Set<PortableResourceDescriptor> outputs) {
        long value = mixRevision(recipeRevision, providers.getRevision());
        value = mixRevision(value, providers.getAvailabilityRevision());
        value = mixRevision(value, policyRevision);
        value = mixRevision(value, deviceRevision);
        for (UUID processorId : processorIds) value = mixRevision(value, processorId.hashCode());
        for (PortableResourceDescriptor output : outputs) value = mixRevision(value,
              output.hashCode());
        return value & Long.MAX_VALUE;
    }

    private static long mixRevision(long value, long next) {
        return (value ^ next) * 0x9E3779B97F4A7C15L + 0xBF58476D1CE4E5B9L;
    }

    private static final class RouteClosureKey {

        private final UUID frequencyUUID;
        private final PortableResourceDescriptor target;
        private final long contentRevision;
        private final long capacityRevision;
        private final long claimRevision;
        private final long accessRevision;
        private final long recipeRevision;
        private final long providerRevision;
        private final long policyRevision;
        private final long taskRevision;

        private RouteClosureKey(UUID frequencyUUID, PortableResourceDescriptor target,
              QIOPlanSourceRevisions revisions) {
            this.frequencyUUID = frequencyUUID;
            this.target = target;
            contentRevision = revisions.getContentRevision();
            capacityRevision = revisions.getCapacityRevision();
            claimRevision = revisions.getClaimRevision();
            accessRevision = revisions.getAccessRevision();
            recipeRevision = revisions.getRecipeCatalogRevision();
            providerRevision = revisions.getProviderCatalogRevision();
            policyRevision = revisions.getPolicyRevision();
            taskRevision = revisions.getTaskCommitmentRevision();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof RouteClosureKey key)) return false;
            return contentRevision == key.contentRevision &&
                  capacityRevision == key.capacityRevision &&
                  claimRevision == key.claimRevision && accessRevision == key.accessRevision &&
                  recipeRevision == key.recipeRevision &&
                  providerRevision == key.providerRevision &&
                  policyRevision == key.policyRevision && taskRevision == key.taskRevision &&
                  frequencyUUID.equals(key.frequencyUUID) && target.equals(key.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(frequencyUUID, target, contentRevision, capacityRevision,
                  claimRevision, accessRevision, recipeRevision, providerRevision,
                  policyRevision, taskRevision);
        }
    }

    private static final class EffectiveRouteView {

        private final long recipeRevision;
        private final long providerRevision;
        private final long providerAvailabilityRevision;
        private final long policyRevision;
        private final long deviceRevision;
        private final List<UUID> processorIds;
        private final QIOPlanningRouteClosure.ProviderIndex providerIndex;
        private final Map<ResourceLocation, Long> workbenchPriorities;
        private final Set<PortableResourceDescriptor> schedulableOutputs;
        private final long revision;

        private EffectiveRouteView(long recipeRevision, long providerRevision,
              long providerAvailabilityRevision, long policyRevision, long deviceRevision,
              List<UUID> processorIds,
              QIOPlanningRouteClosure.ProviderIndex providerIndex,
              Map<ResourceLocation, Long> workbenchPriorities,
              Set<PortableResourceDescriptor> schedulableOutputs, long revision) {
            this.recipeRevision = recipeRevision;
            this.providerRevision = providerRevision;
            this.providerAvailabilityRevision = providerAvailabilityRevision;
            this.policyRevision = policyRevision;
            this.deviceRevision = deviceRevision;
            this.processorIds = Collections.unmodifiableList(new ArrayList<>(processorIds));
            this.providerIndex = providerIndex;
            this.workbenchPriorities = Collections.unmodifiableMap(new LinkedHashMap<>(
                  workbenchPriorities));
            this.schedulableOutputs = schedulableOutputs;
            this.revision = revision;
        }

        private boolean matches(long recipeRevision, long providerRevision,
              long providerAvailabilityRevision, long policyRevision, long deviceRevision,
              List<UUID> processorIds) {
            return this.recipeRevision == recipeRevision &&
                  this.providerRevision == providerRevision &&
                  this.providerAvailabilityRevision == providerAvailabilityRevision &&
                  this.policyRevision == policyRevision && this.deviceRevision == deviceRevision &&
                  this.processorIds.equals(processorIds);
        }
    }

}
