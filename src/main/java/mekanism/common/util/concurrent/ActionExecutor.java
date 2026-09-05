package mekanism.common.util.concurrent;

import io.netty.util.internal.ThrowableUtil;
import mekanism.common.Mekanism;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

public class ActionExecutor implements Runnable, Comparable<ActionExecutor> {

    public final Action action;

    public final int priority;
    public volatile boolean isCompleted = false;
    public volatile int usedTime = 0;
    private final CountDownLatch completion = new CountDownLatch(1);
    private final AtomicInteger state = new AtomicInteger();
    private volatile Throwable failure;
    private volatile boolean cancelled;

    public ActionExecutor(Action action) {
        this(action, 0);
    }

    public ActionExecutor(Action action, int priority) {
        this.action = action;
        this.priority = priority;
    }

    public void run() {
        if (!state.compareAndSet(0, 1)) return;
        long start = System.nanoTime() / 1000;

        try {
            action.doAction();
        } catch (Throwable e) {
            failure = e;
            Mekanism.logger.warn("An error occurred during asynchronous task execution!");
            Mekanism.logger.warn(ThrowableUtil.stackTraceToString(e));
        } finally {
            usedTime = (int) (System.nanoTime() / 1000 - start);
            isCompleted = true;
            state.set(2);
            completion.countDown();
        }
    }

    public void cancel() {
        cancel(new CancellationException("Task was cancelled"));
    }

    public void cancel(Throwable cause) {
        cancelled = true;
        failure = cause == null ? new CancellationException("Task was cancelled") : cause;
        if (state.compareAndSet(0, 2)) {
            isCompleted = true;
            completion.countDown();
        }
    }

    public boolean isCancelled() { return cancelled; }
    public Throwable getFailure() { return failure; }

    public void awaitCompletion() {
        boolean interrupted = false;
        while (true) {
            try {
                completion.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public int compareTo(ActionExecutor o) {
        return Integer.compare(o.priority, priority);
    }
}
