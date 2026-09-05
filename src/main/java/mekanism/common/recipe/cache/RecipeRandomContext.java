package mekanism.common.recipe.cache;

import java.util.Random;
import java.util.function.Supplier;

/** Server-thread random sequence assigned when an immutable plan is captured. */
public final class RecipeRandomContext {

    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private RecipeRandomContext() {
    }

    public static void run(long seed, Runnable action) {
        State previous = ACTIVE.get();
        ACTIVE.set(new State(seed));
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    public static <T> T call(long seed, Supplier<T> action) {
        State previous = ACTIVE.get();
        ACTIVE.set(new State(seed));
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    public static double nextDouble(Random fallback) {
        State state = ACTIVE.get();
        return state == null ? fallback.nextDouble() :
              RecipeExecutionPlanner.randomUnit(state.seed, state.sequence++);
    }

    public static boolean isActive() {
        return ACTIVE.get() != null;
    }

    public static long deriveLaneSeed(long seed, int lane) {
        long value = seed ^ (0xD1342543DE82EF95L * (lane + 1L));
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    /** Uses an independent deterministic sequence for one lane inside a plan commit. */
    public static void runLane(int lane, Runnable action) {
        State current = ACTIVE.get();
        if (current == null) {
            action.run();
        } else {
            run(deriveLaneSeed(current.seed, lane), action);
        }
    }

    private static void restore(State previous) {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }

    private static final class State {
        private final long seed;
        private long sequence;
        private State(long seed) { this.seed = seed; }
    }
}
