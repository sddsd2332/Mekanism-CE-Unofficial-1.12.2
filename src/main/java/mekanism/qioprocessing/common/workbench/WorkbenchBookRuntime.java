package mekanism.qioprocessing.common.workbench;

import mekanism.qioprocessing.common.content.QIOProcessingNetworkData;
import mekanism.qioprocessing.common.content.QIOProcessingNetworkManager;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import mekanism.qioprocessing.common.recipe.RecipeDirectoryService;
import mekanism.common.util.WorldDirectoryUtils;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-thread facade for the new blank Workbench schema. It never reads the legacy config.
 */
public final class WorkbenchBookRuntime {
    private static final String DIRECTORY = "mekanism/qio_processing/workbench_v1";
    @Nullable
    private static WorkbenchBookStore store;
    @Nullable
    private static Path directory;
    @Nullable
    private static Thread owner;
    private static final Map<UUID, WorkbenchSnapshot> books = new LinkedHashMap<>();
    private static final Map<UUID, QIOWorkbenchConfiguration> planning = new LinkedHashMap<>();

    private WorkbenchBookRuntime() {
    }

    public static synchronized boolean open(World world) {
        requireServerThread(world);
        File worldDirectory = WorldDirectoryUtils.getWorldDirectory(world);
        if (worldDirectory == null) {
            return false;
        }
        Path next = worldDirectory.toPath().resolve(DIRECTORY)
                .toAbsolutePath().normalize();
        if (directory != null && !directory.equals(next)) {
            throw new IllegalStateException("Workbench store is already bound to another world");
        }
        if (store == null) {
            owner = Thread.currentThread();
            directory = next;
            store = new WorkbenchBookStore(next);
        }
        return true;
    }

    public static synchronized WorkbenchSnapshot loadOrBlank(World world, UUID frequency) throws IOException {
        if (!open(world)) {
            throw new IOException("Workbench store requires a persistent world save directory");
        }
        return load(Objects.requireNonNull(frequency));
    }

    public static synchronized void save(World world, WorkbenchSnapshot snapshot) throws IOException {
        if (!open(world)) {
            throw new IOException("Workbench store requires a persistent world save directory");
        }
        Objects.requireNonNull(store).save(Objects.requireNonNull(snapshot));
    }

    private static WorkbenchSnapshot load(UUID frequency) throws IOException {
        requireOwner();
        WorkbenchSnapshot cached = books.get(frequency);
        if (cached == null) {
            cached = Objects.requireNonNull(store).load(frequency);
            books.put(frequency, cached);
        }
        return cached;
    }

    /**
     * Transient adapter only; the legacy persisted configuration keeps its task identity.
     */
    public static synchronized QIOWorkbenchConfiguration planningConfiguration(UUID frequency) {
        requireOwner();
        try {
            WorkbenchSnapshot book = load(frequency);
            QIOWorkbenchConfiguration cached = planning.get(frequency);
            if (cached == null || cached.getRuntimeBook() != book ||
                    cached.getRuntimeCatalog() != RecipeDirectoryService.INSTANCE.catalog()) {
                cached = QIOWorkbenchConfiguration.runtimeView(book, RecipeDirectoryService.INSTANCE.catalog());
                planning.put(frequency, cached);
            }
            return cached;
        } catch (IOException error) {
            throw new UncheckedIOException("Unable to load Workbench book " + frequency, error);
        }
    }

    /**
     * Called after every successful atomic store replacement, including direct store writers.
     */
    static synchronized void saved(Path source, WorkbenchSnapshot snapshot) {
        if (directory == null || !directory.equals(source.toAbsolutePath().normalize())) return;
        requireOwner();
        books.put(snapshot.id(), snapshot);
        planning.remove(snapshot.id());
        QIOProcessingNetworkData network = QIOProcessingNetworkManager.INSTANCE.get(snapshot.id());
        if (network != null) network.workbenchBookChanged();
    }

    private static void requireOwner() {
        if (store == null || Thread.currentThread() != owner) {
            throw new IllegalStateException("Workbench runtime requires its server owner thread");
        }
    }

    public static synchronized boolean isOpen() {
        return store != null;
    }

    @Nullable
    public static synchronized Path directory() {
        return directory;
    }

    public static synchronized void close() {
        store = null;
        directory = null;
        owner = null;
        books.clear();
        planning.clear();
    }

    private static void requireServerThread(World world) {
        if (world == null || world.isRemote || world.getMinecraftServer() == null ||
                !world.getMinecraftServer().isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Workbench store requires the server thread");
        }
    }
}
