package mekanism.common.util;

import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Resolves a persistent directory for worlds that are backed by a save handler.
 */
public final class WorldDirectoryUtils {

    private WorldDirectoryUtils() {
    }

    /**
     * Gets the directory associated with a world, or {@code null} when the world is
     * transient or its save handler does not expose persistent storage.
     */
    @Nullable
    public static File getWorldDirectory(@Nullable World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        try {
            ISaveHandler saveHandler = world.getSaveHandler();
            return saveHandler == null ? null : saveHandler.getWorldDirectory();
        } catch (UnsupportedOperationException ignored) {
            // DummyWorld deliberately does not provide persistent world storage.
            return null;
        }
    }
}
