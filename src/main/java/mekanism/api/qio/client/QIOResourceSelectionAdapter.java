package mekanism.api.qio.client;

import mekanism.api.qio.resource.QIOResourceDescriptor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Client-only reverse mapping used by QIO resource target slots.
 *
 * <p>A renderer presents a descriptor that QIO already knows about. This adapter handles the
 * opposite direction: turning a recipe-viewer ingredient or the contents of a held container
 * into a descriptor owned by one codec. Implementations must return amount-free descriptors and
 * must not mutate the supplied ingredient or container.</p>
 */
@SideOnly(Side.CLIENT)
public interface QIOResourceSelectionAdapter {

    @Nonnull
    ResourceLocation getCodecId();

    /** Returns a descriptor when this codec understands the supplied recipe-viewer ingredient. */
    @Nullable
    default QIOResourceDescriptor fromIngredient(@Nonnull Object ingredient) {
        return null;
    }

    /** Returns the resources of this codec represented by the current container state. */
    @Nonnull
    default List<QIOResourceDescriptor> getContainedResources(@Nonnull ItemStack container) {
        return Collections.emptyList();
    }
}
