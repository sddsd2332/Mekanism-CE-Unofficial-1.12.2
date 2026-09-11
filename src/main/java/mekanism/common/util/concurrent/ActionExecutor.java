package mekanism.common.util.concurrent;

import io.netty.util.internal.ThrowableUtil;
import mekanism.common.Mekanism;

import java.util.concurrent.CountDownLatch;

public class ActionExecutor implements Runnable, Comparable<ActionExecutor> {

    public final Action action;

    public final int priority;
    public volatile boolean isCompleted = false;
    public volatile int usedTime = 0;
    private final CountDownLatch completion = new CountDownLatch(1);

    public ActionExecutor(Action action) {
        this(action, 0);
    }

    public ActionExecutor(Action action, int priority) {
        this.action = action;
        this.priority = priority;
    }

    public void run() {
        long start = System.nanoTime() / 1000;

        try {
            action.doAction();
        } catch (Throwable e) {
            Mekanism.logger.warn("An error occurred during asynchronous task execution!");
            Mekanism.logger.warn(ThrowableUtil.stackTraceToString(e));
        } finally {
            usedTime = (int) (System.nanoTime() / 1000 - start);
            isCompleted = true;
            completion.countDown();
        }
    }

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
        return o.priority - priority;
    }
}
