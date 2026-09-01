package mekanism.api.qio.client;

import mekanism.api.qio.resource.QIOResourceCodec;
import mekanism.api.qio.resource.QIOResourceCodecRegistry;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Client-only registry of QIO resource presentations, keyed by the owning codec id. */
@SideOnly(Side.CLIENT)
public final class QIOResourceRendererRegistry {

    public static final QIOResourceRendererRegistry INSTANCE = new QIOResourceRendererRegistry();

    private final Map<ResourceLocation, QIOResourceRenderer<?>> renderers = new LinkedHashMap<>();

    private QIOResourceRendererRegistry() {
    }

    /** Registers a renderer after its common codec has been registered. Duplicate ids fail. */
    @Nonnull
    public synchronized <T> QIOResourceRenderer<T> register(@Nonnull QIOResourceRenderer<T> renderer) {
        Objects.requireNonNull(renderer, "QIO resource renderer cannot be null");
        ResourceLocation codecId = Objects.requireNonNull(renderer.getCodecId(),
              "QIO resource renderer codec id cannot be null");
        Class<T> valueClass = Objects.requireNonNull(renderer.getValueClass(),
              "QIO resource renderer value class cannot be null");
        QIOResourceCodec<?> codec = QIOResourceCodecRegistry.INSTANCE.get(codecId);
        if (codec == null) {
            throw new IllegalArgumentException("A QIO codec must be registered before its client renderer: " +
                  codecId);
        }
        if (!codec.getValueClass().equals(valueClass)) {
            throw new IllegalArgumentException("QIO renderer value class does not match codec " + codecId +
                  ": " + valueClass.getName() + " != " + codec.getValueClass().getName());
        }
        if (renderers.putIfAbsent(codecId, renderer) != null) {
            throw new IllegalArgumentException("Duplicate QIO resource renderer registered: " + codecId);
        }
        return renderer;
    }

    @Nullable
    public synchronized QIOResourceRenderer<?> get(@Nullable ResourceLocation codecId) {
        return codecId == null ? null : renderers.get(codecId);
    }

    @Nonnull
    public synchronized Map<ResourceLocation, QIOResourceRenderer<?>> getRenderers() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(renderers));
    }
}
