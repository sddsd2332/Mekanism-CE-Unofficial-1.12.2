package mekanism.qioprocessing.api.processor;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit, frozen registration boundary for built-in and addon processor TileEntity families. */
public final class QIOCraftingProcessorHostRegistry {

    public static final class ResolvedHost {

        private final QIOCraftingProcessorHostRegistration<?> registration;
        private final QIOCraftingProcessorDefinition definition;

        private ResolvedHost(QIOCraftingProcessorHostRegistration<?> registration,
              QIOCraftingProcessorDefinition definition) {
            this.registration = registration;
            this.definition = definition;
        }

        @Nonnull
        public ResourceLocation getHostId() {
            return registration.getHostId();
        }

        @Nonnull
        public String getOwnerModId() {
            return registration.getOwnerModId();
        }

        @Nonnull
        public Class<? extends TileEntity> getTileClass() {
            return registration.getTileClass();
        }

        @Nonnull
        public QIOCraftingProcessorDefinition getDefinition() {
            return definition;
        }
    }

    private static final Map<ResourceLocation, QIOCraftingProcessorHostRegistration<?>> BY_ID =
          new LinkedHashMap<>();
    private static final Map<Class<? extends TileEntity>, QIOCraftingProcessorHostRegistration<?>> BY_TILE =
          new LinkedHashMap<>();
    private static boolean frozen;

    private QIOCraftingProcessorHostRegistry() {
    }

    @Nonnull
    public static synchronized <T extends TileEntity> QIOCraftingProcessorHostRegistration<T> register(
          @Nonnull QIOCraftingProcessorHostRegistration<T> registration) {
        Objects.requireNonNull(registration, "registration");
        if (frozen) {
            throw new IllegalStateException("QIO crafting processor host registration is already frozen");
        }
        if (BY_ID.containsKey(registration.getHostId())) {
            throw new IllegalArgumentException("Duplicate QIO crafting processor host id: " +
                  registration.getHostId());
        }
        if (BY_TILE.containsKey(registration.getTileClass())) {
            throw new IllegalArgumentException("Duplicate QIO crafting processor TileEntity registration: " +
                  registration.getTileClass().getName());
        }
        BY_ID.put(registration.getHostId(), registration);
        BY_TILE.put(registration.getTileClass(), registration);
        return registration;
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    public static synchronized boolean isFrozen() {
        return frozen;
    }

    @Nullable
    public static synchronized QIOCraftingProcessorHostRegistration<?> get(ResourceLocation hostId) {
        return hostId == null ? null : BY_ID.get(hostId);
    }

    @Nonnull
    public static synchronized List<QIOCraftingProcessorHostRegistration<?>> getRegistrations() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    @Nullable
    public static synchronized ResolvedHost resolve(@Nullable TileEntity tile) {
        if (tile == null) {
            return null;
        }
        QIOCraftingProcessorHostRegistration<?> selected = null;
        Class<?> actualClass = tile.getClass();
        for (QIOCraftingProcessorHostRegistration<?> candidate : BY_ID.values()) {
            Class<? extends TileEntity> candidateClass = candidate.getTileClass();
            if (!candidateClass.isAssignableFrom(actualClass)) {
                continue;
            }
            if (selected == null || selected.getTileClass().isAssignableFrom(candidateClass)) {
                selected = candidate;
            }
        }
        if (selected == null) {
            return null;
        }
        ResourceLocation definitionId = selected.resolveDefinitionId(tile);
        QIOCraftingProcessorDefinition definition = QIOCraftingProcessorRegistry.get(definitionId);
        if (definition == null) {
            throw new IllegalStateException("QIO processor host " + selected.getHostId() +
                  " resolved an unregistered definition " + definitionId);
        }
        return new ResolvedHost(selected, definition);
    }

    static synchronized void resetForTests() {
        BY_ID.clear();
        BY_TILE.clear();
        frozen = false;
    }
}
