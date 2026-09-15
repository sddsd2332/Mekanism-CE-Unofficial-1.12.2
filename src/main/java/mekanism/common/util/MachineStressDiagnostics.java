package mekanism.common.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in fixture counters. Sources are registered before capture and released at shutdown. */
public final class MachineStressDiagnostics {
    public static final boolean ENABLED = Boolean.getBoolean("mekanism.machine.stress.diagnostics");
    private static final Map<Object, Source> sources = new IdentityHashMap<>();
    private static final Map<String, Long> counts = new LinkedHashMap<>();
    private static final ThreadLocal<Source> notification = new ThreadLocal<>();
    private static boolean capturing;

    private MachineStressDiagnostics() { }

    public static synchronized void register(Object object, Object owner, String category) {
        if (ENABLED && !capturing && object != null) sources.put(object, new Source(owner, category));
    }

    public static synchronized void beginTick() {
        counts.clear();
        capturing = ENABLED;
    }

    public static synchronized Map<String, Long> endTick() {
        capturing = false;
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    public static synchronized void clear() {
        capturing = false;
        sources.clear();
        counts.clear();
        notification.remove();
    }

    public static synchronized void record(Object object, String event) {
        if (!capturing) return;
        Source source = sources.get(object);
        if (source != null) increment(event + ":" + source.category);
    }

    public static synchronized Source beginNotification(Object object) {
        Source previous = notification.get();
        Source source = sources.get(object);
        notification.set(source);
        if (capturing && source != null) increment("notification:" + source.category);
        return previous;
    }

    public static void endNotification(Source previous) {
        if (previous == null) notification.remove();
        else notification.set(previous);
    }

    public static String currentSource(Object owner) {
        Source source = notification.get();
        return source != null && source.owner == owner ? source.category : "direct_or_other";
    }

    public static synchronized void recordTile(Object tile) {
        if (capturing && sources.containsKey(tile)) increment("tile_notification:" + currentSource(tile));
    }

    private static void increment(String key) { counts.merge(key, 1L, Long::sum); }

    public static final class Source {
        private final Object owner;
        private final String category;
        private Source(Object owner, String category) { this.owner = owner; this.category = category; }
    }
}
