package mekanism.common.recipe.cache;

import mekanism.api.IContainerTransaction;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicPlanCommitterTest {

    @Test
    void simulationFailureExecutesNothing() {
        AtomicInteger value = new AtomicInteger();
        boolean committed = AtomicPlanCommitter.commit(new DirectTransaction(), () -> true,
              new AtomicPlanCommitter.Operation(() -> false, () -> { value.incrementAndGet(); return true; },
                    value::decrementAndGet));
        assertFalse(committed);
        assertEquals(0, value.get());
    }

    @Test
    void executionFailureRollsBackEarlierOperations() {
        AtomicInteger value = new AtomicInteger();
        boolean committed = AtomicPlanCommitter.commit(new DirectTransaction(), () -> true,
              new AtomicPlanCommitter.Operation(() -> true, () -> { value.incrementAndGet(); return true; },
                    value::decrementAndGet),
              new AtomicPlanCommitter.Operation(() -> true, () -> false, () -> { }));
        assertFalse(committed);
        assertEquals(0, value.get());
    }

    @Test
    void allSuccessfulOperationsCommitTogether() {
        AtomicInteger value = new AtomicInteger();
        boolean committed = AtomicPlanCommitter.commit(new DirectTransaction(), () -> true,
              new AtomicPlanCommitter.Operation(() -> true, () -> { value.addAndGet(2); return true; },
                    () -> value.addAndGet(-2)),
              new AtomicPlanCommitter.Operation(() -> true, () -> { value.addAndGet(3); return true; },
                    () -> value.addAndGet(-3)));
        assertTrue(committed);
        assertEquals(5, value.get());
    }

    private static final class DirectTransaction implements IContainerTransaction {
        @Override public void runContainerTransaction(Runnable action) { action.run(); }
        @Override public <T> T callContainerTransaction(Supplier<T> action) { return action.get(); }
    }
}
