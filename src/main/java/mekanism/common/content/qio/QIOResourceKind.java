package mekanism.common.content.qio;

import javax.annotation.Nullable;
import java.util.Locale;

public enum QIOResourceKind {
    ITEM,
    FLUID,
    GAS;

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static QIOResourceKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
    }

    @Nullable
    public static QIOResourceKind byName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
