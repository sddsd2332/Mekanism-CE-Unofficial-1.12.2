package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.common.planning.QIOPlanningExecutor.CancellationToken;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Runs the fast acyclic planner first and invokes the bounded seeded-cycle stage only when needed. */
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
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        QIOPlanningResult first = QIOAcyclicPlanner.INSTANCE.plan(request, cancellationToken);
        if (first.getStatus() != QIOPlanningResult.Status.CYCLE_REQUIRES_SCC) {
            return first;
        }
        return QIOSeededCyclePlanner.INSTANCE.plan(request, cancellationToken);
    }
}
