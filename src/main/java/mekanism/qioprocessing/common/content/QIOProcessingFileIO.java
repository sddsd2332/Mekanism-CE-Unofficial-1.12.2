package mekanism.qioprocessing.common.content;

import mekanism.common.Mekanism;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SyncFailedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

final class QIOProcessingFileIO {

    private static final long MAX_COMPRESSED_FILE_SIZE = 64L * 1024 * 1024;
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
            throw new IOException("QIO Processing file exceeds 64 MiB: " + file);
        }
        try (FileInputStream input = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(input);
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
            throw new IOException("QIO Processing file would exceed 64 MiB: " + target);
        }
        NBTTagCompound verified = read(temporary);
        if (verified == null || !verified.equals(data)) {
            throw new IOException("QIO Processing write verification failed: " + target);
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
