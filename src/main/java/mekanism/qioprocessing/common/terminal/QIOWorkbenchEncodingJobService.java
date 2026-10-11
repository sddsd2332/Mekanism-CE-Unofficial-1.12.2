package mekanism.qioprocessing.common.terminal;

import mekanism.common.Mekanism;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.workbench.*;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration.EncodedPattern;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfigurationMutation.Action;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchEncodingRecord.State;
import mekanism.qioprocessing.common.planning.*;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchClosureService.ConfirmResult;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchClosureService.PreviewResult;
import mekanism.qioprocessing.common.terminal.QIOWorkbenchClosureService.Status;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * Server-owned jobs. Windows subscribe to results; they never own the computation or draft.
 */
public final class QIOWorkbenchEncodingJobService {
    public static final QIOWorkbenchEncodingJobService INSTANCE = new QIOWorkbenchEncodingJobService();
    private static final int MAX_ACTIVE = 16;
    private final Map<UUID, Job> active = new LinkedHashMap<>();
    private final Map<UUID, Long> validatedEpochs = new HashMap<>();
    private long serverEpoch;

    private QIOWorkbenchEncodingJobService() {
    }

    public void start(QIOWorkbenchConfigurationService.Context context, QIOProcessingTerminalSession session,
                      UUID owner, long configurationRevision, long catalogRevision, QIOWorkbenchConfigurationMutation mutation,
                      QIOWorkbenchClosureMode mode, boolean skipCycles, World world, Consumer<PreviewResult> completion) {
        if (mutation.getAction() == Action.ENCODE_TARGETS || mode != QIOWorkbenchClosureMode.NONE) {
            QIOWorkbenchStoreEncodingJobService.INSTANCE.start(context, session, owner, configurationRevision,
                    catalogRevision, mutation, mode, skipCycles, world, completion);
            return;
        }
        QIOWorkbenchDirectoryService.requireServerThread(world);
        Status rejected = admission(context, session, owner, configurationRevision, catalogRevision);
        if (rejected != null) {
            completion.accept(new PreviewResult(rejected, null));
            return;
        }
        if (mutation.getAction() != Action.ENCODE_PATTERN &&
                mutation.getAction() != Action.ENCODE_TARGETS) {
            completion.accept(new PreviewResult(Status.INVALID_TARGET, null));
            return;
        }
        if ((mutation.getAction() == Action.ENCODE_TARGETS || mode != QIOWorkbenchClosureMode.NONE) &&
                QIOWorkbenchDirectoryService.INSTANCE.getState() != QIOWorkbenchDirectoryService.State.READY) {
            completion.accept(new PreviewResult(Status.UNAVAILABLE, null));
            return;
        }
        QIOProcessingNetworkData network = context.getNetwork();
        // One player cannot accidentally start duplicate work by repeatedly reopening/retrying.
        for (QIOWorkbenchEncodingRecord record : network.getWorkbenchEncodingJobs()) {
            if (!record.isTerminal() && record.getOwner().equals(owner) &&
                    record.getConfiguration().equals(network.getWorkbenchConfiguration().getConfigUUID()) &&
                    record.getConfigurationRevision() == configurationRevision &&
                    record.getMode() == mode && record.skipsCycles() == skipCycles &&
                    record.getRequest().equals(mutation.write())) {
                completion.accept(view(record));
                return;
            }
        }
        QIOWorkbenchEncodingRecord record = new QIOWorkbenchEncodingRecord(UUID.randomUUID(), owner,
                network.getWorkbenchConfiguration().getConfigUUID(), configurationRevision, catalogRevision,
                world.provider.getDimension(), mutation.write(), mode, skipCycles);
        try {
            network.saveWorkbenchEncodingJob(record);
            launch(network, record, mutation, world, completion);
        } catch (RuntimeException error) {
            failBeforeLaunch(network, record, error, completion);
        }
    }

    @Nullable
    private Status admission(QIOWorkbenchConfigurationService.Context context, QIOProcessingTerminalSession session,
                             UUID owner, long configurationRevision, long catalogRevision) {
        if (!context.isEditable()) return Status.READ_ONLY;
        if (!session.getPlayerUUID().equals(owner)) return Status.ACCESS_DENIED;
        if (context.getNetwork().getWorkbenchConfiguration().getRevision() != configurationRevision)
            return Status.REVISION_CONFLICT;
        if (context.getCatalogRevision() != catalogRevision) return Status.CATALOG_CHANGED;
        if (!QIORecipeCatalogService.INSTANCE.isReady()) return Status.UNAVAILABLE;
        if (active.size() >= MAX_ACTIVE) return Status.BUSY;
        return null;
    }

    private void launch(QIOProcessingNetworkData network, QIOWorkbenchEncodingRecord record,
                        QIOWorkbenchConfigurationMutation mutation, World world, Consumer<PreviewResult> completion) {
        long preparationStarted = System.nanoTime(), preparationCpu = QIOPlanningMetrics.cpuNow();
        QIOWorkbenchDeclarations directory = QIOWorkbenchDirectoryService.INSTANCE.getDirectory();
        List<EncodedPattern> roots = Collections.emptyList();
        List<mekanism.qioprocessing.api.resource.PortableResourceDescriptor> targets = new ArrayList<>();
        if (mutation.getAction() == Action.ENCODE_TARGETS) {
            for (net.minecraft.item.ItemStack target : mutation.getTargets())
                targets.add(QIOWorkbenchDeclarations.Stack.capture(target).getResource());
        }
        QIOWorkbenchDiscovery.Input input = new QIOWorkbenchDiscovery.Input(directory,
                QIORecipeCatalogService.INSTANCE.capturePlannerBindings(directory), network.getWorkbenchConfiguration(),
                roots, targets, record.getMode(), record.skipsCycles());
        Job job = new Job(network, record, serverEpoch, QIOWorkbenchDirectoryService.INSTANCE.getEpoch(), input, completion);
        job.startedNanos = preparationStarted;
        job.metrics.add(QIOPlanningMetrics.Metric.MAIN_PREPARATION_NS, System.nanoTime() - preparationStarted);
        job.metrics.cpu(QIOPlanningMetrics.Metric.MAIN_PREPARATION_CPU_NS,
                QIOPlanningMetrics.Metric.MAIN_PREPARATION_CPU_SAMPLES, preparationCpu);
        active.put(record.getId(), job);
        if (mutation.getAction() == Action.ENCODE_PATTERN) {
            List<QIOWorkbenchDeclarations.Stack> grid = new ArrayList<>(9);
            for (net.minecraft.item.ItemStack stack : mutation.getGrid())
                grid.add(QIOWorkbenchDeclarations.Stack.capture(stack, 1));
            job.manualGrid = Collections.unmodifiableList(grid);
            job.record.phase(State.VALIDATING, "");
            job.network.saveWorkbenchEncodingJob(job.record);
            pumpManual(job);
        } else submitRound(job);
        if (current(job)) completion.accept(view(record));
    }

    private void pumpManual(Job job) {
        if (!current(job) || job.waiting || job.manualGrid == null) return;
        QIOWorkbenchValidationService.Admission admission = QIOWorkbenchValidationService.INSTANCE.submit(
                QIOWorkbenchValidationService.Request.manual(job.record.getId(), job.record.getDimension(), job.directoryEpoch, job.manualGrid),
                result -> {
                    if (!current(job)) return;
                    job.waiting = false;
                    job.metrics.validation(result);
                    if (result.getStatus() == QIOWorkbenchValidationService.Status.WORLD_UNAVAILABLE) {
                        waitForWorld(job);
                        return;
                    }
                    if (!result.isValid()) {
                        fail(job, result.getStatus() + ": " + result.getDiagnostic(), Status.INVALID_PATTERN);
                        return;
                    }
                    try {
                        net.minecraft.util.ResourceLocation id = Objects.requireNonNull(result.getRecipeId());
                        String signature = QIOWorkbenchDiscovery.declarationSignature(job.input, id, result.getInputs());
                        // A manual selection is a physical nine-slot grid, including shifted or
                        // mirrored shaped inputs. Do not repack it using a declared layout.
                        QIOVerifiedWorkbenchPattern pattern = QIOVerifiedWorkbenchPattern.concrete(UUID.randomUUID(), id,
                                signature, generation(), QIOWorkbenchDeclarations.Layout.ORDERED_UNKNOWN, result);
                        EncodedPattern root = EncodedPattern.fromVerified(pattern,
                                QIOWorkbenchDiscovery.plannerSignature(job.input, id, signature));
                        job.remember(QIOWorkbenchDiscovery.fixedKey(root), pattern);
                        job.input = QIOWorkbenchDiscovery.manualRoot(job.input, root);
                        job.manualGrid = null;
                        submitRound(job);
                    } catch (RuntimeException error) {
                        fail(job, "Manual encoding failed: " + error.getMessage(), Status.INVALID_PATTERN);
                    }
                });
        job.waiting = admission == QIOWorkbenchValidationService.Admission.ACCEPTED;
        if (admission == QIOWorkbenchValidationService.Admission.STOPPED)
            fail(job, "Validation service stopped", Status.UNAVAILABLE);
    }

    private void submitRound(Job job) {
        if (!current(job)) return;
        QIOPlanningExecutor executor = QIOPlanningService.INSTANCE.getExecutor();
        if (executor == null) {
            fail(job, "Planning executor unavailable", Status.UNAVAILABLE);
            return;
        }
        job.record.phase(State.SEARCHING, "");
        job.network.saveWorkbenchEncodingJob(job.record);
        Map<String, QIOVerifiedWorkbenchPattern> validated = Collections.unmodifiableMap(new LinkedHashMap<>(job.verified));
        Set<String> rejected = Collections.unmodifiableSet(new HashSet<>(job.rejected));
        QIOPlanningExecutor.Submission submission = executor.submit(QIOPlanningExecutor.TaskKind.ENCODING, job.input,
                (input, cancellation) -> QIOWorkbenchDiscovery.discover(input, validated, rejected, cancellation::isCancelled),
                result -> {
                    if (!current(job)) return;
                    job.worker = null;
                    job.metrics.merge(result.getMetrics());
                    job.metrics.add(QIOPlanningMetrics.Metric.WORKER_QUEUE_NS, result.getQueueNanos());
                    job.metrics.add(QIOPlanningMetrics.Metric.WORKER_NS, result.getExecutionNanos());
                    job.metrics.add(QIOPlanningMetrics.Metric.DELIVERY_WAIT_NS, result.getCompletionWaitNanos());
                    if (job.directoryEpoch != QIOWorkbenchDirectoryService.INSTANCE.getEpoch()) {
                        fail(job, "Workbench declaration generation changed", Status.CATALOG_CHANGED);
                        return;
                    }
                    if (result.getStatus() != QIOPlanningExecutor.ResultStatus.SUCCESS || result.getValue() == null) {
                        fail(job, "Recipe discovery did not complete", Status.UNAVAILABLE);
                        return;
                    }
                    job.result = result.getValue();
                    job.diagnostics.keySet().removeIf(key -> key.startsWith("discovery:"));
                    for (int index = 0; index < job.result.getDiagnostics().size(); index++) {
                        job.diagnostics.put("discovery:" + index, job.result.getDiagnostics().get(index));
                    }
                    job.cursor = 0;
                    if (job.result.getRoots().isEmpty()) {
                        fail(job, "No supported and valid recipe for the requested roots. " + job.diagnostics.values(), Status.INVALID_PATTERN);
                    } else if (job.result.getValidationRequests().isEmpty()) {
                        try {
                            ready(job);
                        } catch (RuntimeException error) {
                            fail(job, "Unable to retain draft: " + error.getMessage(), Status.INVALID_PATTERN);
                        }
                    } else {
                        job.record.phase(State.VALIDATING, "");
                        job.network.saveWorkbenchEncodingJob(job.record);
                        pump(job);
                    }
                });
        job.worker = submission.getTaskId();
        if (!submission.isAccepted()) fail(job, "Encoding worker admission rejected", Status.BUSY);
    }

    private void failBeforeLaunch(QIOProcessingNetworkData network, QIOWorkbenchEncodingRecord record,
                                  RuntimeException error, Consumer<PreviewResult> completion) {
        Job job = active.remove(record.getId());
        if (job != null) QIOWorkbenchValidationService.INSTANCE.cancel(record.getId());
        if (network.getWorkbenchEncodingJob(record.getId()) != null && !record.isTerminal()) {
            record.phase(State.FAILED, error.getClass().getSimpleName() + ": " + error.getMessage());
            network.saveWorkbenchEncodingJob(record);
        }
        completion.accept(network.getWorkbenchEncodingJob(record.getId()) == null ?
                new PreviewResult(Status.BUSY, null) : view(record));
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        QIOWorkbenchStoreEncodingJobService.INSTANCE.tick(event);
        if (event.phase != TickEvent.Phase.END) return;
        for (Job job : new ArrayList<>(active.values())) {
            if (!current(job)) continue;
            if (job.directoryEpoch != QIOWorkbenchDirectoryService.INSTANCE.getEpoch()) {
                fail(job, "Workbench generation changed", Status.CATALOG_CHANGED);
            } else if (job.record.getState() == State.WAITING_WORLD) {
                if (DimensionManager.getWorld(job.record.getDimension()) == null) continue;
                job.record.phase(State.VALIDATING, "");
                job.network.saveWorkbenchEncodingJob(job.record);
                if (job.manualGrid != null) pumpManual(job);
                else pump(job);
            } else if (job.record.getState() == State.VALIDATING && !job.waiting) {
                if (job.manualGrid != null) pumpManual(job);
                else pump(job);
            }
        }
    }

    private void waitForWorld(Job job) {
        captureMetrics(job);
        job.record.phase(State.WAITING_WORLD, "WORLD_UNAVAILABLE: waiting for dimension " + job.record.getDimension());
        job.network.saveWorkbenchEncodingJob(job.record);
        notify(job, new PreviewResult(Status.BUSY, progress(job.record)));
    }

    /**
     * Only one outstanding validation per job; callbacks yield/requeue under the shared tick budget.
     */
    private void pump(Job job) {
        if (!current(job) || job.waiting || job.result == null) return;
        if (job.cursor == job.result.getValidationRequests().size()) {
            submitRound(job);
            return;
        }
        if (job.validationCount >= 65_536) {
            fail(job, "Encoding validation budget exhausted", Status.INVALID_PATTERN);
            return;
        }
        QIOWorkbenchDiscovery.Candidate source = job.result.getValidationRequests().get(job.cursor);
        QIOWorkbenchValidationService.Admission admission = QIOWorkbenchValidationService.INSTANCE.submit(
                source.request(job.record.getId(), job.record.getDimension(), job.directoryEpoch),
                result -> {
                    if (!current(job)) return;
                    job.waiting = false;
                    job.metrics.validation(result);
                    if (!result.isValid()) {
                        if (result.getStatus() == QIOWorkbenchValidationService.Status.MEMORY_LIMIT) {
                            fail(job, result.getDiagnostic(), Status.FAILED);
                            return;
                        }
                        if (result.getStatus() == QIOWorkbenchValidationService.Status.WORLD_UNAVAILABLE) {
                            waitForWorld(job);
                            return;
                        }
                        if (result.getStatus() == QIOWorkbenchValidationService.Status.STALE_GENERATION) {
                            fail(job, result.getDiagnostic(), Status.UNAVAILABLE);
                            return;
                        }
                        job.rejected.add(source.getKey());
                        job.diagnostics.put(source.getRecipeId().toString(), result.getStatus() + ": " + result.getDiagnostic());
                        if (result.getStatus() != QIOWorkbenchValidationService.Status.INPUT_MISMATCH &&
                                result.getStatus() != QIOWorkbenchValidationService.Status.OUTPUT_MISMATCH &&
                                result.getStatus() != QIOWorkbenchValidationService.Status.UNSUPPORTED_STATE &&
                                result.getStatus() != QIOWorkbenchValidationService.Status.EMPTY_RESULT) {
                            job.rejected.add("recipe:" + source.getRecipeId());
                        }
                    } else {
                        try {
                            job.remember(source.getKey(), source.verified(generation(), result));
                            job.diagnostics.remove(source.getRecipeId().toString());
                        } catch (RuntimeException error) {
                            fail(job, "Invalid validation result: " + error.getMessage(), Status.INVALID_PATTERN);
                            return;
                        }
                    }
                    job.cursor++;
                    pump(job);
                });
        if (admission == QIOWorkbenchValidationService.Admission.ACCEPTED) job.validationCount++;
        job.waiting = admission == QIOWorkbenchValidationService.Admission.ACCEPTED;
        if (admission == QIOWorkbenchValidationService.Admission.STOPPED) {
            fail(job, "Validation service stopped", Status.UNAVAILABLE);
        }
    }

    private String generation() {
        return QIOWorkbenchDirectoryService.INSTANCE.getDirectory().getGeneration();
    }

    private void ready(Job job) {
        job.metrics.maximum(QIOPlanningMetrics.Metric.TOTAL_NS, System.nanoTime() - job.startedNanos);
        job.record.recordMetrics(job.metrics.snapshot());
        QIOWorkbenchDiscovery.Round result = job.result;
        QIOWorkbenchConfiguration draft = new QIOWorkbenchConfiguration();
        List<EncodedPattern> patterns = new ArrayList<>();
        for (QIOWorkbenchDiscovery.Candidate candidate : result.getSelected()) {
            QIOVerifiedWorkbenchPattern verified = Objects.requireNonNull(job.verified.get(candidate.getKey()), "Unvalidated selected grid");
            patterns.add(EncodedPattern.fromVerified(verified, candidate.getPlannerSignature()));
        }
        draft.putEncodedPatternsIfAbsent(patterns);
        QIOWorkbenchClosurePreview preview = new QIOWorkbenchClosurePreview(job.record.getId(),
                result.getRoots().size(), result.getNewProductCount(), result.getNewRecipeCount(),
                result.getExistingRecipeCount(), result.getLeafMaterialCount(), result.getCycleCount(),
                result.getSkippedCyclicRecipeCount(), result.isTruncated(), result.getCyclePaths()).withDiagnostic(String.join("; ", job.diagnostics.values()))
                .withMetrics(job.record.getMetrics());
        String root = job.record.getRequest().getString("action").equals(Action.ENCODE_PATTERN.name()) ?
                result.getRoots().iterator().next() : null;
        job.record.ready(draft, preview, root, generation());
        job.network.saveWorkbenchEncodingJob(job.record);
        validatedEpochs.put(job.record.getId(), job.directoryEpoch);
        active.remove(job.record.getId());
        notify(job, new PreviewResult(Status.READY, preview));
        Mekanism.logger.debug("[QIO Workbench] Encoding {} metrics: {}", job.record.getId(), job.record.getMetrics());
    }

    private boolean current(Job job) {
        return job.serverEpoch == serverEpoch && active.get(job.record.getId()) == job && !job.record.isTerminal();
    }

    private void fail(Job job, String diagnostic, Status status) {
        if (!current(job)) return;
        captureMetrics(job);
        if (job.worker != null && QIOPlanningService.INSTANCE.getExecutor() != null) {
            QIOPlanningService.INSTANCE.getExecutor().cancel(job.worker);
        }
        QIOWorkbenchValidationService.INSTANCE.cancel(job.record.getId());
        job.record.phase(State.FAILED, diagnostic);
        job.network.saveWorkbenchEncodingJob(job.record);
        active.remove(job.record.getId());
        notify(job, new PreviewResult(status == Status.BUSY ? status : Status.FAILED, progress(job.record)));
    }

    private void notify(Job job, PreviewResult result) {
        try {
            job.completion.accept(result);
        } catch (RuntimeException error) {
            Mekanism.logger.warn("Workbench result notification failed; draft remains queryable", error);
        }
    }

    public PreviewResult query(QIOWorkbenchConfigurationService.Context context, QIOProcessingTerminalSession session,
                               UUID owner, @Nullable UUID id) {
        if (QIOWorkbenchStoreEncodingJobService.INSTANCE.owns(context, session, owner, id)) {
            return QIOWorkbenchStoreEncodingJobService.INSTANCE.query(context, session, owner, id);
        }
        if (!context.isEditable() || !session.getPlayerUUID().equals(owner))
            return new PreviewResult(Status.ACCESS_DENIED, null);
        QIOWorkbenchEncodingRecord selected = null;
        for (QIOWorkbenchEncodingRecord record : context.getNetwork().getWorkbenchEncodingJobs()) {
            if (!record.getOwner().equals(owner) ||
                    !record.getConfiguration().equals(context.getNetwork().getWorkbenchConfiguration().getConfigUUID()))
                continue;
            if (id != null ? id.equals(record.getId()) : !record.isTerminal() || record.getState() == State.FAILED) {
                if (selected == null || record.getCreated() >= selected.getCreated()) selected = record;
            }
        }
        if (selected != null && selected.getState() == State.READY &&
                (!Objects.equals(validatedEpochs.get(selected.getId()), QIOWorkbenchDirectoryService.INSTANCE.getEpoch()) ||
                        selected.getConfigurationRevision() != context.getNetwork().getWorkbenchConfiguration().getRevision() ||
                        selected.getCatalogRevision() != context.getCatalogRevision())) {
            selected.phase(State.PAUSED_RESTART, "Revalidation required");
            context.getNetwork().saveWorkbenchEncodingJob(selected);
        }
        return selected == null ? new PreviewResult(Status.EXPIRED, null) : view(selected);
    }

    private PreviewResult view(QIOWorkbenchEncodingRecord record) {
        Job running = active.get(record.getId());
        if (running != null) captureMetrics(running);
        return switch (record.getState()) {
            case READY -> new PreviewResult(Status.READY, record.getPreview());
            case CONFIRMED -> new PreviewResult(record.wasApplied() ? Status.APPLIED : Status.UNCHANGED, null);
            case CANCELLED -> new PreviewResult(Status.CANCELLED, progress(record));
            case FAILED -> new PreviewResult(Status.FAILED, progress(record));
            case PAUSED_RESTART -> new PreviewResult(Status.PAUSED_RESTART, progress(record));
            default -> new PreviewResult(Status.BUSY, progress(record));
        };
    }

    private QIOWorkbenchClosurePreview progress(QIOWorkbenchEncodingRecord record) {
        return (record.getPreview() == null ? new QIOWorkbenchClosurePreview(record.getId(), 0, 0, 0, 0, 0, 0, 0,
                false, Collections.emptyList()) : record.getPreview()).withDiagnostic(record.getDiagnostic()).withMetrics(record.getMetrics());
    }

    public PreviewResult cancel(QIOWorkbenchConfigurationService.Context context, QIOProcessingTerminalSession session,
                                UUID owner, UUID id) {
        if (QIOWorkbenchStoreEncodingJobService.INSTANCE.owns(context, session, owner, id)) {
            return QIOWorkbenchStoreEncodingJobService.INSTANCE.cancel(context, session, owner, id);
        }
        QIOWorkbenchEncodingRecord record = owned(context, session, owner, id);
        if (record == null) return new PreviewResult(Status.ACCESS_DENIED, null);
        if (record.getState() == State.CONFIRMED || record.getState() == State.CANCELLED) return view(record);
        Job job = active.remove(id);
        if (job != null) captureMetrics(job);
        if (job != null && job.worker != null && QIOPlanningService.INSTANCE.getExecutor() != null) {
            QIOPlanningService.INSTANCE.getExecutor().cancel(job.worker);
        }
        QIOWorkbenchValidationService.INSTANCE.cancel(id);
        validatedEpochs.remove(id);
        record.cancel();
        context.getNetwork().saveWorkbenchEncodingJob(record);
        return view(record);
    }

    public void resume(QIOWorkbenchConfigurationService.Context context, QIOProcessingTerminalSession session,
                       UUID owner, UUID id, World world, Consumer<PreviewResult> completion) {
        if (QIOWorkbenchStoreEncodingJobService.INSTANCE.owns(context, session, owner, id)) {
            QIOWorkbenchStoreEncodingJobService.INSTANCE.resume(context, session, owner, id, world, completion);
            return;
        }
        QIOWorkbenchDirectoryService.requireServerThread(world);
        QIOWorkbenchEncodingRecord previous = owned(context, session, owner, id);
        if (previous == null) {
            completion.accept(new PreviewResult(Status.ACCESS_DENIED, null));
            return;
        }
        if (previous.getState() == State.CONFIRMED || previous.getState() == State.CANCELLED ||
                active.containsKey(id)) {
            completion.accept(view(previous));
            return;
        }
        QIOProcessingNetworkData network = context.getNetwork();
        QIOWorkbenchConfiguration configuration = network.getWorkbenchConfiguration();
        Status rejected = admission(context, session, owner, configuration.getRevision(), context.getCatalogRevision());
        if (rejected != null) {
            completion.accept(new PreviewResult(rejected, null));
            return;
        }
        QIOWorkbenchEncodingRecord replacement = null;
        try {
            QIOWorkbenchConfigurationMutation mutation = QIOWorkbenchConfigurationMutation.read(previous.getRequest());
            QIOWorkbenchEncodingRecord record = new QIOWorkbenchEncodingRecord(id, owner, configuration.getConfigUUID(),
                    configuration.getRevision(), context.getCatalogRevision(), world.provider.getDimension(),
                    previous.getRequest(), previous.getMode(), previous.skipsCycles());
            replacement = record;
            validatedEpochs.remove(id);
            network.saveWorkbenchEncodingJob(record);
            launch(network, record, mutation, world, completion);
        } catch (Exception error) {
            if (replacement == null) completion.accept(new PreviewResult(Status.INVALID_PATTERN, null));
            else failBeforeLaunch(network, replacement, error instanceof RuntimeException ? (RuntimeException) error :
                    new IllegalArgumentException(error), completion);
        }
    }

    @Nullable
    private QIOWorkbenchEncodingRecord owned(QIOWorkbenchConfigurationService.Context context,
                                             QIOProcessingTerminalSession session, UUID owner, UUID id) {
        if (!context.isEditable() || !session.getPlayerUUID().equals(owner)) return null;
        QIOWorkbenchEncodingRecord record = context.getNetwork().getWorkbenchEncodingJob(id);
        return record != null && record.getOwner().equals(owner) &&
                record.getConfiguration().equals(context.getNetwork().getWorkbenchConfiguration().getConfigUUID()) ? record : null;
    }

    public ConfirmResult confirm(QIOWorkbenchConfigurationService.Context context, QIOProcessingTerminalSession session,
                                 UUID owner, UUID id, World world) {
        if (QIOWorkbenchStoreEncodingJobService.INSTANCE.owns(context, session, owner, id)) {
            return QIOWorkbenchStoreEncodingJobService.INSTANCE.confirm(context, session, owner, id, world);
        }
        QIOWorkbenchDirectoryService.requireServerThread(world);
        QIOWorkbenchEncodingRecord record = owned(context, session, owner, id);
        if (record == null) return new ConfirmResult(Status.ACCESS_DENIED);
        if (record.getState() == State.CONFIRMED)
            return new ConfirmResult(record.wasApplied() ? Status.APPLIED : Status.UNCHANGED);
        if (record.getState() != State.READY) return new ConfirmResult(
                record.getState() == State.PAUSED_RESTART ? Status.PAUSED_RESTART : Status.BUSY);
        if (!Objects.equals(validatedEpochs.get(id), QIOWorkbenchDirectoryService.INSTANCE.getEpoch())) {
            record.phase(State.PAUSED_RESTART, "Revalidation required");
            context.getNetwork().saveWorkbenchEncodingJob(record);
            return new ConfirmResult(Status.PAUSED_RESTART);
        }
        QIOProcessingNetworkData network = context.getNetwork();
        QIOWorkbenchConfiguration configuration = network.getWorkbenchConfiguration();
        if (record.getConfigurationRevision() != configuration.getRevision()) {
            record.phase(State.PAUSED_RESTART, "Configuration changed; recompute before import");
            network.saveWorkbenchEncodingJob(record);
            return new ConfirmResult(Status.TARGET_CHANGED);
        }
        if (record.getCatalogRevision() != context.getCatalogRevision() || !record.getGeneration().equals(generation())) {
            record.phase(State.PAUSED_RESTART, "Catalog changed; recompute before import");
            network.saveWorkbenchEncodingJob(record);
            return new ConfirmResult(Status.CATALOG_CHANGED);
        }
        QIOWorkbenchConfiguration draft = Objects.requireNonNull(record.getDraft(), "draft");
        EncodedPattern replacing = record.getReplacingRoot() == null ? null : draft.getEncodedPattern(record.getReplacingRoot());
        long before = configuration.getRevision();
        try {
            boolean changed = configuration.applyEncodedClosure(replacing, draft.getEncodedPatterns());
            for (EncodedPattern pattern : draft.getEncodedPatterns()) {
                if (pattern.getVerified() != null)
                    QIORecipeCatalogService.INSTANCE.recordVerified(pattern.getVerified(), record.getDimension());
            }
            // Record the receipt before any optional catalog invalidation can fail.
            record.confirmed(changed);
            network.saveWorkbenchEncodingJob(record);
            if (changed && replacing != null)
                QIOWorkbenchConfigurationService.preferIndexedCandidates(configuration, replacing, record.getDimension());
            network.markWorkbenchConfigurationChanged(before);
            validatedEpochs.remove(id);
            if (changed) QIORecipeCatalogService.INSTANCE.encodedPatternsAdded(draft.getEncodedPatterns());
            return new ConfirmResult(changed ? Status.APPLIED : Status.UNCHANGED);
        } catch (RuntimeException error) {
            if (record.getState() == State.CONFIRMED) {
                network.markWorkbenchConfigurationChanged(before);
                Mekanism.logger.error("Workbench import committed; catalog invalidation failed", error);
                return new ConfirmResult(record.wasApplied() ? Status.APPLIED : Status.UNCHANGED);
            }
            return new ConfirmResult(Status.INVALID_PATTERN);
        }
    }

    public void shutdown() {
        QIOWorkbenchStoreEncodingJobService.INSTANCE.shutdown();
        serverEpoch++;
        for (Job job : active.values()) {
            captureMetrics(job);
            QIOWorkbenchValidationService.INSTANCE.cancel(job.record.getId());
            if (job.worker != null && QIOPlanningService.INSTANCE.getExecutor() != null) {
                QIOPlanningService.INSTANCE.getExecutor().cancel(job.worker);
            }
            if (!job.record.isTerminal()) {
                job.record.phase(State.PAUSED_RESTART, "Server stopped");
                job.network.saveWorkbenchEncodingJob(job.record);
            }
        }
        active.clear();
        validatedEpochs.clear();
    }

    private static final class Job {
        long startedNanos = System.nanoTime();
        final QIOPlanningMetrics metrics = new QIOPlanningMetrics();
        final QIOProcessingNetworkData network;
        final QIOWorkbenchEncodingRecord record;
        final long serverEpoch;
        final long directoryEpoch;
        final Consumer<PreviewResult> completion;
        QIOWorkbenchDiscovery.Input input;
        @Nullable
        List<QIOWorkbenchDeclarations.Stack> manualGrid;
        final Map<String, QIOVerifiedWorkbenchPattern> verified = new LinkedHashMap<>();
        long verifiedBytes;

        void remember(String key, QIOVerifiedWorkbenchPattern pattern) {
            QIOVerifiedWorkbenchPattern old = verified.get(key);
            long next = verifiedBytes + pattern.getRetainedBytes() - (old == null ? 0 : old.getRetainedBytes());
            if (next > mekanism.qioprocessing.common.planning.QIOWorkbenchMemory.TASK_LIMIT) {
                throw new mekanism.qioprocessing.common.planning.QIOWorkbenchMemory.LimitException("WORKBENCH_ENCODING_KNOWLEDGE_MEMORY_LIMIT");
            }
            verified.put(key, pattern);
            verifiedBytes = next;
        }

        final Set<String> rejected = new HashSet<>();
        final Map<String, String> diagnostics = new LinkedHashMap<>();
        int validationCount;
        @Nullable
        UUID worker;
        @Nullable
        QIOWorkbenchDiscovery.Round result;
        int cursor;
        boolean waiting;

        Job(QIOProcessingNetworkData network, QIOWorkbenchEncodingRecord record, long serverEpoch,
            long directoryEpoch, QIOWorkbenchDiscovery.Input input, Consumer<PreviewResult> completion) {
            this.network = network;
            this.record = record;
            this.serverEpoch = serverEpoch;
            this.directoryEpoch = directoryEpoch;
            this.input = input;
            this.completion = completion;
        }
    }

    /**
     * A partial worker snapshot is never merged back into the live accumulator.
     */
    private void captureMetrics(Job job) {
        QIOPlanningMetrics copy = new QIOPlanningMetrics();
        copy.merge(job.metrics.snapshot());
        QIOPlanningExecutor executor = QIOPlanningService.INSTANCE.getExecutor();
        if (job.worker != null && executor != null) copy.merge(executor.snapshotMetrics(job.worker));
        copy.maximum(QIOPlanningMetrics.Metric.TOTAL_NS, System.nanoTime() - job.startedNanos);
        job.record.recordMetrics(copy.snapshot());
    }
}
