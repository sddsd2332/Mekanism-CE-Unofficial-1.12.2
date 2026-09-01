package mekanism.api.qio.external;

import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Explicit identity conversion between one QIO codec and one external storage system. */
public interface QIOResourceExternalConverter<T> {

    @Nonnull
    ResourceLocation getExternalSystemId();

    @Nonnull
    ResourceLocation getCodecId();

    @Nonnull
    Class<T> getExternalType();

    @Nullable
    T toExternal(@Nonnull QIOResourceDescriptor descriptor);

    @Nullable
    QIOResourceDescriptor fromExternal(@Nonnull T externalResource);
}
