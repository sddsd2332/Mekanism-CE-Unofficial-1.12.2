package mekanism.common.content.qio;

import mekanism.common.lib.inventory.HashedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.io.File;
import java.util.UUID;

/**
 * Compatibility facade for older item-only QIO callers. New code should use
 * {@link QIOResourceTypeRegistry} directly so resource kind is explicit.
 */
public final class QIOItemTypeRegistry {

    private QIOItemTypeRegistry() {
    }

    public static void createOrLoad(World world) {
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(world);
    }

    public static void createOrLoad(File worldDirectory) {
        QIOResourceTypeRegistry.INSTANCE.createOrLoad(worldDirectory);
    }

    public static UUID getOrTrackUUID(HashedItem item) {
        return QIOResourceTypeRegistry.INSTANCE.getOrTrackItem(item);
    }

    @Nullable
    public static UUID getUUIDForType(HashedItem item) {
        return QIOResourceTypeRegistry.INSTANCE.getUUIDForItem(item);
    }

    @Nullable
    public static HashedItem getTypeByUUID(UUID uuid) {
        QIOResourceType type = QIOResourceTypeRegistry.INSTANCE.getTypeByUUID(uuid);
        return type == null ? null : type.getItemType();
    }

    public static ItemStack createStack(UUID uuid, int size) {
        return QIOResourceTypeRegistry.INSTANCE.createItemStack(uuid, size);
    }

    public static void flush() {
        QIOResourceTypeRegistry.INSTANCE.flush();
    }

    public static void reset() {
        QIOResourceTypeRegistry.INSTANCE.reset();
    }
}
