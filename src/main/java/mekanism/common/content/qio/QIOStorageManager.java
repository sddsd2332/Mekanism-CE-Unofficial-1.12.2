package mekanism.common.content.qio;

import net.minecraft.world.World;

/**
 * Coordinates the two independent QIO file stores with the server/world lifecycle.
 */
public final class QIOStorageManager {

    private static final int STORAGE_MAINTENANCE_INTERVAL = 100;

    private QIOStorageManager() {
    }

    public static void load(World world) {
        if (world != null && !world.isRemote) {
            QIOResourceTypeRegistry.INSTANCE.createOrLoad(world);
            QIODriveStorage.INSTANCE.createOrLoad(world);
        }
    }

    public static boolean isLoaded() {
        return QIOResourceTypeRegistry.INSTANCE.isLoaded() && QIODriveStorage.INSTANCE.isLoaded();
    }

    public static void tick(World world) {
        if (world != null && !world.isRemote && world.provider.getDimension() == 0) {
            if (world.getTotalWorldTime() % STORAGE_MAINTENANCE_INTERVAL == 0) {
                if (!isLoaded()) {
                    load(world);
                }
                flush();
            }
        }
    }

    public static void flush() {
        // A drive file may reference a type created in the same tick, so types
        // are always committed before drive records.
        QIOResourceTypeRegistry.INSTANCE.flush();
        QIODriveStorage.INSTANCE.flush();
    }

    public static void shutdown() {
        flush();
        QIODriveStorage.INSTANCE.reset();
        QIOResourceTypeRegistry.INSTANCE.reset();
    }
}
