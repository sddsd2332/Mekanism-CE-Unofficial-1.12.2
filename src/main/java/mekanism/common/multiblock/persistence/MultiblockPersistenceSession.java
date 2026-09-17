package mekanism.common.multiblock.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main-thread ownership coordinator for one world/manager generation. IO workers never call this
 * object. A detached commit result is published only by poll(); unloading the session suppresses
 * publication while draining accepted writes. The tile/protocol adapter must freeze live sources
 * before beginTransfer and verify the complete structure before calling bind.
 */
public final class MultiblockPersistenceSession implements AutoCloseable {
    private static final long MAX_DIRTY_BYTES = 48L * 1024 * 1024;
    private final Thread owner = Thread.currentThread();
    private final ManagedCacheIo io;
    private ManagedCacheIo.Session storage;
    private boolean recoveryLoading;
    private MultiblockEmergencySnapshot recoverySnapshot;
    private final LegacyIndexLoader legacyLoader;
    private final Path legacyFile;
    private LegacyIndexLoader.Handle legacy;
    private ManagedCacheStore.View view;
    private ManagedCacheIo.Ticket<ManagedCacheStore.View> pending;
    private boolean pendingTimingRecorded;
    private Transfer transfer;
    private Map<UUID, byte[]> saving = Collections.emptyMap();
    private final Map<UUID, byte[]> dirty = new LinkedHashMap<>();
    private final Set<ManagedCacheStore.SourceKey> reserved = new HashSet<>();
    private final Map<UUID, Object> bindings = new HashMap<>();
    private long dirtyBytes;
    private State state = State.LOADING;
    private IOException failure;
    private long acceptedTransfers, confirmedTransfers, confirmedSaves, failedOperations, successfulRecoveries;
    private long failedTransfers, recoveredCommittedTransfers, recoveredAbortedTransfers;
    private boolean transferFailureRecorded;
    private long newTargets, legacyTransfers, managedTransfers, mixedTransfers, mergeTransfers;
    private long pendingStartedNanos, acknowledgedNanos;
    private long workerNanos, workerOperations, workerQueuedNanos;
    private final long[] acceptedReasons = new long[TransferReason.values().length];
    private final long[] confirmedReasons = new long[TransferReason.values().length];

    /** Read-only session counters; elapsed time includes IO queueing and main-thread acknowledgement. */
    public Map<String, String> diagnostics() {
        checkThread();
        Map<String, String> result = new LinkedHashMap<>();
        result.put("state", state.name());
        result.put("managedCaches", Integer.toString(view == null ? 0 : view.entries.size()));
        result.put("consumedSources", Integer.toString(view == null ? 0 : view.consumed.size()));
        result.put("dirtyCaches", Integer.toString(dirty.size()));
        result.put("dirtyBytes", Long.toString(dirtyBytes));
        result.put("acceptedTransfers", Long.toString(acceptedTransfers));
        result.put("confirmedTransfers", Long.toString(confirmedTransfers));
        result.put("confirmedSaves", Long.toString(confirmedSaves));
        result.put("failedOperations", Long.toString(failedOperations));
        result.put("failedTransfers", Long.toString(failedTransfers));
        result.put("recoveredCommittedTransfers", Long.toString(recoveredCommittedTransfers));
        result.put("recoveredAbortedTransfers", Long.toString(recoveredAbortedTransfers));
        result.put("successfulRecoveries", Long.toString(successfulRecoveries));
        result.put("newTargets", Long.toString(newTargets));
        result.put("legacyTransfers", Long.toString(legacyTransfers));
        result.put("managedTransfers", Long.toString(managedTransfers));
        result.put("mixedTransfers", Long.toString(mixedTransfers));
        result.put("mergeTransfers", Long.toString(mergeTransfers));
        for (TransferReason reason : TransferReason.values()) {
            result.put("acceptedReason." + reason.name(), Long.toString(acceptedReasons[reason.ordinal()]));
            result.put("confirmedReason." + reason.name(), Long.toString(confirmedReasons[reason.ordinal()]));
        }
        result.put("acknowledgedMillis", Long.toString(acknowledgedNanos / 1_000_000));
        result.put("workerMillis", Long.toString(workerNanos / 1_000_000));
        result.put("workerOperations", Long.toString(workerOperations));
        result.put("workerQueueMillis", Long.toString(workerQueuedNanos / 1_000_000));
        result.put("legacyState", legacy == null ? "NOT_REQUESTED" : legacy.state().name());
        if (legacy != null) {
            long queueNanos = legacy.queueNanos();
            long readNanos = legacy.readNanos();
            result.put("legacyQueueMillis", Long.toString(queueNanos < 0 ? -1 : queueNanos / 1_000_000));
            result.put("legacyReadMillis", Long.toString(readNanos < 0 ? -1 : readNanos / 1_000_000));
        }
        if (legacy != null && legacy.state() == LegacyIndexLoader.State.READY) {
            // Preserve the original unique-count field; expose raw records separately.
            result.put("legacyIndexRecords", Integer.toString(legacy.index().size()));
            result.put("legacyRecordCount", Integer.toString(legacy.index().recordCount));
            result.put("legacyMissing", Boolean.toString(legacy.index().missing));
        }
        if (failure != null) result.put("failure", failure.toString());
        if (legacyFailure() != null) result.put("legacyFailure", legacyFailure().toString());
        return Collections.unmodifiableMap(result);
    }

    public MultiblockPersistenceSession(ManagedCacheIo io, LegacyIndexLoader legacyLoader, MultiblockStoragePaths paths) throws IOException {
        this(io, legacyLoader, paths.managedDirectory, paths.namespace, paths.legacyFile);
    }

    MultiblockPersistenceSession(ManagedCacheIo io, LegacyIndexLoader legacyLoader, Path directory, String namespace, Path legacyFile) throws IOException {
        this.io = io;
        storage = io.open(directory, namespace);
        this.legacyLoader = legacyLoader;
        this.legacyFile = legacyFile;
    }

    public State state() { checkThread(); return state; }
    public IOException failure() { checkThread(); return failure; }
    public IOException legacyFailure() { checkThread(); return legacy == null ? null : legacy.failure(); }
    public boolean busy() { checkThread(); return pending != null; }
    public ManagedCacheIo.Ticket<Void> closeTicket() { checkThread(); return storage.closedTicket(); }

    private void recordWorkerTiming() {
        // Failed operations retain their ticket for recovery. Count it once, including polls
        // after FAILED/CLOSING, and only after the worker has published the complete timing.
        if (pending != null && !pendingTimingRecorded && pending.isDone()) {
            long duration = pending.workerNanos();
            if (duration >= 0) {
                workerNanos += duration;
                workerQueuedNanos += pending.queuedNanos();
                workerOperations++;
            }
            pendingTimingRecorded = true;
        }
    }

    public void poll() {
        checkThread();
        recordWorkerTiming();
        if (state == State.CLOSING) {
            if (storage.closedTicket().isDone()) {
                try { storage.closedTicket().result(); } catch (IOException error) { failure = error; }
                state = State.CLOSED;
            }
            return;
        }
        if (state == State.CLOSED || state == State.FAILED) return;
        try {
            if (state == State.RECOVERING) {
                if (!recoveryLoading) {
                    if (!storage.closedTicket().isDone() || !io.isReleased(storage.directory)) return;
                    // The close ticket can contain the original uncertain-write error. Recovery
                    // still has to reopen and replay; that error is not proof of non-commit.
                    storage = io.open(storage.directory, storage.namespace);
                    recoveryLoading = true;
                }
                if (storage.state() == ManagedCacheIo.State.FAILED) throw storage.failure();
                if (storage.state() != ManagedCacheIo.State.READY) return;
                reconcileRecovery(storage.initialView());
                return;
            }
            if (storage.state() == ManagedCacheIo.State.FAILED) throw storage.failure();
            if (state == State.LOADING) {
                if (storage.state() != ManagedCacheIo.State.READY) return;
                view = storage.initialView();
                state = State.READY;
            }
            if (pending == null || !pending.isDone()) return;
            recordWorkerTiming();
            ManagedCacheStore.View committed = pending.result();
            if (transfer != null) {
                ManagedCacheStore.Entry entry = committed.entries.get(transfer.target);
                if (entry == null || !entry.createdBy.equals(transfer.transaction)) throw new IOException("Migration result does not match submitted target");
                for (ManagedCacheStore.SourceKey source : reserved) {
                    if (!source.legacy) {
                        byte[] removed = dirty.remove(source.id);
                        if (removed != null) dirtyBytes -= removed.length;
                        bindings.remove(source.id);
                    }
                }
                transfer.committed = true;
                confirmedTransfers++;
                confirmedReasons[transfer.reason.ordinal()]++;
                transfer = null;
                reserved.clear();
            } else {
                confirmedSaves++;
                for (Map.Entry<UUID, byte[]> snapshot : saving.entrySet()) {
                    // A later tick may already have replaced this dirty snapshot. Acknowledging
                    // the old save must not accidentally mark the later version as persisted.
                    if (dirty.get(snapshot.getKey()) == snapshot.getValue()) {
                        dirty.remove(snapshot.getKey());
                        dirtyBytes -= snapshot.getValue().length;
                    } else if (dirty.containsKey(snapshot.getKey())) {
                        // Give snapshots not included in this batch a turn before hot caches.
                        byte[] newer = dirty.remove(snapshot.getKey());
                        dirty.put(snapshot.getKey(), newer);
                    }
                }
            }
            view = committed;
            acknowledgedNanos += Math.max(0, System.nanoTime() - pendingStartedNanos);
            saving = Collections.emptyMap();
            pending = null;
        } catch (IOException error) {
            failedOperations++;
            if (transfer != null && !transferFailureRecorded) {
                failedTransfers++;
                transferFailureRecorded = true;
            }
            failure = error;
            state = State.FAILED;
            // Reserved sources remain frozen: a failed acknowledgement cannot undo disk state.
        }
    }

    /** Caller has stopped all live changes. Keeps snapshots and reservations until replay proves ownership. */
    public void recover() throws IOException {
        checkThread();
        if (state != State.FAILED) throw new IllegalStateException("Only a failed session can recover");
        MultiblockEmergencySnapshot emergency = captureEmergency();
        storage.closeWithFinalSnapshots(Collections.emptyMap(), emergency);
        recoverySnapshot = emergency;
        recoveryLoading = false;
        state = State.RECOVERING;
    }

    /** Retain the last paused runtime state even after IO failure; never changes frozen transfer sources. */
    public void retainFailedSnapshot(UUID id, byte[] snapshot) throws IOException {
        checkThread();
        if (state != State.FAILED) throw new IllegalStateException("Session has not failed");
        if (reserved.contains(ManagedCacheStore.SourceKey.managed(id))) return;
        if (view == null || !view.entries.containsKey(id)) throw new IOException("No known authority for failed snapshot: " + id);
        putDirty(id, snapshot);
    }

    private void reconcileRecovery(ManagedCacheStore.View recovered) throws IOException {
        boolean committed = false;
        if (transfer != null) {
            ManagedCacheStore.Entry target = recovered.entries.get(transfer.target);
            committed = target != null;
            if (committed && (!target.createdBy.equals(transfer.transaction) || !target.matchesBinding(transfer.positions))) {
                throw new IOException("Recovered target does not match failed transfer");
            }
            for (ManagedCacheStore.SourceKey source : reserved) {
                ManagedCacheStore.Receipt receipt = recovered.consumed.get(source);
                if (committed) {
                    if (receipt == null || !receipt.transaction.equals(transfer.transaction) || !receipt.target.equals(transfer.target)) {
                        throw new IOException("Recovered transfer has inconsistent source consumption");
                    }
                } else if (receipt != null) {
                    throw new IOException("Failed transfer source was consumed by an unexpected transaction");
                }
            }
        }
        if (recoverySnapshot != null) recoverySnapshot.verifyRestored(recovered);
        dirty.clear();
        dirtyBytes = 0;
        recoverySnapshot = null;
        if (transfer != null) {
            transfer.committed = committed;
            transfer.aborted = !committed;
            if (committed) {
                recoveredCommittedTransfers++;
                confirmedTransfers++;
                confirmedReasons[transfer.reason.ordinal()]++;
            } else {
                recoveredAbortedTransfers++;
            }
        }
        transfer = null;
        reserved.clear();
        bindings.clear();
        saving = Collections.emptyMap();
        pending = null;
        view = recovered;
        failure = null;
        state = State.READY;
        successfulRecoveries++;
    }

    /** Pure managed structures do not need to load the historical tombstone file. */
    public LegacyStatus legacyStatus(UUID id) {
        requireReady();
        ManagedCacheStore.SourceKey key = ManagedCacheStore.SourceKey.legacy(id);
        if (reserved.contains(key)) return LegacyStatus.RESERVED;
        if (view.consumed.containsKey(key)) return LegacyStatus.CONSUMED;
        if (view.entries.containsKey(id) || view.consumed.containsKey(ManagedCacheStore.SourceKey.managed(id))) return LegacyStatus.CONFLICT;
        if (legacy == null) legacy = legacyLoader.open(legacyFile);
        switch (legacy.state()) {
            case READY: return legacy.index().contains(id) ? LegacyStatus.INVALIDATED : LegacyStatus.AVAILABLE;
            case FAILED: case CLOSED: return LegacyStatus.FAILED;
            default: return LegacyStatus.LOADING;
        }
    }

    /** Explicit retry only; a failed index is not resubmitted from each structure tick. */
    public void retryLegacyIndex() {
        requireReady();
        if (legacy != null && legacy.state() == LegacyIndexLoader.State.FAILED) {
            legacy.close();
            legacy = legacyLoader.open(legacyFile);
        }
    }

    public ManagedCacheStore.Entry entry(UUID id) {
        requireReady();
        return view.entries.get(id);
    }

    public ManagedCacheStore.Entry findByBinding(long[] positions) throws IOException {
        requireReady();
        return view.findByBinding(positions);
    }

    public boolean reserved(ManagedCacheStore.SourceKey source) {
        checkThread();
        return reserved.contains(source);
    }

    /** Missing managed authority never means an empty inventory. */
    public byte[] latestSnapshot(UUID id) throws IOException {
        requireReady();
        ManagedCacheStore.Entry entry = view.entries.get(id);
        if (entry == null) throw new IOException("Managed cache authority is missing: " + id);
        byte[] snapshot = dirty.get(id);
        return snapshot == null ? entry.cache() : snapshot.clone();
    }

    public void changed(UUID id, byte[] snapshot) throws IOException {
        requireReady();
        if (reserved.contains(ManagedCacheStore.SourceKey.managed(id))) throw new IOException("Attempt to mutate a frozen migration source: " + id);
        ManagedCacheStore.Entry current = view.entries.get(id);
        if (current == null) throw new IOException("Cannot save missing managed cache: " + id);
        ManagedCacheNbt.validate(snapshot);
        byte[] before = dirty.get(id);
        if (Arrays.equals(before == null ? current.cache() : before, snapshot)) return;
        putDirty(id, snapshot);
    }

    private void putDirty(UUID id, byte[] snapshot) throws IOException {
        ManagedCacheNbt.validate(snapshot);
        byte[] before = dirty.get(id);
        long afterBytes = dirtyBytes - (before == null ? 0 : before.length) + snapshot.length;
        int afterCount = dirty.size() + (before == null ? 1 : 0);
        if (afterBytes > MAX_DIRTY_BYTES || afterCount > MultiblockEmergencySnapshot.MAX_ENTRIES ||
              afterBytes + afterCount * 256L + 12 > MultiblockJournal.MAX_PAYLOAD_BYTES) {
            throw new MultiblockBackpressureException("Dirty multiblock cache memory budget exceeded; saving must catch up");
        }
        dirty.put(id, snapshot.clone());
        dirtyBytes = afterBytes;
    }

    /** Call after the last machine/transfer activity to capture the desired ordinary save boundary. */
    public boolean flush() throws IOException {
        requireReady();
        if (pending != null || dirty.isEmpty()) return false;
        Map<UUID, byte[]> captured = new LinkedHashMap<>();
        Collection<ManagedCacheStore.Update> updates = new ArrayList<>();
        long payloadBytes = 16;
        for (Map.Entry<UUID, byte[]> entry : dirty.entrySet()) {
            ManagedCacheStore.Entry current = view.entries.get(entry.getKey());
            if (current == null) throw new IOException("Dirty snapshot has no authority: " + entry.getKey());
            long entryBytes = current.savePayloadBytes(entry.getValue().length);
            if (updates.size() == ManagedCacheStore.MAX_SOURCES || payloadBytes + entryBytes > MultiblockJournal.MAX_PAYLOAD_BYTES) break;
            payloadBytes += entryBytes;
            captured.put(entry.getKey(), entry.getValue());
            updates.add(new ManagedCacheStore.Update(entry.getKey(), current.revision, entry.getValue()));
        }
        ManagedCacheIo.Ticket<ManagedCacheStore.View> accepted = storage.save(UUID.randomUUID(), updates);
        saving = captured;
        pending = accepted;
        pendingTimingRecorded = false;
        pendingStartedNanos = System.nanoTime();
        return true;
    }

    /**
     * The supplied snapshot has already passed machine-specific merge and capacity checks. Sources
     * must be physically frozen by the caller in the same main-thread operation. Returns null when
     * another disk operation is outstanding; callers retain their original source authority then.
     */
    public Transfer beginTransfer(Collection<ManagedCacheStore.SourceKey> sources, long[] positions, byte[] snapshot) throws IOException {
        requireReady();
        if (pending != null) return null;
        ManagedCacheNbt.validate(snapshot);
        if (positions.length == 0 || positions.length > ManagedCacheStore.MAX_BINDING_POSITIONS) throw new IOException("Invalid migration binding");
        long[] checked = positions.clone();
        Arrays.sort(checked);
        for (int i = 1; i < checked.length; i++) if (checked[i] == checked[i - 1]) throw new IOException("Duplicate migration coordinate");
        Set<UUID> ids = new HashSet<>();
        Collection<ManagedCacheStore.Source> captured = new ArrayList<>();
        for (ManagedCacheStore.SourceKey source : sources) {
            if (!ids.add(source.id)) throw new IOException("Duplicate migration source UUID");
            if (source.legacy) {
                if (legacyStatus(source.id) != LegacyStatus.AVAILABLE) throw new IOException("Legacy source is not available for migration: " + source.id);
                captured.add(ManagedCacheStore.Source.legacy(source.id));
            } else {
                ManagedCacheStore.Entry current = view.entries.get(source.id);
                if (current == null) throw new IOException("Managed migration source is missing: " + source.id);
                captured.add(ManagedCacheStore.Source.managed(source.id, current.revision));
            }
        }
        UUID target;
        do {
            target = UUID.randomUUID();
        } while (ids.contains(target) || view.entries.containsKey(target) ||
              view.consumed.containsKey(ManagedCacheStore.SourceKey.managed(target)) ||
              view.consumed.containsKey(ManagedCacheStore.SourceKey.legacy(target)));
        TransferReason reason;
        if (sources.isEmpty()) reason = TransferReason.NEW_STRUCTURE;
        else if (sources.size() > 1) reason = TransferReason.MULTIPLE_SOURCES;
        else {
            ManagedCacheStore.SourceKey source = sources.iterator().next();
            reason = source.legacy ? TransferReason.LEGACY_MIGRATION :
                  view.entries.get(source.id).matchesBinding(checked) ? TransferReason.SAME_BINDING_REPLACEMENT : TransferReason.BINDING_CHANGED;
        }
        Transfer operation = new Transfer(UUID.randomUUID(), target, checked, reason);
        ManagedCacheIo.Ticket<ManagedCacheStore.View> accepted = storage.transfer(operation.transaction, target, captured, checked, snapshot);
        reserved.addAll(sources);
        transfer = operation;
        transferFailureRecorded = false;
        pending = accepted;
        pendingTimingRecorded = false;
        pendingStartedNanos = System.nanoTime();
        acceptedTransfers++;
        acceptedReasons[reason.ordinal()]++;
        boolean hasLegacy = sources.stream().anyMatch(source -> source.legacy);
        boolean hasManaged = sources.stream().anyMatch(source -> !source.legacy);
        if (sources.isEmpty()) newTargets++;
        else if (hasLegacy && hasManaged) mixedTransfers++;
        else if (hasLegacy) legacyTransfers++;
        else managedTransfers++;
        if (sources.size() > 1) mergeTransfers++;
        return operation;
    }

    /** Resolve a reference/consumed source only at the exact last committed binding. */
    public ManagedCacheStore.Entry resolve(ManagedCacheStore.SourceKey source, long[] verifiedPositions) throws IOException {
        requireReady();
        if (reserved.contains(source)) return null;
        ManagedCacheStore.Entry current = view.resolve(source);
        if (current == null) {
            if (!source.legacy || view.consumed.containsKey(source)) throw new IOException("Referenced managed cache is missing: " + source.id);
            return null;
        }
        return current.matchesBinding(verifiedPositions) ? current : null;
    }

    /** Only a completely verified structure may bind. The token is a runtime structure identity. */
    public boolean bind(UUID id, long[] verifiedPositions, Object structureToken) throws IOException {
        requireReady();
        if (structureToken == null) throw new IllegalArgumentException("Missing structure identity");
        ManagedCacheStore.Entry entry = view.entries.get(id);
        if (entry == null) throw new IOException("Cannot bind missing managed authority: " + id);
        if (reserved.contains(ManagedCacheStore.SourceKey.managed(id)) || !entry.matchesBinding(verifiedPositions)) return false;
        Object current = bindings.get(id);
        if (current != null && current != structureToken) return false;
        bindings.put(id, structureToken);
        return true;
    }

    public void unbind(UUID id, Object structureToken) {
        checkThread();
        if (bindings.get(id) == structureToken) bindings.remove(id);
    }

    @Override
    public void close() throws IOException {
        checkThread();
        if (state == State.CLOSED || state == State.CLOSING) return;
        // Accept the close barrier before clearing memory. If enqueue fails, the caller can retain
        // this session and diagnose/retry; losing dirty state is never treated as a successful unload.
        // recover() already queued the emergency envelope. During reopen, load restores it before
        // this close barrier; before reopen, the original close is still draining that envelope.
        MultiblockEmergencySnapshot emergency = state == State.RECOVERING ? null : captureEmergency();
        storage.closeWithFinalSnapshots(Collections.emptyMap(), emergency);
        if (legacy != null) legacy.close();
        state = State.CLOSING;
        view = null;
        bindings.clear();
        dirty.clear();
        dirtyBytes = 0;
        saving = Collections.emptyMap();
        recoverySnapshot = null;
    }

    private MultiblockEmergencySnapshot captureEmergency() throws IOException {
        return dirty.isEmpty() ? null : MultiblockEmergencySnapshot.capture(view, dirty, saving, transfer, reserved);
    }

    private void requireReady() {
        checkThread();
        if (state != State.READY) throw new IllegalStateException("Multiblock ownership session is " + state, failure);
    }

    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Multiblock ownership must be accessed on its owning server thread");
    }

    public enum State { LOADING, READY, FAILED, RECOVERING, CLOSING, CLOSED }
    public enum LegacyStatus { LOADING, AVAILABLE, INVALIDATED, CONSUMED, RESERVED, CONFLICT, FAILED }
    /** Mutually exclusive observed transfer causes, not inferred player actions. */
    public enum TransferReason { NEW_STRUCTURE, LEGACY_MIGRATION, MULTIPLE_SOURCES, BINDING_CHANGED, SAME_BINDING_REPLACEMENT }

    public static final class Transfer {
        public final UUID transaction;
        public final UUID target;
        public final TransferReason reason;
        private final long[] positions;
        private boolean committed;
        private boolean aborted;

        private Transfer(UUID transaction, UUID target, long[] positions, TransferReason reason) {
            this.transaction = transaction;
            this.target = target;
            this.positions = positions;
            this.reason = reason;
        }

        public boolean committed() { return committed; }
        public boolean aborted() { return aborted; }
        public long[] positions() { return positions.clone(); }
    }
}
