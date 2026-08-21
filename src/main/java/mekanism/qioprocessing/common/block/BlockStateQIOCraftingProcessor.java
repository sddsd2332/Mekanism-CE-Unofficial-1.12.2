package mekanism.qioprocessing.common.block;

import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.block.states.BlockStateQIOComponent;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;

/** Listed render state for QIO crafting processors. */
/**
 * QIO 处理模块中的 BlockStateQIOCraftingProcessor 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class BlockStateQIOCraftingProcessor extends BlockStateContainer {

    public static final PropertyBool workingProperty = PropertyBool.create("working");

    public BlockStateQIOCraftingProcessor(Block block) {
        super(block, BlockStateFacing.facingProperty,
              BlockStateQIOComponent.activeProperty, workingProperty);
    }
}
