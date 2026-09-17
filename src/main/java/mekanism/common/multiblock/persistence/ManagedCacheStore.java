package mekanism.common.multiblock.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Durable ownership for one resolved world/manager namespace. All calls belong to its serialized
 * IO worker. Only immutable byte/coordinate snapshots cross the main-thread boundary. Machine
 * merge rules, legacy tombstone filtering and live structure exclusivity belong to the caller;
 * this layer enforces revision checks and permanent source consumption at the commit boundary.
 */
public final class ManagedCacheStore implements AutoCloseable {
    public static final int MAX_CACHE_BYTES = 16 * 1024 * 1024;
    public static final int MAX_BINDING_POSITIONS = 100_000;
    public static final int MAX_SOURCES = 4096;
    private static final int VERSION = 1;
    private static final int TRANSFER = 1;
    private static final int SAVE = 2;
    private static final int CHECKPOINT_MAGIC = 0x4d424350;
    private static final int MAX_CHECKPOINT_RECORDS = 1_000_000;
    private final MultiblockJournal journal;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<SourceKey, Receipt> consumed = new HashMap<>();
    private boolean ready;
    private int commitsSinceCheckpoint;

    public ManagedCacheStore(Path directory, String namespace) throws IOException {
        this(new MultiblockJournal(directory, namespace));
    }

    ManagedCacheStore(MultiblockJournal journal) throws IOException {
        this.journal = journal;
        try {
            journal.recover(record -> {
                if (record.checkpoint) {
                    restoreCheckpoint(record.payload, record.checkpointPart, record.checkpointLast);
                    commitsSinceCheckpoint = 0;
                    return;
                }
                Operation operation = decode(record.payload, record.transaction);
                validate(operation);
                apply(operation);
                commitsSinceCheckpoint++;
            });
            ready = true;
        } catch (IOException | RuntimeException error) {
            try { journal.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
            throw error;
        }
    }

    public Entry get(UUID id) {
        checkReady();
        return entries.get(id);
    }

    public Receipt consumed(SourceKey source) {
        checkReady();
        return consumed.get(source);
    }

    /** Detached maps with immutable values; safe to publish after lifecycle validation. */
    public View view() {
        checkReady();
        return new View(entries, consumed);
    }

    public boolean hasDirectorySync() {
        return journal.hasDirectorySync();
    }

    /**
     * New structure: no sources. Migration/merge: all sources and the target snapshot in ONE
     * record. The caller must freeze the live sources until success or recovery, and use a fresh
     * target UUID. Prepared records alone cannot authorize target use.
     */
    public Entry transfer(UUID transaction, UUID target, Collection<Source> sources, long[] positions, byte[] cache) throws IOException {
        checkReady();
        Objects.requireNonNull(transaction, "transaction");
        List<Source> capturedSources = new ArrayList<>(sources);
        if (capturedSources.size() > MAX_SOURCES) throw new IOException("Too many multiblock transfer sources");
        Entry result = new Entry(target, 1, transaction, positions, cache);
        Operation operation = new Operation(transaction, capturedSources, Collections.singletonList(result), true);
        validate(operation);
        commit(operation);
        return result;
    }

    /** A batch is one commit; stale revisions reject the entire batch before anything is written. */
    public List<Entry> save(UUID transaction, Collection<Update> updates) throws IOException {
        checkReady();
        Objects.requireNonNull(transaction, "transaction");
        if (updates.isEmpty() || updates.size() > MAX_SOURCES) throw new IOException("Invalid multiblock save batch size");
        List<Entry> changed = new ArrayList<>();
        for (Update update : updates) {
            Entry existing = entries.get(update.id);
            if (existing == null || update.expectedRevision != existing.revision || existing.revision == Long.MAX_VALUE) {
                throw new IOException("Missing or stale managed cache revision: " + update.id);
            }
            changed.add(new Entry(update.id, existing.revision + 1, existing.createdBy, existing.positions, update.cache));
        }
        Operation operation = new Operation(transaction, Collections.emptyList(), changed, false);
        validate(operation);
        commit(operation);
        return Collections.unmodifiableList(changed);
    }

    private void commit(Operation operation) throws IOException {
        byte[] payload = encode(operation);
        try {
            if (commitsSinceCheckpoint >= 128) checkpoint();
            journal.append(operation.transaction, payload);
            apply(operation);
            commitsSinceCheckpoint++;
        } catch (IOException | RuntimeException error) {
            // The commit may already be durable. Nobody may observe pre-commit authority after
            // this point; reopening must discover which side of the boundary reached disk.
            ready = false;
            throw error;
        }
    }

    public void checkpoint() throws IOException {
        checkReady();
        if (commitsSinceCheckpoint == 0) return;
        long size = 16L + consumed.size() * 49L;
        for (Entry entry : entries.values()) size += 48L + entry.positions.length * 8L + entry.cache.length;
        require(entries.size() <= MAX_CHECKPOINT_RECORDS && consumed.size() <= MAX_CHECKPOINT_RECORDS,
              "checkpoint record count exceeds budget; no receipts were discarded");
        if (size > MultiblockJournal.MAX_PAYLOAD_BYTES) {
            try {
                journal.checkpointSegments(new CheckpointSegments());
                commitsSinceCheckpoint = 0;
            } catch (IOException | RuntimeException error) {
                ready = false;
                throw error;
            }
            return;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) size);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(CHECKPOINT_MAGIC);
            output.writeInt(VERSION);
            output.writeInt(entries.size());
            for (Entry entry : entries.values()) writeEntry(output, entry);
            output.writeInt(consumed.size());
            for (Map.Entry<SourceKey, Receipt> receipt : consumed.entrySet()) {
                output.writeBoolean(receipt.getKey().legacy);
                writeUuid(output, receipt.getKey().id);
                writeUuid(output, receipt.getValue().transaction);
                writeUuid(output, receipt.getValue().target);
            }
        }
        try {
            journal.checkpoint(bytes.toByteArray());
            commitsSinceCheckpoint = 0;
        } catch (IOException | RuntimeException error) {
            ready = false;
            throw error;
        }
    }

    private final class CheckpointSegments implements MultiblockJournal.SegmentSource {
        private final java.util.Iterator<Entry> remainingEntries = entries.values().iterator();
        private final java.util.Iterator<Map.Entry<SourceKey, Receipt>> remainingReceipts = consumed.entrySet().iterator();
        private Entry pendingEntry;

        @Override public byte[] next() throws IOException {
            if (pendingEntry == null && !remainingEntries.hasNext() && !remainingReceipts.hasNext()) return null;
            List<Entry> batch = new ArrayList<>();
            long bytes = 16;
            while (pendingEntry != null || remainingEntries.hasNext()) {
                if (pendingEntry == null) pendingEntry = remainingEntries.next();
                long size = pendingEntry.savePayloadBytes(pendingEntry.cache.length);
                if (bytes + size > 32L * 1024 * 1024) break;
                batch.add(pendingEntry);
                bytes += size;
                pendingEntry = null;
            }
            List<Map.Entry<SourceKey, Receipt>> receipts = new ArrayList<>();
            if (pendingEntry == null && !remainingEntries.hasNext()) {
                while (remainingReceipts.hasNext() && bytes + 49 <= 32L * 1024 * 1024) {
                    receipts.add(remainingReceipts.next());
                    bytes += 49;
                }
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) bytes);
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(CHECKPOINT_MAGIC);
                output.writeInt(VERSION);
                output.writeInt(batch.size());
                for (Entry entry : batch) writeEntry(output, entry);
                output.writeInt(receipts.size());
                for (Map.Entry<SourceKey, Receipt> receipt : receipts) {
                    output.writeBoolean(receipt.getKey().legacy);
                    writeUuid(output, receipt.getKey().id);
                    writeUuid(output, receipt.getValue().transaction);
                    writeUuid(output, receipt.getValue().target);
                }
            }
            return buffer.toByteArray();
        }
    }

    private void restoreCheckpoint(byte[] bytes, int part, boolean last) throws IOException {
        if (part == 0) require(entries.isEmpty() && consumed.isEmpty(), "checkpoint must precede all replay operations");
        Map<UUID, Entry> restoredEntries = entries;
        Map<SourceKey, Receipt> restoredReceipts = consumed;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            require(input.readInt() == CHECKPOINT_MAGIC && input.readInt() == VERSION, "checkpoint version");
            int count = input.readInt();
            require(count >= 0 && count <= MAX_CHECKPOINT_RECORDS && count * 48L <= input.available(), "checkpoint entry count");
            for (int i = 0; i < count; i++) {
                Entry entry = readEntry(input);
                require(!consumed.containsKey(SourceKey.legacy(entry.id)) && !consumed.containsKey(SourceKey.managed(entry.id)), "active consumed checkpoint entry");
                require(restoredEntries.put(entry.id, entry) == null, "duplicate checkpoint entry");
            }
            int receipts = input.readInt();
            require(receipts >= 0 && receipts <= MAX_CHECKPOINT_RECORDS && receipts * 49L == input.available(), "checkpoint receipt count");
            Set<UUID> sourceIds = new HashSet<>();
            for (int i = 0; i < receipts; i++) {
                int legacy = input.readUnsignedByte();
                require(legacy <= 1, "checkpoint source format");
                SourceKey source = new SourceKey(readUuid(input), legacy == 1);
                Receipt receipt = new Receipt(readUuid(input), readUuid(input));
                require(sourceIds.add(source.id) && !restoredEntries.containsKey(source.id) &&
                      !consumed.containsKey(SourceKey.legacy(source.id)) && !consumed.containsKey(SourceKey.managed(source.id)), "duplicate or still active consumed source");
                restoredReceipts.put(source, receipt);
            }
            require(input.read() == -1, "checkpoint trailing data");
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid multiblock checkpoint", error);
        }
        require(entries.size() <= MAX_CHECKPOINT_RECORDS && consumed.size() <= MAX_CHECKPOINT_RECORDS, "checkpoint aggregate record count");
        if (!last) return;
        // Validate every chain once; old receipts must end in a current authority, never a cycle
        // or a missing target that could invite a later re-import of an old snapshot.
        Set<UUID> resolved = new HashSet<>(restoredEntries.keySet());
        for (Receipt receipt : restoredReceipts.values()) {
            UUID cursor = receipt.target;
            Set<UUID> path = new HashSet<>();
            while (!resolved.contains(cursor)) {
                require(path.add(cursor), "checkpoint ownership cycle");
                Receipt next = restoredReceipts.get(SourceKey.managed(cursor));
                require(next != null, "checkpoint ownership target missing");
                cursor = next.target;
            }
            resolved.addAll(path);
        }
    }

    private void validate(Operation operation) throws IOException {
        Set<UUID> targets = new HashSet<>();
        for (Entry next : operation.entries) {
            require(targets.add(next.id), "duplicate target");
            Entry current = entries.get(next.id);
            require(!consumed.containsKey(SourceKey.managed(next.id)) && !consumed.containsKey(SourceKey.legacy(next.id)), "target ID was consumed");
            if (operation.transfer) {
                require(current == null && next.revision == 1 && next.createdBy.equals(operation.transaction), "target already exists or invalid creation");
            } else {
                require(current != null && current.revision != Long.MAX_VALUE && next.revision == current.revision + 1 &&
                      next.createdBy.equals(current.createdBy) && Arrays.equals(next.positions, current.positions), "stale save or changed binding");
            }
        }
        if (operation.transfer) {
            require(operation.entries.size() == 1, "transfer requires one target");
            Set<UUID> sourceIds = new HashSet<>();
            for (Source source : operation.sources) {
                require(sourceIds.add(source.key.id), "duplicate source UUID");
                require(!targets.contains(source.key.id), "source and target share a UUID");
                require(!consumed.containsKey(SourceKey.legacy(source.key.id)) &&
                      !consumed.containsKey(SourceKey.managed(source.key.id)), "source was already consumed");
                Entry existing = entries.get(source.key.id);
                if (source.key.legacy) {
                    require(existing == null && source.revision == 0, "legacy source collides with managed authority");
                } else {
                    require(existing != null && existing.revision == source.revision, "managed source missing or stale");
                }
            }
        } else {
            require(operation.sources.isEmpty(), "save cannot consume sources");
        }
    }

    private void apply(Operation operation) {
        if (operation.transfer) {
            UUID target = operation.entries.get(0).id;
            for (Source source : operation.sources) {
                consumed.put(source.key, new Receipt(operation.transaction, target));
                if (!source.key.legacy) entries.remove(source.key.id);
            }
        }
        for (Entry entry : operation.entries) entries.put(entry.id, entry);
    }

    private static byte[] encode(Operation operation) throws IOException {
        long estimated = 16L + operation.sources.size() * 25L;
        for (Entry entry : operation.entries) estimated += 48L + entry.positions.length * 8L + entry.cache.length;
        require(estimated <= MultiblockJournal.MAX_PAYLOAD_BYTES, "transaction exceeds payload budget");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) estimated);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(VERSION);
            output.writeByte(operation.transfer ? TRANSFER : SAVE);
            output.writeInt(operation.sources.size());
            for (Source source : operation.sources) {
                output.writeBoolean(source.key.legacy);
                writeUuid(output, source.key.id);
                output.writeLong(source.revision);
            }
            output.writeInt(operation.entries.size());
            for (Entry entry : operation.entries) writeEntry(output, entry);
        }
        return bytes.toByteArray();
    }

    private static Operation decode(byte[] bytes, UUID transaction) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            require(input.readInt() == VERSION, "unknown ledger version");
            int type = input.readUnsignedByte();
            require(type == TRANSFER || type == SAVE, "unknown operation");
            int sourceCount = input.readInt();
            require(sourceCount >= 0 && sourceCount <= MAX_SOURCES && sourceCount * 25L <= input.available(), "source count");
            List<Source> sources = new ArrayList<>(sourceCount);
            for (int i = 0; i < sourceCount; i++) {
                int legacy = input.readUnsignedByte();
                require(legacy <= 1, "source format");
                sources.add(new Source(new SourceKey(readUuid(input), legacy == 1), input.readLong()));
            }
            int count = input.readInt();
            require(count > 0 && count <= MAX_SOURCES && (type != TRANSFER || count == 1), "entry count");
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(readEntry(input));
            }
            require(input.read() == -1, "trailing ledger bytes");
            return new Operation(transaction, sources, entries, type == TRANSFER);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid multiblock ledger record", error);
        }
    }

    private static void writeEntry(DataOutputStream output, Entry entry) throws IOException {
        writeUuid(output, entry.id);
        output.writeLong(entry.revision);
        writeUuid(output, entry.createdBy);
        output.writeInt(entry.positions.length);
        for (long position : entry.positions) output.writeLong(position);
        output.writeInt(entry.cache.length);
        output.write(entry.cache);
    }

    private static Entry readEntry(DataInputStream input) throws IOException {
        UUID id = readUuid(input);
        long revision = input.readLong();
        UUID createdBy = readUuid(input);
        int positionCount = input.readInt();
        require(positionCount > 0 && positionCount <= MAX_BINDING_POSITIONS && positionCount * 8L <= input.available(), "binding size");
        long[] positions = new long[positionCount];
        for (int j = 0; j < positionCount; j++) positions[j] = input.readLong();
        int size = input.readInt();
        require(size >= 0 && size <= MAX_CACHE_BYTES && size <= input.available(), "cache size");
        byte[] cache = new byte[size];
        input.readFully(cache);
        return new Entry(id, revision, createdBy, positions, cache);
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void require(boolean condition, String reason) throws IOException {
        if (!condition) throw new IOException("Invalid multiblock ownership operation: " + reason);
    }

    private void checkReady() {
        if (!ready) throw new IllegalStateException("Managed cache store is closed or failed; recover before use");
    }

    @Override
    public void close() throws IOException {
        ready = false;
        journal.close();
    }

    public static final class Entry {
        public final UUID id;
        public final long revision;
        public final UUID createdBy;
        private final long[] positions;
        private final int bindingHash;
        private final byte[] cache;

        private Entry(UUID id, long revision, UUID createdBy, long[] positions, byte[] cache) {
            this.id = Objects.requireNonNull(id, "id");
            this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
            if (revision <= 0 || positions.length == 0 || positions.length > MAX_BINDING_POSITIONS || cache.length > MAX_CACHE_BYTES) {
                throw new IllegalArgumentException("Invalid managed cache snapshot size/revision");
            }
            this.revision = revision;
            this.positions = positions.clone();
            Arrays.sort(this.positions);
            for (int i = 1; i < this.positions.length; i++) {
                if (this.positions[i] == this.positions[i - 1]) throw new IllegalArgumentException("Duplicate binding coordinate");
            }
            bindingHash = Arrays.hashCode(this.positions);
            this.cache = cache.clone();
        }

        public long[] positions() { return positions.clone(); }
        long savePayloadBytes(int snapshotBytes) { return 48L + positions.length * 8L + snapshotBytes; }
        public byte[] cache() { return cache.clone(); }

        public boolean matchesBinding(long[] candidate) {
            long[] sorted = candidate.clone();
            Arrays.sort(sorted);
            return Arrays.equals(positions, sorted);
        }
    }

    public static final class SourceKey {
        public final UUID id;
        public final boolean legacy;

        private SourceKey(UUID id, boolean legacy) {
            this.id = Objects.requireNonNull(id, "id");
            this.legacy = legacy;
        }

        public static SourceKey legacy(UUID id) { return new SourceKey(id, true); }
        public static SourceKey managed(UUID id) { return new SourceKey(id, false); }

        @Override public boolean equals(Object other) {
            if (!(other instanceof SourceKey)) return false;
            SourceKey key = (SourceKey) other;
            return id.equals(key.id) && legacy == key.legacy;
        }

        @Override public int hashCode() { return 31 * id.hashCode() + Boolean.hashCode(legacy); }
    }

    public static final class Source {
        public final SourceKey key;
        public final long revision;

        private Source(SourceKey key, long revision) {
            if (key.legacy ? revision != 0 : revision <= 0) throw new IllegalArgumentException("Invalid source revision");
            this.key = key;
            this.revision = revision;
        }

        public static Source legacy(UUID id) { return new Source(SourceKey.legacy(id), 0); }
        public static Source managed(UUID id, long revision) { return new Source(SourceKey.managed(id), revision); }
    }

    public static final class Update {
        public final UUID id;
        public final long expectedRevision;
        private final byte[] cache;

        public Update(UUID id, long expectedRevision, byte[] cache) {
            this.id = Objects.requireNonNull(id, "id");
            this.expectedRevision = expectedRevision;
            if (cache.length > MAX_CACHE_BYTES) throw new IllegalArgumentException("Cache exceeds budget");
            this.cache = cache.clone();
        }

        public int payloadBytes() { return cache.length; }
    }

    public static final class Receipt {
        public final UUID transaction;
        public final UUID target;

        private Receipt(UUID transaction, UUID target) {
            this.transaction = transaction;
            this.target = target;
        }
    }

    public static final class View {
        public final Map<UUID, Entry> entries;
        public final Map<SourceKey, Receipt> consumed;
        private final Map<Integer, List<Entry>> bindings = new HashMap<>();

        private View(Map<UUID, Entry> entries, Map<SourceKey, Receipt> consumed) {
            this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
            this.consumed = Collections.unmodifiableMap(new LinkedHashMap<>(consumed));
            for (Entry entry : entries.values()) {
                bindings.computeIfAbsent(entry.bindingHash, ignored -> new ArrayList<>()).add(entry);
            }
        }

        /** Missing casing references do not delete a committed binding's inventory. */
        public Entry findByBinding(long[] positions) throws IOException {
            long[] sorted = positions.clone();
            Arrays.sort(sorted);
            List<Entry> candidates = bindings.get(Arrays.hashCode(sorted));
            Entry found = null;
            if (candidates != null) for (Entry candidate : candidates) {
                if (Arrays.equals(candidate.positions, sorted)) {
                    if (found != null) throw new IOException("Multiple managed caches claim the same structure binding");
                    found = candidate;
                }
            }
            return found;
        }

        /** This resolves ownership only. Caller must still validate the live structure binding. */
        public Entry resolve(SourceKey source) throws IOException {
            Set<UUID> visited = new HashSet<>();
            SourceKey current = source;
            while (true) {
                if (!visited.add(current.id)) throw new IOException("Multiblock ownership cycle");
                Receipt receipt = consumed.get(current);
                if (receipt == null) return current.legacy ? null : entries.get(current.id);
                current = SourceKey.managed(receipt.target);
            }
        }
    }

    private static final class Operation {
        final UUID transaction;
        final List<Source> sources;
        final List<Entry> entries;
        final boolean transfer;

        Operation(UUID transaction, List<Source> sources, List<Entry> entries, boolean transfer) {
            this.transaction = transaction;
            this.sources = sources;
            this.entries = entries;
            this.transfer = transfer;
        }
    }
}
