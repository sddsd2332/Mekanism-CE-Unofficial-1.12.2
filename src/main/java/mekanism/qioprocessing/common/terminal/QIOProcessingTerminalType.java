package mekanism.qioprocessing.common.terminal;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/** Fixed responsibilities of the four block and portable QIO Processing terminals. */
public enum QIOProcessingTerminalType {
    MANAGEMENT("management", false),
    SMART_PROCESSING("smart_processing", true),
    MAINTENANCE("maintenance", false),
    CRAFTING_MONITOR("crafting_monitor", false);

    private final String serializedName;
    private final boolean craftingWindows;

    QIOProcessingTerminalType(String serializedName, boolean craftingWindows) {
        this.serializedName = serializedName;
        this.craftingWindows = craftingWindows;
    }

    @Nonnull
    public String getSerializedName() {
        return serializedName;
    }

    public boolean hasCraftingWindows() {
        return craftingWindows;
    }

    @Nullable
    public static QIOProcessingTerminalType bySerializedName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (QIOProcessingTerminalType type : values()) {
            if (type.serializedName.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
