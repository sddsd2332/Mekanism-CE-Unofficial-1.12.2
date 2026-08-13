package mekanism.qioprocessing.common.order;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.planning.QIOPlanningResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Server-authoritative unlocked planning preview. It owns no QIO material or execution slot. */
public final class QIOOrderPreview {

    public enum State {
        PREPARING,
        PLANNING,
        READY,
        FAILED,
        CANCELLED,
        EXPIRED,
        CONFIRMED
    }

    private final UUID previewId;
    private final UUID requester;
    private final QIOFrequencyReference frequency;
    private final PortableResourceDescriptor target;
    private final long amount;
    private final long priority;
    private final boolean mergeOrder;
    private final long createdAtTick;
    private final long expiresAtTick;
    private final long requestStartedNanos;
    private State state = State.PREPARING;
    @Nullable
    private UUID planningTaskId;
    @Nullable
    private QIOPlanningResult result;
    private long mainThreadPreparationNanos;
    private long routePreparationNanos;
    private long schedulingNanos;
    private long planningNanos;
    private long totalNanos;
    private int planningAttempt = 1;

    QIOOrderPreview(UUID previewId, UUID requester, QIOFrequencyReference frequency,
          PortableResourceDescriptor target, long amount, long priority, long createdAtTick,
          long expiresAtTick, boolean mergeOrder, long requestStartedNanos,
          long mainThreadPreparationNanos) {
        this.previewId = Objects.requireNonNull(previewId, "previewId");
        this.requester = Objects.requireNonNull(requester, "requester");
        this.frequency = Objects.requireNonNull(frequency, "frequency");
        this.target = Objects.requireNonNull(target, "target");
        if (amount <= 0 || createdAtTick < 0 || expiresAtTick <= createdAtTick ||
            mainThreadPreparationNanos < 0) {
            throw new IllegalArgumentException("Invalid QIO order preview bounds");
        }
        this.amount = amount;
        this.priority = priority;
        this.mergeOrder = mergeOrder;
        this.createdAtTick = createdAtTick;
        this.expiresAtTick = expiresAtTick;
        this.requestStartedNanos = requestStartedNanos;
        this.mainThreadPreparationNanos = mainThreadPreparationNanos;
    }

    @Nonnull
    public UUID getPreviewId() {
        return previewId;
    }

    @Nonnull
    public UUID getRequester() {
        return requester;
    }

    @Nonnull
    public QIOFrequencyReference getFrequency() {
        return frequency;
    }

    @Nonnull
    public PortableResourceDescriptor getTarget() {
        return target;
    }

    public long getAmount() {
        return amount;
    }

    public long getPriority() {
        return priority;
    }

    public boolean isMergeOrder() {
        return mergeOrder;
    }

    public long getCreatedAtTick() {
        return createdAtTick;
    }

    public long getExpiresAtTick() {
        return expiresAtTick;
    }

    @Nonnull
    public synchronized State getState() {
        return state;
    }

    @Nullable
    public synchronized UUID getPlanningTaskId() {
        return planningTaskId;
    }

    @Nullable
    public synchronized QIOPlanningResult getResult() {
        return result;
    }

    public synchronized long getPlanningNanos() {
        return planningNanos;
    }

    public synchronized long getMainThreadPreparationNanos() {
        return mainThreadPreparationNanos;
    }

    public synchronized long getRoutePreparationNanos() {
        return routePreparationNanos;
    }

    public synchronized long getSchedulingNanos() {
        return schedulingNanos;
    }

    public synchronized long getTotalNanos() {
        return totalNanos;
    }

    public synchronized int getPlanningAttempt() {
        return planningAttempt;
    }

    synchronized void bindTask(UUID taskId) {
        if ((state != State.PREPARING && state != State.PLANNING) ||
            planningTaskId != null) {
            throw new IllegalStateException("QIO preview already has a planning task");
        }
        planningTaskId = Objects.requireNonNull(taskId, "taskId");
    }

    synchronized void beginPlanning() {
        if (state == State.PREPARING) {
            state = State.PLANNING;
        }
    }

    synchronized boolean retry(long additionalMainThreadPreparationNanos,
          long routePreparationNanos, long planningNanos, long asynchronousNanos) {
        if (state != State.PREPARING && state != State.PLANNING) {
            return false;
        }
        recordTimings(additionalMainThreadPreparationNanos, routePreparationNanos,
              planningNanos, asynchronousNanos);
        planningTaskId = null;
        result = null;
        state = State.PREPARING;
        planningAttempt = Math.addExact(planningAttempt, 1);
        return true;
    }

    synchronized void complete(QIOPlanningResult result, long routePreparationNanos,
          long planningNanos, long asynchronousNanos) {
        if (state != State.PREPARING && state != State.PLANNING) {
            return;
        }
        this.result = Objects.requireNonNull(result, "result");
        recordTimings(0, routePreparationNanos, planningNanos, asynchronousNanos);
        totalNanos = Math.max(0, System.nanoTime() - requestStartedNanos);
        state = result.getStatus() == QIOPlanningResult.Status.SUCCESS ? State.READY : State.FAILED;
    }

    private void recordTimings(long additionalMainThreadPreparationNanos,
          long routePreparationNanos, long planningNanos, long asynchronousNanos) {
        this.mainThreadPreparationNanos = saturatedAdd(this.mainThreadPreparationNanos,
              Math.max(0, additionalMainThreadPreparationNanos));
        this.routePreparationNanos = saturatedAdd(this.routePreparationNanos,
              Math.max(0, routePreparationNanos));
        this.planningNanos = saturatedAdd(this.planningNanos, Math.max(0, planningNanos));
        long accounted = saturatedAdd(Math.max(0, routePreparationNanos),
              Math.max(0, planningNanos));
        long scheduling = Math.max(0, asynchronousNanos -
              Math.min(Math.max(0, asynchronousNanos), accounted));
        schedulingNanos = saturatedAdd(schedulingNanos, scheduling);
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    synchronized boolean cancel() {
        if (state == State.CONFIRMED || state == State.CANCELLED || state == State.EXPIRED) {
            return false;
        }
        state = State.CANCELLED;
        return true;
    }

    synchronized boolean expire(long currentTick) {
        if (currentTick < expiresAtTick || state == State.CONFIRMED ||
              state == State.CANCELLED || state == State.EXPIRED) {
            return false;
        }
        state = State.EXPIRED;
        return true;
    }

    synchronized void confirm() {
        if (state != State.READY) {
            throw new IllegalStateException("QIO preview is not ready for confirmation");
        }
        state = State.CONFIRMED;
    }
}
