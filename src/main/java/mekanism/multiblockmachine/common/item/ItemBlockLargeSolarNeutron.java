package mekanism.multiblockmachine.common.item;

import mekanism.api.EnumColor;
import mekanism.common.Mekanism;
import mekanism.common.util.LangUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public class ItemBlockLargeSolarNeutron extends ItemBlockLargeBase {

    public ItemBlockLargeSolarNeutron(Block block) {
        super(block, "LargeSolarNeutronActivator");
    }

    @Override
    public boolean canPlace(@Nonnull ItemStack stack, @Nonnull EntityPlayer player, World world, @Nonnull BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, @Nonnull IBlockState state) {
        BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos();
        for (int yPos = 0; yPos <= 2; yPos++) {
            for (int xPos = -1; xPos <= 1; xPos++) {
                for (int zPos = -1; zPos <= 1; zPos++) {
                    testPos.setPos(pos.getX() + xPos, pos.getY() + yPos, pos.getZ() + zPos);
                    Block b = world.getBlockState(testPos).getBlock();
                    if (!world.isValid(testPos) || !world.isBlockLoaded(testPos, false) || !b.isReplaceable(world, testPos)) {
                        if (player instanceof EntityPlayerMP mp) {
                            mp.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + EnumColor.GREY + " " + LangUtils.localize("tooltip.canPlace.pos") + ": " + "X " + testPos.getX() + " " + "Y " + testPos.getY() + " " + "Z " + testPos.getZ()));
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
