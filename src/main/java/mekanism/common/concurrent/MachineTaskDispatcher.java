package mekanism.common.concurrent;

import mekanism.common.util.concurrent.ActionExecutor;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A bounded number of pool runners consume already-published machine tickets.
 * No batch threshold, submission delay or independent worker thread is used.
 * Consumers may briefly recheck an empty queue before retiring.
 */
final class MachineTaskDispatcher {
    private final int parallelism;
    private final Consumer<ActionExecutor> dispatch;
    // The core's generic queue factory is MPSC, so it cannot be used by these runners.
    private final ConcurrentLinkedQueue<ActionExecutor> tasks;
    private final long idleRetirementNanos;
    private final LongSupplier nanoTime;
    private int runners;
    private long machineRequests;
    private long runnerSubmissions;
    private long dispatchFailures;

    MachineTaskDispatcher(int parallelism, Consumer<ActionExecutor> dispatch) {
        this(parallelism, dispatch, new ConcurrentLinkedQueue<>());
    }

    MachineTaskDispatcher(int parallelism, Consumer<ActionExecutor> dispatch,
                          ConcurrentLinkedQueue<ActionExecutor> tasks) {
        this(parallelism, dispatch, tasks,
                TimeUnit.MICROSECONDS.toNanos(Math.max(0L,
                        Math.min(200L, Long.getLong("mekanism.machine.runnerIdleMicros", 200L)))),
                System::nanoTime);
    }

    MachineTaskDispatcher(int parallelism, Consumer<ActionExecutor> dispatch,
                          ConcurrentLinkedQueue<ActionExecutor> tasks, long idleRetirementNanos, LongSupplier nanoTime) {
        if (parallelism < 1) throw new IllegalArgumentException("Runner parallelism must be positive");
        if (idleRetirementNanos < 0 || idleRetirementNanos > 200_000L)
            throw new IllegalArgumentException("Runner empty-queue window must be 0..200 microseconds");
        this.parallelism = parallelism;
        this.dispatch = Objects.requireNonNull(dispatch);
        this.tasks = Objects.requireNonNull(tasks);
        this.idleRetirementNanos = idleRetirementNanos;
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    /**
     * The owner registers the task's completion ticket before calling this method.
     * Serialize admission with runner retirement and pool acceptance. Workers never
     * hold this monitor while running a machine action or acquiring its container lock.
     */
    synchronized void submit(ActionExecutor task) {
        tasks.offer(task);
        machineRequests++;
        if (runners == parallelism || tasks.isEmpty()) return;
        ActionExecutor runner = null;
        runners++;
        try {
            runner = new ActionExecutor(this::drain);
            dispatch.accept(runner);
            runnerSubmissions++;
        } catch (RuntimeException | Error failure) {
            runners--;
            dispatchFailures++;
            if (runner != null) runner.completeWithoutExecution();
            if (runners == 0) {
                // No accepted runner could have consumed the ticket. No other producer
                // can enter admission until removal completes. The owner releases it.
                tasks.remove(task);
                throw failure;
            }
            // Growth failed, but an already-accepted runner owns the drain obligation.
            // It cannot retire past this admission without observing the queued work.
        }
    }

    private void drain() {
        long idleStarted = 0L;
        boolean idle = false;
        for (; ; ) {
            ActionExecutor task = tasks.poll();
            if (task != null) {
                idle = false;
                try {
                    task.run();
                } catch (RuntimeException | Error reportingFailure) {
                    // ActionExecutor releases its completion latch in finally, including
                    // when failure reporting itself throws. Other tickets must still run.
                }
                continue;
            }
            // This is a consumer-side deadline, never a producer sleep or batch wait.
            // New tasks are polled immediately. Stop spinning on interruption, but
            // still perform the retirement handoff so accepted tickets are not lost.
            if (idleRetirementNanos > 0 && !Thread.currentThread().isInterrupted()) {
                long now = nanoTime.getAsLong();
                if (!idle) {
                    idle = true;
                    idleStarted = now;
                }
                if (now - idleStarted < idleRetirementNanos) continue;
            }
            synchronized (this) {
                // Admission and the final empty check share the same monitor: either
                // this runner sees the new task, or its producer starts a successor.
                if (!tasks.isEmpty()) continue;
                runners--;
                return;
            }
        }
    }

    synchronized TaskExecutor.MachineDispatchStats snapshot(long directSubmissions, boolean enabled) {
        return new TaskExecutor.MachineDispatchStats(enabled, machineRequests + directSubmissions,
                runnerSubmissions, directSubmissions, dispatchFailures, runners,
                TimeUnit.NANOSECONDS.toMicros(idleRetirementNanos));
    }
}
