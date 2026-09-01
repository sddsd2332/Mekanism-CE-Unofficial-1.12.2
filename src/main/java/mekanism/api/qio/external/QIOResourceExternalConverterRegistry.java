package mekanism.api.qio.external;

import mekanism.api.qio.resource.QIOResourceCodecRegistry;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Registry of explicit QIO-to-external-storage converters. */
public final class QIOResourceExternalConverterRegistry {

    public static final QIOResourceExternalConverterRegistry INSTANCE =
          new QIOResourceExternalConverterRegistry();

    private final Map<Key, QIOResourceExternalConverter<?>> converters = new LinkedHashMap<>();
    private final Map<ExternalTypeKey, QIOResourceExternalConverter<?>> convertersByExternalType =
          new LinkedHashMap<>();

    private QIOResourceExternalConverterRegistry() {
    }

    @Nonnull
    public synchronized <T> QIOResourceExternalConverter<T> register(
          @Nonnull QIOResourceExternalConverter<T> converter) {
        Objects.requireNonNull(converter, "QIO external converter cannot be null");
        ResourceLocation systemId = Objects.requireNonNull(converter.getExternalSystemId(),
              "QIO external system id cannot be null");
        ResourceLocation codecId = Objects.requireNonNull(converter.getCodecId(),
              "QIO external converter codec id cannot be null");
        Class<T> externalType = Objects.requireNonNull(converter.getExternalType(),
              "QIO external converter type cannot be null");
        if (!QIOResourceCodecRegistry.INSTANCE.isRegistered(codecId)) {
            throw new IllegalArgumentException("A QIO codec must be registered before its external converter: " +
                  codecId);
        }
        Key key = new Key(systemId, codecId);
        ExternalTypeKey typeKey = new ExternalTypeKey(systemId, externalType);
        if (converters.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate QIO external converter registered for " + key);
        }
        if (convertersByExternalType.containsKey(typeKey)) {
            throw new IllegalArgumentException("Ambiguous QIO external converter type registered for " + typeKey);
        }
        converters.put(key, converter);
        convertersByExternalType.put(typeKey, converter);
        return converter;
    }

    @Nullable
    public synchronized QIOResourceExternalConverter<?> get(@Nullable ResourceLocation externalSystemId,
          @Nullable ResourceLocation codecId) {
        return externalSystemId == null || codecId == null ? null :
              converters.get(new Key(externalSystemId, codecId));
    }

    @Nullable
    public synchronized Object toExternal(@Nonnull ResourceLocation externalSystemId,
          @Nonnull QIOResourceDescriptor descriptor) {
        Objects.requireNonNull(externalSystemId, "externalSystemId");
        Objects.requireNonNull(descriptor, "descriptor");
        if (!descriptor.isResolved()) {
            return null;
        }
        QIOResourceExternalConverter<?> converter = get(externalSystemId, descriptor.getCodecId());
        return converter == null ? null : toExternalUnchecked(converter, descriptor);
    }

    @Nullable
    public synchronized QIOResourceDescriptor fromExternal(@Nonnull ResourceLocation externalSystemId,
          @Nonnull Object externalResource) {
        Objects.requireNonNull(externalSystemId, "externalSystemId");
        Objects.requireNonNull(externalResource, "externalResource");
        QIOResourceExternalConverter<?> exact = convertersByExternalType.get(
              new ExternalTypeKey(externalSystemId, externalResource.getClass()));
        if (exact != null) {
            return fromExternalUnchecked(exact, externalResource);
        }
        QIOResourceExternalConverter<?> candidate = null;
        for (Map.Entry<ExternalTypeKey, QIOResourceExternalConverter<?>> entry :
              convertersByExternalType.entrySet()) {
            if (entry.getKey().systemId.equals(externalSystemId) &&
                  entry.getKey().externalType.isInstance(externalResource)) {
                if (candidate != null) {
                    return null;
                }
                candidate = entry.getValue();
            }
        }
        return candidate == null ? null : fromExternalUnchecked(candidate, externalResource);
    }

    @Nonnull
    public synchronized Map<String, QIOResourceExternalConverter<?>> getConverters() {
        Map<String, QIOResourceExternalConverter<?>> snapshot = new LinkedHashMap<>();
        converters.forEach((key, value) -> snapshot.put(key.toString(), value));
        return Collections.unmodifiableMap(snapshot);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object toExternalUnchecked(QIOResourceExternalConverter converter,
          QIOResourceDescriptor descriptor) {
        try {
            Object converted = converter.toExternal(descriptor);
            return converted == null || converter.getExternalType().isInstance(converted) ? converted : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static QIOResourceDescriptor fromExternalUnchecked(QIOResourceExternalConverter converter,
          Object externalResource) {
        try {
            QIOResourceDescriptor descriptor = converter.fromExternal(externalResource);
            return descriptor != null && descriptor.isResolved() &&
                  converter.getCodecId().equals(descriptor.getCodecId()) ? descriptor : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static final class Key {
        private final ResourceLocation systemId;
        private final ResourceLocation codecId;

        private Key(ResourceLocation systemId, ResourceLocation codecId) {
            this.systemId = systemId;
            this.codecId = codecId;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || obj instanceof Key other && systemId.equals(other.systemId) &&
                  codecId.equals(other.codecId);
        }

        @Override
        public int hashCode() {
            return 31 * systemId.hashCode() + codecId.hashCode();
        }

        @Override
        public String toString() {
            return systemId + "|" + codecId;
        }
    }

    private static final class ExternalTypeKey {
        private final ResourceLocation systemId;
        private final Class<?> externalType;

        private ExternalTypeKey(ResourceLocation systemId, Class<?> externalType) {
            this.systemId = systemId;
            this.externalType = externalType;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || obj instanceof ExternalTypeKey other && systemId.equals(other.systemId) &&
                  externalType.equals(other.externalType);
        }

        @Override
        public int hashCode() {
            return 31 * systemId.hashCode() + externalType.hashCode();
        }

        @Override
        public String toString() {
            return systemId + "|" + externalType.getName();
        }
    }
}
