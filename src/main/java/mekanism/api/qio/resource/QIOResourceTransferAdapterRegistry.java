package mekanism.api.qio.resource;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registry of explicit adjacent-storage transfer adapters, keyed by codec id. */
public final class QIOResourceTransferAdapterRegistry {

    public static final QIOResourceTransferAdapterRegistry INSTANCE = new QIOResourceTransferAdapterRegistry();

    private final Map<ResourceLocation, QIOResourceTransferAdapter> adapters = new LinkedHashMap<>();

    private QIOResourceTransferAdapterRegistry() {
    }

    @Nonnull
    public synchronized QIOResourceTransferAdapter register(@Nonnull QIOResourceTransferAdapter adapter) {
        Objects.requireNonNull(adapter, "QIO resource transfer adapter cannot be null");
        ResourceLocation codecId = Objects.requireNonNull(adapter.getCodecId(), "QIO transfer codec id cannot be null");
        if (!QIOResourceCodecRegistry.INSTANCE.isRegistered(codecId)) {
            throw new IllegalArgumentException("A QIO resource codec must be registered before its transfer adapter: " + codecId);
        }
        if (adapters.putIfAbsent(codecId, adapter) != null) {
            throw new IllegalArgumentException("Duplicate QIO resource transfer adapter registered: " + codecId);
        }
        return adapter;
    }

    @Nullable
    public synchronized QIOResourceTransferAdapter get(@Nullable ResourceLocation codecId) {
        return codecId == null ? null : adapters.get(codecId);
    }

    @Nonnull
    public synchronized List<QIOResourceTransferAdapter> getAdapters() {
        return Collections.unmodifiableList(new ArrayList<>(adapters.values()));
    }
}
