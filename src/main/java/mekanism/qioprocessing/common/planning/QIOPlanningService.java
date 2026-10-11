package mekanism.qioprocessing.common.planning;

import mekanism.common.config.MekanismConfig;
import mekanism.common.config.QIOProcessingConfig;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Owns the one planning pool associated with the active Minecraft server.
 */

/**
 * QIO 处理模块中的 QIOPlanningService 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPlanningService {

    public static final QIOPlanningService INSTANCE = new QIOPlanningService();

    private QIOPlanningExecutor executor;
    private int drainedThisServerTick;
    private long completionNanosThisServerTick;
    private boolean draining;

    private QIOPlanningService() {
    }

    /** 启动后台规划执行器。 */
    public synchronized void start() {
        stop();
        QIOProcessingConfig config = MekanismConfig.current().qioProcessing;
        executor = new QIOPlanningExecutor(config.planningWorkerThreads.val(),
                config.planningQueueCapacity.val(), config.planningTaskTimeoutSeconds.val(),
                TimeUnit.SECONDS);
        drainedThisServerTick = 0;
        completionNanosThisServerTick = 0;
    }

    /** 停止规划执行器并清理待处理任务。 */
    public synchronized void stop() {
        if (executor != null) {
            executor.close();
            executor = null;
        }
        QIOTopologicalPlanner.INSTANCE.clearCache();
        drainedThisServerTick = 0;
        completionNanosThisServerTick = 0;
    }

    @Nullable
    /** 返回当前服务器的执行器；停服后不隐式重启。 */
    public synchronized QIOPlanningExecutor getExecutor() {
        return executor;
    }

    /** Submits the built-in pure-data planner to the bounded independent worker pool. */
    @Nonnull
    /** 提交一次异步规划请求。 */
    public synchronized QIOPlanningExecutor.Submission submit(@Nonnull QIOPlanningRequest request,
                                                              @Nonnull Consumer<QIOPlanningExecutor.PlanningResult<QIOPlanningResult>> completion) {
        if (executor == null) {
            throw new IllegalStateException("QIO planning service is not running");
        }
        return executor.submit(request, QIOPlanner.INSTANCE, completion);
    }

    /** Submits an interactive pure-data preparation or planning task. */
    @Nonnull
    /** 提交通用规划任务，供规划器和测试复用。 */
    public synchronized <I, O> QIOPlanningExecutor.Submission submitTask(@Nonnull I input,
                                                                         @Nonnull QIOPlanningExecutor.PlanningTask<I, O> task,
                                                                         @Nonnull Consumer<QIOPlanningExecutor.PlanningResult<O>> completion) {
        return submitTask(QIOPlanningExecutor.TaskKind.INTERACTIVE, input, task, completion);
    }

    @Nonnull
    public synchronized <I, O> QIOPlanningExecutor.Submission submitTask(
            @Nonnull QIOPlanningExecutor.TaskKind kind, @Nonnull I input,
            @Nonnull QIOPlanningExecutor.PlanningTask<I, O> task,
            @Nonnull Consumer<QIOPlanningExecutor.PlanningResult<O>> completion) {
        if (executor == null) {
            throw new IllegalStateException("QIO planning service is not running");
        }
        return executor.submit(kind, input, task, completion);
    }

    @SubscribeEvent
    /** 服务端 tick 派发验证续作和完成结果，后台计算不需要 tick 授时。 */
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            synchronized (this) {
                drainedThisServerTick = 0;
                completionNanosThisServerTick = 0;
            }
            return;
        }
        drainCompletedForCurrentTick();
    }

    /** Publishes completed worker results without exceeding the configured per-tick commit budget. */
    /** 在主线程消费规划完成回调。 */
    public int drainCompletedForCurrentTick() {
        QIOPlanningExecutor active;
        synchronized (this) {
            if (executor == null || draining) return 0;
            active = executor;
            draining = true;
        }
        int drained = 0;
        try {
            QIOProcessingConfig config = MekanismConfig.current().qioProcessing;
            long budget = TimeUnit.MILLISECONDS.toNanos(config.planningResultTimePerTick.val());
            while (true) {
                synchronized (this) {
                    if (executor != active || drainedThisServerTick >= config.planningResultsPerTick.val() ||
                            completionNanosThisServerTick >= budget) break;
                }
                long started = System.nanoTime();
                QIOPlanningExecutor.DrainResult result = active.drainWork(1);
                synchronized (this) {
                    if (executor != active) break;
                    drainedThisServerTick += result.work;
                    completionNanosThisServerTick += Math.max(0, System.nanoTime() - started);
                }
                drained += result.completed;
                if (result.work == 0) break;
            }
        } finally {
            synchronized (this) {
                draining = false;
            }
        }
        return drained;
    }
}
