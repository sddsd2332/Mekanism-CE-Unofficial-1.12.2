package mekanism.qioprocessing.common.planning;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Bounded reuse across recipes; admitted tasks separately pin only the candidates they visit.
 */
final class QIOWorkbenchKnowledge {
    private static final QIOWeightedCache<Key, QIOWorkbenchVariant> CACHE = new QIOWeightedCache<>(
            32_768, 64 * QIOWorkbenchMemory.MIB,
            (key, value) -> 128 + QIOWorkbenchMemory.text(key.id) + value.getRetainedBytes());

    static synchronized QIOWorkbenchVariant get(Object owner, String id) {
        QIOWorkbenchVariant value = CACHE.get(new Key(owner, id));
        QIOPlanningMetrics.current().add(value == null ? QIOPlanningMetrics.Metric.VARIANT_CACHE_MISSES :
                QIOPlanningMetrics.Metric.VARIANT_CACHE_HITS, 1);
        return value;
    }

    static synchronized void put(Object owner, String id, QIOWorkbenchVariant value) {
        CACHE.put(new Key(owner, id), value);
    }

    static synchronized void refresh(Object owner, String id) {
        CACHE.refresh(new Key(owner, id));
    }

    static synchronized void clear() {
        CACHE.clear();
    }

    static TaskBudget taskBudget() {
        return QIOPlanningExecutor.taskLocal(QIOWorkbenchKnowledge.class, TaskBudget::new);
    }

    /**
     * Never evicts task knowledge. An exhausted task terminates rather than retrying forgotten failures.
     */
    static final class TaskBudget {
        private final QIOPlanningMetrics metrics = QIOPlanningMetrics.current();
        private final Map<QIOWorkbenchVariant, Long> retained = new IdentityHashMap<>();
        private long bytes;
        private boolean exceeded;

        synchronized void update(QIOWorkbenchVariant value) {
            long weight = 96 + value.getRetainedBytes();
            Long previous = retained.put(value, weight);
            bytes += weight - (previous == null ? 0 : previous);
            metrics.maximum(QIOPlanningMetrics.Metric.PEAK_KNOWLEDGE_BYTES, bytes);
            metrics.maximum(QIOPlanningMetrics.Metric.PEAK_KNOWLEDGE_ENTRIES, retained.size());
            exceeded |= retained.size() > 65_536 || bytes > QIOWorkbenchMemory.TASK_LIMIT;
        }

        synchronized void check() {
            if (exceeded) throw new QIOPlanningLimitException("WORKBENCH_TASK_KNOWLEDGE_MEMORY_OR_COUNT_LIMIT");
        }

        /**
         * Optional lookahead must decline before changing or poisoning the task's budget.
         */
        synchronized boolean tryUpdate(QIOWorkbenchVariant value, long reservedBytes) {
            long weight = 96 + value.getRetainedBytes();
            Long previous = retained.get(value);
            long growth = weight - (previous == null ? 0 : previous);
            if (exceeded || reservedBytes < 0 || reservedBytes > QIOWorkbenchMemory.TASK_LIMIT ||
                    bytes + growth > QIOWorkbenchMemory.TASK_LIMIT - reservedBytes ||
                    previous == null && retained.size() >= 65_536) return false;
            update(value);
            return true;
        }
    }

    private static final class Key {
        private final Object owner;
        private final String id;

        private Key(Object owner, String id) {
            this.owner = owner;
            this.id = id;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(owner) + id.hashCode();
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof Key key && owner == key.owner && id.equals(key.id);
        }
    }
}
