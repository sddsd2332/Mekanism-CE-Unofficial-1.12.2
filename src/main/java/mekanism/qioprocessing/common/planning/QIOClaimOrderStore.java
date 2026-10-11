package mekanism.qioprocessing.common.planning;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Owner-thread write-ahead journal. Never silently replaces an existing order or a corrupt file.
 */
public final class QIOClaimOrderStore {
    private static final long MAX_ORDER_FILE_BYTES = 1024L * 1024;
    private static final long MAX_ORDER_NBT_BYTES = 4L * 1024 * 1024;
    private static final long MAX_MARKER_FILE_BYTES = 4096;
    private static final long MAX_MARKER_NBT_BYTES = 64L * 1024;
    private static final String RECOVERY_FORMAT = "mekanismqioprocessing:claim_recovery_diagnostic";
    private final Thread owner = Thread.currentThread();
    private final Path directory;
    private int activeScans;

    public QIOClaimOrderStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public QIOClaimOrderIntent load(UUID orderId) throws IOException {
        requireOwner();
        Path path = file(orderId);
        try {
            QIOClaimOrderIntent intent = QIOClaimOrderIntent.read(readBounded(path, MAX_ORDER_FILE_BYTES, MAX_ORDER_NBT_BYTES));
            if (!orderId.equals(intent.orderId())) throw new IOException("Claim order identity mismatch: " + path);
            return intent;
        } catch (RuntimeException error) {
            throw new IOException("Invalid claim order file: " + path, error);
        }
    }

    public boolean exists(UUID orderId) {
        requireOwner();
        return Files.exists(file(orderId));
    }

    public void saveNew(QIOClaimOrderIntent intent) throws IOException {
        requireOwner();
        requireNoActiveScan();
        Files.createDirectories(directory);
        Path target = file(intent.orderId());
        if (Files.exists(target)) {
            if (!load(intent.orderId()).write().equals(intent.write()))
                throw new IOException("Conflicting claim order identity: " + target);
            return;
        }
        Path temporary = Files.createTempFile(directory, intent.orderId() + "-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                CompressedStreamTools.writeCompressed(intent.write(), output);
            }
            if (Files.size(temporary) > MAX_ORDER_FILE_BYTES)
                throw new IOException("Claim order file exceeds size limit");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Persist a cancellation barrier before any release; a later resume must never reissue CREATE.
     */
    public void requestCancellation(UUID orderId) throws IOException {
        requireOwner();
        load(orderId);
        Path target = cancellationFile(orderId);
        if (Files.exists(target)) {
            readCancellation(orderId);
            return;
        }
        NBTTagCompound marker = new NBTTagCompound();
        marker.setString("format", "mekanismqioprocessing:claim_order_cancellation");
        marker.setInteger("version", 1);
        marker.setUniqueId("order", orderId);
        Path temporary = Files.createTempFile(directory, orderId + "-cancel-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                CompressedStreamTools.writeCompressed(marker, output);
            }
            if (Files.size(temporary) > MAX_MARKER_FILE_BYTES)
                throw new IOException("Claim cancellation file exceeds size limit");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public boolean cancellationRequested(UUID orderId) throws IOException {
        requireOwner();
        if (!Files.exists(cancellationFile(orderId))) return false;
        readCancellation(orderId);
        return true;
    }

    public static final class RecoveryRecord {
        private final int attempts;
        private final long lastAttemptMillis;
        private final QIOClaimOrderRecovery.Status status;

        private RecoveryRecord(int attempts, long lastAttemptMillis, QIOClaimOrderRecovery.Status status) {
            this.attempts = attempts;
            this.lastAttemptMillis = lastAttemptMillis;
            this.status = status;
        }

        public int attempts() {
            return attempts;
        }

        public long lastAttemptMillis() {
            return lastAttemptMillis;
        }

        public QIOClaimOrderRecovery.Status status() {
            return status;
        }
    }

    public RecoveryRecord readRecovery(UUID orderId) throws IOException {
        requireOwner();
        Path path = recoveryFile(orderId);
        if (!Files.exists(path)) return null;
        try {
            NBTTagCompound tag = readBounded(path, MAX_MARKER_FILE_BYTES, MAX_MARKER_NBT_BYTES);
            if (tag == null || !tag.hasKey("format", 8) || !RECOVERY_FORMAT.equals(tag.getString("format"))
                    || !tag.hasKey("version", 3) || tag.getInteger("version") != 1
                    || !tag.hasUniqueId("order") || !orderId.equals(tag.getUniqueId("order"))
                    || !tag.hasKey("attempts", 3) || tag.getInteger("attempts") <= 0
                    || !tag.hasKey("lastAttemptMillis", 4) || tag.getLong("lastAttemptMillis") < 0
                    || !tag.hasKey("status", 8)) throw new IOException("Invalid claim recovery diagnostic: " + path);
            QIOClaimOrderRecovery.Status status = QIOClaimOrderRecovery.Status.valueOf(tag.getString("status"));
            return new RecoveryRecord(tag.getInteger("attempts"), tag.getLong("lastAttemptMillis"), status);
        } catch (RuntimeException error) {
            throw new IOException("Invalid claim recovery diagnostic: " + path, error);
        }
    }

    void beginRecovery(UUID orderId, long nowMillis) throws IOException {
        requireOwner();
        if (nowMillis < 0) throw new IllegalArgumentException("Invalid claim recovery diagnostic");
        load(orderId);
        if (!cancellationRequested(orderId)) throw new IOException("Claim recovery requires a cancellation barrier");
        RecoveryRecord previous = readRecovery(orderId);
        int attempts = previous == null ? 1 : previous.attempts == Integer.MAX_VALUE ? Integer.MAX_VALUE : previous.attempts + 1;
        writeRecovery(orderId, attempts, nowMillis, QIOClaimOrderRecovery.Status.ATTEMPTING);
    }

    void finishRecovery(UUID orderId, QIOClaimOrderRecovery.Status status, long nowMillis) throws IOException {
        requireOwner();
        if (status == null || status == QIOClaimOrderRecovery.Status.ATTEMPTING || nowMillis < 0) {
            throw new IllegalArgumentException("Invalid claim recovery result");
        }
        RecoveryRecord previous = readRecovery(orderId);
        if (previous == null || previous.status != QIOClaimOrderRecovery.Status.ATTEMPTING) {
            throw new IOException("Claim recovery attempt is not in progress");
        }
        writeRecovery(orderId, previous.attempts, nowMillis, status);
    }

    private void writeRecovery(UUID orderId, int attempts, long nowMillis, QIOClaimOrderRecovery.Status status) throws IOException {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("format", RECOVERY_FORMAT);
        tag.setInteger("version", 1);
        tag.setUniqueId("order", orderId);
        tag.setInteger("attempts", attempts);
        tag.setLong("lastAttemptMillis", nowMillis);
        tag.setString("status", status.name());
        Path temporary = Files.createTempFile(directory, orderId + "-recovery-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                CompressedStreamTools.writeCompressed(tag, output);
            }
            if (Files.size(temporary) > MAX_MARKER_FILE_BYTES)
                throw new IOException("Claim recovery diagnostic exceeds size limit");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, recoveryFile(orderId), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Enumerate durable orders without deleting or skipping a malformed journal.
     */
    public List<UUID> orderIds() throws IOException {
        requireOwner();
        List<UUID> ids = new ArrayList<>();
        try (Scan scan = scan()) {
            ScanEntry entry;
            while ((entry = scan.next()) != null) if (entry.orderId() != null) ids.add(entry.orderId());
        }
        ids.sort(UUID::compareTo);
        return Collections.unmodifiableList(ids);
    }

    public Scan scan() throws IOException {
        requireOwner();
        if (!Files.exists(directory)) {
            activeScans++;
            return new Scan(null, Collections.emptyIterator());
        }
        DirectoryStream<Path> stream = Files.newDirectoryStream(directory);
        try {
            Iterator<Path> iterator = stream.iterator();
            activeScans++;
            return new Scan(stream, iterator);
        } catch (RuntimeException error) {
            stream.close();
            throw error;
        }
    }

    void requireNoActiveScan() {
        requireOwner();
        if (activeScans != 0)
            throw new IllegalStateException("Claim recovery scan must finish before new reservations");
    }

    public static final class ScanEntry {
        private final UUID orderId;

        private ScanEntry(UUID orderId) {
            this.orderId = orderId;
        }

        public UUID orderId() {
            return orderId;
        }
    }

    public final class Scan implements AutoCloseable {
        private final DirectoryStream<Path> stream;
        private final Iterator<Path> iterator;
        private boolean closed;

        private Scan(DirectoryStream<Path> stream, Iterator<Path> iterator) {
            this.stream = stream;
            this.iterator = iterator;
        }

        /**
         * At most one filesystem entry per call; null means end of the directory.
         */
        public ScanEntry next() throws IOException {
            requireOwner();
            if (closed) throw new IllegalStateException("Claim order scan is closed");
            if (!iterator.hasNext()) {
                close();
                return null;
            }
            Path path = iterator.next();
            String name = path.getFileName().toString();
            boolean intentFile = name.endsWith(".dat"), cancellation = name.endsWith(".cancel"), diagnostic = name.endsWith(".recovery");
            if (!intentFile && !cancellation && !diagnostic) return new ScanEntry(null);
            String raw = name.substring(0, name.length() - (intentFile ? 4 : cancellation ? 7 : 9));
            UUID id;
            try {
                id = UUID.fromString(raw);
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid claim order filename: " + path, error);
            }
            if (!id.toString().equals(raw)) throw new IOException("Noncanonical claim order filename: " + path);
            if (intentFile) {
                load(id);
                return new ScanEntry(id);
            }
            if (!Files.exists(file(id))) throw new IOException("Orphan claim order sidecar: " + path);
            if (cancellation) readCancellation(id);
            else {
                if (!cancellationRequested(id))
                    throw new IOException("Claim recovery diagnostic without cancellation: " + path);
                readRecovery(id);
            }
            return new ScanEntry(null);
        }

        @Override
        public void close() throws IOException {
            requireOwner();
            if (!closed) {
                closed = true;
                activeScans--;
                if (stream != null) stream.close();
            }
        }
    }

    private void readCancellation(UUID orderId) throws IOException {
        Path path = cancellationFile(orderId);
        try {
            NBTTagCompound marker = readBounded(path, MAX_MARKER_FILE_BYTES, MAX_MARKER_NBT_BYTES);
            if (marker == null || !marker.hasKey("format", 8)
                    || !"mekanismqioprocessing:claim_order_cancellation".equals(marker.getString("format"))
                    || !marker.hasKey("version", 3) || marker.getInteger("version") != 1
                    || !marker.hasUniqueId("order") || !orderId.equals(marker.getUniqueId("order"))) {
                throw new IOException("Invalid claim order cancellation: " + path);
            }
        } catch (RuntimeException error) {
            throw new IOException("Invalid claim order cancellation: " + path, error);
        }
    }

    private static NBTTagCompound readBounded(Path path, long compressedLimit, long expandedLimit) throws IOException {
        if (Files.size(path) > compressedLimit) throw new IOException("Claim journal file exceeds size limit: " + path);
        try (InputStream input = Files.newInputStream(path);
             GZIPInputStream gzip = new GZIPInputStream(input);
             DataInputStream data = new DataInputStream(new BufferedInputStream(new LimitedInputStream(gzip, expandedLimit)))) {
            return CompressedStreamTools.read(data, new NBTSizeTracker(expandedLimit));
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        private LimitedInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0 && ++count > limit) throw new IOException("Claim journal expanded size limit exceeded");
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            int read = super.read(buffer, offset, (int) Math.min(length, limit - count + 1));
            if (read > 0 && (count += read) > limit)
                throw new IOException("Claim journal expanded size limit exceeded");
            return read;
        }
    }

    private Path file(UUID orderId) {
        return directory.resolve(orderId + ".dat");
    }

    private Path cancellationFile(UUID orderId) {
        return directory.resolve(orderId + ".cancel");
    }

    private Path recoveryFile(UUID orderId) {
        return directory.resolve(orderId + ".recovery");
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Claim order store requires its owner thread");
    }
}

