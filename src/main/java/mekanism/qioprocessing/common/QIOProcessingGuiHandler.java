package mekanism.qioprocessing.common;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

/** Routes block and item GUI requests through the module's sided proxy. */
/**
 * QIO 处理模块中的 QIOProcessingGuiHandler 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProcessingGuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y,
          int z) {
        return MekanismQIOProcessing.proxy.getServerGui(id, player, world,
              new BlockPos(x, y, z));
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y,
          int z) {
        return MekanismQIOProcessing.proxy.getClientGui(id, player, world,
              new BlockPos(x, y, z));
    }
}
