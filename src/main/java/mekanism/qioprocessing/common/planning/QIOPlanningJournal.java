package mekanism.qioprocessing.common.planning;

import java.util.*;

/**
 * Task-owned reversible ledger. Values must be immutable or replaced by copy-on-write.
 */
final class QIOPlanningJournal {
    private final List<Runnable> undo = new ArrayList<>();
    private int owners;

    <K, V> Map<K, V> map() {
        return new LedgerMap<>();
    }

    int retain() {
        owners++;
        return undo.size();
    }

    void release() {
        if (owners <= 0) throw new IllegalStateException("Unbalanced QIO checkpoint release");
        if (--owners == 0) undo.clear();
    }

    void rollback(int mark) {
        if (mark < 0 || mark > undo.size()) throw new IllegalStateException("Expired QIO rollback mark");
        for (int index = undo.size() - 1; index >= mark; index--) undo.get(index).run();
        undo.subList(mark, undo.size()).clear();
    }

    private void record(Runnable action) {
        if (undo.size() >= 262_144) throw new QIOPlanningLimitException("QIO branch ledger budget exceeded");
        undo.add(action);
    }

    private final class LedgerMap<K, V> extends AbstractMap<K, V> {
        private final Map<K, V> values = new TreeMap<>();

        @Override
        public Set<Entry<K, V>> entrySet() {
            return Collections.unmodifiableMap(values).entrySet();
        }

        @Override
        public V get(Object key) {
            return values.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return values.containsKey(key);
        }

        @Override
        public V put(K key, V value) {
            Objects.requireNonNull(value, "ledger value");
            V old = values.get(key);
            if (Objects.equals(old, value)) return old;
            if (owners > 0) record(() -> {
                if (old == null) values.remove(key);
                else values.put(key, old);
            });
            return values.put(key, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public V remove(Object key) {
            V old = values.get(key);
            if (old != null && owners > 0) {
                K typed = (K) key;
                record(() -> values.put(typed, old));
            }
            return values.remove(key);
        }
    }
}
