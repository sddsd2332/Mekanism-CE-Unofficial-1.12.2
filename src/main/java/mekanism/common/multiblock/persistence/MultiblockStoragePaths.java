package mekanism.common.multiblock.persistence;

import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;

/** Resolve once on the owning server thread, before submitting any file work. */
public final class MultiblockStoragePaths {
    private static final Field SAVE_HANDLER = ReflectionHelper.findField(MapStorage.class, new String[]{"saveHandler", "field_75751_a"});
    public final Path legacyFile;
    public final Path managedDirectory;
    public final String namespace;

    private MultiblockStoragePaths(Path legacyFile, Path managedDirectory, String namespace) {
        this.legacyFile = legacyFile;
        this.managedDirectory = managedDirectory;
        this.namespace = namespace;
    }

    public static MultiblockStoragePaths resolve(World world, String manager) throws IOException {
        if (world == null || world.isRemote) throw new IOException("Multiblock storage requires a server world");
        return resolve(world.getPerWorldStorage(), manager);
    }

    static MultiblockStoragePaths resolve(MapStorage storage, String manager) throws IOException {
        try {
            ISaveHandler handler = (ISaveHandler) SAVE_HANDLER.get(storage);
            return resolve(handler, manager);
        } catch (IllegalAccessException | RuntimeException error) {
            throw new IOException("Cannot resolve the world's actual multiblock save handler", error);
        }
    }

    static MultiblockStoragePaths resolve(ISaveHandler handler, String manager) throws IOException {
        if (handler == null) throw new IOException("World has no persistent per-world save handler");
        if (manager == null || !manager.matches("[A-Za-z0-9_]{1,80}")) throw new IOException("Invalid multiblock manager name");
        // Use precisely the handler that MapStorage used for the legacy file. getSaveHandler()
        // alone is the root world's handler on Forge, and is wrong for per-dimension data.
        File legacy = handler.getMapFileFromName("mekanism_" + manager + "_invalidated_multiblocks");
        if (legacy == null) throw new IOException("World save handler returned no multiblock path");
        Path file = legacy.toPath().toAbsolutePath().normalize();
        Path parent = file.getParent();
        if (parent == null) throw new IOException("Multiblock data path has no parent");
        // Path isolates worlds at runtime; the namespace intentionally survives moving a save.
        // A UUID source is only interpreted inside this resolved per-world manager directory.
        return new MultiblockStoragePaths(file, parent.resolve("mekanism_multiblocks").resolve(manager), "mekanism:multiblock:v1:" + manager);
    }
}
