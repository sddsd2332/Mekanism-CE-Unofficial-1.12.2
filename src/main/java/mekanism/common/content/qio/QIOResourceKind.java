package mekanism.common.content.qio;

import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Legacy compatibility view for the three built-in resource codecs.
 *
 * @deprecated Resource identity is defined by {@link QIOResourceDescriptor}; custom codecs are
 * intentionally not representable by this compatibility enum.
 */
@Deprecated
public enum QIOResourceKind {
    ITEM(QIOResourceCodecs.ITEM_STACK_ID, QIOResourceCodecs.ITEM_FAMILY),
    FLUID(QIOResourceCodecs.FLUID_STACK_ID, QIOResourceCodecs.FLUID_FAMILY),
    GAS(QIOResourceCodecs.GAS_STACK_ID, QIOResourceCodecs.GAS_FAMILY);

    @Nullable
    private final ResourceLocation codecId;
    private final String family;

    QIOResourceKind(@Nullable ResourceLocation codecId, String family) {
        this.codecId = codecId;
        this.family = family;
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public ResourceLocation getCodecId() {
        return codecId;
    }

    public String getFamily() {
        return family;
    }

    public boolean isBuiltin() {
        return codecId != null;
    }

    @Nullable
    public static QIOResourceKind fromDescriptor(@Nullable QIOResourceDescriptor descriptor) {
        if (descriptor != null) {
            for (QIOResourceKind kind : values()) {
                if (kind.codecId != null && kind.codecId.equals(descriptor.getCodecId())) {
                    return kind;
                }
            }
        }
        return null;
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
