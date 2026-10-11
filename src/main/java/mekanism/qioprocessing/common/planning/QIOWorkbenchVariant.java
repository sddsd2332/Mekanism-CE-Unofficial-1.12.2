package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep.ProviderKind;
import mekanism.qioprocessing.common.planning.QIOWorkbenchDeclarations.Stack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * One detached concrete candidate and its process-local validation knowledge. A speculative route
 * is allowed only inside the pure solver: QIOPlanner must validate its selected variants before
 * returning a result. It cannot be serialized or bound to a production pattern while unresolved.
 */
final class QIOWorkbenchVariant {
    private final ResourceLocation recipe;
    private final String variantId;
    private final List<Stack> grid;
    private final boolean[] virtual;
    private final Map<PortableResourceDescriptor, Long> inputs;
    private final PortableResourceDescriptor primary;
    private final int primaryAmount;
    private final Map<PortableResourceDescriptor, Long> proposedOutputs;
    private final int dimension;
    private final long epoch;
    private final long serverEpoch;
    @Nullable
    private volatile QIOWorkbenchValidationService.Result result;
    @Nullable
    private QIOPlanningRoute cachedRoute;
    @Nullable
    private final Map<PortableResourceDescriptor, Long> trustedOutputs;
    private final java.util.concurrent.atomic.AtomicBoolean executionPending = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean executionRequested;
    private final Runnable changed;
    private final boolean sequential;
    private final long baseBytes;
    private final List<java.lang.ref.WeakReference<QIOWorkbenchKnowledge.TaskBudget>> budgets = new ArrayList<>();

    QIOWorkbenchVariant(ResourceLocation recipe, String variantId, List<Stack> grid, boolean[] virtual,
                        Map<PortableResourceDescriptor, Long> inputs, PortableResourceDescriptor primary, int primaryAmount,
                        Map<PortableResourceDescriptor, Long> proposedOutputs, int dimension, long epoch,
                        @Nullable Map<PortableResourceDescriptor, Long> trustedOutputs, boolean sequential, Runnable changed) {
        this.recipe = recipe;
        this.changed = changed;
        this.sequential = sequential;
        this.variantId = variantId;
        this.grid = Collections.unmodifiableList(new ArrayList<>(grid));
        this.virtual = virtual.clone();
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        this.primary = primary;
        this.primaryAmount = primaryAmount;
        this.proposedOutputs = Collections.unmodifiableMap(new LinkedHashMap<>(proposedOutputs));
        this.dimension = dimension;
        this.epoch = epoch;
        this.serverEpoch = QIOWorkbenchValidationService.INSTANCE.getServerEpoch();
        this.trustedOutputs = trustedOutputs == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(trustedOutputs));
        baseBytes = 512 + QIOWorkbenchMemory.text(variantId) + QIOWorkbenchMemory.stacks(this.grid) +
                QIOWorkbenchMemory.resources(this.inputs) + QIOWorkbenchMemory.resources(this.proposedOutputs) +
                QIOWorkbenchMemory.resource(primary) + (trustedOutputs == null ? 0 : QIOWorkbenchMemory.resources(trustedOutputs));
    }

    long getRetainedBytes() {
        QIOWorkbenchValidationService.Result current = result;
        return baseBytes + (current == null ? 0 : current.getRetainedBytes());
    }

    long getMaximumRetainedBytes() {
        return trustedOutputs != null ? baseBytes : resolved() ? getRetainedBytes() : baseBytes + QIOWorkbenchMemory.RESULT_LIMIT;
    }

    synchronized void retain(QIOWorkbenchKnowledge.TaskBudget budget) {
        registerBudget(budget);
        budget.update(this);
        budget.check();
    }

    synchronized boolean tryRetain(QIOWorkbenchKnowledge.TaskBudget budget, long reservedBytes) {
        if (!budget.tryUpdate(this, reservedBytes)) return false;
        registerBudget(budget);
        return true;
    }

    private void registerBudget(QIOWorkbenchKnowledge.TaskBudget budget) {
        boolean registered = false;
        for (Iterator<java.lang.ref.WeakReference<QIOWorkbenchKnowledge.TaskBudget>> it = budgets.iterator(); it.hasNext(); ) {
            QIOWorkbenchKnowledge.TaskBudget existing = it.next().get();
            if (existing == null) it.remove();
            else if (existing == budget) registered = true;
        }
        if (!registered) budgets.add(new java.lang.ref.WeakReference<>(budget));
    }

    boolean resolved() {
        return trustedOutputs != null || result != null;
    }

    boolean sequential() {
        QIOWorkbenchValidationService.Result current = result;
        return sequential || current != null && current.isValid() &&
                QIOWorkbenchStateInputs.changesState(current.getInputs(), current.getRemainders(), virtual);
    }

    boolean valid() {
        return trustedOutputs != null || result != null && result.isValid() && inputs.equals(result.getConsumedInputs());
    }

    boolean executionPending() {
        return executionRequested && !resolved() && currentEpoch();
    }

    @Nullable
    synchronized QIOPlanningRoute route(long priority, long variantPriority) {
        if (resolved() && !valid()) return null;
        if (cachedRoute != null) return cachedRoute.withPriority(priority).withVariantPriority(variantPriority);
        Map<PortableResourceDescriptor, Long> outputs = trustedOutputs != null ? trustedOutputs :
                result == null ? proposedOutputs : result.getOutputs();
        QIOPlanningRoute.Builder builder = QIOPlanningRoute.builder(ProviderKind.WORKBENCH,
                        QIOWorkbenchRecipeCatalog.PROVIDER_ID, recipe.toString(), recipe.toString()).variantId(variantId)
                .priority(priority).variantPriority(variantPriority).verification(this);
        inputs.forEach(builder::input);
        outputs.forEach(builder::output);
        cachedRoute = builder.build();
        return cachedRoute;
    }

    @Nullable
    QIOWorkbenchRecipePattern execution() {
        if (!currentEpoch()) return null;
        executionRequested = true;
        if (!resolved() && executionPending.compareAndSet(false, true)) {
            QIOWorkbenchValidationService.Admission admission = QIOWorkbenchValidationService.INSTANCE.submit(request(UUID.randomUUID()), outcome -> {
                if (currentEpoch() && outcome.getStatus() != QIOWorkbenchValidationService.Status.WORLD_UNAVAILABLE &&
                        outcome.getStatus() != QIOWorkbenchValidationService.Status.STALE_GENERATION) complete(outcome);
                executionPending.set(false);
            });
            if (admission != QIOWorkbenchValidationService.Admission.ACCEPTED) executionPending.set(false);
        }
        QIOPlanningRoute route = resolved() && valid() ? route(0, 0) : null;
        return route == null ? null : QIOWorkbenchRecipePattern.frozen(route, recipe, grid, virtual);
    }

    private QIOWorkbenchValidationService.Request request(UUID owner) {
        return new QIOWorkbenchValidationService.Request(owner, recipe, dimension, epoch, grid, virtual, primary, primaryAmount);
    }

    boolean currentEpoch() {
        return epoch == QIOWorkbenchDirectoryService.INSTANCE.getEpoch() &&
                QIOWorkbenchValidationService.INSTANCE.isCurrentServer(serverEpoch);
    }

    static QIOPlanningContinuation validation(Collection<QIOWorkbenchVariant> values) {
        List<QIOWorkbenchVariant> pending = new ArrayList<>(new LinkedHashSet<>(values));
        QIOWorkbenchKnowledge.TaskBudget budget = QIOWorkbenchKnowledge.taskBudget();
        QIOPlanningMetrics metrics = QIOPlanningMetrics.current();
        for (QIOWorkbenchVariant value : pending) value.retain(budget);
        return new QIOPlanningContinuation("Selected workbench variants require server validation") {
            private int submitted;
            private int completed;
            private volatile boolean cancelled;
            private Runnable resume;
            private Consumer<Throwable> failure;

            @Override
            public boolean submit(UUID taskId, Runnable resume, Consumer<Throwable> failure) {
                this.resume = resume;
                this.failure = failure;
                if (cancelled) return true;
                while (submitted < pending.size()) {
                    if (cancelled) return true;
                    QIOWorkbenchVariant variant = pending.get(submitted);
                    if (!variant.currentEpoch()) {
                        cancelled = true;
                        QIOWorkbenchValidationService.INSTANCE.cancel(taskId);
                        failure.accept(new QIOPlanningContextException(QIOPlanningResult.Status.VERSION_CHANGED,
                                "Workbench validation context expired"));
                        return true;
                    }
                    if (variant.resolved()) {
                        submitted++;
                        completed++;
                        continue;
                    }
                    QIOWorkbenchValidationService.Admission admission = QIOWorkbenchValidationService.INSTANCE.submit(
                            variant.request(taskId), result -> {
                                if (cancelled) return;
                                metrics.validation(result);
                                if (result.getStatus() == QIOWorkbenchValidationService.Status.MEMORY_LIMIT) {
                                    cancelled = true;
                                    QIOWorkbenchValidationService.INSTANCE.cancel(taskId);
                                    this.failure.accept(new QIOPlanningLimitException(result.getDiagnostic()));
                                    return;
                                }
                                if (result.getStatus() == QIOWorkbenchValidationService.Status.STALE_GENERATION ||
                                        result.getStatus() == QIOWorkbenchValidationService.Status.WORLD_UNAVAILABLE) {
                                    cancelled = true;
                                    QIOWorkbenchValidationService.INSTANCE.cancel(taskId);
                                    this.failure.accept(new QIOPlanningContextException(
                                            result.getStatus() == QIOWorkbenchValidationService.Status.WORLD_UNAVAILABLE ?
                                                    QIOPlanningResult.Status.WORLD_UNAVAILABLE : QIOPlanningResult.Status.VERSION_CHANGED,
                                            result.getDiagnostic()));
                                    return;
                                }
                                variant.complete(result);
                                try {
                                    budget.check();
                                } catch (QIOPlanningLimitException limit) {
                                    cancelled = true;
                                    QIOWorkbenchValidationService.INSTANCE.cancel(taskId);
                                    this.failure.accept(limit);
                                    return;
                                }
                                completed++;
                                if (completed == pending.size()) this.resume.run();
                            });
                    if (cancelled) {
                        QIOWorkbenchValidationService.INSTANCE.cancel(taskId);
                        return true;
                    }
                    if (admission == QIOWorkbenchValidationService.Admission.BUSY) return false;
                    if (admission == QIOWorkbenchValidationService.Admission.STOPPED) {
                        cancelled = true;
                        failure.accept(new IllegalStateException("Workbench validation service stopped"));
                        return true;
                    }
                    submitted++;
                }
                if (completed == pending.size()) resume.run();
                return true;
            }

            @Override
            public void cancel(UUID taskId) {
                cancelled = true;
                QIOWorkbenchValidationService.INSTANCE.cancel(taskId);
            }
        };
    }

    private void complete(QIOWorkbenchValidationService.Result outcome) {
        synchronized (this) {
            if (result != null) return;
            result = outcome;
            cachedRoute = null;
            for (Iterator<java.lang.ref.WeakReference<QIOWorkbenchKnowledge.TaskBudget>> it = budgets.iterator(); it.hasNext(); ) {
                QIOWorkbenchKnowledge.TaskBudget budget = it.next().get();
                if (budget == null) it.remove();
                else budget.update(this);
            }
        }
        changed.run();
    }
}
