package mekanism.qioprocessing.common.planning;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Owner-thread atomic store for replayable workbench execution progress.
 */
public final class QIOWorkbenchExecutionStore {
    private static final long MAX_FILE_BYTES = 1024L * 1024;
    private static final long MAX_NBT_BYTES = 4L * 1024 * 1024;
    private final Thread owner = Thread.currentThread();
    private final Path directory;

    public QIOWorkbenchExecutionStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public QIOWorkbenchExecutionState load(UUID orderId) throws IOException {
        requireOwner();
        try {
            QIOWorkbenchExecutionState state = QIOWorkbenchExecutionState.read(
                    readBounded(file(orderId), MAX_FILE_BYTES, MAX_NBT_BYTES));
            if (!orderId.equals(state.orderId())) throw new IOException("Execution state identity mismatch");
            return state;
        } catch (RuntimeException error) {
            throw new IOException("Invalid execution state file: " + file(orderId), error);
        }
    }

    public boolean exists(UUID orderId) {
        requireOwner();
        return Files.exists(file(orderId));
    }

    public void saveNew(QIOWorkbenchExecutionState state) throws IOException {
        requireOwner();
        if (exists(state.orderId())) {
            if (!load(state.orderId()).write().equals(state.write())) {
                throw new IOException("Conflicting execution state identity: " + file(state.orderId()));
            }
            return;
        }
        write(state, false);
    }

    public void save(QIOWorkbenchExecutionState state) throws IOException {
        requireOwner();
        write(state, true);
    }

    public List<UUID> orderIds() throws IOException {
        requireOwner();
        if (!Files.exists(directory)) return Collections.emptyList();
        List<UUID> ids = new ArrayList<>();
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.exec")) {
            for (Path path : stream) {
                String raw = path.getFileName().toString();
                raw = raw.substring(0, raw.length() - 5);
                UUID orderId;
                try {
                    orderId = UUID.fromString(raw);
                } catch (IllegalArgumentException error) {
                    throw new IOException("Invalid execution state filename: " + path, error);
                }
                if (!orderId.toString().equals(raw) || !orderId.equals(load(orderId).orderId())) {
                    throw new IOException("Invalid execution state identity: " + path);
                }
                ids.add(orderId);
            }
        }
        ids.sort(UUID::compareTo);
        return Collections.unmodifiableList(ids);
    }

    private void write(QIOWorkbenchExecutionState state, boolean replace) throws IOException {
        Files.createDirectories(directory);
        Path target = file(state.orderId());
        if (!replace && Files.exists(target)) throw new IOException("Execution state already exists: " + target);
        Path temporary = Files.createTempFile(directory, state.orderId() + "-execution-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                CompressedStreamTools.writeCompressed(state.write(), output);
            }
            if (Files.size(temporary) > MAX_FILE_BYTES) throw new IOException("Execution state exceeds size limit");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (replace)
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path file(UUID orderId) {
        return directory.resolve(orderId + ".exec");
    }

    private static NBTTagCompound readBounded(Path path, long compressedLimit, long expandedLimit) throws IOException {
        if (Files.size(path) > compressedLimit) throw new IOException("Execution state exceeds size limit: " + path);
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
            if (value >= 0 && ++count > limit) throw new IOException("Execution state expanded size limit exceeded");
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            int read = super.read(buffer, offset, (int) Math.min(length, limit - count + 1));
            if (read > 0 && (count += read) > limit)
                throw new IOException("Execution state expanded size limit exceeded");
            return read;
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Execution state store requires its owner thread");
    }
}

