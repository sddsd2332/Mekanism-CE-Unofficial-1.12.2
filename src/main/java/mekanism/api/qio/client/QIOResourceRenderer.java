package mekanism.api.qio.client;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Client-only presentation for one registered QIO resource codec.
 *
 * <p>The resource value is an amount-free template decoded by the matching codec. Render
 * coordinates are relative to the current GUI and identify the upper-left corner of a 16 by 16
 * resource cell. Implementations must not retain or mutate the supplied value.</p>
 */
@SideOnly(Side.CLIENT)
public interface QIOResourceRenderer<T> {

    @Nonnull
    ResourceLocation getCodecId();

    @Nonnull
    Class<T> getValueClass();

    void render(@Nonnull T resource, int x, int y);

    @Nonnull
    String getDisplayName(@Nonnull T resource);

    /** Stable text used by registry-name sorting. */
    @Nonnull
    default String getRegistryName(@Nonnull T resource) {
        return getCodecId().toString();
    }

    /** Stable text used by mod-name sorting. */
    @Nonnull
    default String getModId(@Nonnull T resource) {
        return getCodecId().getNamespace();
    }

    /** Tooltip lines before the QIO stored-count line is appended. */
    @Nonnull
    default List<String> getTooltip(@Nonnull T resource) {
        return Collections.singletonList(getDisplayName(resource));
    }

    /** Optional ingredient understood by an installed recipe viewer. */
    @Nullable
    default Object getIngredient(@Nonnull T resource) {
        return null;
    }
}
