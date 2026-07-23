package mekanism.common.content.qio;

import mekanism.common.tier.QIODriveTier;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Objects;

/** Defines which resource kinds a physical QIO drive may store. */
public enum QIODriveType {
    MIXED(null, "mixed", 3),
    ITEM(QIOResourceKind.ITEM, "item", 1),
    FLUID(QIOResourceKind.FLUID, "fluid", 1),
    GAS(QIOResourceKind.GAS, "gas", 1);

    @Nullable
    private final QIOResourceKind resourceKind;
    private final String serializedName;
    private final int countCapacityMultiplier;

    QIODriveType(@Nullable QIOResourceKind resourceKind, String serializedName, int countCapacityMultiplier) {
        this.resourceKind = resourceKind;
        this.serializedName = serializedName;
        this.countCapacityMultiplier = countCapacityMultiplier;
    }

    public boolean accepts(@Nullable QIOResourceKind kind) {
        return kind != null && (resourceKind == null || resourceKind == kind);
    }

    public boolean isMixed() {
        return resourceKind == null;
    }

    @Nullable
    public QIOResourceKind getResourceKind() {
        return resourceKind;
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
        return getExactCountCapacity(Objects.requireNonNull(definition, "definition").getMaxCount());
    }

    /** Applies this drive type's capacity multiplier to a base definition value. */
    public QIOAmount getExactCountCapacity(long baseCountCapacity) {
        return QIOAmount.of(baseCountCapacity).multiply(countCapacityMultiplier);
    }

    /** Exact fixed-point capacity used for resource insertion checks. */
    public QIOAmount getExactStorageCapacity(QIODriveDefinition definition) {
        return getExactCountCapacity(definition).multiply(QIOStorageUnits.UNITS_PER_ITEM);
    }

    public String getTranslationKey() {
        return "qio.mekanism.drive_type." + serializedName;
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
}
