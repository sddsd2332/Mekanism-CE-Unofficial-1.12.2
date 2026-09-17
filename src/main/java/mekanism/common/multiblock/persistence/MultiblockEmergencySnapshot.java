package mekanism.common.multiblock.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Detached last runtime state, conditional on the exact authority observed before an IO failure. */
final class MultiblockEmergencySnapshot {
    static final int MAX_ENTRIES = 100_000;
    private static final int MAGIC = 0x4d424553;
    private static final int VERSION = 1;
    private final List<Item> items;

    private MultiblockEmergencySnapshot(List<Item> items) { this.items = items; }

    static MultiblockEmergencySnapshot capture(ManagedCacheStore.View view, Map<UUID, byte[]> dirty,
          Map<UUID, byte[]> saving, MultiblockPersistenceSession.Transfer transfer, Set<ManagedCacheStore.SourceKey> reserved) throws IOException {
        if (dirty.size() > MAX_ENTRIES) throw new IOException("Emergency snapshot count exceeds budget");
        List<Item> items = new ArrayList<>();
        long bytes = 0;
        for (Map.Entry<UUID, byte[]> entry : dirty.entrySet()) {
            ManagedCacheStore.Entry before = view == null ? null : view.entries.get(entry.getKey());
            if (before == null) throw new IOException("Emergency snapshot has no observed authority: " + entry.getKey());
            ManagedCacheNbt.validate(entry.getValue());
            Item item = new Item();
            item.id = before.id;
            item.createdBy = before.createdBy;
            item.revision = before.revision;
            item.bindingHash = bindingHash(before.positions());
            item.beforeHash = digest(before.cache());
            byte[] pendingSave = saving.get(before.id);
            item.savingHash = pendingSave == null ? null : digest(pendingSave);
            if (transfer != null && reserved.contains(ManagedCacheStore.SourceKey.managed(before.id))) {
                item.transfer = transfer.transaction;
                item.target = transfer.target;
            }
            item.snapshot = entry.getValue().clone();
            bytes += item.snapshot.length + 256L;
            if (bytes > MultiblockJournal.MAX_PAYLOAD_BYTES - 12) throw new IOException("Emergency snapshot exceeds byte budget");
            items.add(item);
        }
        return new MultiblockEmergencySnapshot(items);
    }

    long payloadBytes() {
        long bytes = 12;
        for (Item item : items) bytes += item.snapshot.length + 256L;
        return bytes;
    }

    void persist(Path directory, String namespace) throws IOException {
        try (MultiblockJournal journal = open(directory, namespace)) {
            final byte[][] last = {null};
            journal.recover(record -> last[0] = record.payload);
            byte[] encoded = encode();
            // A previous unresolved envelope cannot be replaced with a different observation.
            if (last[0] != null && last[0].length != 0 && !Arrays.equals(last[0], encoded)) {
                throw new IOException("An unresolved multiblock emergency snapshot already exists");
            }
            if (last[0] == null || last[0].length == 0) journal.append(UUID.randomUUID(), encoded);
        }
    }

    static void restore(Path directory, String namespace, ManagedCacheStore store) throws IOException {
        try (MultiblockJournal journal = open(directory, namespace)) {
            final byte[][] last = {null};
            journal.recover(record -> last[0] = record.payload);
            if (last[0] == null || last[0].length == 0) return;
            decode(last[0]).apply(store);
            // Publish acknowledgement only after main-store durability. If interrupted before it,
            // revision+content validation below recognizes the already applied snapshot.
            journal.append(UUID.randomUUID(), new byte[0]);
            journal.checkpoint(new byte[0]);
        }
    }

    private static MultiblockJournal open(Path directory, String namespace) throws IOException {
        return new MultiblockJournal(directory.resolve("emergency"), namespace + ":emergency:v1");
    }

    private void apply(ManagedCacheStore store) throws IOException {
        ManagedCacheStore.View view = store.view();
        List<ManagedCacheStore.Update> updates = new ArrayList<>();
        for (Item item : items) {
            ManagedCacheStore.Receipt receipt = view.consumed.get(ManagedCacheStore.SourceKey.managed(item.id));
            if (receipt != null) {
                ManagedCacheStore.Entry target = view.entries.get(receipt.target);
                require(item.transfer != null && item.transfer.equals(receipt.transaction) && item.target.equals(receipt.target) &&
                      target != null && target.createdBy.equals(item.transfer), "Unexpected source consumption");
                continue;
            }
            ManagedCacheStore.Entry current = view.entries.get(item.id);
            require(current != null && item.createdBy.equals(current.createdBy) &&
                  Arrays.equals(item.bindingHash, bindingHash(current.positions())), "Missing or different authority");
            long revision = current.revision;
            byte[] currentHash = digest(current.cache());
            boolean original = revision == item.revision && Arrays.equals(currentHash, item.beforeHash);
            boolean pendingSaved = item.savingHash != null && item.revision < Long.MAX_VALUE && revision == item.revision + 1 && Arrays.equals(currentHash, item.savingHash);
            boolean alreadyApplied = Arrays.equals(currentHash, digest(item.snapshot)) &&
                  (item.revision < Long.MAX_VALUE && revision == item.revision + 1 ||
                        item.savingHash != null && item.revision < Long.MAX_VALUE - 1 && revision == item.revision + 2);
            require(original || pendingSaved || alreadyApplied, "Revision/content does not match the failed write");
            if (!alreadyApplied) updates.add(new ManagedCacheStore.Update(item.id, revision, item.snapshot));
        }
        // Validate the entire envelope before writing. A crash between batches is replayed
        // idempotently using the alreadyApplied revision/content checks above.
        List<ManagedCacheStore.Update> batch = new ArrayList<>();
        long payloadBytes = 16;
        for (ManagedCacheStore.Update update : updates) {
            long entryBytes = view.entries.get(update.id).savePayloadBytes(update.payloadBytes());
            if (batch.size() == ManagedCacheStore.MAX_SOURCES || payloadBytes + entryBytes > MultiblockJournal.MAX_PAYLOAD_BYTES) {
                store.save(UUID.randomUUID(), batch);
                batch.clear();
                payloadBytes = 16;
            }
            batch.add(update);
            payloadBytes += entryBytes;
        }
        if (!batch.isEmpty()) store.save(UUID.randomUUID(), batch);
    }

    /** Online recovery must prove that the retained runtime state was restored before publication. */
    void verifyRestored(ManagedCacheStore.View view) throws IOException {
        for (Item item : items) {
            ManagedCacheStore.Receipt receipt = view.consumed.get(ManagedCacheStore.SourceKey.managed(item.id));
            if (receipt != null) {
                ManagedCacheStore.Entry target = view.entries.get(receipt.target);
                require(item.transfer != null && item.transfer.equals(receipt.transaction) && item.target.equals(receipt.target) &&
                      target != null && target.createdBy.equals(item.transfer), "Unexpected source consumption after recovery");
                continue;
            }
            ManagedCacheStore.Entry current = view.entries.get(item.id);
            require(current != null && item.createdBy.equals(current.createdBy) &&
                  Arrays.equals(item.bindingHash, bindingHash(current.positions())), "Missing or different recovered authority");
            require(Arrays.equals(item.snapshot, current.cache()) &&
                  (item.revision < Long.MAX_VALUE && current.revision == item.revision + 1 ||
                        item.savingHash != null && item.revision < Long.MAX_VALUE - 1 && current.revision == item.revision + 2),
                  "Emergency state was not restored");
        }
    }

    private byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(items.size());
            for (Item item : items) {
                writeUuid(out, item.id);
                writeUuid(out, item.createdBy);
                out.writeLong(item.revision);
                out.write(item.bindingHash);
                out.write(item.beforeHash);
                out.writeBoolean(item.savingHash != null);
                if (item.savingHash != null) out.write(item.savingHash);
                out.writeBoolean(item.transfer != null);
                if (item.transfer != null) { writeUuid(out, item.transfer); writeUuid(out, item.target); }
                out.writeInt(item.snapshot.length);
                out.write(item.snapshot);
            }
        }
        return bytes.toByteArray();
    }

    private static MultiblockEmergencySnapshot decode(byte[] encoded) throws IOException {
        require(encoded.length <= MultiblockJournal.MAX_PAYLOAD_BYTES, "Byte budget");
        List<Item> items = new ArrayList<>();
        Set<UUID> ids = new java.util.HashSet<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            require(in.readInt() == MAGIC && in.readInt() == VERSION, "Version");
            int count = in.readInt();
            require(count >= 0 && count <= MAX_ENTRIES && count * 110L <= in.available(), "Entry count");
            for (int index = 0; index < count; index++) {
                Item item = new Item();
                item.id = readUuid(in);
                require(ids.add(item.id), "Duplicate cache");
                item.createdBy = readUuid(in);
                item.revision = in.readLong();
                require(item.revision > 0, "Revision");
                item.bindingHash = hash(in);
                item.beforeHash = hash(in);
                if (in.readBoolean()) item.savingHash = hash(in);
                if (in.readBoolean()) { item.transfer = readUuid(in); item.target = readUuid(in); }
                int size = in.readInt();
                require(size >= 0 && size <= ManagedCacheStore.MAX_CACHE_BYTES && size <= in.available(), "Snapshot size");
                item.snapshot = new byte[size];
                in.readFully(item.snapshot);
                ManagedCacheNbt.validate(item.snapshot);
                items.add(item);
            }
            require(in.available() == 0, "Trailing data");
        }
        return new MultiblockEmergencySnapshot(items);
    }

    private static byte[] bindingHash(long[] positions) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) { for (long position : positions) out.writeLong(position); }
        return digest(bytes.toByteArray());
    }

    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static byte[] hash(DataInputStream in) throws IOException { byte[] hash = new byte[32]; in.readFully(hash); return hash; }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void writeUuid(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static void require(boolean valid, String reason) throws IOException { if (!valid) throw new IOException("Invalid multiblock emergency snapshot: " + reason); }

    private static final class Item {
        UUID id, createdBy, transfer, target;
        long revision;
        byte[] bindingHash, beforeHash, savingHash, snapshot;
    }
}
