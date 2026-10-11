package mekanism.common.block;

import mekanism.client.Particle;
import mekanism.common.tile.interfaces.ITileRadioactive;
import mekanism.common.util.WorldUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Random;

public abstract class BlockMekanismContainer extends BlockContainer {

    protected BlockMekanismContainer(Material materialIn) {
        super(materialIn);
    }

    @Nonnull
    protected abstract ItemStack getDropItem(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos);

    /**
     * {@inheritDoc} Used together with {@link Block#removedByPlayer(IBlockState, World, BlockPos, EntityPlayer, boolean)}.
     * <br>
     * This is like Vanilla's {@link BlockContainer#harvestBlock(World, EntityPlayer, BlockPos, IBlockState, TileEntity, ItemStack)} except that uses the custom {@link
     * ItemStack} from {@link #getDropItem(IBlockState, IBlockAccess, BlockPos)}
     *
     * @author Forge
     * @see BlockFlowerPot#harvestBlock(World, EntityPlayer, BlockPos, IBlockState, TileEntity, ItemStack)
     */
    @Override
    public void harvestBlock(@Nonnull World world, EntityPlayer player, @Nonnull BlockPos pos, @Nonnull IBlockState state, TileEntity te, @Nonnull ItemStack stack) {
        StatBase blockStats = StatList.getBlockStats(this);
        if (blockStats != null) {
            player.addStat(blockStats);
        }
        player.addExhaustion(0.005F);
        if (!world.isRemote) {
            ItemStack dropItem = getDropItem(state, world, pos);
            //why Add a custom name
            /*
            if (te instanceof IWorldNameable worldNameable) {
                dropItem.setStackDisplayName(worldNameable.getName());
            }
             */

            Block.spawnAsEntity(world, pos, dropItem);
        }
        //Set it to air like the flower pot's harvestBlock method
        world.setBlockToAir(pos);
    }

    /**
     * Returns that this "cannot" be silk touched. This is so that {@link Block#getSilkTouchDrop(IBlockState)} is not called, because only {@link
     * Block#getDrops(NonNullList, IBlockAccess, BlockPos, IBlockState, int)} supports tile entities. Our blocks keep their inventory and other behave like they are being
     * silk touched by default anyway.
     *
     * @return false
     */
    @Override
    @Deprecated
    protected boolean canSilkHarvest() {
        return false;
    }

    @Override
    public void getDrops(@Nonnull NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, @Nonnull IBlockState state, int fortune) {
        drops.add(getDropItem(state, world, pos));
    }

    /**
     * {@inheritDoc} Keep tile entity in world until after {@link Block#getDrops(NonNullList, IBlockAccess, BlockPos, IBlockState, int)}. Used together with {@link
     * Block#harvestBlock(World, EntityPlayer, BlockPos, IBlockState, TileEntity, ItemStack)}.
     *
     * @author Forge
     * @see BlockFlowerPot#removedByPlayer(IBlockState, World, BlockPos, EntityPlayer, boolean)
     */
    @Override
    public boolean removedByPlayer(@Nonnull IBlockState state, World world, @Nonnull BlockPos pos, @Nonnull EntityPlayer player, boolean willHarvest) {
        return willHarvest || super.removedByPlayer(state, world, pos, player, false);
    }

    @Nonnull
    @Override
    public ItemStack getPickBlock(@Nonnull IBlockState state, RayTraceResult target, @Nonnull World world, @Nonnull BlockPos pos, EntityPlayer player) {
        return getDropItem(state, world, pos);
    }

    @Override
    @Deprecated
    public float getPlayerRelativeBlockHardness(IBlockState state, @Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos) {
        float speed = super.getPlayerRelativeBlockHardness(state, player, world, pos);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof ITileRadioactive rad && rad.hasRadioactiveContents()) {
            return speed / 5F;
        }
        return speed;
    }

    @SideOnly(Side.CLIENT)
    @Deprecated
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random random) {
        super.randomDisplayTick(state, world, pos, random);
        TileEntity tile = WorldUtils.getTileEntity(world, pos);
        if (tile instanceof ITileRadioactive rad) {
            int count = rad.getRadiationParticleCount();
            if (count > 0) {
                //Update count to be randomized but store it instead of calculating our max number each time we loop
                count = random.nextInt(count);
                for (int i = 0; i < count; i++) {
                    double randX = pos.getX() - 0.1 + random.nextDouble() * 1.2;
                    double randY = pos.getY() - 0.1 + random.nextDouble() * 1.2;
                    double randZ = pos.getZ() - 0.1 + random.nextDouble() * 1.2;
                    world.spawnParticle(Particle.radiation, randX, randY, randZ, 0, 0, 0);
                }
            }
        }
    }

}
