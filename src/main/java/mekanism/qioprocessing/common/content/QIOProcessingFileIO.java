package mekanism.qioprocessing.common.content;

import mekanism.common.Mekanism;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SyncFailedException;
import java.util.zip.GZIPInputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

/**
 * QIO 处理模块中的 QIOProcessingFileIO 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
final class QIOProcessingFileIO {

    private static final long MAX_COMPRESSED_FILE_SIZE = 64L * 1024 * 1024;
    /** Limits apply to the expanded stream as well as the compressed file on disk. */
    private static final long MAX_DECOMPRESSED_FILE_SIZE = 256L * 1024 * 1024;
    private static final long MAX_NBT_NODES = 4_000_000L;
    private static final long MAX_NBT_LIST_ENTRIES = 1_000_000L;
    private static final long MAX_NBT_STRING_BYTES = 65_535L;
    private static final int MAX_NBT_DEPTH = 512;
    private static final String BACKUP_SUFFIX = ".bak";

    private QIOProcessingFileIO() {
    }

    static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Unable to create QIO Processing directory: " + directory);
        }
    }

    @Nullable
    static NBTTagCompound read(File file) throws IOException {
        if (!file.isFile()) {
            return null;
        }
        if (file.length() > MAX_COMPRESSED_FILE_SIZE) {
            throw new FileTooLargeException("QIO Processing file exceeds 64 MiB: " + file);
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
            // NBTSizeTracker and vanilla's depth checks report limits as runtime exceptions.
            // Convert them to IO failures so the existing quarantine path handles them.
            String message = error.getMessage();
            if (message != null && (message.contains("too big") ||
                  message.contains("too high complexity"))) {
                throw new FileTooLargeException("QIO Processing NBT allocation limit exceeded");
            }
            throw new IOException("Unable to read bounded QIO Processing NBT", error);
        }
    }

    static void writeAtomic(File target, NBTTagCompound data) throws IOException {
        ensureDirectory(target.getParentFile());
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            writeVerifiedTemporary(temporary, data, target);
            if (target.isFile()) {
                rotateBackup(target);
            }
            moveReplacing(temporary, target);
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    /** Replaces a target atomically after its previous generation was archived separately. */
    static void replaceAtomicWithoutBackup(File target, NBTTagCompound data) throws IOException {
        ensureDirectory(target.getParentFile());
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            writeVerifiedTemporary(temporary, data, target);
            moveReplacing(temporary, target);
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    static File backupFile(File target) {
        return new File(target.getParentFile(), target.getName() + BACKUP_SUFFIX);
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

    static File[] listBackupFiles(File directory) {
        File[] files = directory.listFiles(file -> file.isFile() &&
              file.getName().endsWith(".dat" + BACKUP_SUFFIX));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, (left, right) -> left.getName().compareTo(right.getName()));
        return files;
    }

    @Nullable
    static UUID parseDataFileUUID(File file) {
        return parseUUIDPrefix(file, ".dat");
    }

    @Nullable
    static UUID parseBackupFileUUID(File file) {
        return parseUUIDPrefix(file, ".dat" + BACKUP_SUFFIX);
    }

    @Nullable
    static UUID parseIsolatedDataFileUUID(File file) {
        UUID damaged = parseUUIDPrefix(file, ".dat.damaged");
        return damaged == null ? parseUUIDPrefix(file, ".dat.future") : damaged;
    }

    @Nullable
    static File quarantine(File file, String suffix) {
        if (file == null || !file.isFile()) {
            return null;
        }
        File target = new File(file.getParentFile(), file.getName() + suffix);
        int counter = 1;
        while (target.exists()) {
            target = new File(file.getParentFile(), file.getName() + suffix + '.' + counter++);
        }
        try {
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Mekanism.logger.warn("Isolated QIO Processing file {} as {}", file, target);
            return target;
        } catch (IOException e) {
            Mekanism.logger.error("Unable to isolate QIO Processing file {}", file, e);
            return null;
        }
    }

    /** Copies one generation to a uniquely named archive and verifies it byte-for-byte. */
    @Nullable
    static File archiveCopy(File file, String suffix) {
        if (file == null || !file.isFile()) {
            return null;
        }
        File target = uniqueArchiveTarget(file, suffix);
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            Files.copy(file.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try (FileOutputStream output = new FileOutputStream(temporary, true)) {
                syncIfSupported(output, temporary);
            }
            if (!sameBytes(file, temporary)) {
                throw new IOException("QIO Processing archive verification failed: " + file);
            }
            moveReplacing(temporary, target);
            Mekanism.logger.warn("Archived QIO Processing file {} as {}", file, target);
            return target;
        } catch (IOException e) {
            Mekanism.logger.error("Unable to archive QIO Processing file {}", file, e);
            return null;
        } finally {
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException e) {
                Mekanism.logger.warn("Unable to remove temporary QIO Processing archive {}",
                      temporary, e);
            }
        }
    }

    private static void writeVerifiedTemporary(File temporary, NBTTagCompound data,
          File target) throws IOException {
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            CompressedStreamTools.writeCompressed(data, output);
            syncIfSupported(output, temporary);
        }
        if (temporary.length() > MAX_COMPRESSED_FILE_SIZE) {
            throw new FileTooLargeException(
                  "QIO Processing file would exceed 64 MiB: " + target);
        }
        NBTTagCompound verified = read(temporary);
        if (verified == null || !verified.equals(data)) {
            throw new IOException("QIO Processing write verification failed: " + target);
        }
    }

    static boolean isFileTooLarge(IOException error) {
        return error instanceof FileTooLargeException;
    }

    private static final class FileTooLargeException extends IOException {

        private FileTooLargeException(String message) {
            super(message);
        }
    }

    private static void validateNbt(NBTTagCompound root) throws IOException {
        NbtBudget budget = new NbtBudget();
        validateNbt(root, 0, budget);
    }

    // Package-private regression hook; production reads always enter through read(File).
    static void validateNbtForTests(NBTTagCompound root) throws IOException {
        validateNbt(root);
    }

    private static void validateNbt(@Nullable NBTBase tag, int depth, NbtBudget budget)
          throws IOException {
        if (tag == null) return;
        if (depth > MAX_NBT_DEPTH) {
            throw new FileTooLargeException("QIO Processing NBT nesting exceeds " +
                  MAX_NBT_DEPTH + " levels");
        }
        if (++budget.nodes > MAX_NBT_NODES) {
            throw new FileTooLargeException("QIO Processing NBT node limit exceeded");
        }
        if (tag instanceof NBTTagString string &&
              modifiedUtfLength(string.getString()) > MAX_NBT_STRING_BYTES) {
            throw new FileTooLargeException(
                  "QIO Processing NBT string exceeds 65,535 encoded bytes");
        }
        if (tag instanceof NBTTagCompound compound) {
            if (compound.getSize() > MAX_NBT_NODES) {
                throw new FileTooLargeException("QIO Processing NBT compound is too large");
            }
            for (String key : compound.getKeySet()) {
                if (key == null || modifiedUtfLength(key) > MAX_NBT_STRING_BYTES) {
                    throw new FileTooLargeException("QIO Processing NBT key is too long");
                }
                validateNbt(compound.getTag(key), depth + 1, budget);
            }
        } else if (tag instanceof NBTTagList list) {
            int count = list.tagCount();
            budget.listEntries += count;
            if (budget.listEntries > MAX_NBT_LIST_ENTRIES) {
                throw new FileTooLargeException("QIO Processing NBT list-entry limit exceeded");
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

    /** Counts bytes after decompression and aborts before an unbounded NBT read can allocate. */
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
                throw new FileTooLargeException("QIO Processing decompressed data exceeds " +
                      MAX_DECOMPRESSED_FILE_SIZE + " bytes");
            }
        }
    }

    private static void rotateBackup(File target) throws IOException {
        NBTTagCompound current = read(target);
        if (current == null) {
            throw new IOException("Unable to verify current QIO Processing file: " + target);
        }
        File backup = backupFile(target);
        File temporaryBackup = new File(target.getParentFile(),
              target.getName() + BACKUP_SUFFIX + ".tmp");
        try {
            Files.copy(target.toPath(), temporaryBackup.toPath(),
                  StandardCopyOption.REPLACE_EXISTING);
            try (FileOutputStream output = new FileOutputStream(temporaryBackup, true)) {
                syncIfSupported(output, temporaryBackup);
            }
            NBTTagCompound verified = read(temporaryBackup);
            if (verified == null || !verified.equals(current)) {
                throw new IOException("QIO Processing backup verification failed: " + target);
            }
            moveReplacing(temporaryBackup, backup);
        } finally {
            Files.deleteIfExists(temporaryBackup.toPath());
        }
    }

    private static void moveReplacing(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                  StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | java.nio.file.FileAlreadyExistsException e) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static File uniqueArchiveTarget(File file, String suffix) {
        File target = new File(file.getParentFile(), file.getName() + suffix);
        int counter = 1;
        while (target.exists()) {
            target = new File(file.getParentFile(), file.getName() + suffix + '.' + counter++);
        }
        return target;
    }

    private static boolean sameBytes(File first, File second) throws IOException {
        if (first.length() != second.length()) {
            return false;
        }
        try (InputStream left = new BufferedInputStream(new FileInputStream(first));
             InputStream right = new BufferedInputStream(new FileInputStream(second))) {
            byte[] leftBuffer = new byte[8_192];
            byte[] rightBuffer = new byte[8_192];
            int read;
            while ((read = left.read(leftBuffer)) != -1) {
                int rightRead = readFully(right, rightBuffer, read);
                if (rightRead != read) {
                    return false;
                }
                for (int index = 0; index < read; index++) {
                    if (leftBuffer[index] != rightBuffer[index]) {
                        return false;
                    }
                }
            }
            return right.read() == -1;
        }
    }

    private static int readFully(InputStream input, byte[] buffer, int length)
          throws IOException {
        int total = 0;
        while (total < length) {
            int read = input.read(buffer, total, length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static void syncIfSupported(FileOutputStream output, File file)
          throws IOException {
        try {
            output.getFD().sync();
        } catch (SyncFailedException e) {
            Mekanism.logger.debug("File-descriptor sync is unavailable for {}; " +
                  "continuing with close, verification, and atomic replacement", file);
        }
    }

    @Nullable
    private static UUID parseUUIDPrefix(File file, String marker) {
        if (file == null) {
            return null;
        }
        String name = file.getName();
        int markerIndex = name.indexOf(marker);
        if (markerIndex <= 0) {
            return null;
        }
        String raw = name.substring(0, markerIndex);
        try {
            UUID uuid = UUID.fromString(raw);
            return uuid.toString().equals(raw) ? uuid : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
