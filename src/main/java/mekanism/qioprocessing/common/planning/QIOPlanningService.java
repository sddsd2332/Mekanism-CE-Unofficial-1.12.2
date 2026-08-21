package mekanism.qioprocessing.common.planning;

import mekanism.common.config.MekanismConfig;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Owns the one planning pool associated with the active Minecraft server. */
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

    private QIOPlanningService() {
    }

    /** 启动后台规划执行器。 */
    public synchronized void start() {
        stop();
        executor = new QIOPlanningExecutor();
        drainedThisServerTick = 0;
    }

    /** 停止规划执行器并清理待处理任务。 */
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdown(5, TimeUnit.SECONDS);
            executor = null;
        }
        QIOTopologicalPlanner.INSTANCE.clearCache();
        drainedThisServerTick = 0;
    }

    @Nullable
    /** 返回当前规划执行器；未启动时按需启动。 */
    public synchronized QIOPlanningExecutor getExecutor() {
        return executor;
    }

    /** Submits the built-in pure-data planner to the server-wide cached worker pool. */
    @Nonnull
    /** 提交一次异步规划请求。 */
    public synchronized QIOPlanningExecutor.Submission submit(@Nonnull QIOPlanningRequest request,
          @Nonnull Consumer<QIOPlanningExecutor.PlanningResult<QIOPlanningResult>> completion) {
        if (executor == null) {
            throw new IllegalStateException("QIO planning service is not running");
        }
        return executor.submit(request, QIOPlanner.INSTANCE, completion);
    }

    /** Submits a pure-data preparation or planning task to the shared tick-budgeted pool. */
    @Nonnull
    /** 提交通用规划任务，供规划器和测试复用。 */
    public synchronized <I, O> QIOPlanningExecutor.Submission submitTask(@Nonnull I input,
          @Nonnull QIOPlanningExecutor.PlanningTask<I, O> task,
          @Nonnull Consumer<QIOPlanningExecutor.PlanningResult<O>> completion) {
        if (executor == null) {
            throw new IllegalStateException("QIO planning service is not running");
        }
        return executor.submit(input, task, completion);
    }

    @SubscribeEvent
    /** 服务端 tick 中推进规划时间片并派发完成结果。 */
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            synchronized (this) {
                drainedThisServerTick = 0;
            }
            return;
        }
        runPlanningSliceForCurrentTick();
        drainCompletedForCurrentTick();
    }

    /** Advances all active calculations using the AE2-style shared server-tick budget. */
    /** 使用当前配置运行一次规划时间片。 */
    public int runPlanningSliceForCurrentTick() {
        QIOPlanningExecutor activeExecutor;
        int milliseconds;
        synchronized (this) {
            activeExecutor = executor;
            milliseconds = MekanismConfig.current().qioProcessing.planningTimePerTick.val();
        }
        return activeExecutor == null ? 0 :
              activeExecutor.runPlanningSlice(milliseconds, TimeUnit.MILLISECONDS);
    }

    /** Publishes completed worker results without exceeding the configured per-tick commit budget. */
    /** 在主线程消费规划完成回调。 */
    public synchronized int drainCompletedForCurrentTick() {
        if (executor == null) {
            return 0;
        }
        int limit = MekanismConfig.current().qioProcessing.planningResultsPerTick.val();
        int remaining = Math.max(0, limit - drainedThisServerTick);
        if (remaining == 0) {
            return 0;
        }
        int drained = executor.drainCompleted(remaining);
        drainedThisServerTick += drained;
        return drained;
    }
}
