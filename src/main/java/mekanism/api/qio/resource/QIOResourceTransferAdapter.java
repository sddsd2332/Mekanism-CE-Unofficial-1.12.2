package mekanism.api.qio.resource;

import mekanism.api.Action;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Explicit bridge between one QIO codec and an adjacent external storage capability.
 *
 * <p>Implementations must honor simulate/execute semantics. QIO never guesses custom transfer
 * behavior from a shared family name.</p>
 */
public interface QIOResourceTransferAdapter {

    @Nonnull
    ResourceLocation getCodecId();

    boolean supports(@Nonnull TileEntity target, @Nonnull EnumFacing targetFace);

    /** Returns a bounded snapshot of resources that may be extracted from the target. */
    @Nonnull
    List<QIOResourceStack> getExtractable(@Nonnull TileEntity target, @Nonnull EnumFacing targetFace,
          int maximumTypes, long maximumAmount);

    long extract(@Nonnull TileEntity target, @Nonnull EnumFacing targetFace,
          @Nonnull QIOResourceDescriptor descriptor, long amount, @Nonnull Action action);

    long insert(@Nonnull TileEntity target, @Nonnull EnumFacing targetFace,
          @Nonnull QIOResourceDescriptor descriptor, long amount, @Nonnull Action action);
}
