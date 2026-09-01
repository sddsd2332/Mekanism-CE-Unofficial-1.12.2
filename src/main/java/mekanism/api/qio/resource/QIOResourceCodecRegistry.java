package mekanism.api.qio.resource;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Process-wide registry of uniquely named QIO resource codecs. */
public final class QIOResourceCodecRegistry {

    public static final QIOResourceCodecRegistry INSTANCE = new QIOResourceCodecRegistry();

    private final Map<ResourceLocation, QIOResourceCodec<?>> codecs = new LinkedHashMap<>();

    private QIOResourceCodecRegistry() {
        QIOResourceCodecs.registerBuiltins(this);
    }

    /** Registers one codec. Duplicate ids always fail, even when both registrations use one class. */
    @Nonnull
    public synchronized <T> QIOResourceCodec<T> register(@Nonnull QIOResourceCodec<T> codec) {
        Objects.requireNonNull(codec, "QIO resource codec cannot be null");
        ResourceLocation codecId = Objects.requireNonNull(codec.getCodecId(), "QIO codec id cannot be null");
        QIOResourceFamily.requireValid(codec.getFamily());
        Objects.requireNonNull(codec.getValueClass(), "QIO codec value class cannot be null");
        if (codec.getCodecVersion() <= 0) {
            throw new IllegalArgumentException("QIO codec version must be positive: " + codecId);
        }
        if (codec.getStorageUnitsPerUnit() <= 0) {
            throw new IllegalArgumentException("QIO codec storage units must be positive: " + codecId);
        }
        QIOResourceCodec<?> previous = codecs.putIfAbsent(codecId, codec);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate QIO resource codec registered: " + codecId);
        }
        return codec;
    }

    @Nullable
    public synchronized QIOResourceCodec<?> get(@Nullable ResourceLocation codecId) {
        return codecId == null ? null : codecs.get(codecId);
    }

    @Nullable
    public synchronized QIOResourceCodec<?> get(@Nullable String codecId) {
        if (codecId == null || codecId.isEmpty()) {
            return null;
        }
        try {
            return get(new ResourceLocation(codecId));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public synchronized boolean isRegistered(@Nullable ResourceLocation codecId) {
        return codecId != null && codecs.containsKey(codecId);
    }

    @Nonnull
    public synchronized Map<ResourceLocation, QIOResourceCodec<?>> getCodecs() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(codecs));
    }
}
