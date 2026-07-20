package mekanism.common.content.qio;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

final class QIOFileIO {

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
        try (FileInputStream input = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(input);
        }
    }

    static void writeAtomic(File target, NBTTagCompound data) throws IOException {
        ensureDirectory(target.getParentFile());
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            CompressedStreamTools.writeCompressed(data, output);
        }
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | java.nio.file.FileAlreadyExistsException e) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
