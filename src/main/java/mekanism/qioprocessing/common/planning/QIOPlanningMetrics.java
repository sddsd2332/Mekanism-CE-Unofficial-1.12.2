package mekanism.qioprocessing.common.planning;

import net.minecraft.nbt.NBTTagCompound;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Fixed-size task diagnostics. Times use measured boundaries; CPU has no wall-clock fallback.
 */
public final class QIOPlanningMetrics {
    public enum Metric {
        WORKER_RUNS, WORKER_CPU_NS, WORKER_CPU_SAMPLES, PARTIAL_SNAPSHOTS,
        ROUTE_NS, ROUTE_CPU_NS, ROUTE_CPU_SAMPLES, ROUTE_CALLS,
        SOLVE_NS, SOLVE_CPU_NS, SOLVE_CPU_SAMPLES, SOLVE_CALLS,
        DISCOVERY_NS, DISCOVERY_CPU_NS, DISCOVERY_CPU_SAMPLES, DISCOVERY_ROUNDS,
        CANDIDATE_NS, CANDIDATE_CPU_NS, CANDIDATE_CPU_SAMPLES, CANDIDATE_CALLS,
        CANDIDATE_VISITS, CANDIDATE_GRIDS, BRANCH_ATTEMPTS, BACKTRACKS, WORKBENCH_EXACT_ROUTE_HITS,
        WORKBENCH_DIRECT_GRID_ATTEMPTS, WORKBENCH_STOCK_PRUNES,
        VARIANT_CACHE_HITS, VARIANT_CACHE_MISSES, PINNED_KNOWLEDGE_HITS,
        STRUCTURE_CACHE_HITS, STRUCTURE_CACHE_MISSES,
        TOPOLOGY_CACHE_HITS, TOPOLOGY_CACHE_MISSES,
        PREVIEW_PLAN_CACHE_HITS, PREVIEW_PLAN_CACHE_MISSES, PREVIEW_PLAN_CACHE_STORES,
        PREVIEW_PLAN_CACHE_REJECTIONS,
        TOPOLOGY_INCUMBENTS, GROUPED_ROUTE_CACHE_HITS, GROUPED_ROUTE_CACHE_MISSES,
        WORKBENCH_ALTERNATIVE_PREPARATIONS,
        FIRST_COMPLETE_PLANS, FIRST_COMPLETE_NODES, FIRST_COMPLETE_NS,
        OPTIMIZATION_NODES, OPTIMIZATION_BRANCHES, OPTIMIZATION_ELAPSED_NS, OPTIMIZATION_VALIDATION_YIELDS,
        DEMAND_BOUND_PRUNES, DEMAND_PREFIX_RETRIES,
        OPTIMIZATION_BUDGET_RETURNS, CYCLE_LOCAL_RETRIES, WORKBENCH_HEURISTIC_GRID_ATTEMPTS,
        RECURSIVE_BOOTSTRAP_RETRIES,
        VALIDATION_REQUESTS, VALIDATION_FAILURES, VALIDATION_QUEUE_NS, VALIDATION_CALL_NS,
        VALIDATION_CPU_NS, VALIDATION_CPU_SAMPLES, VERSION_RETRIES,
        PEAK_ROUTES, PEAK_RESOURCES, PEAK_PLAN_STEPS, PEAK_DEPENDENCIES, PEAK_USED_VARIANTS, PEAK_CYCLE_NODES,
        PEAK_EXPLORED_NODES, PEAK_OPERATIONS, PEAK_KNOWLEDGE_BYTES, PEAK_KNOWLEDGE_ENTRIES,
        MAIN_PREPARATION_NS, MAIN_PREPARATION_CPU_NS, MAIN_PREPARATION_CPU_SAMPLES,
        WORKER_QUEUE_NS, WORKER_NS, DELIVERY_WAIT_NS, TOTAL_NS
    }

    public enum Phase {
        ROUTE(Metric.ROUTE_NS, Metric.ROUTE_CPU_NS, Metric.ROUTE_CPU_SAMPLES, Metric.ROUTE_CALLS),
        SOLVE(Metric.SOLVE_NS, Metric.SOLVE_CPU_NS, Metric.SOLVE_CPU_SAMPLES, Metric.SOLVE_CALLS),
        DISCOVERY(Metric.DISCOVERY_NS, Metric.DISCOVERY_CPU_NS, Metric.DISCOVERY_CPU_SAMPLES, Metric.DISCOVERY_ROUNDS),
        CANDIDATE(Metric.CANDIDATE_NS, Metric.CANDIDATE_CPU_NS, Metric.CANDIDATE_CPU_SAMPLES, Metric.CANDIDATE_CALLS);
        final Metric wall, cpu, samples, calls;

        Phase(Metric wall, Metric cpu, Metric samples, Metric calls) {
            this.wall = wall;
            this.cpu = cpu;
            this.samples = samples;
            this.calls = calls;
        }
    }

    private static final ThreadMXBean CPU = ManagementFactory.getThreadMXBean();
    static final QIOPlanningMetrics NONE = new QIOPlanningMetrics(false);
    private final boolean enabled;
    private final AtomicLongArray values = new AtomicLongArray(Metric.values().length);

    public QIOPlanningMetrics() {
        this(true);
    }

    private QIOPlanningMetrics(boolean enabled) {
        this.enabled = enabled;
    }

    public static QIOPlanningMetrics current() {
        return QIOPlanningExecutor.currentMetrics();
    }

    public static long cpuNow() {
        try {
            return CPU.isCurrentThreadCpuTimeSupported() && CPU.isThreadCpuTimeEnabled() ? CPU.getCurrentThreadCpuTime() : -1;
        } catch (UnsupportedOperationException | SecurityException ignored) {
            return -1;
        }
    }

    public void add(Metric metric, long amount) {
        if (!enabled || amount <= 0) return;
        int index = metric.ordinal();
        long old, next;
        do {
            old = values.get(index);
            next = amount > Long.MAX_VALUE - old ? Long.MAX_VALUE : old + amount;
        }
        while (!values.compareAndSet(index, old, next));
    }

    public void maximum(Metric metric, long amount) {
        if (!enabled) return;
        int index = metric.ordinal();
        long old;
        do {
            old = values.get(index);
            if (amount <= old) return;
        }
        while (!values.compareAndSet(index, old, amount));
    }

    public void cpu(Metric time, Metric samples, long started) {
        if (!enabled || started < 0) return;
        long end = cpuNow();
        if (end >= started) {
            add(time, end - started);
            add(samples, 1);
        }
    }

    public Scope measure(Phase phase) {
        return new Scope(this, phase);
    }

    public Snapshot snapshot() {
        long[] copy = new long[values.length()];
        for (int index = 0; index < copy.length; index++) copy[index] = values.get(index);
        return new Snapshot(copy, enabled);
    }

    public void merge(Snapshot snapshot) {
        for (Metric metric : Metric.values()) {
            if (metric.name().startsWith("PEAK_")) maximum(metric, snapshot.get(metric));
            else add(metric, snapshot.get(metric));
        }
    }

    public void validation(QIOWorkbenchValidationService.Result result) {
        add(Metric.VALIDATION_REQUESTS, 1);
        if (!result.isValid()) add(Metric.VALIDATION_FAILURES, 1);
        add(Metric.VALIDATION_QUEUE_NS, result.getQueueNanos());
        add(Metric.VALIDATION_CALL_NS, result.getCallNanos());
        if (result.getCpuNanos() >= 0) {
            add(Metric.VALIDATION_CPU_NS, result.getCpuNanos());
            add(Metric.VALIDATION_CPU_SAMPLES, 1);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final QIOPlanningMetrics metrics;
        private final Phase phase;
        private final long wall, cpu;

        private Scope(QIOPlanningMetrics metrics, Phase phase) {
            this.metrics = metrics;
            this.phase = phase;
            wall = metrics.enabled ? System.nanoTime() : 0;
            cpu = metrics.enabled ? cpuNow() : -1;
        }

        @Override
        public void close() {
            if (!metrics.enabled) return;
            metrics.add(phase.wall, System.nanoTime() - wall);
            metrics.cpu(phase.cpu, phase.samples, cpu);
            metrics.add(phase.calls, 1);
        }
    }

    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(new long[Metric.values().length], false);
        private final long[] values;
        private final boolean present;

        private Snapshot(long[] values, boolean present) {
            this.values = values;
            this.present = present;
        }

        public long get(Metric metric) {
            return values[metric.ordinal()];
        }

        public boolean isPresent() {
            return present;
        }

        public NBTTagCompound write() {
            NBTTagCompound data = new NBTTagCompound();
            if (!present) return data;
            data.setInteger("schema", 1);
            for (Metric metric : Metric.values()) data.setLong(metric.name(), get(metric));
            return data;
        }

        public static Snapshot read(NBTTagCompound data) {
            if (data.isEmpty()) return EMPTY;
            if (data.getInteger("schema") != 1 || data.getKeySet().size() > Metric.values().length + 1) {
                return EMPTY; // Optional diagnostics must never invalidate a saved draft or plan.
            }
            long[] values = new long[Metric.values().length];
            for (Metric metric : Metric.values()) {
                long value = data.getLong(metric.name());
                if (value < 0) return EMPTY;
                values[metric.ordinal()] = value;
            }
            return new Snapshot(values, true);
        }

        @Override
        public String toString() {
            StringBuilder text = new StringBuilder();
            for (Metric metric : Metric.values())
                if (get(metric) > 0) text.append(metric).append('=').append(get(metric)).append(' ');
            return text.toString();
        }
    }
}
