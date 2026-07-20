package mekanism.common.block.states;

import mekanism.common.block.BlockQIODriveArray;
import mekanism.common.block.property.PropertyDriveStatus;
import net.minecraft.block.properties.IProperty;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Block state container for the drive array's facing and client-only status data. */
public class BlockStateQIODriveArray extends ExtendedBlockState {

    public BlockStateQIODriveArray(BlockQIODriveArray block) {
        super(block, new IProperty[]{BlockStateFacing.facingProperty, BlockStateQIOComponent.activeProperty},
              new IUnlistedProperty[]{PropertyDriveStatus.INSTANCE});
    }
}
