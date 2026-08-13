package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.common.planning.QIOPlanningExecutor.CancellationToken;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Runs the fast acyclic planner first and invokes the bounded seeded-cycle stage only when needed. */
public final class QIOPlanner implements
      QIOPlanningExecutor.PlanningTask<QIOPlanningRequest, QIOPlanningResult> {

    public static final QIOPlanner INSTANCE = new QIOPlanner();

    private QIOPlanner() {
    }

    @Nonnull
    @Override
    public QIOPlanningResult plan(@Nonnull QIOPlanningRequest request,
          @Nonnull CancellationToken cancellationToken) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        QIOPlanningResult first = QIOAcyclicPlanner.INSTANCE.plan(request, cancellationToken);
        if (first.getStatus() != QIOPlanningResult.Status.CYCLE_REQUIRES_SCC) {
            return first;
        }
        return QIOSeededCyclePlanner.INSTANCE.plan(request, cancellationToken);
    }
}
