package mekanism.qioprocessing.common.planning;

import mekanism.common.Mekanism;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Server-wide, cooperatively time-sliced QIO planning workers. */
public final class QIOPlanningExecutor implements AutoCloseable {

    public enum SubmissionStatus {
        ACCEPTED,
        SHUTDOWN
    }

    public enum ResultStatus {
        SUCCESS,
        CANCELLED,
        TIMED_OUT,
        FAILED
    }

    @FunctionalInterface
    public interface PlanningTask<I, O> {

        O plan(I input, CancellationToken cancellationToken) throws Exception;
    }

    public interface CancellationToken {

        /**
         * This is also the cooperative scheduling checkpoint. A planning task may block here
         * until the server grants its next calculation slice.
         */
        boolean isCancelled();

        default void throwIfCancelled() throws InterruptedException {
            if (isCancelled() || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("QIO planning task cancelled");
            }
        }
    }

    public static final class Submission {

        private final UUID taskId;
        private final SubmissionStatus status;

        private Submission(UUID taskId, SubmissionStatus status) {
            this.taskId = taskId;
            this.status = status;
        }

        @Nonnull
        public UUID getTaskId() {
            return taskId;
        }

        @Nonnull
        public SubmissionStatus getStatus() {
            return status;
        }

        public boolean isAccepted() {
            return status == SubmissionStatus.ACCEPTED;
        }
    }

    public static final class PlanningResult<O> {

        private final UUID taskId;
        private final ResultStatus status;
        private final O value;
        private final Throwable error;
        private final long elapsedNanos;

        private PlanningResult(UUID taskId, ResultStatus status, @Nullable O value,
              @Nullable Throwable error, long elapsedNanos) {
            this.taskId = taskId;
            this.status = status;
            this.value = value;
            this.error = error;
            this.elapsedNanos = elapsedNanos;
        }

        @Nonnull
        public UUID getTaskId() {
            return taskId;
        }

        @Nonnull
        public ResultStatus getStatus() {
            return status;
        }

        @Nullable
        public O getValue() {
            return value;
        }

        @Nullable
        public Throwable getError() {
            return error;
        }

        public long getElapsedNanos() {
            return elapsedNanos;
        }
    }

    private static final long NO_TIMEOUT = 0;
    private static final long MINIMUM_SLICE_NANOS = TimeUnit.MICROSECONDS.toNanos(1);

    private final ThreadPoolExecutor workers;
    @Nullable private final ScheduledExecutorService timeoutScheduler;
    private final long taskTimeoutNanos;
    private final Map<UUID, TaskControl> tasks = new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<CompletedTask<?>> completed = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    /** Creates the same cached-worker, tick-sliced model used by AE2 crafting calculations. */
    public QIOPlanningExecutor() {
        this(NO_TIMEOUT, TimeUnit.NANOSECONDS);
    }

    /** Test and diagnostic constructor. A zero timeout disables automatic wall-clock expiry. */
    QIOPlanningExecutor(long taskTimeout, @Nonnull TimeUnit taskTimeoutUnit) {
        Objects.requireNonNull(taskTimeoutUnit, "taskTimeoutUnit");
        if (taskTimeout < 0 || taskTimeout > 0 && taskTimeoutUnit.toNanos(taskTimeout) <= 0) {
            throw new IllegalArgumentException("QIO planning task timeout cannot be negative or overflow");
        }
        taskTimeoutNanos = taskTimeout == 0 ? NO_TIMEOUT : taskTimeoutUnit.toNanos(taskTimeout);
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable,
                  "Mekanism-QIO-Planner-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        workers = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
              new SynchronousQueue<>(), factory, new ThreadPoolExecutor.AbortPolicy());
        timeoutScheduler = taskTimeoutNanos == NO_TIMEOUT ? null :
              Executors.newSingleThreadScheduledExecutor(runnable -> {
                  Thread thread = new Thread(runnable, "Mekanism-QIO-Planner-Watchdog");
                  thread.setDaemon(true);
                  return thread;
              });
    }

    @Nonnull
    public synchronized <I, O> Submission submit(@Nonnull I input,
          @Nonnull PlanningTask<I, O> task,
          @Nonnull Consumer<PlanningResult<O>> completion) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(completion, "completion");
        UUID taskId = UUID.randomUUID();
        if (!accepting.get()) {
            return new Submission(taskId, SubmissionStatus.SHUTDOWN);
        }
        TaskControl control = new TaskControl(taskTimeoutNanos);
        tasks.put(taskId, control);
        try {
            if (timeoutScheduler != null) {
                control.timeoutFuture = timeoutScheduler.schedule(() -> control.cancel(true),
                      taskTimeoutNanos, TimeUnit.NANOSECONDS);
            }
            workers.execute(() -> execute(taskId, input, task, completion, control));
            return new Submission(taskId, SubmissionStatus.ACCEPTED);
        } catch (RejectedExecutionException e) {
            cancelTimeout(control);
            tasks.remove(taskId);
            return new Submission(taskId, SubmissionStatus.SHUTDOWN);
        }
    }

    public synchronized boolean cancel(UUID taskId) {
        TaskControl control = taskId == null ? null : tasks.get(taskId);
        return control != null && control.cancel(false);
    }

    /**
     * Grants every active calculation an equal part of the server-wide budget and waits until
     * each worker finishes or reaches its next cooperative checkpoint.
     */
    public int runPlanningSlice(long budget, @Nonnull TimeUnit unit) {
        if (budget <= 0) {
            return 0;
        }
        long budgetNanos = Objects.requireNonNull(unit, "unit").toNanos(budget);
        if (budgetNanos <= 0) {
            return 0;
        }
        List<TaskControl> active;
        synchronized (this) {
            active = new ArrayList<>(tasks.size());
            for (TaskControl control : tasks.values()) {
                if (!control.finished.get() && !control.cancelled.get()) {
                    active.add(control);
                }
            }
        }
        if (active.isEmpty()) {
            return 0;
        }
        long sliceNanos = Math.max(MINIMUM_SLICE_NANOS, budgetNanos / active.size());
        int advanced = 0;
        for (TaskControl control : active) {
            if (control.runFor(sliceNanos)) {
                advanced++;
            }
        }
        return advanced;
    }

    /** Runs completion callbacks on the caller thread and never waits for a worker. */
    public int drainCompleted(int limit) {
        if (limit <= 0) {
            return 0;
        }
        int drained = 0;
        CompletedTask<?> task;
        while (drained < limit && (task = completed.poll()) != null) {
            synchronized (this) {
                tasks.remove(task.taskId);
            }
            task.complete();
            drained++;
        }
        return drained;
    }

    /** Current cached worker count; this grows with concurrent calculations and later shrinks. */
    public int getWorkerCount() {
        return workers.getPoolSize();
    }

    /** Cached workers hand tasks off directly and therefore have no executor waiting queue. */
    public int getQueuedTaskCount() {
        return 0;
    }

    public synchronized int getActiveTaskCount() {
        int active = 0;
        for (TaskControl control : tasks.values()) {
            if (!control.finished.get() && !control.cancelled.get()) {
                active++;
            }
        }
        return active;
    }

    public int getPendingCompletionCount() {
        return completed.size();
    }

    public boolean isAccepting() {
        return accepting.get() && !workers.isShutdown();
    }

    public void shutdown(long timeout, TimeUnit unit) {
        List<TaskControl> controls;
        synchronized (this) {
            if (!accepting.compareAndSet(true, false)) {
                return;
            }
            controls = new ArrayList<>(tasks.values());
        }
        for (TaskControl control : controls) {
            control.cancel(false);
        }
        workers.shutdownNow();
        if (timeoutScheduler != null) {
            timeoutScheduler.shutdownNow();
        }
        try {
            if (!workers.awaitTermination(Math.max(0, timeout),
                  Objects.requireNonNull(unit, "unit"))) {
                Mekanism.logger.warn("QIO planning workers did not terminate within the shutdown budget");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            tasks.clear();
        }
        completed.clear();
    }

    @Override
    public void close() {
        shutdown(5, TimeUnit.SECONDS);
    }

    private <I, O> void execute(UUID taskId, I input, PlanningTask<I, O> task,
          Consumer<PlanningResult<O>> completion, TaskControl control) {
        control.worker = Thread.currentThread();
        long started = System.nanoTime();
        PlanningResult<O> result;
        try {
            if (!control.awaitSlice()) {
                result = cancelledResult(taskId, control, started);
            } else {
                O value = task.plan(input, control::checkpoint);
                ResultStatus status = control.timedOut.get() ? ResultStatus.TIMED_OUT :
                      control.cancelled.get() || Thread.currentThread().isInterrupted() ?
                            ResultStatus.CANCELLED : ResultStatus.SUCCESS;
                result = new PlanningResult<>(taskId, status,
                      status == ResultStatus.SUCCESS ? value : null,
                      status == ResultStatus.TIMED_OUT ? timeoutException() : null,
                      System.nanoTime() - started);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = cancelledResult(taskId, control, started);
        } catch (Throwable error) {
            boolean timedOut = control.timedOut.get();
            boolean cancelled = control.cancelled.get() ||
                  Thread.currentThread().isInterrupted();
            result = new PlanningResult<>(taskId,
                  timedOut ? ResultStatus.TIMED_OUT : cancelled ? ResultStatus.CANCELLED :
                        ResultStatus.FAILED,
                  null, timedOut ? timeoutException() : error,
                  System.nanoTime() - started);
        } finally {
            control.worker = null;
            control.finish();
            cancelTimeout(control);
        }
        completed.add(new CompletedTask<>(taskId, result, completion));
    }

    private static <O> PlanningResult<O> cancelledResult(UUID taskId, TaskControl control,
          long started) {
        boolean timedOut = control.timedOut.get();
        return new PlanningResult<>(taskId,
              timedOut ? ResultStatus.TIMED_OUT : ResultStatus.CANCELLED, null,
              timedOut ? timeoutException() : null, System.nanoTime() - started);
    }

    private static java.util.concurrent.TimeoutException timeoutException() {
        return new java.util.concurrent.TimeoutException(
              "QIO planning task exceeded its time budget");
    }

    private static void cancelTimeout(TaskControl control) {
        ScheduledFuture<?> future = control.timeoutFuture;
        if (future != null) {
            future.cancel(false);
        }
    }

    private static final class TaskControl {

        private final Object monitor = new Object();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean timedOut = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final long deadlineNanos;
        private volatile Thread worker;
        private boolean running;
        private long sliceDeadlineNanos;
        @Nullable private volatile ScheduledFuture<?> timeoutFuture;

        private TaskControl(long timeoutNanos) {
            deadlineNanos = timeoutNanos == NO_TIMEOUT ? NO_TIMEOUT :
                  saturatedAdd(System.nanoTime(), timeoutNanos);
        }

        private boolean runFor(long sliceNanos) {
            synchronized (monitor) {
                if (finished.get() || cancelled.get()) {
                    return false;
                }
                sliceDeadlineNanos = saturatedAdd(System.nanoTime(), sliceNanos);
                running = true;
                monitor.notifyAll();
                boolean interrupted = false;
                while (running && !finished.get() && !cancelled.get()) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                return true;
            }
        }

        private boolean checkpoint() {
            try {
                return !awaitSlice();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel(false);
                return true;
            }
        }

        private boolean awaitSlice() throws InterruptedException {
            synchronized (monitor) {
                while (!cancelled.get() && !finished.get() && !deadlineReached()) {
                    if (running && System.nanoTime() - sliceDeadlineNanos < 0) {
                        return true;
                    }
                    if (running) {
                        running = false;
                        monitor.notifyAll();
                    }
                    monitor.wait();
                }
                return false;
            }
        }

        private boolean cancel(boolean timeout) {
            if (!cancelled.compareAndSet(false, true)) {
                return false;
            }
            if (timeout) {
                timedOut.set(true);
            }
            synchronized (monitor) {
                running = false;
                monitor.notifyAll();
            }
            Thread activeWorker = worker;
            if (activeWorker != null) {
                activeWorker.interrupt();
            }
            return true;
        }

        private void finish() {
            finished.set(true);
            synchronized (monitor) {
                running = false;
                monitor.notifyAll();
            }
        }

        private boolean deadlineReached() {
            if (deadlineNanos != NO_TIMEOUT && System.nanoTime() - deadlineNanos >= 0) {
                timedOut.set(true);
                cancelled.set(true);
                return true;
            }
            return false;
        }

        private static long saturatedAdd(long left, long right) {
            return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
        }
    }

    private static final class CompletedTask<O> {

        private final UUID taskId;
        private final PlanningResult<O> result;
        private final Consumer<PlanningResult<O>> completion;

        private CompletedTask(UUID taskId, PlanningResult<O> result,
              Consumer<PlanningResult<O>> completion) {
            this.taskId = taskId;
            this.result = result;
            this.completion = completion;
        }

        private void complete() {
            try {
                completion.accept(result);
            } catch (RuntimeException e) {
                Mekanism.logger.error("A QIO planning completion callback failed", e);
            }
        }
    }
}
