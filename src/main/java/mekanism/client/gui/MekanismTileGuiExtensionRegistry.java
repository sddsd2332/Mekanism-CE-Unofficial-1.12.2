package mekanism.client.gui;

import mekanism.client.gui.element.Widget;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Client-only factory registry for optional-module tabs on ordinary Mekanism machine screens. */
public final class MekanismTileGuiExtensionRegistry {

    private static final Map<ResourceLocation, Factory> FACTORIES = new LinkedHashMap<>();

    private MekanismTileGuiExtensionRegistry() {
    }

    public static synchronized void register(@Nonnull ResourceLocation id, @Nonnull Factory factory) {
        Objects.requireNonNull(id, "GUI extension id cannot be null");
        Objects.requireNonNull(factory, "GUI extension factory cannot be null");
        if (FACTORIES.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("Duplicate Mekanism tile GUI extension " + id);
        }
    }

    public static synchronized boolean unregister(@Nullable ResourceLocation id) {
        return id != null && FACTORIES.remove(id) != null;
    }

    static List<Widget> createElements(GuiMekanismTile<?, ?> gui,
          TileEntityContainerBlock tile) {
        List<Map.Entry<ResourceLocation, Factory>> snapshot;
        synchronized (MekanismTileGuiExtensionRegistry.class) {
            snapshot = new ArrayList<>(FACTORIES.entrySet());
        }
        List<Widget> elements = new ArrayList<>(snapshot.size());
        for (Map.Entry<ResourceLocation, Factory> entry : snapshot) {
            Widget element = entry.getValue().create(gui, tile);
            if (element != null) {
                elements.add(element);
            }
        }
        return elements;
    }

    @FunctionalInterface
    public interface Factory {

        @Nullable
        Widget create(@Nonnull GuiMekanismTile<?, ?> gui,
              @Nonnull TileEntityContainerBlock tile);
    }
}
