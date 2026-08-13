package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/** Bounded result published by a pure-data QIO planning worker. */
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

    @Nonnull
    public static QIOPlanningResult success(@Nonnull QIOCraftPlan plan, int exploredNodes,
          long plannedOperations) {
        return new QIOPlanningResult(Status.SUCCESS, Objects.requireNonNull(plan, "plan"),
              QIOPlanningTrace.empty(), "", exploredNodes, plannedOperations);
    }

    @Nonnull
    public static QIOPlanningResult failure(@Nonnull Status status, String diagnostic,
          int exploredNodes, long plannedOperations) {
        return failure(status, diagnostic, exploredNodes, plannedOperations,
              QIOPlanningTrace.empty());
    }

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
    public Status getStatus() {
        return status;
    }

    @Nullable
    public QIOCraftPlan getPlan() {
        return plan;
    }

    @Nonnull
    public QIOPlanningTrace getTrace() {
        return trace;
    }

    @Nonnull
    public String getDiagnostic() {
        return diagnostic;
    }

    public int getExploredNodes() {
        return exploredNodes;
    }

    public long getPlannedOperations() {
        return plannedOperations;
    }
}
