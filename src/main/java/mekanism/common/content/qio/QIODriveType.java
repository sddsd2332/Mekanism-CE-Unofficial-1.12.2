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
        return Math.multiplyExact(Objects.requireNonNull(tier, "tier").getMaxCount(), countCapacityMultiplier);
    }

    public long getStorageCapacity(QIODriveTier tier) {
        return QIOStorageUnits.toStorageCapacity(getCountCapacity(tier));
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
