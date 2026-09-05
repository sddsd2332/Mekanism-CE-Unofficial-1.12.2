package mekanism.common.recipe.cache;

import mekanism.api.IContainerTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Small all-or-nothing commit helper. All simulation callbacks run before any
 * execute callback; execute callbacks run inside one owner transaction and are
 * rolled back in reverse order when a callback reports/throws a failure.
 */
public final class AtomicPlanCommitter {

    private AtomicPlanCommitter() {
    }

    public static boolean commit(IContainerTransaction owner, BooleanSupplier stillValid,
          List<Operation> operations) {
        Objects.requireNonNull(owner, "transaction owner");
        Objects.requireNonNull(stillValid, "validity check");
        Objects.requireNonNull(operations, "operations");
        if (!stillValid.getAsBoolean()) return false;
        for (Operation operation : operations) {
            if (operation == null || !operation.simulate()) return false;
        }
        return owner.callContainerTransaction(() -> {
            if (!stillValid.getAsBoolean()) return false;
            List<Operation> executed = new ArrayList<>();
            boolean committed = false;
            try {
                for (Operation operation : operations) {
                    executed.add(operation);
                    if (!operation.execute()) {
                        return false;
                    }
                }
                committed = true;
                return true;
            } catch (RuntimeException error) {
                return false;
            } finally {
                if (!committed) rollback(executed);
            }
        });
    }

    public static boolean commit(IContainerTransaction owner, BooleanSupplier stillValid,
          Operation... operations) {
        List<Operation> list = new ArrayList<>();
        if (operations != null) Collections.addAll(list, operations);
        return commit(owner, stillValid, list);
    }

    private static void rollback(List<Operation> executed) {
        RuntimeException failure = null;
        for (int index = executed.size() - 1; index >= 0; index--) {
            try {
                executed.get(index).rollback();
            } catch (RuntimeException error) {
                if (failure == null) failure = new IllegalStateException("Recipe transaction rollback failed", error);
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    public static final class Operation {
        private final BooleanSupplier simulation;
        private final BooleanSupplier execution;
        private final Runnable rollback;

        public Operation(BooleanSupplier simulation, BooleanSupplier execution, Runnable rollback) {
            this.simulation = Objects.requireNonNull(simulation, "simulation");
            this.execution = Objects.requireNonNull(execution, "execution");
            this.rollback = rollback == null ? () -> { } : rollback;
        }

        public boolean simulate() { return simulation.getAsBoolean(); }
        public boolean execute() { return execution.getAsBoolean(); }
        public void rollback() { rollback.run(); }
    }
}
