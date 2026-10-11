package mekanism.qioprocessing.common.workbench;

import net.minecraft.nbt.CompressedStreamTools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Serial owner-thread storage for user route selections. The caller supplies a world-specific directory.
 */
public final class WorkbenchBookStore {
    private final Path directory;
    private final Thread owner = Thread.currentThread();

    public WorkbenchBookStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public WorkbenchSnapshot load(UUID id) throws IOException {
        requireOwner();
        Path path = file(id);
        if (!Files.exists(path)) return new WorkbenchRecipeBook(id).snapshot();
        try (InputStream input = Files.newInputStream(path)) {
            WorkbenchSnapshot snapshot = WorkbenchBookCodec.read(CompressedStreamTools.readCompressed(input));
            if (!id.equals(snapshot.id())) throw new IOException("Workbench file identity mismatch: " + path);
            return snapshot;
        } catch (RuntimeException error) {
            throw new IOException("Invalid workbench file: " + path, error);
        }
    }

    /**
     * Never overwrite a newer revision, silently replace corrupt data, or delete the original before replacement.
     */
    public void save(WorkbenchSnapshot snapshot) throws IOException {
        requireOwner();
        Files.createDirectories(directory);
        Path target = file(snapshot.id());
        if (Files.exists(target)) {
            WorkbenchSnapshot saved = load(snapshot.id());
            if (saved.revision() > snapshot.revision()) throw new IOException("Refusing an older workbench revision");
            if (saved.revision() == snapshot.revision()) {
                if (saved.sameContents(snapshot.routes(), snapshot.preferences())) return;
                throw new IOException("Conflicting workbench contents at the same revision");
            }
        }
        Path temp = Files.createTempFile(directory, snapshot.id() + "-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temp)) {
                CompressedStreamTools.writeCompressed(WorkbenchBookCodec.write(snapshot), output);
            }
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            // If this filesystem cannot replace atomically, fail and retain the previous file.
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            WorkbenchBookRuntime.saved(directory, snapshot);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Path file(UUID id) {
        return directory.resolve(id + ".dat");
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Workbench store must be used by its owner thread");
    }
}
