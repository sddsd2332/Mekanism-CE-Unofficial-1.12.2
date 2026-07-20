package mekanism.common.block.states;

import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;

/** Listed render state shared by QIO components. */
public class BlockStateQIOComponent extends BlockStateContainer {

    public static final PropertyBool activeProperty = PropertyBool.create("active");

    public BlockStateQIOComponent(Block block) {
        super(block, BlockStateFacing.facingProperty, activeProperty);
    }
}
