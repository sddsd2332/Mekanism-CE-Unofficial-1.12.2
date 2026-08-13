package mekanism.common.inventory.container;

import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Optional-module hook for adding synchronized state to ordinary Mekanism tile containers. */
public final class MekanismTileContainerExtensionRegistry {

    private static final Map<ResourceLocation, Factory> FACTORIES = new LinkedHashMap<>();

    private MekanismTileContainerExtensionRegistry() {
    }

    public static synchronized void register(@Nonnull ResourceLocation id, @Nonnull Factory factory) {
        Objects.requireNonNull(id, "Container extension id cannot be null");
        Objects.requireNonNull(factory, "Container extension factory cannot be null");
        if (FACTORIES.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("Duplicate Mekanism tile container extension " + id);
        }
    }

    public static synchronized boolean unregister(@Nullable ResourceLocation id) {
        return id != null && FACTORIES.remove(id) != null;
    }

    static void attach(MekanismTileContainer<?> container, TileEntityContainerBlock tile) {
        List<Map.Entry<ResourceLocation, Factory>> snapshot;
        synchronized (MekanismTileContainerExtensionRegistry.class) {
            snapshot = new ArrayList<>(FACTORIES.entrySet());
        }
        for (Map.Entry<ResourceLocation, Factory> entry : snapshot) {
            Object extension = entry.getValue().create(container, tile);
            if (extension != null) {
                container.addExtension(entry.getKey(), extension);
            }
        }
    }

    @FunctionalInterface
    public interface Factory {

        @Nullable
        Object create(@Nonnull MekanismTileContainer<?> container,
              @Nonnull TileEntityContainerBlock tile);
    }
}
