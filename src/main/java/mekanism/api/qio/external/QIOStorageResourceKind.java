package mekanism.api.qio.external;

import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceDescriptor;

import javax.annotation.Nullable;

/**
 * Legacy compatibility view for the built-in external storage codecs.
 *
 * @deprecated Use {@link QIOResourceDescriptor#getCodecId()} and
 * {@link QIOResourceDescriptor#getFamily()}.
 */
@Deprecated
public enum QIOStorageResourceKind {
    ITEM,
    FLUID,
    GAS;

    @Nullable
    public static QIOStorageResourceKind fromDescriptor(@Nullable QIOResourceDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        if (QIOResourceCodecs.ITEM_STACK_ID.equals(descriptor.getCodecId())) {
            return ITEM;
        }
        if (QIOResourceCodecs.FLUID_STACK_ID.equals(descriptor.getCodecId())) {
            return FLUID;
        }
        return QIOResourceCodecs.GAS_STACK_ID.equals(descriptor.getCodecId()) ? GAS : null;
    }
}
