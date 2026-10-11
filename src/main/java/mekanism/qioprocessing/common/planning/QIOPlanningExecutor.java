package mekanism.qioprocessing.common.planning;

import mekanism.common.Mekanism;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * One server's bounded, independent pure-data workers. No worker needs a server tick to run.
 * Results stay owned by this executor until the server thread drains them. Pending results
 * count against admission too, so a slow server cannot accumulate unbounded completed plans.
 */
public final class QIOPlanningExecutor implements AutoCloseable {

    public enum SubmissionStatus {ACCEPTED, SHUTDOWN, BUSY}

    public enum ResultStatus {SUCCESS, CANCELLED, TIMED_OUT, FAILED}

    /**
     * Background automation and encoding cannot consume the slots reserved for previews.
     */
    public enum TaskKind {INTERACTIVE, ENCODING, BACKGROUND}

    @FunctionalInterface
    public interface PlanningTask<I, O> {
        O plan(I input, CancellationToken cancellationToken) throws Exception;
    }

    public interface CancellationToken {
        /**
         * A nonblocking cancellation check, never a tick/slice permission request.
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
        @Nullable
        private final O value;
        @Nullable
        private final Throwable error;
        private final long submittedNanos;
        private final long startedNanos;
        private final long finishedNanos;
        private long publishedNanos;
        private long executionNanos;
        private long queueNanos;
        private long validationWaitNanos;
        private QIOPlanningMetrics.Snapshot metrics = QIOPlanningMetrics.Snapshot.EMPTY;

        private PlanningResult(UUID taskId, ResultStatus status, @Nullable O value,
                               @Nullable Throwable error, long submittedNanos, long startedNanos,
                               long finishedNanos) {
            this.taskId = taskId;
            this.status = status;
            this.value = value;
            this.error = error;
            this.submittedNanos = submittedNanos;
            this.startedNanos = startedNanos;
            this.finishedNanos = finishedNanos;
            publishedNanos = finishedNanos;
            executionNanos = elapsed(startedNanos, finishedNanos);
            queueNanos = elapsed(submittedNanos, startedNanos);
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

        /**
         * Accepted submission to worker completion, including queue time.
         */
        public long getElapsedNanos() {
            return elapsed(submittedNanos, finishedNanos);
        }

        public long getQueueNanos() {
            return queueNanos;
        }

        public long getExecutionNanos() {
            return executionNanos;
        }

        public long getValidationWaitNanos() {
            return validationWaitNanos;
        }

        public long getCompletionWaitNanos() {
            return elapsed(finishedNanos, publishedNanos);
        }

        public QIOPlanningMetrics.Snapshot getMetrics() {
            return metrics;
        }

        public long getSubmittedNanos() {
            return submittedNanos;
        }

        public long getStartedNanos() {
            return startedNanos;
        }

        public long getFinishedNanos() {
            return finishedNanos;
        }

        public long getPublishedNanos() {
            return publishedNanos;
        }
    }

    private final ThreadPoolExecutor workers;
    private final Thread ownerThread = Thread.currentThread();
    @Nullable
    private final ScheduledThreadPoolExecutor watchdog;
    private final long timeoutNanos;
    private final int maximumOutstanding;
    private final int nonInteractiveLimit;
    private final Map<UUID, TaskControl<?>> tasks = new LinkedHashMap<>();
    private final ArrayDeque<TaskControl<?>> completed = new ArrayDeque<>();
    private final ArrayDeque<TaskControl<?>> continuations = new ArrayDeque<>();
    private final ArrayDeque<TaskControl<?>> resumptions = new ArrayDeque<>();
    private boolean accepting = true;
    private static final ThreadLocal<Map<Object, Object>> LOCAL = new ThreadLocal<>();

    static QIOPlanningMetrics currentMetrics() {
        Map<Object, Object> local = LOCAL.get();
        return local == null ? QIOPlanningMetrics.NONE :
                (QIOPlanningMetrics) local.computeIfAbsent(QIOPlanningMetrics.class, ignored -> new QIOPlanningMetrics());
    }

    /**
     * Owned by one admitted task, retained across validation yields, released on completion.
     */
    @SuppressWarnings("unchecked")
    static <T> T taskLocal(Object key, java.util.function.Supplier<T> create) {
        Map<Object, Object> values = LOCAL.get();
        return values == null ? create.get() : (T) values.computeIfAbsent(key, ignored -> create.get());
    }

    public QIOPlanningExecutor() {
        this(defaultWorkerCount(), 64, 0, TimeUnit.NANOSECONDS);
    }

    /**
     * Diagnostic constructor retained for callers which need an explicit deadline.
     */
    QIOPlanningExecutor(long timeout, @Nonnull TimeUnit unit) {
        this(defaultWorkerCount(), 64, timeout, unit);
    }

    public QIOPlanningExecutor(int workerCount, int queueCapacity, long timeout,
                               @Nonnull TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (workerCount < 1 || queueCapacity < 1 || timeout < 0 ||
                timeout > 0 && unit.toNanos(timeout) <= 0) {
            throw new IllegalArgumentException("Invalid QIO worker, queue or timeout bounds");
        }
        maximumOutstanding = Math.addExact(workerCount, queueCapacity);
        // Reserve admission for manual previews. FIFO execution makes progress for already
        // accepted work regardless of which kind submits next.
        nonInteractiveLimit = Math.max(1, maximumOutstanding - Math.max(1, maximumOutstanding / 4));
        timeoutNanos = unit.toNanos(timeout);
        AtomicInteger number = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "Mekanism-QIO-Planner-" + number.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        workers = new ThreadPoolExecutor(workerCount, workerCount, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), factory, new ThreadPoolExecutor.AbortPolicy());
        workers.allowCoreThreadTimeOut(true);
        watchdog = timeout == 0 ? null : new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "Mekanism-QIO-Planner-Watchdog");
            thread.setDaemon(true);
            return thread;
        });
        if (watchdog != null) watchdog.setRemoveOnCancelPolicy(true);
    }

    public static int defaultWorkerCount() {
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
    }

    @Nonnull
    public <I, O> Submission submit(@Nonnull I input, @Nonnull PlanningTask<I, O> task,
                                    @Nonnull Consumer<PlanningResult<O>> completion) {
        return submit(TaskKind.INTERACTIVE, input, task, completion);
    }

    @Nonnull
    public synchronized <I, O> Submission submit(@Nonnull TaskKind kind, @Nonnull I input,
                                                 @Nonnull PlanningTask<I, O> task, @Nonnull Consumer<PlanningResult<O>> completion) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(completion, "completion");
        UUID id = UUID.randomUUID();
        if (!accepting) return new Submission(id, SubmissionStatus.SHUTDOWN);
        if (tasks.size() >= maximumOutstanding || kind != TaskKind.INTERACTIVE &&
                nonInteractiveCount() >= nonInteractiveLimit) {
            return new Submission(id, SubmissionStatus.BUSY);
        }
        TaskControl<O> control = new TaskControl<>(id, kind,
                cancellation -> task.plan(input, cancellation), completion);
        tasks.put(id, control);
        try {
            workers.execute(control);
            if (watchdog != null) {
                control.timeoutFuture = watchdog.schedule(() -> cancel(id, true),
                        timeoutNanos, TimeUnit.NANOSECONDS);
            }
            return new Submission(id, SubmissionStatus.ACCEPTED);
        } catch (RejectedExecutionException e) {
            workers.remove(control);
            tasks.remove(id);
            return new Submission(id, accepting ? SubmissionStatus.BUSY : SubmissionStatus.SHUTDOWN);
        }
    }

    public boolean cancel(UUID taskId) {
        return cancel(taskId, false);
    }

    private synchronized boolean cancel(UUID taskId, boolean timedOut) {
        TaskControl<?> control = tasks.get(taskId);
        if (control == null || control.cancelled || timedOut && control.result != null) return false;
        control.cancelled = true;
        control.timedOut = timedOut;
        if (control.continuation != null) control.continuation.cancel(control.id);
        continuations.remove(control);
        resumptions.remove(control);
        if (control.worker != null) {
            // Clearing worker uses this same lock; cancellation cannot interrupt a later
            // task after this thread has returned to the pool.
            control.worker.interrupt();
        } else if (control.result == null) {
            workers.remove(control);
            if (control.continuation == null) {
                control.queueNanos += elapsed(control.queuedAt, System.nanoTime());
            }
            control.finish(null, null);
        }
        return true;
    }

    /**
     * Counts owner-thread progress separately from published results.
     */
    static final class DrainResult {
        final int work;
        final int completed;

        private DrainResult(int work, int completed) {
            this.work = work;
            this.completed = completed;
        }
    }

    /**
     * Server-thread publication only. No executor lock is held while invoking callbacks.
     */
    public int drainCompleted(int limit) {
        return drainWork(limit).completed;
    }

    /**
     * A continuation handoff is useful progress even if no plan has finished yet.
     */
    DrainResult drainWork(int limit) {
        requireOwnerThread();
        int drained = 0;
        while (drained < limit) {
            TaskControl<?> control;
            synchronized (this) {
                if (!accepting || (control = completed.pollFirst()) == null) break;
                tasks.remove(control.id);
                control.result.publishedNanos = System.nanoTime();
            }
            control.publish();
            drained++;
        }
        return new DrainResult(drained + drainContinuations(limit - drained), drained);
    }

    /**
     * Admits continuations without spinning on a full validation or worker queue.
     */
    private int drainContinuations(int limit) {
        int advanced = 0;
        int count;
        // Already validated tasks must not sit behind a full validation queue.
        synchronized (this) {
            count = Math.min(Math.max(0, limit), resumptions.size());
            for (int index = 0; index < count; index++) {
                TaskControl<?> control = resumptions.pollFirst();
                if (!accepting || control.cancelled || control.result != null) continue;
                try {
                    workers.execute(control);
                    advanced++;
                } catch (RejectedExecutionException busy) {
                    resumptions.addFirst(control);
                    break;
                }
            }
        }
        limit -= advanced;
        synchronized (this) {
            count = Math.min(Math.max(0, limit), continuations.size());
        }
        for (int index = 0; index < count; index++) {
            TaskControl<?> control;
            QIOPlanningContinuation pending;
            synchronized (this) {
                control = continuations.pollFirst();
                if (control == null) break;
                if (!accepting || control.cancelled || control.result != null) continue;
                pending = control.continuation;
            }
            try {
                boolean admitted = pending.submit(control.id, () -> resume(control, pending),
                        error -> failContinuation(control, pending, error));
                if (admitted) advanced++;
                synchronized (this) {
                    if (!admitted && accepting && !control.cancelled && control.result == null &&
                            control.continuation == pending) continuations.addLast(control);
                }
            } catch (RuntimeException error) {
                failContinuation(control, pending, error);
                advanced++;
            }
        }
        return advanced;
    }

    private synchronized void resume(TaskControl<?> control, QIOPlanningContinuation pending) {
        if (!accepting || control.cancelled || control.result != null || control.continuation != pending) return;
        control.continuation = null;
        continuations.remove(control);
        long now = System.nanoTime();
        control.validationWaitNanos += elapsed(control.suspendedAt, now);
        control.queuedAt = now;
        // Validation already finished on the owner thread. A free worker must not wait
        // for the next tick merely to leave the resumption queue.
        try {
            workers.execute(control);
        } catch (RejectedExecutionException busy) {
            resumptions.addLast(control);
        }
    }

    private synchronized void failContinuation(TaskControl<?> control, QIOPlanningContinuation pending, Throwable error) {
        if (!accepting || control.result != null || control.continuation != pending) return;
        pending.cancel(control.id);
        control.continuation = null;
        continuations.remove(control);
        control.validationWaitNanos += elapsed(control.suspendedAt, System.nanoTime());
        control.finish(null, Objects.requireNonNull(error));
    }

    public int getWorkerCount() {
        return workers.getPoolSize();
    }

    /**
     * Nonblocking progress/cancellation snapshot; open CPU scopes are deliberately excluded.
     */
    public synchronized QIOPlanningMetrics.Snapshot snapshotMetrics(UUID id) {
        TaskControl<?> task = tasks.get(id);
        if (task == null) return QIOPlanningMetrics.Snapshot.EMPTY;
        QIOPlanningMetrics copy = new QIOPlanningMetrics();
        copy.merge(task.metrics.snapshot());
        long now = System.nanoTime();
        copy.add(QIOPlanningMetrics.Metric.WORKER_QUEUE_NS, task.queueNanos);
        copy.add(QIOPlanningMetrics.Metric.WORKER_NS, task.executionNanos);
        if (task.result != null) {
            copy.add(QIOPlanningMetrics.Metric.DELIVERY_WAIT_NS, elapsed(task.result.finishedNanos, now));
        } else {
            copy.add(QIOPlanningMetrics.Metric.PARTIAL_SNAPSHOTS, 1);
            if (task.worker != null) copy.add(QIOPlanningMetrics.Metric.WORKER_NS, elapsed(task.runningAt, now));
            else if (task.continuation == null)
                copy.add(QIOPlanningMetrics.Metric.WORKER_QUEUE_NS, elapsed(task.queuedAt, now));
        }
        return copy.snapshot();
    }

    public int getQueuedTaskCount() {
        return workers.getQueue().size();
    }

    public synchronized int getActiveTaskCount() {
        int active = 0;
        for (TaskControl<?> task : tasks.values()) if (task.result == null) active++;
        return active;
    }

    public synchronized int getPendingCompletionCount() {
        return completed.size();
    }

    public synchronized boolean isAccepting() {
        return accepting;
    }

    /**
     * Stops admission and invalidates every callback immediately. Does not join workers on the
     * server thread. Old timeout parameters are retained for source compatibility only.
     */
    public synchronized void shutdown(long timeout, TimeUnit unit) {
        requireOwnerThread();
        Objects.requireNonNull(unit, "unit");
        if (!accepting) return;
        accepting = false;
        for (TaskControl<?> control : tasks.values()) {
            control.cancelled = true;
            if (control.worker == null) control.local.clear();
            if (control.continuation != null) control.continuation.cancel(control.id);
            if (control.timeoutFuture != null) control.timeoutFuture.cancel(false);
            if (control.worker != null) control.worker.interrupt();
        }
        workers.shutdownNow();
        if (watchdog != null) watchdog.shutdownNow();
        tasks.clear();
        completed.clear();
        continuations.clear();
        resumptions.clear();
    }

    @Override
    public void close() {
        shutdown(0, TimeUnit.NANOSECONDS);
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("QIO result publication and shutdown require the server owner thread");
        }
    }

    private int nonInteractiveCount() {
        int count = 0;
        for (TaskControl<?> task : tasks.values()) if (task.kind != TaskKind.INTERACTIVE) count++;
        return count;
    }

    private static long elapsed(long start, long end) {
        return Math.max(0, end - start);
    }

    @FunctionalInterface
    private interface Calculation<O> {
        O run(CancellationToken token) throws Exception;
    }

    private final class TaskControl<O> implements Runnable {
        private final UUID id;
        private final TaskKind kind;
        private final Calculation<O> calculation;
        private final Map<Object, Object> local = new LinkedHashMap<>();
        private final QIOPlanningMetrics metrics = new QIOPlanningMetrics();
        private final Consumer<PlanningResult<O>> completion;
        private final long submittedNanos = System.nanoTime();
        private long startedNanos;
        private boolean started;
        private long queuedAt = submittedNanos;
        private long executionNanos;
        private long runningAt;
        private long queueNanos;
        private long validationWaitNanos;
        private long suspendedAt;
        @Nullable
        private QIOPlanningContinuation continuation;
        private volatile boolean cancelled;
        private boolean timedOut;
        @Nullable
        private Thread worker;
        @Nullable
        private ScheduledFuture<?> timeoutFuture;
        @Nullable
        private PlanningResult<O> result;

        private TaskControl(UUID id, TaskKind kind, Calculation<O> calculation,
                            Consumer<PlanningResult<O>> completion) {
            this.id = id;
            this.kind = kind;
            this.calculation = calculation;
            this.completion = completion;
            local.put(QIOPlanningMetrics.class, metrics);
        }

        @Override
        public void run() {
            long attemptStarted;
            synchronized (QIOPlanningExecutor.this) {
                if (!accepting || result != null) return;
                worker = Thread.currentThread();
                attemptStarted = System.nanoTime();
                runningAt = attemptStarted;
                if (!started) startedNanos = attemptStarted;
                queueNanos += elapsed(queuedAt, attemptStarted);
                started = true;
            }
            O value = null;
            long cpuStarted = QIOPlanningMetrics.cpuNow();
            Throwable error = null;
            QIOPlanningContinuation yielded = null;
            try {
                LOCAL.set(local);
                if (!cancelled) value = calculation.run(
                        () -> cancelled || Thread.currentThread().isInterrupted());
            } catch (QIOPlanningContinuation pending) {
                yielded = pending;
            } catch (InterruptedException | CancellationException e) {
                cancelled = true;
                error = e;
            } catch (Throwable e) {
                error = e;
            } finally {
                QIOPlanningMetrics metrics = currentMetrics();
                metrics.add(QIOPlanningMetrics.Metric.WORKER_RUNS, 1);
                metrics.cpu(QIOPlanningMetrics.Metric.WORKER_CPU_NS, QIOPlanningMetrics.Metric.WORKER_CPU_SAMPLES, cpuStarted);
                LOCAL.remove();
                synchronized (QIOPlanningExecutor.this) {
                    if (Thread.currentThread().isInterrupted()) cancelled = true;
                    worker = null;
                    long now = System.nanoTime();
                    executionNanos += elapsed(attemptStarted, now);
                    if (yielded != null && accepting && !cancelled) {
                        continuation = yielded;
                        suspendedAt = now;
                        continuations.addLast(this);
                    } else finish(value, error);
                }
            }
        }

        /**
         * Called under the executor lock, once even if cancellation wins before execution.
         */
        private void finish(@Nullable O value, @Nullable Throwable error) {
            if (result != null) return;
            QIOPlanningMetrics taskMetrics = (QIOPlanningMetrics) local.get(QIOPlanningMetrics.class);
            QIOPlanningMetrics.Snapshot metrics = taskMetrics == null ? QIOPlanningMetrics.Snapshot.EMPTY : taskMetrics.snapshot();
            local.clear();
            long finishedNanos = System.nanoTime();
            ResultStatus status = timedOut ? ResultStatus.TIMED_OUT : cancelled ?
                    ResultStatus.CANCELLED : error == null ? ResultStatus.SUCCESS : ResultStatus.FAILED;
            result = new PlanningResult<>(id, status, status == ResultStatus.SUCCESS ? value : null,
                    timedOut ? new java.util.concurrent.TimeoutException("QIO planning deadline exceeded") : error,
                    submittedNanos, started ? startedNanos : finishedNanos, finishedNanos);
            result.executionNanos = executionNanos;
            result.metrics = metrics;
            result.queueNanos = queueNanos;
            result.validationWaitNanos = validationWaitNanos +
                    (continuation == null ? 0 : elapsed(suspendedAt, finishedNanos));
            if (timeoutFuture != null) timeoutFuture.cancel(false);
            if (accepting) completed.addLast(this);
        }

        private void publish() {
            PlanningResult<O> published = result;
            // Cancellation may arrive after calculation but before the main-thread callback.
            if (cancelled && published.status == ResultStatus.SUCCESS) {
                published = new PlanningResult<>(id, ResultStatus.CANCELLED, null, null,
                        published.submittedNanos, published.startedNanos, published.finishedNanos);
                published.publishedNanos = result.publishedNanos;
                published.executionNanos = result.executionNanos;
                published.queueNanos = result.queueNanos;
                published.validationWaitNanos = result.validationWaitNanos;
                published.metrics = result.metrics;
            }
            try {
                completion.accept(published);
            } catch (RuntimeException e) {
                Mekanism.logger.error("A QIO planning completion callback failed", e);
            }
        }
    }
}
