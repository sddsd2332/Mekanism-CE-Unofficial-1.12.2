package mekanism.common.content.qio;

import mekanism.common.Mekanism;
import mekanism.common.tier.BaseTier;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Extensible capacity definition for QIO drive items.
 *
 * <p>Definitions are registered during mod startup and are identified by a
 * stable {@link ResourceLocation}. Addons can reuse the complete QIO drive
 * implementation by registering a definition and constructing an
 * {@link mekanism.common.item.ItemQIODrive} with it.</p>
 */
public final class QIODriveDefinition {

    private static final Map<ResourceLocation, QIODriveDefinition> REGISTRY = new LinkedHashMap<>();
    private static final Map<ResourceLocation, QIODriveDefinition> REGISTRY_VIEW = Collections.unmodifiableMap(REGISTRY);

    public static final QIODriveDefinition BASE = builder("base")
          .baseTier(BaseTier.BASIC)
          .maxCount(16_000L)
          .maxTypes(128)
          .register();
    public static final QIODriveDefinition HYPER_DENSE = builder("hyper_dense")
          .baseTier(BaseTier.ADVANCED)
          .maxCount(128_000L)
          .maxTypes(256)
          .register();
    public static final QIODriveDefinition TIME_DILATING = builder("time_dilating")
          .baseTier(BaseTier.ELITE)
          .maxCount(1_048_000L)
          .maxTypes(1_024)
          .register();
    public static final QIODriveDefinition SUPERMASSIVE = builder("supermassive")
          .baseTier(BaseTier.ULTIMATE)
          .maxCount(16_000_000_000L)
          .maxTypes(8_192)
          .register();

    private final ResourceLocation registryName;
    private final BaseTier baseTier;
    private final long maxCount;
    private final int maxTypes;
    private final boolean countUnlimited;
    private final boolean typesUnlimited;

    private QIODriveDefinition(Builder builder) {
        registryName = builder.registryName;
        baseTier = Objects.requireNonNull(builder.baseTier, "QIO drive base tier cannot be null");
        if (!builder.countConfigured) {
            throw new IllegalArgumentException("QIO drive count capacity must be configured");
        }
        if (!builder.countUnlimited && builder.maxCount <= 0) {
            throw new IllegalArgumentException("QIO drive count capacity must be positive: " + builder.maxCount);
        }
        if (!builder.typesConfigured) {
            throw new IllegalArgumentException("QIO drive type capacity must be configured");
        }
        if (!builder.typesUnlimited && builder.maxTypes <= 0) {
            throw new IllegalArgumentException("QIO drive type capacity must be positive: " + builder.maxTypes);
        }
        countUnlimited = builder.countUnlimited;
        typesUnlimited = builder.typesUnlimited;
        maxCount = countUnlimited ? Long.MAX_VALUE : builder.maxCount;
        maxTypes = typesUnlimited ? Integer.MAX_VALUE : builder.maxTypes;
    }

    public static Builder builder(String name) {
        return builder(Mekanism.MODID, name);
    }

    public static Builder builder(String modid, String name) {
        return builder(new ResourceLocation(modid, name));
    }

    public static Builder builder(ResourceLocation registryName) {
        return new Builder(registryName);
    }

    public static QIODriveDefinition register(Builder builder) {
        QIODriveDefinition definition = new QIODriveDefinition(
              Objects.requireNonNull(builder, "QIO drive definition builder cannot be null"));
        QIODriveDefinition previous = REGISTRY.putIfAbsent(definition.registryName, definition);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate QIO drive definition registered: " + definition.registryName);
        }
        return definition;
    }

    @Nullable
    public static QIODriveDefinition byName(@Nullable ResourceLocation registryName) {
        return registryName == null ? null : REGISTRY.get(registryName);
    }

    @Nullable
    public static QIODriveDefinition byName(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return byName(name.indexOf(':') == -1 ? new ResourceLocation(Mekanism.MODID, name) : new ResourceLocation(name));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isRegistered(@Nullable QIODriveDefinition definition) {
        return definition != null && REGISTRY.get(definition.registryName) == definition;
    }

    public static Map<ResourceLocation, QIODriveDefinition> getRegistry() {
        return REGISTRY_VIEW;
    }

    @Nonnull
    public ResourceLocation getRegistryName() {
        return registryName;
    }

    public String getRegistryNameString() {
        return registryName.toString();
    }

    @Nonnull
    public BaseTier getBaseTier() {
        return baseTier;
    }

    /** Base capacity used by one specialized item, fluid, or gas drive. */
    public long getMaxCount() {
        return maxCount;
    }

    public int getMaxTypes() {
        return maxTypes;
    }

    public boolean isCountUnlimited() {
        return countUnlimited;
    }

    public boolean isTypesUnlimited() {
        return typesUnlimited;
    }

    public boolean isCreativeCapacity() {
        return countUnlimited && typesUnlimited;
    }

    @Override
    public String toString() {
        return getRegistryNameString();
    }

    public static final class Builder {

        private final ResourceLocation registryName;
        private BaseTier baseTier;
        private long maxCount;
        private int maxTypes;
        private boolean countConfigured;
        private boolean typesConfigured;
        private boolean countUnlimited;
        private boolean typesUnlimited;

        private Builder(ResourceLocation registryName) {
            this.registryName = Objects.requireNonNull(registryName, "QIO drive definition registry name cannot be null");
        }

        public Builder baseTier(BaseTier baseTier) {
            this.baseTier = Objects.requireNonNull(baseTier, "QIO drive base tier cannot be null");
            return this;
        }

        public Builder maxCount(long maxCount) {
            boolean unlimited = maxCount == Long.MAX_VALUE;
            if (countConfigured && countUnlimited != unlimited) {
                throw new IllegalStateException("QIO drive count capacity is already configured as " +
                      (countUnlimited ? "unlimited" : "finite"));
            }
            countConfigured = true;
            countUnlimited = unlimited;
            this.maxCount = maxCount;
            return this;
        }

        public Builder maxTypes(int maxTypes) {
            boolean unlimited = maxTypes == Integer.MAX_VALUE;
            if (typesConfigured && typesUnlimited != unlimited) {
                throw new IllegalStateException("QIO drive type capacity is already configured as " +
                      (typesUnlimited ? "unlimited" : "finite"));
            }
            typesConfigured = true;
            typesUnlimited = unlimited;
            this.maxTypes = maxTypes;
            return this;
        }

        /** Marks resource-count capacity as unlimited without changing type capacity. */
        public Builder unlimitedCount() {
            return maxCount(Long.MAX_VALUE);
        }

        /** Marks type capacity as unlimited without changing resource-count capacity. */
        public Builder unlimitedTypes() {
            return maxTypes(Integer.MAX_VALUE);
        }

        /** Convenience for a drive with both resource-count and type capacity unlimited. */
        public Builder creativeCapacity() {
            return unlimitedCount().unlimitedTypes();
        }

        public QIODriveDefinition register() {
            return QIODriveDefinition.register(this);
        }
    }
}
