package mekanism.qioprocessing.common.maintenance;

import mekanism.api.qio.external.IQIOStorageView;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.api.qio.external.QIOStorageChange;
import mekanism.api.qio.external.QIOStorageChangeBatch;
import mekanism.api.qio.external.QIOStorageEntry;
import mekanism.api.qio.external.QIOStorageSnapshot;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.QIOProcessingConfig;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.content.qio.QIOResourceTypeRegistry;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOFrequencyIdentitySnapshot;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkLifecycle;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;
import mekanism.qioprocessing.common.content.job.QIOCraftingJobSource;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceEvaluation;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceEvaluator;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRule;
import mekanism.qioprocessing.common.content.maintenance.QIOMaintenanceRuleCatalog;
import mekanism.qioprocessing.common.content.material.QIOMaterialClaimCoordinator;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.execution.QIOProcessingExecutionService;
import mekanism.qioprocessing.common.order.QIOOrderService;
import mekanism.qioprocessing.common.planning.QIOPlanningExecutor;
import mekanism.qioprocessing.common.planning.QIOPlanner;
import mekanism.qioprocessing.common.planning.QIOPlanningRequest;
import mekanism.qioprocessing.common.planning.QIOPlanningResult;
import mekanism.qioprocessing.common.planning.QIOPlanningService;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Frequency-owned inventory maintenance evaluation and automatic order coordinator. */
public final class QIOMaintenanceService {

    public static final QIOMaintenanceService INSTANCE = new QIOMaintenanceService();

    private static final long CONTENT_SETTLE_TICKS = 2;
    private static final long MAX_CONTENT_DELAY_TICKS = 20;
    private static final long STRUCTURAL_SETTLE_TICKS = 100;
    private static final long MAX_STRUCTURAL_DELAY_TICKS = 200;
    private static final long SESSION_RETRY_TICKS = 20;
    private static final int MAX_DIRTY_RESOURCES = 4_096;
    private static final int MAX_GLOBAL_IN_FLIGHT = 4;
    private static final int MAX_IN_FLIGHT_PER_FREQUENCY = 2;

    private final Map<UUID, InFlightPlanning> inFlight = new LinkedHashMap<>();
    /** Long-lived listeners; callbacks only mark bounded wake state. */
    private final Map<UUID, StorageSession> storageSessions = new LinkedHashMap<>();
    private final Map<UUID, Long> storageRetryTicks = new HashMap<>();
    private int networkCursor;
    private long observedTick;

    private QIOMaintenanceService() {
    }

    /** Performs a globally bounded, round-robin maintenance slice on the server thread. */
    public synchronized void tick(@Nonnull World world) {
        Objects.requireNonNull(world, "world");
        if (world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        observedTick = world.getTotalWorldTime();
        removeOrphanedInFlight();
        List<QIOProcessingNetworkData> networks = new ArrayList<>(
              QIOProcessingNetworkManager.INSTANCE.getNetworks());
        networks.removeIf(network -> network.getLifecycle() != QIOProcessingNetworkLifecycle.ACTIVE ||
              !network.getMaintenanceRules().hasRules() &&
                    !network.getMaintenanceRules().hasPendingEvaluations());
        networks.sort(Comparator.comparing(network -> network.getFrequencyUUID().toString()));
        synchronizeStorageSessions(networks, observedTick);
        if (networks.isEmpty()) {
            networkCursor = 0;
            return;
        }
        QIOProcessingConfig config = MekanismConfig.current().qioProcessing;
        int remaining = config.maintenanceGroupsPerTick.val();
        int start = Math.floorMod(networkCursor, networks.size());
        for (int offset = 0; offset < networks.size() && remaining > 0; offset++) {
            QIOProcessingNetworkData network = networks.get((start + offset) % networks.size());
            int remainingNetworks = networks.size() - offset;
            int quota = Math.max(1, remaining / remainingNetworks);
            remaining -= Math.min(remaining, processNetwork(world, network,
                  observedTick, quota, config));
        }
        networkCursor = (start + 1) % networks.size();
    }

    public synchronized void shutdown() {
        QIOPlanningExecutor executor = QIOPlanningService.INSTANCE.getExecutor();
        if (executor != null) {
            for (InFlightPlanning planning : inFlight.values()) {
                executor.cancel(planning.taskId);
            }
        }
        inFlight.clear();
        for (StorageSession session : storageSessions.values()) {
            session.view.close();
        }
        storageSessions.clear();
        storageRetryTicks.clear();
        networkCursor = 0;
    }

    private int processNetwork(World world, QIOProcessingNetworkData network, long currentTick,
          int quota, QIOProcessingConfig config) {
        QIOMaintenanceRuleCatalog catalog = network.getMaintenanceRules();
        StorageSession storageSession = storageSessions.get(network.getFrequencyUUID());
        boolean changed = false;
        if (storageSession != null) {
            QIOMaintenanceWakeTracker.WakeBatch wake = storageSession.drainWake(network,
                  currentTick);
            if (wake.isFullScan()) {
                changed |= catalog.requestEvaluationPass();
            } else {
                for (PortableResourceDescriptor resource : wake.getResources()) {
                    catalog.requestTargetedEvaluation(resource);
                }
            }
        }
        changed |= reconcileOutstandingJobs(network, catalog);
        boolean evaluationDue = currentTick >= catalog.getNextEvaluationTick();
        List<QIOMaintenanceEvaluation> resumable = new ArrayList<>();
        if (catalog.hasPendingEvaluations()) {
            for (QIOMaintenanceEvaluation evaluation : catalog.getPendingEvaluations()) {
                if (!inFlight.containsKey(evaluation.getEvaluationId())) {
                    resumable.add(evaluation);
                }
            }
        }
        boolean targetedDue = catalog.hasTargetedEvaluations();
        if (resumable.isEmpty() && !evaluationDue && !targetedDue) {
            if (changed) {
                network.markMaintenanceRuntimeChanged();
            }
            return 0;
        }

        IQIOStorageView view = storageSession == null ? open(network) : storageSession.view;
        boolean transientView = storageSession == null;
        if (view == null || !view.isValid()) {
            if (transientView && view != null) {
                view.close();
            }
            if (changed) {
                network.markMaintenanceRuntimeChanged();
            }
            int consumed = handleAccessLoss(network, catalog, resumable, evaluationDue,
                  currentTick, quota, config);
            return consumed;
        }
        Map<PortableResourceDescriptor, List<QIOMaintenanceRule>> grouped =
              catalog.groupedRules();
        List<Map.Entry<PortableResourceDescriptor, List<QIOMaintenanceRule>>> groups =
              evaluationDue ? new ArrayList<>(grouped.entrySet()) : Collections.emptyList();
        List<EvaluationCandidate> candidates = new ArrayList<>(quota);

        for (QIOMaintenanceEvaluation evaluation : resumable) {
            if (candidates.size() >= quota) {
                break;
            }
            candidates.add(EvaluationCandidate.resumable(evaluation));
        }

        int available = quota - candidates.size();
        int targetedBudget = available;
        if (evaluationDue && available > 0) {
            targetedBudget = available == 1 ? ((currentTick & 1) == 0 ? 1 : 0) :
                  Math.max(1, available / 2);
        }
        int targetedAdded = 0;
        int targetedAttempts = 0;
        while (targetedAdded < targetedBudget && targetedAttempts++ < MAX_DIRTY_RESOURCES) {
            PortableResourceDescriptor resource = catalog.pollTargetedEvaluation();
            if (resource == null) {
                break;
            }
            List<QIOMaintenanceRule> group = grouped.get(resource);
            if (group == null || catalog.getPendingForResource(resource) != null) {
                continue;
            }
            candidates.add(EvaluationCandidate.targeted(resource, group));
            targetedAdded++;
        }

        if (evaluationDue && candidates.size() < quota) {
            int cursor = Math.min(catalog.getEvaluationCursor(), groups.size());
            for (int index = cursor; index < groups.size() && candidates.size() < quota;
                  index++) {
                Map.Entry<PortableResourceDescriptor, List<QIOMaintenanceRule>> entry =
                      groups.get(index);
                candidates.add(EvaluationCandidate.audit(entry.getKey(), entry.getValue()));
            }
        }

        int consumed = 0;
        int processedAuditGroups = 0;
        try {
            Map<PortableResourceDescriptor, BigInteger> spendable = new LinkedHashMap<>();
            for (EvaluationCandidate candidate : candidates) {
                spendable.computeIfAbsent(candidate.resource,
                      resource -> availableAmount(view, resource));
            }
            QIOMaintenanceEvaluator.EvaluationContext evaluationContext =
                  QIOMaintenanceEvaluator.EvaluationContext.createProjected(network, spendable);
            PlanningInputs planningInputs = new PlanningInputs(world, network, view,
                  storageSession);
            List<PreparedSubmission> submissions = new ArrayList<>();
            for (EvaluationCandidate candidate : candidates) {
                consumed++;
                if (candidate.kind == CandidateKind.RESUMABLE) {
                    changed |= resumeEvaluation(network, catalog, grouped, evaluationContext,
                          candidate.evaluation, config, currentTick, planningInputs,
                          submissions);
                    continue;
                }
                FreshEvaluationResult result = evaluateFreshGroup(network, catalog,
                      evaluationContext, candidate.resource, candidate.group, config,
                      currentTick, planningInputs, submissions);
                changed |= result.changed;
                if (result.deferred) {
                    if (candidate.kind == CandidateKind.TARGETED) {
                        catalog.requestTargetedEvaluation(candidate.resource);
                    } else {
                        break;
                    }
                } else if (candidate.kind == CandidateKind.AUDIT) {
                    processedAuditGroups++;
                }
            }

            if (evaluationDue && (processedAuditGroups > 0 || groups.isEmpty())) {
                catalog.advanceCursor(groups.size(), processedAuditGroups, currentTick,
                      config.maintenanceEvaluationInterval.val());
                changed = true;
            }

            if (changed) {
                network.markMaintenanceRuntimeChanged();
            }
            if (!submissions.isEmpty() && !persistBeforePlanning(network)) {
                return consumed;
            }
            boolean rejected = false;
            for (PreparedSubmission submission : submissions) {
                rejected |= submit(network, submission, currentTick) == SubmitOutcome.FAILED;
            }
            if (rejected) {
                network.markMaintenanceRuntimeChanged();
                persistQuietly(network);
            }
            return consumed;
        } catch (RuntimeException e) {
            if (changed) {
                network.markMaintenanceRuntimeChanged();
            }
            Mekanism.logger.error("Unable to evaluate QIO maintenance rules for {}",
                  network.getFrequencyUUID(), e);
            return Math.max(1, consumed);
        } finally {
            if (transientView) {
                view.close();
            }
        }
    }

    private boolean resumeEvaluation(QIOProcessingNetworkData network,
          QIOMaintenanceRuleCatalog catalog,
          Map<PortableResourceDescriptor, List<QIOMaintenanceRule>> grouped,
          QIOMaintenanceEvaluator.EvaluationContext context,
          QIOMaintenanceEvaluation evaluation, QIOProcessingConfig config, long currentTick,
          PlanningInputs planningInputs, List<PreparedSubmission> submissions) {
        List<QIOMaintenanceRule> group = grouped.get(evaluation.getResource());
        if (!matchesRuleSnapshot(evaluation, group)) {
            return catalog.invalidateEvaluation(evaluation.getEvaluationId());
        }
        QIOMaintenanceEvaluator.Decision decision = evaluate(network, context,
              evaluation.getResource(), group, config, currentTick);
        if (decision.getStatus() == QIOMaintenanceEvaluator.Status.ACTIVE_ORDER &&
              decision.getActiveJobId() != null) {
            return catalog.completeEvaluation(evaluation.getEvaluationId(),
                  decision.getActiveJobId());
        }
        if (!matchesPendingDecision(evaluation, decision)) {
            QIOMaintenanceRule.EvaluationStatus status = decision.getStatus() ==
                  QIOMaintenanceEvaluator.Status.NOT_TRIGGERED ?
                  QIOMaintenanceRule.EvaluationStatus.NOT_TRIGGERED :
                  QIOMaintenanceRule.EvaluationStatus.ERROR;
            return catalog.failEvaluation(evaluation.getEvaluationId(), status, currentTick,
                  "Effective maintenance request changed while planning");
        }
        if (!hasPlanningCapacity(network.getFrequencyUUID(), submissions)) {
            return false;
        }
        QIOOrderService.PlanningCaptureResult captured = planningInputs.capture(
              evaluation.getResource());
        if (captured.getStatus() != QIOOrderService.RequestStatus.ACCEPTED ||
              captured.getCapture() == null) {
            return catalog.failEvaluation(evaluation.getEvaluationId(),
                  QIOMaintenanceRule.EvaluationStatus.NO_ROUTE, currentTick,
                  captured.getStatus().name());
        }
        QIOMaintenanceEvaluation refreshed = evaluation.withSourceRevisions(
              captured.getCapture().getSourceRevisions());
        catalog.replacePendingEvaluation(refreshed);
        submissions.add(new PreparedSubmission(refreshed, captured.getCapture()));
        return true;
    }

    private FreshEvaluationResult evaluateFreshGroup(QIOProcessingNetworkData network,
          QIOMaintenanceRuleCatalog catalog,
          QIOMaintenanceEvaluator.EvaluationContext context,
          PortableResourceDescriptor resource, List<QIOMaintenanceRule> group,
          QIOProcessingConfig config, long currentTick, PlanningInputs planningInputs,
          List<PreparedSubmission> submissions) {
        if (catalog.getPendingForResource(resource) != null) {
            return FreshEvaluationResult.UNCHANGED;
        }
        try {
            QIOMaintenanceEvaluator.Decision decision = evaluate(network, context, resource,
                  group, config, currentTick);
            switch (decision.getStatus()) {
                case NOT_TRIGGERED:
                    return FreshEvaluationResult.changed(recordGroupStatus(catalog, group,
                          statusEvaluationId(network, catalog, resource, group,
                                "not-triggered"),
                          QIOMaintenanceRule.EvaluationStatus.NOT_TRIGGERED, currentTick,
                          null, false));
                case RETRY_WAIT:
                    return FreshEvaluationResult.UNCHANGED;
                case ACTIVE_ORDER:
                    if (decision.getActiveJobId() == null ||
                          decision.getTriggeredRules().isEmpty()) {
                        return FreshEvaluationResult.UNCHANGED;
                    }
                    UUID activeJobId = decision.getActiveJobId();
                    boolean alreadyBound = decision.getTriggeredRules().stream().allMatch(rule ->
                          activeJobId.equals(rule.getOutstandingJobId()) &&
                                rule.getEvaluationStatus() ==
                                      QIOMaintenanceRule.EvaluationStatus.ACTIVE_ORDER);
                    if (alreadyBound) {
                        return FreshEvaluationResult.UNCHANGED;
                    }
                    catalog.bindOutstanding(decision.getTriggeredRules(),
                          statusEvaluationId(network, catalog, resource,
                                decision.getTriggeredRules(), "active-order"), activeJobId);
                    return FreshEvaluationResult.CHANGED;
                case REQUEST:
                    if (!hasPlanningCapacity(network.getFrequencyUUID(), submissions)) {
                        return FreshEvaluationResult.DEFERRED;
                    }
                    QIOOrderService.PlanningCaptureResult captured = planningInputs.capture(
                          resource);
                    if (captured.getStatus() != QIOOrderService.RequestStatus.ACCEPTED ||
                          captured.getCapture() == null) {
                        catalog.recordGroupEvaluation(decision.getTriggeredRules(),
                              statusEvaluationId(network, catalog, resource,
                                    decision.getTriggeredRules(), "no-route"),
                              QIOMaintenanceRule.EvaluationStatus.NO_ROUTE, currentTick,
                              captured.getStatus().name());
                        return FreshEvaluationResult.CHANGED;
                    }
                    long generation = catalog.reserveEvaluationGeneration();
                    QIOMaintenanceEvaluation evaluation = QIOMaintenanceEvaluation.create(
                          network.getFrequencyUUID(), generation, resource, group,
                          decision.getTriggeredRules(), catalog.getRulesRevision(),
                          captured.getCapture().getSourceRevisions(),
                          decision.getRequestAmount(), decision.getJobPriority(), currentTick);
                    catalog.beginEvaluation(evaluation, group, currentTick);
                    submissions.add(new PreparedSubmission(evaluation,
                          captured.getCapture()));
                    return FreshEvaluationResult.CHANGED;
                default:
                    throw new IllegalStateException("Unknown maintenance decision");
            }
        } catch (RuntimeException e) {
            catalog.recordGroupEvaluation(group, statusEvaluationId(network, catalog, resource,
                  group, "error"), QIOMaintenanceRule.EvaluationStatus.ERROR, currentTick,
                  diagnostic(e));
            return FreshEvaluationResult.CHANGED;
        }
    }

    private static boolean recordGroupStatus(QIOMaintenanceRuleCatalog catalog,
          List<QIOMaintenanceRule> group, UUID evaluationId,
          QIOMaintenanceRule.EvaluationStatus status, long currentTick,
          @Nullable String diagnostic, boolean force) {
        String expectedDiagnostic = diagnostic == null ? "" : diagnostic.trim();
        if (!force && group.stream().allMatch(rule -> rule.getEvaluationStatus() == status &&
              expectedDiagnostic.equals(rule.getDiagnostic()))) {
            return false;
        }
        catalog.recordGroupEvaluation(group, evaluationId, status, currentTick, diagnostic);
        return true;
    }

    private boolean hasPlanningCapacity(UUID frequencyUUID,
          List<PreparedSubmission> submissions) {
        if (inFlight.size() + submissions.size() >= MAX_GLOBAL_IN_FLIGHT) {
            return false;
        }
        int frequencyInFlight = 0;
        for (InFlightPlanning planning : inFlight.values()) {
            if (frequencyUUID.equals(planning.frequencyUUID)) {
                frequencyInFlight++;
            }
        }
        return frequencyInFlight + submissions.size() < MAX_IN_FLIGHT_PER_FREQUENCY;
    }

    private static BigInteger availableAmount(IQIOStorageView view,
          PortableResourceDescriptor resource) {
        UUID resourceUUID;
        switch (resource.getKind()) {
            case ITEM:
                net.minecraft.item.ItemStack item = resource.resolveItem();
                resourceUUID = item.isEmpty() ? null : QIOResourceTypeRegistry.INSTANCE
                      .getUUIDForItem(HashedItem.create(item));
                break;
            case FLUID:
                resourceUUID = QIOResourceTypeRegistry.INSTANCE.getUUIDForFluid(
                      resource.resolveFluid());
                break;
            case GAS:
                resourceUUID = QIOResourceTypeRegistry.INSTANCE.getUUIDForGas(
                      resource.resolveGas());
                break;
            default:
                throw new IllegalStateException("Unknown maintenance resource kind");
        }
        QIOStorageEntry entry = resourceUUID == null ? null : view.getResource(resourceUUID);
        if (entry == null) {
            return BigInteger.ZERO;
        }
        if (!resource.equals(PortableResourceDescriptor.fromStorageEntry(entry))) {
            throw new IllegalStateException("QIO maintenance resource identity changed");
        }
        return entry.getExactAvailableAmount();
    }

    /** Keeps one listener/view per active frequency and bounds reconnect attempts. */
    private void synchronizeStorageSessions(List<QIOProcessingNetworkData> networks,
          long currentTick) {
        Map<UUID, QIOProcessingNetworkData> active = new LinkedHashMap<>();
        for (QIOProcessingNetworkData network : networks) {
            active.put(network.getFrequencyUUID(), network);
        }
        Iterator<Map.Entry<UUID, StorageSession>> iterator = storageSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, StorageSession> entry = iterator.next();
            QIOProcessingNetworkData network = active.get(entry.getKey());
            StorageSession session = entry.getValue();
            if (network == null || network.getLifecycle() != QIOProcessingNetworkLifecycle.ACTIVE ||
                  session.wakeTracker.isInvalidated() || !session.view.isValid()) {
                session.view.close();
                iterator.remove();
                storageRetryTicks.put(entry.getKey(), currentTick + SESSION_RETRY_TICKS);
            }
        }
        storageRetryTicks.keySet().removeIf(frequencyUUID -> !active.containsKey(frequencyUUID));
        for (QIOProcessingNetworkData network : networks) {
            UUID frequencyUUID = network.getFrequencyUUID();
            if (storageSessions.containsKey(frequencyUUID) ||
                  currentTick < storageRetryTicks.getOrDefault(frequencyUUID, 0L)) {
                continue;
            }
            IQIOStorageView view = null;
            try {
                view = open(network);
                if (view != null && view.isValid()) {
                    StorageSession session = new StorageSession(frequencyUUID, view);
                    if (view.addListener(changes -> onStorageChanged(session, changes))) {
                        session.wakeTracker.requestInitialScan(currentTick);
                        storageSessions.put(frequencyUUID, session);
                        storageRetryTicks.remove(frequencyUUID);
                        continue;
                    }
                }
            } catch (RuntimeException e) {
                Mekanism.logger.debug("Unable to attach QIO maintenance storage listener for {}",
                      frequencyUUID, e);
            }
            if (view != null) {
                view.close();
            }
            storageRetryTicks.put(frequencyUUID, currentTick + SESSION_RETRY_TICKS);
        }
    }

    private synchronized void onStorageChanged(StorageSession expected,
          QIOStorageChangeBatch changes) {
        StorageSession current = storageSessions.get(expected.frequencyUUID);
        if (current != expected) {
            return;
        }
        QIOProcessingExecutionService.INSTANCE.wakeStorage(expected.frequencyUUID);
        if (changes.isInvalidated()) {
            current.wakeTracker.invalidate();
            return;
        }
        current.contentsRevision = changes.getNewContentsRevision();
        current.accessRevision = changes.getAccessRevision();
        current.cachedSnapshot = null;
        boolean structural = changes.isFullRescanRequired() || changes.isCapacityChanged() ||
              changes.isClaimChanged();
        if (structural) {
            current.wakeTracker.requestStructuralScan(observedTick);
        } else {
            for (QIOStorageChange change : changes.getChanges()) {
                try {
                    current.wakeTracker.recordResource(
                          PortableResourceDescriptor.fromStorageEntry(change.getResource()),
                          observedTick);
                } catch (RuntimeException e) {
                    current.wakeTracker.requestStructuralScan(observedTick);
                    break;
                }
            }
        }
    }

    private int handleAccessLoss(QIOProcessingNetworkData network,
          QIOMaintenanceRuleCatalog catalog, List<QIOMaintenanceEvaluation> resumable,
          boolean evaluationDue, long currentTick, int quota, QIOProcessingConfig config) {
        int consumed = 0;
        boolean changed = false;
        for (QIOMaintenanceEvaluation evaluation : resumable) {
            if (consumed >= quota) {
                break;
            }
            consumed++;
            changed |= catalog.failEvaluation(evaluation.getEvaluationId(),
                  QIOMaintenanceRule.EvaluationStatus.ACCESS_DENIED, currentTick,
                  "QIO frequency storage access is unavailable");
        }
        if (evaluationDue && consumed < quota) {
            List<Map.Entry<PortableResourceDescriptor, List<QIOMaintenanceRule>>> groups =
                  new ArrayList<>(catalog.groupedRules().entrySet());
            int cursor = Math.min(catalog.getEvaluationCursor(), groups.size());
            int processed = 0;
            while (cursor + processed < groups.size() && consumed < quota) {
                Map.Entry<PortableResourceDescriptor, List<QIOMaintenanceRule>> entry =
                      groups.get(cursor + processed);
                processed++;
                consumed++;
                if (hasActiveOutstanding(network, entry.getValue())) {
                    continue;
                }
                catalog.recordGroupEvaluation(entry.getValue(), statusEvaluationId(network,
                      catalog, entry.getKey(), entry.getValue(), "access-denied"),
                      QIOMaintenanceRule.EvaluationStatus.ACCESS_DENIED, currentTick,
                      "QIO frequency storage access is unavailable");
                changed = true;
            }
            if (processed > 0 || groups.isEmpty()) {
                catalog.advanceCursor(groups.size(), processed, currentTick,
                      config.maintenanceEvaluationInterval.val());
                changed = true;
            }
        }
        if (changed) {
            network.markMaintenanceRuntimeChanged();
        }
        return consumed;
    }

    private SubmitOutcome submit(QIOProcessingNetworkData network,
          PreparedSubmission submission,
          long currentTick) {
        QIOMaintenanceEvaluation evaluation = submission.evaluation;
        QIOPlanningExecutor.Submission submitted;
        try {
            submitted = QIOPlanningService.INSTANCE.submitTask(submission.capture,
                  (capture, cancellation) -> {
                      QIOOrderService.PlanningPreparation preparation =
                            QIOOrderService.INSTANCE.prepareCaptured(capture,
                                  cancellation::isCancelled);
                      if (preparation.getSnapshot() == null) {
                          return QIOPlanningResult.failure(QIOPlanningResult.Status.NO_ROUTE,
                                preparation.getStatus().name(), 0, 0);
                      }
                      QIOPlanningRequest request = new QIOPlanningRequest(
                            evaluation.getEvaluationId(), 1, preparation.getSnapshot(),
                            evaluation.getResource(), evaluation.getRequestAmount());
                      return QIOPlanner.INSTANCE.plan(request, cancellation);
                  }, completed -> completePlanning(evaluation.getEvaluationId(), completed));
        } catch (RuntimeException e) {
            network.getMaintenanceRules().failEvaluation(evaluation.getEvaluationId(),
                  QIOMaintenanceRule.EvaluationStatus.ERROR, currentTick, diagnostic(e));
            return SubmitOutcome.FAILED;
        }
        if (!submitted.isAccepted()) {
            network.getMaintenanceRules().failEvaluation(evaluation.getEvaluationId(),
                  QIOMaintenanceRule.EvaluationStatus.ERROR, currentTick,
                  submitted.getStatus().name());
            return SubmitOutcome.FAILED;
        }
        inFlight.put(evaluation.getEvaluationId(), new InFlightPlanning(
              network.getFrequencyUUID(), submitted.getTaskId()));
        return SubmitOutcome.SUBMITTED;
    }

    private synchronized void completePlanning(UUID evaluationId,
          QIOPlanningExecutor.PlanningResult<QIOPlanningResult> completed) {
        InFlightPlanning planning = inFlight.get(evaluationId);
        if (planning == null || !planning.taskId.equals(completed.getTaskId())) {
            return;
        }
        inFlight.remove(evaluationId);
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
              planning.frequencyUUID);
        World world = DimensionManager.getWorld(0);
        if (network == null || world == null || world.isRemote) {
            return;
        }
        QIOMaintenanceRuleCatalog catalog = network.getMaintenanceRules();
        QIOMaintenanceEvaluation evaluation = catalog.getPending(evaluationId);
        if (evaluation == null) {
            return;
        }
        long currentTick = Math.max(evaluation.getCreatedAtTick(), world.getTotalWorldTime());
        if (completed.getStatus() != QIOPlanningExecutor.ResultStatus.SUCCESS ||
              completed.getValue() == null) {
            String failure = completed.getError() == null ? completed.getStatus().name() :
                  diagnostic(completed.getError());
            failAndPersist(network, evaluationId, QIOMaintenanceRule.EvaluationStatus.ERROR,
                  currentTick, failure);
            return;
        }
        QIOPlanningResult result = completed.getValue();
        if (result.getStatus() != QIOPlanningResult.Status.SUCCESS || result.getPlan() == null) {
            QIOMaintenanceRule.EvaluationStatus status = result.getStatus() ==
                  QIOPlanningResult.Status.NO_ROUTE ||
                  result.getStatus() == QIOPlanningResult.Status.CYCLE_NO_SEED ||
                  result.getStatus() == QIOPlanningResult.Status.CYCLE_REQUIRES_SCC ?
                  QIOMaintenanceRule.EvaluationStatus.NO_ROUTE :
                  QIOMaintenanceRule.EvaluationStatus.ERROR;
            failAndPersist(network, evaluationId, status, currentTick,
                  result.getStatus() + ": " + result.getDiagnostic());
            return;
        }

        IQIOStorageView view = open(network);
        if (view == null || !view.isValid()) {
            if (view != null) {
                view.close();
            }
            failAndPersist(network, evaluationId,
                  QIOMaintenanceRule.EvaluationStatus.ACCESS_DENIED, currentTick,
                  "QIO frequency storage access is unavailable");
            return;
        }
        try {
            QIOStorageSnapshot storage = view.getSnapshot();
            List<QIOMaintenanceRule> group = network.getMaintenanceRules().groupedRules()
                  .get(evaluation.getResource());
            if (!matchesRuleSnapshot(evaluation, group)) {
                invalidateAndPersist(network, evaluationId);
                return;
            }
            QIOMaintenanceEvaluator.Decision decision = evaluate(network, storage,
                  evaluation.getResource(), group, MekanismConfig.current().qioProcessing,
                  currentTick);
            if (decision.getStatus() == QIOMaintenanceEvaluator.Status.ACTIVE_ORDER &&
                  decision.getActiveJobId() != null) {
                catalog.completeEvaluation(evaluationId, decision.getActiveJobId());
                network.markMaintenanceRuntimeChanged();
                persistQuietly(network);
                return;
            }
            if (!matchesPendingDecision(evaluation, decision)) {
                failAndPersist(network, evaluationId,
                      QIOMaintenanceRule.EvaluationStatus.ERROR, currentTick,
                      "Effective maintenance request changed while planning");
                return;
            }
            QIOCraftPlan plan = result.getPlan();
            if (!evaluation.getResource().equals(plan.getRootResource()) ||
                  evaluation.getRequestAmount() != plan.getRootAmount() ||
                  !QIOOrderService.INSTANCE.isAutomatedPlanCurrent(world, network, storage,
                        plan.getSourceRevisions())) {
                failAndPersist(network, evaluationId,
                      QIOMaintenanceRule.EvaluationStatus.ERROR, currentTick,
                      "Maintenance planning inputs changed before commit");
                return;
            }

            QIOCraftingJob job = network.getJob(evaluation.getJobId());
            if (job == null) {
                try {
                    job = network.createJob(evaluation.getJobId(),
                          QIOCraftingJobSource.MAINTENANCE, null, evaluation.getJobPriority(),
                          currentTick, plan, MekanismConfig.current().qioProcessing
                                .nonTerminalJobsPerFrequency.val());
                } catch (RuntimeException e) {
                    failAndPersist(network, evaluationId,
                          QIOMaintenanceRule.EvaluationStatus.ERROR, currentTick, diagnostic(e));
                    return;
                }
            } else if (job.getSource() != QIOCraftingJobSource.MAINTENANCE ||
                  !evaluation.getResource().equals(job.getActivePlan().getRootResource()) ||
                  evaluation.getRequestAmount() != job.getActivePlan().getRootAmount()) {
                failAndPersist(network, evaluationId,
                      QIOMaintenanceRule.EvaluationStatus.ERROR, currentTick,
                      "Stable maintenance job identity conflicts with another order");
                return;
            }
            if (!catalog.completeEvaluation(evaluationId, job.getJobId())) {
                return;
            }
            network.markMaintenanceRuntimeChanged();
            try {
                QIOProcessingNetworkManager.INSTANCE.persistenceBarrier().persist(network);
            } catch (IOException e) {
                Mekanism.logger.error("Unable to persist QIO maintenance order {}",
                      job.getJobId(), e);
                return;
            }
            try {
                QIOMaterialClaimCoordinator.refresh(network, job.getJobId(), view,
                      QIOProcessingNetworkManager.INSTANCE.persistenceBarrier());
            } catch (IOException | RuntimeException e) {
                Mekanism.logger.error("Unable to initialize material claim for QIO maintenance order {}",
                      job.getJobId(), e);
            }
        } catch (RuntimeException e) {
            failAndPersist(network, evaluationId, QIOMaintenanceRule.EvaluationStatus.ERROR,
                  currentTick, diagnostic(e));
        } finally {
            view.close();
        }
    }

    private static QIOMaintenanceEvaluator.Decision evaluate(QIOProcessingNetworkData network,
          QIOStorageSnapshot storage, PortableResourceDescriptor resource,
          List<QIOMaintenanceRule> group, QIOProcessingConfig config, long currentTick) {
        return QIOMaintenanceEvaluator.evaluate(network, storage, resource, group,
              config.maintenanceBatchCap.val(), currentTick);
    }

    private static QIOMaintenanceEvaluator.Decision evaluate(QIOProcessingNetworkData network,
          QIOMaintenanceEvaluator.EvaluationContext context,
          PortableResourceDescriptor resource, List<QIOMaintenanceRule> group,
          QIOProcessingConfig config, long currentTick) {
        return QIOMaintenanceEvaluator.evaluate(network, context, resource, group,
              config.maintenanceBatchCap.val(), currentTick);
    }

    private static boolean reconcileOutstandingJobs(QIOProcessingNetworkData network,
          QIOMaintenanceRuleCatalog catalog) {
        if (!catalog.hasOutstandingRules()) {
            return false;
        }
        Map<UUID, Boolean> terminal = new HashMap<>();
        for (QIOCraftingJob job : network.getJobs()) {
            terminal.put(job.getJobId(), job.getState().isTerminal());
        }
        return catalog.clearTerminalOutstanding(terminal);
    }

    private static boolean hasActiveOutstanding(QIOProcessingNetworkData network,
          List<QIOMaintenanceRule> group) {
        for (QIOMaintenanceRule rule : group) {
            UUID jobId = rule.getOutstandingJobId();
            QIOCraftingJob job = jobId == null ? null : network.getJob(jobId);
            if (job != null && !job.getState().isTerminal()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRuleSnapshot(QIOMaintenanceEvaluation evaluation,
          @Nullable List<QIOMaintenanceRule> group) {
        if (group == null || group.size() != evaluation.getGroupRuleRevisions().size()) {
            return false;
        }
        Map<UUID, Long> current = new LinkedHashMap<>();
        for (QIOMaintenanceRule rule : group) {
            current.put(rule.getRuleId(), rule.getRuleRevision());
        }
        return current.equals(evaluation.getGroupRuleRevisions());
    }

    private static boolean matchesPendingDecision(QIOMaintenanceEvaluation evaluation,
          QIOMaintenanceEvaluator.Decision decision) {
        if (decision.getStatus() != QIOMaintenanceEvaluator.Status.REQUEST ||
              decision.getRequestAmount() != evaluation.getRequestAmount() ||
              decision.getJobPriority() != evaluation.getJobPriority()) {
            return false;
        }
        List<UUID> triggered = new ArrayList<>();
        for (QIOMaintenanceRule rule : decision.getTriggeredRules()) {
            triggered.add(rule.getRuleId());
        }
        triggered.sort(Comparator.comparing(UUID::toString));
        return triggered.equals(evaluation.getTriggeredRuleIds());
    }

    @Nullable
    private static IQIOStorageView open(QIOProcessingNetworkData network) {
        QIOFrequencyIdentitySnapshot identity = network.getLastKnownFrequencyIdentity();
        UUID principal = identity.getOwnerUUID();
        QIOFrequencyReference reference = new QIOFrequencyReference(network.getFrequencyUUID(),
              identity.getName(), identity.getOwnerUUID(), identity.getSecurityMode(), principal);
        return QIOFrequencyStorageAccess.INSTANCE.open(reference, principal);
    }

    private static boolean persistBeforePlanning(QIOProcessingNetworkData network) {
        try {
            QIOProcessingNetworkManager.INSTANCE.persistenceBarrier().persist(network);
            return true;
        } catch (IOException | RuntimeException e) {
            Mekanism.logger.error("Unable to persist QIO maintenance evaluation claim for {}",
                  network.getFrequencyUUID(), e);
            return false;
        }
    }

    private static void failAndPersist(QIOProcessingNetworkData network, UUID evaluationId,
          QIOMaintenanceRule.EvaluationStatus status, long currentTick, String diagnostic) {
        if (network.getMaintenanceRules().failEvaluation(evaluationId, status, currentTick,
              diagnostic)) {
            network.markMaintenanceRuntimeChanged();
            persistQuietly(network);
        }
    }

    private static void invalidateAndPersist(QIOProcessingNetworkData network,
          UUID evaluationId) {
        if (network.getMaintenanceRules().invalidateEvaluation(evaluationId)) {
            network.markMaintenanceRuntimeChanged();
            persistQuietly(network);
        }
    }

    private static void persistQuietly(QIOProcessingNetworkData network) {
        try {
            QIOProcessingNetworkManager.INSTANCE.persistenceBarrier().persist(network);
        } catch (IOException | RuntimeException e) {
            Mekanism.logger.error("Unable to persist QIO maintenance state for {}",
                  network.getFrequencyUUID(), e);
        }
    }

    private void removeOrphanedInFlight() {
        QIOPlanningExecutor executor = QIOPlanningService.INSTANCE.getExecutor();
        java.util.Iterator<Map.Entry<UUID, InFlightPlanning>> iterator =
              inFlight.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, InFlightPlanning> entry = iterator.next();
            QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(
                  entry.getValue().frequencyUUID);
            if (network == null || network.getMaintenanceRules().getPending(entry.getKey()) == null) {
                if (executor != null) {
                    executor.cancel(entry.getValue().taskId);
                }
                iterator.remove();
            }
        }
    }

    private static UUID statusEvaluationId(QIOProcessingNetworkData network,
          QIOMaintenanceRuleCatalog catalog, PortableResourceDescriptor resource,
          Collection<QIOMaintenanceRule> group, String outcome) {
        StringBuilder key = new StringBuilder("qio-maintenance-status-v1|")
              .append(network.getFrequencyUUID()).append('|')
              .append(catalog.getEvaluationGeneration()).append('|').append(resource)
              .append('|').append(outcome);
        group.stream().sorted(Comparator.comparing(rule -> rule.getRuleId().toString()))
              .forEach(rule -> key.append("|rule=").append(rule.getRuleId()).append('@')
                    .append(rule.getRuleRevision()));
        return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String diagnostic(Throwable error) {
        String message = error == null ? "Unknown maintenance error" : error.getMessage();
        return message == null || message.trim().isEmpty() ?
              error.getClass().getSimpleName() : message.trim();
    }

    private enum CandidateKind {
        RESUMABLE,
        TARGETED,
        AUDIT
    }

    private enum SubmitOutcome {
        SUBMITTED,
        DEFERRED,
        FAILED
    }

    private static final class EvaluationCandidate {

        private final CandidateKind kind;
        private final PortableResourceDescriptor resource;
        @Nullable
        private final List<QIOMaintenanceRule> group;
        @Nullable
        private final QIOMaintenanceEvaluation evaluation;

        private EvaluationCandidate(CandidateKind kind,
              PortableResourceDescriptor resource, @Nullable List<QIOMaintenanceRule> group,
              @Nullable QIOMaintenanceEvaluation evaluation) {
            this.kind = kind;
            this.resource = resource;
            this.group = group;
            this.evaluation = evaluation;
        }

        private static EvaluationCandidate resumable(QIOMaintenanceEvaluation evaluation) {
            return new EvaluationCandidate(CandidateKind.RESUMABLE,
                  evaluation.getResource(), null, evaluation);
        }

        private static EvaluationCandidate targeted(PortableResourceDescriptor resource,
              List<QIOMaintenanceRule> group) {
            return new EvaluationCandidate(CandidateKind.TARGETED, resource, group, null);
        }

        private static EvaluationCandidate audit(PortableResourceDescriptor resource,
              List<QIOMaintenanceRule> group) {
            return new EvaluationCandidate(CandidateKind.AUDIT, resource, group, null);
        }
    }

    private static final class FreshEvaluationResult {

        private static final FreshEvaluationResult UNCHANGED =
              new FreshEvaluationResult(false, false);
        private static final FreshEvaluationResult CHANGED =
              new FreshEvaluationResult(true, false);
        private static final FreshEvaluationResult DEFERRED =
              new FreshEvaluationResult(false, true);

        private final boolean changed;
        private final boolean deferred;

        private FreshEvaluationResult(boolean changed, boolean deferred) {
            this.changed = changed;
            this.deferred = deferred;
        }

        private static FreshEvaluationResult changed(boolean changed) {
            return changed ? CHANGED : UNCHANGED;
        }
    }

    private static final class PlanningInputs {

        private final World world;
        private final QIOProcessingNetworkData network;
        private final IQIOStorageView view;
        @Nullable
        private final StorageSession storageSession;
        private final Map<PortableResourceDescriptor, QIOOrderService.PlanningCaptureResult>
              captures = new LinkedHashMap<>();
        @Nullable
        private QIOStorageSnapshot storage;

        private PlanningInputs(World world, QIOProcessingNetworkData network,
              IQIOStorageView view, @Nullable StorageSession storageSession) {
            this.world = world;
            this.network = network;
            this.view = view;
            this.storageSession = storageSession;
        }

        private QIOOrderService.PlanningCaptureResult capture(
              PortableResourceDescriptor resource) {
            return captures.computeIfAbsent(resource, ignored ->
                  QIOOrderService.INSTANCE.capturePlanning(world, network, storage(), resource));
        }

        private QIOStorageSnapshot storage() {
            if (storage == null) {
                storage = storageSession == null ? view.getSnapshot() : storageSession.snapshot();
            }
            return storage;
        }
    }

    private static final class StorageSession {

        private final UUID frequencyUUID;
        private final IQIOStorageView view;
        private final QIOMaintenanceWakeTracker wakeTracker = new QIOMaintenanceWakeTracker(
              MAX_DIRTY_RESOURCES, CONTENT_SETTLE_TICKS, MAX_CONTENT_DELAY_TICKS,
              STRUCTURAL_SETTLE_TICKS, MAX_STRUCTURAL_DELAY_TICKS);
        private long contentsRevision = -1;
        private long accessRevision = -1;
        @Nullable
        private QIOStorageSnapshot cachedSnapshot;

        private StorageSession(UUID frequencyUUID, IQIOStorageView view) {
            this.frequencyUUID = frequencyUUID;
            this.view = view;
            contentsRevision = view.getContentsRevision();
            accessRevision = view.getAccessRevision();
        }

        private QIOStorageSnapshot snapshot() {
            long currentContents = view.getContentsRevision();
            long currentCapacity = view.getCapacityRevision();
            long currentClaim = view.getClaimRevision();
            long currentAccess = view.getAccessRevision();
            if (cachedSnapshot == null ||
                  cachedSnapshot.getContentsRevision() != currentContents ||
                  cachedSnapshot.getCapacityRevision() != currentCapacity ||
                  cachedSnapshot.getClaimRevision() != currentClaim ||
                  cachedSnapshot.getAccessRevision() != currentAccess) {
                cachedSnapshot = view.getSnapshot();
            }
            return cachedSnapshot;
        }

        private QIOMaintenanceWakeTracker.WakeBatch drainWake(
              QIOProcessingNetworkData network, long currentTick) {
            return wakeTracker.drainIfDue(currentTick,
                  network.getMaintenanceRules()::hasRuleForResource);
        }
    }

    private static final class PreparedSubmission {

        private final QIOMaintenanceEvaluation evaluation;
        private final QIOOrderService.PlanningCapture capture;

        private PreparedSubmission(QIOMaintenanceEvaluation evaluation,
              QIOOrderService.PlanningCapture capture) {
            this.evaluation = evaluation;
            this.capture = capture;
        }
    }

    private static final class InFlightPlanning {

        private final UUID frequencyUUID;
        private final UUID taskId;

        private InFlightPlanning(UUID frequencyUUID, UUID taskId) {
            this.frequencyUUID = frequencyUUID;
            this.taskId = taskId;
        }
    }
}
