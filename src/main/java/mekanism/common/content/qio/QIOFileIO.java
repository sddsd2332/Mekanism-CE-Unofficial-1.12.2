package mekanism.common.content.qio;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

final class QIOFileIO {

    private static final long MAX_COMPRESSED_FILE_SIZE = 64L * 1024 * 1024;
    private static final long MAX_DECOMPRESSED_FILE_SIZE = 256L * 1024 * 1024;
    private static final long MAX_NBT_NODES = 4_000_000L;
    private static final long MAX_NBT_LIST_ENTRIES = 1_000_000L;
    private static final long MAX_NBT_STRING_BYTES = 65_535L;
    private static final int MAX_NBT_DEPTH = 512;

    private QIOFileIO() {
    }

    static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Unable to create QIO storage directory: " + directory);
        }
    }

    @Nullable
    static NBTTagCompound read(File file) throws IOException {
        if (!file.isFile()) {
            return null;
        }
        if (file.length() > MAX_COMPRESSED_FILE_SIZE) {
            throw new FileTooLargeException("QIO file exceeds 64 MiB: " + file);
        }
        try (FileInputStream input = new FileInputStream(file);
             GZIPInputStream gzip = new GZIPInputStream(input);
             LimitedInputStream limited = new LimitedInputStream(gzip,
                   MAX_DECOMPRESSED_FILE_SIZE);
             DataInputStream data = new DataInputStream(new BufferedInputStream(limited))) {
            NBTTagCompound result = CompressedStreamTools.read(data,
                  new NBTSizeTracker(MAX_DECOMPRESSED_FILE_SIZE));
            validateNbt(result);
            return result;
        } catch (FileTooLargeException error) {
            throw error;
        } catch (RuntimeException error) {
            String message = error.getMessage();
            if (message != null && (message.contains("too big") ||
                  message.contains("too high complexity"))) {
                throw new FileTooLargeException("QIO NBT allocation limit exceeded");
            }
            throw new IOException("Unable to read bounded QIO NBT", error);
        }
    }

    static void writeAtomic(File target, NBTTagCompound data) throws IOException {
        ensureDirectory(target.getParentFile());
        validateNbt(data);
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                CompressedStreamTools.writeCompressed(data, output);
            }
            if (temporary.length() > MAX_COMPRESSED_FILE_SIZE) {
                throw new FileTooLargeException("QIO file would exceed 64 MiB: " + target);
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                      StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | java.nio.file.FileAlreadyExistsException e) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    static File dataFile(File directory, UUID uuid) {
        return new File(directory, uuid.toString() + ".dat");
    }

    static File[] listDataFiles(File directory) {
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".dat"));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, (left, right) -> left.getName().compareTo(right.getName()));
        return files;
    }

    /** Moves an unreadable record aside so a later flush can never overwrite the evidence. */
    static void quarantine(File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        File target = new File(file.getParentFile(), file.getName() + ".damaged");
        int suffix = 1;
        while (target.exists()) {
            target = new File(file.getParentFile(), file.getName() + ".damaged." + suffix++);
        }
        try {
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            QIOLog.LOGGER.warn("Quarantined damaged QIO record {} as {}", file, target);
        } catch (IOException e) {
            QIOLog.LOGGER.warn("Unable to quarantine damaged QIO record {}", file, e);
        }
    }

    private static void validateNbt(@Nullable NBTTagCompound root) throws IOException {
        NbtBudget budget = new NbtBudget();
        validateNbt(root, 0, budget);
    }

    private static void validateNbt(@Nullable NBTBase tag, int depth, NbtBudget budget)
          throws IOException {
        if (tag == null) return;
        if (depth > MAX_NBT_DEPTH) {
            throw new FileTooLargeException("QIO NBT nesting exceeds " + MAX_NBT_DEPTH + " levels");
        }
        if (++budget.nodes > MAX_NBT_NODES) {
            throw new FileTooLargeException("QIO NBT node limit exceeded");
        }
        if (tag instanceof NBTTagString string &&
              modifiedUtfLength(string.getString()) > MAX_NBT_STRING_BYTES) {
            throw new FileTooLargeException("QIO NBT string exceeds 65,535 encoded bytes");
        }
        if (tag instanceof NBTTagCompound compound) {
            if (compound.getSize() > MAX_NBT_NODES) {
                throw new FileTooLargeException("QIO NBT compound is too large");
            }
            for (String key : compound.getKeySet()) {
                if (key == null || modifiedUtfLength(key) > MAX_NBT_STRING_BYTES) {
                    throw new FileTooLargeException("QIO NBT key is too long");
                }
                validateNbt(compound.getTag(key), depth + 1, budget);
            }
        } else if (tag instanceof NBTTagList list) {
            int count = list.tagCount();
            budget.listEntries += count;
            if (budget.listEntries > MAX_NBT_LIST_ENTRIES) {
                throw new FileTooLargeException("QIO NBT list-entry limit exceeded");
            }
            for (NBTBase child : list) {
                validateNbt(child, depth + 1, budget);
            }
        }
    }

    private static final class NbtBudget {
        private long nodes;
        private long listEntries;
    }

    private static long modifiedUtfLength(String value) {
        long bytes = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            bytes += character == 0 ? 2 : character <= 0x7F ? 1 : character <= 0x7FF ? 2 : 3;
            if (bytes > MAX_NBT_STRING_BYTES) return bytes;
        }
        return bytes;
    }

    /** Counts expanded bytes so a small gzip cannot allocate an unbounded NBT tree. */
    private static final class LimitedInputStream extends FilterInputStream {

        private final long limit;
        private long count;

        private LimitedInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            ensureCapacity(1);
            int value = super.read();
            if (value >= 0) count++;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            ensureCapacity(Math.min(length, limit - count));
            int read = super.read(buffer, offset, length);
            if (read > 0) count += read;
            return read;
        }

        private void ensureCapacity(long requested) throws FileTooLargeException {
            if (count >= limit || requested > limit - count) {
                throw new FileTooLargeException("QIO decompressed data exceeds 256 MiB");
            }
        }
    }

    private static final class FileTooLargeException extends IOException {

        private FileTooLargeException(String message) {
            super(message);
        }
    }

    @Nullable
    static UUID parseDataFileUUID(File file) {
        String name = file.getName();
        if (!name.endsWith(".dat")) {
            return null;
        }
        String rawUUID = name.substring(0, name.length() - 4);
        try {
            UUID uuid = UUID.fromString(rawUUID);
            return uuid.toString().equals(rawUUID) ? uuid : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    static UUID parseQuarantinedDataFileUUID(File file) {
        if (file == null) {
            return null;
        }
        String name = file.getName();
        int marker = name.indexOf(".dat.damaged");
        if (marker <= 0) {
            return null;
        }
        try {
            String raw = name.substring(0, marker);
            UUID uuid = UUID.fromString(raw);
            return uuid.toString().equals(raw) ? uuid : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
