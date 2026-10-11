package mekanism.qioprocessing.common.util;

import java.util.*;
import java.util.function.Predicate;

/**
 * Owner-locked round robin with O(1) identity removal and no unloaded-host tombstones.
 */
public final class QIOIdentityWorkRing<T> {
    private final Map<T, Node<T>> nodes = new IdentityHashMap<>();
    private Node<T> head;

    public void add(T value) {
        Objects.requireNonNull(value);
        if (nodes.containsKey(value)) return;
        Node<T> node = new Node<>(value);
        nodes.put(value, node);
        if (head == null) {
            head = node;
            node.next = node.previous = node;
        } else {
            node.next = head;
            node.previous = head.previous;
            head.previous.next = node;
            head.previous = node;
        }
    }

    public void remove(T value) {
        Node<T> node = nodes.remove(value);
        if (node == null) return;
        if (node.next == node) head = null;
        else {
            node.previous.next = node.next;
            node.next.previous = node.previous;
            if (head == node) head = node.next;
        }
    }

    /**
     * Inspects at most limit entries, even if every entry is healthy/ineligible.
     */
    public List<T> next(int limit) {
        int count = Math.min(Math.max(0, limit), nodes.size());
        List<T> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(head.value);
            head = head.next;
        }
        return result;
    }

    /**
     * Detach pending work before callbacks; a callback can enqueue it again without being erased.
     */
    public List<T> drain(int limit) {
        List<T> result = next(limit);
        for (T value : result) remove(value);
        return result;
    }

    public void removeIf(Predicate<T> predicate) {
        for (T value : next(nodes.size())) if (predicate.test(value)) remove(value);
    }

    public void clear() {
        head = null;
        nodes.clear();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    private static final class Node<T> {
        final T value;
        Node<T> next, previous;

        Node(T value) {
            this.value = value;
        }
    }
}
