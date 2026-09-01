package mekanism.api.qio.client;

import mekanism.api.qio.resource.QIOResourceCodecRegistry;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Client-only registry of addon resource selection adapters, keyed by codec id. */
@SideOnly(Side.CLIENT)
public final class QIOResourceSelectionAdapterRegistry {

    public static final QIOResourceSelectionAdapterRegistry INSTANCE =
          new QIOResourceSelectionAdapterRegistry();

    private static final int MAX_CANDIDATES = 32;

    private final Map<ResourceLocation, QIOResourceSelectionAdapter> adapters = new LinkedHashMap<>();

    private QIOResourceSelectionAdapterRegistry() {
    }

    /** Registers an adapter after its common codec. Duplicate codec ownership fails. */
    @Nonnull
    public synchronized QIOResourceSelectionAdapter register(
          @Nonnull QIOResourceSelectionAdapter adapter) {
        Objects.requireNonNull(adapter, "QIO resource selection adapter cannot be null");
        ResourceLocation codecId = Objects.requireNonNull(adapter.getCodecId(),
              "QIO resource selection adapter codec id cannot be null");
        if (!QIOResourceCodecRegistry.INSTANCE.isRegistered(codecId)) {
            throw new IllegalArgumentException(
                  "A QIO codec must be registered before its selection adapter: " + codecId);
        }
        if (adapters.putIfAbsent(codecId, adapter) != null) {
            throw new IllegalArgumentException(
                  "Duplicate QIO resource selection adapter registered: " + codecId);
        }
        return adapter;
    }

    @Nullable
    public synchronized QIOResourceSelectionAdapter get(@Nullable ResourceLocation codecId) {
        return codecId == null ? null : adapters.get(codecId);
    }

    /** Returns every valid codec candidate. Callers must reject ambiguous results. */
    @Nonnull
    public synchronized List<QIOResourceDescriptor> getIngredientCandidates(
          @Nonnull Object ingredient) {
        Objects.requireNonNull(ingredient, "QIO selection ingredient cannot be null");
        Set<QIOResourceDescriptor> candidates = new LinkedHashSet<>();
        for (QIOResourceSelectionAdapter adapter : adapters.values()) {
            try {
                addCandidate(candidates, adapter, adapter.fromIngredient(ingredient));
            } catch (RuntimeException | LinkageError ignored) {
            }
            if (candidates.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(candidates));
    }

    /** Returns every valid resource represented by the current container state. */
    @Nonnull
    public synchronized List<QIOResourceDescriptor> getContainedResourceCandidates(
          @Nonnull ItemStack container) {
        Objects.requireNonNull(container, "QIO selection container cannot be null");
        if (container.isEmpty()) {
            return Collections.emptyList();
        }
        Set<QIOResourceDescriptor> candidates = new LinkedHashSet<>();
        for (QIOResourceSelectionAdapter adapter : adapters.values()) {
            try {
                List<QIOResourceDescriptor> supplied = adapter.getContainedResources(container.copy());
                if (supplied != null) {
                    for (QIOResourceDescriptor descriptor : supplied) {
                        addCandidate(candidates, adapter, descriptor);
                        if (candidates.size() >= MAX_CANDIDATES) {
                            break;
                        }
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
            if (candidates.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(candidates));
    }

    @Nonnull
    public synchronized Map<ResourceLocation, QIOResourceSelectionAdapter> getAdapters() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(adapters));
    }

    private static void addCandidate(Set<QIOResourceDescriptor> candidates,
          QIOResourceSelectionAdapter adapter, @Nullable QIOResourceDescriptor descriptor) {
        if (descriptor != null && descriptor.isResolved() &&
              adapter.getCodecId().equals(descriptor.getCodecId())) {
            candidates.add(descriptor);
        }
    }
}
