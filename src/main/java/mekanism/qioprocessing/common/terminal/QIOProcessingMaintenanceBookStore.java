package mekanism.qioprocessing.common.terminal;

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
 * Owner-thread atomic storage for one world's maintenance rule books.
 */
public final class QIOProcessingMaintenanceBookStore {
    private final Path directory;
    private final Thread owner = Thread.currentThread();

    public QIOProcessingMaintenanceBookStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public QIOProcessingMaintenanceBook load(UUID frequency) throws IOException {
        requireOwner();
        Path path = file(frequency);
        if (!Files.exists(path)) return QIOProcessingMaintenanceBook.empty(frequency);
        try (InputStream input = Files.newInputStream(path)) {
            return QIOProcessingMaintenanceBook.read(CompressedStreamTools.readCompressed(input), frequency);
        } catch (RuntimeException error) {
            throw new IOException("Invalid maintenance book: " + path, error);
        }
    }

    public void save(QIOProcessingMaintenanceBook book) throws IOException {
        requireOwner();
        Files.createDirectories(directory);
        Path target = file(book.frequency());
        if (Files.exists(target)) {
            QIOProcessingMaintenanceBook saved = load(book.frequency());
            if (saved.revision() > book.revision()) throw new IOException("Refusing an older maintenance revision");
            if (saved.revision() == book.revision() && !saved.write().equals(book.write())) {
                throw new IOException("Conflicting maintenance contents at the same revision");
            }
            if (saved.revision() == book.revision()) return;
        }
        Path temporary = Files.createTempFile(directory, book.frequency() + "-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                CompressedStreamTools.writeCompressed(book.write(), output);
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path file(UUID frequency) {
        return directory.resolve(frequency + ".dat");
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Maintenance store requires owner thread");
    }
}

