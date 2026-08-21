package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/** Bounded result published by a pure-data QIO planning worker. */
/**
 * QIO 处理模块中的 QIOPlanningResult 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPlanningResult {

    public enum Status {
        SUCCESS,
        NO_ROUTE,
        CYCLE_REQUIRES_SCC,
        CYCLE_NO_SEED,
        UNRESOLVABLE_CYCLE,
        TOO_COMPLEX,
        AMOUNT_OVERFLOW,
        CANCELLED,
        INTERNAL_ERROR
    }

    private final Status status;
    @Nullable
    private final QIOCraftPlan plan;
    private final QIOPlanningTrace trace;
    private final String diagnostic;
    private final int exploredNodes;
    private final long plannedOperations;

    private QIOPlanningResult(Status status, @Nullable QIOCraftPlan plan,
          @Nonnull QIOPlanningTrace trace, String diagnostic, int exploredNodes,
          long plannedOperations) {
        this.status = Objects.requireNonNull(status, "status");
        this.plan = plan;
        this.trace = Objects.requireNonNull(trace, "trace");
        this.diagnostic = diagnostic == null ? "" : diagnostic;
        this.exploredNodes = Math.max(0, exploredNodes);
        this.plannedOperations = Math.max(0, plannedOperations);
        if ((status == Status.SUCCESS) != (plan != null)) {
            throw new IllegalArgumentException("QIO planning success must contain exactly one plan");
        }
    }

    /** 创建成功规划结果。 */
    @Nonnull
    public static QIOPlanningResult success(@Nonnull QIOCraftPlan plan, int exploredNodes,
          long plannedOperations) {
        return new QIOPlanningResult(Status.SUCCESS, Objects.requireNonNull(plan, "plan"),
              QIOPlanningTrace.empty(), "", exploredNodes, plannedOperations);
    }

    /** 创建带诊断和探索统计的失败结果。 */
    @Nonnull
    public static QIOPlanningResult failure(@Nonnull Status status, String diagnostic,
          int exploredNodes, long plannedOperations) {
        return failure(status, diagnostic, exploredNodes, plannedOperations,
              QIOPlanningTrace.empty());
    }

    /** 创建带默认空 trace 的失败结果。 */
    @Nonnull
    public static QIOPlanningResult failure(@Nonnull Status status, String diagnostic,
          int exploredNodes, long plannedOperations, @Nonnull QIOPlanningTrace trace) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("Use success for a successful QIO planning result");
        }
        return new QIOPlanningResult(status, null, trace, diagnostic, exploredNodes,
              plannedOperations);
    }

    @Nonnull
    /** 返回规划状态。 */
    public Status getStatus() {
        return status;
    }

    @Nullable
    /** 返回成功时的合成计划；失败时为 null。 */
    public QIOCraftPlan getPlan() {
        return plan;
    }

    @Nonnull
    /** 返回规划追踪信息。 */
    public QIOPlanningTrace getTrace() {
        return trace;
    }

    @Nonnull
    /** 返回失败诊断文本。 */
    public String getDiagnostic() {
        return diagnostic;
    }

    /** 返回探索过的节点数。 */
    public int getExploredNodes() {
        return exploredNodes;
    }

    /** 返回计划批次数。 */
    public long getPlannedOperations() {
        return plannedOperations;
    }
}
