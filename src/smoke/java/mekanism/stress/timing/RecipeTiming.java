package mekanism.stress.timing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Smoke-only elapsed method time. Totals are summed thread time, not server tick wall time. */
public final class RecipeTiming {
    private static final String[] NAMES = {"authoritativeMapLookupIncludingCopy", "cacheCheckIncludingLookupAndBinding"};
    private static final Meter[] METERS = {new Meter(), new Meter()};
    private static final boolean[] INSTRUMENTED = new boolean[2];
    private static volatile boolean active;

    private RecipeTiming() { }
    public static void instrumented(int metric) { INSTRUMENTED[metric] = true; }
    public static long start() { return active ? System.nanoTime() : 0; }

    public static void finish(Object returned, long started, int metric) {
        if (started == 0) return;
        long elapsed = System.nanoTime() - started;
        Meter meter = METERS[metric];
        meter.calls.increment();
        meter.nanos.add(elapsed);
        if (returned != null) meter.nonNull.increment();
        long previous = meter.max.get();
        while (elapsed > previous && !meter.max.compareAndSet(previous, elapsed)) previous = meter.max.get();
    }

    /** Called at the joined tick boundary; fixture operations are never timed by these meters. */
    public static void begin() {
        for (int i = 0; i < METERS.length; i++) {
            if (!INSTRUMENTED[i]) throw new IllegalStateException("Recipe timing transformer was not applied: " + NAMES[i]);
            METERS[i].clear();
        }
        active = true;
    }

    public static void pause() { active = false; }
    public static void resume() { active = true; }

    public static Map<String, Object> report() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", "Elapsed time on each calling thread, successful returns only. Cache-check time includes map lookup: do not add the two totals. Instrumentation overhead is included in tick timing.");
        for (int i = 0; i < METERS.length; i++) {
            Meter meter = METERS[i];
            Map<String, Object> value = new LinkedHashMap<>();
            long calls = meter.calls.sum();
            value.put("instrumented", INSTRUMENTED[i]);
            value.put("calls", calls);
            value.put("nonNullReturns", meter.nonNull.sum());
            value.put("totalNanos", meter.nanos.sum());
            value.put("meanNanos", calls == 0 ? null : meter.nanos.sum() / (double) calls);
            value.put("maxNanos", meter.max.get());
            result.put(NAMES[i], value);
        }
        return result;
    }

    private static final class Meter {
        final LongAdder calls = new LongAdder();
        final LongAdder nanos = new LongAdder();
        final LongAdder nonNull = new LongAdder();
        final AtomicLong max = new AtomicLong();
        void clear() { calls.reset(); nanos.reset(); nonNull.reset(); max.set(0); }
    }
}
