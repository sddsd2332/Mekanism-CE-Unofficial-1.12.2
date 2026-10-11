package mekanism.common.concurrent;


import io.netty.util.internal.ThrowableUtil;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import mekanism.common.Mekanism;
import mekanism.common.tile.base.TileEntitySynchronized;
import mekanism.common.util.concurrent.*;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

/**
 * Asynchronous system based on MMCE
 * Perhaps this can provide operational performance
 */
public class TaskExecutor {

    public static final int THREAD_COUNT = Math.min(Math.max(Runtime.getRuntime().availableProcessors() / 4, 4), 8);
    // A controlled direct/runner comparison can use the same core jar. Read once;
    // backend switching while machine tickets are pending is deliberately unsupported.
    private static final boolean MACHINE_RUNNERS_ENABLED =
            !"direct".equals(System.getProperty("mekanism.machine.dispatch", "runner"));

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

    private final Queue<ActionExecutor> submitted = Queues.createConcurrentQueue();

    private final Queue<ActionExecutor> executors = Queues.createConcurrentQueue();
    private final Long2ObjectMap<ExecuteGroup> executeGroups = new Long2ObjectOpenHashMap<>();

    private final Queue<ForkJoinTask<?>> forkJoinTasks = Queues.createConcurrentQueue();

    private final Queue<Action> mainThreadActions = Queues.createConcurrentQueue();
    private final Queue<TileEntitySynchronized> requireUpdateTEQueue = Queues.createConcurrentQueue();
    private final Queue<TileEntitySynchronized> requireMarkNoUpdateTEQueue = Queues.createConcurrentQueue();
    private final Queue<TileEntitySynchronized> requireSaveTEQueue = Queues.createConcurrentQueue();
    private final Queue<TileEntitySynchronized> requireUpdateComparatorOutputLevel = Queues.createConcurrentQueue();

    private final TaskSubmitter submitter = new TaskSubmitter();
    private final MachineTaskDispatcher machineTasks = new MachineTaskDispatcher(THREAD_COUNT, this::execute);
    private final LongAdder directMachineSubmissions = new LongAdder();

    private volatile boolean inTick = false;
    private volatile boolean shouldUseForkJoinPool = false;

    public void init() {
        THREAD_POOL.prestartAllCoreThreads();
        submitter.start();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.side == Side.CLIENT) {
            return;
        }
        switch (event.phase) {
            case START -> {
                inTick = true;
                submitter.wakeForWork();
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
        int executed = 0;
        long time = System.nanoTime() / 1000;

        // Async work may enqueue more async work. Drain it to a fixed point before
        // running any post-async main-thread action so container/world phases cannot overlap.
        do {
            submitTask();
            executed += awaitActionExecutors();
        } while (!executors.isEmpty() || !submitted.isEmpty() || hasPendingExecuteGroupTasks());

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
        if (requireUpdateTEQueue.isEmpty() && requireMarkNoUpdateTEQueue.isEmpty() && requireSaveTEQueue.isEmpty() && requireUpdateComparatorOutputLevel.isEmpty()) {
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
        while ((tile = requireSaveTEQueue.poll()) != null) toUpdate.add(tile);
        toUpdate.forEach(TileEntitySynchronized::markForSave);

        // updateComparatorOutputLevelSync() uses a per-tile CAS before enqueueing,
        // so this queue is already coalesced. Avoid a second ReferenceOpenHashSet
        // pass and consume each pending tile directly.
        while ((tile = requireUpdateComparatorOutputLevel.poll()) != null) {
            tile.updateComparatorOutputLevel();
        }
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
     * Dispatches ordinary machine work through immediate runners in the selected pool during a server tick.
     * Register completion before dispatch, including when a worker immediately produces more work.
     * Outside the tick, preserve the existing queued submission boundary.
     */
    public ActionExecutor addMachineTask(final Action action) {
        if (!inTick) {
            return addTask(action);
        }
        ActionExecutor executor = new ActionExecutor(action);
        submitted.offer(executor);
        try {
            if (MACHINE_RUNNERS_ENABLED) {
                machineTasks.submit(executor);
            } else {
                directMachineSubmissions.increment();
                execute(executor);
            }
        } catch (RuntimeException | Error failure) {
            // The barrier may already have taken this entry. Release it without executing
            // rejected machine work on the caller thread, and propagate the dispatch failure.
            executor.completeWithoutExecution();
            throw failure;
        }
        return executor;
    }

    /**
     * Cheap aggregate counts for boundary diagnostics; no per-machine timing probes.
     */
    public MachineDispatchStats getMachineDispatchStats() {
        return machineTasks.snapshot(directMachineSubmissions.sum(), MACHINE_RUNNERS_ENABLED);
    }

    public static final class MachineDispatchStats {
        public final boolean runnersEnabled;
        public final long machineRequests;
        public final long runnerSubmissions;
        public final long directSubmissions;
        public final long runnerDispatchFailures;
        public final int liveRunners;
        public final long runnerIdleMicros;

        MachineDispatchStats(boolean runnersEnabled, long machineRequests, long runnerSubmissions,
                             long directSubmissions, long runnerDispatchFailures, int liveRunners, long runnerIdleMicros) {
            this.runnersEnabled = runnersEnabled;
            this.machineRequests = machineRequests;
            this.runnerSubmissions = runnerSubmissions;
            this.directSubmissions = directSubmissions;
            this.runnerDispatchFailures = runnerDispatchFailures;
            this.liveRunners = liveRunners;
            this.runnerIdleMicros = runnerIdleMicros;
        }
    }

    public void addTESaveTask(TileEntitySynchronized tile) {
        requireSaveTEQueue.offer(tile);
    }

    /**
     * <p>添加一个异步操作引用，这个操作必定在本 Tick 结束前执行完毕。</p>
     *
     * @param action   要执行的异步任务
     * @param priority 优先级
     */
    public ActionExecutor addTask(final Action action, final int priority) {
        ActionExecutor actionExecutor = new ActionExecutor(action, priority);
        executors.offer(actionExecutor);
        if (inTick) {
            submitter.requestWork();
        }

        return actionExecutor;
    }

    public ActionExecutor addExecuteGroupTask(final Action action, final long groupId) {
        ActionExecutor executor;
        synchronized (executeGroups) {
            ExecuteGroup group = executeGroups.get(groupId);
            if (group == null) {
                group = new ExecuteGroup(groupId);
                executeGroups.put(groupId, group);
            }
            executor = group.offer(new ActionExecutor(action));
        }
        if (inTick) {
            submitter.requestWork();
        }
        return executor;
    }


    public <T> ForkJoinTask<T> submitForkJoinTask(final ForkJoinTask<T> task) {
        forkJoinTasks.offer(task);
        if (inTick) {
            submitter.requestWork();
        }
        return task;
    }

    /**
     * <p>添加一个同步操作引用，这个操作必定会在异步操作完成后在<strong>主线程</strong>中顺序执行。</p>
     *
     * @param action 要执行的同步任务
     */
    public void addSyncTask(final Action action) {
        mainThreadActions.offer(action);
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
            execute(executor);
            submitted.offer(executor);
        }

        ForkJoinTask<?> forkJoinTask;
        while ((forkJoinTask = forkJoinTasks.poll()) != null) {
            FORK_JOIN_POOL.submit(forkJoinTask);
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
                execute(groupExecutor);
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
        private final CoalescingWakeup wakeup = new CoalescingWakeup();
        private final AtomicBoolean wakeRequested = new AtomicBoolean();

        public synchronized void start() {
            if (thread != null && thread.isAlive()) {
                return;
            }
            wakeup.cancel();
            wakeRequested.set(false);
            thread = new Thread(this);
            thread.setName("MEK-TaskSubmitter");
            thread.start();
        }

        public void wakeForWork() {
            if (hasWork()) requestWork();
        }

        public void requestWork() {
            // Publish at most one wake request while the submitter drains or waits. The queues
            // remain authoritative, so a failed signal is harmless and the next loop observes it.
            if (wakeRequested.compareAndSet(false, true)) wakeup.signal(thread);
        }

        private boolean hasWork() {
            return inTick && (!executors.isEmpty() || !forkJoinTasks.isEmpty() || hasPendingExecuteGroupTasks());
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                if (hasWork()) {
                    try {
                        wakeRequested.set(false);
                        submitTask();
                    } catch (Throwable e) {
                        Mekanism.logger.error("Task submitter failed while dispatching work", e);
                        LockSupport.parkNanos(1_000_000L);
                    }
                } else {
                    // Publish the sleep intent BEFORE checking work again. A producer either
                    // leaves visible work for this check or deposits a permit before park.
                    // Re-arm before the indefinite park to close the producer/park race.
                    wakeup.arm();
                    wakeRequested.set(false);
                    if (!hasWork() && !Thread.currentThread().isInterrupted()) {
                        wakeup.arm();
                        if (!hasWork()) LockSupport.park();
                    }
                    wakeup.cancel();
                }
            }
        }
    }
}
