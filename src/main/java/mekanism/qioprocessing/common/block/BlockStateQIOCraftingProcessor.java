package mekanism.qioprocessing.common.block;

import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.block.states.BlockStateQIOComponent;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;

/** Listed render state for QIO crafting processors. */
public final class BlockStateQIOCraftingProcessor extends BlockStateContainer {

    public static final PropertyBool workingProperty = PropertyBool.create("working");

    public BlockStateQIOCraftingProcessor(Block block) {
        super(block, BlockStateFacing.facingProperty,
              BlockStateQIOComponent.activeProperty, workingProperty);
    }
}
