package mekanism.qioprocessing.common.item;

import mekanism.common.item.ItemBlockQIOComponent;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/** Processor blocks carry stable identity and durable buffers, so their item form is never stackable. */
/**
 * QIO 处理模块中的 ItemBlockQIOCraftingProcessor 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public class ItemBlockQIOCraftingProcessor extends ItemBlockQIOComponent {

    public ItemBlockQIOCraftingProcessor(Block block) {
        super(block);
        setMaxStackSize(1);
    }

    @Override
    public void restorePlacementData(@Nonnull ItemStack stack,
          @Nonnull EntityLivingBase placer, @Nonnull World world,
          @Nonnull BlockPos pos, @Nonnull TileEntity tileEntity) {
        super.restorePlacementData(stack, placer, world, pos, tileEntity);
        if (!world.isRemote && tileEntity instanceof QIOCraftingProcessor processor &&
            placer instanceof EntityPlayer player) {
            processor.restoreExactBinding(player.getUniqueID());
        }
    }
}
