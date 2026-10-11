package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.common.planning.QIOPlanningExecutor.CancellationToken;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Runs the compiled acyclic accelerator or the resumable demand/inventory simulator.
 */

/**
 * QIO 处理模块中的 QIOPlanner 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPlanner implements
        QIOPlanningExecutor.PlanningTask<QIOPlanningRequest, QIOPlanningResult> {

    public static final QIOPlanner INSTANCE = new QIOPlanner();

    private QIOPlanner() {
    }

    @Nonnull
    @Override
    /** 选择合适的规划算法并返回同步规划结果。 */
    public QIOPlanningResult plan(@Nonnull QIOPlanningRequest request,
                                  @Nonnull CancellationToken cancellationToken) {
        try (QIOPlanningMetrics.Scope timing = QIOPlanningMetrics.current().measure(QIOPlanningMetrics.Phase.SOLVE)) {
            return planNow(request, cancellationToken);
        } catch (QIOPlanningVersionException changed) {
            return QIOPlanningResult.failure(QIOPlanningResult.Status.VERSION_CHANGED, changed.getMessage(), 0, 0,
                    QIOPlanningTrace.rootFailure(request.getRootResource()));
        }
    }

    private QIOPlanningResult planNow(QIOPlanningRequest request, CancellationToken cancellationToken) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (cancellationToken.isCancelled() || Thread.currentThread().isInterrupted()) {
            return QIOPlanningResult.failure(QIOPlanningResult.Status.CANCELLED, "QIO planning cancelled", 0, 0,
                    QIOPlanningTrace.rootFailure(request.getRootResource()));
        }
        try {
            QIOWorkbenchKnowledge.taskBudget().check();
        } catch (QIOPlanningLimitException limit) {
            return QIOPlanningResult.failure(QIOPlanningResult.Status.TOO_COMPLEX, limit.getMessage(), 0, 0);
        }
        QIOPlanningSnapshot original = request.getSnapshot();
        java.util.List<QIOPlanningRoute> refreshed = new java.util.ArrayList<>();
        boolean hasVerification = false;
        for (QIOPlanningRoute route : original.getRoutes()) {
            hasVerification |= route.verification() != null;
            QIOPlanningRoute current = route.refreshedVerification();
            if (current != null) refreshed.add(current);
        }
        if (hasVerification) request = new QIOPlanningRequest(request.getPlanId(), request.getPlanRevision(),
                new QIOPlanningSnapshot(original.getSourceRevisions(), original.getAvailableResources(), refreshed,
                        original.getMaximumDepth(), original.getMaximumNodes(), original.getMaximumOperations()),
                request.getRootResource(), request.getRootAmount());
        // Validate the one demand-selected representative of each reachable declaration as a
        // batch. No Cartesian variants are expanded, and a deep chain need not wait one tick
        // per node before its first solve. Further stock-dependent grids still yield locally.
        java.util.List<QIOWorkbenchVariant> initial = new java.util.ArrayList<>();
        for (QIOPlanningRoute route : refreshed) {
            if (route.verification() != null && !route.verification().resolved()) initial.add(route.verification());
        }
        if (!initial.isEmpty()) throw QIOWorkbenchVariant.validation(initial);
        QIOPlanningResult result;
        QIOPlanningResult[] incumbent = new QIOPlanningResult[1];
        try {
            result = QIOTopologicalPlanner.INSTANCE.tryPlan(request, cancellationToken, value -> incumbent[0] = value);
        } catch (QIOPlanningLimitException limit) {
            // Structural expansion may exceed the accelerator's budget even when stock
            // satisfies most of the actual demand. Let the bounded demand walk decide.
            result = null;
        }
        if (result == null) result = QIODemandPlanner.INSTANCE.plan(request, cancellationToken, incumbent[0]);
        // Disabled permanently in this entry: QIOSeededCyclePlanner.INSTANCE.plan(...).
        // Recursive requests use the demand simulator's inventory/seed boundary instead.
        if (hasVerification && result.getStatus() != QIOPlanningResult.Status.CANCELLED) {
            java.util.Set<String> selected = new java.util.HashSet<>();
            if (result.getPlan() != null)
                result.getPlan().getSteps().forEach(step -> selected.add(step.getStableRouteId()));
            java.util.List<QIOWorkbenchVariant> pending = new java.util.ArrayList<>();
            for (QIOPlanningRoute route : refreshed) {
                QIOWorkbenchVariant variant = route.verification();
                if (variant != null && !variant.resolved() && selected.contains(route.getStableId())) {
                    pending.add(variant);
                }
            }
            // No speculative plan can leave this method. All callers use the common executor,
            // which retains admission and resumes the pure task after the batch completes.
            if (!pending.isEmpty()) throw QIOWorkbenchVariant.validation(pending);
        }
        QIOPlanningMetrics.current().maximum(QIOPlanningMetrics.Metric.PEAK_EXPLORED_NODES, result.getExploredNodes());
        QIOPlanningMetrics.current().maximum(QIOPlanningMetrics.Metric.PEAK_OPERATIONS, result.getPlannedOperations());
        if (result.getPlan() != null) {
            QIOPlanningMetrics metrics = QIOPlanningMetrics.current();
            java.util.Set<String> variants = new java.util.HashSet<>();
            long dependencies = 0;
            for (mekanism.qioprocessing.common.content.plan.QIOPlanStep step : result.getPlan().getSteps()) {
                variants.add(step.getStableRouteId());
                dependencies += step.getDependencies().size();
            }
            metrics.maximum(QIOPlanningMetrics.Metric.PEAK_PLAN_STEPS, result.getPlan().getSteps().size());
            metrics.maximum(QIOPlanningMetrics.Metric.PEAK_DEPENDENCIES, dependencies);
            metrics.maximum(QIOPlanningMetrics.Metric.PEAK_USED_VARIANTS, variants.size());
            metrics.maximum(QIOPlanningMetrics.Metric.PEAK_CYCLE_NODES, result.getPlan().getCycleNodes().size());
        }
        return result;
    }

}
