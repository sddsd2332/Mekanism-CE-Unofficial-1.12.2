package mekanism.qioprocessing.common.planning;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongBiFunction;

/**
 * Caller supplies synchronization. Mutable values must explicitly refresh after growing.
 */
final class QIOWeightedCache<K, V> {
    private final Map<K, Entry<V>> entries = new LinkedHashMap<>(128, .75F, true);
    private final int maximumCount;
    private final long maximumBytes;
    private final ToLongBiFunction<K, V> weigh;
    private long bytes;

    QIOWeightedCache(int maximumCount, long maximumBytes, ToLongBiFunction<K, V> weigh) {
        this.maximumCount = maximumCount;
        this.maximumBytes = maximumBytes;
        this.weigh = weigh;
    }

    V get(K key) {
        Entry<V> entry = entries.get(key);
        return entry == null ? null : entry.value;
    }

    boolean containsKey(K key) {
        return entries.containsKey(key);
    }

    int size() {
        return entries.size();
    }

    Iterable<V> values() {
        return () -> entries.values().stream().map(entry -> entry.value).iterator();
    }

    boolean put(K key, V value) {
        long weight = weigh.applyAsLong(key, value);
        remove(key);
        if (weight < 0 || weight > maximumBytes) return false;
        entries.put(key, new Entry<>(value, weight));
        bytes += weight;
        Iterator<Entry<V>> iterator = entries.values().iterator();
        while (entries.size() > maximumCount || bytes > maximumBytes) {
            bytes -= iterator.next().weight;
            iterator.remove();
        }
        return true;
    }

    /**
     * Validation credentials must not rotate out while one configuration is still settling.
     */
    boolean admit(Map<K, V> additions) {
        long nextBytes = bytes;
        int nextCount = entries.size();
        Map<K, Long> weights = new LinkedHashMap<>();
        for (Map.Entry<K, V> addition : additions.entrySet()) {
            long weight = weigh.applyAsLong(addition.getKey(), addition.getValue());
            if (weight < 0 || weight > maximumBytes) return false;
            Entry<V> previous = entries.get(addition.getKey());
            nextBytes += weight - (previous == null ? 0 : previous.weight);
            if (previous == null) nextCount++;
            weights.put(addition.getKey(), weight);
        }
        if (nextBytes > maximumBytes || nextCount > maximumCount) return false;
        additions.forEach((key, value) -> entries.put(key, new Entry<>(value, weights.get(key))));
        bytes = nextBytes;
        return true;
    }

    void refresh(K key) {
        V value = get(key);
        if (value != null) put(key, value);
    }

    void remove(K key) {
        Entry<V> old = entries.remove(key);
        if (old != null) bytes -= old.weight;
    }

    void clear() {
        entries.clear();
        bytes = 0;
    }

    private static final class Entry<V> {
        final V value;
        final long weight;

        Entry(V value, long weight) {
            this.value = value;
            this.weight = weight;
        }
    }
}
