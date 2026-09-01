package mekanism.common.content.qio;

import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.api.qio.resource.QIOResourceFamilyMatcher;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registered storage policy for one physical QIO drive family.
 *
 * <p>The registry id is the persistent identity. A specialization owns its matcher and its
 * item-equivalent capacity for each drive definition. Mixed drives are the sole exception: their
 * capacity is calculated from every registered specialization that contributes to mixed storage.</p>
 */
public final class QIODriveSpecialization {

    private final ResourceLocation registryName;
    private final QIOResourceFamilyMatcher matcher;
    private final Map<ResourceLocation, QIOAmount> capacities;
    private final boolean useDefinitionCapacity;
    private final boolean contributesToMixed;
    private final boolean mixed;
    private final String translationKey;

    QIODriveSpecialization(Builder builder) {
        registryName = builder.registryName;
        matcher = Objects.requireNonNull(builder.matcher, "QIO drive specialization matcher cannot be null");
        capacities = Collections.unmodifiableMap(new LinkedHashMap<>(builder.capacities));
        useDefinitionCapacity = builder.useDefinitionCapacity;
        contributesToMixed = builder.contributesToMixed;
        mixed = builder.mixed;
        translationKey = builder.translationKey == null || builder.translationKey.isEmpty() ?
              "qio." + registryName.getNamespace() + ".drive_specialization." + registryName.getPath() :
              builder.translationKey;
        if (mixed) {
            if (!matcher.isAny() || contributesToMixed || useDefinitionCapacity || !capacities.isEmpty()) {
                throw new IllegalArgumentException("A mixed QIO drive specialization must use the dynamic registry capacity");
            }
        } else if (!useDefinitionCapacity && capacities.isEmpty()) {
            throw new IllegalArgumentException("A QIO drive specialization must declare at least one capacity");
        }
    }

    @Nonnull
    public static Builder builder(@Nonnull ResourceLocation registryName) {
        return new Builder(registryName);
    }

    @Nonnull
    public static Builder builder(@Nonnull String modid, @Nonnull String name) {
        return builder(new ResourceLocation(modid, name));
    }

    @Nonnull
    public ResourceLocation getRegistryName() {
        return registryName;
    }

    @Nonnull
    public String getRegistryNameString() {
        return registryName.toString();
    }

    @Nonnull
    public QIOResourceFamilyMatcher getMatcher() {
        return matcher;
    }

    public boolean accepts(@Nullable QIOResourceDescriptor descriptor) {
        return descriptor != null && matcher.accepts(descriptor);
    }

    public boolean accepts(@Nullable String family, @Nullable ResourceLocation codecId) {
        return matcher.accepts(family, codecId);
    }

    public boolean isMixed() {
        return mixed;
    }

    public boolean contributesToMixed() {
        return contributesToMixed;
    }

    @Nonnull
    public QIOAmount getExactCountCapacity(@Nonnull QIODriveDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (mixed) {
            return QIODriveSpecializationRegistry.INSTANCE.getMixedCountCapacity(definition);
        }
        if (useDefinitionCapacity) {
            return QIOAmount.of(definition.getMaxCount());
        }
        return capacities.getOrDefault(definition.getRegistryName(), QIOAmount.ZERO);
    }

    @Nonnull
    public QIOAmount getExactStorageCapacity(@Nonnull QIODriveDefinition definition) {
        return getExactCountCapacity(definition).multiply(QIOStorageUnits.UNITS_PER_ITEM);
    }

    public long getCountCapacity(@Nonnull QIODriveDefinition definition) {
        return getExactCountCapacity(definition).longValueClamped();
    }

    public long getStorageCapacity(@Nonnull QIODriveDefinition definition) {
        return getExactStorageCapacity(definition).longValueClamped();
    }

    public boolean supports(@Nullable QIODriveDefinition definition) {
        return definition != null && !getExactCountCapacity(definition).isZero();
    }

    @Nonnull
    public String getTranslationKey() {
        return translationKey;
    }

    @Override
    public String toString() {
        return registryName.toString();
    }

    public static final class Builder {

        private final ResourceLocation registryName;
        private final Map<ResourceLocation, QIOAmount> capacities = new LinkedHashMap<>();
        private QIOResourceFamilyMatcher matcher;
        private boolean useDefinitionCapacity;
        private boolean contributesToMixed = true;
        private boolean mixed;
        private String translationKey;

        private Builder(ResourceLocation registryName) {
            this.registryName = Objects.requireNonNull(registryName,
                  "QIO drive specialization registry name cannot be null");
        }

        @Nonnull
        public Builder matcher(@Nonnull QIOResourceFamilyMatcher matcher) {
            this.matcher = Objects.requireNonNull(matcher, "matcher");
            return this;
        }

        /** Uses the base count declared by every drive definition. */
        @Nonnull
        public Builder capacityFromDefinition() {
            useDefinitionCapacity = true;
            return this;
        }

        /** Declares an item-equivalent capacity for one stable drive definition id. */
        @Nonnull
        public Builder capacity(@Nonnull ResourceLocation definitionName, long capacity) {
            return capacity(definitionName, QIOAmount.of(capacity));
        }

        @Nonnull
        public Builder capacity(@Nonnull QIODriveDefinition definition, long capacity) {
            return capacity(Objects.requireNonNull(definition, "definition").getRegistryName(), capacity);
        }

        @Nonnull
        public Builder capacity(@Nonnull ResourceLocation definitionName, @Nonnull QIOAmount capacity) {
            Objects.requireNonNull(definitionName, "definitionName");
            Objects.requireNonNull(capacity, "capacity");
            if (capacity.isZero()) {
                throw new IllegalArgumentException("QIO specialization capacity must be positive");
            }
            if (capacities.putIfAbsent(definitionName, capacity) != null) {
                throw new IllegalArgumentException("Duplicate QIO specialization capacity for " + definitionName);
            }
            return this;
        }

        @Nonnull
        public Builder contributesToMixed(boolean contributesToMixed) {
            this.contributesToMixed = contributesToMixed;
            return this;
        }

        @Nonnull
        public Builder translationKey(@Nonnull String translationKey) {
            this.translationKey = Objects.requireNonNull(translationKey, "translationKey");
            return this;
        }

        Builder mixed() {
            mixed = true;
            contributesToMixed = false;
            matcher = QIOResourceFamilyMatcher.any();
            return this;
        }

        @Nonnull
        public QIODriveSpecialization register() {
            return QIODriveSpecializationRegistry.INSTANCE.register(new QIODriveSpecialization(this));
        }
    }
}
