package mekanism.common.concurrent;

import mekanism.api.IAsyncMachinePlanner;
import mekanism.api.IAsyncPlanCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.init.Bootstrap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.common.util.concurrent.ActionExecutor;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.*;

class TaskExecutorPlanTest {

    @BeforeAll
    static void bootstrapMinecraft() { Bootstrap.register(); }

    private static final AtomicReference<Thread> WORKER = new AtomicReference<>();
    private static volatile List<String> workerPhases;
    private static volatile CountDownLatch orderLatch;
    private static final IAsyncPlanCalculator<Integer, Integer> DOUBLE_CALCULATOR = snapshot -> {
        WORKER.set(Thread.currentThread());
        workerPhases.add("calculate");
        return snapshot * 2;
    };
    private static final IAsyncPlanCalculator<String, String> ORDER_CALCULATOR = snapshot -> {
        if ("first".equals(snapshot)) {
            try {
                orderLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        } else {
            orderLatch.countDown();
        }
        return snapshot;
    };
    private static final IAsyncPlanCalculator<Integer, Integer> FAILING_CALCULATOR = snapshot -> {
        throw new IllegalStateException("boom");
    };
    private static final IAsyncPlanCalculator<Integer, Integer> IDENTITY_CALCULATOR = snapshot -> snapshot;

    @Test
    void waitsForCalculationBeforeMainThreadCommit() {
        TaskExecutor executor = new TaskExecutor();
        Thread caller = Thread.currentThread();
        WORKER.set(null);
        AtomicReference<Thread> committer = new AtomicReference<>();
        List<String> phases = Collections.synchronizedList(new ArrayList<>());
        workerPhases = phases;
        IAsyncMachinePlanner<Integer, Integer> planner = new IAsyncMachinePlanner<>() {
            @Override
            public Integer captureSnapshot() {
                return 3;
            }

            @Override
            public Integer calculatePlan(Integer snapshot) {
                return snapshot * 2;
            }

            @Override
            public IAsyncPlanCalculator<Integer, Integer> getAsyncPlanCalculator() {
                return DOUBLE_CALCULATOR;
            }

            @Override
            public void commitPlan(Integer snapshot, Integer plan) {
                phases.add("commit");
            }
        };

        executor.submitPlan(planner, planner.captureSnapshot(), plan -> {
            committer.set(Thread.currentThread());
            phases.add("commit:" + plan);
        });
        executor.addSyncTask(() -> phases.add("callback"));
        executor.executeActions();

        assertNotEquals(caller, WORKER.get());
        assertEquals(caller, committer.get());
        assertEquals(Arrays.asList("calculate", "commit:6", "callback"), phases);
    }

    @Test
    void commitsInCaptureOrderEvenWhenWorkersFinishOutOfOrder() {
        TaskExecutor executor = new TaskExecutor();
        List<String> commits = Collections.synchronizedList(new ArrayList<>());
        orderLatch = new CountDownLatch(1);
        IAsyncMachinePlanner<String, String> first = planner(ORDER_CALCULATOR);
        IAsyncMachinePlanner<String, String> second = planner(ORDER_CALCULATOR);

        executor.submitPlan(first, "first", commits::add);
        executor.submitPlan(second, "second", commits::add);
        executor.executeActions();

        assertEquals(Arrays.asList("first", "second"), commits);
    }

    @Test
    void ownerSlotIsReleasedWhenCalculationFails() {
        TaskExecutor executor = new TaskExecutor();
        Object owner = new Object();
        IAsyncMachinePlanner<Integer, Integer> failing = new IAsyncMachinePlanner<>() {
            @Override public Integer captureSnapshot() { return 1; }
            @Override public Integer calculatePlan(Integer snapshot) { throw new IllegalStateException("boom"); }
            @Override public IAsyncPlanCalculator<Integer, Integer> getAsyncPlanCalculator() { return FAILING_CALCULATOR; }
            @Override public void commitPlan(Integer snapshot, Integer plan) { }
        };
        executor.submitPlan(owner, failing, 1, ignored -> { });
        executor.executeActions();
        assertEquals(false, executor.hasPendingPlan(owner));
    }

    @Test
    void ownerSlotIsReleasedWhenValidationOrDiscardThrows() {
        for (boolean failValidation : new boolean[]{false, true}) {
            TaskExecutor executor = new TaskExecutor();
            Object owner = new Object();
            IAsyncMachinePlanner<Integer, Integer> failing = new IAsyncMachinePlanner<Integer, Integer>() {
                @Override public Integer captureSnapshot() { return 1; }
                @Override public Integer calculatePlan(Integer snapshot) { return snapshot; }
                @Override public IAsyncPlanCalculator<Integer, Integer> getAsyncPlanCalculator() { return IDENTITY_CALCULATOR; }
                @Override public void commitPlan(Integer snapshot, Integer plan) { fail("Invalid plan must not commit"); }
                @Override public boolean isPlanStillValid(Integer snapshot, Integer plan) {
                    if (failValidation) throw new IllegalStateException("validation failure");
                    return false;
                }
                @Override public void onPlanDiscarded(Integer snapshot, Integer plan, Throwable cause) {
                    throw new IllegalStateException("discard failure");
                }
            };
            executor.submitPlan(owner, failing, 1, ignored -> fail("Invalid plan must not commit"));
            executor.executeActions();
            assertFalse(executor.hasPendingPlan(owner));
        }
    }

    @Test
    void cancellationCannotReleaseReplacementOwnersReservation() {
        TaskExecutor executor = new TaskExecutor();
        Object owner = new Object();
        List<Integer> commits = new ArrayList<>();
        executor.submitPlan(owner, planner(IDENTITY_CALCULATOR), 1, commits::add);
        executor.cancelPlan(owner);
        executor.addPlanCommitTask(() -> assertTrue(executor.hasPendingPlan(owner)));
        executor.submitPlan(owner, planner(IDENTITY_CALCULATOR), 2, commits::add);
        executor.executeActions();
        assertEquals(Collections.singletonList(2), commits);
        assertFalse(executor.hasPendingPlan(owner));
    }

    @Test
    void cancelledTaskAndThrowingCommitBothReleaseOwnerSlots() {
        TaskExecutor executor = new TaskExecutor();
        Object cancelled = new Object();
        Object throwing = new Object();
        executor.submitPlan(cancelled, planner(IDENTITY_CALCULATOR), 1, ignored -> fail("Cancelled plan committed")).cancel();
        executor.submitPlan(throwing, planner(IDENTITY_CALCULATOR), 2, ignored -> { throw new IllegalStateException("commit failure"); });
        executor.executeActions();
        assertFalse(executor.hasPendingPlan(cancelled));
        assertFalse(executor.hasPendingPlan(throwing));
    }

    @Test
    void dispatchRejectionCompletesBarrierAndReleasesOwner() {
        TaskExecutor executor = new TaskExecutor() {
            @Override
            protected void execute(ActionExecutor task) { throw new RejectedExecutionException("dispatch failure"); }
        };
        Object owner = new Object();
        ActionExecutor task = executor.submitPlan(owner, planner(IDENTITY_CALCULATOR), 1, ignored -> fail("Rejected plan committed"));
        executor.executeActions();
        assertTrue(task.isCompleted);
        assertTrue(task.getFailure() instanceof RejectedExecutionException);
        assertFalse(executor.hasPendingPlan(owner));
    }

    @Test
    void shutdownRunsReservationCleanupWithoutCommittingAndRejectsNewPlans() {
        TaskExecutor executor = new TaskExecutor();
        Object owner = new Object();
        AtomicInteger committed = new AtomicInteger();
        AtomicInteger cleaned = new AtomicInteger();
        executor.submitPlan(owner, planner(IDENTITY_CALCULATOR), 1, ignored -> committed.incrementAndGet());
        executor.addPlanCommitTask(executor.reservePlanCommitSequence(), committed::incrementAndGet, cleaned::incrementAndGet);
        executor.shutdown();
        assertEquals(0, committed.get());
        assertEquals(1, cleaned.get());
        assertFalse(executor.hasPendingPlan(owner));
        assertThrows(RejectedExecutionException.class, () -> executor.submitPlan(owner, planner(IDENTITY_CALCULATOR), 2, ignored -> committed.incrementAndGet()));
        executor.resume();
        try {
            executor.executeActions();
            assertEquals(0, committed.get());
            executor.submitPlan(owner, planner(IDENTITY_CALCULATOR), 3, ignored -> committed.incrementAndGet());
            executor.executeActions();
            assertEquals(1, committed.get());
            assertFalse(executor.hasPendingPlan(owner));
        } finally {
            executor.shutdown();
        }
    }

    private static <T> IAsyncMachinePlanner<T, T> planner(IAsyncPlanCalculator<T, T> calculation) {
        return new IAsyncMachinePlanner<>() {
            @Override public T captureSnapshot() { return null; }
            @Override public T calculatePlan(T snapshot) { return calculation.calculate(snapshot); }
            @Override public IAsyncPlanCalculator<T, T> getAsyncPlanCalculator() { return calculation; }
            @Override public void commitPlan(T snapshot, T plan) { }
        };
    }

    @Test
    void fullBatchesStartDuringTileTickAndTailJoinsBeforeOrderedCommits() throws Exception {
        TaskExecutor executor = new TaskExecutor();
        CountDownLatch firstBatch = new CountDownLatch(256);
        AtomicInteger calculated = new AtomicInteger();
        AtomicInteger cleaned = new AtomicInteger();
        List<Integer> commits = new ArrayList<>();
        executor.init();
        try {
            executor.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
            for (int index = 0; index < 513; index++) {
                final int value = index;
                executor.addPlanCalculation(512 - index, () -> {
                    calculated.incrementAndGet();
                    firstBatch.countDown();
                }, () -> {
                    assertEquals(513, calculated.get(), "Commit ran before the whole tick barrier");
                    commits.add(value);
                }, cleaned::incrementAndGet);
            }
            assertTrue(firstBatch.await(5, TimeUnit.SECONDS), "Full batch waited for tick END to start");
            assertTrue(commits.isEmpty());
            executor.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
            assertEquals(513, commits.size());
            for (int index = 0; index < commits.size(); index++) assertEquals(512 - index, commits.get(index).intValue());
            assertEquals(513, cleaned.get());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shutdownJoinsBatchTailAndCleansEveryAcceptedPlanExactlyOnce() throws Exception {
        TaskExecutor executor = new TaskExecutor();
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger calculated = new AtomicInteger();
        AtomicInteger cleaned = new AtomicInteger();
        AtomicInteger committed = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            for (int index = 0; index < 10000; index++) {
                try {
                    executor.addPlanCalculation(index, calculated::incrementAndGet,
                          committed::incrementAndGet, cleaned::incrementAndGet);
                    accepted.incrementAndGet();
                    started.countDown();
                } catch (RejectedExecutionException expected) {
                    break;
                }
            }
        });
        producer.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        producer.join(5000);
        assertFalse(producer.isAlive());
        assertEquals(accepted.get(), calculated.get());
        assertEquals(accepted.get(), cleaned.get());
        assertEquals(0, committed.get());
        assertThrows(RejectedExecutionException.class, () -> executor.addPlanCalculation(0,
              calculated::incrementAndGet, committed::incrementAndGet, cleaned::incrementAndGet));
        executor.shutdown();
        assertEquals(accepted.get(), cleaned.get());
    }

    @Test
    void batchedCallbackMayQueueAnotherCommitWithinSameDrain() {
        TaskExecutor executor = new TaskExecutor();
        List<Integer> commits = new ArrayList<>();
        executor.addPlanCalculation(0, () -> { }, () -> {
            commits.add(1);
            executor.addPlanCommitTask(1, () -> commits.add(2));
        }, () -> { });
        executor.executeActions();
        assertEquals(Arrays.asList(1, 2), commits);
    }
}
