package mekanism.qioprocessing.common.planning;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOPlanningExecutorTest {

    @Test
    void cachedWorkersAcceptConcurrentJobsAndAdvanceThroughSharedSlices() throws Exception {
        QIOPlanningExecutor executor = new QIOPlanningExecutor();
        try {
            int taskCount = 12;
            AtomicBoolean release = new AtomicBoolean();
            CountDownLatch started = new CountDownLatch(taskCount);
            List<QIOPlanningExecutor.PlanningResult<Integer>> results = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < taskCount; i++) {
                QIOPlanningExecutor.Submission submission = executor.submit(i,
                      (input, cancellation) -> {
                          started.countDown();
                          while (!release.get()) {
                              cancellation.throwIfCancelled();
                              Thread.yield();
                          }
                          return input * 2;
                      }, results::add);
                assertEquals(QIOPlanningExecutor.SubmissionStatus.ACCEPTED, submission.getStatus());
            }
            assertEquals(taskCount, executor.getActiveTaskCount());
            assertEquals(0, executor.getQueuedTaskCount());
            assertEquals(taskCount, executor.runPlanningSlice(20, TimeUnit.MILLISECONDS));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTrue(executor.getWorkerCount() >= taskCount);

            release.set(true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (results.size() < taskCount && System.nanoTime() < deadline) {
                executor.runPlanningSlice(20, TimeUnit.MILLISECONDS);
                executor.drainCompleted(taskCount);
                Thread.yield();
            }

            assertEquals(taskCount, results.size());
            assertTrue(results.stream().allMatch(result ->
                  result.getStatus() == QIOPlanningExecutor.ResultStatus.SUCCESS));
        } finally {
            executor.close();
        }
    }

    @Test
    void cancellationIsReportedThroughTheMainThreadDrain() throws Exception {
        QIOPlanningExecutor executor = new QIOPlanningExecutor();
        try {
            CountDownLatch started = new CountDownLatch(1);
            List<QIOPlanningExecutor.PlanningResult<Integer>> results = new ArrayList<>();
            QIOPlanningExecutor.PlanningTask<Integer, Integer> task = (input, cancellation) -> {
                started.countDown();
                while (true) {
                    cancellation.throwIfCancelled();
                    Thread.sleep(5);
                }
            };
            QIOPlanningExecutor.Submission submission = executor.submit(1, task, results::add);
            executor.runPlanningSlice(1, TimeUnit.MILLISECONDS);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTrue(executor.cancel(submission.getTaskId()));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (results.isEmpty() && System.nanoTime() < deadline) {
                executor.drainCompleted(1);
                Thread.yield();
            }

            assertEquals(1, results.size());
            assertEquals(QIOPlanningExecutor.ResultStatus.CANCELLED, results.get(0).getStatus());
        } finally {
            executor.close();
        }
    }

    @Test
    void taskTimeoutIsReportedThroughTheMainThreadDrain() throws Exception {
        QIOPlanningExecutor executor = new QIOPlanningExecutor(50, TimeUnit.MILLISECONDS);
        try {
            List<QIOPlanningExecutor.PlanningResult<Integer>> results = new ArrayList<>();
            QIOPlanningExecutor.Submission submission = executor.<Integer, Integer>submit(1,
                  (input, cancellation) -> {
                      while (true) {
                          cancellation.throwIfCancelled();
                          Thread.yield();
                      }
                  }, results::add);
            assertEquals(QIOPlanningExecutor.SubmissionStatus.ACCEPTED,
                  submission.getStatus());

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (results.isEmpty() && System.nanoTime() < deadline) {
                executor.drainCompleted(1);
                Thread.yield();
            }

            assertEquals(1, results.size());
            assertEquals(QIOPlanningExecutor.ResultStatus.TIMED_OUT,
                  results.get(0).getStatus());
        } finally {
            executor.close();
        }
    }
}
