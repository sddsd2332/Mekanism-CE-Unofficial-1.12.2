package mekanism.qioprocessing.common.content;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

/** Narrow public facade for verified QIO Processing storage used by sibling packages. */
public final class QIOProcessingStorageIO {

    private QIOProcessingStorageIO() {
    }

    public static void ensureDirectory(File directory) throws IOException {
        QIOProcessingFileIO.ensureDirectory(directory);
    }

    @Nullable
    public static NBTTagCompound read(File file) throws IOException {
        return QIOProcessingFileIO.read(file);
    }

    public static void writeAtomic(File target, NBTTagCompound data) throws IOException {
        QIOProcessingFileIO.writeAtomic(target, data);
    }

    public static void replaceAtomicWithoutBackup(File target, NBTTagCompound data)
          throws IOException {
        QIOProcessingFileIO.replaceAtomicWithoutBackup(target, data);
    }

    public static boolean isFileTooLarge(IOException error) {
        return QIOProcessingFileIO.isFileTooLarge(error);
    }

    public static File backupFile(File target) {
        return QIOProcessingFileIO.backupFile(target);
    }

    @Nullable
    public static File quarantine(File file, String suffix) {
        return QIOProcessingFileIO.quarantine(file, suffix);
    }
}
