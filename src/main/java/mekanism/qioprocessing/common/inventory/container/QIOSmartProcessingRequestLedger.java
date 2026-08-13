package mekanism.qioprocessing.common.inventory.container;

import mekanism.qioprocessing.common.terminal.QIOSmartProcessingPreviewSnapshot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded, session-local replay ledger for smart-processing mutations. */
public final class QIOSmartProcessingRequestLedger {

    private static final int MAX_REQUESTS = 4_096;

    public enum Status {
        NEW,
        REPLAY,
        PENDING,
        CONFLICT,
        FULL
    }

    public static final class Lookup {

        private final Status status;
        @Nullable
        private final Response response;

        private Lookup(Status status, @Nullable Response response) {
            this.status = status;
            this.response = response;
        }

        @Nonnull
        public Status getStatus() {
            return status;
        }

        @Nullable
        public Response getResponse() {
            return response;
        }
    }

    public static final class Response {

        private final String actionStatus;
        @Nullable
        private final QIOSmartProcessingPreviewSnapshot snapshot;
        @Nullable
        private final UUID jobId;

        public Response(@Nonnull String actionStatus,
              @Nullable QIOSmartProcessingPreviewSnapshot snapshot, @Nullable UUID jobId) {
            this.actionStatus = Objects.requireNonNull(actionStatus, "actionStatus");
            this.snapshot = snapshot;
            this.jobId = jobId;
        }

        @Nonnull
        public String getActionStatus() {
            return actionStatus;
        }

        @Nullable
        public QIOSmartProcessingPreviewSnapshot getSnapshot() {
            return snapshot;
        }

        @Nullable
        public UUID getJobId() {
            return jobId;
        }
    }

    private static final class Entry {

        private final String fingerprint;
        @Nullable
        private Response response;

        private Entry(String fingerprint) {
            this.fingerprint = fingerprint;
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    @Nonnull
    public synchronized Lookup begin(@Nonnull UUID requestId,
          @Nonnull String fingerprint) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Entry existing = entries.get(requestId);
        if (existing != null) {
            if (!existing.fingerprint.equals(fingerprint)) {
                return new Lookup(Status.CONFLICT, null);
            }
            return existing.response == null ? new Lookup(Status.PENDING, null) :
                  new Lookup(Status.REPLAY, existing.response);
        }
        if (entries.size() >= MAX_REQUESTS) {
            return new Lookup(Status.FULL, null);
        }
        entries.put(requestId, new Entry(fingerprint));
        return new Lookup(Status.NEW, null);
    }

    public synchronized void complete(@Nonnull UUID requestId,
          @Nonnull String fingerprint, @Nonnull Response response) {
        Entry entry = entries.get(Objects.requireNonNull(requestId, "requestId"));
        if (entry == null || !entry.fingerprint.equals(
              Objects.requireNonNull(fingerprint, "fingerprint")) || entry.response != null) {
            throw new IllegalStateException("Smart-processing request ledger completion mismatch");
        }
        entry.response = Objects.requireNonNull(response, "response");
    }

    public synchronized void abort(@Nonnull UUID requestId, @Nonnull String fingerprint) {
        Entry entry = entries.get(Objects.requireNonNull(requestId, "requestId"));
        if (entry != null && entry.response == null && entry.fingerprint.equals(
              Objects.requireNonNull(fingerprint, "fingerprint"))) {
            entries.remove(requestId);
        }
    }

    public synchronized void clear() {
        entries.clear();
    }
}
