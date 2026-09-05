package mekanism.common.concurrent;


import io.netty.util.internal.ThrowableUtil;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import mekanism.common.Mekanism;
import mekanism.api.IAsyncMachinePlanner;
import mekanism.api.IAsyncPlanCalculator;
import mekanism.common.tile.base.TileEntitySynchronized;
import mekanism.common.util.concurrent.*;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Queue;
import java.util.Objects;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.concurrent.locks.LockSupport;

/**
 * Asynchronous system based on MMCE
 * Perhaps this can provide operational performance
 */
public class TaskExecutor {

    public static final int THREAD_COUNT = Math.min(Math.max(Runtime.getRuntime().availableProcessors() / 4, 4), 8);

    public static final ThreadPoolExecutor THREAD_POOL = new ThreadPoolExecutor(THREAD_COUNT, THREAD_COUNT,
            5000, TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(),
            new CustomThreadFactory("MEK-TaskExecutor-%s", SidedThreadGroups.SERVER));

    public static final ForkJoinPool FORK_JOIN_POOL = new ForkJoinPool(THREAD_COUNT,
            new CustomForkJoinWorkerThreadFactory("MEK-ForkJoinPool-worker-%s"),
            null, true);

    public static long totalExecuted = 0;
    public static long taskUsedTime = 0;
    public static long totalUsedTime = 0;
    public static long executedCount = 0;

    public static long tickExisted = 0;

    /** Returns true only for threads owned by the machine planning pools. */
    public static boolean isWorkerThread() {
        Thread thread = Thread.currentThread();
        return thread.getName().startsWith("MEK-TaskExecutor-") ||
              thread.getName().startsWith("MEK-ForkJoinPool-worker-");
    }

    private final Queue<ActionExecutor> submitted = Queues.createConcurrentQueue();

    private final Queue<ActionExecutor> executors = Queues.createConcurrentQueue();
    private final Long2ObjectMap<ExecuteGroup> executeGroups = new Long2ObjectOpenHashMap<>();

    private final Queue<ForkJoinTask<?>> forkJoinTasks = Queues.createConcurrentQueue();

    private final Queue<PlanCommitAction> planCommitActions = new PriorityBlockingQueue<>();
    private final Queue<Action> mainThreadActions = Queues.createConcurrentQueue();
    private final Queue<TileEntitySynchronized> requireUpdateTEQueue = Queues.createConcurrentQueue();
    private final Queue<TileEntitySynchronized> requireMarkNoUpdateTEQueue = Queues.createConcurrentQueue();
    private final Queue<TileEntitySynchronized> requireUpdateComparatorOutputLevel = Queues.createConcurrentQueue();

    private final TaskSubmitter submitter = new TaskSubmitter();
    private final Map<Object, ActionExecutor> pendingPlanKeys =
          Collections.synchronizedMap(new IdentityHashMap<>());
    private final AtomicLong planCommitSequence = new AtomicLong();

    private volatile boolean inTick = false;
    private volatile boolean shouldUseForkJoinPool = false;
    private volatile Thread serverThread;
    private volatile boolean acceptingTasks = true;
    private final Object admissionLock = new Object();

    public void init() {
        acceptingTasks = true;
        THREAD_POOL.prestartAllCoreThreads();
        submitter.start();
    }

    /** Re-enables the reusable executor when a new integrated/dedicated server starts. */
    public void resume() {
        synchronized (admissionLock) {
            acceptingTasks = true;
            serverThread = null;
        }
        submitter.start();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.side == Side.CLIENT) {
            return;
        }
        switch (event.phase) {
            case START -> {
                serverThread = Thread.currentThread();
                inTick = true;
                submitter.unpark();
            }
            default -> {
                inTick = false;
                tickExisted++;
            }
        }

        int executed = executeActions();
        if (executed > 0) {
            totalExecuted += executed;
            executedCount++;
        }

        synchronized (executeGroups) {
            executeGroups.clear();
        }
        checkShouldUseForkJoinPool();
    }

    /**
     * <p>大量任务的前提下，使用阻塞队列会导致 CPU 资源占用的激增，此时我们不再关注优先级，而是考虑尽可能快地提交任务。</p>
     *
     * <p>With a large number of tasks, the use of a blocking queue can lead to a spike in CPU resource usage, at which
     * point we stop focusing on prioritization and think about submitting tasks as fast as possible.</p>
     */
    private void checkShouldUseForkJoinPool() {
        if (tickExisted % 20 != 0) {
            return;
        }

        if (shouldUseForkJoinPool) {
            if (!shouldUseForkJoinPool()) {
                Mekanism.logger.warn("The thread pool has been re-switched to ThreadPoolExecutor (below the limit of 1500).");
                shouldUseForkJoinPool = false;
            }
            return;
        }
        if (shouldUseForkJoinPool()) {
            Mekanism.logger.warn("The thread pool has now been replaced with a ForkJoinPool due to too many tasks in a single commit (Limit 1500).");
            shouldUseForkJoinPool = true;
        }
    }

    private boolean shouldUseForkJoinPool() {
        long executedAvgPerExecution = executedCount == 0 ? 0 : totalExecuted / executedCount;
        return executedAvgPerExecution >= 1500;
    }

    /**
     * 正式执行队列内的所有操作。
     *
     * @return 已执行的数量
     */
    public int executeActions() {
        if (isWorkerThread() || !isServerThread()) throw new IllegalStateException("Task callbacks must run on the server thread");
        int executed = 0;
        long time = System.nanoTime() / 1000;

        // Async work may enqueue more async work. Drain it to a fixed point before
        // running any post-async main-thread action so container/world phases cannot overlap.
        do {
            submitTask();
            executed += awaitActionExecutors();
        } while (!executors.isEmpty() || !submitted.isEmpty() || hasPendingExecuteGroupTasks());

        executed += executePlanCommitActions();
        executed += executeMainThreadActions();
        updateTileEntity();

        totalUsedTime += System.nanoTime() / 1000 - time;
        return executed;
    }

    private int executeMainThreadActions() {
        int executed = 0;
        if (mainThreadActions.isEmpty()) {
            return executed;
        }

        Action action;
        while ((action = mainThreadActions.poll()) != null) {
            try {
                action.doAction();
            } catch (Throwable e) {
                Mekanism.logger.warn("An error occurred during synchronous task execution!");
                Mekanism.logger.warn(ThrowableUtil.stackTraceToString(e));
            }
            executed++;
        }
        return executed;
    }

    private int executePlanCommitActions() {
        int executed = 0;
        PlanCommitAction action;
        while ((action = planCommitActions.poll()) != null) {
            try {
                if (acceptingTasks) action.action.doAction();
            } catch (Throwable error) {
                Mekanism.logger.warn("An error occurred during machine plan commit!");
                Mekanism.logger.warn(ThrowableUtil.stackTraceToString(error));
            } finally {
                cleanupPlan(action);
            }
            executed++;
        }
        return executed;
    }

    private void cleanupPlan(PlanCommitAction action) {
        try {
            action.cleanup.doAction();
        } catch (Throwable error) {
            Mekanism.logger.warn("Machine plan cleanup failed", error);
        }
    }

    private int awaitActionExecutors() {
        int executed = 0;

        ActionExecutor executor;
        while ((executor = submitted.poll()) != null) {
            // Do not execute main-thread actions here. They are post-async actions
            // and may read or mutate the same containers owned by this task.
            executor.awaitCompletion();

            taskUsedTime += executor.usedTime;
            executed++;
        }
        return executed;
    }

    private void updateTileEntity() {
        if (requireUpdateTEQueue.isEmpty() && requireMarkNoUpdateTEQueue.isEmpty() && requireUpdateComparatorOutputLevel.isEmpty()) {
            return;
        }

        ReferenceSet<TileEntitySynchronized> toUpdate = new ReferenceOpenHashSet<>();
        TileEntitySynchronized tile;
        while ((tile = requireUpdateTEQueue.poll()) != null) {
            toUpdate.add(tile);
        }
        toUpdate.forEach(TileEntitySynchronized::markForUpdate);

        toUpdate.clear();
        while ((tile = requireMarkNoUpdateTEQueue.poll()) != null) {
            toUpdate.add(tile);
        }
        toUpdate.forEach(TileEntitySynchronized::markNoUpdate);

        toUpdate.clear();
        while ((tile = requireUpdateComparatorOutputLevel.poll()) != null) {
            toUpdate.add(tile);
        }
        toUpdate.forEach(TileEntitySynchronized::updateComparatorOutputLevel);
    }

    /**
     * <p>添加一个异步操作引用，这个操作必定在本 Tick 结束前执行完毕。</p>
     *
     * @param action 要执行的异步任务
     */
    public ActionExecutor addTask(final Action action) {
        return addTask(action, 0);
    }

    /**
     * <p>添加一个异步操作引用，这个操作必定在本 Tick 结束前执行完毕。</p>
     *
     * @param action   要执行的异步任务
     * @param priority 优先级
     */
    public ActionExecutor addTask(final Action action, final int priority) {
        Objects.requireNonNull(action, "Async task cannot be null");
        ActionExecutor actionExecutor = new ActionExecutor(action, priority);
        synchronized (admissionLock) {
            requireAcceptingTasks();
            executors.offer(actionExecutor);
        }
        if (inTick) {
            submitter.unpark();
        }

        return actionExecutor;
    }

    public ActionExecutor addExecuteGroupTask(final Action action, final long groupId) {
        ActionExecutor executor;
        synchronized (admissionLock) {
            requireAcceptingTasks();
            synchronized (executeGroups) {
            ExecuteGroup group = executeGroups.get(groupId);
            if (group == null) {
                group = new ExecuteGroup(groupId);
                executeGroups.put(groupId, group);
            }
            executor = group.offer(new ActionExecutor(action));
            }
        }
        if (inTick) {
            submitter.unpark();
        }
        return executor;
    }


    public <T> ForkJoinTask<T> submitForkJoinTask(final ForkJoinTask<T> task) {
        synchronized (admissionLock) {
            requireAcceptingTasks();
            forkJoinTasks.offer(task);
        }
        if (inTick) {
            submitter.unpark();
        }
        return task;
    }

    /**
     * <p>添加一个同步操作引用，这个操作必定会在异步操作完成后在<strong>主线程</strong>中顺序执行。</p>
     *
     * @param action 要执行的同步任务
     */
    public void addSyncTask(final Action action) {
        Objects.requireNonNull(action, "Synchronous task cannot be null");
        synchronized (admissionLock) {
            if (acceptingTasks) mainThreadActions.offer(action);
        }
    }

    /** Adds an atomic machine commit which runs before ordinary main-thread callbacks. */
    public void addPlanCommitTask(final Action action) {
        addPlanCommitTask(reservePlanCommitSequence(), action);
    }

    public long reservePlanCommitSequence() {
        return planCommitSequence.getAndIncrement();
    }

    public void addPlanCommitTask(final long sequence, final Action action) {
        addPlanCommitTask(sequence, action, () -> { });
    }

    /** Cleanup releases server-owned reservations, including when shutdown discards the callback. */
    public void addPlanCommitTask(final long sequence, final Action action, final Action cleanup) {
        Objects.requireNonNull(action, "Plan commit task cannot be null");
        Objects.requireNonNull(cleanup, "Plan cleanup cannot be null");
        synchronized (admissionLock) {
            requireAcceptingTasks();
            planCommitActions.offer(new PlanCommitAction(sequence, action, cleanup));
        }
    }

    private void requireAcceptingTasks() {
        if (!acceptingTasks) throw new RejectedExecutionException("Task executor is shutting down");
    }

    /**
     * Queues a pure planner calculation and publishes its commit callback only after
     * the calculation has joined the current Tick barrier. The callback is always
     * run by {@link #executeMainThreadActions()} on the server thread.
     */
    public <SNAPSHOT, PLAN> ActionExecutor submitPlan(final IAsyncMachinePlanner<SNAPSHOT, PLAN> planner,
          final SNAPSHOT snapshot, final Consumer<PLAN> commit) {
        return submitPlan(planner, snapshot, commit, () -> { });
    }

    private <SNAPSHOT, PLAN> ActionExecutor submitPlan(final IAsyncMachinePlanner<SNAPSHOT, PLAN> planner,
          final SNAPSHOT snapshot, final Consumer<PLAN> commit, final Action cleanup) {
        Objects.requireNonNull(planner, "Planner cannot be null");
        Objects.requireNonNull(commit, "Plan commit callback cannot be null");
        IAsyncPlanCalculator<SNAPSHOT, PLAN> calculator = planner.getAsyncPlanCalculator();
        if (!AsyncPlanSafetyValidator.isDetached(calculator) ||
            !AsyncPlanSafetyValidator.isDetachedValue(snapshot)) {
            throw new IllegalArgumentException("Async plan requires a detached calculator and immutable snapshot");
        }
        final long commitSequence = reservePlanCommitSequence();
        DetachedCalculation<SNAPSHOT, PLAN> calculation = new DetachedCalculation<>(calculator, snapshot);
        ActionExecutor task = new ActionExecutor(calculation);
        PlanCommitAction callback = new PlanCommitAction(commitSequence, () -> {
            PLAN completedPlan = calculation.plan;
            Throwable failure = calculation.failure == null ? task.getFailure() : calculation.failure;
            if (!task.isCancelled() && failure == null && completedPlan != null &&
                AsyncPlanSafetyValidator.isDetachedValue(completedPlan) &&
                planner.isPlanStillValid(snapshot, completedPlan)) {
                commit.accept(completedPlan);
            } else {
                planner.onPlanDiscarded(snapshot, completedPlan, failure);
            }
        }, cleanup);
        synchronized (admissionLock) {
            requireAcceptingTasks();
            planCommitActions.offer(callback);
            executors.offer(task);
        }
        if (inTick) submitter.unpark();
        return task;
    }

    /** Convenience overload which invokes the planner's own server-side commit method. */
    public <SNAPSHOT, PLAN> ActionExecutor submitPlan(final IAsyncMachinePlanner<SNAPSHOT, PLAN> planner,
          final SNAPSHOT snapshot) {
        return submitPlan(planner, snapshot, plan -> planner.commitPlan(snapshot, plan));
    }

    /**
     * Submits at most one plan for an owner identity. The owner key is compared by
     * identity, which matches TileEntity lifetime semantics.
     */
    public <SNAPSHOT, PLAN> ActionExecutor submitPlan(final Object ownerKey,
          final IAsyncMachinePlanner<SNAPSHOT, PLAN> planner, final SNAPSHOT snapshot,
          final Consumer<PLAN> commit) {
        Objects.requireNonNull(ownerKey, "Plan owner key cannot be null");
        synchronized (pendingPlanKeys) {
            ActionExecutor existing = pendingPlanKeys.get(ownerKey);
            if (existing != null) {
                return existing;
            }
            ActionExecutor[] holder = new ActionExecutor[1];
            holder[0] = submitPlan(planner, snapshot, commit, () -> pendingPlanKeys.remove(ownerKey, holder[0]));
            pendingPlanKeys.put(ownerKey, holder[0]);
            return holder[0];
        }
    }

    public boolean hasPendingPlan(Object ownerKey) {
        synchronized (pendingPlanKeys) {
            return pendingPlanKeys.containsKey(ownerKey);
        }
    }

    public void cancelPlan(Object ownerKey) {
        synchronized (pendingPlanKeys) {
            ActionExecutor task = pendingPlanKeys.remove(ownerKey);
            if (task != null) task.cancel();
        }
    }

    /** Alias used by machine integrations during the migration period. */
    public <SNAPSHOT, PLAN> ActionExecutor addAsyncPlan(final IAsyncMachinePlanner<SNAPSHOT, PLAN> planner,
          final SNAPSHOT snapshot, final Consumer<PLAN> commit) {
        return submitPlan(planner, snapshot, commit);
    }

    /** Returns true when called from the thread which began the current server tick. */
    public boolean isServerThread() {
        return serverThread == null || serverThread == Thread.currentThread();
    }

    /**
     * Stops accepting new work, joins already captured calculations, and discards
     * every world-mutating callback. The static pools remain reusable by a later
     * integrated-server session in the same JVM.
     */
    public void shutdown() {
        synchronized (admissionLock) {
            acceptingTasks = false;
            inTick = false;
        }
        submitter.stopAndWait();
        do {
            submitTask();
            awaitActionExecutors();
        } while (!executors.isEmpty() || !submitted.isEmpty() || hasPendingExecuteGroupTasks());
        ForkJoinTask<?> task;
        while ((task = forkJoinTasks.poll()) != null) {
            task.cancel(false);
        }
        mainThreadActions.clear();
        PlanCommitAction callback;
        while ((callback = planCommitActions.poll()) != null) cleanupPlan(callback);
        requireUpdateTEQueue.clear();
        requireMarkNoUpdateTEQueue.clear();
        requireUpdateComparatorOutputLevel.clear();
        synchronized (executeGroups) {
            executeGroups.clear();
        }
        pendingPlanKeys.clear();
    }

    private static final class PlanCommitAction implements Comparable<PlanCommitAction> {
        private final long sequence;
        private final Action action;
        private final Action cleanup;

        private PlanCommitAction(long sequence, Action action, Action cleanup) {
            this.sequence = sequence;
            this.action = action;
            this.cleanup = cleanup;
        }

        @Override
        public int compareTo(PlanCommitAction other) {
            return Long.compare(sequence, other.sequence);
        }
    }

    private static final class DetachedCalculation<SNAPSHOT, PLAN> implements Action {
        private final IAsyncPlanCalculator<SNAPSHOT, PLAN> calculator;
        private final SNAPSHOT snapshot;
        private volatile PLAN plan;
        private volatile Throwable failure;

        private DetachedCalculation(IAsyncPlanCalculator<SNAPSHOT, PLAN> calculator, SNAPSHOT snapshot) {
            this.calculator = calculator;
            this.snapshot = snapshot;
        }

        @Override
        public void doAction() {
            try {
                plan = calculator.calculate(snapshot);
            } catch (Throwable error) {
                failure = error;
            }
        }
    }

    public void addTEUpdateTask(final TileEntitySynchronized te) {
        requireUpdateTEQueue.offer(te);
    }

    public void addTEMarkNoUpdateTask(final TileEntitySynchronized te) {
        requireMarkNoUpdateTEQueue.offer(te);
    }

    public void addUpdateComparatorOutputLevelTask(final TileEntitySynchronized te) {
        requireUpdateComparatorOutputLevel.offer(te);
    }

    protected void execute(final ActionExecutor executor) {
        if (shouldUseForkJoinPool) {
            FORK_JOIN_POOL.execute(executor);
        } else {
            THREAD_POOL.execute(executor);
        }
    }

    private synchronized void submitTask() {
        ActionExecutor executor;
        while ((executor = executors.poll()) != null) {
            try {
                execute(executor);
            } catch (Throwable error) {
                executor.cancel(error);
            }
            submitted.offer(executor);
        }

        ForkJoinTask<?> forkJoinTask;
        while ((forkJoinTask = forkJoinTasks.poll()) != null) {
            if (!acceptingTasks) forkJoinTask.cancel(false);
            else {
                try {
                    FORK_JOIN_POOL.submit(forkJoinTask);
                } catch (Throwable error) {
                    forkJoinTask.completeExceptionally(error);
                }
            }
        }

        synchronized (executeGroups) {
            LongList toRemove = new LongArrayList();
            for (final ExecuteGroup group : executeGroups.values()) {
                if (group.isSubmitted()) {
                    continue;
                }
                if (group.isEmpty()) {
                    toRemove.add(group.getGroupId());
                    continue;
                }
                ActionExecutor groupExecutor = new ActionExecutor(() -> {
                    ActionExecutor actionExecutor;
                    while ((actionExecutor = group.poll()) != null) {
                        actionExecutor.run();
                    }
                    group.setSubmitted(false);
                });
                group.setSubmitted(true);
                try {
                    execute(groupExecutor);
                } catch (Throwable error) {
                    groupExecutor.cancel(error);
                    ActionExecutor task;
                    while ((task = group.poll()) != null) task.cancel(error);
                    group.setSubmitted(false);
                }
                submitted.offer(groupExecutor);
            }
            LongListIterator it = toRemove.iterator();
            while (it.hasNext()) {
                executeGroups.remove(it.nextLong());
            }
        }
    }

    private boolean hasPendingExecuteGroupTasks() {
        synchronized (executeGroups) {
            for (ExecuteGroup group : executeGroups.values()) {
                if (!group.isSubmitted() && !group.isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }

    public class TaskSubmitter implements Runnable {
        public volatile Thread thread = null;

        public synchronized void start() {
            if (thread != null && thread.isAlive()) {
                return;
            }
            thread = new Thread(this);
            thread.setName("MEK-TaskSubmitter");
            thread.start();
        }

        public synchronized void stopAndWait() {
            Thread stopping = thread;
            if (stopping == null || stopping == Thread.currentThread()) return;
            stopping.interrupt();
            boolean interrupted = false;
            while (stopping.isAlive()) {
                try {
                    stopping.join();
                } catch (InterruptedException error) {
                    interrupted = true;
                }
            }
            thread = null;
            if (interrupted) Thread.currentThread().interrupt();
        }

        public void unpark() {
            if (thread != null) {
                LockSupport.unpark(thread);
            }
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                if (inTick) {
                    if (!executors.isEmpty() || hasPendingExecuteGroupTasks() || !forkJoinTasks.isEmpty()) {
                        try {
                            submitTask();
                        } catch (Throwable e) {
                            Mekanism.logger.error("Task submitter failed while dispatching work", e);
                            LockSupport.parkNanos(1_000_000L);
                        }
                    } else {
                        LockSupport.park();
                    }
                } else {
                    LockSupport.park();
                }
            }
        }
    }
}
