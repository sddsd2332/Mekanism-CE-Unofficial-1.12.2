package mekanism.api.qio.resource;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * Defines the stable identity, persistence and capacity semantics of one QIO resource format.
 *
 * <p>The codec id is globally unique. The family is intentionally reusable and is only used for
 * exact semantic matching; it never selects a decoder or implies conversion compatibility.</p>
 */
public interface QIOResourceCodec<T> {

    @Nonnull
    ResourceLocation getCodecId();

    @Nonnull
    String getFamily();

    @Nonnull
    Class<T> getValueClass();

    /** Current payload version written by this codec. */
    default int getCodecVersion() {
        return 1;
    }

    /** Returns an immutable-by-contract, amount-free resource template. */
    @Nonnull
    T normalize(@Nonnull T value);

    boolean sameType(@Nonnull T first, @Nonnull T second);

    int typeHash(@Nonnull T value);

    @Nonnull
    NBTTagCompound writeTemplate(@Nonnull T value);

    /** Reads any payload version explicitly supported by this codec. */
    @Nonnull
    T readTemplate(@Nonnull NBTTagCompound payload, int codecVersion);

    /** Fixed-point QIO storage units consumed by one minimum integer unit of this resource. */
    long getStorageUnitsPerUnit();
}
