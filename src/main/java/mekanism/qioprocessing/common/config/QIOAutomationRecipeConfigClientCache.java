package mekanism.qioprocessing.common.config;

import mekanism.api.Coord4D;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Client-only request correlation for machine route configuration windows. */
public final class QIOAutomationRecipeConfigClientCache {

    public enum Status {
        OK,
        REVISION_CONFLICT,
        INVALID_TARGET,
        UNAVAILABLE
    }

    private static final Map<Key, Entry> ENTRIES = new HashMap<>();

    private QIOAutomationRecipeConfigClientCache() {
    }

    public static synchronized void expect(@Nonnull Coord4D coord,
          @Nonnull QIOAutomationRecipeConfigType type, @Nonnull UUID requestId) {
        Entry entry = ENTRIES.computeIfAbsent(Key.of(coord, type), ignored -> new Entry());
        entry.expectedRequestId = Objects.requireNonNull(requestId, "requestId");
        // The caller is explicitly starting a new read and takes ownership of any
        // previously observed broadcast invalidation.
        entry.stale = false;
    }

    public static synchronized boolean apply(@Nonnull Coord4D coord,
          @Nonnull QIOAutomationRecipeConfigType type, @Nullable UUID requestId,
          @Nonnull Status status, @Nullable QIOAutomationRecipeConfigSnapshot snapshot) {
        Entry entry = ENTRIES.computeIfAbsent(Key.of(coord, type), ignored -> new Entry());
        boolean correlatedResponse = requestId != null;
        if (correlatedResponse && !requestId.equals(entry.expectedRequestId) ||
            status == Status.UNAVAILABLE != (snapshot == null)) {
            return false;
        }
        if (snapshot != null && snapshot.getType() != type) {
            return false;
        }
        // A tile broadcasts one requester's page to every viewer. A different page from
        // the same revisions is not a data change and must not interrupt a local page
        // accumulator; a revision change still marks the view stale below.
        if (requestId == null && entry.snapshot != null && snapshot != null &&
            (snapshot.getOffset() != entry.snapshot.getOffset() ||
                  !snapshot.getQuery().equals(entry.snapshot.getQuery())) &&
            snapshot.getProfileRevision() == entry.snapshot.getProfileRevision() &&
            snapshot.getPolicyRevision() == entry.snapshot.getPolicyRevision()) {
            return false;
        }
        if (snapshot != null && entry.snapshot != null &&
            (snapshot.getProfileRevision() < entry.snapshot.getProfileRevision() ||
             snapshot.getProfileRevision() == entry.snapshot.getProfileRevision() &&
             snapshot.getPolicyRevision() < entry.snapshot.getPolicyRevision())) {
            return false;
        }
        boolean staleBeforeResponse = entry.stale;
        if (requestId == null && snapshot != null && entry.snapshot != null &&
            (snapshot.getOffset() != entry.snapshot.getOffset() ||
                  !snapshot.getQuery().equals(entry.snapshot.getQuery()))) {
            entry.stale = true;
            entry.generation = entry.generation == Long.MAX_VALUE ? 0 : entry.generation + 1;
            return true;
        }
        if (correlatedResponse) {
            entry.expectedRequestId = null;
        }
        entry.status = status;
        entry.snapshot = snapshot;
        entry.stale = staleBeforeResponse;
        entry.generation = entry.generation == Long.MAX_VALUE ? 0 : entry.generation + 1;
        return true;
    }

    @Nullable
    public static synchronized View get(@Nonnull Coord4D coord,
          @Nonnull QIOAutomationRecipeConfigType type) {
        Entry entry = ENTRIES.get(Key.of(coord, type));
        return entry == null ? null : new View(entry.status, entry.snapshot, entry.generation,
              entry.expectedRequestId != null, entry.stale);
    }

    public static synchronized void clear(@Nonnull Coord4D coord,
          @Nonnull QIOAutomationRecipeConfigType type) {
        ENTRIES.remove(Key.of(coord, type));
    }

    public static final class View {

        private final Status status;
        private final QIOAutomationRecipeConfigSnapshot snapshot;
        private final long generation;
        private final boolean pending;
        private final boolean stale;

        private View(Status status, QIOAutomationRecipeConfigSnapshot snapshot,
              long generation, boolean pending, boolean stale) {
            this.status = status;
            this.snapshot = snapshot;
            this.generation = generation;
            this.pending = pending;
            this.stale = stale;
        }

        @Nonnull
        public Status getStatus() {
            return status;
        }

        @Nullable
        public QIOAutomationRecipeConfigSnapshot getSnapshot() {
            return snapshot;
        }

        public long getGeneration() {
            return generation;
        }

        public boolean isPending() {
            return pending;
        }

        public boolean isStale() {
            return stale;
        }
    }

    private static final class Entry {

        private Status status = Status.UNAVAILABLE;
        private QIOAutomationRecipeConfigSnapshot snapshot;
        private long generation;
        private UUID expectedRequestId;
        private boolean stale;
    }

    private static final class Key {

        private final int x;
        private final int y;
        private final int z;
        private final int dimension;
        private final QIOAutomationRecipeConfigType type;

        private Key(int x, int y, int z, int dimension,
              QIOAutomationRecipeConfigType type) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.type = type;
        }

        private static Key of(Coord4D coord, QIOAutomationRecipeConfigType type) {
            Objects.requireNonNull(coord, "coord");
            return new Key(coord.x, coord.y, coord.z, coord.dimensionId,
                  Objects.requireNonNull(type, "type"));
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Key other && x == other.x && y == other.y && z == other.z &&
                  dimension == other.dimension && type == other.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z, dimension, type);
        }
    }
}
