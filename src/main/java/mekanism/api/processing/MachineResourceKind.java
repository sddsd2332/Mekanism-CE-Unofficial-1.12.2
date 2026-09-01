package mekanism.api.processing;

import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Legacy resource channel view used by existing machine integrations.
 * Resource identity and compatibility are defined by the descriptor and port matcher.
 */
public enum MachineResourceKind {
    ITEM(QIOResourceCodecs.ITEM_STACK_ID),
    FLUID(QIOResourceCodecs.FLUID_STACK_ID),
    GAS(QIOResourceCodecs.GAS_STACK_ID),
    CUSTOM(null);

    @Nullable
    private final ResourceLocation codecId;

    MachineResourceKind(@Nullable ResourceLocation codecId) {
        this.codecId = codecId;
    }

    @Nullable
    public ResourceLocation getCodecId() {
        return codecId;
    }

    public static MachineResourceKind fromDescriptor(QIOResourceDescriptor descriptor) {
        if (descriptor != null) {
            for (MachineResourceKind kind : values()) {
                if (kind.codecId != null && kind.codecId.equals(descriptor.getCodecId())) {
                    return kind;
                }
            }
        }
        return CUSTOM;
    }
}
