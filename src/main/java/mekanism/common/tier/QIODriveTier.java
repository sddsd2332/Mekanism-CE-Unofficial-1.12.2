package mekanism.common.tier;

import javax.annotation.Nullable;
import java.util.Locale;

public enum QIODriveTier implements ITier {
    BASE(BaseTier.BASIC, 16_000L, 128),
    HYPER_DENSE(BaseTier.ADVANCED, 128_000L, 256),
    TIME_DILATING(BaseTier.ELITE, 1_048_000L, 1_024),
    SUPERMASSIVE(BaseTier.ULTIMATE, 16_000_000_000L, 8_192);

    // Names used by older QIO ports. They intentionally point at the same
    // canonical enum values so serialized tier names remain stable.
    public static final QIODriveTier BASIC = BASE;
    public static final QIODriveTier ADVANCED = HYPER_DENSE;
    public static final QIODriveTier ELITE = TIME_DILATING;
    public static final QIODriveTier ULTIMATE = SUPERMASSIVE;

    private final BaseTier baseTier;
    private final long maxCount;
    private final int maxTypes;

    QIODriveTier(BaseTier baseTier, long maxCount, int maxTypes) {
        this.baseTier = baseTier;
        this.maxCount = maxCount;
        this.maxTypes = maxTypes;
    }

    @Override
    public BaseTier getBaseTier() {
        return baseTier;
    }

    public long getMaxCount() {
        return maxCount;
    }

    public int getMaxTypes() {
        return maxTypes;
    }

    public long getCountCapacity() {
        return maxCount;
    }

    public int getTypeCapacity() {
        return maxTypes;
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
}
