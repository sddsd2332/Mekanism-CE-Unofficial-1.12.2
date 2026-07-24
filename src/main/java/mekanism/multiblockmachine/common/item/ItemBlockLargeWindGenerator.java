package mekanism.multiblockmachine.common.item;

import mekanism.api.EnumColor;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeWindGenerator;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nonnull;
import java.util.Map;

public class ItemBlockLargeWindGenerator extends ItemBlockLargeBaseEnergy {

    public ItemBlockLargeWindGenerator(Block block) {
        super(block, "LargeWindGenerator");
    }

    @Override
    public double getMachineStorage() {
        return MekanismConfig.current().generators.windGeneratorStorage.val() * MekanismConfig.current().multiblock.LargeWindGeneratorProcesses.val();
    }

    @Override
    public boolean canPlace(@Nonnull ItemStack stack, @Nonnull EntityPlayer player, World world, @Nonnull BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, @Nonnull IBlockState state) {
        boolean isCanPlace = false;
        BlockPos[] blockedPosition = {null};
        TileEntityLargeWindGenerator.collectBoundingBlocks(pos, player.getHorizontalFacing().getOpposite(), (testPos, advanced) -> {
            if (blockedPosition[0] == null && !MekanismUtils.isValidBoundingBlockPosition(world, testPos, pos)) {
                blockedPosition[0] = testPos;
            }
        });
        if (blockedPosition[0] != null) {
            isCanPlace = true;
            BlockPos testPos = blockedPosition[0];
            if (player instanceof EntityPlayerMP mp) {
                mp.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + EnumColor.GREY + " " + LangUtils.localize("tooltip.canPlace.pos") + ": " + "X " + testPos.getX() + " " + "Y " + testPos.getY() + " " + "Z " + testPos.getZ()));
            }
        }

        if (MekanismConfig.current().multiblock.LargeWindGenerationRangeStops.val()) {
            ChunkPos currentChunk = new ChunkPos(pos);
            int rangeCheck = MekanismConfig.current().multiblock.LargeWindGeneratorRangeCheck.val();
            int range;
            if (rangeCheck % 16 != 0) {
                range = rangeCheck / 16 + 1;
            } else {
                range = rangeCheck / 16;
            }
            outer:
            for (int chunkX = currentChunk.x - range; chunkX <= currentChunk.x + range; chunkX++) {
                for (int chunkZ = currentChunk.z - range; chunkZ <= currentChunk.z + range; chunkZ++) {
                    Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }
                    Map<BlockPos, TileEntity> tileEntityMap = chunk.getTileEntityMap();
                    for (TileEntity tileEntity : tileEntityMap.values()) {
                        if (tileEntity instanceof TileEntityLargeWindGenerator) {
                            BlockPos tilePos = tileEntity.getPos();
                            double distanceSquared = pos.distanceSq(tilePos);
                            if (distanceSquared <= rangeCheck * rangeCheck) {
                                isCanPlace = true;
                                if (player instanceof EntityPlayerMP mp) {
                                    mp.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + EnumColor.GREY + " " + LangUtils.localize("tooltip.tileEntity.pos") + ": " + "X " + tilePos.getX() + " " + "Y " + tilePos.getY() + " " + "Z " + tilePos.getZ()));
                                }
                                break outer;
                            }
                        }
                    }
                }
            }
        }
        return isCanPlace;
    }

}
