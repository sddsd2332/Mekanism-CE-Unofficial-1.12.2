package mekanism.common.tier;

import mekanism.common.content.qio.QIODriveDefinition;

import javax.annotation.Nullable;
import java.util.Locale;

public enum QIODriveTier implements ITier {
    BASE(QIODriveDefinition.BASE),
    HYPER_DENSE(QIODriveDefinition.HYPER_DENSE),
    TIME_DILATING(QIODriveDefinition.TIME_DILATING),
    SUPERMASSIVE(QIODriveDefinition.SUPERMASSIVE);

    // Names used by older QIO ports. They intentionally point at the same
    // canonical enum values so serialized tier names remain stable.
    public static final QIODriveTier BASIC = BASE;
    public static final QIODriveTier ADVANCED = HYPER_DENSE;
    public static final QIODriveTier ELITE = TIME_DILATING;
    public static final QIODriveTier ULTIMATE = SUPERMASSIVE;

    private final QIODriveDefinition definition;

    QIODriveTier(QIODriveDefinition definition) {
        this.definition = definition;
    }

    @Override
    public BaseTier getBaseTier() {
        return definition.getBaseTier();
    }

    public long getMaxCount() {
        return definition.getMaxCount();
    }

    public int getMaxTypes() {
        return definition.getMaxTypes();
    }

    public long getCountCapacity() {
        return getMaxCount();
    }

    public int getTypeCapacity() {
        return getMaxTypes();
    }

    public QIODriveDefinition getDefinition() {
        return definition;
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static QIODriveTier byName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String normalized = name.toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "BASIC" -> "BASE";
            case "ADVANCED" -> "HYPER_DENSE";
            case "ELITE" -> "TIME_DILATING";
            case "ULTIMATE" -> "SUPERMASSIVE";
            default -> normalized;
        };
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    public static QIODriveTier byDefinition(@Nullable QIODriveDefinition definition) {
        if (definition == null) {
            return null;
        }
        for (QIODriveTier tier : values()) {
            if (tier.definition == definition || tier.definition.getRegistryName().equals(definition.getRegistryName())) {
                return tier;
            }
        }
        return null;
    }
}
