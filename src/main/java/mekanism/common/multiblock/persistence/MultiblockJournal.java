package mekanism.common.multiblock.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Single-writer, append-only commit log. A transaction payload must contain BOTH target changes and
 * consumed sources. A synced preparation has no authority until its synced marker is atomically
 * published. No ordinary-move fallback, replacement of previous commits, or silent corrupt-tail
 * recovery is permitted. Minecraft objects never enter this IO layer.
 *
 * File sync and atomic rename provide process-crash recovery. Directory sync is additionally used
 * when the platform exposes it; {@link #hasDirectorySync()} reports that capability, not a promise
 * about hardware power-loss behavior. Any failed append poisons this session until full recovery.
 */
public final class MultiblockJournal implements AutoCloseable {
    static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;
    private static final int DATA_MAGIC = 0x4d424450;
    private static final int COMMIT_MAGIC = 0x4d424443;
    private static final int VERSION = 1;
    private final Path directory;
    private final String namespace;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final FileChannel directoryChannel;
    private final FaultInjector faults;
    private final FileOperations fileOperations;
    private long sequence;
    private byte[] previousHash = new byte[32];
    private boolean recovered;
    private boolean failed;
    private boolean closed;
    private String checkpointPreparation;
    private final Set<String> checkpointSegments = new HashSet<>();

    public MultiblockJournal(Path directory, String namespace) throws IOException {
        this(directory, namespace, stage -> {});
    }

    MultiblockJournal(Path directory, String namespace, FaultInjector faults) throws IOException {
        this(directory, namespace, faults, new FileOperations());
    }

    MultiblockJournal(Path directory, String namespace, FaultInjector faults, FileOperations fileOperations) throws IOException {
        if (namespace == null || namespace.isEmpty() || namespace.length() > 1024) {
            throw new IllegalArgumentException("Invalid world/manager journal namespace");
        }
        this.directory = directory.toAbsolutePath().normalize();
        this.namespace = namespace;
        this.faults = faults;
        this.fileOperations = fileOperations;
        Files.createDirectories(this.directory);
        lockChannel = FileChannel.open(this.directory.resolve("writer.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired = null;
        FileChannel directoryHandle = null;
        try {
            try {
                acquired = lockChannel.tryLock();
            } catch (OverlappingFileLockException error) {
                throw new IOException("Multiblock journal is already open: " + directory, error);
            }
            if (acquired == null) throw new IOException("Multiblock journal has another writer: " + directory);
            // The Windows provider cannot open directories as FileChannels. Do not claim a sync
            // happened there. On providers supporting it, real force() failures remain fatal.
            try {
                directoryHandle = FileChannel.open(this.directory, StandardOpenOption.READ);
            } catch (AccessDeniedException error) {
                if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) throw error;
            } catch (UnsupportedOperationException unsupported) {
                directoryHandle = null;
            }
        } catch (IOException | RuntimeException error) {
            if (directoryHandle != null) directoryHandle.close();
            if (acquired != null) acquired.release();
            lockChannel.close();
            throw error;
        }
        lock = acquired;
        directoryChannel = directoryHandle;
    }

    public boolean hasDirectorySync() {
        return directoryChannel != null;
    }

    /** Replays in commit order. Never skip a damaged committed record or a sequence gap. */
    public void recover(Replay replay) throws IOException {
        checkOpen();
        if (recovered) throw new IOException("Journal was already recovered");
        try {
            byte[] checkpoint = null;
            try {
                checkpoint = readBounded(directory.resolve("checkpoint"), 4096);
            } catch (NoSuchFileException absent) {
                // No checkpoint yet: all authoritative commits must still start at sequence one.
            }
            if (checkpoint != null) {
                long checkpointSequence;
                try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(checkpoint))) {
                    input.readInt();
                    input.readInt();
                    input.readUTF();
                    checkpointSequence = input.readLong();
                }
                require(checkpointSequence > 0, "checkpoint sequence");
                Marker marker = readMarker(checkpoint, checkpointSequence, false);
                byte[] prepared = readBounded(directory.resolve(marker.preparedName), MAX_PAYLOAD_BYTES + 8192);
                require(Arrays.equals(digest(prepared), marker.preparedHash), "checkpoint snapshot checksum");
                Record record = readPrepared(prepared, checkpointSequence, marker.transaction);
                if (marker.segmented) {
                    replaySegments(record, replay);
                } else {
                    replay.accept(new Record(record.sequence, record.transaction, record.payload, true));
                }
                sequence = checkpointSequence;
                previousHash = marker.parentHash;
                checkpointPreparation = marker.preparedName;
            }
            List<Long> commits = new ArrayList<>();
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "commit-*")) {
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    if (!name.matches("commit-[0-9]{20}")) throw new IOException("Unexpected commit filename: " + name);
                    try {
                        long parsed = Long.parseLong(name.substring(7));
                        if (parsed > sequence) commits.add(parsed);
                    } catch (NumberFormatException error) {
                        throw new IOException("Commit sequence overflow", error);
                    }
                }
            }
            commits.sort(Long::compareTo);
            for (long next : commits) {
                if (next != sequence + 1) throw new IOException("Missing multiblock commit before " + next);
                byte[] markerBytes = readBounded(commitPath(next), 4096);
                Marker marker = readMarker(markerBytes, next, true);
                byte[] prepared = readBounded(directory.resolve(marker.preparedName), MAX_PAYLOAD_BYTES + 8192);
                if (!Arrays.equals(digest(prepared), marker.preparedHash)) throw new IOException("Prepared snapshot checksum mismatch at " + next);
                Record record = readPrepared(prepared, next, marker.transaction);
                replay.accept(record);
                sequence = next;
                previousHash = digest(markerBytes);
            }
            recovered = true;
        } catch (IOException | RuntimeException error) {
            failed = true;
            throw error;
        }
    }

    /**
     * Replaces the replay prefix with a full ownership snapshot at the current committed sequence.
     * Only after publishing and syncing the checkpoint may the old prefix be removed. Recovery
     * never falls back when the published checkpoint is damaged. No source receipt is discarded.
     */
    public void checkpoint(byte[] payload) throws IOException {
        publishCheckpoint(payload, false, java.util.Collections.emptySet());
    }

    /** Produces one bounded segment at a time; null ends the snapshot. */
    public interface SegmentSource {
        byte[] next() throws IOException;
    }

    public void checkpointSegments(SegmentSource source) throws IOException {
        checkOpen();
        if (!recovered || sequence == 0) throw new IOException("Checkpoint needs a recovered, nonempty journal");
        Set<String> names = new HashSet<>();
        ByteArrayOutputStream manifest = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(manifest)) {
            byte[] payload;
            while ((payload = source.next()) != null) {
                require(names.size() < 65536 && payload.length <= MAX_PAYLOAD_BYTES, "checkpoint segment budget");
                UUID id = UUID.randomUUID();
                String name = "prepared-" + id + "-" + UUID.randomUUID() + ".bin";
                byte[] prepared = encodePrepared(sequence, id, payload);
                writeSyncedNew(directory.resolve(name), prepared);
                if (!Arrays.equals(prepared, readBounded(directory.resolve(name), MAX_PAYLOAD_BYTES + 8192))) {
                    throw new IOException("Checkpoint segment readback differs");
                }
                names.add(name);
                output.writeUTF(name);
                writeUuid(output, id);
                output.write(digest(prepared));
            }
            require(!names.isEmpty(), "empty segmented checkpoint");
            publishCheckpoint(manifest.toByteArray(), true, names);
        } catch (IOException | RuntimeException error) {
            failed = true;
            throw error;
        }
    }

    private void replaySegments(Record manifest, Replay replay) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(manifest.payload))) {
            int index = 0;
            while (input.available() > 0) {
                require(index < 65536, "checkpoint segment count");
                String name = input.readUTF();
                require(name.matches("prepared-[0-9a-f-]{36}-[0-9a-f-]{36}\\.bin") && checkpointSegments.add(name), "checkpoint segment name");
                UUID id = readUuid(input);
                byte[] hash = new byte[32];
                input.readFully(hash);
                byte[] prepared = readBounded(directory.resolve(name), MAX_PAYLOAD_BYTES + 8192);
                require(Arrays.equals(hash, digest(prepared)), "checkpoint segment checksum");
                Record part = readPrepared(prepared, manifest.sequence, id);
                replay.accept(new Record(manifest.sequence, manifest.transaction, part.payload, true, index++, input.available() == 0));
            }
            require(index > 0, "empty checkpoint manifest");
        }
    }

    private void publishCheckpoint(byte[] payload, boolean segmented, Set<String> segments) throws IOException {
        checkOpen();
        if (!recovered || sequence == 0) throw new IOException("Checkpoint needs a recovered, nonempty journal");
        if (payload.length > MAX_PAYLOAD_BYTES) throw new IOException("Checkpoint exceeds payload budget");
        UUID transaction = UUID.randomUUID();
        String preparation = "prepared-" + transaction + "-" + UUID.randomUUID() + ".bin";
        Path pending = directory.resolve("pending-" + UUID.randomUUID());
        try {
            byte[] data = encodePrepared(sequence, transaction, payload);
            writeSyncedNew(directory.resolve(preparation), data);
            if (!Arrays.equals(data, readBounded(directory.resolve(preparation), MAX_PAYLOAD_BYTES + 8192))) {
                throw new IOException("Checkpoint readback differs");
            }
            faults.at(Stage.CHECKPOINT_PREPARED);
            byte[] marker = encodeMarker(sequence, transaction, preparation, digest(data), segmented);
            writeSyncedNew(pending, marker);
            if (!Arrays.equals(marker, readBounded(pending, 4096))) throw new IOException("Checkpoint marker readback differs");
            faults.at(Stage.CHECKPOINT_BEFORE_PUBLISH);
            fileOperations.publish(pending, directory.resolve("checkpoint"), true);
            faults.at(Stage.CHECKPOINT_PUBLISHED);
            if (directoryChannel != null) directoryChannel.force(true);
            checkpointPreparation = preparation;
            checkpointSegments.clear();
            checkpointSegments.addAll(segments);
            faults.at(Stage.CHECKPOINT_BEFORE_CLEANUP);
            cleanCheckpointPrefix(sequence);
        } catch (IOException | RuntimeException error) {
            failed = true;
            throw error;
        }
    }

    private void cleanCheckpointPrefix(long through) throws IOException {
        Set<String> retained = new HashSet<>();
        retained.add(checkpointPreparation);
        retained.addAll(checkpointSegments);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "commit-*")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                require(name.matches("commit-[0-9]{20}"), "commit filename during cleanup");
                long number = Long.parseLong(name.substring(7));
                if (number <= through) Files.delete(file);
                else retained.add(readMarker(readBounded(file, 4096), number, false).preparedName);
            }
        }
        faults.at(Stage.CHECKPOINT_MARKERS_REMOVED);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if ((name.matches("prepared-[0-9a-f-]{36}-[0-9a-f-]{36}\\.bin") && !retained.contains(name)) ||
                      name.matches("pending-[0-9a-f-]{36}")) Files.delete(file);
            }
        }
        if (directoryChannel != null) directoryChannel.force(true);
        faults.at(Stage.CHECKPOINT_CLEANED);
    }

    /**
     * Call only from a serialized IO worker with an immutable snapshot. Success authorizes main
     * thread publication; failure is indeterminate and requires reopening/replay, never rollback.
     */
    public long append(UUID transaction, byte[] payload) throws IOException {
        checkOpen();
        if (!recovered) throw new IOException("Recover before appending");
        if (payload.length > MAX_PAYLOAD_BYTES) throw new IOException("Multiblock transaction exceeds payload budget");
        if (sequence == Long.MAX_VALUE) throw new IOException("Multiblock journal sequence exhausted");
        long next = sequence + 1;
        String preparedName = "prepared-" + transaction + "-" + UUID.randomUUID() + ".bin";
        Path preparedPath = directory.resolve(preparedName);
        Path temporaryMarker = directory.resolve("pending-" + UUID.randomUUID());
        try {
            byte[] prepared = encodePrepared(next, transaction, payload);
            faults.at(Stage.BEFORE_PREPARE);
            writeSyncedNew(preparedPath, prepared);
            faults.at(Stage.PREPARE_SYNCED);
            if (!Arrays.equals(prepared, readBounded(preparedPath, MAX_PAYLOAD_BYTES + 8192))) {
                throw new IOException("Multiblock preparation readback differs");
            }
            byte[] marker = encodeMarker(next, transaction, preparedName, digest(prepared));
            writeSyncedNew(temporaryMarker, marker);
            faults.at(Stage.MARKER_SYNCED);
            if (!Arrays.equals(marker, readBounded(temporaryMarker, 4096))) throw new IOException("Commit marker readback differs");
            // CREATE_NEW preparation and the exclusive writer lock prevent accidental replacement.
            if (Files.exists(commitPath(next))) throw new IOException("Commit sequence already exists: " + next);
            faults.at(Stage.BEFORE_PUBLISH);
            fileOperations.publish(temporaryMarker, commitPath(next), false);
            faults.at(Stage.PUBLISHED);
            if (directoryChannel != null) directoryChannel.force(true);
            faults.at(Stage.COMMIT_SYNCED);
            previousHash = digest(marker);
            sequence = next;
            return next;
        } catch (IOException | RuntimeException error) {
            // Even if rename succeeded immediately before an error, never retry as a new import.
            failed = true;
            throw error;
        }
    }

    private byte[] encodePrepared(long sequence, UUID transaction, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(DATA_MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(namespace);
            out.writeLong(sequence);
            writeUuid(out, transaction);
            out.writeInt(payload.length);
            out.write(payload);
        }
        return bytes.toByteArray();
    }

    private Record readPrepared(byte[] bytes, long expectedSequence, UUID transaction) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            require(input.readInt() == DATA_MAGIC && input.readInt() == VERSION, "prepared format");
            require(namespace.equals(input.readUTF()), "world/manager namespace");
            require(input.readLong() == expectedSequence && readUuid(input).equals(transaction), "prepared identity");
            int size = input.readInt();
            require(size >= 0 && size <= MAX_PAYLOAD_BYTES && size == input.available(), "prepared payload length");
            byte[] payload = new byte[size];
            input.readFully(payload);
            return new Record(expectedSequence, transaction, payload);
        }
    }

    private byte[] encodeMarker(long sequence, UUID transaction, String name, byte[] hash) throws IOException {
        return encodeMarker(sequence, transaction, name, hash, false);
    }

    private byte[] encodeMarker(long sequence, UUID transaction, String name, byte[] hash, boolean segmented) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(COMMIT_MAGIC);
            output.writeInt(segmented ? 2 : VERSION);
            output.writeUTF(namespace);
            output.writeLong(sequence);
            writeUuid(output, transaction);
            output.writeUTF(name);
            output.write(hash);
            output.write(previousHash);
        }
        byte[] content = bytes.toByteArray();
        bytes.write(digest(content));
        return bytes.toByteArray();
    }

    private Marker readMarker(byte[] bytes, long sequence, boolean verifyParent) throws IOException {
        require(bytes.length >= 32, "truncated marker");
        byte[] content = Arrays.copyOf(bytes, bytes.length - 32);
        require(Arrays.equals(digest(content), Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length)), "marker checksum");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(content))) {
            require(input.readInt() == COMMIT_MAGIC, "marker magic");
            int version = input.readInt();
            require(version == VERSION || version == 2 && !verifyParent, "marker format");
            require(namespace.equals(input.readUTF()), "world/manager namespace");
            require(input.readLong() == sequence, "marker sequence");
            UUID transaction = readUuid(input);
            String name = input.readUTF();
            require(name.matches("prepared-[0-9a-f-]{36}-[0-9a-f-]{36}\\.bin"), "prepared filename");
            byte[] hash = new byte[32];
            byte[] parent = new byte[32];
            input.readFully(hash);
            input.readFully(parent);
            require((!verifyParent || Arrays.equals(parent, previousHash)) && input.read() == -1, "marker chain or trailing data");
            return new Marker(transaction, name, hash, parent, version == 2);
        }
    }

    private void writeSyncedNew(Path file, byte[] data) throws IOException {
        try (FileChannel output = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            fileOperations.write(output, buffer);
            fileOperations.sync(output); // A failed force is a failed commit, never a debug-only warning.
        }
    }

    private static byte[] readBounded(Path file, int limit) throws IOException {
        try (FileChannel input = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = input.size();
            if (size < 0 || size > limit) throw new IOException("Journal record size limit: " + file);
            byte[] data = new byte[(int) size];
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) if (input.read(buffer) < 0) throw new IOException("Truncated journal record: " + file);
            if (input.read(ByteBuffer.allocate(1)) != -1) throw new IOException("Journal record grew while reading: " + file);
            return data;
        }
    }

    private Path commitPath(long sequence) {
        return directory.resolve(String.format(Locale.ROOT, "commit-%020d", sequence));
    }

    private static byte[] digest(byte[] bytes) {
        return LegacyMultiblockIndex.sha256Digest().digest(bytes);
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void require(boolean condition, String reason) throws IOException {
        if (!condition) throw new IOException("Invalid multiblock journal: " + reason);
    }

    private void checkOpen() throws IOException {
        if (closed || failed) throw new IOException("Multiblock journal is closed or failed; reopen and recover");
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                if (directoryChannel != null) directoryChannel.close();
            } finally {
                try {
                    lock.release();
                } finally {
                    lockChannel.close();
                }
            }
        }
    }

    public interface Replay {
        void accept(Record record) throws IOException;
    }

    public static final class Record {
        public final long sequence;
        public final UUID transaction;
        public final byte[] payload;
        public final boolean checkpoint;
        public final int checkpointPart;
        public final boolean checkpointLast;

        private Record(long sequence, UUID transaction, byte[] payload) {
            this(sequence, transaction, payload, false);
        }

        private Record(long sequence, UUID transaction, byte[] payload, boolean checkpoint) {
            this(sequence, transaction, payload, checkpoint, 0, true);
        }

        private Record(long sequence, UUID transaction, byte[] payload, boolean checkpoint, int checkpointPart, boolean checkpointLast) {
            this.sequence = sequence;
            this.transaction = transaction;
            this.payload = payload;
            this.checkpoint = checkpoint;
            this.checkpointPart = checkpointPart;
            this.checkpointLast = checkpointLast;
        }
    }

    private static final class Marker {
        final UUID transaction;
        final String preparedName;
        final byte[] preparedHash;
        final byte[] parentHash;
        final boolean segmented;

        Marker(UUID transaction, String preparedName, byte[] preparedHash, byte[] parentHash, boolean segmented) {
            this.transaction = transaction;
            this.preparedName = preparedName;
            this.preparedHash = preparedHash;
            this.parentHash = parentHash;
            this.segmented = segmented;
        }
    }

    enum Stage {
        BEFORE_PREPARE, PREPARE_SYNCED, MARKER_SYNCED, BEFORE_PUBLISH, PUBLISHED, COMMIT_SYNCED,
        CHECKPOINT_PREPARED, CHECKPOINT_BEFORE_PUBLISH, CHECKPOINT_PUBLISHED, CHECKPOINT_BEFORE_CLEANUP,
        CHECKPOINT_MARKERS_REMOVED, CHECKPOINT_CLEANED;

        boolean isCheckpoint() { return name().startsWith("CHECKPOINT_"); }
    }

    interface FaultInjector {
        void at(Stage stage) throws IOException;
    }

    /** Package-private IO seam for unsupported atomic move, disk-full and sync failure tests. */
    static class FileOperations {
        void write(FileChannel output, ByteBuffer buffer) throws IOException {
            while (buffer.hasRemaining()) output.write(buffer);
        }

        void sync(FileChannel output) throws IOException { output.force(true); }

        void publish(Path source, Path target, boolean replacingCheckpoint) throws IOException {
            if (replacingCheckpoint) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
    }
}
