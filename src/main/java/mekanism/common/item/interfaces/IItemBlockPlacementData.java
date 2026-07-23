package mekanism.common.item.interfaces;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * Restores Mekanism data from a block item after the block and tile have been created.
 *
 * <p>This hook is invoked from {@code Block#onBlockPlacedBy}, so placement helpers that
 * bypass {@code ItemBlock#placeBlockAt} still preserve the item's tier and contents.</p>
 */
public interface IItemBlockPlacementData {

    void restorePlacementData(@Nonnull ItemStack stack, @Nonnull EntityLivingBase placer, @Nonnull World world,
          @Nonnull BlockPos pos, @Nonnull TileEntity tileEntity);
}
