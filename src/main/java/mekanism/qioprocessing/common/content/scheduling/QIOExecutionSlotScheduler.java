package mekanism.qioprocessing.common.content.scheduling;

import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.job.QIOCraftingJob;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic, bounded selector for frequency-level execution slots. */
public final class QIOExecutionSlotScheduler {

    private QIOExecutionSlotScheduler() {
    }

    public static int grantAvailableSlots(@Nonnull QIOProcessingNetworkData network,
          int configuredLimit, int maximumGrants, long agingInterval, long agingCap) {
        Objects.requireNonNull(network, "network");
        if (configuredLimit <= 0) {
            throw new IllegalArgumentException("configuredLimit must be positive");
        }
        if (maximumGrants <= 0) {
            throw new IllegalArgumentException("maximumGrants must be positive");
        }
        if (agingInterval <= 0 || agingCap < 0) {
            throw new IllegalArgumentException("Invalid QIO scheduling aging policy");
        }

        List<QIOCraftingJob> candidates = network.getExecutionSlotCandidates();
        if (candidates.isEmpty()) {
            return 0;
        }
        network.advanceSchedulerClock();
        long available = (long) configuredLimit - network.getActiveExecutionSlotCount();
        if (available <= 0) {
            return 0;
        }

        long schedulerClock = network.getSchedulerClock();
        List<QIOCraftingJob> ordered = new ArrayList<>(candidates);
        ordered.sort(candidateComparator(schedulerClock, agingInterval, agingCap));
        int grantLimit = (int) Math.min(Math.min(available, maximumGrants), ordered.size());
        int granted = 0;
        for (int i = 0; i < grantLimit; i++) {
            if (network.acquireExecutionSlot(ordered.get(i).getJobId(), configuredLimit) == null) {
                break;
            }
            granted++;
        }
        return granted;
    }

    @Nonnull
    static Comparator<QIOCraftingJob> candidateComparator(long schedulerClock,
          long agingInterval, long agingCap) {
        return (left, right) -> {
            int priority = Long.compare(effectivePriority(right, schedulerClock, agingInterval,
                  agingCap), effectivePriority(left, schedulerClock, agingInterval, agingCap));
            if (priority != 0) {
                return priority;
            }
            boolean leftNeverDispatched = left.getLastDispatchSequence() < 0;
            boolean rightNeverDispatched = right.getLastDispatchSequence() < 0;
            if (leftNeverDispatched != rightNeverDispatched) {
                return leftNeverDispatched ? -1 : 1;
            }
            int stableOrder = leftNeverDispatched ?
                  Long.compare(left.getEnqueueSequence(), right.getEnqueueSequence()) :
                  Long.compare(left.getLastDispatchSequence(), right.getLastDispatchSequence());
            if (stableOrder != 0) {
                return stableOrder;
            }
            int enqueue = Long.compare(left.getEnqueueSequence(), right.getEnqueueSequence());
            return enqueue != 0 ? enqueue :
                  left.getJobId().toString().compareTo(right.getJobId().toString());
        };
    }

    static long effectivePriority(QIOCraftingJob job, long schedulerClock,
          long agingInterval, long agingCap) {
        long readySince = job.getReadySinceSchedulerClock();
        if (readySince < 0 || readySince > schedulerClock) {
            throw new IllegalArgumentException("QIO scheduling candidate has an invalid readySince");
        }
        long waiting = schedulerClock - readySince;
        long bonus = Math.min(agingCap, waiting / agingInterval);
        long priority = job.getBasePriority();
        return bonus > 0 && priority > Long.MAX_VALUE - bonus ? Long.MAX_VALUE : priority + bonus;
    }
}
