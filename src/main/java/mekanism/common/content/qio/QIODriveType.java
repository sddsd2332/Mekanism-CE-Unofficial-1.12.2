package mekanism.common.content.qio;

import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.common.tier.QIODriveTier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Objects;

/**
 * Legacy source-compatible view of the four built-in drive specializations.
 *
 * @deprecated Use {@link QIODriveSpecialization}; addon specializations are registry entries and
 * intentionally cannot be represented by this enum.
 */
@Deprecated
public enum QIODriveType {
    MIXED(null, "mixed", QIODriveSpecializations.MIXED),
    ITEM(QIOResourceKind.ITEM, "item", QIODriveSpecializations.ITEM),
    FLUID(QIOResourceKind.FLUID, "fluid", QIODriveSpecializations.FLUID),
    GAS(QIOResourceKind.GAS, "gas", QIODriveSpecializations.GAS);

    @Nullable
    private final QIOResourceKind resourceKind;
    private final String serializedName;
    private final QIODriveSpecialization specialization;

    QIODriveType(@Nullable QIOResourceKind resourceKind, String serializedName,
          QIODriveSpecialization specialization) {
        this.resourceKind = resourceKind;
        this.serializedName = serializedName;
        this.specialization = specialization;
    }

    public boolean accepts(@Nullable QIOResourceKind kind) {
        return kind != null && (resourceKind == null || resourceKind == kind);
    }

    /** Exact family-based acceptance used by the extensible storage core. */
    public boolean accepts(@Nullable String family) {
        return family != null && (resourceKind == null || resourceKind.getFamily().equals(family));
    }

    public boolean accepts(@Nullable QIOResourceDescriptor descriptor) {
        return descriptor != null && accepts(descriptor.getFamily());
    }

    public boolean isMixed() {
        return resourceKind == null;
    }

    @Nullable
    public QIOResourceKind getResourceKind() {
        return resourceKind;
    }

    @Nullable
    public String getResourceFamily() {
        return resourceKind == null ? null : resourceKind.getFamily();
    }

    public String getSerializedName() {
        return serializedName;
    }

    public long getCountCapacity(QIODriveTier tier) {
        return getCountCapacity(Objects.requireNonNull(tier, "tier").getDefinition());
    }

    public long getStorageCapacity(QIODriveTier tier) {
        return getStorageCapacity(Objects.requireNonNull(tier, "tier").getDefinition());
    }

    public QIOAmount getExactCountCapacity(QIODriveTier tier) {
        return getExactCountCapacity(Objects.requireNonNull(tier, "tier").getDefinition());
    }

    public QIOAmount getExactStorageCapacity(QIODriveTier tier) {
        return getExactStorageCapacity(Objects.requireNonNull(tier, "tier").getDefinition());
    }

    public long getCountCapacity(QIODriveDefinition definition) {
        return getExactCountCapacity(definition).longValueClamped();
    }

    public long getStorageCapacity(QIODriveDefinition definition) {
        return getExactStorageCapacity(definition).longValueClamped();
    }

    /** Exact item-equivalent capacity, including the mixed-drive multiplier. */
    public QIOAmount getExactCountCapacity(QIODriveDefinition definition) {
        return specialization.getExactCountCapacity(Objects.requireNonNull(definition, "definition"));
    }

    /** Applies this drive type's capacity multiplier to a base definition value. */
    public QIOAmount getExactCountCapacity(long baseCountCapacity) {
        if (this == MIXED) {
            int contributors = 0;
            for (QIODriveSpecialization registered : QIODriveSpecializationRegistry.INSTANCE.getSpecializations().values()) {
                if (registered.contributesToMixed()) {
                    contributors++;
                }
            }
            return QIOAmount.of(baseCountCapacity).multiply(contributors);
        }
        return QIOAmount.of(baseCountCapacity);
    }

    /** Exact fixed-point capacity used for resource insertion checks. */
    public QIOAmount getExactStorageCapacity(QIODriveDefinition definition) {
        return getExactCountCapacity(definition).multiply(QIOStorageUnits.UNITS_PER_ITEM);
    }

    public String getTranslationKey() {
        return specialization.getTranslationKey();
    }

    @Nonnull
    public QIODriveSpecialization getSpecialization() {
        return specialization;
    }

    @Nullable
    public static QIODriveType byName(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        for (QIODriveType type : values()) {
            if (type.serializedName.equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    @Nullable
    public static QIODriveType fromSpecialization(@Nullable QIODriveSpecialization specialization) {
        if (specialization != null) {
            for (QIODriveType type : values()) {
                if (type.specialization.getRegistryName().equals(specialization.getRegistryName())) {
                    return type;
                }
            }
        }
        return null;
    }
}
